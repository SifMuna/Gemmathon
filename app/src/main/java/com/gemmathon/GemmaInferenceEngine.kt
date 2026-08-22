package com.gemmathon

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class EvaluationResult(
    val isSuccess: Boolean,
    val feedback: String,
    val revisedInstructions: String
)

class GemmaInferenceEngine {
    private var llmInference: LlmInference? = null
    private var currentSettings: Settings = Settings()
    private val gson = Gson()
    private val tag = "GemmaInferenceEngine"

    @Throws(Exception::class)
    suspend fun init(context: Context, modelPath: String, settings: Settings) = withContext(Dispatchers.IO) {
        Log.d(tag, "Initializing Gemma model from: $modelPath")
        llmInference?.close()
        currentSettings = settings
        val backend = when (settings.backend) {
            InferenceBackend.CPU -> LlmInference.Backend.CPU
            InferenceBackend.GPU -> LlmInference.Backend.GPU
        }
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(settings.maxTokens)
            .setMaxTopK(settings.topK)
            .setPreferredBackend(backend)
            .build()
        llmInference = LlmInference.createFromOptions(context, options)
        Log.d(tag, "Gemma model initialized successfully")
    }

    fun updateSettings(settings: Settings) {
        currentSettings = settings
    }

    private fun generateWithSession(prompt: String, seedOffset: Int = 0): String {
        val engine = llmInference ?: throw IllegalStateException("Model not initialized")
        val baseSeed = if (currentSettings.useFixedSeed) currentSettings.randomSeed else (0..Int.MAX_VALUE).random()
        val seed = baseSeed + seedOffset
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(currentSettings.topK)
            .setTopP(currentSettings.topP)
            .setTemperature(currentSettings.temperature)
            .setRandomSeed(seed)
            .build()
        DebugLog.d("=== PARAMS === temp=${currentSettings.temperature} topK=${currentSettings.topK} topP=${currentSettings.topP} maxTokens=${currentSettings.maxTokens} seed=$seed")
        val session = LlmInferenceSession.createFromOptions(engine, sessionOptions)
        return try {
            session.addQueryChunk(prompt)
            session.generateResponse()
        } finally {
            session.close()
        }
    }

    suspend fun generateCode(task: String): String = withContext(Dispatchers.IO) {
        val prompt = buildString {
            append("<start_of_turn>user\n")
            append(currentSettings.codeGenPrompt)
            append("\n\nWrite Python code to: $task")
            append("<end_of_turn>\n")
            append("<start_of_turn>model\n")
        }
        Log.d(tag, "Generating code for task: $task")
        DebugLog.d("=== CODE GEN PROMPT ===\n$prompt")
        val response = generateWithSession(prompt)
        DebugLog.d("=== CODE GEN RESPONSE ===\n$response")
        Log.d(tag, "Raw code response: $response")
        extractCodeFromResponse(response)
    }

    suspend fun evaluateOutput(
        code: String,
        stdout: String,
        stderr: String,
        originalTask: String
    ): EvaluationResult = withContext(Dispatchers.IO) {
        val cleanedStderr = cleanStderr(stderr)
        val numericValues = Regex("""[-+]?\d+\.\d+""").findAll(stdout)
            .mapNotNull { it.value.toDoubleOrNull() }.take(5).toList()
        val prompt = buildString {
            append("<start_of_turn>user\n")
            append(currentSettings.evalPrompt)
            append("\n\nTask: $originalTask\n")
            append("Code:\n$code\n")
            if (numericValues.isNotEmpty()) {
                append("Numeric outputs detected: ${numericValues.joinToString(", ")}\n")
                append("Verify these are mathematically correct for the task.\n")
            }
            append("Stdout: ${stdout.take(500)}\n")
            append("Stderr: ${cleanedStderr.take(500)}")
            append("<end_of_turn>\n")
            append("<start_of_turn>model\n")
        }
        Log.d(tag, "Evaluating code output")
        DebugLog.d("=== EVAL PROMPT ===\n$prompt")
        val response = generateWithSession(prompt)
        DebugLog.d("=== EVAL RESPONSE ===\n$response")
        Log.d(tag, "Raw evaluation response: $response")
        parseEvaluationResponse(response)
    }

    suspend fun generateRewrite(
        originalTask: String,
        previousCode: String,
        feedback: String,
        revisedInstructions: String,
        stderr: String = "",
        attemptSeedOffset: Int = 0
    ): String = withContext(Dispatchers.IO) {
        val cleanedStderr = cleanStderr(stderr)
        val strippedCode = stripPythonComments(previousCode)
        val badLine = extractBadLine(previousCode, cleanedStderr)
        val fixHint = revisedInstructions.take(200).let {
            if (it.length < revisedInstructions.length) "$it…" else it
        }
        val prompt = buildString {
            append("<start_of_turn>user\n")
            append(currentSettings.rewritePrompt)
            append("\n\nOriginal task: $originalTask\n\n")
            append("Previous code that failed:\n$strippedCode\n\n")
            if (cleanedStderr.isNotBlank()) {
                append("Error output:\n$cleanedStderr\n\n")
            }
            if (badLine != null) {
                append("Specific bad line: $badLine\n\n")
            }
            append("What went wrong: $feedback\n")
            if (fixHint.isNotBlank()) append("Hint: $fixHint\n")
            append("<end_of_turn>\n")
            append("<start_of_turn>model\n")
        }
        Log.d(tag, "Generating rewrite for attempt")
        DebugLog.d("=== REWRITE PROMPT ===\n$prompt")
        val response = generateWithSession(prompt, seedOffset = attemptSeedOffset)
        DebugLog.d("=== REWRITE RESPONSE ===\n$response")
        Log.d(tag, "Raw rewrite response: $response")
        extractCodeFromResponse(response)
    }

