/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.os.HandlerThread;

import androidx.annotation.Nullable;

import com.android.wm.shell.appzoomout.AppZoomOut;
import com.android.wm.shell.back.BackAnimation;
import com.android.wm.shell.bubbles.Bubbles;
import com.android.wm.shell.bubbles.bar.BubbleBarExpandedView;
import com.android.wm.shell.desktopmode.DesktopMode;
import com.android.wm.shell.displayareahelper.DisplayAreaHelper;
import com.android.wm.shell.keyguard.KeyguardTransitions;
import com.android.wm.shell.onehanded.OneHanded;
import com.android.wm.shell.pip.Pip;
import com.android.wm.shell.recents.RecentTasks;
import com.android.wm.shell.shared.ShellTransitions;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.splitscreen.SplitScreen;
import com.android.wm.shell.startingsurface.StartingSurface;
import com.android.wm.shell.sysui.ShellInterface;
import com.android.wm.shell.taskview.TaskViewFactory;
import com.android.wm.shell.windowdecor.viewholder.AppHandles;

import java.util.Optional;

import dagger.BindsInstance;
import dagger.Subcomponent;

/**
 * Dagger Subcomponent for WindowManager.  This class explicitly describes the interfaces exported
 * from the WM component into the SysUI component, and references the specific dependencies
 * provided by its particular device/form-factor SystemUI implementation.
 *
 * <p> ie. {@link WMComponent} includes {@link WMShellModule} and {@code TvWMComponent} includes
 * {@link TvWMShellModule}
 */
@WMSingleton
@Subcomponent(modules = {WMShellModule.class})
public interface WMComponent {

    /**
     * Builder for a WMComponent.
     */
    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        Builder setShellMainThread(@Nullable @ShellMainThread HandlerThread t);

        WMComponent build();
    }

    /**
     * Initializes all the WMShell components before starting any of the SystemUI components.
     */
    default void init() {
        getShell().onInit();
    }

    // Interfaces provided to SysUI

    @WMSingleton
    ShellInterface getShell();

    @WMSingleton
    Optional<OneHanded> getOneHanded();

    @WMSingleton
    Optional<Pip> getPip();

    @WMSingleton
    Optional<SplitScreen> getSplitScreen();

    @WMSingleton
    Optional<Bubbles> getBubbles();

    @WMSingleton
    Optional<TaskViewFactory> getTaskViewFactory();

    @WMSingleton
    ShellTransitions getShellTransitions();

    @WMSingleton
    KeyguardTransitions getKeyguardTransitions();

    @WMSingleton
    Optional<StartingSurface> getStartingSurface();

    @WMSingleton
    Optional<DisplayAreaHelper> getDisplayAreaHelper();

    @WMSingleton
    Optional<RecentTasks> getRecentTasks();

    @WMSingleton
    Optional<BackAnimation> getBackAnimation();

    /**
     * Optional {@link DesktopMode} component for interacting with desktop mode.
     */
    @WMSingleton
    Optional<DesktopMode> getDesktopMode();

    @WMSingleton
    Optional<AppZoomOut> getAppZoomOut();

    @WMSingleton
    Optional<AppHandles> getAppHandles();

    // Injector methods to support field injection

    /** Injector method for {@link BubbleBarExpandedView}. */
    void inject(BubbleBarExpandedView bubbleBarExpandedView);
}
