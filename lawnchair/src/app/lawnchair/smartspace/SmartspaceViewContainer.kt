package app.lawnchair.smartspace

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.R
import com.patrykmichalik.preferencemanager.firstBlocking

class SmartspaceViewContainer @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, previewMode: Boolean = false
) : FrameLayout(context, attrs) {

    init {
        val prefs = PreferenceManager2.getInstance(context)
        if (prefs.enableEnhancedSmartspace.firstBlocking()) {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.smartspace_enhanced, this, false) as BcSmartspaceView
            view.previewMode = previewMode
            addView(view)
        } else {
            inflate(context, R.layout.smartspace_legacy, this)
        }
    }
}
