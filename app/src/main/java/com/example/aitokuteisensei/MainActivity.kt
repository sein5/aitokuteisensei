package com.example.aitokuteisensei

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    // Instantiate our unified state engine
    val chatEngine = remember { GemmaChatEngine(context) }
    var isEngineInitialized by remember { mutableStateOf(false) }

    // Safely handles initialization on a background thread when onboarding completes
    LaunchedEffect(isOnboarded) {
        if (isOnboarded && internalAppModelFile.exists()) {
            chatEngine.initialize(internalAppModelFile)
            isEngineInitialized = true
        }
        isVerificationFinished = true
    }

    if (isVerificationFinished) {
        if (isOnboarded && internalAppModelFile.exists()) {
            if (isEngineInitialized) {
                // Pass the chat engine directly to the overhauled screen layout
                GemmaChatScreen(chatEngine = chatEngine)
            } else {
                // Brief loading state while Gemma loads into memory context
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator(color = androidx.compose.ui.graphics.Color(0xFFFF9800))
                }
            }
        } else {
            OnboardingScreen(
                internalAppModelFile = internalAppModelFile,
                onSetupComplete = { isVerificationFinished = false }
            )
        }
    }
}