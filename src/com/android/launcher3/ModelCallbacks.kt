package com.android.launcher3

import android.animation.AnimatorSet
import android.os.CancellationSignal
import android.os.Trace
import android.util.Log
import android.util.Pair
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import com.android.launcher3.LauncherConstants.TraceEvents.DISPLAY_WORKSPACE_TRACE_METHOD_NAME
import com.android.launcher3.LauncherConstants.TraceEvents.SINGLE_TRACE_COOKIE
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.WorkspaceLayoutManager.FIRST_SCREEN_ID
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.config.FeatureFlags
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ItemInstallQueue
import com.android.launcher3.model.ItemInstallQueue.FLAG_LOADER_RUNNING
import com.android.launcher3.model.ModelUtils.WIDGET_FILTER
import com.android.launcher3.model.ModelUtils.currentScreenContentFilter
import com.android.launcher3.model.StringCache
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.PredictedContainerInfo
import com.android.launcher3.model.data.WorkspaceData
import com.android.launcher3.popup.PopupContainerWithArrow
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.IntArray as LIntArray
import com.android.launcher3.util.IntArray
import com.android.launcher3.util.IntSet as LIntSet
import com.android.launcher3.util.IntSet
import com.android.launcher3.util.ItemInfoMatcher
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.Preconditions
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.TraceHelper
import com.android.launcher3.util.ViewOnDrawExecutor
import com.android.launcher3.widget.model.WidgetsListBaseEntry
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Predicate

class ModelCallbacks(private var launcher: Launcher) : BgDataModel.Callbacks {

    private var activeBindTask = AtomicReference(CancellationSignal())

    var synchronouslyBoundPages = IntSet()
    var pagesToBindSynchronously = IntSet()
    var stringCache: StringCache? = null

    var pendingExecutor: ViewOnDrawExecutor? = null
    var workspaceLoading = true

