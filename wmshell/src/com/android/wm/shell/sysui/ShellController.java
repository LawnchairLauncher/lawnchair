/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.sysui;

import static android.content.pm.ActivityInfo.CONFIG_ASSETS_PATHS;
import static android.content.pm.ActivityInfo.CONFIG_FONT_SCALE;
import static android.content.pm.ActivityInfo.CONFIG_LAYOUT_DIRECTION;
import static android.content.pm.ActivityInfo.CONFIG_LOCALE;
import static android.content.pm.ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE;
import static android.content.pm.ActivityInfo.CONFIG_UI_MODE;

import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_INIT;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_SYSUI_EVENTS;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.UserInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.ArrayMap;
import android.view.InsetsSource;
import android.view.InsetsState;
import android.view.SurfaceControlRegistry;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.DisplayInsetsController.OnInsetsChangedListener;
import com.android.wm.shell.common.ExternalInterfaceBinder;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.annotations.ExternalThread;

import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Handles event callbacks from SysUI that can be used within the Shell.
 */
public class ShellController {
    private static final String TAG = ShellController.class.getSimpleName();

    private final Context mContext;
    private final ShellInit mShellInit;
    private final ShellCommandHandler mShellCommandHandler;
    private final ShellExecutor mMainExecutor;
    private final DisplayInsetsController mDisplayInsetsController;
    private final ShellInterfaceImpl mImpl = new ShellInterfaceImpl();

    private final CopyOnWriteArrayList<ConfigurationChangeListener> mConfigChangeListeners =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<KeyguardChangeListener> mKeyguardChangeListeners =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<UserChangeListener> mUserChangeListeners =
            new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<DisplayImeChangeListener, Executor> mDisplayImeChangeListeners =
            new ConcurrentHashMap<>();

    private ArrayMap<String, Supplier<ExternalInterfaceBinder>> mExternalInterfaceSuppliers =
            new ArrayMap<>();
    // References to the existing interfaces, to be invalidated when they are recreated
    private ArrayMap<String, ExternalInterfaceBinder> mExternalInterfaces = new ArrayMap<>();

    private Configuration mLastConfiguration;

    private OnInsetsChangedListener mInsetsChangeListener = new OnInsetsChangedListener() {
        private InsetsState mInsetsState = new InsetsState();

        @Override
        public void insetsChanged(InsetsState insetsState) {
            if (mInsetsState == insetsState) {
                return;
            }

            InsetsSource oldSource = mInsetsState.peekSource(InsetsSource.ID_IME);
            boolean wasVisible = (oldSource != null && oldSource.isVisible());
            Rect oldFrame = wasVisible ? oldSource.getFrame() : null;

            InsetsSource newSource = insetsState.peekSource(InsetsSource.ID_IME);
            boolean isVisible = (newSource != null && newSource.isVisible());
            Rect newFrame = isVisible ? newSource.getFrame() : null;

            if (wasVisible != isVisible) {
                onImeVisibilityChanged(isVisible);
            }

            if (newFrame != null && !newFrame.equals(oldFrame)) {
                onImeBoundsChanged(newFrame);
            }

            mInsetsState = insetsState;
        }
    };


    public ShellController(Context context,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            DisplayInsetsController displayInsetsController,
            ShellExecutor mainExecutor) {
        mContext = context;
        mShellInit = shellInit;
        mShellCommandHandler = shellCommandHandler;
        mDisplayInsetsController = displayInsetsController;
        mMainExecutor = mainExecutor;
        shellInit.addInitCallback(this::onInit, this);
    }

    private void onInit() {
        mShellCommandHandler.addDumpCallback(this::dump, this);
        mDisplayInsetsController.addInsetsChangedListener(
                mContext.getDisplayId(), mInsetsChangeListener);
    }

    /**
     * Returns the external interface to this controller.
     */
    public ShellInterface asShell() {
        return mImpl;
    }

