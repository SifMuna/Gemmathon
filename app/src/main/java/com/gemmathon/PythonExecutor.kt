package com.gemmathon

import android.util.Log
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExecutionResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val durationMs: Long
)

class PythonExecutor {
    private val tag = "PythonExecutor"

    suspend fun execute(code: String): ExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var stdout = ""
        var stderr = ""
        var exitCode = 0

        try {
            val result = Python.getInstance()
                .getModule("code_runner")
                .callAttr("run", code)
                .asList()
            stdout = result[0].toString()
            stderr = result[1].toString()
            exitCode = result[2].toInt()
            Log.d(tag, "Execution complete. stdout=${stdout.take(200)}, stderr=${stderr.take(200)}, exit=$exitCode")
        } catch (e: Exception) {
            Log.e(tag, "Failed to execute Python code", e)
            stderr = "Execution infrastructure error: ${e.message}"
            exitCode = -1
        }

        val durationMs = System.currentTimeMillis() - startTime

        ExecutionResult(
            stdout = stdout.trim(),
            stderr = stderr.trim(),
            exitCode = exitCode,
            durationMs = durationMs
        )
    }
}
