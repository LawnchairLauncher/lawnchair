/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.text.TextUtils
import android.util.Pair
import androidx.annotation.WorkerThread
import com.android.launcher3.celllayout.CellPosMapper
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.AddWorkspaceItemsTask
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BaseLauncherBinder.BaseLauncherBinderFactory
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.CacheDataUpdatedTask
import com.android.launcher3.model.ItemInstallQueue
import com.android.launcher3.model.LoaderTask
import com.android.launcher3.model.LoaderTask.LoaderTaskFactory
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.model.ModelDelegate
import com.android.launcher3.model.ModelInitializer
import com.android.launcher3.model.ModelLauncherCallbacks
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.ModelWriter
import com.android.launcher3.model.PackageUpdatedTask
import com.android.launcher3.model.ReloadStringCacheTask
import com.android.launcher3.model.ShortcutsChangedTask
import com.android.launcher3.model.UserLockStateChangedTask
import com.android.launcher3.model.UserManagerState
import com.android.launcher3.model.WorkspaceItemSpaceFinder
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.shortcuts.ShortcutRequest
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.Preconditions
import java.io.FileDescriptor
import java.io.PrintWriter
import java.util.concurrent.CancellationException
import java.util.function.Consumer
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider

/**
 * Maintains in-memory state of the Launcher. It is expected that there should be only one
 * LauncherModel object held in a static. Also provide APIs for updating the database state for the
 * Launcher.
 */
