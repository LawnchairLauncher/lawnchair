/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.dagger;

import static android.window.DesktopExperienceFlags.ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS;
import static android.window.DesktopModeFlags.ENABLE_DESKTOP_SYSTEM_DIALOGS_TRANSITIONS;
import static android.window.DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_ENTER_TRANSITIONS_BUGFIX;
import static android.window.DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_MODALS_POLICY;
import static android.window.DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_TASK_LIMIT;

import static com.android.systemui.Flags.enableViewCaptureTracing;
import static com.android.window.flags2.Flags.enableInorderTransitionCallbacksForDesktop;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.IActivityTaskManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.UserManager;
import android.view.Choreographer;
import android.view.IWindowManager;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.DesktopExperienceFlags;
import android.window.DesktopModeFlags;

import androidx.annotation.OptIn;

import com.android.app.viewcapture.ViewCaptureAwareWindowManagerFactory;
import com.android.internal.jank.InteractionJankMonitor;
import com.android.internal.logging.UiEventLogger;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.util.LatencyTracker;
import com.android.launcher3.icons.IconProvider;
import com.android.wm.shell.RootDisplayAreaOrganizer;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.activityembedding.ActivityEmbeddingController;
import com.android.wm.shell.apptoweb.AppToWebGenericLinksParser;
import com.android.wm.shell.apptoweb.AssistContentRequester;
import com.android.wm.shell.appzoomout.AppZoomOutController;
import com.android.wm.shell.back.BackAnimationController;
import com.android.wm.shell.bubbles.BubbleController;
import com.android.wm.shell.bubbles.BubbleData;
import com.android.wm.shell.bubbles.BubbleDataRepository;
import com.android.wm.shell.bubbles.BubbleEducationController;
import com.android.wm.shell.bubbles.BubbleLogger;
import com.android.wm.shell.bubbles.BubblePositioner;
import com.android.wm.shell.bubbles.BubbleResizabilityChecker;
import com.android.wm.shell.bubbles.BubbleTaskUnfoldTransitionMerger;
import com.android.wm.shell.bubbles.BubbleTransitions;
import com.android.wm.shell.bubbles.appinfo.BubbleAppInfoProvider;
import com.android.wm.shell.bubbles.appinfo.PackageManagerBubbleAppInfoProvider;
import com.android.wm.shell.bubbles.bar.DragToBubbleController;
import com.android.wm.shell.bubbles.storage.BubblePersistentRepository;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.FloatingContentCoordinator;
import com.android.wm.shell.common.HomeIntentProvider;
import com.android.wm.shell.common.LaunchAdjacentController;
import com.android.wm.shell.common.MultiDisplayDragMoveIndicatorController;
import com.android.wm.shell.common.MultiDisplayDragMoveIndicatorSurface;
import com.android.wm.shell.common.MultiInstanceHelper;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.TaskStackListenerImpl;
import com.android.wm.shell.common.UserProfileContexts;
import com.android.wm.shell.common.split.SplitState;
import com.android.wm.shell.compatui.api.CompatUIHandler;
import com.android.wm.shell.compatui.letterbox.DelegateLetterboxTransitionObserver;
import com.android.wm.shell.compatui.letterbox.LetterboxCommandHandler;
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxCleanupAdapter;
import com.android.wm.shell.compatui.letterbox.state.LetterboxTaskListenerAdapter;
import com.android.wm.shell.crashhandling.ShellCrashHandler;
import com.android.wm.shell.dagger.back.ShellBackAnimationModule;
import com.android.wm.shell.dagger.pip.PipModule;
import com.android.wm.shell.desktopmode.CloseDesktopTaskTransitionHandler;
import com.android.wm.shell.desktopmode.DefaultDragToDesktopTransitionHandler;
import com.android.wm.shell.desktopmode.DesktopActivityOrientationChangeHandler;
import com.android.wm.shell.desktopmode.DesktopBackNavTransitionObserver;
import com.android.wm.shell.desktopmode.DesktopDisplayEventHandler;
import com.android.wm.shell.desktopmode.DesktopImeHandler;
import com.android.wm.shell.desktopmode.DesktopImmersiveController;
import com.android.wm.shell.desktopmode.DesktopInOrderTransitionObserver;
import com.android.wm.shell.desktopmode.DesktopMinimizationTransitionHandler;
import com.android.wm.shell.desktopmode.DesktopMixedTransitionHandler;
import com.android.wm.shell.desktopmode.DesktopModeDragAndDropAnimatorHelper;
import com.android.wm.shell.desktopmode.DesktopModeDragAndDropTransitionHandler;
import com.android.wm.shell.desktopmode.DesktopModeEventLogger;
import com.android.wm.shell.desktopmode.DesktopModeKeyGestureHandler;
import com.android.wm.shell.desktopmode.DesktopModeLoggerTransitionObserver;
import com.android.wm.shell.desktopmode.DesktopModeMoveToDisplayTransitionHandler;
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger;
import com.android.wm.shell.desktopmode.DesktopTaskChangeListener;
import com.android.wm.shell.desktopmode.DesktopTasksController;
import com.android.wm.shell.desktopmode.DesktopTasksLimiter;
import com.android.wm.shell.desktopmode.DesktopTasksTransitionObserver;
import com.android.wm.shell.desktopmode.DesktopUserRepositories;
import com.android.wm.shell.desktopmode.DisplayDisconnectTransitionHandler;
import com.android.wm.shell.desktopmode.DragToDesktopTransitionHandler;
import com.android.wm.shell.desktopmode.DragToDisplayTransitionHandler;
import com.android.wm.shell.desktopmode.EnterDesktopTaskTransitionHandler;
import com.android.wm.shell.desktopmode.ExitDesktopTaskTransitionHandler;
import com.android.wm.shell.desktopmode.OverviewToDesktopTransitionObserver;
import com.android.wm.shell.desktopmode.ReturnToDragStartAnimator;
import com.android.wm.shell.desktopmode.SpringDragToDesktopTransitionHandler;
import com.android.wm.shell.desktopmode.ToggleResizeDesktopTaskTransitionHandler;
import com.android.wm.shell.desktopmode.VisualIndicatorUpdateScheduler;
import com.android.wm.shell.desktopmode.WindowDecorCaptionRepository;
import com.android.wm.shell.desktopmode.compatui.SystemModalsTransitionHandler;
import com.android.wm.shell.desktopmode.desktopfirst.DesktopDisplayModeController;
import com.android.wm.shell.desktopmode.desktopfirst.DesktopFirstListenerManager;
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider;
import com.android.wm.shell.desktopmode.education.AppHandleEducationController;
import com.android.wm.shell.desktopmode.education.AppHandleEducationFilter;
import com.android.wm.shell.desktopmode.education.AppToWebEducationController;
import com.android.wm.shell.desktopmode.education.AppToWebEducationFilter;
import com.android.wm.shell.desktopmode.education.data.AppHandleEducationDatastoreRepository;
import com.android.wm.shell.desktopmode.education.data.AppToWebEducationDatastoreRepository;
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer;
import com.android.wm.shell.desktopmode.multidesks.DesksTransitionObserver;
import com.android.wm.shell.desktopmode.multidesks.RootTaskDesksOrganizer;
import com.android.wm.shell.desktopmode.persistence.DesktopPersistentRepository;
import com.android.wm.shell.desktopmode.persistence.DesktopRepositoryInitializer;
import com.android.wm.shell.desktopmode.persistence.DesktopRepositoryInitializerImpl;
import com.android.wm.shell.draganddrop.DragAndDropController;
import com.android.wm.shell.draganddrop.GlobalDragListener;
import com.android.wm.shell.freeform.FreeformComponents;
import com.android.wm.shell.freeform.FreeformTaskListener;
import com.android.wm.shell.freeform.FreeformTaskTransitionHandler;
import com.android.wm.shell.freeform.FreeformTaskTransitionObserver;
import com.android.wm.shell.freeform.FreeformTaskTransitionStarter;
import com.android.wm.shell.freeform.FreeformTaskTransitionStarterInitializer;
import com.android.wm.shell.freeform.TaskChangeListener;
import com.android.wm.shell.keyguard.KeyguardTransitionHandler;
import com.android.wm.shell.onehanded.OneHandedController;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.recents.RecentTasksController;
import com.android.wm.shell.recents.RecentsTransitionHandler;
import com.android.wm.shell.shared.TransactionPool;
import com.android.wm.shell.shared.annotations.ShellAnimationThread;
import com.android.wm.shell.shared.annotations.ShellBackgroundThread;
import com.android.wm.shell.shared.annotations.ShellDesktopThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.shared.desktopmode.DesktopConfig;
import com.android.wm.shell.shared.desktopmode.DesktopModeCompatPolicy;
import com.android.wm.shell.shared.desktopmode.DesktopState;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.taskview.TaskViewRepository;
import com.android.wm.shell.taskview.TaskViewTransitions;
import com.android.wm.shell.transition.DefaultMixedHandler;
import com.android.wm.shell.transition.FocusTransitionObserver;
import com.android.wm.shell.transition.HomeTransitionObserver;
import com.android.wm.shell.transition.MixedTransitionHandler;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.unfold.ShellUnfoldProgressProvider;
import com.android.wm.shell.unfold.UnfoldAnimationController;
import com.android.wm.shell.unfold.UnfoldBackgroundController;
import com.android.wm.shell.unfold.UnfoldTransitionHandler;
import com.android.wm.shell.unfold.animation.FullscreenUnfoldTaskAnimator;
import com.android.wm.shell.unfold.animation.SplitTaskUnfoldAnimator;
import com.android.wm.shell.unfold.animation.UnfoldTaskAnimator;
import com.android.wm.shell.unfold.qualifier.UnfoldShellTransition;
import com.android.wm.shell.unfold.qualifier.UnfoldTransition;
import com.android.wm.shell.windowdecor.CaptionWindowDecorViewModel;
import com.android.wm.shell.windowdecor.DesktopModeWindowDecorViewModel;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;
import com.android.wm.shell.windowdecor.additionalviewcontainer.AdditionalSystemViewContainer;
import com.android.wm.shell.windowdecor.common.AppHandleAndHeaderVisibilityHelper;
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader;
import com.android.wm.shell.windowdecor.common.viewhost.DefaultWindowDecorViewHostSupplier;
import com.android.wm.shell.windowdecor.common.viewhost.PooledWindowDecorViewHostSupplier;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier;
import com.android.wm.shell.windowdecor.education.DesktopWindowingEducationPromoController;
import com.android.wm.shell.windowdecor.education.DesktopWindowingEducationTooltipController;
import com.android.wm.shell.windowdecor.tiling.DesktopTilingDecorViewModel;
import com.android.wm.shell.windowdecor.viewholder.AppHandleNotifier;