    /**
     * Adds a new configuration listener. The configuration change callbacks are not made in any
     * particular order.
     */
    public void addConfigurationChangeListener(ConfigurationChangeListener listener) {
        mConfigChangeListeners.remove(listener);
        mConfigChangeListeners.add(listener);
    }

    /**
     * Removes an existing configuration listener.
     */
    public void removeConfigurationChangeListener(ConfigurationChangeListener listener) {
        mConfigChangeListeners.remove(listener);
    }

    /**
     * Adds a new Keyguard listener. The Keyguard change callbacks are not made in any
     * particular order.
     */
    public void addKeyguardChangeListener(KeyguardChangeListener listener) {
        mKeyguardChangeListeners.remove(listener);
        mKeyguardChangeListeners.add(listener);
    }

    /**
     * Removes an existing Keyguard listener.
     */
    public void removeKeyguardChangeListener(KeyguardChangeListener listener) {
        mKeyguardChangeListeners.remove(listener);
    }

    /**
     * Adds a new user-change listener. The user change callbacks are not made in any
     * particular order.
     */
    public void addUserChangeListener(UserChangeListener listener) {
        mUserChangeListeners.remove(listener);
        mUserChangeListeners.add(listener);
    }

    /**
     * Removes an existing user-change listener.
     */
    public void removeUserChangeListener(UserChangeListener listener) {
        mUserChangeListeners.remove(listener);
    }

    /**
     * Adds an interface that can be called from a remote process. This method takes a supplier
     * because each binder reference is valid for a single process, and in multi-user mode, SysUI
     * will request new binder instances for each instance of Launcher that it provides binders
     * to.
     *
     * @param extra the key for the interface, {@see ShellSharedConstants}
     * @param binderSupplier the supplier of the binder to pass to the external process
     * @param callerInstance the instance of the caller, purely for logging
     */
    public void addExternalInterface(String extra, Supplier<ExternalInterfaceBinder> binderSupplier,
            Object callerInstance) {
        ProtoLog.v(WM_SHELL_INIT, "Adding external interface from %s with key %s",
                callerInstance.getClass().getSimpleName(), extra);
        if (mExternalInterfaceSuppliers.containsKey(extra)) {
            throw new IllegalArgumentException("Supplier with same key already exists: "
                    + extra);
        }
        mExternalInterfaceSuppliers.put(extra, binderSupplier);
    }

    /**
     * Updates the given bundle with the set of external interfaces, invalidating the old set of
     * binders.
     */
    @VisibleForTesting
    public void createExternalInterfaces(Bundle output) {
        // Invalidate the old binders
        for (int i = 0; i < mExternalInterfaces.size(); i++) {
            mExternalInterfaces.valueAt(i).invalidate();
        }
        mExternalInterfaces.clear();

        // Create new binders for each key
        for (int i = 0; i < mExternalInterfaceSuppliers.size(); i++) {
            final String key = mExternalInterfaceSuppliers.keyAt(i);
            final ExternalInterfaceBinder b = mExternalInterfaceSuppliers.valueAt(i).get();
            mExternalInterfaces.put(key, b);
            output.putBinder(key, b.asBinder());
        }
    }

