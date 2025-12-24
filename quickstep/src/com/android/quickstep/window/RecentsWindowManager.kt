/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.quickstep.window

import android.animation.AnimatorSet
import android.app.ActivityOptions
import android.app.ActivityTaskManager
import android.content.ComponentCallbacks
import android.content.ComponentName
import android.content.Context
import android.content.LocusId
import android.content.pm.ActivityInfo.CONFIG_ORIENTATION
import android.content.pm.ActivityInfo.CONFIG_SCREEN_SIZE
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Display.DEFAULT_DISPLAY
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.RemoteAnimationAdapter
import android.view.RemoteAnimationTarget
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.view.SurfaceControlViewHost
import android.view.View
import android.window.BackEvent
import android.window.DesktopExperienceFlags
import android.window.OnBackInvokedCallback
import android.window.RemoteTransition
import android.window.SplashScreen
import androidx.annotation.UiThread
import androidx.core.animation.addListener
import androidx.core.view.isVisible
import com.android.app.displaylib.PerDisplayInstanceProviderWithTeardown
import com.android.app.displaylib.PerDisplayRepository
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BaseActivity
import com.android.launcher3.Flags.enablePredictiveBackInOverview
import com.android.launcher3.LauncherAnimationRunner
import com.android.launcher3.LauncherAnimationRunner.RemoteAnimationFactory
import com.android.launcher3.LauncherRootView
import com.android.launcher3.QuickstepTransitionManager.RECENTS_LAUNCH_DURATION
import com.android.launcher3.QuickstepTransitionManager.STATUS_BAR_TRANSITION_DURATION
import com.android.launcher3.QuickstepTransitionManager.STATUS_BAR_TRANSITION_PRE_DELAY
import com.android.launcher3.R
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.compat.AccessibilityManagerCompat
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.dagger.WindowContext
import com.android.launcher3.desktop.DesktopRecentsTransitionController
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.statemanager.StateManager.AtomicAnimationFactory
import com.android.launcher3.statemanager.StatefulContainer
import com.android.launcher3.taskbar.TaskbarInteractor
import com.android.launcher3.testing.TestLogging
import com.android.launcher3.testing.shared.TestProtocol.LAUNCHER_ACTIVITY_STOPPED_MESSAGE
import com.android.launcher3.testing.shared.TestProtocol.SEQUENCE_MAIN
import com.android.launcher3.util.ActivityOptionsWrapper
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.Executors
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.ScreenOnTracker
import com.android.launcher3.util.ScreenOnTracker.ScreenOnListener
import com.android.launcher3.util.SystemUiController
import com.android.launcher3.util.WallpaperColorHints
import com.android.launcher3.util.window.WindowManagerProxy
import com.android.launcher3.views.BaseDragLayer
import com.android.launcher3.views.ScrimView
import com.android.quickstep.BaseContainerInterface
import com.android.quickstep.FallbackWindowInterface
import com.android.quickstep.HomeVisibilityState
import com.android.quickstep.OverviewComponentObserver
import com.android.quickstep.RecentsAnimationCallbacks
import com.android.quickstep.RecentsAnimationCallbacks.RecentsAnimationListener
import com.android.quickstep.RecentsAnimationController
import com.android.quickstep.RecentsModel
import com.android.quickstep.RemoteAnimationTargets
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.TaskViewUtils
import com.android.quickstep.dagger.QuickstepBaseAppComponent
import com.android.quickstep.fallback.FallbackRecentsStateController
import com.android.quickstep.fallback.FallbackRecentsView
import com.android.quickstep.fallback.RecentsDragLayer
import com.android.quickstep.fallback.RecentsState
import com.android.quickstep.fallback.RecentsState.Companion.BACKGROUND_APP
import com.android.quickstep.fallback.RecentsState.Companion.BG_LAUNCHER
import com.android.quickstep.fallback.RecentsState.Companion.DEFAULT
import com.android.quickstep.fallback.RecentsState.Companion.MODAL_TASK
import com.android.quickstep.fallback.RecentsState.Companion.OVERVIEW_SPLIT_SELECT
import com.android.quickstep.fallback.toLauncherStateOrdinal
import com.android.quickstep.util.RecentsAtomicAnimationFactory
import com.android.quickstep.util.RecentsWindowProtoLogProxy
import com.android.quickstep.util.SplitSelectStateController
import com.android.quickstep.util.TISBindHelper
import com.android.quickstep.views.OverviewActionsView
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.views.TaskView
import com.android.systemui.animation.back.FlingOnBackAnimationCallback
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.wm.shell.shared.desktopmode.DesktopState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import javax.inject.Inject

