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

import static android.view.EventLogTags.IMF_IME_REMOTE_ANIM_CANCEL;
import static android.view.EventLogTags.IMF_IME_REMOTE_ANIM_END;
import static android.view.EventLogTags.IMF_IME_REMOTE_ANIM_START;
import static android.view.inputmethod.ImeTracker.DEBUG_IME_VISIBILITY;

import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_IME_CONTROLLER;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.EventLog;
import android.util.Slog;
import android.util.SparseArray;
import android.view.IDisplayWindowInsetsController;
import android.view.IWindowManager;
import android.view.InsetsSource;
import android.view.InsetsSourceControl;
import android.view.InsetsState;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.WindowInsets;
import android.view.WindowInsets.Type.InsetsType;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.ImeTracker;
import android.view.inputmethod.InputMethodManagerGlobal;

import androidx.annotation.VisibleForTesting;

import com.android.internal.inputmethod.SoftInputShowHideReason;
import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.shared.TransactionPool;
import com.android.wm.shell.sysui.ShellInit;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Manages IME control at the display-level. This occurs when IME comes up in multi-window mode.
 */
public class DisplayImeController implements DisplayController.OnDisplaysChangedListener {
    private static final String TAG = "DisplayImeController";

    // NOTE: All these constants came from InsetsController.
    public static final int ANIMATION_DURATION_SHOW_MS = 275;
    public static final int ANIMATION_DURATION_HIDE_MS = 340;
    public static final Interpolator INTERPOLATOR = new PathInterpolator(0.4f, 0f, 0.2f, 1f);
    private static final int DIRECTION_NONE = 0;
    private static final int DIRECTION_SHOW = 1;
    private static final int DIRECTION_HIDE = 2;
    private static final int FLOATING_IME_BOTTOM_INSET = -80;

    protected final IWindowManager mWmService;
    protected final Executor mMainExecutor;
    private final TransactionPool mTransactionPool;
    private final DisplayController mDisplayController;
    private final DisplayInsetsController mDisplayInsetsController;
    private final SparseArray<PerDisplay> mImePerDisplay = new SparseArray<>();
    private final ArrayList<ImePositionProcessor> mPositionProcessors = new ArrayList<>();


    public DisplayImeController(IWindowManager wmService,
            ShellInit shellInit,
            DisplayController displayController,
            DisplayInsetsController displayInsetsController,
            TransactionPool transactionPool,
            Executor mainExecutor) {
        mWmService = wmService;
        mDisplayController = displayController;
        mDisplayInsetsController = displayInsetsController;
        mMainExecutor = mainExecutor;
        mTransactionPool = transactionPool;
        shellInit.addInitCallback(this::onInit, this);
    }

    /**
     * Starts monitor displays changes and set insets controller for each displays.
     */
    public void onInit() {
        mDisplayController.addDisplayWindowListener(this);
    }

    @Override
    public void onDisplayAdded(int displayId) {
        // Add's a system-ui window-manager specifically for ime. This type is special because
        // WM will defer IME inset handling to it in multi-window scenarious.
        PerDisplay pd = new PerDisplay(displayId,
                mDisplayController.getDisplayLayout(displayId).rotation());
        pd.register();
        mImePerDisplay.put(displayId, pd);
    }

    @Override
    public void onDisplayConfigurationChanged(int displayId, Configuration newConfig) {
        PerDisplay pd = mImePerDisplay.get(displayId);
        if (pd == null) {
            return;
        }
        if (mDisplayController.getDisplayLayout(displayId).rotation()
                != pd.mRotation && isImeShowing(displayId)) {
            pd.startAnimation(true, false /* forceRestart */,
                    SoftInputShowHideReason.DISPLAY_CONFIGURATION_CHANGED);
        }
    }

    @Override
    public void onDisplayRemoved(int displayId) {
        PerDisplay pd = mImePerDisplay.get(displayId);
        if (pd == null) {
            return;
        }
        pd.unregister();
        mImePerDisplay.remove(displayId);
    }

    private boolean isImeShowing(int displayId) {
        PerDisplay pd = mImePerDisplay.get(displayId);
        if (pd == null) {
            return false;
        }
        final InsetsSource imeSource = pd.mInsetsState.peekSource(InsetsSource.ID_IME);
        return imeSource != null && pd.mImeSourceControl != null && imeSource.isVisible();
    }

