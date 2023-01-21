package app.lawnchair.qsb

import android.content.Context
import android.util.AttributeSet
import com.android.launcher3.qsb.QsbContainerView

class GoogleQsbContainerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : QsbContainerView(context, attrs, defStyleAttr) {

    class QsbFragment : QsbContainerView.QsbFragment() {
        override fun isQsbEnabled(): Boolean = true
    }
}
