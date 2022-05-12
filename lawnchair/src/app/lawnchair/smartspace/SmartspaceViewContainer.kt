package app.lawnchair.smartspace

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.R
import com.patrykmichalik.preferencemanager.firstBlocking

class SmartspaceViewContainer @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    init {
        val prefs = PreferenceManager2.getInstance(context)
        if (prefs.enableEnhancedSmartspace.firstBlocking()) {
            inflate(context, R.layout.smartspace_enhanced, this)
        } else {
            inflate(context, R.layout.smartspace_legacy, this)
        }
    }
}
