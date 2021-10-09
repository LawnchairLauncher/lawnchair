package app.lawnchair.allapps

import android.content.Context
import android.util.AttributeSet
import com.android.launcher3.ExtendedEditText
import com.android.launcher3.R

class FallbackSearchInputView(context: Context, attrs: AttributeSet?) : ExtendedEditText(context, attrs) {

    override fun show() {
        super.show()
        setBackgroundResource(R.drawable.search_input_fg)
    }
}
