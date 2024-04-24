package com.android.launcher3

import android.annotation.TargetApi
import android.os.Build
import android.os.Trace
import androidx.annotation.UiThread
import com.android.launcher3.Flags.enableSmartspaceRemovalToggle
import com.android.launcher3.LauncherConstants.TraceEvents
import com.android.launcher3.Utilities.SHOULD_SHOW_FIRST_PAGE_WIDGET
import com.android.launcher3.WorkspaceLayoutManager.FIRST_SCREEN_ID
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.config.FeatureFlags
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.StringCache
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.PopupContainerWithArrow
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.IntArray as LIntArray
import com.android.launcher3.util.IntSet as LIntSet
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.Preconditions
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.TraceHelper
import com.android.launcher3.util.ViewOnDrawExecutor
import com.android.launcher3.widget.PendingAddWidgetInfo
import com.android.launcher3.widget.model.WidgetsListBaseEntry
import java.util.function.Predicate

class ModelCallbacks(private var launcher: Launcher) : BgDataModel.Callbacks {

    var synchronouslyBoundPages = LIntSet()
    var pagesToBindSynchronously = LIntSet()

    private var isFirstPagePinnedItemEnabled =
        (BuildConfig.QSB_ON_FIRST_SCREEN && !enableSmartspaceRemovalToggle())

    var stringCache: StringCache? = null

    var pendingExecutor: ViewOnDrawExecutor? = null

    var workspaceLoading = true