    /**
     * Refreshes the shortcuts shown on the workspace.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    private fun startBinding() {
        TraceHelper.INSTANCE.beginSection("startBinding")
        // Floating panels (except the full widget sheet) are associated with individual icons. If
        // we are starting a fresh bind, close all such panels as all the icons are about
        // to go away.
        AbstractFloatingView.closeOpenViews(
            launcher,
            true,
            AbstractFloatingView.TYPE_ALL and AbstractFloatingView.TYPE_REBIND_SAFE.inv(),
        )
        workspaceLoading = true

        // Clear the workspace because it's going to be rebound
        launcher.dragController.cancelDrag()
        launcher.workspace.clearDropTargets()
        launcher.workspace.removeAllWorkspaceScreens()
        // Avoid clearing the widget update listeners for staying up-to-date with widget info
        launcher.appWidgetHolder.clearWidgetViews()
        // TODO(b/335141365): Remove this log after the bug is fixed.
        Log.d(
            TAG,
            "startBinding: " +
                "hotseat layout was vertical: ${launcher.hotseat?.isHasVerticalHotseat}" +
                " and is setting to ${launcher.deviceProfile.isVerticalBarLayout}",
        )
        launcher.hotseat?.resetLayout(launcher.deviceProfile.isVerticalBarLayout)
        launcher.startBinding()
        TraceHelper.INSTANCE.endSection()
    }

    private fun onInitialBindComplete(
        boundPages: LIntSet,
        pendingTasks: RunnableList,
        onCompleteSignal: RunnableList,
        workspaceItemCount: Int,
        isBindSync: Boolean,
    ) {
        if (Utilities.ATLEAST_Q) {
            Trace.endAsyncSection(DISPLAY_WORKSPACE_TRACE_METHOD_NAME, SINGLE_TRACE_COOKIE)
        }
        synchronouslyBoundPages = boundPages
        pagesToBindSynchronously = LIntSet()
        clearPendingBinds()
        if (!launcher.isInState(LauncherState.ALL_APPS) && !Flags.enableWorkspaceInflation()) {
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
    private fun finishBindingItems(pagesBoundFirst: IntSet?) {
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
        pagesToBindSynchronously = IntSet()

        // Cache one page worth of icons
        launcher.viewCache.setCacheSize(
            R.layout.folder_application,
            deviceProfile.numFolderColumns * deviceProfile.numFolderRows,
        )
        launcher.viewCache.setCacheSize(R.layout.folder_page, 2)
        TraceHelper.INSTANCE.endSection()
        launcher.workspace.removeExtraEmptyScreen(/* stripEmptyScreens= */ true)
        launcher.workspace.pageIndicator.setPauseScroll(
            /*pause=*/ false,
            deviceProfile.deviceProperties.isTwoPanels,
        )
        launcher.finishBindingItems(pagesBoundFirst)
    }

    /**
     * Clear any pending bind callbacks. This is called when is loader is planning to perform a full
     * rebind from scratch.
     */
    fun clearPendingBinds() {
        pendingExecutor?.cancel() ?: return
        pendingExecutor = null

        // We might have set this flag previously and forgot to clear it.
        launcher.appsView.appsStore.disableDeferUpdatesSilently(
            AllAppsStore.DEFER_UPDATES_NEXT_DRAW
        )
    }

    @UiThread
    override fun bindAllApplications(
        apps: Array<AppInfo>,
        flags: Int,
        packageUserKeytoUidMap: Map<PackageUserKey, Int>,
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
    override fun bindDeepShortcutMap(deepShortcutMapCopy: HashMap<ComponentKey, Int>) {
        launcher.popupDataProvider.setDeepShortcutMap(deepShortcutMapCopy)
    }

    override fun bindIncrementalDownloadProgressUpdated(app: AppInfo) {
        launcher.appsView.appsStore.updateProgressBar(app)
    }

    /**
     * Update the state of a package, typically related to install state. Implementation of the
     * method from LauncherModel.Callbacks.
     */
    override fun bindItemsUpdated(updates: Set<ItemInfo>) {
        val workspace = launcher.workspace
        val itemsToRebind = workspace.updateContainerItems(updates, launcher)
        PopupContainerWithArrow.dismissInvalidPopup(launcher)

        updates
            .mapNotNull { if (it is PredictedContainerInfo) it else null }
            .forEach { launcher.bindPredictedContainerInfo(it) }

        if (itemsToRebind.isEmpty()) return
        workspace.removeItemsByMatcher(ItemInfoMatcher.ofItems(itemsToRebind), false)
        itemsToRebind
            .filter { workspace.isContainerSupported(it.container) }
            .let {
                if (it.isNotEmpty()) {
                    bindItems(it, false)
                }
            }
        workspace.stripEmptyScreens()
    }

    /**
     * A package was uninstalled/updated. We take both the super set of packageNames in addition to
     * specific applications to remove, the reason being that this can be called when a package is
     * updated as well. In that scenario, we only remove specific components from the workspace and
     * hotseat, where as package-removal should clear all items by package name.
     */
    override fun bindWorkspaceComponentsRemoved(matcher: Predicate<ItemInfo?>) {
        launcher.workspace.removeItemsByMatcher(matcher, true)
        launcher.dragController.onAppsRemoved(matcher)
        PopupContainerWithArrow.dismissInvalidPopup(launcher)
    }

    override fun bindAllWidgets(allWidgets: List<WidgetsListBaseEntry>) {
        launcher.widgetPickerDataProvider.setWidgets(allWidgets)
    }

    /** Returns the ids of the workspaces to bind. */
    private fun getPagesToBindSynchronously(orderedScreenIds: IntArray): IntSet {
        // If workspace binding is still in progress, getCurrentPageScreenIds won't be
        // accurate, and we should use mSynchronouslyBoundPages that's set during initial binding.
        val visibleIds =
            when {
                !pagesToBindSynchronously.isEmpty -> pagesToBindSynchronously
                !workspaceLoading -> launcher.workspace.currentPageScreenIds
                else -> synchronouslyBoundPages
            }
        // Launcher IntArray has the same name as Kotlin IntArray
        val result = IntSet()
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
            if (launcher.deviceProfile.deviceProperties.isTwoPanels && actualIds.contains(pairId)) {
                result.add(pairId)
            }
        } else if (
            LauncherAppState.getIDP(launcher).supportedProfiles.any {
                it.deviceProperties.isTwoPanels
            } && actualIds.contains(pairId)
        ) {
            // Add the right panel if left panel is hidden when switching display, due to empty
            // pages being hidden in single panel.
            result.add(pairId)
        }
        return result
    }

    private fun bindScreens(orderedScreenIds: LIntArray) {
        launcher.workspace.pageIndicator.setPauseScroll(
            /*pause=*/ true,
            launcher.deviceProfile.deviceProperties.isTwoPanels,
        )
        val firstScreenPosition = 0
        if (orderedScreenIds.indexOf(FIRST_SCREEN_ID) != firstScreenPosition) {
            orderedScreenIds.removeValue(FIRST_SCREEN_ID)
            orderedScreenIds.add(firstScreenPosition, FIRST_SCREEN_ID)
        } else if (orderedScreenIds.isEmpty) {
            // If there are no screens, we need to have an empty screen
            launcher.workspace.addExtraEmptyScreens()
        }
        bindAddScreens(orderedScreenIds)

        // After we have added all the screens, if the wallpaper was locked to the default state,
        // then notify to indicate that it can be released and a proper wallpaper offset can be
        // computed before the next layout
        launcher.workspace.unlockWallpaperFromDefaultPageOnNextLayout()
    }

    override fun bindItemsAdded(items: List<ItemInfo>) {
        val newScreens = LIntSet()
        val nonAnimatedItems = mutableListOf<ItemInfo>()
        val animatedItems = mutableListOf<ItemInfo>()
        val folderItems = mutableListOf<ItemInfo>()
        val lastScreen =
            items
                .maxByOrNull { if (it.container == CONTAINER_DESKTOP) it.screenId else 0 }
                ?.screenId ?: 0

        items.forEach {
            when (it.container) {
                CONTAINER_HOTSEAT -> nonAnimatedItems.add(it)
                CONTAINER_DESKTOP -> {
                    newScreens.add(it.screenId)
                    if (it.screenId == lastScreen) animatedItems.add(it)
                    else nonAnimatedItems.add(it)
                }
                else -> folderItems.add(it)
            }
        }

        launcher.workspace.mScreenOrder.forEach { newScreens.remove(it) }
        if (!newScreens.isEmpty) bindAddScreens(newScreens.array)

        // We add the items without animation on non-visible pages, and with
        // animations on the new page (which we will try and snap to).
        if (nonAnimatedItems.isNotEmpty()) {
            bindItems(nonAnimatedItems, false)
        }
        if (animatedItems.isNotEmpty()) {
            bindItems(animatedItems, true)
        }

        // Remove the extra empty screen
        launcher.workspace.removeExtraEmptyScreen(false)
    }

    private fun bindAddScreens(orderedScreenIdsArg: LIntArray) {
        var orderedScreenIds = orderedScreenIdsArg
        if (launcher.deviceProfile.deviceProperties.isTwoPanels) {
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
            .filter { screenId -> screenId != FIRST_SCREEN_ID }
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

    override fun bindStringCache(cache: StringCache) {
        stringCache = cache
        launcher.appsView.updateWorkUI()
    }

    /** Bind the items start-end from the list. */
    @VisibleForTesting
    fun bindItems(items: List<ItemInfo>, forceAnimateIcons: Boolean) {
        launcher.bindInflatedItems(
            items.map { Pair.create(it, launcher.itemInflater.inflateItem(it)) },
            if (forceAnimateIcons) AnimatorSet() else null,
        )
    }

    @AnyThread
    override fun bindCompleteModelAsync(itemIdMap: WorkspaceData, isBindingSync: Boolean) {
        val taskTracker = CancellationSignal()
        activeBindTask.getAndSet(taskTracker).cancel()

        val inflater = launcher.itemInflater

        fun executeCallbacksTask(executor: Executor = MAIN_EXECUTOR, task: () -> Unit) {
            executor.execute {
                if (taskTracker.isCanceled) {
                    Log.d(TAG, "Too many consecutive reloads, skipping obsolete data-bind")
                } else {
                    task.invoke()
                }
            }
        }

        // Tries to inflate the items asynchronously and bind. Returns true on success or false if
        // async-binding is not supported in this case
        fun inflateAsyncAndBind(items: List<ItemInfo>, executor: Executor) {
            if (taskTracker.isCanceled) {
                Log.d(TAG, "Too many consecutive reloads, skipping obsolete view inflation")
                return
            }

            val bindItems = items.map { Pair.create(it, inflater.inflateItem(it, null)) }
            if (bindItems.isNotEmpty())
                executeCallbacksTask(executor) { launcher.bindInflatedItems(bindItems, null) }
        }

        fun bindItemsInChunks(items: List<ItemInfo>, chuckSize: Int, executor: Executor) {
            // Bind the workspace items
            val itemCount = items.size
            var i = 0
            while (i < itemCount) {
                val start = i
                val end = (start + chuckSize).coerceAtMost(itemCount)
                executeCallbacksTask(executor) { bindItems(items.subList(start, end), false) }
                i = end
            }
        }

        MAIN_EXECUTOR.execute { clearPendingBinds() }

        val orderedScreenIds = itemIdMap.collectWorkspaceScreens(launcher)
        val currentScreenIds = getPagesToBindSynchronously(orderedScreenIds)

        fun setupPendingBind(pendingExecutor: Executor) {
            executeCallbacksTask(pendingExecutor) { finishBindingItems(currentScreenIds) }
            pendingExecutor.execute {
                ItemInstallQueue.INSTANCE[launcher].resumeModelPush(FLAG_LOADER_RUNNING)
            }
        }

        // Separate the items that are on the current screen, and all the other remaining items
        val currentWorkspaceItems = ArrayList<ItemInfo>()
        val otherWorkspaceItems = ArrayList<ItemInfo>()
        val currentAppWidgets = ArrayList<ItemInfo>()
        val otherAppWidgets = ArrayList<ItemInfo>()

        val currentScreenCheck = currentScreenContentFilter(currentScreenIds)
        itemIdMap.forEach { item: ItemInfo ->
            if (currentScreenCheck.test(item)) {
                (if (WIDGET_FILTER.test(item)) currentAppWidgets else currentWorkspaceItems).add(
                    item
                )
            } else if (item.container == CONTAINER_DESKTOP) {
                (if (WIDGET_FILTER.test(item)) otherAppWidgets else otherWorkspaceItems).add(item)
            }
        }

        sortWorkspaceItemsSpatially(currentWorkspaceItems)
        sortWorkspaceItemsSpatially(otherWorkspaceItems)

        // Tell the workspace that we're about to start binding items
        executeCallbacksTask {
            clearPendingBinds()
            startBinding()
        }

        // Bind workspace screens
        executeCallbacksTask { bindScreens(orderedScreenIds) }

        // Load items on the current page.
        if (Flags.enableWorkspaceInflation()) {
            inflateAsyncAndBind(currentWorkspaceItems, MAIN_EXECUTOR)
            inflateAsyncAndBind(currentAppWidgets, MAIN_EXECUTOR)
        } else {
            bindItemsInChunks(currentWorkspaceItems, ITEMS_CHUNK, MAIN_EXECUTOR)
            bindItemsInChunks(currentAppWidgets, 1, MAIN_EXECUTOR)
        }

        itemIdMap
            .mapNotNull { if (it is PredictedContainerInfo) it else null }
            .forEach { executeCallbacksTask { launcher.bindPredictedContainerInfo(it) } }

        val pendingTasks = RunnableList()
        val pendingExecutor = Executor { pendingTasks.add(it) }

        val onCompleteSignal = RunnableList()
        onCompleteSignal.add { Log.d(TAG, "Calling onCompleteSignal") }

        if (Flags.enableWorkspaceInflation()) {
            Log.d(TAG, "Starting async inflation")
            Executors.MODEL_EXECUTOR.execute {
                inflateAsyncAndBind(otherWorkspaceItems, pendingExecutor)
                inflateAsyncAndBind(otherAppWidgets, pendingExecutor)
                setupPendingBind(pendingExecutor)

                // Wait for the async inflation to complete and then notify the completion
                // signal on UI thread.
                MAIN_EXECUTOR.execute { onCompleteSignal.executeAllAndDestroy() }
            }
        } else {
            Log.d(TAG, "Starting sync inflation")
            bindItemsInChunks(otherWorkspaceItems, ITEMS_CHUNK, pendingExecutor)
            bindItemsInChunks(otherAppWidgets, 1, pendingExecutor)
            setupPendingBind(pendingExecutor)
            onCompleteSignal.executeAllAndDestroy()
        }

        // Only include the first level items on desktop (excluding folder contents) for item count
        val workspaceItemCount =
            currentWorkspaceItems.size +
                otherWorkspaceItems.size +
                currentAppWidgets.size +
                otherAppWidgets.size
        executeCallbacksTask {
            onInitialBindComplete(
                currentScreenIds,
                pendingTasks,
                onCompleteSignal,
                workspaceItemCount,
                isBindingSync,
            )
        }
    }

    /**
     * Sorts the set of items by hotseat, workspace (spatially from top to bottom, left to right)
     */
    private fun sortWorkspaceItemsSpatially(workspaceItems: MutableList<ItemInfo>) {
        val idp = launcher.deviceProfile.inv
        val screenCols = idp.numColumns
        val screenCellCount = idp.numColumns * idp.numRows
        workspaceItems.sortWith { lhs: ItemInfo, rhs: ItemInfo ->
            when {
                // Between containers, order by hotseat, desktop
                lhs.container != rhs.container -> lhs.container.compareTo(rhs.container)

                // Within workspace, order by their spatial position in that container
                lhs.container == CONTAINER_DESKTOP ->
                    compareValuesBy(lhs, rhs) {
                        it.screenId * screenCellCount + it.cellY * screenCols + it.cellX
                    }

                // We currently use the screen id as the rank
                lhs.container == CONTAINER_HOTSEAT -> lhs.screenId.compareTo(rhs.screenId)

                else -> 0
            }
        }
    }

    companion object {
        private const val TAG = "ModelCallbacks"
        private const val ITEMS_CHUNK: Int = 6 // batch size for the workspace icons
    }
}
