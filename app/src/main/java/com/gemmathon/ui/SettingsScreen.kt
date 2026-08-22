package com.gemmathon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gemmathon.DEFAULT_CODE_GEN_PROMPT
import com.gemmathon.DEFAULT_EVAL_PROMPT
import com.gemmathon.DEFAULT_REWRITE_PROMPT
import com.gemmathon.InferenceBackend
import com.gemmathon.Settings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onBack: () -> Unit
) {
    var local by remember(settings) { mutableStateOf(settings) }

    fun save(updated: Settings) {
        local = updated
        onSettingsChange(updated)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ── Inference Parameters ──────────────────────────────────────
            SectionHeader("Inference Parameters")

            LabeledSlider(
                label = "Temperature",
                value = local.temperature,
                valueText = "%.2f".format(local.temperature),
                range = 0f..2f,
                onValueChangeFinished = { save(local.copy(temperature = it)) }
            )

            LabeledSlider(
                label = "Top-K",
                value = local.topK.toFloat(),
                valueText = "${local.topK}",
                range = 1f..100f,
                steps = 98,
                onValueChangeFinished = { save(local.copy(topK = it.roundToInt())) }
            )

            LabeledSlider(
                label = "Top-P",
                value = local.topP,
                valueText = "%.2f".format(local.topP),
                range = 0f..1f,
                onValueChangeFinished = { save(local.copy(topP = it)) }
            )

            LabeledSlider(
                label = "Max Tokens",
                value = local.maxTokens.toFloat(),
                valueText = "${local.maxTokens}",
                range = 256f..4096f,
                steps = 14,
                onValueChangeFinished = { save(local.copy(maxTokens = it.roundToInt())) }
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Use fixed random seed", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = local.useFixedSeed,
                    onCheckedChange = { save(local.copy(useFixedSeed = it)) }
                )
            }

            if (local.useFixedSeed) {
                var seedText by remember(local.randomSeed) { mutableStateOf(local.randomSeed.toString()) }
                OutlinedTextField(
                    value = seedText,
                    onValueChange = { v ->
                        seedText = v
                        v.toIntOrNull()?.let { save(local.copy(randomSeed = it)) }
                    },
                    label = { Text("Random Seed") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Execution ─────────────────────────────────────────────────
            SectionHeader("Execution")

            LabeledSlider(
                label = "Max Attempts",
                value = local.maxAttempts.toFloat(),
                valueText = "${local.maxAttempts}",
                range = 1f..10f,
                steps = 8,
                onValueChangeFinished = { save(local.copy(maxAttempts = it.roundToInt())) }
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text("Backend", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = local.backend == InferenceBackend.CPU,
                    onClick = { save(local.copy(backend = InferenceBackend.CPU)) }
                )
                Text("CPU", modifier = Modifier.padding(end = 24.dp))
                RadioButton(
                    selected = local.backend == InferenceBackend.GPU,
                    onClick = { save(local.copy(backend = InferenceBackend.GPU)) }
                )
                Text("GPU")
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "Backend and Max Tokens changes require model reload to take effect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── System Prompts ────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SectionHeader("System Prompts")
                TextButton(onClick = {
                    save(local.copy(
                        codeGenPrompt = DEFAULT_CODE_GEN_PROMPT,
                        evalPrompt = DEFAULT_EVAL_PROMPT,
                        rewritePrompt = DEFAULT_REWRITE_PROMPT
                    ))
                }) {
                    Text("Reset to defaults", style = MaterialTheme.typography.bodySmall)
                }
            }

            PromptField(
                label = "Code Generation",
                value = local.codeGenPrompt,
                onValueChange = { save(local.copy(codeGenPrompt = it)) }
            )

            PromptField(
                label = "Evaluation",
                value = local.evalPrompt,
                onValueChange = { save(local.copy(evalPrompt = it)) },
                warning = "Changing this prompt may break JSON result parsing."
            )

            PromptField(
                label = "Rewrite",
                value = local.rewritePrompt,
                onValueChange = { save(local.copy(rewritePrompt = it)) }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChangeFinished: (Float) -> Unit
) {
    var current by remember(value) { mutableStateOf(value) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (current == value) valueText else
                    if (steps > 0) "${current.roundToInt()}" else "%.2f".format(current),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = current,
            onValueChange = { current = it },
            valueRange = range,
            steps = steps,
            onValueChangeFinished = { onValueChangeFinished(current) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PromptField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    warning: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        if (warning != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error)
                    Text(warning, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 8,
            textStyle = MaterialTheme.typography.bodySmall
        )
    }
}
