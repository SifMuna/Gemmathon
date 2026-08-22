package com.gemmathon

enum class InferenceBackend { CPU, GPU }

const val DEFAULT_CODE_GEN_PROMPT =
    "You are a Python coding assistant. Write clean, working Python 3 code.\n" +
    "Only output the Python code itself, no explanation, no markdown fences.\n" +
    "Write self-contained code — define every function and variable you use.\n" +
    "Always print() any results or computed values so they appear in stdout.\n" +
    "Do not write any comments.\n" +
    "If the task requires finding values that satisfy mathematical constraints, use a loop to search — never hardcode a guess without proving it is correct."

const val DEFAULT_EVAL_PROMPT =
    "You are a code evaluator. Analyze Python code execution results.\n" +
    "Respond with ONLY a JSON object (no markdown, no explanation): " +
    "{\"success\": bool, \"feedback\": str, \"revised_instructions\": str}\n" +
    "Did the code accomplish the task correctly? Verify EVERY constraint in the task " +
    "against the actual output — check each one explicitly. Code that runs without errors " +
    "but violates any constraint (wrong value, wrong parity, fails a mathematical condition, etc.) " +
    "is a failure. Keep feedback under 50 words. When success is true set revised_instructions to \"\"; " +
    "when success is false set revised_instructions to a brief specific fix (e.g. which formula or function call to use). " +
    "Respond with ONLY the JSON object."

const val DEFAULT_REWRITE_PROMPT =
    "You are a Python coding assistant fixing broken code.\n" +
    "Only output the corrected Python code, no explanation, no markdown fences.\n" +
    "Do not write any comments. Every function body must contain executable statements, not just comments."

data class TuningProfile(val name: String, val temperature: Float, val topK: Int, val topP: Float)
data class TuningResult(
    val profile: TuningProfile,
    val success: Boolean,
    val attempts: Int,
    val durationMs: Long,
    val output: String = "",
    val evalFeedback: String = ""
)

val TUNING_PROFILES = listOf(
    TuningProfile("Minimal",  0.1f, 10, 0.80f),
    TuningProfile("Low",      0.2f, 20, 0.85f),
    TuningProfile("Balanced", 0.3f, 30, 0.90f),
    TuningProfile("Standard", 0.4f, 40, 0.95f),
)

data class Settings(
    val temperature: Float = 0.1f,
    val topK: Int = 3,
    val topP: Float = 0.9f,
    val maxTokens: Int = 2048,
    val randomSeed: Int = 101,
    val useFixedSeed: Boolean = false,
    val maxAttempts: Int = 5,
    val backend: InferenceBackend = InferenceBackend.CPU,
    val codeGenPrompt: String = DEFAULT_CODE_GEN_PROMPT,
    val evalPrompt: String = DEFAULT_EVAL_PROMPT,
    val rewritePrompt: String = DEFAULT_REWRITE_PROMPT
)
