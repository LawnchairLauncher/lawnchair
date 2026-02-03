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

import static android.provider.Settings.Secure.COMPAT_UI_EDUCATION_SHOWING;

import static com.android.wm.shell.compatui.CompatUIStatusManager.COMPAT_UI_EDUCATION_HIDDEN;
import static com.android.wm.shell.onehanded.OneHandedController.SUPPORT_ONE_HANDED_MODE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityTaskManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.UserManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.IWindowManager;
import android.view.accessibility.AccessibilityManager;
import android.window.DesktopModeFlags;
import android.window.SystemPerformanceHinter;

import com.android.internal.logging.UiEventLogger;
import com.android.launcher3.icons.IconProvider;
import com.android.window.flags2.Flags;
import com.android.wm.shell.ProtoLogController;
import com.android.wm.shell.R;
import com.android.wm.shell.RootDisplayAreaOrganizer;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.WindowManagerShellWrapper;
import com.android.wm.shell.activityembedding.ActivityEmbeddingController;
import com.android.wm.shell.appzoomout.AppZoomOut;
import com.android.wm.shell.appzoomout.AppZoomOutController;
import com.android.wm.shell.back.BackAnimation;
import com.android.wm.shell.back.BackAnimationBackground;
import com.android.wm.shell.back.BackAnimationController;
import com.android.wm.shell.back.ShellBackAnimationRegistry;
import com.android.wm.shell.bubbles.BubbleController;
import com.android.wm.shell.bubbles.Bubbles;
import com.android.wm.shell.common.DevicePostureController;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.DockStateReader;
import com.android.wm.shell.common.FloatingContentCoordinator;
import com.android.wm.shell.common.LaunchAdjacentController;
import com.android.wm.shell.common.MultiInstanceHelper;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.SystemWindows;
import com.android.wm.shell.common.TabletopModeController;
import com.android.wm.shell.common.TaskStackListenerImpl;
import com.android.wm.shell.common.pip.PhonePipKeepClearAlgorithm;
import com.android.wm.shell.common.pip.PhoneSizeSpecSource;
import com.android.wm.shell.common.pip.PipAppOpsListener;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipMediaController;
import com.android.wm.shell.common.pip.PipPerfHintController;
import com.android.wm.shell.common.pip.PipSnapAlgorithm;
import com.android.wm.shell.common.pip.PipUiEventLogger;
import com.android.wm.shell.common.pip.SizeSpecSource;
import com.android.wm.shell.common.split.SplitState;
import com.android.wm.shell.compatui.CompatUIConfiguration;
import com.android.wm.shell.compatui.CompatUIController;
import com.android.wm.shell.compatui.CompatUIShellCommandHandler;
import com.android.wm.shell.compatui.CompatUIStatusManager;
import com.android.wm.shell.compatui.api.CompatUIComponentFactory;
import com.android.wm.shell.compatui.api.CompatUIComponentIdGenerator;
import com.android.wm.shell.compatui.api.CompatUIHandler;
import com.android.wm.shell.compatui.api.CompatUIRepository;
import com.android.wm.shell.compatui.api.CompatUIState;
import com.android.wm.shell.compatui.components.RestartButtonSpecKt;
import com.android.wm.shell.compatui.impl.DefaultCompatUIComponentFactory;
import com.android.wm.shell.compatui.impl.DefaultCompatUIHandler;
import com.android.wm.shell.compatui.impl.DefaultCompatUIRepository;
import com.android.wm.shell.compatui.impl.DefaultComponentIdGenerator;
import com.android.wm.shell.desktopmode.DesktopMode;
import com.android.wm.shell.desktopmode.DesktopTasksController;
import com.android.wm.shell.desktopmode.DesktopUserRepositories;
import com.android.wm.shell.desktopmode.common.DefaultHomePackageSupplier;
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider;
import com.android.wm.shell.displayareahelper.DisplayAreaHelper;
import com.android.wm.shell.displayareahelper.DisplayAreaHelperController;
import com.android.wm.shell.freeform.FreeformComponents;
import com.android.wm.shell.fullscreen.FullscreenTaskListener;
import com.android.wm.shell.hidedisplaycutout.HideDisplayCutoutController;
import com.android.wm.shell.keyguard.KeyguardTransitionHandler;
import com.android.wm.shell.keyguard.KeyguardTransitions;
import com.android.wm.shell.onehanded.OneHanded;
import com.android.wm.shell.onehanded.OneHandedController;
import com.android.wm.shell.performance.PerfHintController;
import com.android.wm.shell.recents.RecentTasks;
import com.android.wm.shell.recents.RecentTasksController;
import com.android.wm.shell.recents.RecentsTransitionHandler;
import com.android.wm.shell.recents.TaskStackTransitionObserver;
import com.android.wm.shell.shared.ShellTransitions;
import com.android.wm.shell.shared.TransactionPool;
import com.android.wm.shell.shared.annotations.ShellAnimationThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.shared.annotations.ShellSplashscreenThread;
import com.android.wm.shell.shared.desktopmode.DesktopConfig;
import com.android.wm.shell.shared.desktopmode.DesktopConfigImpl;
import com.android.wm.shell.shared.desktopmode.DesktopModeCompatPolicy;
import com.android.wm.shell.shared.desktopmode.DesktopState;
import com.android.wm.shell.shared.desktopmode.DesktopStateImpl;
import com.android.wm.shell.splitscreen.SplitScreen;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.startingsurface.StartingSurface;
import com.android.wm.shell.startingsurface.StartingWindowController;
import com.android.wm.shell.startingsurface.StartingWindowTypeAlgorithm;
import com.android.wm.shell.startingsurface.phone.PhoneStartingWindowTypeAlgorithm;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.sysui.ShellInterface;
import com.android.wm.shell.taskview.TaskViewFactory;
import com.android.wm.shell.taskview.TaskViewFactoryController;
import com.android.wm.shell.taskview.TaskViewRepository;
import com.android.wm.shell.taskview.TaskViewTransitions;
import com.android.wm.shell.transition.FocusTransitionObserver;
import com.android.wm.shell.transition.HomeTransitionObserver;
import com.android.wm.shell.transition.MixedTransitionHandler;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.unfold.ShellUnfoldProgressProvider;
import com.android.wm.shell.unfold.UnfoldAnimationController;
import com.android.wm.shell.unfold.UnfoldTransitionHandler;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;
import com.android.wm.shell.windowdecor.viewholder.AppHandleNotifier;
import com.android.wm.shell.windowdecor.viewholder.AppHandles;

