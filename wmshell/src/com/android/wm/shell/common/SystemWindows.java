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

package com.android.wm.shell.common;

import static android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Region;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.MergedConfiguration;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;
import android.view.DragEvent;
import android.view.IScrollCaptureResponseListener;
import android.view.IWindow;
import android.view.IWindowManager;
import android.view.IWindowSession;
import android.view.IWindowSessionCallback;
import android.view.InsetsSourceControl;
import android.view.InsetsState;
import android.view.ScrollCaptureResponse;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceSession;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowlessWindowManager;
import android.view.inputmethod.ImeTracker;
import android.window.ActivityWindowInfo;
import android.window.ClientWindowFrames;
import android.window.InputTransferToken;

import com.android.internal.os.IResultReceiver;

import java.util.HashMap;

/**
 * Represents the "windowing" layer of the WM Shell. This layer allows shell components to place and
 * manipulate windows without talking to WindowManager.
 */
public class SystemWindows {
    private static final String TAG = "SystemWindows";

    private final SparseArray<PerDisplay> mPerDisplay = new SparseArray<>();
    private final HashMap<View, SurfaceControlViewHost> mViewRoots = new HashMap<>();
    private final DisplayController mDisplayController;
    private final IWindowManager mWmService;
    private IWindowSession mSession;

    private final DisplayController.OnDisplaysChangedListener mDisplayListener =
            new DisplayController.OnDisplaysChangedListener() {
                @Override
                public void onDisplayAdded(int displayId) { }

                @Override
                public void onDisplayConfigurationChanged(int displayId, Configuration newConfig) {
                    PerDisplay pd = mPerDisplay.get(displayId);
                    if (pd == null) {
                        return;
                    }
                    pd.updateConfiguration(newConfig);
                }

                @Override
                public void onDisplayRemoved(int displayId) { }
            };

    public SystemWindows(DisplayController displayController, IWindowManager wmService) {
        mWmService = wmService;
        mDisplayController = displayController;
        mDisplayController.addDisplayWindowListener(mDisplayListener);
        try {
            mSession = wmService.openSession(
                    new IWindowSessionCallback.Stub() {
                        @Override
                        public void onAnimatorScaleChanged(float scale) {}
                    });
        } catch (RemoteException e) {
            Slog.e(TAG, "Unable to create layer", e);
        }
    }

    /**
     * Adds a view to system-ui window management.
     */
    public void addView(View view, WindowManager.LayoutParams attrs, int displayId,
            @WindowManager.ShellRootLayer int shellRootLayer) {
        PerDisplay pd = mPerDisplay.get(displayId);
        if (pd == null) {
            pd = new PerDisplay(displayId);
            mPerDisplay.put(displayId, pd);
        }
        pd.addView(view, attrs, shellRootLayer);
    }

    /**
     * Removes a view from system-ui window management.
     * @param view
     */
    public void removeView(View view) {
        SurfaceControlViewHost root = mViewRoots.remove(view);
        root.release();
    }

    /**
     * Updates the layout params of a view.
     */
    public void updateViewLayout(@NonNull View view, ViewGroup.LayoutParams params) {
        SurfaceControlViewHost root = mViewRoots.get(view);
        if (root == null || !(params instanceof WindowManager.LayoutParams)) {
            return;
        }
        view.setLayoutParams(params);
        root.relayout((WindowManager.LayoutParams) params);
    }

    /**
     * Sets the accessibility window for the given {@param shellRootLayer}.
     */
    public void setShellRootAccessibilityWindow(int displayId,
            @WindowManager.ShellRootLayer int shellRootLayer, View view) {
        PerDisplay pd = mPerDisplay.get(displayId);
        if (pd == null) {
            return;
        }
        pd.setShellRootAccessibilityWindow(shellRootLayer, view);
    }

    /**
     * Sets the touchable region of a view's window. This will be cropped to the window size.
     * @param view
     * @param region
     */
    public void setTouchableRegion(@NonNull View view, Region region) {
        SurfaceControlViewHost root = mViewRoots.get(view);
        if (root == null) {
            return;
        }
        WindowlessWindowManager wwm = root.getWindowlessWM();
        if (!(wwm instanceof SysUiWindowManager)) {
            return;
        }
        ((SysUiWindowManager) wwm).setTouchableRegionForWindow(view, region);
    }