    private void dispatchPositionChanged(int displayId, int imeTop,
            SurfaceControl.Transaction t) {
        synchronized (mPositionProcessors) {
            for (ImePositionProcessor pp : mPositionProcessors) {
                pp.onImePositionChanged(displayId, imeTop, t);
            }
        }
    }

    private void dispatchImeRequested(int displayId, boolean isRequested) {
        synchronized (mPositionProcessors) {
            for (ImePositionProcessor pp : mPositionProcessors) {
                pp.onImeRequested(displayId, isRequested);
            }
        }
    }

    @ImePositionProcessor.ImeAnimationFlags
    private int dispatchStartPositioning(int displayId, int hiddenTop, int shownTop,
            boolean show, boolean isFloating, SurfaceControl.Transaction t) {
        synchronized (mPositionProcessors) {
            int flags = 0;
            for (ImePositionProcessor pp : mPositionProcessors) {
                flags |= pp.onImeStartPositioning(
                        displayId, hiddenTop, shownTop, show, isFloating, t);
            }
            return flags;
        }
    }

    private void dispatchEndPositioning(int displayId, boolean cancel,
            SurfaceControl.Transaction t) {
        synchronized (mPositionProcessors) {
            for (ImePositionProcessor pp : mPositionProcessors) {
                pp.onImeEndPositioning(displayId, cancel, t);
            }
        }
    }

    private void dispatchImeControlTargetChanged(int displayId, boolean controlling) {
        synchronized (mPositionProcessors) {
            for (ImePositionProcessor pp : mPositionProcessors) {
                pp.onImeControlTargetChanged(displayId, controlling);
            }
        }
    }

    private void dispatchVisibilityChanged(int displayId, boolean isShowing) {
        synchronized (mPositionProcessors) {
            for (ImePositionProcessor pp : mPositionProcessors) {
                pp.onImeVisibilityChanged(displayId, isShowing);
            }
        }
    }

    /**
     * Adds an {@link ImePositionProcessor} to be called during ime position updates.
     */
    public void addPositionProcessor(ImePositionProcessor processor) {
        synchronized (mPositionProcessors) {
            if (mPositionProcessors.contains(processor)) {
                return;
            }
            mPositionProcessors.add(processor);
        }
    }

    /**
     * Removes an {@link ImePositionProcessor} to be called during ime position updates.
     */
    public void removePositionProcessor(ImePositionProcessor processor) {
        synchronized (mPositionProcessors) {
            mPositionProcessors.remove(processor);
        }
    }

    /** Hides the IME for Bubbles when the device is locked. */
    public void hideImeForBubblesWhenLocked(int displayId) {
        PerDisplay pd = mImePerDisplay.get(displayId);
        InsetsSourceControl imeSourceControl = pd.getImeSourceControl();
        if (imeSourceControl != null) {
            final var statsToken = ImeTracker.forLogging().onStart(ImeTracker.TYPE_HIDE,
                    ImeTracker.ORIGIN_WM_SHELL,
                    SoftInputShowHideReason.HIDE_FOR_BUBBLES_WHEN_LOCKED, false /* fromUser */);
            pd.setImeInputTargetRequestedVisibility(false, statsToken);
        }
    }

    /** An implementation of {@link IDisplayWindowInsetsController} for a given display id. */
    public class PerDisplay implements DisplayInsetsController.OnInsetsChangedListener {
        final int mDisplayId;
        final InsetsState mInsetsState = new InsetsState();
        boolean mImeRequestedVisible =
                (WindowInsets.Type.defaultVisible() & WindowInsets.Type.ime()) != 0;
        InsetsSourceControl mImeSourceControl = null;
        int mAnimationDirection = DIRECTION_NONE;
        ValueAnimator mAnimation = null;
        int mRotation = Surface.ROTATION_0;
        boolean mImeShowing = false;
        final Rect mImeFrame = new Rect();
        boolean mAnimateAlpha = true;

        public PerDisplay(int displayId, int initialRotation) {
            mDisplayId = displayId;
            mRotation = initialRotation;
        }