    @VisibleForTesting
    void onConfigurationChanged(Configuration newConfig) {
        // The initial config is send on startup and doesn't trigger listener callbacks
        if (mLastConfiguration == null) {
            mLastConfiguration = new Configuration(newConfig);
            ProtoLog.v(WM_SHELL_SYSUI_EVENTS, "Initial Configuration: %s", newConfig);
            return;
        }

        final int diff = newConfig.diff(mLastConfiguration);
        ProtoLog.v(WM_SHELL_SYSUI_EVENTS, "New configuration change: %s", newConfig);
        ProtoLog.v(WM_SHELL_SYSUI_EVENTS, "\tchanges=%s",
                Configuration.configurationDiffToString(diff));
        final boolean densityFontScaleChanged = (diff & CONFIG_FONT_SCALE) != 0
                || (diff & ActivityInfo.CONFIG_DENSITY) != 0;
        final boolean smallestScreenWidthChanged = (diff & CONFIG_SMALLEST_SCREEN_SIZE) != 0;
        final boolean themeChanged = (diff & CONFIG_ASSETS_PATHS) != 0
                || (diff & CONFIG_UI_MODE) != 0;
        final boolean localOrLayoutDirectionChanged = (diff & CONFIG_LOCALE) != 0
                || (diff & CONFIG_LAYOUT_DIRECTION) != 0;

        // Update the last configuration and call listeners
        mLastConfiguration.updateFrom(newConfig);
        for (ConfigurationChangeListener listener : mConfigChangeListeners) {
            listener.onConfigurationChanged(newConfig);
            if (densityFontScaleChanged) {
                listener.onDensityOrFontScaleChanged();
            }
            if (smallestScreenWidthChanged) {
                listener.onSmallestScreenWidthChanged();
            }
            if (themeChanged) {
                listener.onThemeChanged();
            }
            if (localOrLayoutDirectionChanged) {
                listener.onLocaleOrLayoutDirectionChanged();
            }
        }
    }

    @VisibleForTesting
    void onKeyguardVisibilityChanged(boolean visible, boolean occluded, boolean animatingDismiss) {
        ProtoLog.v(WM_SHELL_SYSUI_EVENTS, "Keyguard visibility changed: visible=%b "
                + "occluded=%b animatingDismiss=%b", visible, occluded, animatingDismiss);
        for (KeyguardChangeListener listener : mKeyguardChangeListeners) {
            listener.onKeyguardVisibilityChanged(visible, occluded, animatingDismiss);
        }
    }

    @VisibleForTesting
    void onKeyguardDismissAnimationFinished() {
        ProtoLog.v(WM_SHELL_SYSUI_EVENTS, "Keyguard dismiss animation finished");
        for (KeyguardChangeListener listener : mKeyguardChangeListeners) {
            listener.onKeyguardDismissAnimationFinished();
        }
    }

    @VisibleForTesting
    void onUserChanged(int newUserId, @NonNull Context userContext) {
        ProtoLog.v(WM_SHELL_SYSUI_EVENTS, "User changed: id=%d", newUserId);
        for (UserChangeListener listener : mUserChangeListeners) {
            listener.onUserChanged(newUserId, userContext);
        }
    }

    @VisibleForTesting
    void onUserProfilesChanged(@NonNull List<UserInfo> profiles) {
        ProtoLog.v(WM_SHELL_SYSUI_EVENTS, "User profiles changed");
        for (UserChangeListener listener : mUserChangeListeners) {
            listener.onUserProfilesChanged(profiles);
        }
    }

    @VisibleForTesting
    void onImeBoundsChanged(Rect bounds) {
        ProtoLog.v(WM_SHELL_SYSUI_EVENTS, "Display Ime bounds changed");
        mDisplayImeChangeListeners.forEach(
                (DisplayImeChangeListener listener, Executor executor) ->
                executor.execute(() -> listener.onImeBoundsChanged(
                    mContext.getDisplayId(), bounds)));
    }

    @VisibleForTesting
    void onImeVisibilityChanged(boolean isShowing) {
        ProtoLog.v(WM_SHELL_SYSUI_EVENTS, "Display Ime visibility changed: isShowing=%b",
                isShowing);
        mDisplayImeChangeListeners.forEach(
                (DisplayImeChangeListener listener, Executor executor) ->
                executor.execute(() -> listener.onImeVisibilityChanged(
                    mContext.getDisplayId(), isShowing)));
    }

    private void handleInit() {
        SurfaceControlRegistry.createProcessInstance(mContext);
        mShellInit.init();
    }