    /**
     * Refreshes the shortcuts shown on the workspace.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    override fun startBinding() {
        TraceHelper.INSTANCE.beginSection("startBinding")
        // Floating panels (except the full widget sheet) are associated with individual icons. If
        // we are starting a fresh bind, close all such panels as all the icons are about
        // to go away.
        AbstractFloatingView.closeOpenViews(
            launcher,
            true,
            AbstractFloatingView.TYPE_ALL and AbstractFloatingView.TYPE_REBIND_SAFE.inv()
        )
        workspaceLoading = true

        // Clear the workspace because it's going to be rebound
        launcher.dragController.cancelDrag()
        launcher.workspace.clearDropTargets()
        launcher.workspace.removeAllWorkspaceScreens()
        // Avoid clearing the widget update listeners for staying up-to-date with widget info
        launcher.appWidgetHolder.clearWidgetViews()
        launcher.hotseat?.resetLayout(launcher.deviceProfile.isVerticalBarLayout)
        TraceHelper.INSTANCE.endSection()
    }

    @TargetApi(Build.VERSION_CODES.S)
    override fun onInitialBindComplete(
        boundPages: LIntSet,
        pendingTasks: RunnableList,
        onCompleteSignal: RunnableList,
        workspaceItemCount: Int,
        isBindSync: Boolean
    ) {
        if (Utilities.ATLEAST_S) {
            Trace.endAsyncSection(
                TraceEvents.DISPLAY_WORKSPACE_TRACE_METHOD_NAME,
                TraceEvents.DISPLAY_WORKSPACE_TRACE_COOKIE
            )
        }
        synchronouslyBoundPages = boundPages
        pagesToBindSynchronously = LIntSet()
        clearPendingBinds()
        if (!launcher.isInState(LauncherState.ALL_APPS)) {
            launcher.appsView.appsStore.enableDeferUpdates(AllAppsStore.DEFER_UPDATES_NEXT_DRAW)
            pendingTasks.add {
                launcher.appsView.appsStore.disableDeferUpdates(
                    AllAppsStore.DEFER_UPDATES_NEXT_DRAW
                )
            }
        }
        val executor =
            ViewOnDrawExecutor(pendingTasks) {
                if (pendingExecutor == it) {
                    pendingExecutor = null
                }
            }
        pendingExecutor = executor

        if (Flags.enableWorkspaceInflation()) {
            // Finish the executor as soon as the pending inflation is completed
            onCompleteSignal.add(executor::markCompleted)
        } else {
            // Pending executor is already completed, wait until first draw to run the tasks
            executor.attachTo(launcher)
        }
        launcher.bindComplete(workspaceItemCount, isBindSync)
    }

    /**
     * Callback saying that there aren't any more items to bind.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    override fun finishBindingItems(pagesBoundFirst: LIntSet?) {
        TraceHelper.INSTANCE.beginSection("finishBindingItems")
        val deviceProfile = launcher.deviceProfile
        launcher.workspace.restoreInstanceStateForRemainingPages()
        workspaceLoading = false
        launcher.processActivityResult()
        val currentPage =
            if (pagesBoundFirst != null && !pagesBoundFirst.isEmpty)
                launcher.workspace.getPageIndexForScreenId(pagesBoundFirst.array[0])
            else PagedView.INVALID_PAGE
        // When undoing the removal of the last item on a page, return to that page.
        // Since we are just resetting the current page without user interaction,
        // override the previous page so we don't log the page switch.
        launcher.workspace.setCurrentPage(currentPage, currentPage /* overridePrevPage */)
        pagesToBindSynchronously = LIntSet()

        // Cache one page worth of icons
        launcher.viewCache.setCacheSize(
            R.layout.folder_application,
            deviceProfile.numFolderColumns * deviceProfile.numFolderRows
        )
        launcher.viewCache.setCacheSize(R.layout.folder_page, 2)
        TraceHelper.INSTANCE.endSection()
        launcher.workspace.removeExtraEmptyScreen(/* stripEmptyScreens= */ true)
        launcher.workspace.pageIndicator.setPauseScroll(/*pause=*/ false, deviceProfile.isTwoPanels)
    }

    /**
     * Clear any pending bind callbacks. This is called when is loader is planning to perform a full
     * rebind from scratch.
     */
    override fun clearPendingBinds() {
        pendingExecutor?.cancel() ?: return
        pendingExecutor = null

        // We might have set this flag previously and forgot to clear it.
        launcher.appsView.appsStore.disableDeferUpdatesSilently(
            AllAppsStore.DEFER_UPDATES_NEXT_DRAW
        )
    }

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
        if (
            hadWorkApps != launcher.appsView.shouldShowTabs() &&
                launcher.stateManager.state == LauncherState.ALL_APPS
        ) {
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

    /** Returns the ids of the workspaces to bind. */
    override fun getPagesToBindSynchronously(orderedScreenIds: LIntArray): LIntSet {
        // If workspace binding is still in progress, getCurrentPageScreenIds won't be
        // accurate, and we should use mSynchronouslyBoundPages that's set during initial binding.
        val visibleIds =
            when {
                !pagesToBindSynchronously.isEmpty -> pagesToBindSynchronously
                !workspaceLoading -> launcher.workspace.currentPageScreenIds
                else -> synchronouslyBoundPages
            }
        // Launcher IntArray has the same name as Kotlin IntArray
        val result = LIntSet()
        if (visibleIds.isEmpty) {
            return result
        }
        val actualIds = orderedScreenIds.clone()
        val firstId = visibleIds.first()
        val pairId = launcher.workspace.getScreenPair(firstId)
        // Double check that actual screenIds contains the visibleId, as empty screens are hidden
        // in single panel.
        if (actualIds.contains(firstId)) {
            result.add(firstId)
            if (launcher.deviceProfile.isTwoPanels && actualIds.contains(pairId)) {
                result.add(pairId)
            }
        } else if (
            LauncherAppState.getIDP(launcher).supportedProfiles.any(DeviceProfile::isTwoPanels) &&
                actualIds.contains(pairId)
        ) {
            // Add the right panel if left panel is hidden when switching display, due to empty
            // pages being hidden in single panel.
            result.add(pairId)
        }
        return result
    }

    override fun bindSmartspaceWidget() {
        val cl: CellLayout? =
            launcher.workspace.getScreenWithId(WorkspaceLayoutManager.FIRST_SCREEN_ID)
        val spanX = InvariantDeviceProfile.INSTANCE.get(launcher).numSearchContainerColumns

        if (cl?.isRegionVacant(0, 0, spanX, 1) != true) {
            return
        }

        val widgetsListBaseEntry: WidgetsListBaseEntry =
            launcher.popupDataProvider.allWidgets.firstOrNull { item: WidgetsListBaseEntry ->
                item.mPkgItem.packageName == BuildConfig.APPLICATION_ID
            }
                ?: return

        val info =
            PendingAddWidgetInfo(
                widgetsListBaseEntry.mWidgets[0].widgetInfo,
                LauncherSettings.Favorites.CONTAINER_DESKTOP
            )
        launcher.addPendingItem(
            info,
            info.container,
            WorkspaceLayoutManager.FIRST_SCREEN_ID,
            intArrayOf(0, 0),
            info.spanX,
            info.spanY
        )
    }

    override fun bindScreens(orderedScreenIds: LIntArray) {
        launcher.workspace.pageIndicator.setPauseScroll(
            /*pause=*/ true,
            launcher.deviceProfile.isTwoPanels
        )
        val firstScreenPosition = 0
        if (
            (isFirstPagePinnedItemEnabled &&
                !SHOULD_SHOW_FIRST_PAGE_WIDGET) &&
                orderedScreenIds.indexOf(FIRST_SCREEN_ID) != firstScreenPosition
        ) {
            orderedScreenIds.removeValue(FIRST_SCREEN_ID)
            orderedScreenIds.add(firstScreenPosition, FIRST_SCREEN_ID)
        } else if (
            (!isFirstPagePinnedItemEnabled ||
                    SHOULD_SHOW_FIRST_PAGE_WIDGET)
            && orderedScreenIds.isEmpty
        ) {
            // If there are no screens, we need to have an empty screen
            launcher.workspace.addExtraEmptyScreens()
        }
        bindAddScreens(orderedScreenIds)

        // After we have added all the screens, if the wallpaper was locked to the default state,
        // then notify to indicate that it can be released and a proper wallpaper offset can be
        // computed before the next layout
        launcher.workspace.unlockWallpaperFromDefaultPageOnNextLayout()
    }

    override fun bindAppsAdded(
        newScreens: LIntArray?,
        addNotAnimated: java.util.ArrayList<ItemInfo?>?,
        addAnimated: java.util.ArrayList<ItemInfo?>?
    ) {
        // Add the new screens
        if (newScreens != null) {
            // newScreens can contain an empty right panel that is already bound, but not known
            // by BgDataModel.
            newScreens.removeAllValues(launcher.workspace.mScreenOrder)
            bindAddScreens(newScreens)
        }

        // We add the items without animation on non-visible pages, and with
        // animations on the new page (which we will try and snap to).
        if (!addNotAnimated.isNullOrEmpty()) {
            launcher.bindItems(addNotAnimated, false)
        }
        if (!addAnimated.isNullOrEmpty()) {
            launcher.bindItems(addAnimated, true)
        }

        // Remove the extra empty screen
        launcher.workspace.removeExtraEmptyScreen(false)
    }

    private fun bindAddScreens(orderedScreenIdsArg: LIntArray) {
        var orderedScreenIds = orderedScreenIdsArg
        if (launcher.deviceProfile.isTwoPanels) {
            if (FeatureFlags.FOLDABLE_SINGLE_PAGE.get()) {
                orderedScreenIds = filterTwoPanelScreenIds(orderedScreenIds)
            } else {
                // Some empty pages might have been removed while the phone was in a single panel
                // mode, so we want to add those empty pages back.
                val screenIds = LIntSet.wrap(orderedScreenIds)
                orderedScreenIds.forEach { screenId: Int ->
                    screenIds.add(launcher.workspace.getScreenPair(screenId))
                }
                orderedScreenIds = screenIds.array
            }
        }
        orderedScreenIds
            .filterNot { screenId ->
                    isFirstPagePinnedItemEnabled &&
                    !SHOULD_SHOW_FIRST_PAGE_WIDGET &&
                    screenId == WorkspaceLayoutManager.FIRST_SCREEN_ID
            }
            .forEach { screenId ->
                launcher.workspace.insertNewWorkspaceScreenBeforeEmptyScreen(screenId)
            }
    }

    /**
     * Remove odd number because they are already included when isTwoPanels and add the pair screen
     * if not present.
     */
    private fun filterTwoPanelScreenIds(orderedScreenIds: LIntArray): LIntArray {
        val screenIds = LIntSet.wrap(orderedScreenIds)
        orderedScreenIds
            .filter { screenId -> screenId % 2 == 1 }
            .forEach { screenId ->
                screenIds.remove(screenId)
                // In case the pair is not added, add it
                if (!launcher.workspace.containsScreenId(screenId - 1)) {
                    screenIds.add(screenId - 1)
                }
            }
        return screenIds.array
    }

    override fun setIsFirstPagePinnedItemEnabled(isFirstPagePinnedItemEnabled: Boolean) {
        this.isFirstPagePinnedItemEnabled = isFirstPagePinnedItemEnabled
        launcher.workspace.bindAndInitFirstWorkspaceScreen()
    }

    override fun bindStringCache(cache: StringCache) {
        stringCache = cache
        launcher.appsView.updateWorkUI()
    }

    fun getIsFirstPagePinnedItemEnabled(): Boolean = isFirstPagePinnedItemEnabled

    override fun getItemInflater() = launcher.itemInflater
}