@LauncherAppSingleton
class LauncherModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val taskControllerProvider: Provider<ModelTaskController>,
    private val iconCache: IconCache,
    private val prefs: LauncherPrefs,
    private val installQueue: ItemInstallQueue,
    @Named("ICONS_DB") dbFileName: String?,
    initializer: ModelInitializer,
    lifecycle: DaggerSingletonTracker,
    val modelDelegate: ModelDelegate,
    private val mBgAllAppsList: AllAppsList,
    private val mBgDataModel: BgDataModel,
    private val loaderFactory: LoaderTaskFactory,
    private val binderFactory: BaseLauncherBinderFactory,
    private val spaceFinderFactory: Provider<WorkspaceItemSpaceFinder>,
    val modelDbController: ModelDbController,
) {

    private val mCallbacksList = ArrayList<BgDataModel.Callbacks>(1)

    private val mLock = Any()

    private var mLoaderTask: LoaderTask? = null
    private var mIsLoaderTaskRunning = false

    // only allow this once per reboot to reload work apps
    private var mShouldReloadWorkProfile = true

    // Indicates whether the current model data is valid or not.
    // We start off with everything not loaded. After that, we assume that
    // our monitoring of the package manager provides all updates and we never
    // need to do a requery. This is only ever touched from the loader thread.
    private var mModelLoaded = false
    private var mModelDestroyed = false

    fun isModelLoaded() =
        synchronized(mLock) { mModelLoaded && mLoaderTask == null && !mModelDestroyed }

    /**
     * Returns the ID for the last model load. If the load ID doesn't match for a transaction, the
     * transaction should be ignored.
     */
    var lastLoadId: Int = -1
        private set

    // Runnable to check if the shortcuts permission has changed.
    private val mDataValidationCheck = Runnable {
        if (mModelLoaded) {
            modelDelegate.validateData()
        }
    }

    init {
        if (!dbFileName.isNullOrEmpty()) {
            initializer.initialize(this)
        }
        lifecycle.addCloseable { destroy() }
        modelDelegate.init(this, mBgAllAppsList, mBgDataModel)
    }

    fun newModelCallbacks() = ModelLauncherCallbacks(this::enqueueModelUpdateTask)

    /** Adds the provided items to the workspace. */
    fun addAndBindAddedWorkspaceItems(itemList: List<Pair<ItemInfo?, Any?>?>) {
        callbacks.forEach { it.preAddApps() }
        enqueueModelUpdateTask(AddWorkspaceItemsTask(itemList, spaceFinderFactory.get()))
    }

    fun getWriter(
        verifyChanges: Boolean,
        cellPosMapper: CellPosMapper?,
        owner: BgDataModel.Callbacks?,
    ) = ModelWriter(context, this, mBgDataModel, verifyChanges, cellPosMapper, owner)

    /** Called when the icon for an app changes, outside of package event */
    @WorkerThread
    fun onAppIconChanged(packageName: String, user: UserHandle) {
        // Update the icon for the calendar package
        enqueueModelUpdateTask(PackageUpdatedTask(PackageUpdatedTask.OP_UPDATE, user, packageName))
        ShortcutRequest(context, user).forPackage(packageName).query(ShortcutRequest.PINNED).let {
            if (it.isNotEmpty()) {
                enqueueModelUpdateTask(ShortcutsChangedTask(packageName, it, user, false))
            }
        }
    }

    /** Called when the workspace items have drastically changed */
    fun onWorkspaceUiChanged() {
        MODEL_EXECUTOR.execute(modelDelegate::workspaceLoadComplete)
    }

    /** Called when the model is destroyed */
    fun destroy() {
        mModelDestroyed = true
        MODEL_EXECUTOR.execute { modelDelegate.destroy() }
    }

    fun reloadStringCache() {
        enqueueModelUpdateTask(ReloadStringCacheTask(this.modelDelegate))
    }

    /**
     * Called then there use a user event
     *
     * @see UserCache.addUserEventListener
     */
    fun onUserEvent(user: UserHandle, action: String) {
        when (action) {
            Intent.ACTION_MANAGED_PROFILE_AVAILABLE -> {
                if (mShouldReloadWorkProfile) {
                    forceReload()
                } else {
                    enqueueModelUpdateTask(
                        PackageUpdatedTask(PackageUpdatedTask.OP_USER_AVAILABILITY_CHANGE, user)
                    )
                }
                mShouldReloadWorkProfile = false
            }
            Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE -> {
                mShouldReloadWorkProfile = false
                enqueueModelUpdateTask(
                    PackageUpdatedTask(PackageUpdatedTask.OP_USER_AVAILABILITY_CHANGE, user)
                )
            }
            UserCache.ACTION_PROFILE_LOCKED ->
                enqueueModelUpdateTask(UserLockStateChangedTask(user, false))
            UserCache.ACTION_PROFILE_UNLOCKED ->
                enqueueModelUpdateTask(UserLockStateChangedTask(user, true))
            Intent.ACTION_MANAGED_PROFILE_REMOVED -> {
                prefs.put(LauncherPrefs.WORK_EDU_STEP, 0)
                forceReload()
            }
            UserCache.ACTION_PROFILE_ADDED,
            UserCache.ACTION_PROFILE_REMOVED -> forceReload()
            UserCache.ACTION_PROFILE_AVAILABLE,
            UserCache.ACTION_PROFILE_UNAVAILABLE -> {
                // This broadcast is only available when android.os.Flags.allowPrivateProfile() is
                // set. For Work-profile this broadcast will be sent in addition to
                // ACTION_MANAGED_PROFILE_AVAILABLE/UNAVAILABLE. So effectively, this if block only
                // handles the non-work profile case.
                enqueueModelUpdateTask(
                    PackageUpdatedTask(PackageUpdatedTask.OP_USER_AVAILABILITY_CHANGE, user)
                )
            }
        }
    }

    /**
     * Reloads the workspace items from the DB and re-binds the workspace. This should generally not
     * be called as DB updates are automatically followed by UI update
     */
    fun forceReload() {
        synchronized(mLock) {
            // Stop any existing loaders first, so they don't set mModelLoaded to true later
            stopLoader()
            mModelLoaded = false
        }
        rebindCallbacks()
    }

    /** Reloads the model if it is already in use */
    fun reloadIfActive() {
        val wasActive: Boolean
        synchronized(mLock) { wasActive = mModelLoaded || stopLoader() }
        if (wasActive) forceReload()
    }

    /** Rebinds all existing callbacks with already loaded model */
    fun rebindCallbacks() {
        if (hasCallbacks()) {
            startLoader()
        }
    }

    /** Removes an existing callback */
    fun removeCallbacks(callbacks: BgDataModel.Callbacks) {
        synchronized(mCallbacksList) {
            Preconditions.assertUIThread()
            if (mCallbacksList.remove(callbacks)) {
                if (stopLoader()) {
                    // Rebind existing callbacks
                    startLoader()
                }
            }
        }
    }

    /**
     * Adds a callbacks to receive model updates
     *
     * @return true if workspace load was performed synchronously
     */
    fun addCallbacksAndLoad(callbacks: BgDataModel.Callbacks): Boolean {
        synchronized(mLock) {
            addCallbacks(callbacks)
            return startLoader(arrayOf(callbacks))
        }
    }

    /** Adds a callbacks to receive model updates */
    fun addCallbacks(callbacks: BgDataModel.Callbacks) {
        Preconditions.assertUIThread()
        synchronized(mCallbacksList) { mCallbacksList.add(callbacks) }
    }

    /**
     * Starts the loader. Tries to bind {@params synchronousBindPage} synchronously if possible.
     *
     * @return true if the page could be bound synchronously.
     */
    fun startLoader() = startLoader(arrayOf())

    private fun startLoader(newCallbacks: Array<BgDataModel.Callbacks>): Boolean {
        // Enable queue before starting loader. It will get disabled in Launcher#finishBindingItems
        installQueue.pauseModelPush(ItemInstallQueue.FLAG_LOADER_RUNNING)
        synchronized(mLock) {
            // If there is already one running, tell it to stop.
            val wasRunning = stopLoader()
            val bindDirectly = mModelLoaded && !mIsLoaderTaskRunning
            val bindAllCallbacks = wasRunning || !bindDirectly || newCallbacks.isEmpty()
            val callbacksList = if (bindAllCallbacks) callbacks else newCallbacks
            if (callbacksList.isNotEmpty()) {
                // Clear any pending bind-runnables from the synchronized load process.
                callbacksList.forEach { MAIN_EXECUTOR.execute(it::clearPendingBinds) }

                val launcherBinder = binderFactory.createBinder(callbacksList)
                if (bindDirectly) {
                    // Divide the set of loaded items into those that we are binding synchronously,
                    // and everything else that is to be bound normally (asynchronously).
                    launcherBinder.bindWorkspace(bindAllCallbacks, /* isBindSync= */ true)
                    // For now, continue posting the binding of AllApps as there are other
                    // issues that arise from that.
                    launcherBinder.bindAllApps()
                    launcherBinder.bindDeepShortcuts()
                    launcherBinder.bindWidgets()
                    return true
                } else {
                    val task = loaderFactory.newLoaderTask(launcherBinder, UserManagerState())
                    mLoaderTask = task

                    // Always post the loader task, instead of running directly
                    // (even on same thread) so that we exit any nested synchronized blocks
                    MODEL_EXECUTOR.post(task)
                }
            }
        }
        return false
    }

    /**
     * If there is already a loader task running, tell it to stop.
     *
     * @return true if an existing loader was stopped.
     */
    private fun stopLoader(): Boolean {
        synchronized(mLock) {
            val oldTask: LoaderTask? = mLoaderTask
            mLoaderTask = null
            if (oldTask != null) {
                oldTask.stopLocked()
                return true
            }
            return false
        }
    }

    /**
     * Loads the model if not loaded
     *
     * @param callback called with the data model upon successful load or null on model thread.
     */
    fun loadAsync(callback: Consumer<BgDataModel?>) {
        synchronized(mLock) {
            if (!mModelLoaded && !mIsLoaderTaskRunning) {
                startLoader()
            }
        }
        MODEL_EXECUTOR.post { callback.accept(if (isModelLoaded()) mBgDataModel else null) }
    }

    inner class LoaderTransaction(task: LoaderTask) : AutoCloseable {
        private var mTask: LoaderTask? = null

        init {
            synchronized(mLock) {
                if (mLoaderTask !== task) {
                    throw CancellationException("Loader already stopped")
                }
                this@LauncherModel.lastLoadId++
                mTask = task
                mIsLoaderTaskRunning = true
                mModelLoaded = false
            }
        }

        fun commit() {
            synchronized(mLock) {
                // Everything loaded bind the data.
                mModelLoaded = true
            }
        }

        override fun close() {
            synchronized(mLock) {
                // If we are still the last one to be scheduled, remove ourselves.
                if (mLoaderTask === mTask) {
                    mLoaderTask = null
                }
                mIsLoaderTaskRunning = false
            }
        }
    }

    @Throws(CancellationException::class)
    fun beginLoader(task: LoaderTask) = LoaderTransaction(task)

    /**
     * Refreshes the cached shortcuts if the shortcut permission has changed. Current implementation
     * simply reloads the workspace, but it can be optimized to use partial updates similar to
     * [UserCache]
     */
    fun validateModelDataOnResume() {
        MODEL_EXECUTOR.handler.removeCallbacks(mDataValidationCheck)
        MODEL_EXECUTOR.post(mDataValidationCheck)
    }

    /** Called when the icons for packages have been updated in the icon cache. */
    fun onPackageIconsUpdated(updatedPackages: HashSet<String?>, user: UserHandle) {
        // If any package icon has changed (app was updated while launcher was dead),
        // update the corresponding shortcuts.
        enqueueModelUpdateTask(
            CacheDataUpdatedTask(CacheDataUpdatedTask.OP_CACHE_UPDATE, user, updatedPackages)
        )
    }

    /** Called when the labels for the widgets has updated in the icon cache. */
    fun onWidgetLabelsUpdated(updatedPackages: HashSet<String?>, user: UserHandle) {
        enqueueModelUpdateTask { taskController, dataModel, _ ->
            dataModel.widgetsModel.onPackageIconsUpdated(updatedPackages, user)
            taskController.bindUpdatedWidgets(dataModel)
        }
    }

    fun enqueueModelUpdateTask(task: ModelUpdateTask) {
        if (mModelDestroyed) {
            return
        }
        MODEL_EXECUTOR.execute {
            if (!isModelLoaded()) {
                // Loader has not yet run.
                return@execute
            }
            task.execute(taskControllerProvider.get(), mBgDataModel, mBgAllAppsList)
        }
    }

    /**
     * A task to be executed on the current callbacks on the UI thread. If there is no current
     * callbacks, the task is ignored.
     */
    fun interface CallbackTask {
        fun execute(callbacks: BgDataModel.Callbacks)
    }

    fun interface ModelUpdateTask {
        fun execute(taskController: ModelTaskController, dataModel: BgDataModel, apps: AllAppsList)
    }

    fun updateAndBindWorkspaceItem(si: WorkspaceItemInfo, info: ShortcutInfo) {
        enqueueModelUpdateTask { taskController, _, _ ->
            si.updateFromDeepShortcutInfo(info, context)
            iconCache.getShortcutIcon(si, info)
            taskController.getModelWriter().updateItemInDatabase(si)
            taskController.bindUpdatedWorkspaceItems(listOf(si))
        }
    }

    fun refreshAndBindWidgetsAndShortcuts(packageUser: PackageUserKey?) {
        enqueueModelUpdateTask { taskController, dataModel, _ ->
            dataModel.widgetsModel.update(packageUser)
            taskController.bindUpdatedWidgets(dataModel)
        }
    }

    fun dumpState(prefix: String?, fd: FileDescriptor?, writer: PrintWriter, args: Array<String?>) {
        if (args.isNotEmpty() && TextUtils.equals(args[0], "--all")) {
            writer.println(prefix + "All apps list: size=" + mBgAllAppsList.data.size)
            for (info in mBgAllAppsList.data) {
                writer.println(
                    "$prefix   title=\"${info.title}\" bitmapIcon=${info.bitmap.icon} componentName=${info.targetPackage}"
                )
            }
            writer.println()
        }
        modelDelegate.dump(prefix, fd, writer, args)
        mBgDataModel.dump(prefix, fd, writer, args)
    }

    /** Returns true if there are any callbacks attached to the model */
    fun hasCallbacks() = synchronized(mCallbacksList) { mCallbacksList.isNotEmpty() }

    /** Returns an array of currently attached callbacks */
    val callbacks: Array<BgDataModel.Callbacks>
        get() {
            synchronized(mCallbacksList) {
                return mCallbacksList.toTypedArray<BgDataModel.Callbacks>()
            }
        }

    companion object {
        const val TAG = "Launcher.Model"
    }
}
