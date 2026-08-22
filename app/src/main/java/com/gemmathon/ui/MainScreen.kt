package com.gemmathon.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.gemmathon.DebugLog
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Stop
import androidx.compose.foundation.clickable
import androidx.compose.material3.Switch
import com.gemmathon.TuningProfile
import com.gemmathon.TuningResult
import com.gemmathon.TUNING_PROFILES
import androidx.compose.material.icons.filled.RuleFolder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gemmathon.Step
import com.gemmathon.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: UiState,
    onRunTask: (String) -> Unit,
    onRunParameterTest: (String) -> Unit = {},
    onStop: () -> Unit = {},
    onToggleTestMode: () -> Unit = {},
    onApplyProfile: (TuningProfile) -> Unit = {},
    onReset: () -> Unit,
    onShowSettings: () -> Unit = {}
) {
    var taskInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new steps are added
    LaunchedEffect(uiState.steps.size) {
        if (uiState.steps.isNotEmpty()) {
            listState.animateScrollToItem(uiState.steps.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gemmathon",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    val context = LocalContext.current
                    IconButton(onClick = {
                        val log = DebugLog.getAll()
                        if (log.isBlank()) {
                            Toast.makeText(context, "No debug log yet", Toast.LENGTH_SHORT).show()
                        } else {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Gemmathon debug log", log))
                            Toast.makeText(context, "Debug log copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy debug log",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = {
                        val log = DebugLog.getAll()
                        if (log.isBlank()) {
                            Toast.makeText(context, "No debug log yet", Toast.LENGTH_SHORT).show()
                        } else {
                            val file = File(context.cacheDir, "gemmathon_debug.txt")
                            file.writeText(log)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Export debug log"))
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export debug log",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = onShowSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = {
                        onReset()
                        taskInput = ""
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Reset",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        bottomBar = {
            Column {
                if (uiState.isRunning) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = uiState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Task Input Area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Task",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = taskInput,
                        onValueChange = { taskInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = "e.g. write a script to sort a list",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        minLines = 2,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (!uiState.isRunning && taskInput.isNotBlank()) {
                                    onRunTask(taskInput)
                                }
                            }
                        ),
                        enabled = !uiState.isRunning
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Science,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (uiState.testMode) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Parameter Test Mode",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.testMode) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.testMode,
                            onCheckedChange = { onToggleTestMode() },
                            enabled = !uiState.isRunning
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (uiState.isRunning) {
                        Button(
                            onClick = onStop,
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stop")
                        }
                    } else {
                        Button(
                            onClick = {
                                if (taskInput.isNotBlank()) {
                                    if (uiState.testMode) onRunParameterTest(taskInput)
                                    else onRunTask(taskInput)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = taskInput.isNotBlank()
                        ) {
                            Icon(
                                imageVector = if (uiState.testMode) Icons.Default.Science else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (uiState.testMode) "Run Parameter Test" else "Run with Gemma")
                        }
                    }
                }
            }

            // Test mode progress + results
            if (uiState.testMode && (uiState.isRunning || uiState.testResults.isNotEmpty() || uiState.testProgress.isNotBlank())) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.testProgress.isNotBlank()) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(uiState.testProgress, style = MaterialTheme.typography.bodyMedium)
                                if (uiState.isRunning) {
                                    LinearProgressIndicator(
                                        progress = { (uiState.testProfileIndex.toFloat()) / TUNING_PROFILES.size },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    if (uiState.testResults.isNotEmpty()) {
                        TuningResultsCard(results = uiState.testResults, onApplyProfile = onApplyProfile)
                    }
                }
            }
            // Steps List (normal mode)
            else if (!uiState.testMode && uiState.steps.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.steps.size) { index ->
                        val step = uiState.steps[index]
                        val isActive = uiState.isRunning && index == uiState.steps.size - 1
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + expandVertically()
                        ) {
                            StepCard(step = step, isActive = isActive)
                        }
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            } else if (!uiState.isRunning) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Enter a task and tap Run",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Gemma will write and execute Python code",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StepCard(step: Step, isActive: Boolean = false) {
    when (step) {
        is Step.GeneratingCode -> {
            StatusCard(
                icon = Icons.Default.AutoAwesome,
                title = "Generating Code",
                subtitle = "Gemma is writing Python code...",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                showProgress = isActive
            )
        }

        is Step.CodeGenerated -> {
            CodeCard(
                code = step.code,
                attempt = step.attempt,
                title = "Generated Code"
            )
        }

        is Step.Executing -> {
            StatusCard(
                icon = Icons.Default.Terminal,
                title = "Executing",
                subtitle = "Running Python code via Chaquopy...",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                showProgress = isActive
            )
        }

        is Step.ExecutionDone -> {
            ExecutionResultCard(result = step.result, attempt = step.attempt)
        }

        is Step.Evaluating -> {
            StatusCard(
                icon = Icons.Default.RuleFolder,
                title = "Evaluating",
                subtitle = "Gemma is analyzing the output...",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                showProgress = isActive
            )
        }

        is Step.EvaluationResult -> {
            EvaluationResultCard(isSuccess = step.isSuccess, feedback = step.feedback, attempt = step.attempt)
        }

        is Step.Success -> {
            SuccessCard(finalCode = step.finalCode, output = step.output, attempts = step.attempts)
        }

        is Step.Failed -> {
            FailureCard(attempts = step.attempts, lastError = step.lastError)
        }

        is Step.Rewriting -> {
            RewritingCard(feedback = step.feedback, attempt = step.attempt)
        }
    }
}

@Composable
fun StatusCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    containerColor: Color,
    contentColor: Color,
    showProgress: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = contentColor,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun CodeCard(code: String, attempt: Int, title: String) {
    val codeBackground = Color(0xFF1E1E2E)  // Dark code background
    val codeText = Color(0xFFCDD6F4)         // Catppuccin text color
    val keywordColor = Color(0xFFCBA6F7)     // Purple for keywords

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(codeBackground.copy(alpha = 0.8f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = keywordColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$title (Attempt $attempt)",
                    style = MaterialTheme.typography.labelMedium,
                    color = codeText,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "python",
                    style = MaterialTheme.typography.labelSmall,
                    color = keywordColor.copy(alpha = 0.7f)
                )
            }

            // Code content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(codeBackground)
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = codeText,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun ExecutionResultCard(result: com.gemmathon.ExecutionResult, attempt: Int) {
    val terminalBg = Color(0xFF0D1117)
    val stdoutColor = Color(0xFF3FB950)    // GitHub green
    val stderrColor = Color(0xFFF85149)    // GitHub red
    val metaColor = Color(0xFF8B949E)      // GitHub gray

    val hasError = result.exitCode != 0 || result.stderr.isNotBlank()
    val cardColor = if (hasError)
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    else
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(terminalBg)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = metaColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Terminal Output (Attempt $attempt)",
                    style = MaterialTheme.typography.labelMedium,
                    color = metaColor,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "exit: ${result.exitCode} | ${result.durationMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasError) stderrColor else stdoutColor
                )
            }

            // Terminal content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(terminalBg)
                    .horizontalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (result.stdout.isNotBlank()) {
                        Text(
                            text = "$ stdout:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = metaColor
                        )
                        Text(
                            text = result.stdout,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = stdoutColor
                        )
                        if (result.stderr.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    if (result.stderr.isNotBlank()) {
                        Text(
                            text = "$ stderr:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = metaColor
                        )
                        Text(
                            text = result.stderr,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = stderrColor
                        )
                    }
                    if (result.stdout.isBlank() && result.stderr.isBlank()) {
                        Text(
                            text = "(no output)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = metaColor.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SuccessCard(finalCode: String, output: String, attempts: Int) {
    val successGreen = Color(0xFF16A34A)
    val successBg = Color(0xFFDCFCE7)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = successBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = successGreen,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Task Completed Successfully!",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = successGreen
                    )
                    Text(
                        text = "Completed in $attempts attempt${if (attempts > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = successGreen.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Final Output:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = successGreen
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF052E16))
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = output.ifBlank { "(no output — code ran without printing anything)" },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = if (output.isBlank()) Color(0xFF86EFAC).copy(alpha = 0.4f) else Color(0xFF86EFAC),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
fun FailureCard(attempts: Int, lastError: String) {
    val errorRed = Color(0xFFDC2626)
    val errorBg = Color(0xFFFEF2F2)
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = errorBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = errorRed,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Task Failed",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = errorRed
                    )
                    Text(
                        text = "After $attempts attempt${if (attempts > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = errorRed.copy(alpha = 0.8f)
                    )
                }
            }

            if (lastError.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Last Error:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = errorRed
                    )
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("error", lastError))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy error",
                            tint = errorRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = lastError,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = errorRed.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
fun TuningResultsCard(results: List<TuningResult>, onApplyProfile: (TuningProfile) -> Unit) {
    val bestResult = results.filter { it.success }.minByOrNull { it.attempts }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Parameter Test Results",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Header row
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Profile",  style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.8f))
                Text("Result",   style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Attempts", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.weight(1f))
            }

            results.forEach { result ->
                val isBest = result == bestResult
                val rowBg = if (isBest) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                var expanded by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = rowBg)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = !expanded }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1.8f)) {
                                Text(result.profile.name, style = MaterialTheme.typography.bodySmall, fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal)
                                Text(
                                    "t=${result.profile.temperature} k=${result.profile.topK} p=${result.profile.topP}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            Text(
                                if (result.success) "✓" else "✗",
                                modifier = Modifier.weight(1f),
                                color = if (result.success) Color(0xFF16A34A) else Color(0xFFDC2626),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (result.success) "${result.attempts}" else "—",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (result.success) {
                                androidx.compose.material3.TextButton(
                                    onClick = { onApplyProfile(result.profile) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
                                ) {
                                    Text("Apply", style = MaterialTheme.typography.labelSmall)
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            Icon(
                                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (expanded) "Collapse" else "Expand",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        AnimatedVisibility(visible = expanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (result.output.isNotBlank()) {
                                    val outputColor = if (result.success) Color(0xFF3FB950) else Color(0xFFF85149)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF0D1117))
                                            .horizontalScroll(rememberScrollState())
                                    ) {
                                        Text(
                                            text = result.output,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                            color = outputColor,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                                if (result.evalFeedback.isNotBlank()) {
                                    Text(
                                        text = "Eval: ${result.evalFeedback}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EvaluationResultCard(isSuccess: Boolean, feedback: String, attempt: Int) {
    val successGreen = Color(0xFF16A34A)
    val errorRed = Color(0xFFDC2626)
    val color = if (isSuccess) successGreen else errorRed
    val bgColor = if (isSuccess) Color(0xFFDCFCE7) else Color(0xFFFEF2F2)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Evaluator: ${if (isSuccess) "Correct" else "Incorrect"} (attempt $attempt)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
            }
            if (feedback.isNotBlank()) {
                Text(
                    text = feedback,
                    style = MaterialTheme.typography.bodySmall,
                    color = color.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
fun RewritingCard(feedback: String, attempt: Int) {
    val amberColor = Color(0xFFD97706)
    val amberBg = MaterialTheme.colorScheme.tertiaryContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = amberBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Rewriting (after attempt $attempt)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = feedback,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}
