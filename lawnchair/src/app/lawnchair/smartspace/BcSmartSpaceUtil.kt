package app.lawnchair.smartspace

import android.app.ActivityOptions
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.provider.CalendarContract
import android.util.Log
import android.view.View
import app.lawnchair.smartspace.model.SmartspaceAction
import com.android.launcher3.R
import com.android.launcher3.Utilities

object BcSmartSpaceUtil {
    fun getIconDrawable(icon: Icon?, context: Context): Drawable? {
        if (icon == null) return null
        val drawable = icon.loadDrawable(context) ?: return null
        val iconSize =
            context.resources.getDimensionPixelSize(R.dimen.enhanced_smartspace_icon_size)
        drawable.setBounds(0, 0, iconSize, iconSize)
        return drawable
    }

    fun setOnClickListener(
        view: View?,
        action: SmartspaceAction?,
        onClickListener: View.OnClickListener? = null,
        str: String?,
    ) {
        val options = ActivityOptions.makeBasic()
        if (Utilities.ATLEAST_U) {
            options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
            )
        }
        if (view == null || action == null) {
            Log.e(str, "No tap action can be set up")
            return
        }
        view.setOnClickListener {
            runCatching {
                if (action.intent != null) {
                    view.context.startActivity(action.intent)
                } else if (action.pendingIntent != null) {
                    if (Utilities.ATLEAST_U) {
                        action.pendingIntent.send(options.toBundle())
                    } else {
                        action.pendingIntent.send()
                    }
                } else if (action.onClick != null) {
                    action.onClick.run()
                }
                onClickListener?.onClick(view)
            }
        }
    }

    fun getOpenCalendarIntent(): Intent {
        return Intent(Intent.ACTION_VIEW).setData(
            ContentUris.appendId(
                CalendarContract.CONTENT_URI.buildUpon().appendPath("time"),
                System.currentTimeMillis(),
            ).build(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }
}
