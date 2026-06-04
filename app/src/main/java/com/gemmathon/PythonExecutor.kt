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
            val py = Python.getInstance()
            val sys = py.getModule("sys")
            val io = py.getModule("io")

            // Create StringIO buffers for capturing output
            val capturedOut = io.callAttr("StringIO")
            val capturedErr = io.callAttr("StringIO")

            // Redirect stdout and stderr
            val originalStdout = sys["stdout"]
            val originalStderr = sys["stderr"]
            sys["stdout"] = capturedOut
            sys["stderr"] = capturedErr

            try {
                // Execute the code string
                py.getBuiltins().callAttr("exec", code)
            } catch (e: com.chaquo.python.PyException) {
                Log.w(tag, "Python execution raised exception: ${e.message}")
                stderr += "\nPyException: ${e.message}"
                exitCode = 1
            } catch (e: Exception) {
                Log.w(tag, "Exception during Python exec: ${e.message}")
                stderr += "\nException: ${e.message}"
                exitCode = 1
            } finally {
                // Restore original stdout/stderr
                sys["stdout"] = originalStdout
                sys["stderr"] = originalStderr
            }

            // Get captured output
            stdout = capturedOut.callAttr("getvalue").toString()
            stderr = capturedErr.callAttr("getvalue").toString() + stderr

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