import com.google.android.msdl.domain.MSDLPlayer;

import dagger.BindsOptionalOf;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

import java.util.Optional;
import java.util.concurrent.Executors;

/**
 * Provides basic dependencies from {@link com.android.wm.shell}, these dependencies are only
 * accessible from components within the WM subcomponent (can be explicitly exposed to the
 * SysUIComponent, see {@link com.android.systemui.dagger.WMComponent}).
 *
 * <p>This module only defines *common* dependencies across various SystemUI implementations,
 * dependencies that are device/form factor SystemUI implementation specific should go into their
 * respective modules (ie. {@link WMShellModule} for handheld, {@link TvWMShellModule} for tv, etc.)
 */
@Module(
        includes = {
                WMShellConcurrencyModule.class,
                WMShellCoroutinesModule.class
        })
public abstract class WMShellBaseModule {

    //
    // Internal common - Components used internally by multiple shell features
    //

    @WMSingleton
    @Provides
    static FloatingContentCoordinator provideFloatingContentCoordinator() {
        return new FloatingContentCoordinator();
    }

    @WMSingleton
    @Provides
    static DisplayController provideDisplayController(Context context,
            IWindowManager wmService,
            ShellInit shellInit,
            @ShellMainThread ShellExecutor mainExecutor,
            DisplayManager displayManager,
            DesktopState desktopState) {
        return new DisplayController(context, wmService, shellInit, mainExecutor, displayManager,
                desktopState);
    }

    @WMSingleton
    @Provides
    static DisplayInsetsController provideDisplayInsetsController(IWindowManager wmService,
            ShellInit shellInit,
            DisplayController displayController,
            @ShellMainThread ShellExecutor mainExecutor) {
        return new DisplayInsetsController(wmService, shellInit, displayController,
                mainExecutor);
    }

    @WMSingleton
    @Provides
    static DisplayImeController provideDisplayImeController(
            IWindowManager wmService,
            ShellInit shellInit,
            DisplayController displayController,
            DisplayInsetsController displayInsetsController,
            TransactionPool transactionPool,
            @ShellMainThread ShellExecutor mainExecutor
    ) {
        return new DisplayImeController(wmService, shellInit, displayController,
                displayInsetsController, transactionPool, mainExecutor);
    }

    @WMSingleton
    @Provides
    static DisplayLayout provideDisplayLayout() {
        return new DisplayLayout();
    }

    @WMSingleton
    @Provides
    static DevicePostureController provideDevicePostureController(
            Context context,
            ShellInit shellInit,
            @ShellMainThread ShellExecutor mainExecutor
    ) {
        return new DevicePostureController(context, shellInit, mainExecutor);
    }

    @WMSingleton
    @Provides
    static TabletopModeController provideTabletopModeController(
            Context context,
            ShellInit shellInit,
            DevicePostureController postureController,
            DisplayController displayController,
            @ShellMainThread ShellExecutor mainExecutor) {
        return new TabletopModeController(
                context, shellInit, postureController, displayController, mainExecutor);
    }

    @WMSingleton
    @Provides
    static MSDLPlayer provideMSDLPlayer(@Nullable Vibrator vibrator) {
        return MSDLPlayer.Companion.createPlayer(
                vibrator,
                Executors.newSingleThreadExecutor(),
                null /* useHapticFeedbackForToken */
        );
    }

    @WMSingleton
    @Provides
    static ShellTaskOrganizer provideShellTaskOrganizer(
            Context context,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            Optional<CompatUIHandler> compatUI,
            Optional<UnfoldAnimationController> unfoldAnimationController,
            Optional<RecentTasksController> recentTasksOptional,
            @ShellMainThread ShellExecutor mainExecutor) {
        if (!context.getResources().getBoolean(R.bool.config_registerShellTaskOrganizerOnInit)) {
            // TODO(b/238217847): Force override shell init if registration is disabled
            shellInit = new ShellInit(mainExecutor);
        }
        return new ShellTaskOrganizer(
                shellInit,
                shellCommandHandler,
                compatUI.orElse(null),
                unfoldAnimationController,
                recentTasksOptional,
                mainExecutor);
    }

