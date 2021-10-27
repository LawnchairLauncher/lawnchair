package app.lawnchair

import android.content.Context
import androidx.annotation.Keep
import androidx.arch.core.util.Function
import app.lawnchair.bugreport.LawnchairBugReporter
import app.lawnchair.theme.color.ColorTokens
import com.android.launcher3.Utilities
import com.android.launcher3.icons.ThemedIconDrawable
import com.android.quickstep.QuickstepProcessInitializer

@Keep
class LawnchairProcessInitializer(context: Context) : QuickstepProcessInitializer(context) {

    override fun init(context: Context) {
        super.init(context)
        LawnchairBugReporter.INSTANCE.get(context)
        ThemedIconDrawable.COLORS_LOADER = Function {
            if (Utilities.isDarkTheme(it)) {
                intArrayOf(
                    ColorTokens.Neutral1_800.resolveColor(it),
                    ColorTokens.Accent1_100.resolveColor(it),
                )
            } else {
                intArrayOf(
                    ColorTokens.Accent1_100.resolveColor(it),
                    ColorTokens.Neutral2_700.resolveColor(it),
                )
            }
        }
    }
}
