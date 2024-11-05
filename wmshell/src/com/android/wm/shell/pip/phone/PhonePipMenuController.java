/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.pip.phone;

import static android.view.WindowManager.SHELL_ROOT_LAYER_PIP;

import android.annotation.Nullable;
import android.app.RemoteAction;
import android.content.Context;
import android.graphics.Rect;
import android.os.Debug;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Size;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewRootImpl;
import android.view.WindowManagerGlobal;

import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SystemWindows;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipMediaController;
import com.android.wm.shell.common.pip.PipMediaController.ActionListener;
import com.android.wm.shell.common.pip.PipMenuController;
import com.android.wm.shell.common.pip.PipUiEventLogger;
import com.android.wm.shell.protolog.ShellProtoLogGroup;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the PiP menu view which can show menu options or a scrim.
 *
 * The current media session provides actions whenever there are no valid actions provided by the
 * current PiP activity. Otherwise, those actions always take precedence.
 */
public class PhonePipMenuController implements PipMenuController {

    private static final String TAG = "PhonePipMenuController";
    private static final boolean DEBUG = false;

    public static final int MENU_STATE_NONE = 0;
    public static final int MENU_STATE_FULL = 1;

    /**
     * A listener interface to receive notification on changes in PIP.
     */
    public interface Listener {
        /**
         * Called when the PIP menu visibility change has started.
         *
         * @param menuState the new, about-to-change state of the menu
         * @param resize whether or not to resize the PiP with the state change
         */
        void onPipMenuStateChangeStart(int menuState, boolean resize, Runnable callback);

        /**
         * Called when the PIP menu state has finished changing/animating.
         *
         * @param menuState the new state of the menu.
         */
        void onPipMenuStateChangeFinish(int menuState);

        /**
         * Called when the PIP requested to be expanded.
         */
        void onPipExpand();

        /**
         * Called when the PIP requested to be dismissed.
         */
        void onPipDismiss();

        /**
         * Called when the PIP requested to show the menu.
         */
        void onPipShowMenu();
    }

    private final Context mContext;
    private final PipBoundsState mPipBoundsState;
    private final PipMediaController mMediaController;
    private final ShellExecutor mMainExecutor;
    private final Handler mMainHandler;

    private final float[] mTmpTransform = new float[9];

    private final ArrayList<Listener> mListeners = new ArrayList<>();
    private final SystemWindows mSystemWindows;
    private final PipUiEventLogger mPipUiEventLogger;

    private List<RemoteAction> mAppActions;
    private RemoteAction mCloseAction;
    private List<RemoteAction> mMediaActions;

    private int mMenuState;

    private PipMenuView mPipMenuView;

    private SurfaceControl mLeash;

    private ActionListener mMediaActionListener = new ActionListener() {
        @Override
        public void onMediaActionsChanged(List<RemoteAction> mediaActions) {
            mMediaActions = new ArrayList<>(mediaActions);
            updateMenuActions();
        }
    };

    public PhonePipMenuController(Context context, PipBoundsState pipBoundsState,
            PipMediaController mediaController, SystemWindows systemWindows,
            PipUiEventLogger pipUiEventLogger,
            ShellExecutor mainExecutor, Handler mainHandler) {
        mContext = context;
        mPipBoundsState = pipBoundsState;
        mMediaController = mediaController;
        mSystemWindows = systemWindows;
        mMainExecutor = mainExecutor;
        mMainHandler = mainHandler;
        mPipUiEventLogger = pipUiEventLogger;
    }

    public boolean isMenuVisible() {
        return mPipMenuView != null && mMenuState != MENU_STATE_NONE;
    }

    /**
     * Attach the menu when the PiP task first appears.
     */
    @Override
    public void attach(SurfaceControl leash) {
        mLeash = leash;
        attachPipMenuView();
    }

    /**
     * Detach the menu when the PiP task is gone.
     */
    @Override
    public void detach() {
        hideMenu();
        detachPipMenuView();
        mLeash = null;
    }