    @WMSingleton
    @Provides
    static DesktopModeCompatPolicy provideDesktopModeCompatPolicy(
            Context context,
            ShellInit shellInit,
            @ShellMainThread Handler mainHandler) {
        final DesktopModeCompatPolicy policy = new DesktopModeCompatPolicy(context);
        policy.setDefaultHomePackageSupplier(new DefaultHomePackageSupplier(
                context, shellInit, mainHandler));
        return policy;
    }

    @WMSingleton
    @Provides
    static Optional<CompatUIHandler> provideCompatUIController(
            Context context,
            ShellInit shellInit,
            ShellController shellController,
            DisplayController displayController,
            DisplayInsetsController displayInsetsController,
            DisplayImeController imeController,
            SyncTransactionQueue syncQueue,
            @ShellMainThread ShellExecutor mainExecutor,
            Lazy<Transitions> transitionsLazy,
            Lazy<DockStateReader> dockStateReader,
            Lazy<CompatUIConfiguration> compatUIConfiguration,
            Lazy<CompatUIShellCommandHandler> compatUIShellCommandHandler,
            Lazy<AccessibilityManager> accessibilityManager,
            CompatUIRepository compatUIRepository,
            Optional<DesktopUserRepositories> desktopUserRepositories,
            @NonNull CompatUIState compatUIState,
            @NonNull CompatUIComponentIdGenerator componentIdGenerator,
            @NonNull CompatUIComponentFactory compatUIComponentFactory,
            CompatUIStatusManager compatUIStatusManager,
            DesktopState desktopState) {
        if (!context.getResources().getBoolean(R.bool.config_enableCompatUIController)) {
            return Optional.empty();
        }
        if (Flags.appCompatUiFramework()) {
            return Optional.of(
                    new DefaultCompatUIHandler(compatUIRepository, compatUIState,
                            componentIdGenerator, compatUIComponentFactory, mainExecutor));
        }
        return Optional.of(
                new CompatUIController(
                        context,
                        shellInit,
                        shellController,
                        displayController,
                        displayInsetsController,
                        imeController,
                        syncQueue,
                        mainExecutor,
                        transitionsLazy,
                        dockStateReader.get(),
                        compatUIConfiguration.get(),
                        compatUIShellCommandHandler.get(),
                        accessibilityManager.get(),
                        compatUIStatusManager,
                        desktopUserRepositories,
                        desktopState));
    }

    @WMSingleton
    @Provides
    static CompatUIStatusManager provideCompatUIStatusManager(@NonNull Context context) {
        if (DesktopModeFlags.ENABLE_DESKTOP_COMPAT_UI_VISIBILITY_STATUS.isTrue()) {
            return new CompatUIStatusManager(
                    newState -> Settings.Secure.putInt(context.getContentResolver(),
                            COMPAT_UI_EDUCATION_SHOWING, newState),
                    () -> Settings.Secure.getInt(context.getContentResolver(),
                            COMPAT_UI_EDUCATION_SHOWING, COMPAT_UI_EDUCATION_HIDDEN));
        } else {
            return new CompatUIStatusManager();
        }
    }

    @WMSingleton
    @Provides
    static CompatUIState provideCompatUIState() {
        return new CompatUIState();
    }

    @WMSingleton
    @Provides
    static CompatUIComponentFactory provideCompatUIComponentFactory(
            @NonNull Context context,
            @NonNull SyncTransactionQueue syncQueue,
            @NonNull DisplayController displayController) {
        return new DefaultCompatUIComponentFactory(context, syncQueue, displayController);
    }

    @WMSingleton
    @Provides
    static CompatUIComponentIdGenerator provideCompatUIComponentIdGenerator() {
        return new DefaultComponentIdGenerator();
    }

    @WMSingleton
    @Provides
    static CompatUIRepository provideCompatUIRepository() {
        // TODO(b/360288344) Integrate Dagger Multibinding
        final CompatUIRepository repository = new DefaultCompatUIRepository();
        repository.addSpec(RestartButtonSpecKt.getRestartButtonSpec());
        return repository;
    }

    @WMSingleton
    @Provides
    static SyncTransactionQueue provideSyncTransactionQueue(TransactionPool pool,
            @ShellMainThread ShellExecutor mainExecutor) {
        return new SyncTransactionQueue(pool, mainExecutor);
    }

    @WMSingleton
    @Provides
    static SystemWindows provideSystemWindows(DisplayController displayController,
            IWindowManager wmService) {
        return new SystemWindows(displayController, wmService);
    }

    @WMSingleton
    @Provides
    static IconProvider provideIconProvider(Context context) {
        return new IconProvider(context);
    }

    // We currently dedupe multiple messages, so we use the shell main handler directly
    @WMSingleton
    @Provides
    static TaskStackListenerImpl providerTaskStackListenerImpl(
            @ShellMainThread Handler mainHandler) {
        return new TaskStackListenerImpl(mainHandler);
    }

    @WMSingleton
    @Provides
    static TransactionPool provideTransactionPool() {
        return new TransactionPool();
    }

