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
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.net.Uri
import android.os.UserHandle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import app.lawnchair.LawnchairLauncher
import app.lawnchair.override.CustomizeAppDialog
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.AppDrawerAppListToFolder
import app.lawnchair.views.ComposeBottomSheet
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_TASK
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.LauncherIcons
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.model.data.AppInfo as ModelAppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.util.ApplicationInfoWrapper
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.PackageManagerHelper
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.OptionsPopupView
import java.net.URISyntaxException

class LawnchairShortcut {

    companion object {

        fun showAppDrawerFolderPopup(
            launcher: LawnchairLauncher,
            folderIcon: FolderIcon,
        ): Boolean {
            val folderId = folderIcon.mInfo.id
            if (folderId == ItemInfo.NO_ID) return false

            val bounds = Rect()
            launcher.dragLayer.getDescendantRectRelativeToSelf(folderIcon, bounds)
            // Unlike regular app, folder does not have any code that would allow it to be showing
            // any popup we have to create our own.
            //
            // In the future we should perhaps move this entire code to a dedicated file like
            // LawnchairFolderShortcut.kt
            return OptionsPopupView.show<LawnchairLauncher>(
                launcher,
                RectF(bounds),
                listOf(
                    OptionsPopupView.OptionItem(
                        launcher,
                        R.string.edit_folder,
                        R.drawable.ic_edit,
                        StatsLogManager.LauncherEvent.IGNORE,
                    ) {
                        launcher.startActivity(
                            PreferenceActivity.createIntent(
                                launcher,
                                AppDrawerAppListToFolder(folderId),
                            ),
                        )
                        true
                    },
                ),
                true,
            ) != null
        }

        val CUSTOMIZE =
            SystemShortcut.Factory { activity: LawnchairLauncher, itemInfo, originalView ->
                val prefs2 = PreferenceManager2.getInstance(activity)
                if (prefs2.lockHomeScreen.firstCached()) {
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
            SystemShortcut.Factory { activity: ActivityContext, itemInfo: ItemInfo, view: View ->
                val prefs2 = PreferenceManager2.INSTANCE.get(activity.asContext())
                if (prefs2.lockHomeScreen.firstCached()) {
                    return@Factory null
                }
                if (itemInfo.targetComponent == null) {
                    return@Factory null
                }
                if (ApplicationInfoWrapper(
                        activity.asContext(),
                        itemInfo.targetComponent!!.packageName,
                        itemInfo.user,
                    ).isSystem()
                ) {
                    return@Factory null
                }
                UnInstall(activity, itemInfo, view)
            }

        private val SUPPORTED_STORES = setOf(
            "com.android.vending",
            "com.aurora.store",
            "org.fdroid.fdroid",
            "org.gdroid.gdroid",
            "com.looker.droidify",
            "com.github.librecaptcha.apps.fdroidclient",
        )

        val OPEN_IN_STORE =
            SystemShortcut.Factory { activity: ActivityContext, itemInfo: ItemInfo, originalView: View ->
                if (itemInfo.itemType != ITEM_TYPE_APPLICATION) return@Factory null
                val packageName = itemInfo.targetComponent?.packageName ?: return@Factory null
                val context = activity.asContext()
                val installer = PackageManagerHelper.INSTANCE.get(context)
                    .getAppInstallerPackage(packageName) ?: return@Factory null
                if (installer !in SUPPORTED_STORES) return@Factory null
                OpenInStore(activity, itemInfo, originalView, packageName, installer)
            }

        val PAUSE_APPS = SystemShortcut.Factory { activity: LawnchairLauncher, itemInfo: ItemInfo, originalView: View ->
            val targetCmp = itemInfo.targetComponent
            val packageName = targetCmp?.packageName ?: return@Factory null

            if (ApplicationInfoWrapper(
                    activity.asContext(),
                    packageName,
                    itemInfo.user,
                ).isSuspended()
            ) {
                return@Factory null
            }

            PauseApps(activity, itemInfo, originalView)
        }
    }

    class Customize(
        private val launcher: LawnchairLauncher,
        private val appInfo: ModelAppInfo,
        itemInfo: ItemInfo,
        originalView: View,
    ) : SystemShortcut<LawnchairLauncher>(R.drawable.ic_edit, R.string.action_customize, launcher, itemInfo, originalView) {

        @OptIn(ExperimentalMaterial3Api::class)
        override fun onClick(v: View) {
            val outObj = Array<Any?>(1) { null }
            var icon = Utilities.loadFullDrawableWithoutTheme(launcher, appInfo, 0, 0, outObj)
            if (mItemInfo.screenId != NO_ID && Utilities.ATLEAST_T) {
                val adaptiveIcon = icon as? AdaptiveIconDrawable
                    ?: LauncherIcons.obtain(launcher).use { it.wrapToAdaptiveIcon(icon) }
                if (adaptiveIcon != null) {
                    val themeController = ThemeManager.INSTANCE.get(launcher).themeController
                    themeController?.createThemedAdaptiveIcon(
                        launcher,
                        adaptiveIcon,
                        appInfo.bitmap,
                    )?.let {
                        icon = it
                    }
                }
            }
            val launcherActivityInfo = outObj[0] as LauncherActivityInfo?
            if (launcherActivityInfo != null) {
                val defaultTitle = launcherActivityInfo.label.toString()

                AbstractFloatingView.closeAllOpenViews(launcher)
                ComposeBottomSheet.show(
                    context = launcher,
                    enabledValues = setOf(
                        SheetValue.Hidden,
                        SheetValue.PartiallyExpanded,
                        SheetValue.Expanded,
                    ),
                ) {
                    CustomizeAppDialog(
                        icon = icon,
                        defaultTitle = defaultTitle,
                        componentKey = appInfo.toComponentKey(),
                        onClose = { close(true) },
                    )
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
            val appLabel = ApplicationInfoWrapper(
                context,
                mItemInfo.targetComponent?.packageName ?: "",
                mItemInfo.user,
            ).toString()
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

    class UnInstall(private var target: ActivityContext?, private var itemInfo: ItemInfo?, originalView: View?) :
        SystemShortcut<ActivityContext>(
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

    class OpenInStore(
        target: ActivityContext,
        itemInfo: ItemInfo,
        originalView: View,
        private val packageName: String,
        private val installerPackage: String,
    ) : SystemShortcut<ActivityContext>(
        R.drawable.ic_open_in_store,
        R.string.open_in_store_drop_target_label,
        target,
        itemInfo,
        originalView,
    ) {
        override fun onClick(v: View) {
            dismissTaskMenuView()
            val intent = buildIntent() ?: return
            mTarget.startActivitySafely(v, intent, mItemInfo)
        }

        private fun buildIntent(): Intent? {
            val uri = when (installerPackage) {
                "com.android.vending",
                "org.gdroid.gdroid",
                "com.aurora.store",
                -> "market://details?id=$packageName"

                "org.fdroid.fdroid" -> "https://f-droid.org/packages/$packageName/"

                "com.github.librecaptcha.apps.fdroidclient",
                "com.looker.droidify",
                -> "droidify://details?id=$packageName"

                else -> return null
            }
            return Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(installerPackage)
        }
    }
}
