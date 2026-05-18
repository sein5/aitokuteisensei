package com.example.aitokuteisensei

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    val isOnboarded by preferences.isOnboardedFlow.collectAsState(initial = false)
    var isVerificationFinished by remember { mutableStateOf(false) }
    var isEngineInitialized by remember { mutableStateOf(false) }
    var initializationError by remember { mutableStateOf<String?>(null) }

    val chatEngine = remember { GemmaChatEngine(context) }

    LaunchedEffect(isOnboarded) {
        if (isOnboarded && internalAppModelFile.exists()) {
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

    if (isVerificationFinished) {
        if (isOnboarded && internalAppModelFile.exists()) {
            when {
                isEngineInitialized -> {
                    GemmaChatScreen(chatEngine = chatEngine)
                }
                initializationError != null -> {
                    // Fallback visual message instead of an infinite loading layout loop
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.Text(
                            text = "Initialization Error: $initializationError\nPlease restart the app or redownload the model.",
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                else -> {
                    // Short transient loading view while model maps into system native registers
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        CircularProgressIndicator(color = androidx.compose.ui.graphics.Color(0xFFFF9800))
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