package com.example.aitokuteisensei.ui

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.aitokuteisensei.R
import com.example.aitokuteisensei.data.UserPreferences
import com.example.aitokuteisensei.network.DownloadState
import com.example.aitokuteisensei.network.ModelDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    internalAppModelFile: File,
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { UserPreferences(context) }

    var currentStep by remember { mutableIntStateOf(1) }
    var selectedLanguage by remember { mutableStateOf("en") }

    // Initializing your production ModelDownloader wrapper instance
    val modelDownloader = remember { ModelDownloader() }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }

    val languageOptions = listOf(
        "ja" to "🇯🇵 日本語",
        "en" to "🇺🇸 English",
        "zh" to "🇨🇳 中文",
        "id" to "🇮🇩 Bahasa Indonesia",
        "tl" to "🇵🇭 Tagalog"
    )

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch {
                downloadState = DownloadState.CopyingLocalFile
                val success = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(selectedUri).use { inputStream ->
                            internalAppModelFile.outputStream().use { outputStream ->
                                inputStream?.copyTo(outputStream)
                            }
                        }
                        true
                    } catch (e: Exception) {
                        Log.e("Onboarding", "Local file copy failed: ${e.localizedMessage}")
                        false
                    }
                }
                downloadState = if (success) DownloadState.Success else DownloadState.Error("Failed to import local model file.")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "STEP $currentStep OF 2",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFFFF9800),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )

        LinearProgressIndicator(
            progress = { if (currentStep == 1) 0.5f else 1f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            color = Color(0xFFFF9800),
            trackColor = Color(0xFFFFE0B2)
        )

        Box(modifier = Modifier.weight(1f)) {
            if (currentStep == 1) {
                Column {
                    Text(
                        text = stringResource(R.string.onboarding_welcome),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(languageOptions) { (code, name) ->
                            val isSelected = selectedLanguage == code
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFFFFE0B2) else Color(0xFFF5F5F5),
                                border = if (isSelected) BorderStroke(2.dp, Color(0xFFFF9800)) else null,
                                onClick = {
                                    selectedLanguage = code
                                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
                                }
                            ) {
                                Text(
                                    text = name,
                                    modifier = Modifier.padding(16.dp),
                                    color = Color.Black,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_setup_model),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 32.dp),
                        color = Color.DarkGray
                    )

                    AnimatedContent(
                        targetState = downloadState,
                        label = "DownloadUI",
                        transitionSpec = {
                            fadeIn() with fadeOut() // Smoother explicit transitions to avoid layout pops
                        }
                    ) { state ->
                        when (state) {
                            is DownloadState.Idle, is DownloadState.Error -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (state is DownloadState.Error) {
                                        Text(
                                            text = state.message,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(bottom = 16.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            // Set state to fetching instantly to hide this button structure layout immediately
                                            downloadState = DownloadState.FetchingConfig

                                            scope.launch(Dispatchers.Main) {
                                                try {
                                                    val config = withContext(Dispatchers.IO) {
                                                        modelDownloader.fetchModelConfig()
                                                    }

                                                    // Stream processing collected sequentially safely
                                                    modelDownloader.downloadModelToInternalStorage(
                                                        downloadUrl = config.download_url,
                                                        internalFile = internalAppModelFile
                                                    ).collect { collectiveState ->
                                                        if (collectiveState is DownloadState.Success) {
                                                            downloadState = DownloadState.SavingToPublicDownloads
                                                            val exportSuccess = withContext(Dispatchers.IO) {
                                                                modelDownloader.copyToPublicDownloads(
                                                                    context = context,
                                                                    internalFile = internalAppModelFile,
                                                                    fileName = "gemma-4-e2b.litertlm"
                                                                )
                                                            }
                                                            Log.d("Onboarding", "Public allocation status: $exportSuccess")
                                                            downloadState = DownloadState.Success
                                                        } else {
                                                            // Pass state directly to screen component flow
                                                            downloadState = collectiveState
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    downloadState = DownloadState.Error(e.localizedMessage ?: "Network connection error.")
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Download AI Model (Cloud)")
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    OutlinedButton(
                                        onClick = { filePickerLauncher.launch("*/*") },
                                        border = BorderStroke(1.dp, Color(0xFFFF9800)),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                    ) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFFFF9800))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Select Local Model (.litertlm)", color = Color(0xFFFF9800))
                                    }
                                }
                            }
                            is DownloadState.FetchingConfig -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color(0xFFFF9800))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Fetching model metadata configurations...")
                                }
                            }
                            is DownloadState.Downloading -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    // Use explicit checking or absolute indicators to avoid blinking components
                                    CircularProgressIndicator(
                                        progress = state.progress,
                                        color = Color(0xFFFF9800)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Downloading: ${(state.progress * 100).toInt()}%",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${state.downloadedBytes / 1024 / 1024}MB / ${state.totalBytes / 1024 / 1024}MB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                            is DownloadState.SavingToPublicDownloads, is DownloadState.CopyingLocalFile -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color(0xFFFF9800))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = if (state is DownloadState.CopyingLocalFile) "Verifying local asset contents..." else "Backing up file into system Downloads...",
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            is DownloadState.Success -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = stringResource(R.string.state_setup_complete),
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentStep > 1) {
                TextButton(
                    onClick = { currentStep-- },
                    enabled = downloadState !is DownloadState.Downloading &&
                            downloadState !is DownloadState.CopyingLocalFile &&
                            downloadState !is DownloadState.SavingToPublicDownloads
                ) {
                    Text(stringResource(R.string.btn_back), color = Color.Gray)
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Button(
                onClick = {
                    if (currentStep == 1) {
                        scope.launch { preferences.saveLanguage(selectedLanguage) }
                        currentStep++
                    } else {
                        scope.launch {
                            preferences.setOnboarded(true)
                            onSetupComplete()
                        }
                    }
                },
                enabled = when (currentStep) {
                    1 -> selectedLanguage.isNotEmpty()
                    2 -> downloadState is DownloadState.Success
                    else -> false
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9800),
                    disabledContainerColor = Color(0xFFE0E0E0)
                )
            ) {
                Text(
                    text = if (currentStep == 1) stringResource(R.string.btn_continue) else stringResource(R.string.btn_start_learning),
                    color = if (when(currentStep) { 1 -> selectedLanguage.isNotEmpty(); 2 -> downloadState is DownloadState.Success; else -> false }) Color.White else Color.Gray
                )
            }
        }
    }
}