    @WMSingleton
    @Provides
    static WindowManagerShellWrapper provideWindowManagerShellWrapper(
            @ShellMainThread ShellExecutor mainExecutor) {
        return new WindowManagerShellWrapper(mainExecutor);
    }

    @WMSingleton
    @Provides
    static LaunchAdjacentController provideLaunchAdjacentController(
            SyncTransactionQueue syncQueue) {
        return new LaunchAdjacentController(syncQueue);
    }

    @WMSingleton
    @Provides
    static Optional<SystemPerformanceHinter> provideSystemPerformanceHinter(Context context,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            RootTaskDisplayAreaOrganizer rootTdaOrganizer) {
        final PerfHintController perfHintController =
                new PerfHintController(context, shellInit, shellCommandHandler, rootTdaOrganizer);
        return Optional.of(perfHintController.getHinter());
    }

    @WMSingleton
    @Provides
    static MultiInstanceHelper provideMultiInstanceHelper(
            Context context,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler
    ) {
        return new MultiInstanceHelper(context, context.getPackageManager(),
                shellInit, shellCommandHandler, Flags.supportsMultiInstanceSystemUi());
    }

    //
    // Back animation
    //

    @WMSingleton
    @Provides
    static Optional<BackAnimation> provideBackAnimation(
            Optional<BackAnimationController> backAnimationController) {
        return backAnimationController.map(BackAnimationController::getBackAnimationImpl);
    }

    @WMSingleton
    @Provides
    static BackAnimationBackground provideBackAnimationBackground(
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer) {
        return new BackAnimationBackground(rootTaskDisplayAreaOrganizer);
    }

    @WMSingleton
    @Provides
    static Optional<BackAnimationController> provideBackAnimationController(
            Context context,
            ShellInit shellInit,
            ShellController shellController,
            @ShellMainThread ShellExecutor shellExecutor,
            BackAnimationBackground backAnimationBackground,
            Optional<ShellBackAnimationRegistry> shellBackAnimationRegistry,
            ShellCommandHandler shellCommandHandler,
            Transitions transitions,
            @ShellMainThread Handler handler
    ) {
            return shellBackAnimationRegistry.map(
                    (animations) ->
                            new BackAnimationController(
                                    shellInit,
                                    shellController,
                                    shellExecutor,
                                    context,
                                    backAnimationBackground,
                                    animations,
                                    shellCommandHandler,
                                    transitions,
                                    handler));
    }

    @BindsOptionalOf
    abstract ShellBackAnimationRegistry optionalBackAnimationRegistry();

    //
    // PiP (optional feature)
    //

    @WMSingleton
    @Provides
    static PipUiEventLogger providePipUiEventLogger(UiEventLogger uiEventLogger,
            PackageManager packageManager) {
        return new PipUiEventLogger(uiEventLogger, packageManager);
    }

    @WMSingleton
    @Provides
    static PipMediaController providePipMediaController(Context context,
            @ShellMainThread Handler mainHandler) {
        return new PipMediaController(context, mainHandler);
    }

    @WMSingleton
    @Provides
    static SizeSpecSource provideSizeSpecSource(Context context,
            PipDisplayLayoutState pipDisplayLayoutState) {
        return new PhoneSizeSpecSource(context, pipDisplayLayoutState);
    }

    @WMSingleton
    @Provides
    static Optional<PipPerfHintController> providePipPerfHintController(
            PipDisplayLayoutState pipDisplayLayoutState,
            @ShellMainThread ShellExecutor mainExecutor,
            Optional<SystemPerformanceHinter> systemPerformanceHinterOptional) {
        if (systemPerformanceHinterOptional.isPresent()) {
            return Optional.of(new PipPerfHintController(pipDisplayLayoutState, mainExecutor,
                    systemPerformanceHinterOptional.get()));
        } else {
            return Optional.empty();
        }
    }

    @WMSingleton
    @Provides
    static PipBoundsState providePipBoundsState(Context context,
            SizeSpecSource sizeSpecSource, PipDisplayLayoutState pipDisplayLayoutState) {
        return new PipBoundsState(context, sizeSpecSource, pipDisplayLayoutState);
    }


    @WMSingleton
    @Provides
    static PipSnapAlgorithm providePipSnapAlgorithm() {
        return new PipSnapAlgorithm();
    }

    @WMSingleton
    @Provides
    static PhonePipKeepClearAlgorithm providePhonePipKeepClearAlgorithm(Context context,
            PipDisplayLayoutState pipDisplayLayoutState) {
        return new PhonePipKeepClearAlgorithm(context, pipDisplayLayoutState);
    }

    @WMSingleton
    @Provides
    static PipBoundsAlgorithm providesPipBoundsAlgorithm(Context context,
            PipBoundsState pipBoundsState, PipSnapAlgorithm pipSnapAlgorithm,
            PhonePipKeepClearAlgorithm pipKeepClearAlgorithm,
            PipDisplayLayoutState pipDisplayLayoutState, SizeSpecSource sizeSpecSource) {
        return new PipBoundsAlgorithm(context, pipBoundsState, pipSnapAlgorithm,
                pipKeepClearAlgorithm, pipDisplayLayoutState, sizeSpecSource);
    }