        public void register() {
            mDisplayInsetsController.addInsetsChangedListener(mDisplayId, this);
        }

        public void unregister() {
            mDisplayInsetsController.removeInsetsChangedListener(mDisplayId, this);
        }

        @Override
        public void insetsChanged(InsetsState insetsState) {
            if (mInsetsState.equals(insetsState)) {
                return;
            }
            ProtoLog.d(WM_SHELL_IME_CONTROLLER, "Insets changed, state=%s", insetsState);

            final InsetsSource newSource = insetsState.peekSource(InsetsSource.ID_IME);
            final Rect newFrame = newSource != null ? newSource.getFrame() : null;
            final boolean newSourceVisible = newSource != null && newSource.isVisible();
            final InsetsSource oldSource = mInsetsState.peekSource(InsetsSource.ID_IME);
            final Rect oldFrame = oldSource != null ? oldSource.getFrame() : null;

            mInsetsState.set(insetsState, true /* copySources */);
            if (mImeShowing && !Objects.equals(oldFrame, newFrame) && newSourceVisible) {
                ProtoLog.d(WM_SHELL_IME_CONTROLLER,
                        "insetsChanged when IME showing, restart animation");
                startAnimation(mImeShowing, true /* forceRestart */,
                        SoftInputShowHideReason.DISPLAY_INSETS_CHANGED);
            }
        }

        @Override
        @VisibleForTesting
        public void insetsControlChanged(InsetsState insetsState,
                InsetsSourceControl[] activeControls) {
            ProtoLog.d(WM_SHELL_IME_CONTROLLER, "Insets control changed, state=%s controls=%s",
                    insetsState,
                    activeControls != null ? TextUtils.join(", ", activeControls) : "null");
            insetsChanged(insetsState);
            InsetsSourceControl imeSourceControl = null;
            if (activeControls != null) {
                for (InsetsSourceControl activeControl : activeControls) {
                    if (activeControl == null) {
                        continue;
                    }
                    if (activeControl.getType() == WindowInsets.Type.ime()) {
                        imeSourceControl = activeControl;
                    }
                }
            }

            final boolean hadImeSourceControl = mImeSourceControl != null;
            final boolean hasImeSourceControl = imeSourceControl != null;
            if (hadImeSourceControl != hasImeSourceControl) {
                dispatchImeControlTargetChanged(mDisplayId, hasImeSourceControl);
            }
            final boolean hasImeLeash = hasImeSourceControl && imeSourceControl.getLeash() != null;

            boolean pendingImeStartAnimation = false;
            boolean positionChanged = false;
            if (hasImeLeash) {
                final Point lastSurfacePosition = hadImeSourceControl
                        ? mImeSourceControl.getSurfacePosition() : null;
                positionChanged = !imeSourceControl.getSurfacePosition().equals(
                        lastSurfacePosition);
                if (mAnimation != null) {
                    if (positionChanged) {
                        // For showing the IME, the leash has to be available first. Hiding
                        // the IME happens directly via {@link #hideInsets} (triggered by
                        // setImeInputTargetRequestedVisibility) while the leash is not gone
                        // yet.
                        pendingImeStartAnimation = true;
                    }
                } else {
                    if (!haveSameLeash(mImeSourceControl, imeSourceControl)) {
                        pendingImeStartAnimation = true;
                        // The starting point for the IME should be it's previous state
                        // (whether it is initiallyVisible or not)
                        updateImeVisibility(imeSourceControl.isInitiallyVisible());
                        applyVisibilityToLeash(imeSourceControl);
                    }
                }
            } else if (mImeShowing && mAnimation == null) {
                // There is no leash, so the IME cannot be in a showing state
                updateImeVisibility(false);
            }

            // Make mImeSourceControl point to the new control before starting the animation.
            if (hadImeSourceControl && mImeSourceControl != imeSourceControl) {
                mImeSourceControl.release(SurfaceControl::release);
                if (!hasImeLeash && mAnimation != null) {
                    // In case of losing the leash, the animation should be cancelled.
                    mAnimation.cancel();
                }
            }
            mImeSourceControl = imeSourceControl;

            if (pendingImeStartAnimation) {
                startAnimation(mImeRequestedVisible, true /* forceRestart */);
            } else if (positionChanged) {
                // If the leash is the same, but it has changed its position while no
                // animation is ongoing, just update the position without starting a new
                // animation.
                SurfaceControl.Transaction t = mTransactionPool.acquire();
                final var position = mImeSourceControl.getSurfacePosition();
                t.setPosition(mImeSourceControl.getLeash(), position.x, position.y);
                t.apply();
                mTransactionPool.release(t);
            }
        }