import com.google.android.msdl.domain.MSDLPlayer;

import dagger.Binds;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.ExperimentalCoroutinesApi;
import kotlinx.coroutines.MainCoroutineDispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Provides dependencies from {@link com.android.wm.shell}, these dependencies are only accessible
 * from components within the WM subcomponent (can be explicitly exposed to the SysUIComponent, see
 * {@link WMComponent}).
 *
 * <p>This module only defines Shell dependencies for handheld SystemUI implementation. Common
 * dependencies should go into {@link WMShellBaseModule}.
 */
@Module(includes = {WMShellBaseModule.class, PipModule.class, ShellBackAnimationModule.class,
        LetterboxModule.class})
public abstract class WMShellModule {

    //
    // Bubbles
    //

    @WMSingleton
    @Provides
    static BubbleLogger provideBubbleLogger(UiEventLogger uiEventLogger) {
        return new BubbleLogger(uiEventLogger);
    }

    @WMSingleton
    @Provides
    static BubblePositioner provideBubblePositioner(Context context, WindowManager windowManager) {
        return new BubblePositioner(context, windowManager);
    }

    @WMSingleton
    @Provides
    static BubbleEducationController provideBubbleEducationProvider(Context context) {
        return new BubbleEducationController(context);
    }

    @WMSingleton
    @Provides
    static BubbleData provideBubbleData(
            Context context,
            BubbleLogger logger,
            BubblePositioner positioner,
            BubbleEducationController educationController,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellBackgroundThread ShellExecutor bgExecutor) {
        return new BubbleData(
                context, logger, positioner, educationController, mainExecutor, bgExecutor);
    }

    @WMSingleton
    @Provides
    static Optional<BubbleTaskUnfoldTransitionMerger> provideBubbleTaskUnfoldTransitionMerger(
            Optional<BubbleController> bubbleController) {
        return bubbleController.map(controller -> controller);
    }

    @Binds
    abstract BubbleAppInfoProvider bindBubbleAppInfoProvider(
            PackageManagerBubbleAppInfoProvider appInfoProvider);

    @WMSingleton
    @Provides
    static BubbleTransitions provideBubbleTransitions(
            @NonNull Context context,
            @NonNull Transitions transitions,
            @NonNull ShellTaskOrganizer organizer,
            @NonNull TaskViewRepository repository,
            @NonNull BubbleData bubbleData,
            @NonNull @Bubbles TaskViewTransitions taskViewTransitions,
            @NonNull BubbleAppInfoProvider appInfoProvider
    ) {
        return new BubbleTransitions(context, transitions, organizer, repository,
                bubbleData, taskViewTransitions, appInfoProvider);
    }

    @WMSingleton
    @Provides
    @Bubbles
    static TaskViewTransitions provideBubblesTaskViewTransitions(
            @NonNull TaskViewTransitions taskViewTransitions,
            @NonNull Transitions transitions,
            @NonNull TaskViewRepository repository,
            @NonNull ShellTaskOrganizer organizer,
            SyncTransactionQueue syncQueue
    ) {
        return TaskViewTransitions.useRepo()
                ? new TaskViewTransitions(transitions, repository, organizer, syncQueue)
                : taskViewTransitions;
    }

    // Note: Handler needed for LauncherApps.register
    @WMSingleton
    @Provides
    static BubbleController provideBubbleController(
            Context context,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            ShellController shellController,
            BubbleData data,
            BubbleTransitions bubbleTransitions,
            FloatingContentCoordinator floatingContentCoordinator,
            IStatusBarService statusBarService,
            WindowManager windowManager,
            DisplayInsetsController displayInsetsController,
            DisplayImeController displayImeController,
            UserManager userManager,
            LauncherApps launcherApps,
            TaskStackListenerImpl taskStackListener,
            BubbleLogger logger,
            ShellTaskOrganizer organizer,
            BubblePositioner positioner,
            DisplayController displayController,
            @DynamicOverride Optional<OneHandedController> oneHandedOptional,
            DragAndDropController dragAndDropController,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellMainThread Handler mainHandler,
            @ShellBackgroundThread ShellExecutor bgExecutor,
            @Bubbles TaskViewTransitions taskViewTransitions,
            Transitions transitions,
            SyncTransactionQueue syncQueue,
            IWindowManager wmService,
            HomeIntentProvider homeIntentProvider,
            BubbleAppInfoProvider appInfoProvider,
            Lazy<Optional<SplitScreenController>> splitScreenController) {
        final WindowManager wm = enableViewCaptureTracing()
                ? ViewCaptureAwareWindowManagerFactory.getInstance(context)
                : windowManager;
        return new BubbleController(
                context,
                shellInit,
                shellCommandHandler,
                shellController,
                data,
                null /* synchronizer */,
                floatingContentCoordinator,
                new BubbleDataRepository(
                        launcherApps,
                        mainExecutor,
                        bgExecutor,
                        new BubblePersistentRepository(context)),
                bubbleTransitions,
                statusBarService,
                wm,
                displayInsetsController,
                displayImeController,
                userManager,
                launcherApps,
                logger,
                taskStackListener,
                organizer,
                positioner,
                displayController,
                oneHandedOptional,
                dragAndDropController,
                mainExecutor,
                mainHandler,
                bgExecutor,
                taskViewTransitions,
                transitions,
                syncQueue,
                wmService,
                new BubbleResizabilityChecker(),
                homeIntentProvider,
                appInfoProvider,
                splitScreenController);
    }

    //
    // Window decoration
    //

    @WMSingleton
    @Provides
    static WindowDecorViewModel provideWindowDecorViewModel(
            Context context,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellMainThread Handler mainHandler,
            @ShellMainThread Choreographer mainChoreographer,
            @ShellBackgroundThread ShellExecutor bgExecutor,
            ShellInit shellInit,
            IWindowManager windowManager,
            ShellTaskOrganizer taskOrganizer,
            DisplayController displayController,
            SyncTransactionQueue syncQueue,
            Transitions transitions,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            FocusTransitionObserver focusTransitionObserver,
            WindowDecorViewHostSupplier<WindowDecorViewHost> windowDecorViewHostSupplier,
            Optional<DesktopModeWindowDecorViewModel> desktopModeWindowDecorViewModel,
            DesktopState desktopState,
            DesktopConfig desktopConfig) {
        if (desktopModeWindowDecorViewModel.isPresent()) {
            return desktopModeWindowDecorViewModel.get();
        }
        return new CaptionWindowDecorViewModel(
                context,
                mainHandler,
                mainExecutor,
                bgExecutor,
                mainChoreographer,
                windowManager,
                shellInit,
                taskOrganizer,
                displayController,
                rootTaskDisplayAreaOrganizer,
                syncQueue,
                transitions,
                focusTransitionObserver,
                windowDecorViewHostSupplier,
                desktopState,
                desktopConfig);
    }

    @WMSingleton
    @Provides
    static AppToWebGenericLinksParser provideGenericLinksParser(
            Context context, @ShellMainThread ShellExecutor mainExecutor,
            DesktopConfig desktopConfig) {
        return new AppToWebGenericLinksParser(context, mainExecutor, desktopConfig);
    }

    @Provides
    static AssistContentRequester provideAssistContentRequester(
            Context context,
            @ShellMainThread ShellExecutor shellExecutor,
            @ShellBackgroundThread ShellExecutor bgExecutor) {
        return new AssistContentRequester(context, shellExecutor, bgExecutor);
    }

