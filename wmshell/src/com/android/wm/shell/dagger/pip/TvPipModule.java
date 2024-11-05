/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.dagger.pip;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.WindowManagerShellWrapper;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.SystemWindows;
import com.android.wm.shell.common.TaskStackListenerImpl;
import com.android.wm.shell.common.pip.LegacySizeSpecSource;
import com.android.wm.shell.common.pip.PipAppOpsListener;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipMediaController;
import com.android.wm.shell.common.pip.PipPerfHintController;
import com.android.wm.shell.common.pip.PipSnapAlgorithm;
import com.android.wm.shell.common.pip.PipUiEventLogger;
import com.android.wm.shell.dagger.WMShellBaseModule;
import com.android.wm.shell.dagger.WMSingleton;
import com.android.wm.shell.pip.Pip;
import com.android.wm.shell.pip.PipAnimationController;
import com.android.wm.shell.pip.PipParamsChangedForwarder;
import com.android.wm.shell.pip.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip.PipTaskOrganizer;
import com.android.wm.shell.pip.PipTransitionState;
import com.android.wm.shell.pip.tv.TvPipBoundsAlgorithm;
import com.android.wm.shell.pip.tv.TvPipBoundsController;
import com.android.wm.shell.pip.tv.TvPipBoundsState;
import com.android.wm.shell.pip.tv.TvPipController;
import com.android.wm.shell.pip.tv.TvPipMenuController;
import com.android.wm.shell.pip.tv.TvPipNotificationController;
import com.android.wm.shell.pip.tv.TvPipTaskOrganizer;
import com.android.wm.shell.pip.tv.TvPipTransition;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;

import dagger.Module;
import dagger.Provides;

import java.util.Optional;

/**
 * Provides TV specific dependencies for Pip.
 */
@Module(includes = {
        WMShellBaseModule.class,
        Pip1SharedModule.class})
public abstract class TvPipModule {
    @WMSingleton
    @Provides
    static Optional<Pip> providePip(
            Context context,
            ShellInit shellInit,
            ShellController shellController,
            TvPipBoundsState tvPipBoundsState,
            PipDisplayLayoutState pipDisplayLayoutState,
            TvPipBoundsAlgorithm tvPipBoundsAlgorithm,
            TvPipBoundsController tvPipBoundsController,
            PipTransitionState pipTransitionState,
            PipAppOpsListener pipAppOpsListener,
            PipTaskOrganizer pipTaskOrganizer,
            TvPipMenuController tvPipMenuController,
            PipMediaController pipMediaController,
            TvPipTransition tvPipTransition,
            TvPipNotificationController tvPipNotificationController,
            TaskStackListenerImpl taskStackListener,
            PipParamsChangedForwarder pipParamsChangedForwarder,
            DisplayController displayController,
            WindowManagerShellWrapper windowManagerShellWrapper,
            @ShellMainThread Handler mainHandler, // needed for registerReceiverForAllUsers()
            @ShellMainThread ShellExecutor mainExecutor) {
        return Optional.of(
                TvPipController.create(
                        context,
                        shellInit,
                        shellController,
                        tvPipBoundsState,
                        pipDisplayLayoutState,
                        tvPipBoundsAlgorithm,
                        tvPipBoundsController,
                        pipTransitionState,
                        pipAppOpsListener,
                        pipTaskOrganizer,
                        tvPipTransition,
                        tvPipMenuController,
                        pipMediaController,
                        tvPipNotificationController,
                        taskStackListener,
                        pipParamsChangedForwarder,
                        displayController,
                        windowManagerShellWrapper,
                        mainHandler,
                        mainExecutor));
    }

    @WMSingleton
    @Provides
    static TvPipBoundsController provideTvPipBoundsController(
            Context context,
            @ShellMainThread Handler mainHandler,
            TvPipBoundsState tvPipBoundsState,
            TvPipBoundsAlgorithm tvPipBoundsAlgorithm) {
        return new TvPipBoundsController(
                context,
                SystemClock::uptimeMillis,
                mainHandler,
                tvPipBoundsState,
                tvPipBoundsAlgorithm);
    }

    @WMSingleton
    @Provides
    static TvPipBoundsAlgorithm provideTvPipBoundsAlgorithm(Context context,
            TvPipBoundsState tvPipBoundsState, PipSnapAlgorithm pipSnapAlgorithm,
            PipDisplayLayoutState pipDisplayLayoutState, LegacySizeSpecSource sizeSpecSource) {
        return new TvPipBoundsAlgorithm(context, tvPipBoundsState, pipSnapAlgorithm,
                pipDisplayLayoutState, sizeSpecSource);
    }

