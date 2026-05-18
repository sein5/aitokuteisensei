package com.example.aitokuteisensei

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
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
    val history by chatEngine.historyFlow.collectAsState(initial = emptyList())
    val isGenerating by chatEngine.isGenerating.collectAsState()
    val currentGeneration by chatEngine.currentGeneration.collectAsState()

    var textFieldValue by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current

    // FIX: Optimized scrolling engine. Snaps instantly using scrollToItem for ultra-smooth rendering performance
    LaunchedEffect(history.size, currentGeneration) {
        val totalItems = history.size + (if (isGenerating) 1 else 0)
        if (totalItems > 0) {
            listState.scrollToItem(totalItems - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Tokutei Kaigo Tutor", fontWeight = FontWeight.Bold)
                    }
                },
                // FIX: Trash icon clean removal to prevent unwanted accidental deletion sweeps
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(history) { message ->
                    MessageBubbleRow(
                        message = message,
                        onCopy = { clipboardManager.setText(AnnotatedString(message.text)) },
                        onQuote = { textFieldValue = "“${message.text}”\n> $textFieldValue" }
                    )
                }

                if (isGenerating) {
                    item {
                        val currentChunk = currentGeneration
                        if (!currentChunk.isNullOrBlank()) {
                            // Text stream display bubble
                            MessageBubbleRow(
                                message = ChatMessageEntity(text = currentChunk, isUser = false),
                                onCopy = { clipboardManager.setText(AnnotatedString(currentChunk)) },
                                onQuote = { textFieldValue = "“$currentChunk”\n> $textFieldValue" }
                            )
                        } else {
                            // FIX: Displays a pulsating typing element while the backend computes response sequences
                            TypingIndicatorBubble()
                        }
                    }
                }
            }

            // Bottom input bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        placeholder = { Text("Ask your instructor...") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (textFieldValue.trim().isNotEmpty() && !isGenerating) {
                                val userPrompt = textFieldValue.trim()
                                textFieldValue = ""
                                keyboardController?.hide()
                                coroutineScope.launch {
                                    chatEngine.generateResponse(userPrompt)
                                }
                            }
                        })
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (textFieldValue.trim().isNotEmpty() && !isGenerating) {
                                val userPrompt = textFieldValue.trim()
                                textFieldValue = ""
                                keyboardController?.hide()
                                coroutineScope.launch {
                                    chatEngine.generateResponse(userPrompt)
                                }
                            }
                        },
                        enabled = textFieldValue.trim().isNotEmpty() && !isGenerating,
                        modifier = Modifier.background(
                            if (textFieldValue.trim().isNotEmpty() && !isGenerating) Color(0xFFFF9800) else Color.LightGray,
                            shape = CircleShape
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubbleRow(
    message: ChatMessageEntity,
    onCopy: () -> Unit,
    onQuote: () -> Unit
) {
    val isUser = message.isUser
    var showActions by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = if (isUser) Color(0xFFFF9800) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 0.dp,
                        bottomEnd = if (isUser) 0.dp else 16.dp
                    )
                )
                .clickable { showActions = !showActions }
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .widthIn(max = 280.dp)
        ) {
            Text(
                text = parseInlineMarkdown(message.text),
                color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // FIX: Smooth contextual utility actions panel that shows up when a user clicks any message bubble
        AnimatedVisibility(
            visible = showActions,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = { onCopy(); showActions = false }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy text",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = { onQuote(); showActions = false }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = "Quote message",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// FIX: Custom animated typewriter loading dots layout block to replace the blank loading state
@Composable
fun TypingIndicatorBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val dotAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(600, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "d1"
    )
    val dotAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(600, delayMillis = 200, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "d2"
    )
    val dotAlpha3 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(600, delayMillis = 400, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "d3"
    )

    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dotAlpha1), CircleShape))
            Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dotAlpha2), CircleShape))
            Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dotAlpha3), CircleShape))
        }
    }
}

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