    @Provides
    static AdditionalSystemViewContainer.Factory provideAdditionalSystemViewContainerFactory() {
        return new AdditionalSystemViewContainer.Factory();
    }

    @WMSingleton
    @Provides
    static WindowDecorViewHostSupplier<WindowDecorViewHost> provideWindowDecorViewHostSupplier(
            @NonNull Context context,
            @ShellMainThread @NonNull CoroutineScope mainScope,
            @NonNull ShellInit shellInit,
            DesktopState desktopState,
            DesktopConfig desktopConfig) {
        final int poolSize = desktopConfig.getWindowDecorScvhPoolSize();
        final int preWarmSize = desktopConfig.getWindowDecorPreWarmSize();
        if (desktopState.canEnterDesktopModeOrShowAppHandle() && poolSize > 0) {
            return new PooledWindowDecorViewHostSupplier(
                    context, mainScope, shellInit, poolSize, preWarmSize);
        }
        return new DefaultWindowDecorViewHostSupplier(mainScope);
    }

    //
    // Freeform
    //

    @WMSingleton
    @Provides
    @DynamicOverride
    static FreeformComponents provideFreeformComponents(
            FreeformTaskListener taskListener,
            FreeformTaskTransitionHandler transitionHandler,
            FreeformTaskTransitionObserver transitionObserver,
            FreeformTaskTransitionStarterInitializer transitionStarterInitializer) {
        return new FreeformComponents(
                taskListener,
                Optional.of(transitionHandler),
                Optional.of(transitionObserver),
                Optional.of(transitionStarterInitializer));
    }

    @WMSingleton
    @Provides
    static FreeformTaskListener provideFreeformTaskListener(
            Context context,
            ShellInit shellInit,
            ShellTaskOrganizer shellTaskOrganizer,
            Optional<DesktopUserRepositories> desktopUserRepositories,
            Optional<DesktopTasksController> desktopTasksController,
            DesktopModeLoggerTransitionObserver desktopModeLoggerTransitionObserver,
            LaunchAdjacentController launchAdjacentController,
            WindowDecorViewModel windowDecorViewModel,
            Optional<TaskChangeListener> taskChangeListener,
            DesktopState desktopState) {
        return new FreeformTaskListener(
                context,
                shellInit,
                shellTaskOrganizer,
                desktopUserRepositories,
                desktopTasksController,
                desktopModeLoggerTransitionObserver,
                launchAdjacentController,
                windowDecorViewModel,
                taskChangeListener,
                desktopState);
    }

    @WMSingleton
    @Provides
    static FreeformTaskTransitionHandler provideFreeformTaskTransitionHandler(
            Transitions transitions,
            DisplayController displayController,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellAnimationThread ShellExecutor animExecutor,
            @ShellAnimationThread Handler animHandler) {
        return new FreeformTaskTransitionHandler(
                transitions, displayController, mainExecutor, animExecutor, animHandler);
    }

    @WMSingleton
    @Provides
    static FreeformTaskTransitionObserver provideFreeformTaskTransitionObserver(
            ShellInit shellInit,
            Transitions transitions,
            Optional<DesktopImmersiveController> desktopImmersiveController,
            WindowDecorViewModel windowDecorViewModel,
            Optional<TaskChangeListener> taskChangeListener,
            FocusTransitionObserver focusTransitionObserver,
            DesksOrganizer desksOrganizer,
            Optional<DesksTransitionObserver> desksTransitionObserver,
            DesktopState desktopState,
            Optional<DesktopImeHandler> desktopImeHandler,
            Optional<DesktopBackNavTransitionObserver> desktopBackNavTransitionObserver,
            Optional<DesktopInOrderTransitionObserver> desktopInOrderTransitionObserver) {
        return new FreeformTaskTransitionObserver(
                shellInit,
                transitions,
                desktopImmersiveController,
                windowDecorViewModel,
                taskChangeListener,
                focusTransitionObserver,
                desksOrganizer,
                desksTransitionObserver,
                desktopState,
                desktopImeHandler,
                desktopBackNavTransitionObserver,
                desktopInOrderTransitionObserver);
    }

    @WMSingleton
    @Provides
    static FreeformTaskTransitionStarterInitializer provideFreeformTaskTransitionStarterInitializer(
            ShellInit shellInit,
            WindowDecorViewModel windowDecorViewModel,
            FreeformTaskTransitionHandler freeformTaskTransitionHandler,
            Optional<DesktopMixedTransitionHandler> desktopMixedTransitionHandler) {
        FreeformTaskTransitionStarter transitionStarter;
        if (desktopMixedTransitionHandler.isPresent()) {
            transitionStarter = desktopMixedTransitionHandler.get();
        } else {
            transitionStarter = freeformTaskTransitionHandler;
        }
        return new FreeformTaskTransitionStarterInitializer(
                shellInit, windowDecorViewModel, transitionStarter);
    }

    //
    // One handed mode
    //

    // Needs the shell main handler for ContentObserver callbacks
    @WMSingleton
    @Provides
    @DynamicOverride
    static OneHandedController provideOneHandedController(
            Context context,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            ShellController shellController,
            WindowManager windowManager,
            DisplayController displayController,
            DisplayLayout displayLayout,
            TaskStackListenerImpl taskStackListener,
            UiEventLogger uiEventLogger,
            InteractionJankMonitor jankMonitor,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellMainThread Handler mainHandler) {
        return OneHandedController.create(
                context,
                shellInit,
                shellCommandHandler,
                shellController,
                windowManager,
                displayController,
                displayLayout,
                taskStackListener,
                jankMonitor,
                uiEventLogger,
                mainExecutor,
                mainHandler);
    }

    //
    // Splitscreen
    //

    @WMSingleton
    @Provides
    @DynamicOverride
    static SplitScreenController provideSplitScreenController(
            Context context,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            ShellController shellController,
            ShellTaskOrganizer shellTaskOrganizer,
            SyncTransactionQueue syncQueue,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            DisplayController displayController,
            DisplayImeController displayImeController,
            DisplayInsetsController displayInsetsController,
            DragAndDropController dragAndDropController,
            Transitions transitions,
            TransactionPool transactionPool,
            IconProvider iconProvider,
            Optional<RecentTasksController> recentTasks,
            LaunchAdjacentController launchAdjacentController,
            Optional<WindowDecorViewModel> windowDecorViewModel,
            Optional<DesktopTasksController> desktopTasksController,
            Optional<DesktopUserRepositories> desktopUserRepositories,
            MultiInstanceHelper multiInstanceHelper,
            SplitState splitState,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellMainThread Handler mainHandler,
            RootDisplayAreaOrganizer rootDisplayAreaOrganizer,
            DesktopState desktopState,
            IActivityTaskManager activityTaskManager,
            MSDLPlayer msdlPlayer) {
        return new SplitScreenController(
                context,
                shellInit,
                shellCommandHandler,
                shellController,
                shellTaskOrganizer,
                syncQueue,
                rootTaskDisplayAreaOrganizer,
                displayController,
                displayImeController,
                displayInsetsController,
                dragAndDropController,
                transitions,
                transactionPool,
                iconProvider,
                recentTasks,
                launchAdjacentController,
                windowDecorViewModel,
                desktopTasksController,
                desktopUserRepositories,
                null /* stageCoordinator */,
                multiInstanceHelper,
                splitState,
                mainExecutor,
                mainHandler,
                rootDisplayAreaOrganizer,
                desktopState,
                activityTaskManager,
                msdlPlayer);
    }

    //
    // Transitions
    //

    @WMSingleton
    @DynamicOverride
    @Provides
    static MixedTransitionHandler provideMixedTransitionHandler(
            ShellInit shellInit,
            Optional<SplitScreenController> splitScreenOptional,
            @Nullable PipTransitionController pipTransitionController,
            Optional<RecentsTransitionHandler> recentsTransitionHandler,
            KeyguardTransitionHandler keyguardTransitionHandler,
            Optional<DesktopTasksController> desktopTasksController,
            Optional<UnfoldTransitionHandler> unfoldHandler,
            Optional<ActivityEmbeddingController> activityEmbeddingController,
            BubbleTransitions bubbleTransitions,
            Transitions transitions) {
        return new DefaultMixedHandler(
                shellInit,
                transitions,
                splitScreenOptional,
                pipTransitionController,
                recentsTransitionHandler,
                keyguardTransitionHandler,
                desktopTasksController,
                unfoldHandler,
                activityEmbeddingController,
                bubbleTransitions);
    }

    @WMSingleton
    @Provides
    static RecentsTransitionHandler provideRecentsTransitionHandler(
            ShellInit shellInit,
            ShellTaskOrganizer shellTaskOrganizer,
            Transitions transitions,
            Optional<RecentTasksController> recentTasksController,
            HomeTransitionObserver homeTransitionObserver,
            DisplayController displayController,
            DesksOrganizer desksOrganizer) {
        return new RecentsTransitionHandler(
                shellInit,
                shellTaskOrganizer,
                transitions,
                recentTasksController.orElse(null),
                homeTransitionObserver,
                displayController,
                desksOrganizer);
    }

