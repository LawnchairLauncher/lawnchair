package app.lawnchair.ui.preferences.components.layout

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.ui.util.preview.PreviewLawnchair

@Composable
fun PreferenceSearchScaffold(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    Scaffold(
        modifier = modifier,
        topBar = {
            Surface {
                SearchBar(
                    value,
                    onValueChange,
                    backDispatcher,
                    placeholder = placeholder,
                    actions = actions,
                )
            }
        },
        bottomBar = { BottomSpacer() },
    ) {
        content(it)
    }
}

@Composable
private fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    backDispatcher: OnBackPressedDispatcher?,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .zIndex(1f)
            .statusBarsPadding()
            .padding(vertical = 8.dp)
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(56.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = CircleShape,
            ),
    ) {
        ClickableIcon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            onClick = { backDispatcher?.onBackPressed() },
        )
        Box(modifier = Modifier.weight(1f)) {
            SearchTextField(
                value,
                onValueChange,
            ) {
                if (placeholder != null) {
                    placeholder()
                }
            }
        }
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            Row(
                Modifier.fillMaxHeight(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Crossfade(value != "", label = "Close button animation") {
                    if (it) {
                        ClickableIcon(
                            imageVector = Icons.Rounded.Clear,
                            onClick = { onValueChange("") },
                        )
                    }
                }
                actions()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
) {
    val textStyle: TextStyle = LocalTextStyle.current

    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val mergedTextStyle = textStyle.merge(TextStyle(color = textColor))

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = @Composable { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = remember { MutableInteractionSource() },
                    placeholder = placeholder,
                    colors = OutlinedTextFieldDefaults.colors(),
                    contentPadding = PaddingValues(4.dp),
                    container = {},
                )
            },
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            textStyle = mergedTextStyle,
        )
    }
}

@PreviewLawnchair
@Composable
private fun SearchTextFieldPreview() {
    LawnchairTheme {
        SearchTextField(
            value = "Example",
            onValueChange = {},
            placeholder = { Text("Example placeholder") },
        )
    }
}
