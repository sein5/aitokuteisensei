package com.example.aitokuteisensei

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight

// Professional Theme Colors
val PrimaryOrange = Color(0xFFFF9800)
val UserBubbleColor = Color(0xFFFFE0B2) // Light Orange
val AiBubbleColor = Color(0xFFF5F5F5)   // Professional Light Gray

@Composable
fun GemmaChatScreen(chatEngine: GemmaChatEngine) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Repopulates history instantly on startup
    val messages by chatEngine.historyFlow.collectAsState(initial = emptyList())
    val isGenerating by chatEngine.isGenerating.collectAsState()

    // Auto-scroll animation to newest message
    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size + if(isGenerating) 1 else 0)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {

        // Chat History List
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatBubble(
                    text = msg.text,
                    isUser = msg.isUser,
                    modifier = Modifier.animateItem() // Smooth slide-in animation
                )
            }

            if (isGenerating) {
                item {
                    TypingIndicator(modifier = Modifier.animateItem())
                }
            }
        }

        // Input Area
        Surface(
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ask about nursing terms...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryOrange,
                        cursorColor = PrimaryOrange
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                FloatingActionButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isGenerating) {
                            val userMsg = inputText
                            inputText = ""
                            scope.launch {
                                chatEngine.generateResponse(userMsg)
                            }
                        }
                    },
                    containerColor = PrimaryOrange,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun ChatBubble(text: String, isUser: Boolean, modifier: Modifier = Modifier) {
    // Custom Corner Radius (Sharp corner indicates who is speaking)
    val shape = if (isUser) {
        RoundedCornerShape(20.dp, 20.dp, 0.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 0.dp)
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = shape,
            color = if (isUser) UserBubbleColor else AiBubbleColor,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = parseInlineMarkdown(text), // Assuming you keep your Markdown parser
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = Color.Black
            )
        }
    }
}

@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    // Pulsing alpha animation to indicate the AI is thinking
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 0.dp),
            color = AiBubbleColor,
            modifier = Modifier.alpha(alpha)
        ) {
            Text(
                text = "Thinking...",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = Color.DarkGray
            )
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