    @WMSingleton
    @Provides
    static TvPipBoundsState provideTvPipBoundsState(Context context,
            LegacySizeSpecSource sizeSpecSource, PipDisplayLayoutState pipDisplayLayoutState) {
        return new TvPipBoundsState(context, sizeSpecSource, pipDisplayLayoutState);
    }

    @WMSingleton
    @Provides
    static LegacySizeSpecSource provideSizeSpecSource(Context context,
            PipDisplayLayoutState pipDisplayLayoutState) {
        return new LegacySizeSpecSource(context, pipDisplayLayoutState);
    }

    @WMSingleton
    @Provides
    static TvPipTransition provideTvPipTransition(
            Context context,
            @NonNull ShellInit shellInit,
            @NonNull ShellTaskOrganizer shellTaskOrganizer,
            @NonNull Transitions transitions,
            TvPipBoundsState tvPipBoundsState,
            TvPipMenuController tvPipMenuController,
            TvPipBoundsAlgorithm tvPipBoundsAlgorithm,
            PipTransitionState pipTransitionState,
            PipAnimationController pipAnimationController,
            PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
            PipDisplayLayoutState pipDisplayLayoutState) {
        return new TvPipTransition(context, shellInit, shellTaskOrganizer, transitions,
                tvPipBoundsState, tvPipMenuController, tvPipBoundsAlgorithm, pipTransitionState,
                pipAnimationController, pipSurfaceTransactionHelper, pipDisplayLayoutState);
    }

    @WMSingleton
    @Provides
    static TvPipMenuController providesTvPipMenuController(
            Context context,
            TvPipBoundsState tvPipBoundsState,
            SystemWindows systemWindows,
            @ShellMainThread Handler mainHandler) {
        return new TvPipMenuController(context, tvPipBoundsState, systemWindows, mainHandler);
    }

    @WMSingleton
    @Provides
    static TvPipNotificationController provideTvPipNotificationController(Context context,
            PipMediaController pipMediaController,
            PipParamsChangedForwarder pipParamsChangedForwarder) {
        return new TvPipNotificationController(context, pipMediaController,
                pipParamsChangedForwarder);
    }

    @WMSingleton
    @Provides
    static PipTransitionState providePipTransitionState() {
        return new PipTransitionState();
    }

    @WMSingleton
    @Provides
    static PipTaskOrganizer providePipTaskOrganizer(Context context,
            TvPipMenuController tvPipMenuController,
            SyncTransactionQueue syncTransactionQueue,
            TvPipBoundsState tvPipBoundsState,
            PipDisplayLayoutState pipDisplayLayoutState,
            PipTransitionState pipTransitionState,
            TvPipBoundsAlgorithm tvPipBoundsAlgorithm,
            PipAnimationController pipAnimationController,
            TvPipTransition tvPipTransition,
            PipParamsChangedForwarder pipParamsChangedForwarder,
            PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
            Optional<SplitScreenController> splitScreenControllerOptional,
            Optional<PipPerfHintController> pipPerfHintControllerOptional,
            DisplayController displayController,
            PipUiEventLogger pipUiEventLogger, ShellTaskOrganizer shellTaskOrganizer,
            @ShellMainThread ShellExecutor mainExecutor) {
        return new TvPipTaskOrganizer(context,
                syncTransactionQueue, pipTransitionState, tvPipBoundsState, pipDisplayLayoutState,
                tvPipBoundsAlgorithm, tvPipMenuController, pipAnimationController,
                pipSurfaceTransactionHelper, tvPipTransition, pipParamsChangedForwarder,
                splitScreenControllerOptional, pipPerfHintControllerOptional, displayController,
                pipUiEventLogger, shellTaskOrganizer, mainExecutor);
    }

    @WMSingleton
    @Provides
    static PipParamsChangedForwarder providePipParamsChangedForwarder() {
        return new PipParamsChangedForwarder();
    }

    @WMSingleton
    @Provides
    static PipAppOpsListener providePipAppOpsListener(Context context,
            PipTaskOrganizer pipTaskOrganizer,
            @ShellMainThread ShellExecutor mainExecutor) {
        return new PipAppOpsListener(context, pipTaskOrganizer::removePip, mainExecutor);
    }
}