        private void applyVisibilityToLeash(InsetsSourceControl imeSourceControl) {
            SurfaceControl leash = imeSourceControl.getLeash();
            if (leash != null) {
                SurfaceControl.Transaction t = mTransactionPool.acquire();
                if (mImeShowing) {
                    t.show(leash);
                } else {
                    t.hide(leash);
                }
                t.apply();
                mTransactionPool.release(t);
            }
        }

        @Override
        public void showInsets(@InsetsType int types, @Nullable ImeTracker.Token statsToken) {
            if ((types & WindowInsets.Type.ime()) == 0) {
                return;
            }
            ProtoLog.d(WM_SHELL_IME_CONTROLLER, "Ime shown, statsToken=%s",
                    statsToken != null ? statsToken.getBinder() : "null");
            startAnimation(true /* show */, false /* forceRestart */, statsToken);
        }

        @Override
        public void hideInsets(@InsetsType int types, @Nullable ImeTracker.Token statsToken) {
            if ((types & WindowInsets.Type.ime()) == 0) {
                return;
            }
            ProtoLog.d(WM_SHELL_IME_CONTROLLER, "Ime hidden, statsToken=%s",
                    statsToken != null ? statsToken.getBinder() : "null");
            startAnimation(false /* show */, false /* forceRestart */, statsToken);
        }

        @Override
        public void topFocusedWindowChanged(ComponentName component, int requestedVisibleTypes) {
            // Do nothing
        }

        @Override
        // TODO(b/335404678): pass control target
        public void setImeInputTargetRequestedVisibility(boolean visible,
                @NonNull ImeTracker.Token statsToken) {
            ProtoLog.d(WM_SHELL_IME_CONTROLLER,
                    "Input target requested visibility, visible=%b statsToken=%s",
                    visible, statsToken != null ? statsToken.getBinder() : "null");
            ImeTracker.forLogging().onProgress(statsToken,
                    ImeTracker.PHASE_WM_DISPLAY_IME_CONTROLLER_SET_IME_REQUESTED_VISIBLE);
            mImeRequestedVisible = visible;
            dispatchImeRequested(mDisplayId, mImeRequestedVisible);

            // In the case that the IME becomes visible, but we have the control with leash
            // already (e.g., when focussing an editText in activity B, while and editText in
            // activity A is focussed), we will not get a call of #insetsControlChanged, and
            // therefore have to start the show animation from here
            if (visible || mImeShowing) {
                // only start the animation if we're either already showing or becoming visible.
                // otherwise starting another hide animation causes flickers.
                startAnimation(mImeRequestedVisible /* show */, false /* forceRestart */,
                        statsToken);
            }

            boolean hideAnimOngoing;
            boolean reportVisible;
            if (android.view.inputmethod.Flags.reportAnimatingInsetsTypes()) {
                hideAnimOngoing = false;
                reportVisible = mImeRequestedVisible;
            } else {
                // In case of a hide, the statsToken should not been send yet (as the animation
                // is still ongoing). It will be sent at the end of the animation.
                hideAnimOngoing = !mImeRequestedVisible && mAnimation != null;
                reportVisible = mImeRequestedVisible || mAnimation != null;
            }
            setVisibleDirectly(reportVisible, hideAnimOngoing ? null : statsToken);
        }

        /**
         * Sends the local visibility state back to window manager. Needed for legacy adjustForIme.
         */
        private void setVisibleDirectly(boolean visible, @Nullable ImeTracker.Token statsToken) {
            mInsetsState.setSourceVisible(InsetsSource.ID_IME, visible);
            int visibleTypes = visible ? WindowInsets.Type.ime() : 0;
            try {
                mWmService.updateDisplayWindowRequestedVisibleTypes(mDisplayId,
                        visibleTypes, WindowInsets.Type.ime(), statsToken);
            } catch (RemoteException e) {
            }
        }

