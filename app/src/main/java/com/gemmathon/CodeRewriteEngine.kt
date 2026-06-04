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
    data class Success(val finalCode: String, val output: String, val attempts: Int) : Step()
    data class Failed(val attempts: Int, val lastError: String) : Step()
    data class Rewriting(val feedback: String, val attempt: Int) : Step()
}

class CodeRewriteEngine(
    private val gemma: GemmaInferenceEngine,
    private val pythonExecutor: PythonExecutor
) {
    private val tag = "CodeRewriteEngine"
    private val maxAttempts = 5

    fun runTask(task: String): Flow<Step> = flow {
        Log.d(tag, "Starting task: $task")

        var attempt = 0
        var previousCode: String? = null
        var lastFeedback = ""
        var lastRevised = ""

        while (attempt < maxAttempts) {
            attempt++
            Log.d(tag, "Attempt $attempt of $maxAttempts")

            // Step 1: Generate (or rewrite) code
            emit(Step.GeneratingCode)
            val code = try {
                if (previousCode == null) {
                    gemma.generateCode(task)
                } else {
                    gemma.generateRewrite(task, previousCode!!, lastFeedback, lastRevised)
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

            // Step 3: Evaluate
            emit(Step.Evaluating)
            val evaluation = try {
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

            if (evaluation.isSuccess) {
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
}
