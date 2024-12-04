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

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.os.Handler;
import android.os.UserManager;
import android.view.Choreographer;
import android.view.IWindowManager;
import android.view.WindowManager;

import com.android.internal.jank.InteractionJankMonitor;
import com.android.internal.logging.UiEventLogger;
import com.android.internal.statusbar.IStatusBarService;
import com.android.launcher3.icons.IconProvider;
import com.android.window.flags.Flags;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.WindowManagerShellWrapper;
import com.android.wm.shell.activityembedding.ActivityEmbeddingController;
import com.android.wm.shell.bubbles.BubbleController;
import com.android.wm.shell.bubbles.BubbleData;
import com.android.wm.shell.bubbles.BubbleDataRepository;
import com.android.wm.shell.bubbles.BubbleEducationController;
import com.android.wm.shell.bubbles.BubbleLogger;
import com.android.wm.shell.bubbles.BubblePositioner;
import com.android.wm.shell.bubbles.properties.ProdBubbleProperties;
import com.android.wm.shell.bubbles.storage.BubblePersistentRepository;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.FloatingContentCoordinator;
import com.android.wm.shell.common.LaunchAdjacentController;
import com.android.wm.shell.common.MultiInstanceHelper;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.TaskStackListenerImpl;
import com.android.wm.shell.common.TransactionPool;
import com.android.wm.shell.dagger.back.ShellBackAnimationModule;
import com.android.wm.shell.dagger.pip.PipModule;
import com.android.wm.shell.desktopmode.DesktopModeEventLogger;
import com.android.wm.shell.desktopmode.DesktopModeLoggerTransitionObserver;
import com.android.wm.shell.desktopmode.DesktopModeTaskRepository;
import com.android.wm.shell.desktopmode.DesktopTasksController;
import com.android.wm.shell.desktopmode.DesktopTasksLimiter;
import com.android.wm.shell.desktopmode.DesktopTasksTransitionObserver;
import com.android.wm.shell.desktopmode.DragToDesktopTransitionHandler;
import com.android.wm.shell.desktopmode.EnterDesktopTaskTransitionHandler;
import com.android.wm.shell.desktopmode.ExitDesktopTaskTransitionHandler;
import com.android.wm.shell.desktopmode.ToggleResizeDesktopTaskTransitionHandler;
import com.android.wm.shell.draganddrop.DragAndDropController;
import com.android.wm.shell.draganddrop.GlobalDragListener;
import com.android.wm.shell.freeform.FreeformComponents;
import com.android.wm.shell.freeform.FreeformTaskListener;
import com.android.wm.shell.freeform.FreeformTaskTransitionHandler;
import com.android.wm.shell.freeform.FreeformTaskTransitionObserver;
import com.android.wm.shell.keyguard.KeyguardTransitionHandler;
import com.android.wm.shell.onehanded.OneHandedController;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.recents.RecentTasksController;
import com.android.wm.shell.recents.RecentsTransitionHandler;
import com.android.wm.shell.shared.DesktopModeStatus;
import com.android.wm.shell.shared.annotations.ShellAnimationThread;
import com.android.wm.shell.shared.annotations.ShellBackgroundThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.taskview.TaskViewTransitions;
import com.android.wm.shell.transition.DefaultMixedHandler;
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