        private void setAnimating(boolean imeAnimationOngoing,
                @Nullable ImeTracker.Token statsToken) {
            int animatingTypes = imeAnimationOngoing ? WindowInsets.Type.ime() : 0;
            try {
                mWmService.updateDisplayWindowAnimatingTypes(mDisplayId, animatingTypes,
                        statsToken);
            } catch (RemoteException e) {
            }
        }

        private int imeTop(float surfaceOffset, float surfacePositionY) {
            // surfaceOffset is already offset by the surface's top inset, so we need to subtract
            // the top inset so that the return value is in screen coordinates.
            return mImeFrame.top + (int) (surfaceOffset - surfacePositionY);
        }

        private boolean calcIsFloating(InsetsSource imeSource) {
            final Rect frame = imeSource.getFrame();
            if (frame.height() == 0) {
                return true;
            }
            // Some Floating Input Methods will still report a frame, but the frame is actually
            // a nav-bar inset created by WM and not part of the IME (despite being reported as
            // an IME inset). For now, we assume that no non-floating IME will be <= this nav bar
            // frame height so any reported frame that is <= nav-bar frame height is assumed to
            // be floating.
            return frame.height() <= mDisplayController.getDisplayLayout(mDisplayId)
                    .navBarFrameHeight();
        }

        private void startAnimation(final boolean show, final boolean forceRestart) {
            final var imeSource = mInsetsState.peekSource(InsetsSource.ID_IME);
            if (imeSource == null || mImeSourceControl == null) {
                return;
            }
            // TODO(b/353463205): For hide: this still has the statsToken from the previous show
            //  request
            final var statsToken = mImeSourceControl.getImeStatsToken();

            startAnimation(show, forceRestart, statsToken);
        }

        private void startAnimation(final boolean show, final boolean forceRestart,
                @SoftInputShowHideReason int reason) {
            final var imeSource = mInsetsState.peekSource(InsetsSource.ID_IME);
            if (imeSource == null || mImeSourceControl == null) {
                return;
            }
            final ImeTracker.Token statsToken;
            if (mImeSourceControl.getImeStatsToken() != null) {
                statsToken = mImeSourceControl.getImeStatsToken();
            } else {
                statsToken = ImeTracker.forLogging().onStart(
                        show ? ImeTracker.TYPE_SHOW : ImeTracker.TYPE_HIDE,
                        ImeTracker.ORIGIN_WM_SHELL, reason, false /* fromUser */);
            }
            startAnimation(show, forceRestart, statsToken);
        }

