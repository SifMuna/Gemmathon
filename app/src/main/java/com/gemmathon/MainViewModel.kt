package com.gemmathon

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class ModelState {
    data object NotLoaded : ModelState()
    data object Loading : ModelState()
    data class Loaded(val modelPath: String) : ModelState()
    data class Error(val message: String) : ModelState()
}

data class UiState(
    val modelState: ModelState = ModelState.NotLoaded,
    val steps: List<Step> = emptyList(),
    val isRunning: Boolean = false,
    val currentTask: String = "",
    val statusMessage: String = "Ready"
)

class MainViewModel : ViewModel() {
    private val tag = "MainViewModel"

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val gemmaEngine = GemmaInferenceEngine()
    private val pythonExecutor = PythonExecutor()
    private var codeRewriteEngine: CodeRewriteEngine? = null

    fun loadModel(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(modelState = ModelState.Loading, statusMessage = "Loading model...") }

            var pfd: ParcelFileDescriptor? = null
            try {
                val modelPath = when (uri.scheme) {
                    "file" -> uri.path ?: throw Exception("Invalid file URI")
                    else -> {
                        // Keep the PFD open until MediaPipe finishes loading, then close it.
                        // /proc/self/fd/N is only valid while the underlying fd is open.
                        pfd = context.contentResolver.openFileDescriptor(uri, "r")
                            ?: throw Exception("Cannot open model file")
                        "/proc/self/fd/${pfd.fd}"
                    }
                }

                gemmaEngine.init(context, modelPath)   // pfd stays open during load
                pfd?.close()
                pfd = null

                codeRewriteEngine = CodeRewriteEngine(gemmaEngine, pythonExecutor)
                _uiState.update {
                    it.copy(
                        modelState = ModelState.Loaded(modelPath),
                        statusMessage = "Model loaded successfully"
                    )
                }
                Log.d(tag, "Model loaded from: $modelPath")
            } catch (e: Exception) {
                pfd?.close()
                Log.e(tag, "Failed to load model", e)
                _uiState.update {
                    it.copy(
                        modelState = ModelState.Error("Failed to load model: ${e.message}"),
                        statusMessage = "Error loading model"
                    )
                }
            }
        }
    }

    fun loadModelFromPath(context: Context, path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(modelState = ModelState.Loading, statusMessage = "Loading model...") }

            try {
                gemmaEngine.init(context, path)
                codeRewriteEngine = CodeRewriteEngine(gemmaEngine, pythonExecutor)

                _uiState.update {
                    it.copy(
                        modelState = ModelState.Loaded(path),
                        statusMessage = "Model loaded successfully"
                    )
                }
                Log.d(tag, "Model loaded from path: $path")
            } catch (e: Exception) {
                Log.e(tag, "Failed to load model from path", e)
                _uiState.update {
                    it.copy(
                        modelState = ModelState.Error("Failed to load model: ${e.message}"),
                        statusMessage = "Error loading model"
                    )
                }
            }
        }
    }

    fun runTask(task: String) {
        val engine = codeRewriteEngine ?: run {
            _uiState.update { it.copy(statusMessage = "Model not loaded") }
            return
        }

        if (task.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Please enter a task") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    steps = emptyList(),
                    isRunning = true,
                    currentTask = task,
                    statusMessage = "Starting..."
                )
            }

            engine.runTask(task).collect { step ->
                _uiState.update { state ->
                    val newSteps = state.steps + step
                    val statusMsg = when (step) {
                        is Step.GeneratingCode -> "Generating Python code..."
                        is Step.CodeGenerated -> "Code generated (attempt ${step.attempt})"
                        is Step.Executing -> "Executing Python code..."
                        is Step.ExecutionDone -> "Execution complete"
                        is Step.Evaluating -> "Evaluating results..."
                        is Step.Success -> "Task completed successfully in ${step.attempts} attempt(s)!"
                        is Step.Failed -> "Task failed after ${step.attempts} attempt(s)"
                        is Step.Rewriting -> "Rewriting code based on feedback..."
                    }
                    val isRunning = step !is Step.Success && step !is Step.Failed
                    state.copy(
                        steps = newSteps,
                        isRunning = isRunning,
                        statusMessage = statusMsg
                    )
                }
            }
        }
    }

    fun reset() {
        _uiState.update {
            it.copy(
                steps = emptyList(),
                isRunning = false,
                currentTask = "",
                statusMessage = "Ready"
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        gemmaEngine.close()
    }
}