    @WMSingleton
    @Provides
    static PipAppOpsListener providePipAppOpsListener(Context context,
            @ShellMainThread ShellExecutor mainExecutor) {
        return new PipAppOpsListener(context, mainExecutor);
    }

    //
    // Bubbles (optional feature)
    //

    @WMSingleton
    @Provides
    static Optional<Bubbles> provideBubbles(Optional<BubbleController> bubbleController) {
        return bubbleController.map((controller) -> controller.asBubbles());
    }

    @BindsOptionalOf
    abstract BubbleController optionalBubblesController();

    //
    // Fullscreen
    //

    // Workaround for dynamic overriding with a default implementation, see {@link DynamicOverride}
    @BindsOptionalOf
    @DynamicOverride
    abstract FullscreenTaskListener optionalFullscreenTaskListener();

    @WMSingleton
    @Provides
    static FullscreenTaskListener provideFullscreenTaskListener(
            @DynamicOverride Optional<FullscreenTaskListener> fullscreenTaskListener,
            ShellInit shellInit,
            ShellTaskOrganizer shellTaskOrganizer,
            SyncTransactionQueue syncQueue,
            Optional<RecentTasksController> recentTasksOptional,
            Optional<WindowDecorViewModel> windowDecorViewModelOptional,
            Optional<DesktopWallpaperActivityTokenProvider>
                    desktopWallpaperActivityTokenProviderOptional) {
        if (fullscreenTaskListener.isPresent()) {
            return fullscreenTaskListener.get();
        } else {
            return new FullscreenTaskListener(shellInit, shellTaskOrganizer, syncQueue,
                    recentTasksOptional, windowDecorViewModelOptional,
                    desktopWallpaperActivityTokenProviderOptional);
        }
    }

    //
    // Window Decoration
    //

    @BindsOptionalOf
    abstract WindowDecorViewModel optionalWindowDecorViewModel();

    //
    // Unfold transition
    //

    @BindsOptionalOf
    abstract ShellUnfoldProgressProvider optionalShellUnfoldProgressProvider();

    // Workaround for dynamic overriding with a default implementation, see {@link DynamicOverride}
    @BindsOptionalOf
    @DynamicOverride
    abstract UnfoldAnimationController optionalUnfoldController();

    @WMSingleton
    @Provides
    static Optional<UnfoldAnimationController> provideUnfoldController(
            @DynamicOverride Lazy<Optional<UnfoldAnimationController>>
                    fullscreenUnfoldController,
            Optional<ShellUnfoldProgressProvider> progressProvider) {
        if (progressProvider.isPresent()
                && progressProvider.get() != ShellUnfoldProgressProvider.NO_PROVIDER) {
            return fullscreenUnfoldController.get();
        }
        return Optional.empty();
    }

    @BindsOptionalOf
    @DynamicOverride
    abstract UnfoldTransitionHandler optionalUnfoldTransitionHandler();

    @WMSingleton
    @Provides
    static Optional<UnfoldTransitionHandler> provideUnfoldTransitionHandler(
            Optional<ShellUnfoldProgressProvider> progressProvider,
            @DynamicOverride Lazy<Optional<UnfoldTransitionHandler>> handler) {
        if (progressProvider.isPresent()
                && progressProvider.get() != ShellUnfoldProgressProvider.NO_PROVIDER) {
            return handler.get();
        }
        return Optional.empty();
    }

    //
    // Freeform (optional feature)
    //

    // Workaround for dynamic overriding with a default implementation, see {@link DynamicOverride}
    @BindsOptionalOf
    @DynamicOverride
    abstract FreeformComponents optionalFreeformComponents();

    @WMSingleton
    @Provides
    static Optional<FreeformComponents> provideFreeformComponents(
            @DynamicOverride Optional<FreeformComponents> freeformComponents,
            DesktopState desktopState) {
        if (FreeformComponents.requiresFreeformComponents(desktopState)) {
            return freeformComponents;
        }
        return Optional.empty();
    }

    //
    // Hide display cutout
    //

    @WMSingleton
    @Provides
    static Optional<HideDisplayCutoutController> provideHideDisplayCutoutController(Context context,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            ShellController shellController,
            DisplayController displayController,
            @ShellMainThread ShellExecutor mainExecutor) {
        return Optional.ofNullable(
                HideDisplayCutoutController.create(context, shellInit, shellCommandHandler,
                        shellController, displayController, mainExecutor));
    }

    //
    // One handed mode (optional feature)
    //

    @WMSingleton
    @Provides
    static Optional<OneHanded> provideOneHanded(Optional<OneHandedController> oneHandedController) {
        return oneHandedController.map((controller) -> controller.asOneHanded());
    }

    // Workaround for dynamic overriding with a default implementation, see {@link DynamicOverride}
    @BindsOptionalOf
    @DynamicOverride
    abstract OneHandedController optionalOneHandedController();

    @WMSingleton
    @Provides
    static Optional<OneHandedController> providesOneHandedController(
            @DynamicOverride Optional<OneHandedController> oneHandedController) {
        if (SystemProperties.getBoolean(SUPPORT_ONE_HANDED_MODE, false)) {
            return oneHandedController;
        }
        return Optional.empty();
    }

    //
    // Recent tasks
    //

