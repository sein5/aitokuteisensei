package com.example.aitokuteisensei

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.aitokuteisensei.data.UserPreferences
import com.example.aitokuteisensei.ui.OnboardingScreen
import java.io.File

class MainActivity : androidx.appcompat.app.AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigationResolver()
                }
            }
        }
    }
}

@Composable
fun AppNavigationResolver() {
    val context = LocalContext.current
    val internalAppModelFile = remember { File(context.filesDir, "gemma-4-e2b.litertlm") }
    val preferences = remember { UserPreferences(context) }

    // FIX: Set initial value to null to prevent the onboarding UI from flashing on startup
    val isOnboarded by preferences.isOnboardedFlow.collectAsState(initial = null)
    var isVerificationFinished by remember { mutableStateOf(false) }
    var isEngineInitialized by remember { mutableStateOf(false) }
    var initializationError by remember { mutableStateOf<String?>(null) }

    val chatEngine = remember { GemmaChatEngine(context) }

    LaunchedEffect(isOnboarded) {
        // Wait until DataStore emits the true system configuration state
        if (isOnboarded == null) return@LaunchedEffect

        if (isOnboarded == true && internalAppModelFile.exists()) {
            try {
                val success = chatEngine.initialize(internalAppModelFile)
                isEngineInitialized = success
                if (!success) {
                    initializationError = "Failed to load model backend hooks safely."
                }
            } catch (e: Exception) {
                initializationError = e.localizedMessage ?: "Unknown initialization error"
            }
        }
        isVerificationFinished = true
    }

    // Guard: Keep displaying the loader until the initial configuration read finishes
    if (isOnboarded == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = androidx.compose.ui.graphics.Color(0xFFFF9800))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Loading configurations...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
        return
    }

    if (isVerificationFinished) {
        if (isOnboarded == true && internalAppModelFile.exists()) {
            when {
                isEngineInitialized -> {
                    GemmaChatScreen(chatEngine = chatEngine)
                }
                initializationError != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Initialization Error: $initializationError\nPlease restart the app or redownload the model.",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    // FIX: Replaced pure spinner with descriptive loading text
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = androidx.compose.ui.graphics.Color(0xFFFF9800))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Preparing AI Tutor Engine...\nLoading local weights into system memory.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        } else {
            OnboardingScreen(
                internalAppModelFile = internalAppModelFile,
                onSetupComplete = {
                    isVerificationFinished = false
                    isEngineInitialized = false
                    initializationError = null
                }
            )
        }
    }
}