        private void startAnimation(final boolean show, final boolean forceRestart,
                @NonNull final ImeTracker.Token statsToken) {
            if (mImeSourceControl == null || mImeSourceControl.getLeash() == null) {
                ProtoLog.d(WM_SHELL_IME_CONTROLLER, "No Ime leash for animation");
                return;
            }
            if (!mImeRequestedVisible && show) {
                // we have a control with leash, but the IME was not requested visible before,
                // therefore aborting the show animation.
                Slog.e(TAG, "IME was not requested visible, not starting the show animation.");
                // TODO(b/353463205) fail statsToken here
                return;
            }
            final InsetsSource imeSource = mInsetsState.peekSource(InsetsSource.ID_IME);
            if (imeSource == null) {
                ImeTracker.forLogging().onFailed(statsToken, ImeTracker.PHASE_WM_ANIMATION_CREATE);
                return;
            }
            final Rect newFrame = imeSource.getFrame();
            final boolean isFloating = calcIsFloating(imeSource) && show;
            if (isFloating) {
                // This is a "floating" or "expanded" IME, so to get animations, just
                // pretend the ime has some size just below the screen.
                mImeFrame.set(newFrame);
                final int floatingInset = (int) (mDisplayController.getDisplayLayout(mDisplayId)
                        .density() * FLOATING_IME_BOTTOM_INSET);
                mImeFrame.bottom -= floatingInset;
            } else if (newFrame.height() != 0) {
                // Don't set a new frame if it's empty and hiding -- this maintains continuity
                mImeFrame.set(newFrame);
            }
            final String prevVisibility = mAnimationDirection == DIRECTION_SHOW
                    ? "SHOW"
                    : mAnimationDirection == DIRECTION_HIDE
                            ? "HIDE"
                            : "NONE";
            ProtoLog.d(WM_SHELL_IME_CONTROLLER, "Run Ime animation, show=%b was=%s",
                    show, prevVisibility);
            if ((!forceRestart && (mAnimationDirection == DIRECTION_SHOW && show))
                    || (mAnimationDirection == DIRECTION_HIDE && !show)) {
                ImeTracker.forLogging().onCancelled(
                        statsToken, ImeTracker.PHASE_WM_ANIMATION_CREATE);
                return;
            }
            boolean seek = false;
            float seekValue = 0;
            if (mAnimation != null) {
                if (mAnimation.isRunning()) {
                    seekValue = mAnimationDirection == DIRECTION_SHOW && !show
                            // If we were showing previously (and now hiding), we need to use the
                            // inverse.
                            ? 1f - (float) mAnimation.getAnimatedValue()
                            : (float) mAnimation.getAnimatedValue();
                    seek = true;
                }
                mAnimation.cancel();
            }
            final InsetsSourceControl animatingControl = new InsetsSourceControl(mImeSourceControl);
            final SurfaceControl animatingLeash = animatingControl.getLeash();
            final float defaultY = animatingControl.getSurfacePosition().y;
            final float initialX = animatingControl.getSurfacePosition().x;
            final float hiddenY = defaultY + mImeFrame.height();
            final float shownY = defaultY;
            final float startY = show ? hiddenY : shownY;
            final float endY = show ? shownY : hiddenY;
            if (mAnimationDirection == DIRECTION_NONE && mImeShowing && show) {
                // IME is already showing, so set seek to end
                seekValue = 1f;
                seek = true;
            }
            mAnimationDirection = show ? DIRECTION_SHOW : DIRECTION_HIDE;
            updateImeVisibility(show);
            mAnimation = show
                    ? ValueAnimator.ofFloat(0f, 1f)
                    : ValueAnimator.ofFloat(1f, 0f);
            mAnimation.setDuration(
                    show ? ANIMATION_DURATION_SHOW_MS : ANIMATION_DURATION_HIDE_MS);
            if (seek) {
                mAnimation.setCurrentFraction(seekValue);
            } else {
                // In some cases the value in onAnimationStart is zero, therefore setting it
                // explicitly to startY
                mAnimation.setCurrentFraction(0);
            }

            mAnimation.addUpdateListener(animation -> {
                SurfaceControl.Transaction t = mTransactionPool.acquire();
                final float value = (float) animation.getAnimatedValue();
                final int x = mImeSourceControl.getSurfacePosition().x;
                final int initialY = mImeSourceControl.getSurfacePosition().y;
                final int y = (int) (initialY + (1f - value) * mImeFrame.height());
                t.setPosition(animatingLeash, x, y);
                final float alpha = (mAnimateAlpha || isFloating) ? value : 1f;
                t.setAlpha(animatingLeash, alpha);
                dispatchPositionChanged(mDisplayId, imeTop(y, initialY), t);
                t.apply();
                mTransactionPool.release(t);
            });
            mAnimation.setInterpolator(INTERPOLATOR);
            ImeTracker.forLogging().onProgress(statsToken, ImeTracker.PHASE_WM_ANIMATION_CREATE);
            mAnimation.addListener(new AnimatorListenerAdapter() {
                private boolean mCancelled = false;
                @NonNull
                private final ImeTracker.Token mStatsToken = statsToken;

                @Override
                public void onAnimationStart(Animator animation) {
                    ValueAnimator valueAnimator = (ValueAnimator) animation;
                    final float value = (float) valueAnimator.getAnimatedValue();
                    SurfaceControl.Transaction t = mTransactionPool.acquire();
                    t.setPosition(animatingLeash, initialX, startY);

                    ProtoLog.d(WM_SHELL_IME_CONTROLLER,
                            "Ime animation start, d=%d top=%d->%d showing=%b",
                            mDisplayId, imeTop(hiddenY, defaultY), imeTop(shownY, defaultY),
                            (mAnimationDirection == DIRECTION_SHOW));

                    if (android.view.inputmethod.Flags.reportAnimatingInsetsTypes()) {
                        // Updating the animatingTypes when starting the animation is not the
                        // trigger to show the IME. Thus, not sending the statsToken here.
                        setAnimating(true /* imeAnimationOngoing */, null /* statsToken */);
                    }
                    int flags = dispatchStartPositioning(mDisplayId, imeTop(hiddenY, defaultY),
                            imeTop(shownY, defaultY), mAnimationDirection == DIRECTION_SHOW,
                            isFloating, t);
                    mAnimateAlpha = (flags & ImePositionProcessor.IME_ANIMATION_NO_ALPHA) == 0;
                    final float alpha = (mAnimateAlpha || isFloating) ? value : 1f;
                    t.setAlpha(animatingLeash, alpha);
                    if (mAnimationDirection == DIRECTION_SHOW) {
                        ImeTracker.forLogging().onProgress(mStatsToken,
                                ImeTracker.PHASE_WM_ANIMATION_RUNNING);
                        t.show(animatingLeash);
                    }
                    if (DEBUG_IME_VISIBILITY) {
                        EventLog.writeEvent(IMF_IME_REMOTE_ANIM_START,
                                mStatsToken != null ? mStatsToken.getTag() : ImeTracker.TOKEN_NONE,
                                mDisplayId, mAnimationDirection, alpha, value, endY,
                                Objects.toString(animatingLeash),
                                Objects.toString(animatingControl.getInsetsHint()),
                                Objects.toString(animatingControl.getSurfacePosition()),
                                Objects.toString(mImeFrame));
                    }
                    t.apply();
                    mTransactionPool.release(t);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    mCancelled = true;
                    if (DEBUG_IME_VISIBILITY) {
                        EventLog.writeEvent(IMF_IME_REMOTE_ANIM_CANCEL,
                                mStatsToken != null ? mStatsToken.getTag() : ImeTracker.TOKEN_NONE,
                                mDisplayId,
                                Objects.toString(animatingControl.getInsetsHint()));
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    ProtoLog.d(WM_SHELL_IME_CONTROLLER, "Ime animation end, canceled=%b",
                            mCancelled);
                    SurfaceControl.Transaction t = mTransactionPool.acquire();
                    if (!mCancelled) {
                        final int x = mImeSourceControl.getSurfacePosition().x;
                        final int y = mImeSourceControl.getSurfacePosition().y
                                + (show ? 0 : mImeFrame.height());
                        t.setPosition(animatingLeash, x, y);
                        t.setAlpha(animatingLeash, 1f);
                    }
                    if (android.view.inputmethod.Flags.reportAnimatingInsetsTypes()) {
                        setAnimating(false /* imeAnimationOngoing */,
                                mAnimationDirection == DIRECTION_HIDE ? statsToken : null);
                    }
                    if (mAnimationDirection == DIRECTION_HIDE && !mCancelled) {
                        ImeTracker.forLogging().onProgress(mStatsToken,
                                ImeTracker.PHASE_WM_ANIMATION_RUNNING);
                        t.hide(animatingLeash);
                        // Updating the client visibility will not hide the IME, unless it is
                        // not animating anymore. Thus, not sending a statsToken here, but
                        // only later when we're updating the animatingTypes.
                        setVisibleDirectly(false /* visible */,
                                !android.view.inputmethod.Flags.reportAnimatingInsetsTypes()
                                        ? statsToken : null);
                    } else if (mAnimationDirection == DIRECTION_SHOW && !mCancelled) {
                        ImeTracker.forLogging().onShown(mStatsToken);
                    } else if (mCancelled) {
                        ImeTracker.forLogging().onCancelled(mStatsToken,
                                ImeTracker.PHASE_WM_ANIMATION_RUNNING);
                    }
                    // In split screen, we also set {@link
                    // WindowContainer#mExcludeInsetsTypes} but this should only happen after
                    // the IME client visibility was set. Otherwise the insets will we
                    // dispatched too early, and we get a flicker. Thus, only dispatching it
                    // after reporting that the IME is hidden to system server.
                    dispatchEndPositioning(mDisplayId, mCancelled, t);
                    if (DEBUG_IME_VISIBILITY) {
                        EventLog.writeEvent(IMF_IME_REMOTE_ANIM_END,
                                mStatsToken != null ? mStatsToken.getTag() : ImeTracker.TOKEN_NONE,
                                mDisplayId, mAnimationDirection, endY,
                                Objects.toString(animatingLeash),
                                Objects.toString(animatingControl.getInsetsHint()),
                                Objects.toString(animatingControl.getSurfacePosition()),
                                Objects.toString(mImeFrame));
                    }
                    t.apply();
                    mTransactionPool.release(t);

                    mAnimationDirection = DIRECTION_NONE;
                    mAnimation = null;
                    animatingControl.release(SurfaceControl::release);
                }
            });
            mAnimation.start();
        }

        private void updateImeVisibility(boolean isShowing) {
            if (mImeShowing != isShowing) {
                mImeShowing = isShowing;
                dispatchVisibilityChanged(mDisplayId, isShowing);
            }
        }

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        public InsetsSourceControl getImeSourceControl() {
            return mImeSourceControl;
        }
    }

