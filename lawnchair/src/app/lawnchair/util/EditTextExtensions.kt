package app.lawnchair.util

import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.widget.EditText
import androidx.annotation.ColorInt
import com.android.launcher3.Utilities

object EditTextExtensions {
    @JvmStatic
    fun EditText.setCursorColor(@ColorInt color: Int) {
        if (Utilities.ATLEAST_Q) {
            this.textCursorDrawable?.colorFilter = BlendModeColorFilter(color, BlendMode.SRC)
        }
    }

    @JvmStatic
    fun EditText.setTextSelectHandleColor(@ColorInt color: Int) {
        if (Utilities.ATLEAST_Q) {
            this.apply {
                listOf(textSelectHandle, textSelectHandleLeft, textSelectHandleRight).forEach {
                    it?.colorFilter = BlendModeColorFilter(color, BlendMode.SRC_IN)
                }
            }
        }
    }
}
