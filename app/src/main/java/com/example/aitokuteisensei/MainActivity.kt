package com.example.aitokuteisensei

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.example.aitokuteisensei.ui.theme.AITokuteiSenseiTheme
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
                    ModelSetupResolver()
                }
            }
        }
    }
}

@Composable
fun ModelSetupResolver() {
    val context = LocalContext.current
    // The permanent home for our model inside the app sandbox
    val destinationFile = remember { File(context.filesDir, "gemma-4-e2b.litertlm") }

    var isModelReady by remember { mutableStateOf(destinationFile.exists()) }
    var copyProgressMessage by remember { mutableStateOf("") }

    // File picker contract to select a single document
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                copyProgressMessage = "Copying model from Downloads..."
                // Launch background thread to copy the file safely
                java.lang.Thread {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            destinationFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        isModelReady = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        copyProgressMessage = ""
                    }
                }.start()
            }
        }
    )

    if (isModelReady) {
        // Model file is successfully in internal storage! Fire up the chat screen.
        GemmaChatScreen(modelAbsolutePath = destinationFile.absolutePath)
    } else {
        // Prompt screen requiring the user to point to their downloads folder
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Gemma model not found in app internal storage.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (copyProgressMessage.isNotEmpty()) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(copyProgressMessage)
                } else {
                    Button(onClick = {
                        // Filters files. Use "*/*" because ".litertlm" is a custom extension
                        filePickerLauncher.launch(arrayOf("*/*"))
                    }) {
                        Text("Select gemma-4-e2b from Downloads")
                    }
                }
            }
        }
    }
}
