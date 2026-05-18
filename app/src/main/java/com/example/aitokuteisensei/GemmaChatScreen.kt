package com.example.aitokuteisensei

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Simple data class to represent a chat bubble
data class ChatMessage(val text: String, val isUser: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GemmaChatScreen(modelAbsolutePath: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Instantiate our LiteRT manager
    val chatEngine = remember { GemmaChatEngine(context) }

    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoadingModel by remember { mutableStateOf(true) }
    var isGeneratingText by remember { mutableStateOf(false) }

    // Initialize the model safely in the background on startup
    LaunchedEffect(Unit) {
        try {
            chatEngine.initialize(modelAbsolutePath)
        } catch (e: Exception) {
            messages = messages + ChatMessage("Error loading model: ${e.localizedMessage}", false)
        } finally {
            isLoadingModel = false
        }
    }

    // Clean up memory resources when screen leaves composition
    DisposableEffect(Unit) {
        onDispose { chatEngine.close() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Gemma 4 E2B Chat (LiteRT)") }) }
    ) { paddingValues ->
        if (isLoadingModel) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Loading Gemma Model into memory...")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                // Scrollable Message List
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg ->
                        ChatBubble(msg)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // User Input Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask Gemma something...") },
                        enabled = !isGeneratingText
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val userPrompt = inputText.trim()
                            if (userPrompt.isNotEmpty()) {
                                inputText = ""
                                isGeneratingText = true

                                messages = messages + ChatMessage(userPrompt, true)

                                var gemmaResponse = ""
                                messages = messages + ChatMessage(gemmaResponse, false)

                                scope.launch {
                                    chatEngine.sendMessageStream(userPrompt).collect { token ->
                                        gemmaResponse += token
                                        messages = messages.toMutableList().apply {
                                            set(lastIndex, ChatMessage(gemmaResponse, false))
                                        }
                                    }
                                    isGeneratingText = false
                                }
                            }
                        },
                        enabled = inputText.isNotBlank() && !isGeneratingText
                    ) {
                        Text("Send")
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Text(
            text = message.text,
            modifier = Modifier
                .background(bubbleColor, shape = RoundedCornerShape(12.dp))
                .padding(12.dp),
            color = if (message.isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
