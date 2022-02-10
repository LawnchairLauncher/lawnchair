package app.lawnchair.qsb

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.ImageButton
import androidx.core.view.isVisible
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.R
import com.android.launcher3.util.Themes

@SuppressLint("AppCompatCustomView")
class AssistantIconView(context: Context, attrs: AttributeSet?) : ImageButton(context, attrs) {

    init {
        val provider = QsbLayout.getSearchProvider(context, PreferenceManager.getInstance(context))

        val intent = if (provider.supportVoiceIntent) provider.createVoiceIntent() else null
        if (intent == null || context.packageManager.resolveActivity(intent, 0) == null) {
            isVisible = false
        }

        setOnClickListener {
            context.startActivity(intent)
        }
    }

    fun setIcon(isGoogle: Boolean, themed: Boolean) {
        clearColorFilter()
        if (isGoogle) {
            setThemedIconResource(R.drawable.ic_mic_color, themed)
        } else {
            setImageResource(R.drawable.ic_mic_flat)
            setColorFilter(Themes.getColorAccent(context))
        }
    }
}
