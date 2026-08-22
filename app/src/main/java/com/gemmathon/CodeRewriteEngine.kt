package com.gemmathon

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed class Step {
    data object GeneratingCode : Step()
    data class CodeGenerated(val code: String, val attempt: Int) : Step()
    data object Executing : Step()
    data class ExecutionDone(val result: ExecutionResult, val attempt: Int) : Step()
    data object Evaluating : Step()
    data class EvaluationResult(val isSuccess: Boolean, val feedback: String, val attempt: Int) : Step()
    data class Success(val finalCode: String, val output: String, val attempts: Int) : Step()
    data class Failed(val attempts: Int, val lastError: String) : Step()
    data class Rewriting(val feedback: String, val attempt: Int) : Step()
}

class CodeRewriteEngine(
    private val gemma: GemmaInferenceEngine,
    private val pythonExecutor: PythonExecutor,
    private val maxAttempts: Int = 5
) {
    private val tag = "CodeRewriteEngine"

    fun runTask(task: String): Flow<Step> = flow {
        Log.d(tag, "Starting task: $task")

        var attempt = 0
        var previousCode: String? = null
        var lastFeedback = ""
        var lastRevised = ""
        var lastStderr = ""

        while (attempt < maxAttempts) {
            attempt++
            Log.d(tag, "Attempt $attempt of $maxAttempts")

            // Step 1: Generate (or rewrite) code
            emit(Step.GeneratingCode)
            val code = try {
                if (previousCode == null) {
                    gemma.generateCode(task)
                } else {
                    gemma.generateRewrite(task, previousCode!!, lastFeedback, lastRevised, lastStderr, attemptSeedOffset = attempt)
                }
            } catch (e: Exception) {
                Log.e(tag, "Code generation failed", e)
                emit(Step.Failed(attempt, "Code generation error: ${e.message}"))
                return@flow
            }

            emit(Step.CodeGenerated(code, attempt))

            // Step 2: Execute the code
            emit(Step.Executing)
            val result = try {
                pythonExecutor.execute(code)
            } catch (e: Exception) {
                Log.e(tag, "Execution failed", e)
                ExecutionResult(stdout = "", stderr = "Execution error: ${e.message}", exitCode = -1, durationMs = 0)
            }

            emit(Step.ExecutionDone(result, attempt))
            DebugLog.d("=== EXECUTION (attempt $attempt) === exit=${result.exitCode}\n--- stdout ---\n${result.stdout}\n--- stderr ---\n${result.stderr}")

            // Step 3: Evaluate
            emit(Step.Evaluating)
            val plausibilityResult = checkNumericPlausibility(task, result.stdout)
            if (plausibilityResult != null) {
                DebugLog.d("=== PLAUSIBILITY CHECK FAILED === ${plausibilityResult.feedback}")
            }
            val evaluation = plausibilityResult ?: try {
                gemma.evaluateOutput(code = code, stdout = result.stdout, stderr = result.stderr, originalTask = task)
            } catch (e: Exception) {
                Log.e(tag, "Evaluation failed", e)
                val basicSuccess = result.exitCode == 0 && result.stderr.isBlank()
                EvaluationResult(
                    isSuccess = basicSuccess,
                    feedback = if (basicSuccess) "Code ran without errors" else "Errors in stderr: ${result.stderr.take(150)}",
                    revisedInstructions = if (!basicSuccess) "Fix the errors shown in stderr" else ""
                )
            }

            emit(Step.EvaluationResult(evaluation.isSuccess, evaluation.feedback, attempt))

            val cleanRun = result.exitCode == 0 && result.stderr.isBlank()
            if (evaluation.isSuccess && cleanRun) {
                Log.d(tag, "Task succeeded on attempt $attempt")
                emit(Step.Success(finalCode = code, output = result.stdout, attempts = attempt))
                return@flow
            }

            if (attempt < maxAttempts) {
                Log.d(tag, "Rewriting due to: ${evaluation.feedback}")
                emit(Step.Rewriting(evaluation.feedback, attempt))
                previousCode = code
                lastFeedback = evaluation.feedback
                lastRevised = evaluation.revisedInstructions
                lastStderr = result.stderr
            } else {
                val lastError = buildString {
                    if (result.stderr.isNotBlank()) append("Error: ${result.stderr}")
                    if (evaluation.feedback.isNotBlank()) append("\nEvaluation: ${evaluation.feedback}")
                }.trim().ifBlank { "Max attempts reached without success" }
                Log.d(tag, "Task failed after $attempt attempts")
                emit(Step.Failed(attempt, lastError))
            }
        }
    }

    private fun checkNumericPlausibility(task: String, stdout: String): EvaluationResult? {
        val floats = Regex("""[-+]?\d+\.\d+""").findAll(stdout)
            .mapNotNull { it.value.toDoubleOrNull() }.toList()
        if (floats.isEmpty()) return null

        data class KnownConstant(
            val keywords: List<String>,
            val expected: Double,
            val tolerance: Double,
            val hint: String
        )
        val constants = listOf(
            KnownConstant(
                keywords = listOf("pi", "π"),
                expected = 3.14159265,
                tolerance = 0.20,
                hint = "For Monte Carlo: use random.random() (not random.randint) for continuous sampling. " +
                    "For series: the Leibniz formula is π/4 = 1 - 1/3 + 1/5 - 1/7 + ..."
            ),
            KnownConstant(
                keywords = listOf("euler's number", "euler number", "napier"),
                expected = 2.71828182,
                tolerance = 0.10,
                hint = "The series for e uses factorials: each term is 1/math.factorial(n) for n=0,1,2,..."
            ),
            KnownConstant(
                keywords = listOf("golden ratio", "phi", "φ"),
                expected = 1.61803398,
                tolerance = 0.10,
                hint = "The golden ratio is (1 + math.sqrt(5)) / 2. " +
                    "Alternatively: ratio of consecutive Fibonacci numbers converges to phi."
            ),
        )

        val lower = task.lowercase()
        for (const in constants) {
            if (const.keywords.none { lower.contains(it) }) continue
            val closest = floats.minByOrNull { Math.abs(it - const.expected) } ?: continue
            if (Math.abs(closest - const.expected) > const.tolerance) {
                return EvaluationResult(
                    isSuccess = false,
                    feedback = "Numeric output ${"%.5f".format(closest)} is not a plausible estimate " +
                        "of ${const.keywords.first()} (expected ~${"%.5f".format(const.expected)}). " +
                        "The algorithm is fundamentally wrong.",
                    revisedInstructions = const.hint
                )
            }
        }
        return null
    }
}
