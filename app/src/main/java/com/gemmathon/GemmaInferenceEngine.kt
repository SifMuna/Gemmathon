package com.gemmathon

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class EvaluationResult(
    val isSuccess: Boolean,
    val feedback: String,
    val revisedInstructions: String
)

class GemmaInferenceEngine {
    private var llmInference: LlmInference? = null
    private val gson = Gson()
    private val tag = "GemmaInferenceEngine"

    @Throws(Exception::class)
    suspend fun init(context: Context, modelPath: String) = withContext(Dispatchers.IO) {
        Log.d(tag, "Initializing Gemma model from: $modelPath")
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(1024)
            .setTopK(40)
            .setTemperature(0.8f)
            .setRandomSeed(101)
            .build()
        llmInference = LlmInference.createFromOptions(context, options)
        Log.d(tag, "Gemma model initialized successfully")
    }

    suspend fun generateCode(task: String): String = withContext(Dispatchers.IO) {
        val engine = llmInference ?: throw IllegalStateException("Model not initialized")

        val prompt = buildString {
            append("<start_of_turn>user\n")
            append("You are a Python coding assistant. Write clean, working Python 3 code.\n")
            append("Only output the Python code itself, no explanation, no markdown fences.\n\n")
            append("Write Python code to: $task")
            append("<end_of_turn>\n")
            append("<start_of_turn>model\n")
        }

        Log.d(tag, "Generating code for task: $task")
        val response = engine.generateResponse(prompt)
        Log.d(tag, "Raw code response: $response")

        extractCodeFromResponse(response)
    }

    suspend fun evaluateOutput(
        code: String,
        stdout: String,
        stderr: String,
        originalTask: String
    ): EvaluationResult = withContext(Dispatchers.IO) {
        val engine = llmInference ?: throw IllegalStateException("Model not initialized")

        val prompt = buildString {
            append("<start_of_turn>user\n")
            append("You are a code evaluator. Analyze Python code execution results.\n")
            append("Respond with ONLY a JSON object (no markdown, no explanation): ")
            append("{\"success\": bool, \"feedback\": str, \"revised_instructions\": str}\n\n")
            append("Task: $originalTask\n")
            append("Code:\n$code\n")
            append("Stdout: ${stdout.take(500)}\n")
            append("Stderr: ${stderr.take(500)}\n\n")
            append("Did the code accomplish the task correctly? ")
            append("If not, what should be changed? Respond with ONLY the JSON object.")
            append("<end_of_turn>\n")
            append("<start_of_turn>model\n")
        }

        Log.d(tag, "Evaluating code output")
        val response = engine.generateResponse(prompt)
        Log.d(tag, "Raw evaluation response: $response")

        parseEvaluationResponse(response)
    }

    suspend fun generateRewrite(
        originalTask: String,
        previousCode: String,
        feedback: String,
        revisedInstructions: String
    ): String = withContext(Dispatchers.IO) {
        val engine = llmInference ?: throw IllegalStateException("Model not initialized")

        val fixInstructions = revisedInstructions.ifBlank { feedback }
        val prompt = buildString {
            append("<start_of_turn>user\n")
            append("You are a Python coding assistant fixing broken code.\n")
            append("Only output the corrected Python code, no explanation, no markdown fences.\n\n")
            append("Original task: $originalTask\n\n")
            append("Previous code that failed:\n$previousCode\n\n")
            append("What went wrong: $feedback\n")
            append("How to fix it: $fixInstructions")
            append("<end_of_turn>\n")
            append("<start_of_turn>model\n")
        }

        Log.d(tag, "Generating rewrite for attempt")
        val response = engine.generateResponse(prompt)
        Log.d(tag, "Raw rewrite response: $response")
        extractCodeFromResponse(response)
    }

    private fun extractCodeFromResponse(response: String): String {
        // Try to extract code from markdown fences if present
        val fencePatterns = listOf(
            Regex("```python\\s*\\n([\\s\\S]*?)```"),
            Regex("```\\s*\\n([\\s\\S]*?)```"),
            Regex("```([\\s\\S]*?)```")
        )

        for (pattern in fencePatterns) {
            val match = pattern.find(response)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }

        // Return as-is if no fences found
        return response.trim()
    }

    private fun parseEvaluationResponse(response: String): EvaluationResult {
        // Extract JSON from response, handling potential markdown wrapping
        val jsonStr = extractJsonFromResponse(response)

        return try {
            val parsed = gson.fromJson(jsonStr, Map::class.java)
            val success = parsed["success"] as? Boolean ?: false
            val feedback = parsed["feedback"] as? String ?: "No feedback provided"
            val revised = parsed["revised_instructions"] as? String ?: ""

            EvaluationResult(
                isSuccess = success,
                feedback = feedback,
                revisedInstructions = revised
            )
        } catch (e: JsonSyntaxException) {
            Log.w(tag, "Failed to parse evaluation JSON: $jsonStr", e)
            // Fallback: check for common success/failure keywords
            val lower = response.lowercase()
            val isSuccess = lower.contains("success") && !lower.contains("not success") &&
                    !lower.contains("failed") && !lower.contains("error")
            EvaluationResult(
                isSuccess = isSuccess,
                feedback = response.take(200),
                revisedInstructions = if (!isSuccess) "Fix the errors and try again" else ""
            )
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error parsing evaluation", e)
            EvaluationResult(
                isSuccess = false,
                feedback = "Failed to parse evaluation: ${e.message}",
                revisedInstructions = "Please rewrite the code more carefully"
            )
        }
    }

    private fun extractJsonFromResponse(response: String): String {
        // Try to find JSON object in the response
        val jsonPattern = Regex("\\{[\\s\\S]*\\}")
        val match = jsonPattern.find(response)
        if (match != null) {
            return match.value
        }

        // Try stripping markdown fences
        val fencePatterns = listOf(
            Regex("```json\\s*\\n([\\s\\S]*?)```"),
            Regex("```\\s*\\n([\\s\\S]*?)```")
        )
        for (pattern in fencePatterns) {
            val fenceMatch = pattern.find(response)
            if (fenceMatch != null) {
                return fenceMatch.groupValues[1].trim()
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
