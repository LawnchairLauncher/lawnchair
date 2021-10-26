package app.lawnchair

import android.content.Context
import androidx.annotation.Keep
import app.lawnchair.bugreport.LawnchairBugReporter
import com.android.quickstep.QuickstepProcessInitializer

@Keep
class LawnchairProcessInitializer(context: Context) : QuickstepProcessInitializer(context) {

    override fun init(context: Context) {
        super.init(context)
        LawnchairBugReporter.INSTANCE.get(context)
    }
}
