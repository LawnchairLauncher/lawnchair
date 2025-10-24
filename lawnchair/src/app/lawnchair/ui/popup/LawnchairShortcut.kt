package app.lawnchair.ui.popup

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.AppGlobals
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.SuspendDialogInfo
import android.net.Uri
import android.os.UserHandle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import app.lawnchair.LawnchairLauncher
import app.lawnchair.override.CustomizeAppDialog
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.views.ComposeBottomSheet
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BaseDraggingActivity
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_TASK
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.model.data.AppInfo as ModelAppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.PackageManagerHelper
import com.patrykmichalik.opto.core.firstBlocking
import java.net.URISyntaxException

class LawnchairShortcut {

    companion object {

        val CUSTOMIZE =
            SystemShortcut.Factory { activity: LawnchairLauncher, itemInfo, originalView ->
                if (PreferenceManager2.getInstance(activity).lockHomeScreen.firstBlocking()) {
                    null
                } else {
                    getAppInfo(activity, itemInfo)?.let { Customize(activity, it, itemInfo, originalView) }
                }
            }

        private fun getAppInfo(launcher: LawnchairLauncher, itemInfo: ItemInfo): ModelAppInfo? {
            if (itemInfo is ModelAppInfo) return itemInfo
            if (itemInfo.itemType != ITEM_TYPE_APPLICATION) return null
            val key = ComponentKey(itemInfo.targetComponent, itemInfo.user)
            return launcher.appsView.appsStore.getApp(key)
        }

        val UNINSTALL =
            SystemShortcut.Factory { activity: BaseDraggingActivity, itemInfo: ItemInfo, view: View ->
                if (itemInfo.targetComponent == null) {
                    return@Factory null
                }
                if (PackageManagerHelper.isSystemApp(
                        activity,
                        itemInfo.targetComponent!!.packageName,
                    )
                ) {
                    return@Factory null
                }
                UnInstall(activity, itemInfo, view)
            }

        val PAUSE_APPS = SystemShortcut.Factory { activity: LawnchairLauncher, itemInfo: ItemInfo, originalView: View ->
            val targetCmp = itemInfo.targetComponent
            val packageName = targetCmp?.packageName ?: return@Factory null

            if (PackageManagerHelper(activity).isAppSuspended(packageName, itemInfo.user)) return@Factory null

            PauseApps(activity, itemInfo, originalView)
        }
    }

    class Customize(
        private val launcher: LawnchairLauncher,
        private val appInfo: ModelAppInfo,
        itemInfo: ItemInfo,
        originalView: View,
    ) : SystemShortcut<LawnchairLauncher>(R.drawable.ic_edit, R.string.action_customize, launcher, itemInfo, originalView) {

        override fun onClick(v: View) {
            val outObj = Array<Any?>(1) { null }
            var icon = Utilities.loadFullDrawableWithoutTheme(launcher, appInfo, 0, 0, outObj)
            if (mItemInfo.screenId != NO_ID && icon is BitmapInfo.Extender) {
                icon = icon.getThemedDrawable(launcher)
            }
            val launcherActivityInfo = outObj[0] as LauncherActivityInfo?
            if (launcherActivityInfo != null) {
                val defaultTitle = launcherActivityInfo.label.toString()

                AbstractFloatingView.closeAllOpenViews(launcher)
                ComposeBottomSheet.show(
                    context = launcher,
                    contentPaddings = PaddingValues(bottom = 64.dp),
                ) {
                    CustomizeAppDialog(
                        icon = icon,
                        defaultTitle = defaultTitle,
                        componentKey = appInfo.toComponentKey(),
                    ) { close(true) }
                }
            } else {
                Toast.makeText(launcher, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
                AbstractFloatingView.closeAllOpenViews(launcher)
            }
        }
    }

    class PauseApps(
        target: LawnchairLauncher,
        itemInfo: ItemInfo,
        originalView: View,
    ) : SystemShortcut<LawnchairLauncher>(
        R.drawable.ic_hourglass_top,
        R.string.paused_apps_drop_target_label,
        target,
        itemInfo,
        originalView,
    ) {
        @SuppressLint("NewApi")
        override fun onClick(view: View) {
            val context = view.context
            val appLabel = PackageManagerHelper(context).getApplicationInfo(
                mItemInfo.targetComponent?.packageName ?: "",
                mItemInfo.user,
                0,
            )?.let {
                context.packageManager.getApplicationLabel(
                    it,
                )
            }
            AlertDialog.Builder(context)
                .setIcon(R.drawable.ic_hourglass_top)
                .setTitle(context.getString(R.string.pause_apps_dialog_title, appLabel))
                .setMessage(context.getString(R.string.pause_apps_dialog_message, appLabel))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.pause) { _, _ ->
                    try {
                        AppGlobals.getPackageManager().setPackagesSuspendedAsUser(
                            arrayOf(mItemInfo.targetComponent?.packageName ?: ""),
                            true, null, null,
                            SuspendDialogInfo.Builder()
                                .setIcon(R.drawable.ic_hourglass_top)
                                .setTitle(R.string.paused_apps_dialog_title)
                                .setMessage(R.string.paused_apps_dialog_message)
                                .setNeutralButtonAction(SuspendDialogInfo.BUTTON_ACTION_UNSUSPEND)
                                .build(),
                            0,
                            context.opPackageName,
                            context.userId,
                            mItemInfo.user.identifier,
                        )
                    } catch (e: Throwable) {
                        Log.e("LawnchairShortcut", "Failed to pause app", e)
                    }
                }
                .show()
            AbstractFloatingView.closeAllOpenViews(mTarget)
        }
    }

    class UnInstall(private var target: BaseDraggingActivity?, private var itemInfo: ItemInfo?, originalView: View?) :
        SystemShortcut<BaseDraggingActivity>(
            R.drawable.ic_uninstall_no_shadow,
            R.string.uninstall_drop_target_label,
            target,
            itemInfo,
            originalView,
        ) {

        /**
         * @return the component name that should be uninstalled or null.
         */
        private fun getUninstallTarget(item: ItemInfo?, context: Context): ComponentName? {
            var intent: Intent? = null
            var user: UserHandle? = null
            if (item != null &&
                (item.itemType == ITEM_TYPE_APPLICATION || item.itemType == ITEM_TYPE_TASK)
            ) {
                intent = item.intent
                user = item.user
            }
            if (intent != null) {
                val info: LauncherActivityInfo? =
                    context.getSystemService(LauncherApps::class.java)
                        ?.resolveActivity(intent, user)
                if (info != null && (info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                    return info.componentName
                }
            }
            return null
        }

        override fun onClick(view: View) {
            val cn = getUninstallTarget(itemInfo, view.context)
            if (cn == null) {
                // System applications cannot be installed. For now, show a toast explaining that.
                // We may give them the option of disabling apps this way.
                Toast.makeText(
                    view.context,
                    R.string.uninstall_system_app_text,
                    Toast.LENGTH_SHORT,
                ).show()
                return
            }
            try {
                val intent = Intent.parseUri(
                    view.context.getString(R.string.delete_package_intent),
                    0,
                )
                    .setData(
                        Uri.fromParts(
                            "package",
                            itemInfo?.targetComponent?.packageName,
                            itemInfo?.targetComponent?.className,
                        ),
                    )
                    .putExtra(Intent.EXTRA_USER, itemInfo?.user)
                target?.startActivitySafely(view, intent, itemInfo)
                AbstractFloatingView.closeAllOpenViews(target)
            } catch (e: URISyntaxException) {
                // Do nothing.
            }
        }
    }
}