import dagger.Binds;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

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
@Module(
        includes = {
                WMShellBaseModule.class,
                PipModule.class,
                ShellBackAnimationModule.class,
        })
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
    static BubblePositioner provideBubblePositioner(Context context,
            WindowManager windowManager) {
        return new BubblePositioner(context, windowManager);
    }

    @WMSingleton
    @Provides
    static BubbleEducationController provideBubbleEducationProvider(Context context) {
        return new BubbleEducationController(context);
    }

    @WMSingleton
    @Provides
    static BubbleData provideBubbleData(Context context,
            BubbleLogger logger,
            BubblePositioner positioner,
            BubbleEducationController educationController,
            @ShellMainThread ShellExecutor mainExecutor) {
        return new BubbleData(context, logger, positioner, educationController, mainExecutor);
    }

    // Note: Handler needed for LauncherApps.register
    @WMSingleton
    @Provides
    static BubbleController provideBubbleController(Context context,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            ShellController shellController,
            BubbleData data,
            FloatingContentCoordinator floatingContentCoordinator,
            IStatusBarService statusBarService,
            WindowManager windowManager,
            WindowManagerShellWrapper windowManagerShellWrapper,
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
            TaskViewTransitions taskViewTransitions,
            Transitions transitions,
            SyncTransactionQueue syncQueue,
            IWindowManager wmService) {
        return new BubbleController(context, shellInit, shellCommandHandler, shellController, data,
                null /* synchronizer */, floatingContentCoordinator,
                new BubbleDataRepository(launcherApps, mainExecutor,
                        new BubblePersistentRepository(context)),
                statusBarService, windowManager, windowManagerShellWrapper, userManager,
                launcherApps, logger, taskStackListener, organizer, positioner, displayController,
                oneHandedOptional, dragAndDropController, mainExecutor, mainHandler, bgExecutor,
                taskViewTransitions, transitions, syncQueue, wmService,
                ProdBubbleProperties.INSTANCE);
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
            ShellInit shellInit,
            IWindowManager windowManager,
            ShellCommandHandler shellCommandHandler,
            ShellTaskOrganizer taskOrganizer,
            DisplayController displayController,
            ShellController shellController,
            DisplayInsetsController displayInsetsController,
            SyncTransactionQueue syncQueue,
            Transitions transitions,
            Optional<DesktopTasksController> desktopTasksController,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer) {
        if (DesktopModeStatus.canEnterDesktopMode(context)) {
            return new DesktopModeWindowDecorViewModel(
                    context,
                    mainExecutor,
                    mainHandler,
                    mainChoreographer,
                    shellInit,
                    shellCommandHandler,
                    windowManager,
                    taskOrganizer,
                    displayController,
                    shellController,
                    displayInsetsController,
                    syncQueue,
                    transitions,
                    desktopTasksController,
                    rootTaskDisplayAreaOrganizer);
        }
        return new CaptionWindowDecorViewModel(
                context,
                mainHandler,
                mainChoreographer,
                taskOrganizer,
                displayController,
                rootTaskDisplayAreaOrganizer,
                syncQueue,
                transitions);
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
            FreeformTaskTransitionObserver transitionObserver) {
        return new FreeformComponents(
                taskListener, Optional.of(transitionHandler), Optional.of(transitionObserver));
    }

    @WMSingleton
    @Provides
    static FreeformTaskListener provideFreeformTaskListener(
            Context context,
            ShellInit shellInit,
            ShellTaskOrganizer shellTaskOrganizer,
            Optional<DesktopModeTaskRepository> desktopModeTaskRepository,
            WindowDecorViewModel windowDecorViewModel) {
        // TODO(b/238217847): Temporarily add this check here until we can remove the dynamic
        //                    override for this controller from the base module
        ShellInit init = FreeformComponents.isFreeformEnabled(context)
                ? shellInit
                : null;
        return new FreeformTaskListener(context, init, shellTaskOrganizer,
                desktopModeTaskRepository, windowDecorViewModel);
    }

    @WMSingleton
    @Provides
    static FreeformTaskTransitionHandler provideFreeformTaskTransitionHandler(
            ShellInit shellInit,
            Transitions transitions,
            Context context,
            WindowDecorViewModel windowDecorViewModel,
            DisplayController displayController,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellAnimationThread ShellExecutor animExecutor) {
        return new FreeformTaskTransitionHandler(shellInit, transitions, context,
                windowDecorViewModel, displayController, mainExecutor, animExecutor);
    }

    @WMSingleton
    @Provides
    static FreeformTaskTransitionObserver provideFreeformTaskTransitionObserver(
            Context context,
            ShellInit shellInit,
            Transitions transitions,
            WindowDecorViewModel windowDecorViewModel) {
        return new FreeformTaskTransitionObserver(
                context, shellInit, transitions, windowDecorViewModel);
    }

    //
    // One handed mode
    //


    // Needs the shell main handler for ContentObserver callbacks
    @WMSingleton
    @Provides
    @DynamicOverride
    static OneHandedController provideOneHandedController(Context context,
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
        return OneHandedController.create(context, shellInit, shellCommandHandler, shellController,
                windowManager, displayController, displayLayout, taskStackListener, jankMonitor,
                uiEventLogger, mainExecutor, mainHandler);
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
            MultiInstanceHelper multiInstanceHelper,
            @ShellMainThread ShellExecutor mainExecutor) {
        return new SplitScreenController(context, shellInit, shellCommandHandler, shellController,
                shellTaskOrganizer, syncQueue, rootTaskDisplayAreaOrganizer, displayController,
                displayImeController, displayInsetsController, dragAndDropController, transitions,
                transactionPool, iconProvider, recentTasks, launchAdjacentController,
                windowDecorViewModel, desktopTasksController, null /* stageCoordinator */,
                multiInstanceHelper, mainExecutor);
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
            Transitions transitions) {
        return new DefaultMixedHandler(shellInit, transitions, splitScreenOptional,
                pipTransitionController, recentsTransitionHandler,
                keyguardTransitionHandler, desktopTasksController,
                unfoldHandler, activityEmbeddingController);
    }

    @WMSingleton
    @Provides
    static RecentsTransitionHandler provideRecentsTransitionHandler(
            ShellInit shellInit,
            Transitions transitions,
            Optional<RecentTasksController> recentTasksController,
            HomeTransitionObserver homeTransitionObserver) {
        return new RecentsTransitionHandler(shellInit, transitions,
                recentTasksController.orElse(null), homeTransitionObserver);
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
            @ShellMainThread ShellExecutor mainExecutor
    ) {
        final List<UnfoldTaskAnimator> animators = new ArrayList<>();
        animators.add(splitAnimator);
        animators.add(fullscreenAnimator);

        return new UnfoldAnimationController(
                shellInit,
                transactionPool,
                progressProvider.get(),
                animators,
                unfoldTransitionHandler,
                mainExecutor
        );
    }

    @Provides
    static FullscreenUnfoldTaskAnimator provideFullscreenUnfoldTaskAnimator(
            Context context,
            UnfoldBackgroundController unfoldBackgroundController,
            ShellController shellController,
            DisplayInsetsController displayInsetsController
    ) {
        return new FullscreenUnfoldTaskAnimator(context, unfoldBackgroundController,
                shellController, displayInsetsController);
    }

    @Provides
    static SplitTaskUnfoldAnimator provideSplitTaskUnfoldAnimatorBase(
            Context context,
            UnfoldBackgroundController backgroundController,
            ShellController shellController,
            @ShellMainThread ShellExecutor executor,
            Lazy<Optional<SplitScreenController>> splitScreenOptional,
            DisplayInsetsController displayInsetsController
    ) {
        // TODO(b/238217847): The lazy reference here causes some dependency issues since it
        // immediately registers a listener on that controller on init.  We should reference the
        // controller directly once we refactor ShellTaskOrganizer to not depend on the unfold
        // animation controller directly.
        return new SplitTaskUnfoldAnimator(context, executor, splitScreenOptional,
                shellController, backgroundController, displayInsetsController);
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
            ShellInit shellInit) {
        return new UnfoldTransitionHandler(shellInit, progressProvider.get(), animator,
                unfoldAnimator, transactionPool, executor, transitions);
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
            EnterDesktopTaskTransitionHandler enterDesktopTransitionHandler,
            ExitDesktopTaskTransitionHandler exitDesktopTransitionHandler,
            ToggleResizeDesktopTaskTransitionHandler toggleResizeDesktopTaskTransitionHandler,
            DragToDesktopTransitionHandler dragToDesktopTransitionHandler,
            @DynamicOverride DesktopModeTaskRepository desktopModeTaskRepository,
            DesktopModeLoggerTransitionObserver desktopModeLoggerTransitionObserver,
            LaunchAdjacentController launchAdjacentController,
            RecentsTransitionHandler recentsTransitionHandler,
            MultiInstanceHelper multiInstanceHelper,
            @ShellMainThread ShellExecutor mainExecutor,
            Optional<DesktopTasksLimiter> desktopTasksLimiter,
            Optional<RecentTasksController> recentTasksController) {
        return new DesktopTasksController(context, shellInit, shellCommandHandler, shellController,
                displayController, shellTaskOrganizer, syncQueue, rootTaskDisplayAreaOrganizer,
                dragAndDropController, transitions, enterDesktopTransitionHandler,
                exitDesktopTransitionHandler, toggleResizeDesktopTaskTransitionHandler,
                dragToDesktopTransitionHandler, desktopModeTaskRepository,
                desktopModeLoggerTransitionObserver, launchAdjacentController,
                recentsTransitionHandler, multiInstanceHelper,
                mainExecutor, desktopTasksLimiter, recentTasksController.orElse(null));
    }

    @WMSingleton
    @Provides
    static Optional<DesktopTasksLimiter> provideDesktopTasksLimiter(
            Context context,
            Transitions transitions,
            @DynamicOverride DesktopModeTaskRepository desktopModeTaskRepository,
            ShellTaskOrganizer shellTaskOrganizer) {
        if (!DesktopModeStatus.canEnterDesktopMode(context)
                || !Flags.enableDesktopWindowingTaskLimit()) {
            return Optional.empty();
        }
        return Optional.of(
                new DesktopTasksLimiter(
                        transitions, desktopModeTaskRepository, shellTaskOrganizer));
    }


    @WMSingleton
    @Provides
    static DragToDesktopTransitionHandler provideDragToDesktopTransitionHandler(
            Context context,
            Transitions transitions,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            Optional<DesktopTasksLimiter> desktopTasksLimiter) {
        return new DragToDesktopTransitionHandler(context, transitions,
                rootTaskDisplayAreaOrganizer);
    }

    @WMSingleton
    @Provides
    static EnterDesktopTaskTransitionHandler provideEnterDesktopModeTaskTransitionHandler(
            Transitions transitions,
            Optional<DesktopTasksLimiter> desktopTasksLimiter) {
        return new EnterDesktopTaskTransitionHandler(transitions);
    }

    @WMSingleton
    @Provides
    static ToggleResizeDesktopTaskTransitionHandler provideToggleResizeDesktopTaskTransitionHandler(
            Transitions transitions) {
        return new ToggleResizeDesktopTaskTransitionHandler(transitions);
    }

    @WMSingleton
    @Provides
    static ExitDesktopTaskTransitionHandler provideExitDesktopTaskTransitionHandler(
            Transitions transitions,
            Context context
    ) {
        return new ExitDesktopTaskTransitionHandler(transitions, context);
    }

    @WMSingleton
    @Provides
    @DynamicOverride
    static DesktopModeTaskRepository provideDesktopModeTaskRepository() {
        return new DesktopModeTaskRepository();
    }

    @WMSingleton
    @Provides
    static Optional<DesktopTasksTransitionObserver> provideDesktopTasksTransitionObserver(
            Context context,
            Optional<DesktopModeTaskRepository> desktopModeTaskRepository,
            Transitions transitions,
            ShellInit shellInit
    ) {
        return desktopModeTaskRepository.flatMap(repository ->
                Optional.of(new DesktopTasksTransitionObserver(
                        context, repository, transitions, shellInit))
        );
    }

    @WMSingleton
    @Provides
    static DesktopModeLoggerTransitionObserver provideDesktopModeLoggerTransitionObserver(
            Context context,
            ShellInit shellInit,
            Transitions transitions,
            DesktopModeEventLogger desktopModeEventLogger) {
        return new DesktopModeLoggerTransitionObserver(
                context, shellInit, transitions, desktopModeEventLogger);
    }

    @WMSingleton
    @Provides
    static DesktopModeEventLogger provideDesktopModeEventLogger() {
        return new DesktopModeEventLogger();
    }

    //
    // Drag and drop
    //

    @WMSingleton
    @Provides
    static GlobalDragListener provideGlobalDragListener(
            IWindowManager wmService,
            @ShellMainThread ShellExecutor mainExecutor) {
        return new GlobalDragListener(wmService, mainExecutor);
    }

    @WMSingleton
    @Provides
    static DragAndDropController provideDragAndDropController(Context context,
            ShellInit shellInit,
            ShellController shellController,
            ShellCommandHandler shellCommandHandler,
            DisplayController displayController,
            UiEventLogger uiEventLogger,
            IconProvider iconProvider,
            GlobalDragListener globalDragListener,
            Transitions transitions,
            @ShellMainThread ShellExecutor mainExecutor) {
        return new DragAndDropController(context, shellInit, shellController, shellCommandHandler,
                displayController, uiEventLogger, iconProvider, globalDragListener, transitions,
                mainExecutor);
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
            Optional<DesktopTasksTransitionObserver> desktopTasksTransitionObserverOptional
    ) {
        return new Object();
    }
}
