package app.lawnchair

import android.content.Context
import androidx.annotation.Keep
import app.lawnchair.bugreport.LawnchairBugReporter
import app.lawnchair.theme.color.tokens.ColorTokens
import com.android.launcher3.Utilities
import com.android.launcher3.icons.mono.ThemedIconDelegate
import com.android.quickstep.QuickstepProcessInitializer

@Keep
class LawnchairProcessInitializer : QuickstepProcessInitializer() {

    // pE-TODO(QPR2): CRITICAL: Investigate
    override fun init(context: Context) {
        LawnchairBugReporter.INSTANCE.get(context)
        ThemedIconDelegate.COLORS_LOADER = {
            if (Utilities.isDarkTheme(it)) {
                intArrayOf(
                    ColorTokens.Accent2_800.resolveColor(it),
                    ColorTokens.Accent1_200.resolveColor(it),
                )
            } else {
                intArrayOf(
                    ColorTokens.Accent1_100.resolveColor(it),
                    ColorTokens.Accent1_700.resolveColor(it),
                )
            }
        }
        super.init(context)
    }
}
