package app.lawnchair.ui

import android.util.TypedValue
import android.widget.TextView
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.viewinterop.NoOpUpdate
import app.lawnchair.ui.preferences.views.CustomFontTextView

@Composable
fun <T : TextView> AndroidText(
    textView: T,
    modifier: Modifier = Modifier,
    update: (T) -> Unit = NoOpUpdate,
    style: TextStyle = LocalTextStyle.current
) {
    val mergedStyle = LocalTextStyle.current.merge(style)
    val textColor = toIntColor(mergedStyle.color)
    val textSize = with(LocalDensity.current) { mergedStyle.fontSize.toPx() }
    AndroidView(
        factory = { textView.apply { text = "" } },
        modifier = modifier,
        update = {
            it.setTextColor(textColor)
            it.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
            update(it)
        }
    )
}

@Composable
fun AndroidText(
    modifier: Modifier = Modifier,
    update: (CustomFontTextView) -> Unit = NoOpUpdate,
    style: TextStyle = LocalTextStyle.current
) {
    val context = LocalContext.current
    val textView = remember { CustomFontTextView(context) }
    AndroidText(
        textView = textView,
        modifier = modifier,
        update = update,
        style = style
    )
}

@Composable
fun toIntColor(color: Color): Int {
    return remember(color) {
        val (r, g, b, a) = color.convert(ColorSpaces.Srgb)
        android.graphics.Color.argb((a * 255).toInt(), (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
    }
}