    //
    // Unfold transition
    //

    @WMSingleton
    @Provides
    @DynamicOverride
    static UnfoldAnimationController provideUnfoldAnimationController(
            Optional<ShellUnfoldProgressProvider> progressProvider,
            TransactionPool transactionPool,
            @UnfoldTransition SplitTaskUnfoldAnimator splitAnimator,
            FullscreenUnfoldTaskAnimator fullscreenAnimator,
            Lazy<Optional<UnfoldTransitionHandler>> unfoldTransitionHandler,
            ShellInit shellInit,
            @ShellMainThread ShellExecutor mainExecutor) {
        final List<UnfoldTaskAnimator> animators = new ArrayList<>();
        animators.add(splitAnimator);
        animators.add(fullscreenAnimator);

        return new UnfoldAnimationController(
                shellInit,
                transactionPool,
                progressProvider.get(),
                animators,
                unfoldTransitionHandler,
                mainExecutor);
    }

    @Provides
    static FullscreenUnfoldTaskAnimator provideFullscreenUnfoldTaskAnimator(
            Context context,
            UnfoldBackgroundController unfoldBackgroundController,
            ShellController shellController,
            DisplayInsetsController displayInsetsController) {
        return new FullscreenUnfoldTaskAnimator(
                context, unfoldBackgroundController, shellController, displayInsetsController);
    }

    @Provides
    static SplitTaskUnfoldAnimator provideSplitTaskUnfoldAnimatorBase(
            Context context,
            UnfoldBackgroundController backgroundController,
            ShellController shellController,
            @ShellMainThread ShellExecutor executor,
            Lazy<Optional<SplitScreenController>> splitScreenOptional,
            DisplayInsetsController displayInsetsController) {
        // TODO(b/238217847): The lazy reference here causes some dependency issues since it
        // immediately registers a listener on that controller on init.  We should reference the
        // controller directly once we refactor ShellTaskOrganizer to not depend on the unfold
        // animation controller directly.
        return new SplitTaskUnfoldAnimator(
                context,
                executor,
                splitScreenOptional,
                shellController,
                backgroundController,
                displayInsetsController);
    }

    @WMSingleton
    @UnfoldShellTransition
    @Binds
    abstract SplitTaskUnfoldAnimator provideShellSplitTaskUnfoldAnimator(
            SplitTaskUnfoldAnimator splitTaskUnfoldAnimator);

    @WMSingleton
    @UnfoldTransition
    @Binds
    abstract SplitTaskUnfoldAnimator provideSplitTaskUnfoldAnimator(
            SplitTaskUnfoldAnimator splitTaskUnfoldAnimator);

    @WMSingleton
    @Provides
    @DynamicOverride
    static UnfoldTransitionHandler provideUnfoldTransitionHandler(
            Optional<ShellUnfoldProgressProvider> progressProvider,
            FullscreenUnfoldTaskAnimator animator,
            @UnfoldShellTransition SplitTaskUnfoldAnimator unfoldAnimator,
            TransactionPool transactionPool,
            Transitions transitions,
            @ShellMainThread ShellExecutor executor,
            @ShellMainThread Handler handler,
            ShellInit shellInit,
            Optional<BubbleTaskUnfoldTransitionMerger> bubbleTaskUnfoldTransitionMerger) {
        return new UnfoldTransitionHandler(
                shellInit,
                progressProvider.get(),
                animator,
                unfoldAnimator,
                transactionPool,
                executor,
                handler,
                transitions,
                bubbleTaskUnfoldTransitionMerger);
    }

    @WMSingleton
    @Provides
    static UnfoldBackgroundController provideUnfoldBackgroundController(Context context) {
        return new UnfoldBackgroundController(context);
    }

    //
    // Desktop mode (optional feature)
    //

    @WMSingleton
    @Provides
    static DesksOrganizer provideDesksOrganizer(
            @NonNull ShellInit shellInit,
            @NonNull ShellCommandHandler shellCommandHandler,
            @NonNull ShellTaskOrganizer shellTaskOrganizer,
            @NonNull LaunchAdjacentController launchAdjacentController,
            @NonNull RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer
    ) {
        return new RootTaskDesksOrganizer(shellInit, shellCommandHandler, shellTaskOrganizer,
                launchAdjacentController, rootTaskDisplayAreaOrganizer);
    }

    @WMSingleton
    @Provides
    @DynamicOverride
    static DesktopTasksController provideDesktopTasksController(
            Context context,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            ShellController shellController,
            DisplayController displayController,
            ShellTaskOrganizer shellTaskOrganizer,
            SyncTransactionQueue syncQueue,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            DragAndDropController dragAndDropController,
            Transitions transitions,
            KeyguardManager keyguardManager,
            ReturnToDragStartAnimator returnToDragStartAnimator,
            Optional<DesktopMixedTransitionHandler> desktopMixedTransitionHandler,
            EnterDesktopTaskTransitionHandler enterDesktopTransitionHandler,
            ExitDesktopTaskTransitionHandler exitDesktopTransitionHandler,
            DesktopModeDragAndDropTransitionHandler desktopModeDragAndDropTransitionHandler,
            ToggleResizeDesktopTaskTransitionHandler toggleResizeDesktopTaskTransitionHandler,
            DragToDesktopTransitionHandler dragToDesktopTransitionHandler,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            DesktopRepositoryInitializer desktopRepositoryInitializer,
            Optional<DesktopImmersiveController> desktopImmersiveController,
            DesktopModeLoggerTransitionObserver desktopModeLoggerTransitionObserver,
            LaunchAdjacentController launchAdjacentController,
            RecentsTransitionHandler recentsTransitionHandler,
            MultiInstanceHelper multiInstanceHelper,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellMainThread CoroutineScope mainScope,
            @ShellMainThread Handler mainHandler,
            @ShellDesktopThread ShellExecutor desktopExecutor,
            Optional<DesktopTasksLimiter> desktopTasksLimiter,
            Optional<RecentTasksController> recentTasksController,
            InteractionJankMonitor interactionJankMonitor,
            InputManager inputManager,
            FocusTransitionObserver focusTransitionObserver,
            DesktopModeEventLogger desktopModeEventLogger,
            DesktopModeUiEventLogger desktopModeUiEventLogger,
            DesktopWallpaperActivityTokenProvider desktopWallpaperActivityTokenProvider,
            Optional<BubbleController> bubbleController,
            OverviewToDesktopTransitionObserver overviewToDesktopTransitionObserver,
            DesksOrganizer desksOrganizer,
            Optional<DesksTransitionObserver> desksTransitionObserver,
            UserProfileContexts userProfileContexts,
            DesktopModeCompatPolicy desktopModeCompatPolicy,
            DragToDisplayTransitionHandler dragToDisplayTransitionHandler,
            DesktopModeMoveToDisplayTransitionHandler moveToDisplayTransitionHandler,
            HomeIntentProvider homeIntentProvider,
            DesktopState desktopState,
            DesktopConfig desktopConfig,
            VisualIndicatorUpdateScheduler visualIndicatorUpdateScheduler,
            Optional<DesktopFirstListenerManager> desktopFirstListenerManager) {
        return new DesktopTasksController(
                context,
                shellInit,
                shellCommandHandler,
                shellController,
                displayController,
                shellTaskOrganizer,
                syncQueue,
                rootTaskDisplayAreaOrganizer,
                dragAndDropController,
                transitions,
                keyguardManager,
                returnToDragStartAnimator,
                desktopMixedTransitionHandler.get(),
                enterDesktopTransitionHandler,
                exitDesktopTransitionHandler,
                desktopModeDragAndDropTransitionHandler,
                toggleResizeDesktopTaskTransitionHandler,
                dragToDesktopTransitionHandler,
                desktopImmersiveController.get(),
                desktopUserRepositories,
                desktopRepositoryInitializer,
                recentsTransitionHandler,
                multiInstanceHelper,
                mainExecutor,
                mainScope,
                desktopExecutor,
                desktopTasksLimiter,
                recentTasksController.orElse(null),
                interactionJankMonitor,
                mainHandler,
                focusTransitionObserver,
                desktopModeEventLogger,
                desktopModeUiEventLogger,
                desktopWallpaperActivityTokenProvider,
                bubbleController,
                overviewToDesktopTransitionObserver,
                desksOrganizer,
                desksTransitionObserver.get(),
                userProfileContexts,
                desktopModeCompatPolicy,
                dragToDisplayTransitionHandler,
                moveToDisplayTransitionHandler,
                homeIntentProvider,
                desktopState,
                desktopConfig,
                visualIndicatorUpdateScheduler,
                desktopFirstListenerManager);
    }