    @WMSingleton
    @Provides
    static Optional<RecentTasks> provideRecentTasks(
            Optional<RecentTasksController> recentTasksController) {
        return recentTasksController.map((controller) -> controller.asRecentTasks());
    }

    @WMSingleton
    @Provides
    static Optional<RecentTasksController> provideRecentTasksController(
            Context context,
            ShellInit shellInit,
            ShellController shellController,
            ShellCommandHandler shellCommandHandler,
            TaskStackListenerImpl taskStackListener,
            ActivityTaskManager activityTaskManager,
            Optional<DesktopUserRepositories> desktopUserRepositories,
            TaskStackTransitionObserver taskStackTransitionObserver,
            @ShellMainThread ShellExecutor mainExecutor,
            DesktopState desktopState
    ) {
        return Optional.ofNullable(
                RecentTasksController.create(context, shellInit, shellController,
                        shellCommandHandler, taskStackListener, activityTaskManager,
                        desktopUserRepositories, taskStackTransitionObserver, mainExecutor,
                        desktopState));
    }

    @BindsOptionalOf
    abstract RecentsTransitionHandler optionalRecentsTransitionHandler();

    //
    // Shell transitions
    //

    @WMSingleton
    @Provides
    static ShellTransitions provideRemoteTransitions(Transitions transitions) {
        return transitions.asRemoteTransitions();
    }

    @WMSingleton
    @Provides
    static Transitions provideTransitions(Context context,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            ShellController shellController,
            ShellTaskOrganizer organizer,
            TransactionPool pool,
            DisplayController displayController,
            DisplayInsetsController displayInsetsController,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellMainThread Handler mainHandler,
            @ShellAnimationThread ShellExecutor animExecutor,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            HomeTransitionObserver homeTransitionObserver,
            FocusTransitionObserver focusTransitionObserver) {
        return new Transitions(context, shellInit, shellCommandHandler, shellController, organizer,
                pool, displayController, displayInsetsController, mainExecutor, mainHandler,
                animExecutor, rootTaskDisplayAreaOrganizer, homeTransitionObserver,
                focusTransitionObserver);
    }

    @WMSingleton
    @Provides
    static HomeTransitionObserver provideHomeTransitionObserver(Context context,
            @ShellMainThread ShellExecutor mainExecutor,
            DisplayInsetsController displayInsetsController,
            ShellInit shellInit) {
        return new HomeTransitionObserver(context, mainExecutor, displayInsetsController,
                shellInit);
    }

    @WMSingleton
    @Provides
    static FocusTransitionObserver provideFocusTransitionObserver() {
        return new FocusTransitionObserver();
    }

    @WMSingleton
    @Provides
    static TaskViewTransitions provideTaskViewTransitions(Transitions transitions,
            TaskViewRepository repository, ShellTaskOrganizer organizer,
            SyncTransactionQueue syncQueue) {
        return new TaskViewTransitions(transitions, repository, organizer, syncQueue);
    }

    @WMSingleton
    @Provides
    static TaskViewRepository provideTaskViewRepository() {
        return new TaskViewRepository();
    }

    // Workaround for dynamic overriding with a default implementation, see {@link DynamicOverride}
    @BindsOptionalOf
    @DynamicOverride
    abstract MixedTransitionHandler optionalMixedTransitionHandler();

    @WMSingleton
    @Provides
    static Optional<MixedTransitionHandler> provideMixedTransitionHandler(
            @DynamicOverride Optional<MixedTransitionHandler> mixedTransitionHandler
    ) {
        if (mixedTransitionHandler.isPresent()) {
            return mixedTransitionHandler;
        }
        return Optional.empty();
    }

    //
    // Keyguard transitions (optional feature)
    //

    @WMSingleton
    @Provides
    static KeyguardTransitionHandler provideKeyguardTransitionHandler(
            ShellInit shellInit,
            ShellController shellController,
            DisplayController displayController,
            Transitions transitions,
            TaskStackListenerImpl taskStackListener,
            @ShellMainThread Handler mainHandler,
            @ShellMainThread ShellExecutor mainExecutor,
            FocusTransitionObserver focusTransitionObserver) {
        return new KeyguardTransitionHandler(
                shellInit, shellController, displayController, transitions, taskStackListener,
                mainHandler, mainExecutor, focusTransitionObserver);
    }

    @WMSingleton
    @Provides
    static KeyguardTransitions provideKeyguardTransitions(
            KeyguardTransitionHandler handler) {
        return handler.asKeyguardTransitions();
    }

    //
    // Display areas
    //

    @WMSingleton
    @Provides
    static RootTaskDisplayAreaOrganizer provideRootTaskDisplayAreaOrganizer(
            @ShellMainThread ShellExecutor mainExecutor, Context context, ShellInit shellInit) {
        return new RootTaskDisplayAreaOrganizer(mainExecutor, context, shellInit);
    }

    @WMSingleton
    @Provides
    static RootDisplayAreaOrganizer provideRootDisplayAreaOrganizer(
            @ShellMainThread ShellExecutor mainExecutor, ShellInit shellInit) {
        return new RootDisplayAreaOrganizer(mainExecutor, shellInit);
    }

