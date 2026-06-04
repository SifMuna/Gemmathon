package com.gemmathon

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gemmathon.ui.MainScreen
import com.gemmathon.ui.ModelSetupScreen
import com.gemmathon.ui.theme.GemmathonTheme

class MainActivity : ComponentActivity() {
    private val tag = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GemmathonTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: MainViewModel = viewModel()
                    val uiState by viewModel.uiState.collectAsState()

                    val filePickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        uri?.let {
                            Log.d(tag, "Model file selected: $it")
                            viewModel.loadModel(applicationContext, it)
                        }
                    }

                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        Log.d(tag, "Permissions result: $permissions")
                    }

                    when (uiState.modelState) {
                        is ModelState.NotLoaded, is ModelState.Error -> {
                            ModelSetupScreen(
                                modelState = uiState.modelState,
                                onLoadModel = {
                                    requestStoragePermissions(permissionLauncher)
                                    filePickerLauncher.launch("*/*")
                                },
                                onLoadFromDefaultPath = {
                                    val defaultPath = "/sdcard/Download/gemma-4-E2B-it.task"
                                    viewModel.loadModelFromPath(applicationContext, defaultPath)
                                }
                            )
                        }
                        is ModelState.Loading -> {
                            ModelSetupScreen(
                                modelState = uiState.modelState,
                                onLoadModel = {},
                                onLoadFromDefaultPath = {}
                            )
                        }
                        is ModelState.Loaded -> {
                            MainScreen(
                                uiState = uiState,
                                onRunTask = { task -> viewModel.runTask(task) },
                                onReset = { viewModel.reset() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestStoragePermissions(
        launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse("package:${applicationContext.packageName}")
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        } else {
            launcher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            )
        }
    }
}