/**
 * Class that will manage RecentsView lifecycle within a window and interface correctly where
 * needed. This allows us to run RecentsView in a window where needed.
 *
 * todo: b/365776320,b/365777482
 *
 * To add new protologs, see [RecentsWindowProtoLogProxy]. To enable logging to logcat, see
 * [QuickstepProtoLogGroup.Constants.DEBUG_RECENTS_WINDOW]
 */
class RecentsWindowManager
@AssistedInject
constructor(
    @Assisted windowContext: Context,
    @Assisted private val fallbackWindowInterface: FallbackWindowInterface,
    @Assisted private val recentsWindowTracker: RecentsWindowTracker,
    wallpaperColorHints: WallpaperColorHints,
    private val systemUiProxy: SystemUiProxy,
    private val recentsModel: RecentsModel,
    private val screenOnTracker: ScreenOnTracker,
    private val desktopState: DesktopState,
    private val displayController: DisplayController,
    @Ui private val uiExecutor: LooperExecutor,
) :
    RecentsWindowContext(windowContext, wallpaperColorHints.hints),
    RecentsViewContainer,
    StatefulContainer<RecentsState>,
    ComponentCallbacks {

    companion object {
        private const val HOME_APPEAR_DURATION: Long = 250
        private const val RECENTS_ANIMATION_TIMEOUT = 1000L
        private const val TAG = "RecentsWindowManager"

        @JvmField
        val REPOSITORY_INSTANCE =
            DaggerSingletonObject<PerDisplayRepository<RecentsWindowManager>>(
                QuickstepBaseAppComponent::getRecentsWindowManagerRepository
            )
    }

    protected var recentsView: FallbackRecentsView<RecentsWindowManager>? = null
    private var surfaceControlViewHost: SurfaceControlViewHost? = null
    private var layoutInflater: LayoutInflater = LayoutInflater.from(this).cloneInContext(this)
    private var stateManager: StateManager<RecentsState, RecentsWindowManager> =
        StateManager<RecentsState, RecentsWindowManager>(this, BG_LAUNCHER)
    private var systemUiController: SystemUiController? = null

    private var overviewOverlay: SurfaceControl? = null
    private var dragLayer: RecentsDragLayer<RecentsWindowManager>? = null
    private var windowRootView = RecentsWindowRootView(this)
    private var windowView: View? = null
    private var actionsView: OverviewActionsView<*>? = null
    private var scrimView: ScrimView? = null

    private var callbacks: RecentsAnimationCallbacks? = null

    private var taskbarInteractor: TaskbarInteractor? = null

    private var oldConfiguration: Configuration? = null
    private var oldRotation: Int = -1

    private val tisBindHelper: TISBindHelper = TISBindHelper(this) {}
    private val splitSelectStateController: SplitSelectStateController =
        SplitSelectStateController(
            /* container= */ this,
            stateManager,
            /* depthController= */ null,
            statsLogManager,
            systemUiProxy,
            recentsModel,
            /* activityBackCallback= */ null,
        )

    // Callback array that corresponds to events defined in @ActivityEvent
    private val eventCallbacks =
        listOf(RunnableList(), RunnableList(), RunnableList(), RunnableList())

    val onBackInvokedCallback =
        if (enablePredictiveBackInOverview()) {
            object : FlingOnBackAnimationCallback() {
                override fun onBackInvokedCompat() {
                    Log.d(TAG, "onBackInvokedCompat: displayId=$displayId")
                    stateManager.state.onBackInvoked(this@RecentsWindowManager)
                    TestLogging.recordEvent(SEQUENCE_MAIN, "onBackInvoked")
                }

                override fun onBackStartedCompat(backEvent: BackEvent) {
                    Log.d(TAG, "onBackStartedCompat: displayId=$displayId")
                    stateManager.state.onBackStarted(this@RecentsWindowManager)
                }

                override fun onBackProgressedCompat(backEvent: BackEvent) {
                    Log.d(
                        TAG,
                        "onBackProgressedCompat: displayId=$displayId, progress=${backEvent.progress}",
                    )
                    stateManager.state.onBackProgressed(
                        this@RecentsWindowManager,
                        backEvent.progress,
                    )
                }

                override fun onBackCancelledCompat() {
                    Log.d(TAG, "onBackCancelledCompat: displayId=$displayId")
                }
            }
        } else {
            OnBackInvokedCallback {
                Log.d(TAG, "onBackInvokedCallback: displayId=$displayId")
                stateManager.state.onBackInvoked(this@RecentsWindowManager)
                TestLogging.recordEvent(SEQUENCE_MAIN, "onBackInvoked")
            }
        }

    private val homeVisibilityState = systemUiProxy.homeVisibilityState
    private val homeVisibilityListener =
        object : HomeVisibilityState.VisibilityChangeListener {
            override fun onHomeVisibilityChanged(isVisible: Boolean) {
                if (isShowing() && !isVisible && isInState(DEFAULT)) {
                    // handling state where we end recents animation by swiping livetile away
                    // TODO: animate this switch.
                    hideRecentsWindow()
                }
            }
        }

    private val recentsAnimationListener =
        object : RecentsAnimationListener {
            override fun onRecentsAnimationCanceled(thumbnailDatas: HashMap<Int, ThumbnailData>) {
                recentAnimationStopped()
            }

            override fun onRecentsAnimationFinished(controller: RecentsAnimationController) {
                recentAnimationStopped()
            }
        }

    private val screenChangedListener = ScreenOnListener { isOn ->
        if (!isOn) {
            hideRecentsWindow()
        }
    }

    var activityLaunchAnimationRunner: RemoteAnimationFactory? = null

    init {
        fallbackWindowInterface.setRecentsWindowManager(this)
        if (displayId == DEFAULT_DISPLAY) {
            homeVisibilityState.addListener(homeVisibilityListener)
        }

        if (
            DesktopExperienceFlags.ENABLE_NON_DEFAULT_DISPLAY_SPLIT_BUGFIX.isTrue &&
                displayId != DEFAULT_DISPLAY &&
                desktopState.canEnterDesktopModeOrShowAppHandle
        ) {
            splitSelectStateController.initSplitFromDesktopController(this)
        }

        displayController.addChangeListenerForDisplay(this, displayId)
    }

    fun createWindowView() {
        if (windowView != null) {
            return
        }

        theme.applyStyle(overviewBlurStyleResId, true)
        windowView = layoutInflater.inflate(R.layout.fallback_recents_activity, null)
        windowView?.let {
            actionsView = it.findViewById(R.id.overview_actions_view)
            recentsView =
                it.findViewById<FallbackRecentsView<RecentsWindowManager>?>(R.id.overview_panel)
                    ?.apply {
                        init(
                            actionsView,
                            splitSelectStateController,
                            DesktopRecentsTransitionController(
                                stateManager,
                                systemUiProxy,
                                iApplicationThread,
                                /* depthController= */ null,
                                desktopState,
                            ),
                        )
                    }
            actionsView?.apply {
                updateDimension(getDeviceProfile(), recentsView?.lastComputedTaskSize)
                updateVerticalMargin(DisplayController.getNavigationMode(this@RecentsWindowManager))
            }
            scrimView = it.findViewById(R.id.scrim_view)
            dragLayer = it.findViewById(R.id.drag_layer)

            it.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

            surfaceControlViewHost = SurfaceControlViewHost(this, display, null as IBinder?)
            windowRootView.addView(it)
            surfaceControlViewHost?.let { scvh ->
                scvh.setView(windowRootView, getWindowLayoutParams())
                scvh.surfacePackage?.let { surfacePackage ->
                    getOverviewOverlay()?.let { overviewOverlay ->
                        Transaction()
                            .reparent(surfacePackage.surfaceControl, overviewOverlay)
                            .show(surfacePackage.surfaceControl)
                            .apply(true)
                    }
                        ?: run {
                            Log.e(
                                TAG,
                                "OverviewOverlay is null, can't reparent surface",
                                Exception(),
                            )
                        }
                }
                    ?: run {
                        Log.e(TAG, "SurfaceControlViewHost.SurfacePackage is null", Exception())
                    }
            }

            it.findOnBackInvokedDispatcher()
                ?.registerSystemOnBackInvokedCallback(onBackInvokedCallback)

            recentsWindowTracker.handleCreate(this)
            onViewCreated()
        }
        systemUiController = SystemUiController(windowView)
        registerComponentCallbacks(this)
    }

    override fun destroy() {
        super.destroy()
        displayController.removeChangeListenerForDisplay(this, displayId)
        fallbackWindowInterface.setRecentsWindowManager(null)
        tisBindHelper.onDestroy()
        Executors.MAIN_EXECUTOR.execute {
            onViewDestroyed()
            hideRecentsWindow()
            if (windowView?.parent != null) {
                surfaceControlViewHost?.release()
                surfaceControlViewHost = null
            }
            windowView
                ?.findOnBackInvokedDispatcher()
                ?.unregisterOnBackInvokedCallback(onBackInvokedCallback)
            callbacks?.removeListener(recentsAnimationListener)
            if (displayId == DEFAULT_DISPLAY) {
                homeVisibilityState.removeListener(homeVisibilityListener)
            }
            unregisterComponentCallbacks(this)
            recentsWindowTracker.onContextDestroyed(this)
            recentsView?.destroy()
            recentsView = null
            windowView = null
        }
    }

    override fun handleConfigurationChanged(newConfiguration: Configuration?) {
        val diff = oldConfiguration?.let { newConfiguration?.diff(it) } ?: -1
        val rotation = WindowManagerProxy.INSTANCE[this].getRotation(this)
        if ((diff and (CONFIG_ORIENTATION or CONFIG_SCREEN_SIZE)) != 0 || rotation != oldRotation) {
            onHandleConfigurationChanged(newConfiguration)
        }

        oldConfiguration = newConfiguration
        oldRotation = rotation
    }

    fun onHandleConfigurationChanged(configuration: Configuration?) {
        initDeviceProfile()
        AbstractFloatingView.closeOpenViews(
            this,
            true,
            AbstractFloatingView.TYPE_ALL and AbstractFloatingView.TYPE_REBIND_SAFE.inv(),
        )
        dispatchDeviceProfileChanged()

        (windowView as LauncherRootView?)?.dispatchInsets()
        getStateManager().reapplyState(true /* cancelCurrentAnimation */)
        dragLayer?.recreateControllers()
    }

    override fun onDisplayInfoChanged(
        context: Context?,
        info: DisplayController.Info?,
        flags: Int,
    ) {
        initDeviceProfile()
        surfaceControlViewHost?.relayout(getWindowLayoutParams())
    }

    fun getOverviewOverlay(): SurfaceControl? {
        if (overviewOverlay == null) {
            overviewOverlay = systemUiProxy.getOverviewOverlayContainer(displayId)
        }
        return overviewOverlay
    }

    @UiThread
    fun showRecentsWindow(callbacks: RecentsAnimationCallbacks? = null) {
        RecentsWindowProtoLogProxy.logStartRecentsWindow(isShowing(), windowView == null)
        if (isShowing()) {
            return
        }

        createWindowView()
        windowRootView.visibility = View.VISIBLE

        this.callbacks = callbacks
        callbacks?.addListener(recentsAnimationListener)
        screenOnTracker.addListener(screenChangedListener)
    }

    override fun onConfigurationChanged(newConfiguration: Configuration) {
        Log.d(TAG, "onConfigurationChanged: $newConfiguration")
        handleConfigurationChanged(newConfiguration)
    }

    override fun onLowMemory() {
        // Do nothing
    }

    override fun startHome(animated: Boolean, onHomeAnimationComplete: Runnable?) {
        startHomeWithRemoteAnimation(onHomeAnimationComplete = onHomeAnimationComplete)
    }

    @JvmOverloads
    fun startHomeWithRemoteAnimation(
        finishRecentsAnimation: Boolean = true,
        onHomeAnimationComplete: Runnable? = null,
    ) {
        val recentsView: RecentsView<*, *>? = getOverviewPanel()
        if (recentsView == null) {
            onHomeAnimationComplete?.run()
            return
        }
        recentsView.switchToScreenshot {
            if (finishRecentsAnimation) {
                recentsView.finishRecentsAnimation(
                    /* toHome= */ true,
                    { startHomeWithRemoteAnimationInternal(onHomeAnimationComplete) },
                )
            } else {
                startHomeWithRemoteAnimationInternal(onHomeAnimationComplete)
            }
        }
    }

    override fun getActivityLaunchOptions(view: View?, item: ItemInfo?): ActivityOptionsWrapper {
        val taskView =
            view as? TaskView
                ?: return super<RecentsWindowContext>.getActivityLaunchOptions(view, item)
        val recentsView =
            recentsView ?: return super<RecentsWindowContext>.getActivityLaunchOptions(view, item)
        val onEndCallback = RunnableList()

        activityLaunchAnimationRunner =
            object : RemoteAnimationFactory {
                override fun onAnimationStart(
                    transit: Int,
                    apps: Array<RemoteAnimationTarget>?,
                    wallpapers: Array<RemoteAnimationTarget>?,
                    nonApps: Array<RemoteAnimationTarget>?,
                    callback: LauncherAnimationRunner.AnimationResult?,
                ) {
                    if (
                        apps == null ||
                            wallpapers == null ||
                            nonApps == null ||
                            callback == null ||
                            recentsView == null
                    ) {
                        return
                    }
                    val anim =
                        composeRecentsLaunchAnimator(
                                recentsView,
                                taskView,
                                apps,
                                wallpapers,
                                nonApps,
                            )
                            .apply {
                                addListener(
                                    onEnd = {
                                        recentsView.resetTaskVisuals()
                                        stateManager.reapplyState()
                                    }
                                )
                            }
                    callback.setAnimation(
                        anim,
                        this@RecentsWindowManager,
                        { onEndCallback.executeAllAndDestroy() },
                        true, /* skipFirstFrame */
                    )
                }

                override fun onAnimationCancelled() {
                    onEndCallback.executeAllAndDestroy()
                }
            }

        val wrapper =
            LauncherAnimationRunner(
                uiExecutor.handler,
                activityLaunchAnimationRunner,
                /* startAtFrontOfQueue=*/ true,
            )
        val options =
            ActivityOptions.makeRemoteAnimation(
                RemoteAnimationAdapter(
                    wrapper,
                    RECENTS_LAUNCH_DURATION.toLong(),
                    (RECENTS_LAUNCH_DURATION -
                            STATUS_BAR_TRANSITION_DURATION -
                            STATUS_BAR_TRANSITION_PRE_DELAY)
                        .toLong(),
                ),
                RemoteTransition(
                    wrapper.toRemoteTransition(),
                    iApplicationThread,
                    "LaunchFromRecents",
                ),
            )
        return ActivityOptionsWrapper(options, onEndCallback).apply {
            options.apply {
                splashScreenStyle = SplashScreen.SPLASH_SCREEN_STYLE_ICON
                launchDisplayId = taskView.displayId
                pendingIntentBackgroundActivityStartMode =
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }
        }
    }

    /** Composes the animations for a launch from the recents list if possible. */
    private fun composeRecentsLaunchAnimator(
        recentsView: RecentsView<*, *>,
        taskView: TaskView,
        appTargets: Array<RemoteAnimationTarget>,
        wallpaperTargets: Array<RemoteAnimationTarget>,
        nonAppTargets: Array<RemoteAnimationTarget>,
    ): AnimatorSet {
        val animatorSet = AnimatorSet()
        val pendingAnimation = PendingAnimation(RECENTS_LAUNCH_DURATION.toLong())
        TaskViewUtils.createRecentsWindowAnimator(
            recentsView,
            taskView,
            /* skipViewChanges= */ true,
            appTargets,
            wallpaperTargets,
            nonAppTargets,
            /* depthController= */ null,
            /* transitionInfo= */ null,
            /* appearedTaskId= */ ActivityTaskManager.INVALID_TASK_ID,
            pendingAnimation,
        )
        animatorSet.play(pendingAnimation.buildAnim())
        return animatorSet
    }

    private fun startHomeWithRemoteAnimationInternal(onHomeAnimationComplete: Runnable?) {
        val displayId = displayId
        val animationToHomeFactory =
            RemoteAnimationFactory {
                _: Int,
                appTargets: Array<RemoteAnimationTarget>?,
                wallpaperTargets: Array<RemoteAnimationTarget>?,
                nonAppTargets: Array<RemoteAnimationTarget>?,
                result: LauncherAnimationRunner.AnimationResult? ->
                result ?: return@RemoteAnimationFactory
                val controller =
                    getStateManager()
                        .createAnimationToNewWorkspace(BG_LAUNCHER, HOME_APPEAR_DURATION)
                controller.dispatchOnStart()
                val targets =
                    RemoteAnimationTargets(
                        appTargets,
                        wallpaperTargets,
                        nonAppTargets,
                        RemoteAnimationTarget.MODE_OPENING,
                    )
                targets.apps.forEach { Transaction().setAlpha(it.leash, 1f).apply() }
                val anim =
                    AnimatorSet().apply {
                        play(controller.animationPlayer)
                        duration = HOME_APPEAR_DURATION
                    }
                result.setAnimation(
                    anim,
                    this@RecentsWindowManager,
                    {
                        getStateManager().goToState(BG_LAUNCHER, true)
                        hideRecentsWindow()
                        onHomeAnimationComplete?.run()
                    },
                    true, /* skipFirstFrame */
                )
            }
        val runner = LauncherAnimationRunner(mainThreadHandler, animationToHomeFactory, true)
        val options =
            ActivityOptions.makeRemoteAnimation(
                RemoteAnimationAdapter(runner, HOME_APPEAR_DURATION, 0),
                RemoteTransition(
                    runner.toRemoteTransition(),
                    iApplicationThread,
                    "StartHomeFromRecents",
                ),
            )
        options.launchDisplayId = displayId
        OverviewComponentObserver.startHomeIntentSafely(this, options.toBundle(), TAG, displayId)
        stateManager.moveToRestState()
    }

    fun hideRecentsWindow() {
        RecentsWindowProtoLogProxy.logCleanup(isShowing())
        if (isShowing()) {
            AbstractFloatingView.closeAllOpenViews(this, /* animate= */ false)
            recentsView?.viewRootImpl?.touchModeChanged(true)
            windowRootView.visibility = View.GONE
            AccessibilityManagerCompat.sendTestProtocolEventToTest(
                this,
                LAUNCHER_ACTIVITY_STOPPED_MESSAGE,
            )
        }
        stateManager.moveToRestState()
        callbacks?.removeListener(recentsAnimationListener)
        callbacks = null
        screenOnTracker.removeListener(screenChangedListener)
    }

    private fun isShowing(): Boolean {
        return windowView?.parent != null && windowRootView.isVisible
    }

    private fun recentAnimationStopped() {
        if (isInState(BACKGROUND_APP)) {
            hideRecentsWindow()
        }
    }

    override fun getComponentName(): ComponentName {
        return ComponentName(this, RecentsWindowManager::class.java)
    }

    override fun canStartHomeSafely(): Boolean {
        val overviewCommandHelper = tisBindHelper.overviewCommandHelper
        return overviewCommandHelper == null ||
            overviewCommandHelper.canStartHomeSafely() ||
            displayId != DEFAULT_DISPLAY
    }

    override fun setTaskbarInteractor(taskbarInteractor: TaskbarInteractor?) {
        this.taskbarInteractor = taskbarInteractor
    }

    override fun getTaskbarInteractor(): TaskbarInteractor? {
        return taskbarInteractor
    }

    override fun collectStateHandlers(out: MutableList<StateManager.StateHandler<RecentsState?>>?) {
        out!!.add(FallbackRecentsStateController(this))
    }

    override fun getStateManager(): StateManager<RecentsState, RecentsWindowManager> {
        return this.stateManager
    }

    override fun shouldAnimateStateChange(): Boolean {
        return false
    }

    override fun isInState(state: RecentsState?): Boolean {
        return stateManager.state == state
    }

    override fun onStateSetStart(state: RecentsState) {
        super.onStateSetStart(state)
        RecentsWindowProtoLogProxy.logOnStateSetStart(state.toString())
    }

    override fun onStateSetEnd(state: RecentsState) {
        super.onStateSetEnd(state)
        RecentsWindowProtoLogProxy.logOnStateSetEnd(state.toString())
        if (!state.isRecentsViewVisible()) {
            hideRecentsWindow()
        }
        AccessibilityManagerCompat.sendStateEventToTest(baseContext, state.toLauncherStateOrdinal())
    }

    override fun onRepeatStateSetAborted(state: RecentsState) {
        super.onRepeatStateSetAborted(state)
        RecentsWindowProtoLogProxy.logOnRepeatStateSetAborted(state.toString())
        if (!state.isRecentsViewVisible()) {
            hideRecentsWindow()
        }
    }

    override fun getSystemUiController(): SystemUiController? {
        if (systemUiController == null) {
            systemUiController = SystemUiController(rootView)
        }
        return systemUiController
    }

    override fun getScrimView(): ScrimView? {
        return scrimView
    }

    override fun <T : BaseContainerInterface<*, *>?> getContainerInterface(): T {
        return fallbackWindowInterface as T
    }

    override fun <T : View?> getOverviewPanel(): T {
        return recentsView as T
    }

    override fun getSplitSelectStateController(): SplitSelectStateController {
        return splitSelectStateController
    }

    override fun goToRecentsState(recentsState: RecentsState, animated: Boolean) {
        stateManager.goToState(recentsState, animated)
    }

    override fun getRootView(): View {
        return windowRootView
    }

    override fun getDragLayer(): BaseDragLayer<RecentsWindowManager> {
        return dragLayer!!
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent?): Boolean {
        return windowRootView.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchKeyEvent(ev: KeyEvent?): Boolean {
        return windowRootView.dispatchKeyEvent(ev)
    }

    override fun onRootViewDispatchKeyEvent(event: KeyEvent?): Boolean {
        TestLogging.recordKeyEvent(SEQUENCE_MAIN, "Key event", event)
        return if (
            event?.action != KeyEvent.ACTION_DOWN || event.keyCode != KeyEvent.KEYCODE_ESCAPE
        ) {
            super.onRootViewDispatchKeyEvent(event)
        } else if (isInState(OVERVIEW_SPLIT_SELECT) || isInState(MODAL_TASK)) {
            stateManager.goToState(DEFAULT, true)
            true
        } else if (isInState(DEFAULT)) {
            stateManager.state.onBackInvoked(this@RecentsWindowManager)
            true
        } else {
            super.onRootViewDispatchKeyEvent(event)
        }
    }

    override fun getActionsView(): OverviewActionsView<*>? {
        return actionsView
    }

    override fun addForceInvisibleFlag(flag: Int) {}

    override fun clearForceInvisibleFlag(flag: Int) {}

    override fun setLocusContext(id: LocusId?, bundle: Bundle?) {
        // no op
    }

    override fun isStarted(): Boolean {
        return isShowing() && stateManager.state.isRecentsViewVisible()
    }

    /** Adds a callback for the provided activity event */
    override fun addEventCallback(@BaseActivity.ActivityEvent event: Int, callback: Runnable?) {
        eventCallbacks[event].add(callback)
    }

    /** Removes a previously added callback */
    override fun removeEventCallback(@BaseActivity.ActivityEvent event: Int, callback: Runnable?) {
        eventCallbacks[event].remove(callback)
    }

    override fun runOnBindToTouchInteractionService(r: Runnable?) {
        tisBindHelper.runOnBindToTouchInteractionService(r)
    }

    override fun returnToHomescreen() {
        startHomeWithRemoteAnimation()
    }

    override fun isRecentsViewVisible(): Boolean {
        return isShowing() || getStateManager().state!!.isRecentsViewVisible()
    }

    override fun createAtomicAnimationFactory(): AtomicAnimationFactory<RecentsState> {
        return RecentsAtomicAnimationFactory(this)
    }

    override fun getOverviewBlurStyleResId(): Int {
        return R.style.OverviewBlurFallbackStyle
    }

    @AssistedFactory
    interface Factory {
        /** Creates a new instance of [RecentsWindowManager] for a given [context]. */
        fun create(
            @WindowContext context: Context,
            fallbackWindowInterface: FallbackWindowInterface,
            recentsWindowTracker: RecentsWindowTracker,
        ): RecentsWindowManager
    }
}

@LauncherAppSingleton
class RecentsWindowManagerInstanceProvider
@Inject
constructor(
    private val factory: RecentsWindowManager.Factory,
    @WindowContext private val windowContextRepository: PerDisplayRepository<Context>,
    private val fallbackWindowInterfaceRepository: PerDisplayRepository<FallbackWindowInterface>,
    private val recentsWindowTrackerRepository: PerDisplayRepository<RecentsWindowTracker>,
) : PerDisplayInstanceProviderWithTeardown<RecentsWindowManager> {
    override fun createInstance(displayId: Int) =
        windowContextRepository[displayId]?.let { windowContext ->
            fallbackWindowInterfaceRepository[displayId]?.let { fallbackWindowInterface ->
                recentsWindowTrackerRepository[displayId]?.let { recentsWindowTracker ->
                    factory.create(windowContext, fallbackWindowInterface, recentsWindowTracker)
                }
            }
        }

    override fun destroyInstance(instance: RecentsWindowManager) {
        instance.destroy()
    }
}