    @WMSingleton
    @Provides
    static DesktopTilingDecorViewModel provideDesktopTilingViewModel(Context context,
            @ShellMainThread MainCoroutineDispatcher mainDispatcher,
            @ShellBackgroundThread CoroutineScope bgScope,
            DisplayController displayController,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            SyncTransactionQueue syncQueue,
            Transitions transitions,
            ShellTaskOrganizer shellTaskOrganizer,
            ToggleResizeDesktopTaskTransitionHandler toggleResizeDesktopTaskTransitionHandler,
            ReturnToDragStartAnimator returnToDragStartAnimator,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            DesktopModeEventLogger desktopModeEventLogger,
            WindowDecorTaskResourceLoader windowDecorTaskResourceLoader,
            FocusTransitionObserver focusTransitionObserver,
            @ShellMainThread ShellExecutor mainExecutor,
            DesktopState desktopState,
            ShellInit shellInit) {
        return new DesktopTilingDecorViewModel(
                context,
                mainDispatcher,
                bgScope,
                displayController,
                rootTaskDisplayAreaOrganizer,
                syncQueue,
                transitions,
                shellTaskOrganizer,
                toggleResizeDesktopTaskTransitionHandler,
                returnToDragStartAnimator,
                desktopUserRepositories,
                desktopModeEventLogger,
                windowDecorTaskResourceLoader,
                focusTransitionObserver,
                mainExecutor,
                desktopState,
                shellInit
        );
    }

