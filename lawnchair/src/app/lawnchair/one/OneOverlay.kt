package app.lawnchair.one

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// System accent color - emerald
private val AccentColor = Color(0xFF10B981)
private val BackgroundColor = Color(0xFF121212)
private val SurfaceColor = Color(0xFF1E1E1E)
private val OnSurfaceColor = Color(0xFFE0E0E0)
private val OnSurfaceSecondary = Color(0xFF9E9E9E)

@Composable
fun OneOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val oneAPI = remember { OneAPI(context) }

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(visible) {
        if (visible) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { -it / 4 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { -it / 4 },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundColor.copy(alpha = 0.95f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* Consume clicks */ }
                .imePadding()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                OneHeader(onDismiss = onDismiss)

                // Messages
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            OneEmptyState()
                        }
                    }
                    items(messages) { message ->
                        ChatMessageItem(message)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (isLoading) {
                        item {
                            LoadingIndicator()
                        }
                    }
                }

                // Input
                OneInput(
                    value = inputText,
                    onValueChange = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank() && !isLoading) {
                            val userMessage = inputText.trim()
                            inputText = ""
                            messages.add(ChatMessage(userMessage, isUser = true))

                            scope.launch {
                                isLoading = true
                                val apiMessages = messages.map { msg ->
                                    Message(
                                        role = if (msg.isUser) "user" else "assistant",
                                        content = msg.content
                                    )
                                }

                                oneAPI.sendMessage(apiMessages).fold(
                                    onSuccess = { response ->
                                        messages.add(ChatMessage(response, isUser = false))
                                    },
                                    onFailure = { error ->
                                        messages.add(
                                            ChatMessage(
                                                "Error: ${error.message ?: "Unknown error"}",
                                                isUser = false,
                                                isError = true
                                            )
                                        )
                                    }
                                )
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading && oneAPI.hasApiKey(),
                    focusRequester = focusRequester,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun OneHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "One",
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Default,
                color = OnSurfaceColor
            )
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = OnSurfaceSecondary
            )
        }
    }
}

@Composable
private fun OneEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "One",
            style = TextStyle(
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                color = AccentColor
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Less but better",
            style = TextStyle(
                fontSize = 14.sp,
                color = OnSurfaceSecondary
            )
        )
    }
}

@Composable
private fun ChatMessageItem(message: ChatMessage) {
    val backgroundColor = when {
        message.isError -> Color(0xFF5C1E1E)
        message.isUser -> SurfaceColor
        else -> Color.Transparent
    }

    val textColor = when {
        message.isError -> Color(0xFFEF5350)
        else -> OnSurfaceColor
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = backgroundColor,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.let {
                if (message.isUser) it.padding(start = 48.dp)
                else it.padding(end = 48.dp)
            }
        ) {
            Text(
                text = message.content,
                style = TextStyle(
                    fontSize = 15.sp,
                    color = textColor,
                    lineHeight = 22.sp
                ),
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Row(
        modifier = Modifier.padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = AccentColor,
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Thinking...",
            style = TextStyle(
                fontSize = 13.sp,
                color = OnSurfaceSecondary
            )
        )
    }
}

@Composable
private fun OneInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SurfaceColor,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = OnSurfaceColor
                ),
                cursorBrush = SolidColor(AccentColor),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = if (enabled) "Ask One..." else "Configure API key in settings",
                                style = TextStyle(
                                    fontSize = 15.sp,
                                    color = OnSurfaceSecondary
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )

            IconButton(
                onClick = onSend,
                enabled = enabled && value.isNotBlank(),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled && value.isNotBlank()) AccentColor
                        else Color.Transparent
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (enabled && value.isNotBlank()) Color.White
                           else OnSurfaceSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val isError: Boolean = false
)