    void removeImeSurface(int displayId) {
        // Remove the IME surface to make the insets invisible for
        // non-client controlled insets.
        InputMethodManagerGlobal.removeImeSurface(displayId,
                e -> Slog.e(TAG, "Failed to remove IME surface.", e));
    }

    /**
     * Allows other things to synchronize with the ime position
     */
    public interface ImePositionProcessor {

        /** Default animation flags. */
        int IME_ANIMATION_DEFAULT = 0;

        /**
         * Indicates that ime shouldn't animate alpha. It will always be opaque. Used when stuff
         * behind the IME shouldn't be visible (for example during split-screen adjustment where
         * there is nothing behind the ime).
         */
        int IME_ANIMATION_NO_ALPHA = 1;

        /** @hide */
        @IntDef(prefix = {"IME_ANIMATION_"}, value = {
                IME_ANIMATION_DEFAULT,
                IME_ANIMATION_NO_ALPHA,
        })
        @interface ImeAnimationFlags {
        }

        /**
         * Called when the IME was requested by an app
         *
         * @param isRequested {@code true} if the IME was requested to be visible
         */
        default void onImeRequested(int displayId, boolean isRequested) {
        }

        /**
         * Called when the IME position is starting to animate.
         *
         * @param hiddenTop  The y position of the top of the IME surface when it is hidden.
         * @param shownTop   The y position of the top of the IME surface when it is shown.
         * @param showing    {@code true} when we are animating from hidden to shown, {@code false}
         *                   when animating from shown to hidden.
         * @param isFloating {@code true} when the ime is a floating ime (doesn't inset).
         * @return flags that may alter how ime itself is animated (eg. no-alpha).
         */
        @ImeAnimationFlags
        default int onImeStartPositioning(int displayId, int hiddenTop, int shownTop,
                boolean showing, boolean isFloating, SurfaceControl.Transaction t) {
            return IME_ANIMATION_DEFAULT;
        }