    private void handleDump(PrintWriter pw) {
        mShellCommandHandler.dump(pw);
        SurfaceControlRegistry.dump(100 /* limit */, false /* runGc */, pw);
    }

    public void dump(@NonNull PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        pw.println(prefix + TAG);
        pw.println(innerPrefix + "mConfigChangeListeners=" + mConfigChangeListeners.size());
        pw.println(innerPrefix + "mLastConfiguration=" + mLastConfiguration);
        pw.println(innerPrefix + "mKeyguardChangeListeners=" + mKeyguardChangeListeners.size());
        pw.println(innerPrefix + "mUserChangeListeners=" + mUserChangeListeners.size());

        if (!mExternalInterfaces.isEmpty()) {
            pw.println(innerPrefix + "mExternalInterfaces={");
            for (String key : mExternalInterfaces.keySet()) {
                pw.println(innerPrefix + "\t" + key + ": " + mExternalInterfaces.get(key));
            }
            pw.println(innerPrefix + "}");
        }
    }

    /**
     * The interface for calls from outside the Shell, within the host process.
     */
    @ExternalThread
    private class ShellInterfaceImpl implements ShellInterface {
        @Override
        public void onInit() {
            mMainExecutor.execute(ShellController.this::handleInit);
        }

        @Override
        public void onConfigurationChanged(Configuration newConfiguration) {
            mMainExecutor.execute(() ->
                    ShellController.this.onConfigurationChanged(newConfiguration));
        }

        @Override
        public void onKeyguardVisibilityChanged(boolean visible, boolean occluded,
                boolean animatingDismiss) {
            mMainExecutor.execute(() ->
                    ShellController.this.onKeyguardVisibilityChanged(visible, occluded,
                            animatingDismiss));
        }

        @Override
        public void onKeyguardDismissAnimationFinished() {
            mMainExecutor.execute(() ->
                    ShellController.this.onKeyguardDismissAnimationFinished());
        }

        @Override
        public void onUserChanged(int newUserId, @NonNull Context userContext) {
            mMainExecutor.execute(() ->
                    ShellController.this.onUserChanged(newUserId, userContext));
        }

        @Override
        public void onUserProfilesChanged(@NonNull List<UserInfo> profiles) {
            mMainExecutor.execute(() ->
                    ShellController.this.onUserProfilesChanged(profiles));
        }

        @Override
        public void addDisplayImeChangeListener(DisplayImeChangeListener listener,
                Executor executor) {
            ProtoLog.v(WM_SHELL_SYSUI_EVENTS, "Adding new DisplayImeChangeListener");
            mDisplayImeChangeListeners.put(listener, executor);
        }

        @Override
        public void removeDisplayImeChangeListener(DisplayImeChangeListener listener) {
            ProtoLog.v(WM_SHELL_SYSUI_EVENTS, "Removing DisplayImeChangeListener");
            mDisplayImeChangeListeners.remove(listener);
        }

        @Override
        public boolean handleCommand(String[] args, PrintWriter pw) {
            try {
                boolean[] result = new boolean[1];
                mMainExecutor.executeBlocking(() -> {
                    result[0] = mShellCommandHandler.handleCommand(args, pw);
                });
                return result[0];
            } catch (InterruptedException e) {
                throw new RuntimeException("Failed to handle Shell command in 2s", e);
            }
        }

        @Override
        public void createExternalInterfaces(Bundle bundle) {
            try {
                mMainExecutor.executeBlocking(() -> {
                    ShellController.this.createExternalInterfaces(bundle);
                });
            } catch (InterruptedException e) {
                throw new RuntimeException("Failed to get Shell command in 2s", e);
            }
        }

        @Override
        public void dump(PrintWriter pw) {
            try {
                mMainExecutor.executeBlocking(() -> ShellController.this.handleDump(pw));
            } catch (InterruptedException e) {
                throw new RuntimeException("Failed to dump the Shell in 2s", e);
            }
        }
    }
}