    void attachPipMenuView() {
        // In case detach was not called (e.g. PIP unexpectedly closed)
        if (mPipMenuView != null) {
            detachPipMenuView();
        }
        mPipMenuView = new PipMenuView(mContext, this, mMainExecutor, mMainHandler,
                mPipUiEventLogger);
        mPipMenuView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                v.getViewRootImpl().addSurfaceChangedCallback(
                        new ViewRootImpl.SurfaceChangedCallback() {
                            @Override
                            public void surfaceCreated(SurfaceControl.Transaction t) {
                                final SurfaceControl sc = getSurfaceControl();
                                if (sc != null) {
                                    t.reparent(sc, mLeash);
                                    // make menu on top of the surface
                                    t.setLayer(sc, Integer.MAX_VALUE);
                                }
                            }

                            @Override
                            public void surfaceReplaced(SurfaceControl.Transaction t) {
                            }

                            @Override
                            public void surfaceDestroyed() {
                            }
                        });
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
            }
        });

        mSystemWindows.addView(mPipMenuView,
                getPipMenuLayoutParams(mContext, MENU_WINDOW_TITLE, 0 /* width */, 0 /* height */),
                0, SHELL_ROOT_LAYER_PIP);
        setShellRootAccessibilityWindow();

        // Make sure the initial actions are set
        updateMenuActions();
    }

    private void detachPipMenuView() {
        if (mPipMenuView == null) {
            return;
        }

        mSystemWindows.removeView(mPipMenuView);
        mPipMenuView = null;
    }

    /**
     * Updates the layout parameters of the menu.
     * @param destinationBounds New Menu bounds.
     */
    @Override
    public void updateMenuBounds(Rect destinationBounds) {
        mSystemWindows.updateViewLayout(mPipMenuView,
                getPipMenuLayoutParams(mContext, MENU_WINDOW_TITLE, destinationBounds.width(),
                        destinationBounds.height()));
        updateMenuLayout(destinationBounds);
    }

    /**
     * Tries to grab a surface control from {@link PipMenuView}. If this isn't available for some
     * reason (ie. the window isn't ready yet, thus {@link android.view.ViewRootImpl} is
     * {@code null}), it will get the leash that the WindowlessWM has assigned to it.
     */
    public SurfaceControl getSurfaceControl() {
        return mSystemWindows.getViewSurface(mPipMenuView);
    }

    /**
     * Adds a new menu activity listener.
     */
    public void addListener(Listener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    @Nullable
    Size getEstimatedMinMenuSize() {
        return mPipMenuView == null ? null : mPipMenuView.getEstimatedMinMenuSize();
    }

    /**
     * When other components requests the menu controller directly to show the menu, we must
     * first fire off the request to the other listeners who will then propagate the call
     * back to the controller with the right parameters.
     */
    @Override
    public void showMenu() {
        mListeners.forEach(Listener::onPipShowMenu);
    }

    /**
     * Similar to {@link #showMenu(int, Rect, boolean, boolean, boolean)} but only show the menu
     * upon PiP window transition is finished.
     */
    public void showMenuWithPossibleDelay(int menuState, Rect stackBounds, boolean allowMenuTimeout,
            boolean willResizeMenu, boolean showResizeHandle) {
        if (willResizeMenu) {
            // hide all visible controls including close button and etc. first, this is to ensure
            // menu is totally invisible during the transition to eliminate unpleasant artifacts
            fadeOutMenu();
        }
        showMenuInternal(menuState, stackBounds, allowMenuTimeout, willResizeMenu,
                willResizeMenu /* withDelay=willResizeMenu here */, showResizeHandle);
    }

    /**
     * Shows the menu activity immediately.
     */
    public void showMenu(int menuState, Rect stackBounds, boolean allowMenuTimeout,
            boolean willResizeMenu, boolean showResizeHandle) {
        showMenuInternal(menuState, stackBounds, allowMenuTimeout, willResizeMenu,
                false /* withDelay */, showResizeHandle);
    }

    private void showMenuInternal(int menuState, Rect stackBounds, boolean allowMenuTimeout,
            boolean willResizeMenu, boolean withDelay, boolean showResizeHandle) {
        if (DEBUG) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: showMenu() state=%s"
                            + " isMenuVisible=%s"
                            + " allowMenuTimeout=%s"
                            + " willResizeMenu=%s"
                            + " withDelay=%s"
                            + " showResizeHandle=%s"
                            + " callers=\n%s", TAG, menuState, isMenuVisible(), allowMenuTimeout,
                    willResizeMenu, withDelay, showResizeHandle, Debug.getCallers(5, "    "));
        }

        if (!checkPipMenuState()) {
            return;
        }

        // Sync the menu bounds before showing it in case it is out of sync.
        movePipMenu(null /* pipLeash */, null /* transaction */, stackBounds,
                PipMenuController.ALPHA_NO_CHANGE);
        updateMenuBounds(stackBounds);

        mPipMenuView.showMenu(menuState, stackBounds, allowMenuTimeout, willResizeMenu, withDelay,
                showResizeHandle);
    }

    /**
     * Move the PiP menu, which does a translation and possibly a scale transformation.
     */
    @Override
    public void movePipMenu(@Nullable SurfaceControl pipLeash,
            @Nullable SurfaceControl.Transaction t,
            Rect destinationBounds, float alpha) {
        if (destinationBounds.isEmpty()) {
            return;
        }

        if (!checkPipMenuState()) {
            return;
        }

        // TODO(b/286307861) transaction should be applied outside of PiP menu controller
        if (pipLeash != null && t != null) {
            t.apply();
        }
    }

    /**
     * Does an immediate window crop of the PiP menu.
     */
    @Override
    public void resizePipMenu(@Nullable SurfaceControl pipLeash,
            @Nullable SurfaceControl.Transaction t,
            Rect destinationBounds) {
        if (destinationBounds.isEmpty()) {
            return;
        }

        if (!checkPipMenuState()) {
            return;
        }

        // TODO(b/286307861) transaction should be applied outside of PiP menu controller
        if (pipLeash != null && t != null) {
            t.apply();
        }
    }

    private boolean checkPipMenuState() {
        if (mPipMenuView == null || mPipMenuView.getViewRootImpl() == null) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Not going to move PiP, either menu or its parent is not created.", TAG);
            return false;
        }

        return true;
    }

    /**
     * Pokes the menu, indicating that the user is interacting with it.
     */
    public void pokeMenu() {
        final boolean isMenuVisible = isMenuVisible();
        if (DEBUG) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: pokeMenu() isMenuVisible=%b", TAG, isMenuVisible);
        }
        if (isMenuVisible) {
            mPipMenuView.pokeMenu();
        }
    }

    private void fadeOutMenu() {
        final boolean isMenuVisible = isMenuVisible();
        if (DEBUG) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: fadeOutMenu() isMenuVisible=%b", TAG, isMenuVisible);
        }
        if (isMenuVisible) {
            mPipMenuView.fadeOutMenu();
        }
    }

    /**
     * Hides the menu view.
     */
    public void hideMenu() {
        final boolean isMenuVisible = isMenuVisible();
        if (isMenuVisible) {
            mPipMenuView.hideMenu();
        }
    }

    /**
     * Hides the menu view.
     *
     * @param animationType the animation type to use upon hiding the menu
     * @param resize whether or not to resize the PiP with the state change
     */
    public void hideMenu(@PipMenuView.AnimationType int animationType, boolean resize) {
        final boolean isMenuVisible = isMenuVisible();
        if (DEBUG) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: hideMenu() state=%s"
                            + " isMenuVisible=%s"
                            + " animationType=%s"
                            + " resize=%s"
                            + " callers=\n%s", TAG, mMenuState, isMenuVisible,
                    animationType, resize,
                    Debug.getCallers(5, "    "));
        }
        if (isMenuVisible) {
            mPipMenuView.hideMenu(resize, animationType);
        }
    }

    /**
     * Hides the menu activity.
     */
    public void hideMenu(Runnable onStartCallback, Runnable onEndCallback) {
        if (isMenuVisible()) {
            // If the menu is visible in either the closed or full state, then hide the menu and
            // trigger the animation trigger afterwards
            if (onStartCallback != null) {
                onStartCallback.run();
            }
            mPipMenuView.hideMenu(onEndCallback);
        }
    }

    /**
     * Sets the menu actions to the actions provided by the current PiP menu.
     */
    @Override
    public void setAppActions(List<RemoteAction> appActions,
            RemoteAction closeAction) {
        mAppActions = appActions;
        mCloseAction = closeAction;
        updateMenuActions();
    }

    void onPipExpand() {
        mListeners.forEach(Listener::onPipExpand);
    }

    void onPipDismiss() {
        mListeners.forEach(Listener::onPipDismiss);
    }

    /**
     * @return the best set of actions to show in the PiP menu.
     */
    private List<RemoteAction> resolveMenuActions() {
        if (isValidActions(mAppActions)) {
            return mAppActions;
        }
        return mMediaActions;
    }

    /**
     * Updates the PiP menu with the best set of actions provided.
     */
    private void updateMenuActions() {
        if (mPipMenuView != null) {
            mPipMenuView.setActions(mPipBoundsState.getBounds(),
                    resolveMenuActions(), mCloseAction);
        }
    }

    /**
     * Returns whether the set of actions are valid.
     */
    private static boolean isValidActions(List<?> actions) {
        return actions != null && actions.size() > 0;
    }

    /**
     * Handles changes in menu visibility.
     */
    void onMenuStateChangeStart(int menuState, boolean resize, Runnable callback) {
        if (DEBUG) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: onMenuStateChangeStart() mMenuState=%s"
                            + " menuState=%s resize=%s"
                            + " callers=\n%s", TAG, mMenuState, menuState, resize,
                    Debug.getCallers(5, "    "));
        }

        if (menuState != mMenuState) {
            mListeners.forEach(l -> l.onPipMenuStateChangeStart(menuState, resize, callback));
            if (menuState == MENU_STATE_FULL) {
                // Once visible, start listening for media action changes. This call will trigger
                // the menu actions to be updated again.
                mMediaController.addActionListener(mMediaActionListener);
            } else {
                // Once hidden, stop listening for media action changes. This call will trigger
                // the menu actions to be updated again.
                mMediaController.removeActionListener(mMediaActionListener);
            }

            try {
                WindowManagerGlobal.getWindowSession().grantEmbeddedWindowFocus(null /* window */,
                        mSystemWindows.getFocusGrantToken(mPipMenuView),
                        menuState != MENU_STATE_NONE /* grantFocus */);
            } catch (RemoteException e) {
                ProtoLog.e(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: Unable to update focus as menu appears/disappears, %s", TAG, e);
            }
        }
    }

    void onMenuStateChangeFinish(int menuState) {
        if (menuState != mMenuState) {
            mListeners.forEach(l -> l.onPipMenuStateChangeFinish(menuState));
        }
        mMenuState = menuState;
        setShellRootAccessibilityWindow();
    }

    private void setShellRootAccessibilityWindow() {
        switch (mMenuState) {
            case MENU_STATE_NONE:
                mSystemWindows.setShellRootAccessibilityWindow(0, SHELL_ROOT_LAYER_PIP, null);
                break;
            default:
                mSystemWindows.setShellRootAccessibilityWindow(0, SHELL_ROOT_LAYER_PIP,
                        mPipMenuView);
                break;
        }
    }

    /**
     * Handles a pointer event sent from pip input consumer.
     */
    void handlePointerEvent(MotionEvent ev) {
        if (mPipMenuView == null) {
            return;
        }

        if (ev.isTouchEvent()) {
            mPipMenuView.dispatchTouchEvent(ev);
        } else {
            mPipMenuView.dispatchGenericMotionEvent(ev);
        }
    }

    /**
     * Tell the PIP Menu to recalculate its layout given its current position on the display.
     */
    public void updateMenuLayout(Rect bounds) {
        final boolean isMenuVisible = isMenuVisible();
        if (DEBUG) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: updateMenuLayout() state=%s"
                            + " isMenuVisible=%s"
                            + " callers=\n%s", TAG, mMenuState, isMenuVisible,
                    Debug.getCallers(5, "    "));
        }
        if (isMenuVisible) {
            mPipMenuView.updateMenuLayout(bounds);
        }
    }

    void dump(PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        pw.println(prefix + TAG);
        pw.println(innerPrefix + "mMenuState=" + mMenuState);
        pw.println(innerPrefix + "mPipMenuView=" + mPipMenuView);
        pw.println(innerPrefix + "mListeners=" + mListeners.size());
    }
}