        /**
         * Called when the ime position changed. This is expected to be a synchronous call on the
         * animation thread. Operations can be added to the transaction to be applied in sync.
         *
         * @param imeTop The current y position of the top of the IME surface.
         */
        default void onImePositionChanged(int displayId, int imeTop, SurfaceControl.Transaction t) {
        }

        /**
         * Called when the IME position is done animating.
         *
         * @param cancel {@code true} if this was cancelled. This implies another start is coming.
         */
        default void onImeEndPositioning(int displayId, boolean cancel,
                SurfaceControl.Transaction t) {
        }

        /**
         * Called when the IME control target changed. So that the processor can restore its
         * adjusted layout when the IME insets is not controlling by the current controller anymore.
         *
         * @param controlling indicates whether the current controller is controlling IME insets.
         */
        default void onImeControlTargetChanged(int displayId, boolean controlling) {
        }

        /**
         * Called when the IME visibility changed.
         *
         * @param isShowing {@code true} if the IME is shown.
         */
        default void onImeVisibilityChanged(int displayId, boolean isShowing) {

        }
    }

    private static boolean haveSameLeash(InsetsSourceControl a, InsetsSourceControl b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.getLeash() == b.getLeash()) {
            return true;
        }
        if (a.getLeash() == null || b.getLeash() == null) {
            return false;
        }
        return a.getLeash().isSameSurface(b.getLeash());
    }
}