    private fun stripPythonComments(code: String): String {
        val result = StringBuilder()
        for (line in code.lines()) {
            var inStr = false
            var strCh = ' '
            var j = 0
            val stripped = StringBuilder()
            while (j < line.length) {
                val ch = line[j]
                when {
                    inStr && ch == '\\' -> {
                        stripped.append(ch)
                        j++
                        if (j < line.length) stripped.append(line[j])
                    }
                    inStr && ch == strCh -> { inStr = false; stripped.append(ch) }
                    inStr -> stripped.append(ch)
                    ch == '"' || ch == '\'' -> { inStr = true; strCh = ch; stripped.append(ch) }
                    ch == '#' -> break
                    else -> stripped.append(ch)
                }
                j++
            }
            val trimmed = stripped.toString().trimEnd()
            if (trimmed.isNotBlank()) result.appendLine(trimmed)
        }
        return result.toString().trim()
    }

    private fun cleanStderr(stderr: String): String {
        if (stderr.isBlank()) return stderr
        val result = mutableListOf<String>()
        val lines = stderr.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if ("code_runner.py" in line) {
                i += 2  // skip this frame line + the source line below it
                continue
            }
            result.add(line)
            i++
        }
        return result.joinToString("\n").trim()
    }

    private fun extractBadLine(code: String, stderr: String): String? {
        val lineNumRegex = Regex("""File "<generated>", line (\d+)""")
        val lastLineNum = lineNumRegex.findAll(stderr).lastOrNull()
            ?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val codeLine = code.lines().getOrNull(lastLineNum - 1)?.trim() ?: return null
        return "line $lastLineNum: `$codeLine`"
    }

    private fun extractCodeFromResponse(response: String): String {
        val clean = response.substringBefore("<end_of_turn>").trim()
        val fencePatterns = listOf(
            Regex("```python\\s*\\n([\\s\\S]*?)```"),
            Regex("```\\s*\\n([\\s\\S]*?)```"),
            Regex("```([\\s\\S]*?)```")
        )
        for (pattern in fencePatterns) {
            val match = pattern.find(clean)
            if (match != null) return match.groupValues[1].trim()
        }
        return clean
    }

    private fun parseEvaluationResponse(response: String): EvaluationResult {
        val jsonStr = extractJsonFromResponse(response)
        return try {
            val parsed = gson.fromJson(jsonStr, Map::class.java)
            val success = parsed["success"] as? Boolean ?: false
            val feedback = parsed["feedback"] as? String ?: "No feedback provided"
            val revised = parsed["revised_instructions"] as? String ?: ""
            EvaluationResult(isSuccess = success, feedback = feedback, revisedInstructions = revised)
        } catch (e: Exception) {
            Log.w(tag, "Failed to parse evaluation JSON: $jsonStr", e)
            EvaluationResult(
                isSuccess = false,
                feedback = "Could not parse evaluator response — treating as failure. Raw: ${response.take(200)}",
                revisedInstructions = "Fix any errors and try again"
            )
        }
    }

    private fun extractJsonFromResponse(response: String): String {
        // Walk the string to find the first complete {...} by brace counting,
        // so trailing stray braces emitted by the model don't pollute the result.
        val fencePatterns = listOf(
            Regex("```json\\s*\\n([\\s\\S]*?)```"),
            Regex("```\\s*\\n([\\s\\S]*?)```")
        )
        for (pattern in fencePatterns) {
            val fenceMatch = pattern.find(response)
            if (fenceMatch != null) return fenceMatch.groupValues[1].trim()
        }
        val start = response.indexOf('{')
        if (start != -1) {
            var depth = 0
            var inString = false
            var escaped = false
            for (i in start until response.length) {
                val ch = response[i]
                when {
                    escaped          -> escaped = false
                    ch == '\\' && inString -> escaped = true
                    ch == '"'        -> inString = !inString
                    !inString && ch == '{' -> depth++
                    !inString && ch == '}' -> {
                        depth--
                        if (depth == 0) return response.substring(start, i + 1)
                    }
                }
            }
        }
        return response.trim()
    }

    fun isInitialized(): Boolean = llmInference != null

    fun close() {
        llmInference?.close()
        llmInference = null
        Log.d(tag, "Gemma model closed")
    }
}