    @WMSingleton
    @Provides
    static Optional<TaskChangeListener> provideDesktopTaskChangeListener(
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            DesktopState desktopState,
            ShellController shellController) {
        if (ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS.isTrue()
                && desktopState.canEnterDesktopMode()) {
            return Optional.of(new DesktopTaskChangeListener(
                    desktopUserRepositories, desktopState, shellController));
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static Optional<DesktopInOrderTransitionObserver> provideDesktopInOrderTransitionObserver(
            ShellInit shellInit,
            Optional<DesktopImmersiveController> desktopImmersiveController,
            FocusTransitionObserver focusTransitionObserver,
            Optional<DesksTransitionObserver> desksTransitionObserver,
            DesktopState desktopState,
            Optional<DesktopImeHandler> desktopImeHandler,
            Optional<DesktopBackNavTransitionObserver> desktopBackNavTransitionObserver) {
        if (enableInorderTransitionCallbacksForDesktop()
                && ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS.isTrue()
                && desktopState.canEnterDesktopMode()) {
            return Optional.of(new DesktopInOrderTransitionObserver(
                    desktopImmersiveController,
                    focusTransitionObserver,
                    desksTransitionObserver,
                    desktopImeHandler,
                    desktopBackNavTransitionObserver));
        }
        return Optional.empty();
    }
    @WMSingleton
    @Provides
    static Optional<DesktopTasksLimiter> provideDesktopTasksLimiter(
            Transitions transitions,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            ShellTaskOrganizer shellTaskOrganizer,
            DesksOrganizer desksOrganizer,
            DesktopConfig desktopConfig,
            DesktopState desktopState,
            Optional<DesktopMixedTransitionHandler> desktopMixedTransitionHandler) {
        if (!desktopState.canEnterDesktopMode()
                || !ENABLE_DESKTOP_WINDOWING_TASK_LIMIT.isTrue()) {
            return Optional.empty();
        }
        int maxTaskLimit = desktopConfig.getMaxTaskLimit();
        return Optional.of(
                new DesktopTasksLimiter(
                        transitions,
                        desktopUserRepositories,
                        shellTaskOrganizer,
                        desksOrganizer,
                        desktopMixedTransitionHandler.get(),
                        maxTaskLimit <= 0 ? null : maxTaskLimit));
    }

    @WMSingleton
    @Provides
    static Optional<DesktopImmersiveController> provideDesktopImmersiveController(
            ShellInit shellInit,
            Transitions transitions,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            DisplayController displayController,
            ShellTaskOrganizer shellTaskOrganizer,
            ShellCommandHandler shellCommandHandler,
            DesktopState desktopState) {
        if (desktopState.canEnterDesktopModeOrShowAppHandle()) {
            return Optional.of(
                    new DesktopImmersiveController(
                            shellInit,
                            transitions,
                            desktopUserRepositories,
                            displayController,
                            shellTaskOrganizer,
                            shellCommandHandler));
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static ReturnToDragStartAnimator provideReturnToDragStartAnimator(
            InteractionJankMonitor interactionJankMonitor) {
        return new ReturnToDragStartAnimator(interactionJankMonitor);
    }

    @WMSingleton
    @Provides
    static DragToDesktopTransitionHandler provideDragToDesktopTransitionHandler(
            Context context,
            Transitions transitions,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            DesksOrganizer desksOrganizer,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            InteractionJankMonitor interactionJankMonitor,
            Optional<BubbleController> bubbleController,
            DesktopState desktopState) {
        return ENABLE_DESKTOP_WINDOWING_ENTER_TRANSITIONS_BUGFIX.isTrue()
                ? new SpringDragToDesktopTransitionHandler(
                context, transitions, rootTaskDisplayAreaOrganizer, desksOrganizer,
                desktopUserRepositories, interactionJankMonitor, bubbleController, desktopState)
                : new DefaultDragToDesktopTransitionHandler(
                        context, transitions, rootTaskDisplayAreaOrganizer, desksOrganizer,
                        desktopUserRepositories, interactionJankMonitor, bubbleController,
                        desktopState);
    }

    @WMSingleton
    @Provides
    static Optional<DisplayDisconnectTransitionHandler> provideDisplayDisconnectTransitionHandler(
            ShellInit shellInit, Transitions transitions) {
        if (!DesktopExperienceFlags.ENABLE_DISPLAY_DISCONNECT_INTERACTION.isTrue()) {
            return Optional.empty();
        } else {
            return Optional.of(
                    new DisplayDisconnectTransitionHandler(transitions, shellInit)
            );
        }
    }

    @WMSingleton
    @Provides
    static DesktopWallpaperActivityTokenProvider provideDesktopWallpaperActivityTokenProvider() {
        return new DesktopWallpaperActivityTokenProvider();
    }

    @WMSingleton
    @Provides
    static DragToDisplayTransitionHandler provideDragToDisplayTransitionHandler() {
        return new DragToDisplayTransitionHandler();
    }

    @WMSingleton
    @Provides
    static DesktopModeMoveToDisplayTransitionHandler provideMoveToDisplayTransitionHandler(
            InteractionJankMonitor interactionJankMonitor,
            @ShellMainThread Handler shellMainHandler,
            DisplayController displayController) {
        return new DesktopModeMoveToDisplayTransitionHandler(
                new SurfaceControl.Transaction(),
                interactionJankMonitor,
                shellMainHandler,
                displayController);
    }

    @WMSingleton
    @Provides
    static Optional<DesktopModeKeyGestureHandler> provideDesktopModeKeyGestureHandler(
            Context context,
            Optional<DesktopModeWindowDecorViewModel> desktopModeWindowDecorViewModel,
            Optional<DesktopTasksController> desktopTasksController,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            InputManager inputManager,
            ShellTaskOrganizer shellTaskOrganizer,
            FocusTransitionObserver focusTransitionObserver,
            @ShellMainThread ShellExecutor mainExecutor,
            DisplayController displayController,
            DesktopState desktopState) {
        if (desktopState.canEnterDesktopMode()
                && (DesktopExperienceFlags.ENABLE_MOVE_TO_NEXT_DISPLAY_SHORTCUT.isTrue()
                || DesktopModeFlags.ENABLE_TASK_RESIZING_KEYBOARD_SHORTCUTS.isTrue())) {
            return Optional.of(new DesktopModeKeyGestureHandler(context,
                    desktopModeWindowDecorViewModel, desktopTasksController,
                    desktopUserRepositories,
                    inputManager, shellTaskOrganizer, focusTransitionObserver,
                    mainExecutor, displayController));
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static Optional<DesktopModeWindowDecorViewModel> provideDesktopModeWindowDecorViewModel(
            Context context,
            @ShellMainThread ShellExecutor shellExecutor,
            @ShellMainThread Handler mainHandler,
            @ShellMainThread Choreographer mainChoreographer,
            @ShellMainThread MainCoroutineDispatcher mainDispatcher,
            @ShellBackgroundThread CoroutineScope bgScope,
            @ShellBackgroundThread ShellExecutor bgExecutor,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            IWindowManager windowManager,
            ShellTaskOrganizer taskOrganizer,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            DisplayController displayController,
            ShellController shellController,
            DisplayInsetsController displayInsetsController,
            SyncTransactionQueue syncQueue,
            Transitions transitions,
            Optional<DesktopTasksController> desktopTasksController,
            Optional<DesktopImmersiveController> desktopImmersiveController,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            InteractionJankMonitor interactionJankMonitor,
            AppToWebGenericLinksParser genericLinksParser,
            AssistContentRequester assistContentRequester,
            WindowDecorViewHostSupplier<WindowDecorViewHost> windowDecorViewHostSupplier,
            MultiInstanceHelper multiInstanceHelper,
            Optional<DesktopTasksLimiter> desktopTasksLimiter,
            AppHandleEducationController appHandleEducationController,
            AppToWebEducationController appToWebEducationController,
            AppHandleAndHeaderVisibilityHelper appHandleAndHeaderVisibilityHelper,
            WindowDecorCaptionRepository windowDecorCaptionRepository,
            Optional<DesktopActivityOrientationChangeHandler> activityOrientationChangeHandler,
            FocusTransitionObserver focusTransitionObserver,
            DesktopModeEventLogger desktopModeEventLogger,
            DesktopModeUiEventLogger desktopModeUiEventLogger,
            WindowDecorTaskResourceLoader taskResourceLoader,
            RecentsTransitionHandler recentsTransitionHandler,
            DesktopModeCompatPolicy desktopModeCompatPolicy,
            DesktopTilingDecorViewModel desktopTilingDecorViewModel,
            MultiDisplayDragMoveIndicatorController multiDisplayDragMoveIndicatorController,
            Optional<CompatUIHandler> compatUI,
            DesksOrganizer desksOrganizer,
            DesktopState desktopState,
            DesktopConfig desktopConfig
    ) {
        if (!desktopState.canEnterDesktopModeOrShowAppHandle()) {
            return Optional.empty();
        }
        return Optional.of(new DesktopModeWindowDecorViewModel(context, shellExecutor, mainHandler,
                mainChoreographer, mainDispatcher, bgScope, bgExecutor,
                shellInit, shellCommandHandler, windowManager,
                taskOrganizer, desktopUserRepositories, displayController, shellController,
                displayInsetsController, syncQueue, transitions, desktopTasksController,
                desktopImmersiveController.get(),
                rootTaskDisplayAreaOrganizer, interactionJankMonitor, genericLinksParser,
                assistContentRequester, windowDecorViewHostSupplier, multiInstanceHelper,
                desktopTasksLimiter, appHandleEducationController, appToWebEducationController,
                appHandleAndHeaderVisibilityHelper, windowDecorCaptionRepository,
                activityOrientationChangeHandler, focusTransitionObserver, desktopModeEventLogger,
                desktopModeUiEventLogger, taskResourceLoader, recentsTransitionHandler,
                desktopModeCompatPolicy, desktopTilingDecorViewModel,
                multiDisplayDragMoveIndicatorController, compatUI.orElse(null),
                desksOrganizer, desktopState, desktopConfig));
    }

    @WMSingleton
    @Provides
    static MultiDisplayDragMoveIndicatorController
            providesMultiDisplayDragMoveIndicatorController(
            DisplayController displayController,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            MultiDisplayDragMoveIndicatorSurface.Factory
                multiDisplayDragMoveIndicatorSurfaceFactory,
            @ShellDesktopThread ShellExecutor desktopExecutor,
            DesktopState desktopState
    ) {
        return new MultiDisplayDragMoveIndicatorController(
                displayController, rootTaskDisplayAreaOrganizer,
                multiDisplayDragMoveIndicatorSurfaceFactory, desktopExecutor, desktopState);
    }

    @WMSingleton
    @Provides
    static MultiDisplayDragMoveIndicatorSurface.Factory
            providesMultiDisplayDragMoveIndicatorSurfaceFactory() {
        return new MultiDisplayDragMoveIndicatorSurface.Factory();
    }

    @WMSingleton
    @Provides
    static AppHandleAndHeaderVisibilityHelper provideAppHandleAndHeaderVisibilityHelper(
            @NonNull DisplayController displayController,
            @NonNull DesktopModeCompatPolicy desktopModeCompatPolicy,
            @NonNull DesktopState desktopState,
            Optional<BubbleController> bubbleController) {
        return new AppHandleAndHeaderVisibilityHelper(displayController,
                desktopModeCompatPolicy, desktopState, bubbleController);
    }

    @WMSingleton
    @Provides
    static WindowDecorTaskResourceLoader provideWindowDecorTaskResourceLoader(
            @NonNull Context context, @NonNull ShellInit shellInit,
            @NonNull ShellController shellController,
            @NonNull ShellCommandHandler shellCommandHandler,
            @NonNull UserProfileContexts userProfileContexts) {
        return new WindowDecorTaskResourceLoader(context, shellInit, shellController,
                shellCommandHandler, userProfileContexts);
    }

    @WMSingleton
    @Provides
    static Optional<SystemModalsTransitionHandler> provideSystemModalsTransitionHandler(
            Context context,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellAnimationThread ShellExecutor animExecutor,
            ShellInit shellInit,
            Transitions transitions,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            DesktopModeCompatPolicy desktopModeCompatPolicy,
            DesktopState desktopState) {
        if (!desktopState.canEnterDesktopMode()
                || !ENABLE_DESKTOP_WINDOWING_MODALS_POLICY.isTrue()
                || !ENABLE_DESKTOP_SYSTEM_DIALOGS_TRANSITIONS.isTrue()) {
            return Optional.empty();
        }
        return Optional.of(
                new SystemModalsTransitionHandler(
                        context, mainExecutor, animExecutor, shellInit, transitions,
                        desktopUserRepositories, desktopModeCompatPolicy));
    }

    @WMSingleton
    @Provides
    static EnterDesktopTaskTransitionHandler provideEnterDesktopModeTaskTransitionHandler(
            Transitions transitions,
            Context context,
            Optional<DesktopTasksLimiter> desktopTasksLimiter,
            InteractionJankMonitor interactionJankMonitor,
            LatencyTracker latencyTracker) {
        return new EnterDesktopTaskTransitionHandler(
                transitions, context, interactionJankMonitor, latencyTracker);
    }

    @WMSingleton
    @Provides
    static ToggleResizeDesktopTaskTransitionHandler provideToggleResizeDesktopTaskTransitionHandler(
            Transitions transitions, InteractionJankMonitor interactionJankMonitor) {
        return new ToggleResizeDesktopTaskTransitionHandler(transitions, interactionJankMonitor);
    }

    @WMSingleton
    @Provides
    static ExitDesktopTaskTransitionHandler provideExitDesktopTaskTransitionHandler(
            Transitions transitions,
            Context context,
            InteractionJankMonitor interactionJankMonitor,
            @ShellMainThread Handler handler) {
        return new ExitDesktopTaskTransitionHandler(
                transitions, context, interactionJankMonitor, handler);
    }

    @WMSingleton
    @Provides
    static CloseDesktopTaskTransitionHandler provideCloseDesktopTaskTransitionHandler(
            Context context,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellAnimationThread ShellExecutor animExecutor,
            @ShellAnimationThread Handler animHandler) {
        return new CloseDesktopTaskTransitionHandler(context, mainExecutor, animExecutor,
                animHandler);
    }

    @WMSingleton
    @Provides
    static DesktopMinimizationTransitionHandler provideDesktopMinimizationTransitionHandler(
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellAnimationThread ShellExecutor animExecutor,
            DisplayController displayController,
            @ShellAnimationThread Handler mainHandler) {
        return new DesktopMinimizationTransitionHandler(mainExecutor, animExecutor,
                displayController, mainHandler);
    }

    @WMSingleton
    @Provides
    static DesktopModeDragAndDropTransitionHandler provideDesktopModeDragAndDropTransitionHandler(
            Transitions transitions, DesktopModeDragAndDropAnimatorHelper animatorHelper) {
        return new DesktopModeDragAndDropTransitionHandler(transitions, animatorHelper);
    }

    @WMSingleton
    @Provides
    static DesktopModeDragAndDropAnimatorHelper provideDesktopModeDragAndDropAnimatorHelper(
            Context context) {
        return new DesktopModeDragAndDropAnimatorHelper(context, SurfaceControl.Transaction::new);
    }

    @WMSingleton
    @Provides
    @DynamicOverride
    static DesktopUserRepositories provideDesktopUserRepositories(
            ShellInit shellInit,
            ShellController shellController,
            DesktopPersistentRepository desktopPersistentRepository,
            DesktopRepositoryInitializer desktopRepositoryInitializer,
            @ShellMainThread CoroutineScope mainScope,
            UserManager userManager,
            DesktopState desktopState,
            DesktopConfig desktopConfig
    ) {
        return new DesktopUserRepositories(shellInit, shellController,
                desktopPersistentRepository,
                desktopRepositoryInitializer,
                mainScope, userManager, desktopState, desktopConfig);
    }

    @WMSingleton
    @Provides
    static Optional<DesktopActivityOrientationChangeHandler> provideActivityOrientationHandler(
            Context context,
            ShellInit shellInit,
            ShellTaskOrganizer shellTaskOrganizer,
            TaskStackListenerImpl taskStackListener,
            ToggleResizeDesktopTaskTransitionHandler toggleResizeDesktopTaskTransitionHandler,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            DisplayController displayController,
            DesktopState desktopState) {
        if (desktopState.canEnterDesktopMode()) {
            return Optional.of(
                    new DesktopActivityOrientationChangeHandler(
                            context,
                            shellInit,
                            shellTaskOrganizer,
                            taskStackListener,
                            toggleResizeDesktopTaskTransitionHandler,
                            desktopUserRepositories,
                            displayController,
                            desktopState));
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static Optional<DesktopTasksTransitionObserver> provideDesktopTasksTransitionObserver(
            Optional<DesktopUserRepositories> desktopUserRepositories,
            Transitions transitions,
            ShellTaskOrganizer shellTaskOrganizer,
            Optional<DesktopMixedTransitionHandler> desktopMixedTransitionHandler,
            DesktopWallpaperActivityTokenProvider desktopWallpaperActivityTokenProvider,
            DesktopState desktopState,
            ShellInit shellInit) {
        return desktopUserRepositories.flatMap(
                repository ->
                        Optional.of(
                                new DesktopTasksTransitionObserver(
                                        repository,
                                        transitions,
                                        shellTaskOrganizer,
                                        desktopMixedTransitionHandler.get(),
                                        desktopWallpaperActivityTokenProvider,
                                        desktopState,
                                        shellInit)));
    }

    @WMSingleton
    @Provides
    static Optional<DesktopBackNavTransitionObserver> provideDesktopBackNavTransitionObserver(
            Optional<DesktopUserRepositories> desktopUserRepositories,
            Optional<DesktopMixedTransitionHandler> desktopMixedTransitionHandler,
            Optional<BackAnimationController> backAnimationController,
            DesksOrganizer desksOrganizer,
            Transitions transitions,
            DesktopState desktopState,
            ShellInit shellInit) {
        return desktopUserRepositories.flatMap(
                repository ->
                        Optional.of(
                                new DesktopBackNavTransitionObserver(
                                        repository,
                                        desktopMixedTransitionHandler.get(),
                                        backAnimationController.get(),
                                        desksOrganizer,
                                        transitions,
                                        desktopState,
                                        shellInit)));
    }

    @WMSingleton
    @Provides
    static Optional<DesksTransitionObserver> provideDesksTransitionObserver(
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            @NonNull DesksOrganizer desksOrganizer,
            @NonNull Transitions transitions,
            @NonNull ShellController shellController,
            @NonNull DesktopWallpaperActivityTokenProvider desktopWallpaperActivityTokenProvider,
            @NonNull @ShellMainThread CoroutineScope mainScope,
            DesktopState desktopState
    ) {
        if (desktopState.canEnterDesktopModeOrShowAppHandle()) {
            return Optional.of(
                    new DesksTransitionObserver(desktopUserRepositories, desksOrganizer,
                            transitions, shellController, desktopWallpaperActivityTokenProvider,
                            mainScope));
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static Optional<DesktopMixedTransitionHandler> provideDesktopMixedTransitionHandler(
            Context context,
            Transitions transitions,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            FreeformTaskTransitionHandler freeformTaskTransitionHandler,
            CloseDesktopTaskTransitionHandler closeDesktopTaskTransitionHandler,
            Optional<DesktopImmersiveController> desktopImmersiveController,
            DesktopMinimizationTransitionHandler desktopMinimizationTransitionHandler,
            DesktopModeDragAndDropTransitionHandler desktopModeDragAndDropTransitionHandler,
            Optional<SystemModalsTransitionHandler> systemModalsTransitionHandler,
            InteractionJankMonitor interactionJankMonitor,
            @ShellMainThread Handler handler,
            ShellInit shellInit,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            DesktopState desktopState
    ) {
        if (!desktopState.canEnterDesktopMode()
                && !desktopState.overridesShowAppHandle()) {
            return Optional.empty();
        }
        return Optional.of(
                new DesktopMixedTransitionHandler(
                        context,
                        transitions,
                        desktopUserRepositories,
                        freeformTaskTransitionHandler,
                        closeDesktopTaskTransitionHandler,
                        desktopImmersiveController.get(),
                        desktopMinimizationTransitionHandler,
                        desktopModeDragAndDropTransitionHandler,
                        systemModalsTransitionHandler,
                        interactionJankMonitor,
                        handler,
                        shellInit,
                        rootTaskDisplayAreaOrganizer));
    }

    @WMSingleton
    @Provides
    static DesktopModeLoggerTransitionObserver provideDesktopModeLoggerTransitionObserver(
            ShellInit shellInit,
            Transitions transitions,
            DesktopModeEventLogger desktopModeEventLogger,
            Optional<DesktopTasksLimiter> desktopTasksLimiter,
            DesktopState desktopState) {
        return new DesktopModeLoggerTransitionObserver(
                shellInit, transitions, desktopModeEventLogger,
                desktopTasksLimiter, desktopState);
    }

    @WMSingleton
    @Provides
    static DesktopModeEventLogger provideDesktopModeEventLogger() {
        return new DesktopModeEventLogger();
    }

    @WMSingleton
    @Provides
    static Optional<DesktopDisplayEventHandler> provideDesktopDisplayEventHandler(
            ShellInit shellInit,
            @ShellMainThread CoroutineScope mainScope,
            ShellController shellController,
            DisplayController displayController,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            DesksOrganizer desksOrganizer,
            Optional<DesktopUserRepositories> desktopUserRepositories,
            Optional<DesktopTasksController> desktopTasksController,
            Optional<DesktopDisplayModeController> desktopDisplayModeController,
            DesktopRepositoryInitializer desktopRepositoryInitializer,
            Optional<DesksTransitionObserver> desksTransitionObserver,
            DesktopState desktopState
    ) {
        if (!desktopState.canEnterDesktopMode()) {
            return Optional.empty();
        }
        return Optional.of(
                new DesktopDisplayEventHandler(
                        shellInit,
                        mainScope,
                        shellController,
                        displayController,
                        rootTaskDisplayAreaOrganizer,
                        desksOrganizer,
                        desktopRepositoryInitializer,
                        desktopUserRepositories.get(),
                        desktopTasksController.get(),
                        desktopDisplayModeController.get(),
                        desksTransitionObserver.get(),
                        desktopState));
    }

    @WMSingleton
    @Provides
    static Optional<DesktopFirstListenerManager> provideDesktopFirstListenerManager(
            @NonNull DesktopState desktopState,
            @NonNull ShellInit shellInit,
            @NonNull RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            @NonNull DisplayController displayController
    ) {
        if (desktopState.canEnterDesktopMode()
                && DesktopExperienceFlags.ENABLE_DESKTOP_FIRST_LISTENER.isTrue()) {
            return Optional.of(
                    new DesktopFirstListenerManager(shellInit, rootTaskDisplayAreaOrganizer,
                            displayController));
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static AppHandleNotifier provideAppHandleNotifier(
            @ShellMainThread ShellExecutor shellExecutor,
            WindowDecorCaptionRepository windowDecorCaptionRepository,
            @ShellMainThread CoroutineScope mainScope) {
        return new AppHandleNotifier(
                shellExecutor, windowDecorCaptionRepository, mainScope);
    }

    @WMSingleton
    @Provides
    static AppHandleEducationDatastoreRepository provideAppHandleEducationDatastoreRepository(
            Context context) {
        return new AppHandleEducationDatastoreRepository(context);
    }

    @WMSingleton
    @Provides
    static AppHandleEducationFilter provideAppHandleEducationFilter(
            Context context,
            AppHandleEducationDatastoreRepository appHandleEducationDatastoreRepository) {
        return new AppHandleEducationFilter(context, appHandleEducationDatastoreRepository);
    }

    @WMSingleton
    @Provides
    static WindowDecorCaptionRepository provideAppHandleRepository() {
        return new WindowDecorCaptionRepository();
    }

    @WMSingleton
    @Provides
    static DesktopWindowingEducationTooltipController
    provideDesktopWindowingEducationTooltipController(
            Context context,
            AdditionalSystemViewContainer.Factory additionalSystemViewContainerFactory,
            DisplayController displayController) {
        return new DesktopWindowingEducationTooltipController(
                context, additionalSystemViewContainerFactory, displayController);
    }

    @WMSingleton
    @Provides
    static DesktopWindowingEducationPromoController provideDesktopWindowingEducationPromoController(
            Context context,
            AdditionalSystemViewContainer.Factory additionalSystemViewContainerFactory,
            DisplayController displayController
    ) {
        return new DesktopWindowingEducationPromoController(
                context,
                additionalSystemViewContainerFactory,
                displayController
        );
    }

    @OptIn(markerClass = ExperimentalCoroutinesApi.class)
    @WMSingleton
    @Provides
    static AppHandleEducationController provideAppHandleEducationController(
            Context context,
            AppHandleEducationFilter appHandleEducationFilter,
            AppHandleEducationDatastoreRepository appHandleEducationDatastoreRepository,
            WindowDecorCaptionRepository windowDecorCaptionRepository,
            DesktopWindowingEducationTooltipController desktopWindowingEducationTooltipController,
            @ShellMainThread CoroutineScope applicationScope,
            @ShellBackgroundThread MainCoroutineDispatcher backgroundDispatcher,
            DesktopModeUiEventLogger desktopModeUiEventLogger,
            DesktopState desktopState) {
        return new AppHandleEducationController(
                context,
                appHandleEducationFilter,
                appHandleEducationDatastoreRepository,
                windowDecorCaptionRepository,
                desktopWindowingEducationTooltipController,
                applicationScope,
                backgroundDispatcher,
                desktopModeUiEventLogger,
                desktopState);
    }

    @WMSingleton
    @Provides
    static AppToWebEducationDatastoreRepository provideAppToWebEducationDatastoreRepository(
            Context context) {
        return new AppToWebEducationDatastoreRepository(context);
    }

    @WMSingleton
    @Provides
    static AppToWebEducationFilter provideAppToWebEducationFilter(
            Context context,
            AppToWebEducationDatastoreRepository appToWebEducationDatastoreRepository) {
        return new AppToWebEducationFilter(context, appToWebEducationDatastoreRepository);
    }

    @OptIn(markerClass = ExperimentalCoroutinesApi.class)
    @WMSingleton
    @Provides
    static AppToWebEducationController provideAppToWebEducationController(
            Context context,
            AppToWebEducationFilter appToWebEducationFilter,
            AppToWebEducationDatastoreRepository appToWebEducationDatastoreRepository,
            WindowDecorCaptionRepository windowDecorCaptionRepository,
            DesktopWindowingEducationPromoController desktopWindowingEducationPromoController,
            @ShellMainThread CoroutineScope applicationScope,
            @ShellBackgroundThread MainCoroutineDispatcher backgroundDispatcher,
            DesktopState desktopState) {
        return new AppToWebEducationController(context, appToWebEducationFilter,
                appToWebEducationDatastoreRepository, windowDecorCaptionRepository,
                desktopWindowingEducationPromoController, applicationScope,
                backgroundDispatcher, desktopState);
    }

    @WMSingleton
    @Provides
    static DesktopPersistentRepository provideDesktopPersistentRepository(
            Context context, @ShellBackgroundThread CoroutineScope bgScope) {
        return new DesktopPersistentRepository(context, bgScope);
    }

    @WMSingleton
    @Provides
    static DesktopRepositoryInitializer provideDesktopRepositoryInitializer(
            Context context,
            DesktopPersistentRepository desktopPersistentRepository,
            @ShellMainThread CoroutineScope mainScope,
            DesktopConfig desktopConfig,
            DesktopState desktopState) {
        return new DesktopRepositoryInitializerImpl(context, desktopPersistentRepository,
                mainScope, desktopConfig, desktopState);
    }

    @WMSingleton
    @Provides
    static DesktopModeUiEventLogger provideDesktopUiEventLogger(
            UiEventLogger uiEventLogger,
            PackageManager packageManager
    ) {
        return new DesktopModeUiEventLogger(uiEventLogger, packageManager);
    }

    @WMSingleton
    @Provides
    static Optional<DesktopDisplayModeController> provideDesktopDisplayModeController(
            Context context,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            Transitions transitions,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            IWindowManager windowManager,
            ShellTaskOrganizer shellTaskOrganizer,
            DesktopWallpaperActivityTokenProvider desktopWallpaperActivityTokenProvider,
            InputManager inputManager,
            DisplayController displayController,
            @ShellMainThread Handler mainHandler,
            DesktopState desktopState
    ) {
        if (!desktopState.canEnterDesktopMode()) {
            return Optional.empty();
        }
        return Optional.of(
                new DesktopDisplayModeController(
                        context,
                        shellInit,
                        shellCommandHandler,
                        transitions,
                        rootTaskDisplayAreaOrganizer,
                        windowManager,
                        shellTaskOrganizer,
                        desktopWallpaperActivityTokenProvider,
                        inputManager,
                        displayController,
                        mainHandler,
                        desktopState));
    }

    @WMSingleton
    @Provides
    static Optional<DesktopImeHandler> provideDesktopImeHandler(
            Optional<DesktopTasksController> desktopTasksController,
            Optional<DesktopUserRepositories> desktopUserRepositories,
            FocusTransitionObserver focusTransitionObserver,
            DisplayImeController displayImeController,
            DisplayController displayController,
            ShellTaskOrganizer shellTaskOrganizer,
            Transitions transitions,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellAnimationThread ShellExecutor animExecutor,
            Context context,
            ShellInit shellInit,
            DesktopState desktopState) {
        if (!desktopState.canEnterDesktopMode()) {
            return Optional.empty();
        }
        return Optional.of(
                new DesktopImeHandler(desktopTasksController.get(), desktopUserRepositories.get(),
                        focusTransitionObserver, shellTaskOrganizer,
                        displayImeController, displayController, transitions, mainExecutor,
                        animExecutor, context, shellInit));
    }

    @WMSingleton
    @Provides
    static VisualIndicatorUpdateScheduler provideVisualIndicatorUpdateScheduler(
            ShellInit shellInit,
            @ShellMainThread MainCoroutineDispatcher mainDispatcher,
            @ShellBackgroundThread CoroutineScope bgScope,
            DisplayController displayController) {
        return new VisualIndicatorUpdateScheduler(shellInit, mainDispatcher, bgScope,
                displayController);
    }

    //
    // App zoom out
    //

    @WMSingleton
    @Provides
    static AppZoomOutController provideAppZoomOutController(
            Context context,
            ShellInit shellInit,
            ShellTaskOrganizer shellTaskOrganizer,
            DisplayController displayController,
            DisplayLayout displayLayout,
            @ShellMainThread ShellExecutor mainExecutor,
            InteractionJankMonitor interactionJankMonitor) {
        return AppZoomOutController.create(context, shellInit, shellTaskOrganizer,
                displayController, displayLayout, mainExecutor, interactionJankMonitor);
    }

    //
    // Drag and drop
    //

    @WMSingleton
    @Provides
    static GlobalDragListener provideGlobalDragListener(
            IWindowManager wmService, @ShellMainThread ShellExecutor mainExecutor) {
        return new GlobalDragListener(wmService, mainExecutor);
    }

    @WMSingleton
    @Provides
    static DragAndDropController provideDragAndDropController(
            Context context,
            ShellInit shellInit,
            ShellController shellController,
            ShellCommandHandler shellCommandHandler,
            ShellTaskOrganizer shellTaskOrganizer,
            DisplayController displayController,
            UiEventLogger uiEventLogger,
            IconProvider iconProvider,
            GlobalDragListener globalDragListener,
            Transitions transitions,
            Lazy<DragToBubbleController> dragToBubbleControllerLazy,
            @ShellMainThread ShellExecutor mainExecutor,
            DesktopState desktopState) {
        return new DragAndDropController(
                context,
                shellInit,
                shellController,
                shellCommandHandler,
                shellTaskOrganizer,
                displayController,
                uiEventLogger,
                iconProvider,
                globalDragListener,
                transitions,
                dragToBubbleControllerLazy,
                mainExecutor,
                desktopState);
    }

    @WMSingleton
    @Provides
    static DragToBubbleController getDragToBubbleController(Context context,
            BubblePositioner bubblePositioner, BubbleController bubbleController) {
        return new DragToBubbleController(context, bubblePositioner, bubbleController);
    }

    //
    // Misc
    //

    // TODO: Temporarily move dependencies to this instead of ShellInit since that is needed to add
    // the callback. We will be moving to a different explicit startup mechanism in a follow- up CL.
    @WMSingleton
    @ShellCreateTriggerOverride
    @Provides
    static Object provideIndependentShellComponentsToCreate(
            DragAndDropController dragAndDropController,
            @NonNull DelegateLetterboxTransitionObserver letterboxTransitionObserver,
            @NonNull LetterboxCommandHandler letterboxCommandHandler,
            @NonNull LetterboxTaskListenerAdapter letterboxTaskListenerAdapter,
            @NonNull LetterboxCleanupAdapter letterboxCleanupAdapter,
            Optional<DesktopTasksTransitionObserver> desktopTasksTransitionObserverOptional,
            Optional<DesktopDisplayEventHandler> desktopDisplayEventHandler,
            Optional<DesktopModeKeyGestureHandler> desktopModeKeyGestureHandler,
            Optional<SystemModalsTransitionHandler> systemModalsTransitionHandler,
            Optional<DisplayDisconnectTransitionHandler> displayDisconnectTransitionHandler,
            Optional<DesktopImeHandler> desktopImeHandler,
            ShellCrashHandler shellCrashHandler) {
        return new Object();
    }

    @WMSingleton
    @Provides
    static OverviewToDesktopTransitionObserver provideOverviewToDesktopTransitionObserver(
            Transitions transitions, ShellInit shellInit) {
        return new OverviewToDesktopTransitionObserver(transitions, shellInit);
    }

    @WMSingleton
    @Provides
    static UserProfileContexts provideUserProfilesContexts(
            Context context,
            ShellController shellController,
            ShellInit shellInit) {
        return new UserProfileContexts(context, shellController, shellInit);
    }

    @WMSingleton
    @Provides
    static ShellCrashHandler provideShellCrashHandler(
            ShellTaskOrganizer shellTaskOrganizer,
            HomeIntentProvider homeIntentProvider,
            DesktopState desktopState,
            ShellInit shellInit) {
        return new ShellCrashHandler(shellTaskOrganizer, homeIntentProvider, desktopState,
                shellInit);
    }

    @WMSingleton
    @Provides
    static HomeIntentProvider provideHomeIntentProvider(Context context) {
        return new HomeIntentProvider(context);
    }

}
