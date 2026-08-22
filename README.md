# Gemmathon

An Android app that gives an on-device Gemma model a Python interpreter and
lets it iterate: describe a task in plain English, Gemma writes Python code,
the code runs locally via Chaquopy, Gemma evaluates the output, and — if it's
wrong — rewrites the code and tries again, up to a configurable attempt limit.

Everything (inference, execution, evaluation) happens on-device. No network
calls are made for the task loop itself.

## Architecture

- **`GemmaInferenceEngine`** (`app/src/main/java/com/gemmathon/GemmaInferenceEngine.kt`)
  Wraps MediaPipe's `LlmInference`/`LlmInferenceSession` to load a local
  `.task` Gemma model and run three kinds of prompts: code generation,
  output evaluation (asks the model to return JSON: `success`, `feedback`,
  `revised_instructions`), and rewrite generation when a previous attempt
  failed. Includes response cleanup (stripping code fences, brace-counting
  JSON extraction, stderr cleanup to hide interpreter-internal frames).

- **`PythonExecutor`** (`app/src/main/java/com/gemmathon/PythonExecutor.kt`)
  Runs generated code through Chaquopy by calling `code_runner.run()`
  (`app/src/main/python/code_runner.py`) and returns stdout/stderr/exit code.

- **`CodeRewriteEngine`** (`app/src/main/java/com/gemmathon/CodeRewriteEngine.kt`)
  Drives the generate → execute → evaluate → (rewrite) loop as a `Flow<Step>`,
  emitting a `Step` for each stage so the UI can show live progress. Also
  runs a lightweight sanity check (`checkNumericPlausibility`) against known
  constants (π, e, φ) to catch algorithmically-wrong-but-clean-running code
  before asking the model to evaluate it.

- **`MainViewModel`** (`app/src/main/java/com/gemmathon/MainViewModel.kt`)
  Holds `UiState`, owns the `GemmaInferenceEngine`/`PythonExecutor`/
  `CodeRewriteEngine` instances, and exposes `runTask`, `stopTask`, and
  `runParameterTest` (sweeps the four built-in `TUNING_PROFILES` — Minimal /
  Low / Balanced / Standard temperature-topK-topP combos — over the same task
  to compare success rate/latency).

- **`MainActivity`** (`app/src/main/java/com/gemmathon/MainActivity.kt`)
  Switches between `ModelSetupScreen` (pick or load a `.task` model file),
  `MainScreen` (task input + step-by-step run view), and `SettingsScreen`.
  Also registers a `BroadcastReceiver` for `com.gemmathon.RUN_TASK` so tasks
  can be injected over adb for scripted testing.

- **`Settings`** (`app/src/main/java/com/gemmathon/Settings.kt`) /
  **`SettingsRepository`** — inference parameters (temperature, topK, topP,
  max tokens, seed, backend, max rewrite attempts) and the three editable
  system prompts (code-gen, eval, rewrite), persisted via DataStore.

## Build & run

Requires JDK 21 and an Android device (the app targets `arm64-v8a` only).

```bash
./gradlew installDebug
```

A Gemma `.task` model file (e.g. `gemma-4-E2B-it-web.task`) must be loaded
from the device via the in-app file picker, or dropped at
`/sdcard/Download/gemma-4-E2B-it-web.task` and loaded via the "load from
default path" option on the setup screen. Model files are gitignored and
never committed.

## Testing without touching the screen

With a model already loaded, inject a task over adb:

```bash
adb shell am broadcast -a com.gemmathon.RUN_TASK --es task "compute the first 10 fibonacci numbers"
```

## Notable gotchas

- `InferenceBackend.GPU` exists in `Settings` but on-device testing has found
  the CPU backend more reliable for this model/quantization; GPU is opt-in.
- The rewrite prompt strips comments and the specific failing line out of the
  previous attempt (`stripPythonComments`, `extractBadLine`) before handing
  it back to the model — keeps rewrite prompts short and focused.
- `checkNumericPlausibility` runs *before* the model-based evaluator and can
  short-circuit straight to "needs revision" with a hint (e.g. the correct
  series formula) when a known-constant estimate is numerically implausible.
