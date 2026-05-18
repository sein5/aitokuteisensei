package com.example.aitokuteisensei

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.aitokuteisensei.data.ChatMessageEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GemmaChatScreen(chatEngine: GemmaChatEngine) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    val chatHistory by chatEngine.historyFlow.collectAsState(initial = emptyList())
    val isGenerating by chatEngine.isGenerating.collectAsState()
    val currentGeneration by chatEngine.currentGeneration.collectAsState()

    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(chatHistory.size, currentGeneration) {
        val totalItems = chatHistory.size + (if (currentGeneration != null) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    fun handleSendMessage() {
        if (inputText.isNotBlank() && !isGenerating) {
            val userMsg = inputText
            inputText = ""
            keyboardController?.hide()

            scope.launch {
                chatEngine.generateResponse(userMsg)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tokutei Kaigo Tutor", fontWeight = FontWeight.Bold, color = Color.White) },
                actions = {
                    if (chatHistory.isNotEmpty()) {
                        IconButton(onClick = { scope.launch { chatEngine.clearChatHistory() } }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear Session", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFFF9800))
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).background(Color(0xFFF9F9F9))
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (chatHistory.isEmpty() && !isGenerating) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp).align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.School, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Let's study for the Care Worker exam!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Ask me anything about physical care procedures, communication codes, or daily care planning routines.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0B2)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Text("💡 Tip: Try asking \"Explain the steps for safe wheelchair transfers.\"", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE65100), modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState, modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(chatHistory) { message ->
                            ChatBubbleLayout(message)
                        }

                        // Displays the actual real-time typing effect
                        if (isGenerating && currentGeneration != null) {
                            item {
                                ChatBubbleLayout(ChatMessageEntity(text = currentGeneration!!, isUser = false))
                            }
                        } else if (isGenerating) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.CenterStart) {
                                    CircularProgressIndicator(color = Color(0xFFFF9800), modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                                }
                            }
                        }
                    }
                }
            }

            Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp, color = Color.White) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText, onValueChange = { inputText = it },
                        placeholder = { Text("Ask your question...") }, modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { handleSendMessage() }), maxLines = 4
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { handleSendMessage() }, enabled = inputText.isNotBlank() && !isGenerating,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFFFF9800), disabledContainerColor = Color(0xFFE0E0E0))
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send Message", tint = if (inputText.isNotBlank() && !isGenerating) Color.White else Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubbleLayout(message: ChatMessageEntity) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (message.isUser) Color(0xFFFF9800) else Color(0xFFE0E0E0)
    val textColor = if (message.isUser) Color.White else Color.Black
    val shape = if (message.isUser) RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp) else RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Surface(color = bubbleColor, shape = shape, modifier = Modifier.widthIn(max = 280.dp)) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                val lines = message.text.split("\n")
                lines.forEach { line ->
                    when {
                        line.trim().startsWith("* ") -> {
                            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text(text = "• ", style = MaterialTheme.typography.bodyLarge, color = textColor, fontWeight = FontWeight.Bold)
                                Text(text = parseInlineMarkdown(line.trim().removePrefix("* ")), style = MaterialTheme.typography.bodyLarge, color = textColor)
                            }
                        }
                        else -> {
                            if (line.isNotEmpty()) {
                                Text(text = parseInlineMarkdown(line), style = MaterialTheme.typography.bodyLarge, color = textColor, modifier = Modifier.padding(vertical = 1.dp))
                            } else {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        val parts = text.split("**")
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append(part) }
            } else {
                append(part)
            }
        }
    }
}