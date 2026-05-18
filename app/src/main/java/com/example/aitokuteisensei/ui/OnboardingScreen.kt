package com.example.aitokuteisensei.ui

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aitokuteisensei.data.UserPreferences
import com.example.aitokuteisensei.network.DownloadState
import com.example.aitokuteisensei.network.ModelDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun OnboardingScreen(internalAppModelFile: File, onSetupComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { UserPreferences(context) }
    val downloader = remember { ModelDownloader() }

    var currentStep by remember { mutableStateOf(1) }
    var selectedLanguage by remember { mutableStateOf("en") }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var totalSizeText by remember { mutableStateOf("4.2 GB") }

    val languages = listOf(
        "cn" to "Chinese (中文)",
        "en" to "English (English)",
        "ind" to "Indonesian (Bahasa Indonesia)",
        "jp" to "Japanese (日本語)",
        "tgl" to "Tagalog (Filipino)"
    )

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                Log.d("GemmaOnboarding", "Local document picker triggered entry selection path: $uri")
                // BUG FIX: Switch explicitly to processing layout instead of leaking download values
                downloadState = DownloadState.CopyingLocalFile
                scope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            internalAppModelFile.parentFile?.mkdirs()
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                internalAppModelFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            Log.d("GemmaOnboarding", "Local file transfer to sandbox successfully committed.")
                            downloadState = DownloadState.Success
                        } catch (e: Exception) {
                            Log.e("GemmaOnboarding", "Error duplicating selected workspace asset source: ${e.localizedMessage}", e)
                            downloadState = DownloadState.Error("Extraction runtime processing error: ${e.localizedMessage}")
                        }
                    }
                }
            } else {
                Log.d("GemmaOnboarding", "Local storage file assembly selection canceled by user.")
            }
        }
    )

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Sensei Setup Wizard",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Step $currentStep of 2",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.weight(1f),
                label = "step_transition"
            ) { step ->
                if (step == 1) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Choose Your Study Language",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(languages.size) { index ->
                                val (code, name) = languages[index]
                                val isSelected = selectedLanguage == code
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedLanguage = code },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(20.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                            modifier = Modifier.weight(1f),
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        RadioButton(selected = isSelected, onClick = { selectedLanguage = code })
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Download LLM Engine Data",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Downloads to internal storage for local inference execution, and leaves a backup in your public Downloads directory.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        when (val state = downloadState) {
                            is DownloadState.Idle -> {
                                Button(
                                    onClick = {
                                        downloadState = DownloadState.FetchingConfig
                                        scope.launch {
                                            runCatching {
                                                val config = downloader.fetchModelConfig()
                                                totalSizeText = "%.1f GB".format(config.filesize_bytes / 1_000_000_000.0)

                                                downloader.downloadModelToInternalStorage(config.download_url, internalAppModelFile)
                                                    .collect { executionState ->
                                                        if (executionState is DownloadState.Success) {
                                                            downloadState = DownloadState.SavingToPublicDownloads
                                                            val successExport = downloader.copyToPublicDownloads(
                                                                context,
                                                                internalAppModelFile,
                                                                "gemma-4-e2b.litertlm"
                                                            )
                                                            if (successExport) {
                                                                downloadState = DownloadState.Success
                                                            } else {
                                                                downloadState = DownloadState.Error("Data compile successfully updated internally, but Scoped Storage prevented duplicate backup export.")
                                                            }
                                                        } else {
                                                            downloadState = executionState
                                                        }
                                                    }
                                            }.onFailure { exception ->
                                                Log.e("GemmaOnboarding", "Active onboarding pipeline block thrown exception", exception)
                                                downloadState = DownloadState.Error("Pipeline Error: ${exception.localizedMessage}")
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Download via Remote URL")
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedButton(
                                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Select Existing Model File")
                                }
                            }
                            is DownloadState.FetchingConfig -> {
                                CircularProgressIndicator()
                                Text("Resolving URL Configurations...", modifier = Modifier.padding(top = 16.dp))
                            }
                            is DownloadState.Downloading -> {
                                val displayPercent = (state.progress * 100).toInt()
                                val mbDownloaded = state.downloadedBytes / 1_024 / 1_024
                                val mbTotal = state.totalBytes / 1_024 / 1_024

                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).padding(horizontal = 16.dp),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Downloading Model: $displayPercent%",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "$mbDownloaded MB / $mbTotal MB compiled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            is DownloadState.SavingToPublicDownloads -> {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Duplicating copy to public Downloads directory...",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                                    textAlign = TextAlign.Center
                                )
                            }
                            is DownloadState.CopyingLocalFile -> {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Extracting data to isolated App Storage...",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Setting up hardware low-latency engine configuration matrix",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            is DownloadState.Success -> {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(64.dp))
                                Text("Model verified and operational!", modifier = Modifier.padding(top = 16.dp), fontWeight = FontWeight.SemiBold)
                            }
                            is DownloadState.Error -> {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { downloadState = DownloadState.Idle }) {
                                    Text("Retry Connection Setup")
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
                        enabled = downloadState !is DownloadState.Downloading && downloadState !is DownloadState.CopyingLocalFile && downloadState !is DownloadState.SavingToPublicDownloads
                    ) {
                        Text("Back")
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
                    }
                ) {
                    Text(if (currentStep == 1) "Continue" else "Finish Setup")
                }
            }
        }
    }
}