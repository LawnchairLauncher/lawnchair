package app.lawnchair.qsb

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.widget.ImageButton
import androidx.core.view.isVisible
import com.android.launcher3.R
import com.android.launcher3.qsb.QsbContainerView
import com.android.launcher3.util.Themes

@SuppressLint("AppCompatCustomView")
class AssistantIconView(context: Context, attrs: AttributeSet?) : ImageButton(context, attrs) {

    init {
        val intent = Intent(Intent.ACTION_VOICE_COMMAND)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .setPackage(QsbContainerView.getSearchWidgetPackageName(context))
        if (context.packageManager.resolveActivity(intent, 0) == null) {
            isVisible = false
        }

        setOnClickListener {
            context.startActivity(intent)
        }
    }

    fun setIcon(isGoogle: Boolean) {
        clearColorFilter()
        if (isGoogle) {
            setImageResource(R.drawable.ic_mic_color)
        } else {
            setImageResource(R.drawable.ic_mic_flat)
            setColorFilter(Themes.getColorAccent(context))
        }
    }
}
