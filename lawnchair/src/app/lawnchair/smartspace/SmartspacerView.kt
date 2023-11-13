package app.lawnchair.smartspace

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.View
import app.lawnchair.launcher
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.Routes
import com.android.launcher3.R
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.views.OptionsPopupView
import com.kieronquinn.app.smartspacer.sdk.client.views.BcSmartspaceView
import com.kieronquinn.app.smartspacer.sdk.client.views.popup.Popup
import com.kieronquinn.app.smartspacer.sdk.client.views.popup.PopupFactory
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceTarget

class SmartspacerView(context: Context, attrs: AttributeSet?) : BcSmartspaceView(context, attrs) {
    init {
        popupFactory = object : PopupFactory {
            override fun createPopup(
                context: Context,
                anchorView: View,
                target: SmartspaceTarget,
                backgroundColor: Int,
                textColour: Int,
                launchIntent: (Intent?) -> Unit,
                dismissAction: ((SmartspaceTarget) -> Unit)?,
                aboutIntent: Intent?,
                feedbackIntent: Intent?,
                settingsIntent: Intent?
            ): Popup {
                val launcher = context.launcher
                val pos = Rect()
                launcher.dragLayer.getDescendantRectRelativeToSelf(anchorView, pos)
                OptionsPopupView.show(launcher, RectF(pos), listOf(getCustomizeOption()), true)
                return object : Popup {
                    override fun dismiss() {

                    }
                }
            }
        }
    }

    private fun getCustomizeOption() = OptionsPopupView.OptionItem(
        context, R.string.customize_button_text, R.drawable.ic_setting,
        StatsLogManager.LauncherEvent.IGNORE
    ) {
        context.startActivity(PreferenceActivity.createIntent(context, "/${Routes.SMARTSPACE}/"))
        true
    }
}
