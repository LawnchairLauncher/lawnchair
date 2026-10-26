package app.lawnchair.dock

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.LauncherIcons
import com.android.launcher3.model.data.WorkspaceItemInfo

/**
 * A placeable item that opens the app drawer, like Nova's "Apps" button. Stored as a legacy
 * shortcut so the loader keeps it with its own icon; the intent has no package, so nothing can
 * resolve it and ItemClickHandler intercepts it instead.
 */
object DrawerButton {
    const val ACTION = "app.lawnchair.action.OPEN_APP_DRAWER"

    /** Whether [intent] is the drawer button's. */
    @JvmStatic
    fun isDrawerButton(intent: Intent?): Boolean = intent?.action == ACTION

    /** Intent URI stored in the database for the drawer button. */
    fun intentUri(): String = Intent(ACTION).toUri(0)

    /** Grid glyph on an adaptive background, shaped like the other icons. */
    fun icon(context: Context): BitmapInfo {
        val (bg, fg) = colors(context)
        val glyph = ContextCompat.getDrawable(context, R.drawable.ic_apps)!!.mutate().apply {
            colorFilter = PorterDuffColorFilter(fg, PorterDuff.Mode.SRC_IN)
        }
        val adaptive = AdaptiveIconDrawable(ColorDrawable(bg), InsetDrawable(glyph, 0.3f))
        return LauncherIcons.obtain(context).use { it.createBadgedIconBitmap(adaptive) }
    }

    /** New drawer button item, not yet placed. */
    fun makeItem(context: Context): WorkspaceItemInfo = WorkspaceItemInfo().apply {
        itemType = Favorites.ITEM_TYPE_SHORTCUT
        title = context.getString(R.string.dock_drawer_button)
        intent = Intent(ACTION)
        user = Process.myUserHandle()
        bitmap = icon(context)
    }

    /** Background and glyph colours, from Material You when available. */
    private fun colors(context: Context): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getColor(android.R.color.system_accent1_100) to
                context.getColor(android.R.color.system_accent1_700)
        } else {
            Color.rgb(0xE0, 0xE0, 0xE0) to Color.rgb(0x42, 0x42, 0x42)
        }
}
