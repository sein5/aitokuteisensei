package com.example.aitokuteisensei

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Simple data class representation for the UI layout
data class ChatMessage(val text: String, val isUser: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GemmaChatScreen(modelAbsolutePath: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Instantiate our Room-backed local chat engine manager
    val chatEngine = remember { GemmaChatEngine(context) }

    // Observe local Room database streams dynamically
    val savedMessages by chatEngine.historyFlow.collectAsState(initial = emptyList())

    var inputText by remember { mutableStateOf("") }
    var isLoadingModel by remember { mutableStateOf(true) }
    var isGeneratingText by remember { mutableStateOf(false) }

    // Cache stream updates temporarily before final db insertions
    var streamingResponse by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    // Map DB items dynamically into standard chat bubble configurations
    val uiMessages = remember(savedMessages) {
        savedMessages.map { ChatMessage(text = it.text, isUser = it.isUser) }
    }

    // Automatically follow text growth down the view stream
    LaunchedEffect(uiMessages.size, streamingResponse.length) {
        if (uiMessages.isNotEmpty() || streamingResponse.isNotEmpty()) {
            val totalItems = uiMessages.size + (if (streamingResponse.isNotEmpty()) 1 else 0)
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    // Handle startup parameters safe inside IO dispatchers
    LaunchedEffect(Unit) {
        try {
            chatEngine.initialize(modelAbsolutePath)
            isLoadingModel = false
        } catch (e: Exception) {
            isLoadingModel = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kaigo Study Partner") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    if (uiMessages.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch(Dispatchers.IO) { chatEngine.clearChatHistory() }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear History",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoadingModel) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading Gemma-4-e2b local engine...")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(uiMessages) { message ->
                        ChatBubble(message)
                    }

                    // Feed ongoing local streaming text blocks to screen
                    if (streamingResponse.isNotEmpty()) {
                        item {
                            ChatBubble(ChatMessage(text = streamingResponse, isUser = false))
                        }
                    }

                    if (isGeneratingText && streamingResponse.isEmpty()) {
                        item {
                            Text(
                                text = "Sensei is constructing explanation...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .navigationBarsPadding()
                            .imePadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Ask about nursing care chapters...") },
                            modifier = Modifier.weight(1f),
                            maxLines = 4,
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Using FilledIconButton which correctly accepts the 'enabled' parameter
                        FilledIconButton(
                            onClick = {
                                val userPrompt = inputText
                                inputText = ""
                                isGeneratingText = true
                                streamingResponse = ""

                                scope.launch(Dispatchers.IO) {
                                    try {
                                        chatEngine.sendMessageStream(userPrompt).collect { token ->
                                            withContext(Dispatchers.Main) {
                                                streamingResponse += token
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            streamingResponse += "\n[Generation Error encountered]"
                                        }
                                    } finally {
                                        withContext(Dispatchers.Main) {
                                            streamingResponse = ""
                                            isGeneratingText = false
                                        }
                                    }
                                }
                            },
                            enabled = inputText.isNotBlank() && !isGeneratingText,
                            shape = RoundedCornerShape(24.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = "Send Message")
                        }
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
    val textColor = if (message.isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentWidth(align = if (message.isUser) Alignment.End else Alignment.Start)
                .background(bubbleColor, shape = RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            if (message.isUser) {
                Text(text = message.text, color = textColor, style = MaterialTheme.typography.bodyLarge)
            } else {
                MarkdownText(text = message.text, color = textColor)
            }
        }
    }
}

/**
 * Lightweight, self-contained Markdown interpreter logic block
 */
@Composable
fun MarkdownText(text: String, color: Color, modifier: Modifier = Modifier) {
    val lines = text.split("\n")
    Column(modifier = modifier) {
        lines.forEach { line ->
            when {
                line.startsWith("### ") -> {
                    Text(
                        text = line.removePrefix("### "),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = color,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                line.trim().startsWith("* ") -> {
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("• ", style = MaterialTheme.typography.bodyLarge, color = color, fontWeight = FontWeight.Bold)
                        Text(
                            text = parseInlineMarkdown(line.trim().removePrefix("* ")),
                            style = MaterialTheme.typography.bodyLarge,
                            color = color
                        )
                    }
                }
                else -> {
                    if (line.isNotEmpty()) {
                        Text(
                            text = parseInlineMarkdown(line),
                            style = MaterialTheme.typography.bodyLarge,
                            color = color,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

/**
 * Strips out matching target tags to transform **text** into Bold text layout spans
 */
fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        val parts = text.split("**")
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(part)
                }
            } else {
                append(part)
            }
        }
    }
}