    @WMSingleton
    @Provides
    static Optional<DisplayAreaHelper> provideDisplayAreaHelper(
            @ShellMainThread ShellExecutor mainExecutor,
            RootDisplayAreaOrganizer rootDisplayAreaOrganizer) {
        return Optional.of(new DisplayAreaHelperController(mainExecutor,
                rootDisplayAreaOrganizer));
    }

    //
    // Splitscreen (optional feature)
    //

    @WMSingleton
    @Provides
    static Optional<SplitScreen> provideSplitScreen(
            Optional<SplitScreenController> splitScreenController) {
        return splitScreenController.map((controller) -> controller.asSplitScreen());
    }

    // Workaround for dynamic overriding with a default implementation, see {@link DynamicOverride}
    @BindsOptionalOf
    @DynamicOverride
    abstract SplitScreenController optionalSplitScreenController();

    @WMSingleton
    @Provides
    static Optional<SplitScreenController> providesSplitScreenController(
            @DynamicOverride Optional<SplitScreenController> splitscreenController,
            Context context) {
        if (ActivityTaskManager.supportsSplitScreenMultiWindow(context)) {
            return splitscreenController;
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static SplitState provideSplitState() {
        return new SplitState();
    }

    //
    // Starting window
    //

    @WMSingleton
    @Provides
    static Optional<StartingSurface> provideStartingSurface(
            StartingWindowController startingWindowController) {
        return Optional.of(startingWindowController.asStartingSurface());
    }

    @WMSingleton
    @Provides
    static StartingWindowController provideStartingWindowController(
            Context context,
            ShellInit shellInit,
            ShellController shellController,
            ShellTaskOrganizer shellTaskOrganizer,
            @ShellSplashscreenThread ShellExecutor splashScreenExecutor,
            StartingWindowTypeAlgorithm startingWindowTypeAlgorithm, IconProvider iconProvider,
            TransactionPool pool, @ShellMainThread ShellExecutor mainExecutor,
            Transitions transitions) {
        return new StartingWindowController(context, shellInit, shellController, shellTaskOrganizer,
                splashScreenExecutor, startingWindowTypeAlgorithm, iconProvider, pool, mainExecutor,
                transitions);
    }

    // Workaround for dynamic overriding with a default implementation, see {@link DynamicOverride}
    @BindsOptionalOf
    @DynamicOverride
    abstract StartingWindowTypeAlgorithm optionalStartingWindowTypeAlgorithm();

    @WMSingleton
    @Provides
    static StartingWindowTypeAlgorithm provideStartingWindowTypeAlgorithm(
            @DynamicOverride Optional<StartingWindowTypeAlgorithm> startingWindowTypeAlgorithm
    ) {
        if (startingWindowTypeAlgorithm.isPresent()) {
            return startingWindowTypeAlgorithm.get();
        }
        // Default to phone starting window type
        return new PhoneStartingWindowTypeAlgorithm();
    }

    //
    // Task view factory
    //

    @WMSingleton
    @Provides
    static Optional<TaskViewFactory> provideTaskViewFactory(
            TaskViewFactoryController taskViewFactoryController) {
        return Optional.of(taskViewFactoryController.asTaskViewFactory());
    }

    @WMSingleton
    @Provides
    static TaskViewFactoryController provideTaskViewFactoryController(
            ShellTaskOrganizer shellTaskOrganizer,
            @ShellMainThread ShellExecutor mainExecutor,
            SyncTransactionQueue syncQueue,
            TaskViewTransitions taskViewTransitions) {
        return new TaskViewFactoryController(shellTaskOrganizer, mainExecutor, syncQueue,
                taskViewTransitions);
    }


    //
    // ActivityEmbedding
    //

    @WMSingleton
    @Provides
    static Optional<ActivityEmbeddingController> provideActivityEmbeddingController(
            Context context,
            ShellInit shellInit,
            Transitions transitions) {
        return Optional.ofNullable(
                ActivityEmbeddingController.create(context, shellInit, transitions));
    }

    //
    // SysUI -> Shell interface
    //

    @WMSingleton
    @Provides
    static ShellInterface provideShellSysuiCallbacks(
            @ShellCreateTrigger Object createTrigger,
            ShellController shellController) {
        return shellController.asShell();
    }

    @WMSingleton
    @Provides
    static ShellController provideShellController(Context context,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            DisplayInsetsController displayInsetsController,
            UserManager userManager,
            @ShellMainThread ShellExecutor mainExecutor) {
        return new ShellController(context, shellInit, shellCommandHandler,
                displayInsetsController, userManager, mainExecutor);
    }

    //
    // Desktop mode (optional feature)
    //

    @WMSingleton
    @Provides
    static Optional<DesktopMode> provideDesktopMode(
            Optional<DesktopTasksController> desktopTasksController) {
        return desktopTasksController.map(DesktopTasksController::asDesktopMode);
    }


    @BindsOptionalOf
    @DynamicOverride
    abstract DesktopTasksController optionalDesktopTasksController();

    @WMSingleton
    @Provides
    static Optional<DesktopTasksController> providesDesktopTasksController(
            DesktopState desktopState,
            @DynamicOverride Optional<Lazy<DesktopTasksController>> desktopTasksController) {
        // Use optional-of-lazy for the dependency that this provider relies on.
        // Lazy ensures that this provider will not be the cause the dependency is created
        // when it will not be returned due to the condition below.
        return desktopTasksController.flatMap((lazy) -> {
            if (desktopState.canEnterDesktopModeOrShowAppHandle()) {
                return Optional.of(lazy.get());
            }
            return Optional.empty();
        });
    }

    @BindsOptionalOf
    @DynamicOverride
    abstract DesktopUserRepositories optionalDesktopUserRepositories();

    @WMSingleton
    @Provides
    static Optional<DesktopUserRepositories> provideDesktopUserRepositories(
            DesktopState desktopState,
            @DynamicOverride Optional<Lazy<DesktopUserRepositories>> desktopUserRepositories) {
        // Use optional-of-lazy for the dependency that this provider relies on.
        // Lazy ensures that this provider will not be the cause the dependency is created
        // when it will not be returned due to the condition below.
        return desktopUserRepositories.flatMap((lazy) -> {
            if (desktopState.canEnterDesktopMode()) {
                return Optional.of(lazy.get());
            }
            return Optional.empty();
        });
    }

    @BindsOptionalOf
    abstract DesktopWallpaperActivityTokenProvider optionalDesktopWallpaperActivityTokenProvider();

    //
    // App zoom out (optional feature)
    //

    @WMSingleton
    @Provides
    static Optional<AppZoomOut> provideAppZoomOut(
            Optional<AppZoomOutController> appZoomOutController) {
        return appZoomOutController.map((controller) -> controller.asAppZoomOut());
    }

    @BindsOptionalOf
    abstract AppZoomOutController optionalAppZoomOutController();

    //
    // Task Stack
    //

    @WMSingleton
    @Provides
    static TaskStackTransitionObserver provideTaskStackTransitionObserver(
            ShellInit shellInit,
            Lazy<ShellTaskOrganizer> shellTaskOrganizer,
            ShellCommandHandler shellCommandHandler,
            Lazy<Transitions> transitions
    ) {
        return new TaskStackTransitionObserver(shellInit, shellTaskOrganizer, shellCommandHandler,
                transitions);
    }

    //
    // AppHandle
    //

    @WMSingleton
    @Provides
    static Optional<AppHandles> provideAppHandles(Optional<AppHandleNotifier> appHandleNotifier) {
        return appHandleNotifier.map(AppHandleNotifier::asAppHandleImpl);
    }

    @BindsOptionalOf
    abstract AppHandleNotifier getAppHandleNotifier();

    //
    // Misc
    //

    // Workaround for dynamic overriding with a default implementation, see {@link DynamicOverride}
    @BindsOptionalOf
    @ShellCreateTriggerOverride
    abstract Object provideIndependentShellComponentsToCreateOverride();

    // TODO: Temporarily move dependencies to this instead of ShellInit since that is needed to add
    // the callback. We will be moving to a different explicit startup mechanism in a follow- up CL.
    @WMSingleton
    @ShellCreateTrigger
    @Provides
    static Object provideIndependentShellComponentsToCreate(
            DisplayController displayController,
            DisplayImeController displayImeController,
            DisplayInsetsController displayInsetsController,
            ShellTaskOrganizer shellTaskOrganizer,
            Optional<BubbleController> bubblesOptional,
            Optional<SplitScreenController> splitScreenOptional,
            FullscreenTaskListener fullscreenTaskListener,
            Optional<UnfoldAnimationController> unfoldAnimationController,
            Optional<UnfoldTransitionHandler> unfoldTransitionHandler,
            Optional<FreeformComponents> freeformComponents,
            Optional<RecentTasksController> recentTasksOptional,
            Optional<RecentsTransitionHandler> recentsTransitionHandlerOptional,
            Optional<OneHandedController> oneHandedControllerOptional,
            Optional<AppZoomOutController> appZoomOutControllerOptional,
            Optional<AppHandles> appHandlesOptional,
            Optional<HideDisplayCutoutController> hideDisplayCutoutControllerOptional,
            Optional<ActivityEmbeddingController> activityEmbeddingOptional,
            Optional<MixedTransitionHandler> mixedTransitionHandler,
            Transitions transitions,
            StartingWindowController startingWindow,
            ProtoLogController protoLogController,
            @ShellCreateTriggerOverride Optional<Object> overriddenCreateTrigger) {
        return new Object();
    }

    @WMSingleton
    @Provides
    static ShellInit provideShellInit(@ShellMainThread ShellExecutor mainExecutor) {
        return new ShellInit(mainExecutor);
    }

    @WMSingleton
    @Provides
    static ShellCommandHandler provideShellCommandHandler() {
        return new ShellCommandHandler();
    }

    @WMSingleton
    @Provides
    static ProtoLogController provideProtoLogController(
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler) {
        return new ProtoLogController(shellInit, shellCommandHandler);
    }

    @WMSingleton
    @Provides
    static DesktopConfig provideDesktopConfig(Context context,
            DesktopState features) {
        return new DesktopConfigImpl(context, features);
    }

    @WMSingleton
    @Provides
    static DesktopState provideDesktopState(Context context) {
        return new DesktopStateImpl(context);
    }

    @Provides
    static Optional<DesktopState> provideOptionalDesktopState(
            DesktopState desktopState) {
        return Optional.of(desktopState);
    }
}
