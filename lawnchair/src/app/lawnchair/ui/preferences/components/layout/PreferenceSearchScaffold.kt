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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.ContentAlpha
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Surface
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.ui.util.PreviewLawnchair

@Composable
fun PreferenceSearchScaffold(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
            ) {
                SearchBar(
                    value,
                    onValueChange,
                    backDispatcher,
                    placeholder,
                    actions,
                )
                Spacer(modifier = Modifier.requiredHeight(16.dp))
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
    placeholder: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .zIndex(1f)
            .statusBarsPadding()
            .padding(top = 8.dp)
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(56.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.0.dp),
                shape = RoundedCornerShape(100),
            ),
    ) {
        ClickableIcon(
            imageVector = backIcon(),
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
        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
            Row(
                Modifier.fillMaxHeight(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Crossfade (value != "", label = "Close button animation") {
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: @Composable (() -> Unit)? = null,
) {
    val colors = TextFieldDefaults.outlinedTextFieldColors()
    val textStyle: TextStyle = LocalTextStyle.current

    val textColor = colors.textColor(true).value
    val mergedTextStyle = textStyle.merge(TextStyle(color = textColor))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = @Composable { innerTextField ->
                TextFieldDefaults.OutlinedTextFieldDecorationBox(
                    innerTextField = innerTextField,
                    enabled = true,
                    interactionSource = remember { MutableInteractionSource() },
                    singleLine = true,
                    value = value,
                    visualTransformation = VisualTransformation.None,
                    placeholder = placeholder,
                    border = {},
                    contentPadding = PaddingValues(4.dp)
                )
            },
            cursorBrush = SolidColor(colors.cursorColor(false).value),
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
