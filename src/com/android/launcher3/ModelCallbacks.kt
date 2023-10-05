package com.android.launcher3

import androidx.annotation.UiThread
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.PopupContainerWithArrow
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.Preconditions
import com.android.launcher3.widget.model.WidgetsListBaseEntry
import java.util.function.Predicate

class ModelCallbacks(private var launcher: Launcher) : BgDataModel.Callbacks {
    override fun preAddApps() {
        // If there's an undo snackbar, force it to complete to ensure empty screens are removed
        // before trying to add new items.
        launcher.modelWriter.commitDelete()
        val snackbar =
            AbstractFloatingView.getOpenView<AbstractFloatingView>(
                launcher,
                AbstractFloatingView.TYPE_SNACKBAR
            )
        snackbar?.post { snackbar.close(true) }
    }

    @UiThread
    override fun bindAllApplications(
        apps: Array<AppInfo?>?,
        flags: Int,
        packageUserKeytoUidMap: Map<PackageUserKey?, Int?>?
    ) {
        Preconditions.assertUIThread()
        val hadWorkApps = launcher.appsView.shouldShowTabs()
        launcher.appsView.appsStore.setApps(apps, flags, packageUserKeytoUidMap)
        PopupContainerWithArrow.dismissInvalidPopup(launcher)
        if (hadWorkApps != launcher.appsView.shouldShowTabs()) {
            launcher.stateManager.goToState(LauncherState.NORMAL)
        }
    }

    /**
     * Copies LauncherModel's map of activities to shortcut counts to Launcher's. This is necessary
     * because LauncherModel's map is updated in the background, while Launcher runs on the UI.
     */
    override fun bindDeepShortcutMap(deepShortcutMapCopy: HashMap<ComponentKey?, Int?>?) {
        launcher.popupDataProvider.setDeepShortcutMap(deepShortcutMapCopy)
    }

    override fun bindIncrementalDownloadProgressUpdated(app: AppInfo?) {
        launcher.appsView.appsStore.updateProgressBar(app)
    }

    override fun bindWidgetsRestored(widgets: ArrayList<LauncherAppWidgetInfo?>?) {
        launcher.workspace.widgetsRestored(widgets)
    }

    /**
     * Some shortcuts were updated in the background. Implementation of the method from
     * LauncherModel.Callbacks.
     *
     * @param updated list of shortcuts which have changed.
     */
    override fun bindWorkspaceItemsChanged(updated: List<WorkspaceItemInfo?>) {
        if (updated.isNotEmpty()) {
            launcher.workspace.updateWorkspaceItems(updated, launcher)
            PopupContainerWithArrow.dismissInvalidPopup(launcher)
        }
    }

    /**
     * Update the state of a package, typically related to install state. Implementation of the
     * method from LauncherModel.Callbacks.
     */
    override fun bindRestoreItemsChange(updates: HashSet<ItemInfo?>?) {
        launcher.workspace.updateRestoreItems(updates, launcher)
    }

    /**
     * A package was uninstalled/updated. We take both the super set of packageNames in addition to
     * specific applications to remove, the reason being that this can be called when a package is
     * updated as well. In that scenario, we only remove specific components from the workspace and
     * hotseat, where as package-removal should clear all items by package name.
     */
    override fun bindWorkspaceComponentsRemoved(matcher: Predicate<ItemInfo?>?) {
        launcher.workspace.removeItemsByMatcher(matcher)
        launcher.dragController.onAppsRemoved(matcher)
        PopupContainerWithArrow.dismissInvalidPopup(launcher)
    }

    override fun bindAllWidgets(allWidgets: List<WidgetsListBaseEntry?>?) {
        launcher.popupDataProvider.allWidgets = allWidgets
    }
}
