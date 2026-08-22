package com.gemmathon

import android.app.Application
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

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
    val statusMessage: String = "Ready",
    val showSettings: Boolean = false,
    val testMode: Boolean = false,
    val testResults: List<TuningResult> = emptyList(),
    val testProgress: String = "",
    val testProfileIndex: Int = 0
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val tag = "MainViewModel"

    private val settingsRepository = SettingsRepository(application)

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val gemmaEngine = GemmaInferenceEngine()
    private val pythonExecutor = PythonExecutor()
    private var runJob: Job? = null

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsRepository.update(settings)
            gemmaEngine.updateSettings(settings)
        }
    }

    fun showSettings() { _uiState.update { it.copy(showSettings = true) } }
    fun hideSettings() { _uiState.update { it.copy(showSettings = false) } }

    fun loadModel(context: android.content.Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(modelState = ModelState.Loading, statusMessage = "Loading model...") }

            var pfd: ParcelFileDescriptor? = null
            try {
                val modelPath = when (uri.scheme) {
                    "file" -> uri.path ?: throw Exception("Invalid file URI")
                    else -> {
                        pfd = context.contentResolver.openFileDescriptor(uri, "r")
                            ?: throw Exception("Cannot open model file")
                        "/proc/self/fd/${pfd.fd}"
                    }
                }
                gemmaEngine.init(context, modelPath, settings.value)
                pfd?.close()
                pfd = null
                _uiState.update {
                    it.copy(
                        modelState = ModelState.Loaded(modelPath),
                        statusMessage = "Model loaded successfully"
                    )
                }
                Log.d(tag, "Model loaded from: $modelPath")
            } catch (e: Throwable) {
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

    fun loadModelFromPath(context: android.content.Context, path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(modelState = ModelState.Loading, statusMessage = "Loading model...") }
            var pfd: ParcelFileDescriptor? = null
            try {
                pfd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
                val fdPath = "/proc/self/fd/${pfd.fd}"
                gemmaEngine.init(context, fdPath, settings.value)
                pfd.close()
                pfd = null
                _uiState.update {
                    it.copy(
                        modelState = ModelState.Loaded(path),
                        statusMessage = "Model loaded successfully"
                    )
                }
                Log.d(tag, "Model loaded from path: $path")
            } catch (e: Throwable) {
                pfd?.close()
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

    fun stopTask() {
        runJob?.cancel()
        runJob = null
        _uiState.update { it.copy(isRunning = false, statusMessage = "Stopped") }
        gemmaEngine.updateSettings(settings.value)
    }

    fun toggleTestMode() {
        _uiState.update { it.copy(testMode = !it.testMode, testResults = emptyList(), testProgress = "") }
    }

    fun applyTuningProfile(profile: TuningProfile) {
        val updated = settings.value.copy(
            temperature = profile.temperature,
            topK = profile.topK,
            topP = profile.topP
        )
        viewModelScope.launch {
            settingsRepository.update(updated)
            gemmaEngine.updateSettings(updated)
        }
    }

    fun runParameterTest(task: String) {
        if (task.isBlank()) return
        runJob?.cancel()
        runJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRunning = true,
                    testResults = emptyList(),
                    testProgress = "Starting parameter test...",
                    testProfileIndex = 0,
                    statusMessage = "Running parameter test..."
                )
            }
            val results = mutableListOf<TuningResult>()
            val base = settings.value
            for ((index, profile) in TUNING_PROFILES.withIndex()) {
                _uiState.update {
                    it.copy(
                        testProgress = "Testing ${index + 1}/${TUNING_PROFILES.size}: ${profile.name}…",
                        testProfileIndex = index
                    )
                }
                val tempSettings = base.copy(
                    temperature = profile.temperature,
                    topK = profile.topK,
                    topP = profile.topP
                )
                gemmaEngine.updateSettings(tempSettings)
                val engine = CodeRewriteEngine(gemmaEngine, pythonExecutor, base.maxAttempts)
                var success = false
                var attempts = 0
                var lastOutput = ""
                var lastEvalFeedback = ""
                val durationMs = measureTimeMillis {
                    engine.runTask(task).collect { step ->
                        when (step) {
                            is Step.Success          -> { success = true;  attempts = step.attempts; lastOutput = step.output }
                            is Step.Failed           -> { success = false; attempts = step.attempts; lastOutput = step.lastError }
                            is Step.EvaluationResult -> { lastEvalFeedback = step.feedback }
                            else -> {}
                        }
                    }
                }
                results.add(TuningResult(profile, success, attempts, durationMs, lastOutput, lastEvalFeedback))
                _uiState.update { it.copy(testResults = results.toList()) }
            }
            gemmaEngine.updateSettings(base)
            _uiState.update {
                it.copy(
                    isRunning = false,
                    testProgress = "Complete — ${results.count { it.success }}/${TUNING_PROFILES.size} profiles succeeded",
                    statusMessage = "Parameter test complete"
                )
            }
        }
    }

    fun runTask(task: String) {
        if (task.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Please enter a task") }
            return
        }

        runJob?.cancel()
        val engine = CodeRewriteEngine(gemmaEngine, pythonExecutor, settings.value.maxAttempts)

        runJob = viewModelScope.launch {
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
                        is Step.EvaluationResult -> if (step.isSuccess) "Evaluation: correct" else "Evaluation: needs revision"
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