    /**
     * Get the IWindow token for a specific root.
     *
     * @param windowType A window type from {@link WindowManager}.
     */
    IWindow getWindow(int displayId, int windowType) {
        PerDisplay pd = mPerDisplay.get(displayId);
        if (pd == null) {
            return null;
        }
        return pd.getWindow(windowType);
    }

    /**
     * Gets the SurfaceControl associated with a root view. This is the same surface that backs the
     * ViewRootImpl.
     */
    public SurfaceControl getViewSurface(View rootView) {
        for (int i = 0; i < mPerDisplay.size(); ++i) {
            for (int iWm = 0; iWm < mPerDisplay.valueAt(i).mWwms.size(); ++iWm) {
                SurfaceControl out = mPerDisplay.valueAt(i).mWwms.valueAt(iWm)
                        .getSurfaceControlForWindow(rootView);
                if (out != null) {
                    return out;
                }
            }
        }
        return null;
    }

    /**
     * Gets a token associated with the view that can be used to grant the view focus.
     */
    public InputTransferToken getFocusGrantToken(View view) {
        SurfaceControlViewHost root = mViewRoots.get(view);
        if (root == null) {
            Slog.e(TAG, "Couldn't get focus grant token since view does not exist in "
                    + "SystemWindow:" + view);
            return null;
        }
        return root.getInputTransferToken();
    }

    private class PerDisplay {
        final int mDisplayId;
        private final SparseArray<SysUiWindowManager> mWwms = new SparseArray<>();

        PerDisplay(int displayId) {
            mDisplayId = displayId;
        }

        public void addView(View view, WindowManager.LayoutParams attrs,
                @WindowManager.ShellRootLayer int shellRootLayer) {
            SysUiWindowManager wwm = addRoot(shellRootLayer);
            if (wwm == null) {
                Slog.e(TAG, "Unable to create systemui root");
                return;
            }
            final Display display = mDisplayController.getDisplay(mDisplayId);
            SurfaceControlViewHost viewRoot =
                    new SurfaceControlViewHost(view.getContext(), display, wwm, "SystemWindows");
            attrs.flags |= FLAG_HARDWARE_ACCELERATED;
            viewRoot.setView(view, attrs);
            mViewRoots.put(view, viewRoot);
            setShellRootAccessibilityWindow(shellRootLayer, view);
        }

        SysUiWindowManager addRoot(@WindowManager.ShellRootLayer int shellRootLayer) {
            SysUiWindowManager wwm = mWwms.get(shellRootLayer);
            if (wwm != null) {
                return wwm;
            }
            SurfaceControl rootSurface = null;
            ContainerWindow win = new ContainerWindow();
            try {
                rootSurface = mWmService.addShellRoot(mDisplayId, win, shellRootLayer);
            } catch (RemoteException e) {
            }
            if (rootSurface == null) {
                Slog.e(TAG, "Unable to get root surfacecontrol for systemui");
                return null;
            }
            Context displayContext = mDisplayController.getDisplayContext(mDisplayId);
            wwm = new SysUiWindowManager(mDisplayId, displayContext, rootSurface, win);
            mWwms.put(shellRootLayer, wwm);
            return wwm;
        }

        IWindow getWindow(int windowType) {
            SysUiWindowManager wwm = mWwms.get(windowType);
            if (wwm == null) {
                return null;
            }
            return wwm.mContainerWindow;
        }

        void setShellRootAccessibilityWindow(@WindowManager.ShellRootLayer int shellRootLayer,
                View view) {
            SysUiWindowManager wwm = mWwms.get(shellRootLayer);
            if (wwm == null) {
                return;
            }
            try {
                mWmService.setShellRootAccessibilityWindow(mDisplayId, shellRootLayer,
                        view != null ? mViewRoots.get(view).getWindowToken() : null);
            } catch (RemoteException e) {
                Slog.e(TAG, "Error setting accessibility window for " + mDisplayId + ":"
                        + shellRootLayer, e);
            }
        }

