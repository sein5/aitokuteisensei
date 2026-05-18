package com.example.aitokuteisensei

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.aitokuteisensei.data.UserPreferences
import com.example.aitokuteisensei.ui.OnboardingScreen
import java.io.File

class MainActivity : ComponentActivity() {
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
    // Isolated application internal storage sandbox location
    val internalAppModelFile = remember { File(context.filesDir, "gemma-4-e2b.litertlm") }
    val preferences = remember { UserPreferences(context) }

    val isOnboarded by preferences.isOnboardedFlow.collectAsState(initial = false)
    var isVerificationFinished by remember { mutableStateOf(false) }

    LaunchedEffect(isOnboarded) {
        isVerificationFinished = true
    }

    if (isVerificationFinished) {
        if (isOnboarded && internalAppModelFile.exists()) {
            // Runs local hardware inference instantly from isolated storage
            GemmaChatScreen(modelAbsolutePath = internalAppModelFile.absolutePath)
        } else {
            OnboardingScreen(
                internalAppModelFile = internalAppModelFile,
                onSetupComplete = { isVerificationFinished = false }
            )
        }
    }
}