        void updateConfiguration(Configuration configuration) {
            for (int i = 0; i < mWwms.size(); ++i) {
                mWwms.valueAt(i).updateConfiguration(configuration);
            }
        }
    }

    /**
     * A subclass of WindowlessWindowManager that provides insets to its viewroots.
     */
    public class SysUiWindowManager extends WindowlessWindowManager {
        final int mDisplayId;
        ContainerWindow mContainerWindow;
        final HashMap<IBinder, SurfaceControl> mLeashForWindow =
                new HashMap<IBinder, SurfaceControl>();
        public SysUiWindowManager(int displayId, Context ctx, SurfaceControl rootSurface,
                ContainerWindow container) {
            super(ctx.getResources().getConfiguration(), rootSurface, null /* hostInputToken */);
            mContainerWindow = container;
            mDisplayId = displayId;
        }

        void updateConfiguration(Configuration configuration) {
            setConfiguration(configuration);
        }

        SurfaceControl getSurfaceControlForWindow(View rootView) {
            synchronized (this) {
                return mLeashForWindow.get(getWindowBinder(rootView));
            }
        }

        @Override
        protected SurfaceControl getParentSurface(IWindow window,
                WindowManager.LayoutParams attrs) {
            SurfaceControl leash = new SurfaceControl.Builder(new SurfaceSession())
                  .setContainerLayer()
                  .setName("SystemWindowLeash")
                  .setHidden(false)
                  .setParent(mRootSurface)
                  .setCallsite("SysUiWIndowManager#attachToParentSurface").build();
            synchronized (this) {
                mLeashForWindow.put(window.asBinder(), leash);
            }
            return leash;
        }

        @Override
        public void remove(IBinder clientToken) throws RemoteException {
            super.remove(clientToken);
            synchronized(this) {
                new SurfaceControl.Transaction().remove(mLeashForWindow.get(clientToken))
                    .apply();
                mLeashForWindow.remove(clientToken);
            }
        }

        void setTouchableRegionForWindow(View rootView, Region region) {
            IBinder token = rootView.getWindowToken();
            if (token == null) {
                return;
            }
            setTouchRegion(token, region);
        }
    }

    static class ContainerWindow extends IWindow.Stub {
        ContainerWindow() {}

        @Override
        public void resized(ClientWindowFrames frames, boolean reportDraw,
                MergedConfiguration newMergedConfiguration, InsetsState insetsState,
                boolean forceLayout, boolean alwaysConsumeSystemBars, int displayId, int syncSeqId,
                boolean dragResizing, @Nullable ActivityWindowInfo activityWindowInfo) {}

        @Override
        public void insetsControlChanged(InsetsState insetsState,
                InsetsSourceControl.Array activeControls) {}

        @Override
        public void showInsets(int types, boolean fromIme, @Nullable ImeTracker.Token statsToken) {}

        @Override
        public void hideInsets(int types, boolean fromIme, @Nullable ImeTracker.Token statsToken) {}

        @Override
        public void moved(int newX, int newY) {}

        @Override
        public void dispatchAppVisibility(boolean visible) {}

        @Override
        public void dispatchGetNewSurface() {}

        @Override
        public void executeCommand(String command, String parameters, ParcelFileDescriptor out) {}

        @Override
        public void closeSystemDialogs(String reason) {}

        @Override
        public void dispatchWallpaperOffsets(float x, float y, float xStep, float yStep,
                float zoom, boolean sync) {}

        @Override
        public void dispatchWallpaperCommand(String action, int x, int y,
                int z, Bundle extras, boolean sync) {}

        /* Drag/drop */
        @Override
        public void dispatchDragEvent(DragEvent event) {}

        @Override
        public void dispatchWindowShown() {}

        @Override
        public void requestAppKeyboardShortcuts(IResultReceiver receiver, int deviceId) {}

        @Override
        public void requestScrollCapture(IScrollCaptureResponseListener listener) {
            try {
                listener.onScrollCaptureResponse(
                        new ScrollCaptureResponse.Builder()
                                .setDescription("Not Implemented")
                                .build());

            } catch (RemoteException ex) {
                // ignore
            }
        }

        @Override
        public void dumpWindow(ParcelFileDescriptor pfd) {

        }
    }
}
