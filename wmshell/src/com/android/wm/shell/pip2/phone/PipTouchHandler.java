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

package com.android.wm.shell.pip2.phone;

import static android.view.WindowManager.INPUT_CONSUMER_PIP;

import static com.android.internal.config.sysui.SystemUiDeviceConfigFlags.PIP_STASHING;
import static com.android.internal.config.sysui.SystemUiDeviceConfigFlags.PIP_STASH_MINIMUM_VELOCITY_THRESHOLD;
import static com.android.wm.shell.common.pip.PipBoundsState.STASH_TYPE_LEFT;
import static com.android.wm.shell.common.pip.PipBoundsState.STASH_TYPE_NONE;
import static com.android.wm.shell.common.pip.PipBoundsState.STASH_TYPE_RIGHT;
import static com.android.wm.shell.pip2.phone.PhonePipMenuController.MENU_STATE_FULL;
import static com.android.wm.shell.pip2.phone.PhonePipMenuController.MENU_STATE_NONE;
import static com.android.wm.shell.pip2.phone.PipMenuView.ANIM_TYPE_NONE;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.Trace;
import android.provider.DeviceConfig;
import android.util.Size;
import android.view.DisplayCutout;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.WindowManagerGlobal;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.FloatingContentCoordinator;
import com.android.wm.shell.common.MultiDisplayDragMoveBoundsCalculator;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDesktopState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipDoubleTapHelper;
import com.android.wm.shell.common.pip.PipPerfHintController;
import com.android.wm.shell.common.pip.PipUiEventLogger;
import com.android.wm.shell.common.pip.SizeSpecSource;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.shared.pip.PipFlags;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellInit;

import java.io.PrintWriter;
import java.util.Optional;
import java.util.Random;

/**
 * Manages all the touch handling for PIP on the Phone, including moving, dismissing and expanding
 * the PIP.
 */
public class PipTouchHandler implements PipTransitionState.PipTransitionStateChangedListener,
        PipDisplayLayoutState.DisplayIdListener {

    private static final String TAG = "PipTouchHandler";
    private static final float DEFAULT_STASH_VELOCITY_THRESHOLD = 18000.f;
    private static final long PIP_KEEP_CLEAR_AREAS_DELAY =
            SystemProperties.getLong("persist.wm.debug.pip_keep_clear_areas_delay", 200);

    // Allow PIP to resize to a slightly bigger state upon touch
    private boolean mEnableResize;
    private Context mContext;
    private final ShellCommandHandler mShellCommandHandler;
    private final PipBoundsAlgorithm mPipBoundsAlgorithm;
    private final PipDesktopState mPipDesktopState;
    @NonNull private final PipBoundsState mPipBoundsState;
    @NonNull private final PipTransitionState mPipTransitionState;
    @NonNull private final PipScheduler mPipScheduler;
    @NonNull private final SizeSpecSource mSizeSpecSource;
    @NonNull private final PipDisplayLayoutState mPipDisplayLayoutState;
    private final PipUiEventLogger mPipUiEventLogger;
    private PipDismissTargetHandler mPipDismissTargetHandler;
    private final ShellExecutor mMainExecutor;
    @Nullable private final PipPerfHintController mPipPerfHintController;

    private PipResizeGestureHandler mPipResizeGestureHandler;
    private final PipDisplayTransferHandler mPipDisplayTransferHandler;
    private final PhonePipMenuController mMenuController;
    private final AccessibilityManager mAccessibilityManager;
    private final DisplayController mDisplayController;

    /**
     * Whether PIP stash is enabled or not. When enabled, if the user flings toward the edge of the
     * screen, it will be shown in "stashed" mode, where PIP will only show partially.
     */
    @VisibleForTesting boolean mEnableStash = true;

    private float mStashVelocityThreshold;

    // Used to workaround an issue where the WM rotation happens before we are notified, allowing
    // us to send stale bounds
    private int mDeferResizeToNormalBoundsUntilRotation = -1;
    private int mDisplayRotation;

    // Behaviour states
    private int mMenuState = MENU_STATE_NONE;
    private boolean mIsImeShowing;
    private int mImeHeight;
    private int mImeOffset;
    private boolean mIsShelfShowing;
    private int mShelfHeight;
    private int mMovementBoundsExtraOffsets;
    private int mBottomOffsetBufferPx;
    private float mSavedSnapFraction = -1f;
    private boolean mSendingHoverAccessibilityEvents;
    private boolean mMovementWithinDismiss;

    // Touch state
    private PipTouchState mTouchState;
    private final FloatingContentCoordinator mFloatingContentCoordinator;
    private PipMotionHelper mMotionHelper;
    private PipTouchGesture mGesture;
    private PipInputConsumer mPipInputConsumer;

    // Temp vars
    private final Rect mTmpBounds = new Rect();

    // Random int for tracking trace begin and end
    private int mUniqueCookie;

    // Callbacks
    private final Runnable mMoveOnShelVisibilityChanged;

    private final @NonNull PipSurfaceTransactionHelper mSurfaceTransactionHelper;

    /**
     * A listener for the PIP menu activity.
     */
    private class PipMenuListener implements PhonePipMenuController.Listener {
        @Override
        public void onPipMenuStateChangeStart(int menuState, boolean resize, Runnable callback) {
            PipTouchHandler.this.onPipMenuStateChangeStart(menuState, resize, callback);
        }

        @Override
        public void onPipMenuStateChangeFinish(int menuState) {
            setMenuState(menuState);
        }

        @Override
        public void onPipExpand() {
            mMotionHelper.expandLeavePip(false /* skipAnimation */);
        }

        @Override
        public void onPipDismiss() {
            mTouchState.removeDoubleTapTimeoutCallback();
            mMotionHelper.dismissPip();
        }

        @Override
        public void onPipShowMenu() {
            mMenuController.showMenu(MENU_STATE_FULL, mPipBoundsState.getBounds(),
                    true /* allowMenuTimeout */, willResizeMenu(), shouldShowResizeHandle());
        }
    }

    @SuppressLint("InflateParams")
    public PipTouchHandler(Context context,
            @NonNull PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            PhonePipMenuController menuController,
            PipBoundsAlgorithm pipBoundsAlgorithm,
            @NonNull PipBoundsState pipBoundsState,
            @NonNull PipTransitionState pipTransitionState,
            @NonNull PipScheduler pipScheduler,
            @NonNull SizeSpecSource sizeSpecSource,
            @NonNull PipDisplayLayoutState pipDisplayLayoutState,
            PipDesktopState pipDesktopState,
            DisplayController displayController,
            PipMotionHelper pipMotionHelper,
            FloatingContentCoordinator floatingContentCoordinator,
            PipUiEventLogger pipUiEventLogger,
            ShellExecutor mainExecutor,
            Optional<PipPerfHintController> pipPerfHintControllerOptional,
            PipDisplayTransferHandler pipDisplayTransferHandler) {
        mContext = context;
        mSurfaceTransactionHelper = pipSurfaceTransactionHelper;
        mShellCommandHandler = shellCommandHandler;
        mMainExecutor = mainExecutor;
        mPipPerfHintController = pipPerfHintControllerOptional.orElse(null);
        mAccessibilityManager = context.getSystemService(AccessibilityManager.class);
        mPipBoundsAlgorithm = pipBoundsAlgorithm;
        mPipBoundsState = pipBoundsState;
        mPipDesktopState = pipDesktopState;
        mDisplayController = displayController;

        mPipTransitionState = pipTransitionState;
        mPipTransitionState.addPipTransitionStateChangedListener(this::onPipTransitionStateChanged);
        mPipScheduler = pipScheduler;
        mSizeSpecSource = sizeSpecSource;
        mPipDisplayLayoutState = pipDisplayLayoutState;
        mPipDisplayLayoutState.addDisplayIdListener(this);
        mMenuController = menuController;
        mPipUiEventLogger = pipUiEventLogger;
        mFloatingContentCoordinator = floatingContentCoordinator;
        mMenuController.addListener(new PipMenuListener());
        mGesture = new DefaultPipTouchGesture();
        mMotionHelper = pipMotionHelper;
        mPipDisplayTransferHandler = pipDisplayTransferHandler;
        mPipScheduler.setUpdateMovementBoundsRunnable(this::updateMovementBounds);
        mPipDismissTargetHandler = new PipDismissTargetHandler(context, pipUiEventLogger,
                mMotionHelper, mPipDisplayLayoutState, mainExecutor);
        mTouchState = new PipTouchState(ViewConfiguration.get(context),
                () -> {
                    mMenuController.showMenuWithPossibleDelay(MENU_STATE_FULL,
                            mPipBoundsState.getBounds(), true /* allowMenuTimeout */,
                            willResizeMenu(),
                            shouldShowResizeHandle());
                },
                menuController::hideMenu,
                mainExecutor);
        mPipResizeGestureHandler = new PipResizeGestureHandler(context, mSurfaceTransactionHelper,
                pipBoundsAlgorithm,
                pipBoundsState, mTouchState, mPipScheduler, mPipTransitionState, pipUiEventLogger,
                menuController, this::getMovementBounds, mPipDisplayLayoutState, pipDesktopState,
                mainExecutor, mPipPerfHintController);
        mPipBoundsState.addOnAspectRatioChangedCallback(aspectRatio -> onAspectRatioChanged());

        mMoveOnShelVisibilityChanged = () -> {
            if (mIsImeShowing && mImeHeight > mShelfHeight) {
                // Early bail-out if IME is visible with a larger height present;
                // this should block unnecessary PiP movement since we delay checking for
                // KCA triggered movement to wait for other transitions (e.g. due to IME changes).
                return;
            }
            boolean hasUserInteracted = (mPipBoundsState.hasUserMovedPip()
                    || mPipBoundsState.hasUserResizedPip());
            int delta = mPipBoundsAlgorithm.getEntryDestinationBounds().top
                    - mPipBoundsState.getBounds().top;

            if (!mIsImeShowing && !hasUserInteracted && delta != 0) {
                // If the user hasn't interacted with PiP, we respect the keep clear areas
                mMotionHelper.animateToOffset(mPipBoundsState.getBounds(), delta);
            }
        };

        if (PipFlags.isPip2ExperimentEnabled()) {
            shellInit.addInitCallback(this::onInit, this);
        }

        mUniqueCookie = new Random().nextInt();
    }

    /**
     * Called when the touch handler is initialized.
     */
    public void onInit() {
        Resources res = mContext.getResources();
        mEnableResize = res.getBoolean(R.bool.config_pipEnableResizeForMenu);
        reloadResources();

        mShellCommandHandler.addDumpCallback(this::dump, this);
        mMotionHelper.init();
        mPipResizeGestureHandler.init();
        mPipDismissTargetHandler.init();

        mPipInputConsumer = new PipInputConsumer(WindowManagerGlobal.getWindowManagerService(),
                INPUT_CONSUMER_PIP, mPipDisplayLayoutState, mMainExecutor);
        mPipInputConsumer.setInputListener(this::handleTouchEvent);
        mPipInputConsumer.setRegistrationListener(this::onRegistrationChanged);

        mEnableStash = DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_SYSTEMUI,
                PIP_STASHING,
                /* defaultValue = */ true);
        DeviceConfig.addOnPropertiesChangedListener(DeviceConfig.NAMESPACE_SYSTEMUI,
                mMainExecutor,
                properties -> {
                    if (properties.getKeyset().contains(PIP_STASHING)) {
                        mEnableStash = properties.getBoolean(
                                PIP_STASHING, /* defaultValue = */ true);
                    }
                });
        mStashVelocityThreshold = DeviceConfig.getFloat(
                DeviceConfig.NAMESPACE_SYSTEMUI,
                PIP_STASH_MINIMUM_VELOCITY_THRESHOLD,
                DEFAULT_STASH_VELOCITY_THRESHOLD);
        DeviceConfig.addOnPropertiesChangedListener(DeviceConfig.NAMESPACE_SYSTEMUI,
                mMainExecutor,
                properties -> {
                    if (properties.getKeyset().contains(PIP_STASH_MINIMUM_VELOCITY_THRESHOLD)) {
                        mStashVelocityThreshold = properties.getFloat(
                                PIP_STASH_MINIMUM_VELOCITY_THRESHOLD,
                                DEFAULT_STASH_VELOCITY_THRESHOLD);
                    }
                });
    }

    private void reloadResources() {
        final Resources res = mContext.getResources();
        mBottomOffsetBufferPx = res.getDimensionPixelSize(R.dimen.pip_bottom_offset_buffer);
        mImeOffset = res.getDimensionPixelSize(R.dimen.pip_ime_offset);
        mPipDismissTargetHandler.updateMagneticTargetSize();
    }

    @Override
    public void onDisplayIdChanged(@NonNull Context context) {
        mContext = context;
        reloadResources();
    }

    void onOverlayChanged() {
        // onOverlayChanged is triggered upon theme change, update the dismiss target accordingly.
        mPipDismissTargetHandler.init();
    }

    private boolean shouldShowResizeHandle() {
        return false;
    }

    void setTouchGesture(PipTouchGesture gesture) {
        mGesture = gesture;
    }

    @VisibleForTesting PipTouchGesture getTouchGesture() {
        return mGesture;
    }

    void setTouchEnabled(boolean enabled) {
        mTouchState.setAllowTouches(enabled);
    }

    void showPictureInPictureMenu() {
        // Only show the menu if the user isn't currently interacting with the PiP
        if (!mTouchState.isUserInteracting()) {
            mMenuController.showMenu(MENU_STATE_FULL, mPipBoundsState.getBounds(),
                    false /* allowMenuTimeout */, willResizeMenu(),
                    shouldShowResizeHandle());
        }
    }

    void onActivityPinned() {
        mPipDismissTargetHandler.createOrUpdateDismissTarget();
        mPipResizeGestureHandler.onActivityPinned();
        mFloatingContentCoordinator.onContentAdded(mMotionHelper);
        mPipInputConsumer.registerInputConsumer();
    }

    void onActivityUnpinned() {
        // Clean up state after the last PiP activity is removed
        mPipDismissTargetHandler.cleanUpDismissTarget();
        mFloatingContentCoordinator.onContentRemoved(mMotionHelper);
        mPipResizeGestureHandler.onActivityUnpinned();
        mPipInputConsumer.unregisterInputConsumer();
        mPipBoundsState.setHasUserMovedPip(false);
        mPipBoundsState.setHasUserResizedPip(false);
    }

    void onConfigurationChanged() {
        mPipResizeGestureHandler.onConfigurationChanged();
        mMotionHelper.synchronizePinnedStackBounds();
        reloadResources();

        if (mPipTransitionState.isInPip()) {
            // Recreate the dismiss target for the new orientation.
            mPipDismissTargetHandler.createOrUpdateDismissTarget();
        }
    }

    void onImeVisibilityChanged(boolean imeVisible, int imeHeight) {
        mIsImeShowing = imeVisible;
        mImeHeight = imeHeight;

        // Cache new movement bounds using the new potential IME height.
        updateMovementBounds();
        mPipTransitionState.setOnIdlePipTransitionStateRunnable(() -> {
            if (imeVisible && mPipBoundsState.getMotionBoundsState().isInMotion()) {
                // Skip updating bounds now as it will be done after the animation settles
                return;
            }

            int delta = mPipBoundsState.getMovementBounds().bottom
                    - mPipBoundsState.getBounds().top;
            boolean hasUserInteracted = (mPipBoundsState.hasUserMovedPip()
                    || mPipBoundsState.hasUserResizedPip());

            if (!imeVisible && !hasUserInteracted) {
                delta = mPipBoundsAlgorithm.getEntryDestinationBounds().top
                        - mPipBoundsState.getBounds().top;
            }

            if ((imeVisible && delta < 0) || (!imeVisible && !hasUserInteracted)) {
                // The policy is to ignore an IME disappearing if user has interacted with PiP.
                // Otherwise, only offset due to an appearing IME if PiP occludes it.
                mMotionHelper.animateToOffset(mPipBoundsState.getBounds(), delta);
            }
        });
    }

    void onShelfVisibilityChanged(boolean shelfVisible, int shelfHeight) {
        mIsShelfShowing = shelfVisible;
        mShelfHeight = shelfHeight;

        // We need to remove the callback even if the shelf is visible, in case it the delayed
        // callback hasn't been executed yet to avoid the wrong final state.
        mMainExecutor.removeCallbacks(mMoveOnShelVisibilityChanged);

        // Postpone moving in response to hide of Launcher in case there's another change
        mMainExecutor.executeDelayed(mMoveOnShelVisibilityChanged, PIP_KEEP_CLEAR_AREAS_DELAY);
    }

    void adjustBoundsForRotation(Rect outBounds, Rect curBounds, Rect insetBounds) {
        final Rect toMovementBounds = new Rect();
        mPipBoundsAlgorithm.getMovementBounds(outBounds, insetBounds, toMovementBounds, 0);
        final int prevBottom = mPipBoundsState.getMovementBounds().bottom
                - mMovementBoundsExtraOffsets;
        if ((prevBottom - mBottomOffsetBufferPx) <= curBounds.top) {
            outBounds.offsetTo(outBounds.left, toMovementBounds.bottom);
        }
    }

    /**
     * Responds to IPinnedStackListener on resetting aspect ratio for the pinned window.
     */
    public void onAspectRatioChanged() {
        mPipResizeGestureHandler.invalidateUserResizeBounds();
    }

    void onMovementBoundsChanged(Rect insetBounds, Rect normalBounds, Rect curBounds,
            boolean fromImeAdjustment, boolean fromShelfAdjustment, int displayRotation) {
        // Set the user resized bounds equal to the new normal bounds in case they were
        // invalidated (e.g. by an aspect ratio change).
        if (mPipResizeGestureHandler.getUserResizeBounds().isEmpty()) {
            mPipResizeGestureHandler.setUserResizeBounds(normalBounds);
        }

        final int bottomOffset = mIsImeShowing ? mImeHeight : 0;
        final boolean fromDisplayRotationChanged = (mDisplayRotation != displayRotation);
        if (fromDisplayRotationChanged) {
            mTouchState.reset();
        }

        // Re-calculate the expanded bounds
        Rect normalMovementBounds = new Rect();
        mPipBoundsAlgorithm.getMovementBounds(normalBounds, insetBounds,
                normalMovementBounds, bottomOffset);

        if (mPipBoundsState.getMovementBounds().isEmpty()) {
            // mMovementBounds is not initialized yet and a clean movement bounds without
            // bottom offset shall be used later in this function.
            mPipBoundsAlgorithm.getMovementBounds(curBounds, insetBounds,
                    mPipBoundsState.getMovementBounds(), 0 /* bottomOffset */);
        }

        // Calculate the expanded size
        float aspectRatio = (float) normalBounds.width() / normalBounds.height();
        Size expandedSize = mSizeSpecSource.getDefaultSize(aspectRatio);
        mPipBoundsState.setExpandedBounds(
                new Rect(0, 0, expandedSize.getWidth(), expandedSize.getHeight()));
        Rect expandedMovementBounds = new Rect();
        mPipBoundsAlgorithm.getMovementBounds(
                mPipBoundsState.getExpandedBounds(), insetBounds, expandedMovementBounds,
                bottomOffset);

        // The extra offset does not really affect the movement bounds, but are applied based on the
        // current state (ime showing, or shelf offset) when we need to actually shift
        int extraOffset = Math.max(
                mIsImeShowing ? mImeOffset : 0,
                !mIsImeShowing && mIsShelfShowing ? mShelfHeight : 0);

        // Update the movement bounds after doing the calculations based on the old movement bounds
        // above
        mPipBoundsState.setNormalMovementBounds(normalMovementBounds);
        mPipBoundsState.setExpandedMovementBounds(expandedMovementBounds);
        mDisplayRotation = displayRotation;
        updateMovementBounds();
        mMovementBoundsExtraOffsets = extraOffset;

        // If we have a deferred resize, apply it now
        if (mDeferResizeToNormalBoundsUntilRotation == displayRotation) {
            mMotionHelper.animateToUnexpandedState(normalBounds, mSavedSnapFraction,
                    mPipBoundsState.getNormalMovementBounds(), mPipBoundsState.getMovementBounds(),
                    true /* immediate */);
            mSavedSnapFraction = -1f;
            mDeferResizeToNormalBoundsUntilRotation = -1;
        }
    }

    /**
     * TODO Add appropriate description
     */
    public void onRegistrationChanged(boolean isRegistered) {
        if (isRegistered) {
            // Register the accessibility connection.
        } else {
            mAccessibilityManager.setPictureInPictureActionReplacingConnection(null);
        }
        if (!isRegistered && mTouchState.isUserInteracting()) {
            // If the input consumer is unregistered while the user is interacting, then we may not
            // get the final TOUCH_UP event, so clean up the dismiss target as well
            mPipDismissTargetHandler.cleanUpDismissTarget();
        }
    }

    private void onAccessibilityShowMenu() {
        mMenuController.showMenu(MENU_STATE_FULL, mPipBoundsState.getBounds(),
                true /* allowMenuTimeout */, willResizeMenu(),
                shouldShowResizeHandle());
    }

    /**
     * TODO Add appropriate description
     */
    public boolean handleTouchEvent(InputEvent inputEvent) {
        // Skip any non motion events
        if (!(inputEvent instanceof MotionEvent)) {
            return true;
        }

        // do not process input event if not allowed
        if (!mTouchState.getAllowInputEvents()) {
            return true;
        }

        MotionEvent ev = (MotionEvent) inputEvent;
        if (!mPipBoundsState.isStashed() && mPipResizeGestureHandler.willStartResizeGesture(ev)) {
            // Initialize the touch state for the gesture, but immediately reset to invalidate the
            // gesture
            mTouchState.onTouchEvent(ev);
            mTouchState.reset();
            return true;
        }

        if (mPipResizeGestureHandler.hasOngoingGesture()) {
            mGesture.cleanUpHighPerfSessionMaybe();
            mPipDismissTargetHandler.hideDismissTargetMaybe();
            return true;
        }

        if ((ev.getAction() == MotionEvent.ACTION_DOWN || mTouchState.isUserInteracting())
                && isDismissTargetEnabled(ev)
                && mPipDismissTargetHandler.maybeConsumeMotionEvent(ev)) {
            // If the first touch event occurs within the magnetic field, pass the ACTION_DOWN event
            // to the touch state. Touch state needs a DOWN event in order to later process MOVE
            // events it'll receive if the object is dragged out of the magnetic field.
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                mTouchState.onTouchEvent(ev);
            }

            // Continue tracking velocity when the object is in the magnetic field, since we want to
            // respect touch input velocity if the object is dragged out and then flung.
            mTouchState.addMovementToVelocityTracker(ev);

            return true;
        }

        /*
        // Ignore the motion event When the entry animation is waiting to be started
        if (!mTouchState.isUserInteracting() && mPipTaskOrganizer.isEntryScheduled()) {
            ProtoLog.wtf(WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Waiting to start the entry animation, skip the motion event.", TAG);
            return true;
        }
         */

        // Update the touch state
        mTouchState.onTouchEvent(ev);

        boolean shouldDeliverToMenu = mMenuState != MENU_STATE_NONE;

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                Trace.beginAsyncSection("PipTouchHandler#onDown", mUniqueCookie);
                mGesture.onDown(mTouchState);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mGesture.onMove(mTouchState)) {
                    break;
                }

                shouldDeliverToMenu = !mTouchState.isDragging();
                break;
            }
            case MotionEvent.ACTION_UP: {
                // Update the movement bounds again if the state has changed since the user started
                // dragging (ie. when the IME shows)
                updateMovementBounds();
                Trace.endAsyncSection("PipTouchHandler#onDown", mUniqueCookie);
                if (mGesture.onUp(mTouchState)) {
                    break;
                }
            }
            // Fall through to clean up
            case MotionEvent.ACTION_CANCEL: {
                if (mPipDesktopState.isDraggingPipAcrossDisplaysEnabled()) {
                    mPipDisplayTransferHandler.removeMirrors();
                }
                shouldDeliverToMenu = !mTouchState.startedDragging() && !mTouchState.isDragging();
                mTouchState.reset();
                mPipDismissTargetHandler.cleanUpDismissTarget();
                break;
            }
            case MotionEvent.ACTION_HOVER_ENTER: {
                // If Touch Exploration is enabled, some a11y services (e.g. Talkback) is probably
                // on and changing MotionEvents into HoverEvents.
                // Let's not enable menu show/hide for a11y services.
                if (!mAccessibilityManager.isTouchExplorationEnabled()) {
                    mTouchState.removeHoverExitTimeoutCallback();
                    mMenuController.showMenu(MENU_STATE_FULL, mPipBoundsState.getBounds(),
                            false /* allowMenuTimeout */, false /* willResizeMenu */,
                            shouldShowResizeHandle());
                }
            }
            // Fall through
            case MotionEvent.ACTION_HOVER_MOVE: {
                if (!shouldDeliverToMenu && !mSendingHoverAccessibilityEvents) {
                    sendAccessibilityHoverEvent(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
                    mSendingHoverAccessibilityEvents = true;
                }
                break;
            }
            case MotionEvent.ACTION_HOVER_EXIT: {
                // If Touch Exploration is enabled, some a11y services (e.g. Talkback) is probably
                // on and changing MotionEvents into HoverEvents.
                // Let's not enable menu show/hide for a11y services.
                if (!mAccessibilityManager.isTouchExplorationEnabled()) {
                    mTouchState.scheduleHoverExitTimeoutCallback();
                }
                if (!shouldDeliverToMenu && mSendingHoverAccessibilityEvents) {
                    sendAccessibilityHoverEvent(AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
                    mSendingHoverAccessibilityEvents = false;
                }
                break;
            }
        }

        shouldDeliverToMenu &= !mPipBoundsState.isStashed();

        // Deliver the event to PipMenuActivity to handle button click if the menu has shown.
        if (shouldDeliverToMenu) {
            final MotionEvent cloneEvent = MotionEvent.obtain(ev);
            // Send the cancel event and cancel menu timeout if it starts to drag.
            if (mTouchState.startedDragging()) {
                cloneEvent.setAction(MotionEvent.ACTION_CANCEL);
                mMenuController.pokeMenu();
            }

            mMenuController.handlePointerEvent(cloneEvent);
            cloneEvent.recycle();
        }

        return true;
    }

    private void sendAccessibilityHoverEvent(int type) {
        if (!mAccessibilityManager.isEnabled()) {
            return;
        }

        AccessibilityEvent event = AccessibilityEvent.obtain(type);
        event.setImportantForAccessibility(true);
        event.setSourceNodeId(AccessibilityNodeInfo.ROOT_NODE_ID);
        event.setWindowId(
                AccessibilityWindowInfo.PICTURE_IN_PICTURE_ACTION_REPLACER_WINDOW_ID);
        mAccessibilityManager.sendAccessibilityEvent(event);
    }

    /**
     * Called when the PiP menu state is in the process of animating/changing from one to another.
     */
    private void onPipMenuStateChangeStart(int menuState, boolean resize, Runnable callback) {
        if (mMenuState == menuState && !resize) {
            return;
        }

        if (menuState == MENU_STATE_FULL && mMenuState != MENU_STATE_FULL) {
            // Save the current snap fraction and if we do not drag or move the PiP, then
            // we store back to this snap fraction.  Otherwise, we'll reset the snap
            // fraction and snap to the closest edge.
            if (resize) {
                // PIP is too small to show the menu actions and thus needs to be resized to a
                // size that can fit them all. Resize to the default size.
                animateToNormalSize(callback);
            }
        } else if (menuState == MENU_STATE_NONE && mMenuState == MENU_STATE_FULL) {
            // Try and restore the PiP to the closest edge, using the saved snap fraction
            // if possible
            if (resize && !mPipResizeGestureHandler.isResizing()) {
                if (mDeferResizeToNormalBoundsUntilRotation == -1) {
                    // This is a very special case: when the menu is expanded and visible,
                    // navigating to another activity can trigger auto-enter PiP, and if the
                    // revealed activity has a forced rotation set, then the controller will get
                    // updated with the new rotation of the display. However, at the same time,
                    // SystemUI will try to hide the menu by creating an animation to the normal
                    // bounds which are now stale.  In such a case we defer the animation to the
                    // normal bounds until after the next onMovementBoundsChanged() call to get the
                    // bounds in the new orientation
                    int displayRotation = mContext.getDisplay().getRotation();
                    if (mDisplayRotation != displayRotation) {
                        mDeferResizeToNormalBoundsUntilRotation = displayRotation;
                    }
                }

                if (mDeferResizeToNormalBoundsUntilRotation == -1) {
                    animateToUnexpandedState(getUserResizeBounds());
                }
            } else {
                mSavedSnapFraction = -1f;
            }
        }
    }

    private void setMenuState(int menuState) {
        mMenuState = menuState;
        updateMovementBounds();
        // If pip menu has dismissed, we should register the A11y ActionReplacingConnection for pip
        // as well, or it can't handle a11y focus and pip menu can't perform any action.
        onRegistrationChanged(menuState == MENU_STATE_NONE);
        if (menuState == MENU_STATE_NONE) {
            mPipUiEventLogger.log(PipUiEventLogger.PipUiEventEnum.PICTURE_IN_PICTURE_HIDE_MENU);
        } else if (menuState == MENU_STATE_FULL) {
            mPipUiEventLogger.log(PipUiEventLogger.PipUiEventEnum.PICTURE_IN_PICTURE_SHOW_MENU);
        }
    }

    private void animateToMaximizedState() {
        Rect maxBounds = new Rect(0, 0, mPipBoundsState.getMaxSize().x,
                mPipBoundsState.getMaxSize().y);

        mSavedSnapFraction = mMotionHelper.animateToExpandedState(maxBounds,
                getMovementBounds(mPipBoundsState.getBounds()),
                getMovementBounds(maxBounds), null /* callback */);
    }

    private void animateToNormalSize(Runnable callback) {
        // Save the current bounds as the user-resize bounds.
        mPipResizeGestureHandler.setUserResizeBounds(mPipBoundsState.getBounds());
        final Rect adjustedNormalBounds = getAdjustedNormalBounds();
        mSavedSnapFraction = mMotionHelper.animateToExpandedState(adjustedNormalBounds,
                getMovementBounds(mPipBoundsState.getBounds()),
                getMovementBounds(adjustedNormalBounds), callback /* callback */);
    }

    private Rect getAdjustedNormalBounds() {
        final Size minMenuSize = mMenuController.getEstimatedMinMenuSize();
        final Size defaultSize = mSizeSpecSource.getDefaultSize(mPipBoundsState.getAspectRatio());
        final Rect normalBounds = new Rect(0, 0, defaultSize.getWidth(), defaultSize.getHeight());
        return mPipBoundsAlgorithm.adjustNormalBoundsToFitMenu(normalBounds, minMenuSize);
    }

    private void animateToUnexpandedState(Rect restoreBounds) {
        mMotionHelper.animateToUnexpandedState(restoreBounds, mSavedSnapFraction,
                getMovementBounds(restoreBounds),
                getMovementBounds(mPipBoundsState.getBounds()), false /* immediate */);
        mSavedSnapFraction = -1f;
    }

    private void animateToUnStashedState() {
        final Rect pipBounds = mPipBoundsState.getBounds();
        final boolean onLeftEdge = pipBounds.left < mPipBoundsState.getDisplayBounds().left;
        final Rect unStashedBounds = new Rect(0, pipBounds.top, 0, pipBounds.bottom);

        Rect insetBounds = new Rect();
        mPipBoundsAlgorithm.getInsetBounds(insetBounds);
        unStashedBounds.left = onLeftEdge ? insetBounds.left
                : insetBounds.right - pipBounds.width();
        unStashedBounds.right = onLeftEdge ? insetBounds.left + pipBounds.width()
                : insetBounds.right;
        mMotionHelper.animateToUnStashedBounds(unStashedBounds);
    }

    /**
     * @return the motion helper.
     */
    public PipMotionHelper getMotionHelper() {
        return mMotionHelper;
    }

    @VisibleForTesting PipResizeGestureHandler getPipResizeGestureHandler() {
        return mPipResizeGestureHandler;
    }

    @VisibleForTesting
    void setPipResizeGestureHandler(PipResizeGestureHandler pipResizeGestureHandler) {
        mPipResizeGestureHandler = pipResizeGestureHandler;
    }

    @VisibleForTesting PipDismissTargetHandler getPipDismissTargetHandler() {
        return mPipDismissTargetHandler;
    }

    @VisibleForTesting
    void setPipDismissTargetHandler(PipDismissTargetHandler pipDismissTargetHandler) {
        mPipDismissTargetHandler = pipDismissTargetHandler;
    }

    @VisibleForTesting void setPipMotionHelper(PipMotionHelper pipMotionHelper) {
        mMotionHelper = pipMotionHelper;
    }

    @VisibleForTesting void setPipTouchState(PipTouchState pipTouchState) {
        mTouchState = pipTouchState;
    }

    Rect getUserResizeBounds() {
        return mPipResizeGestureHandler.getUserResizeBounds();
    }

    /**
     * Sets the user resize bounds tracked by {@link PipResizeGestureHandler}
     */
    void setUserResizeBounds(Rect bounds) {
        mPipResizeGestureHandler.setUserResizeBounds(bounds);
    }

    /**
     * Gesture controlling normal movement of the PIP.
     */
    private class DefaultPipTouchGesture extends PipTouchGesture {
        private final PointF mDelta = new PointF();
        private final PointF mPointerPositionOnDown = new PointF();
        private int mDisplayIdOnDown;
        private boolean mShouldHideMenuAfterFling;
        private final Rect mStartBounds = new Rect();

        @Nullable private PipPerfHintController.PipHighPerfSession mPipHighPerfSession;

        private void onHighPerfSessionTimeout(PipPerfHintController.PipHighPerfSession session) {}

        @Override
        public void cleanUpHighPerfSessionMaybe() {
            if (mPipHighPerfSession != null) {
                // Close the high perf session once pointer interactions are over;
                mPipHighPerfSession.close();
                mPipHighPerfSession = null;
            }
        }

        @Override
        public void onDown(PipTouchState touchState) {
            if (!touchState.isUserInteracting()) {
                return;
            }

            if (mPipPerfHintController != null) {
                // Cache the PiP high perf session to close it upon touch up.
                mPipHighPerfSession = mPipPerfHintController.startSession(
                        this::onHighPerfSessionTimeout, "DefaultPipTouchGesture#onDown");
            }

            Rect bounds = getPossiblyMotionBounds();
            mDelta.set(0f, 0f);
            mStartBounds.set(bounds);
            final PointF touchPosition = touchState.getDownTouchPosition();
            mPointerPositionOnDown.set(touchPosition.x, touchPosition.y);
            mMovementWithinDismiss = touchPosition.y >= mPipBoundsState.getMovementBounds().bottom;
            mMotionHelper.setSpringingToTouch(false);
            mPipDismissTargetHandler.setTaskLeash(mPipTransitionState.getPinnedTaskLeash());
            mDisplayIdOnDown = touchState.getLastTouchDisplayId();
            // If the menu is still visible then just poke the menu
            // so that it will timeout after the user stops touching it
            if (mMenuState != MENU_STATE_NONE && !mPipBoundsState.isStashed()) {
                mMenuController.pokeMenu();
            }
        }

        @Override
        public boolean onMove(PipTouchState touchState) {
            if (!touchState.isUserInteracting()) {
                return false;
            }

            if (touchState.startedDragging()) {
                mSavedSnapFraction = -1f;
                if (isDismissTargetEnabled(touchState.getLatestMotionEvent())) {
                    mPipDismissTargetHandler.showDismissTargetMaybe();
                }
            }

            if (touchState.isDragging()) {
                mPipBoundsState.setHasUserMovedPip(true);
                final PointF curPos = touchState.getLastTouchPosition();

                if (mPipDesktopState.isDraggingPipAcrossDisplaysEnabled()) {
                    DisplayLayout currentDisplayLayout = mDisplayController.getDisplayLayout(
                            touchState.getLastTouchDisplayId());
                    DisplayLayout displayLayoutOnDown = mDisplayController.getDisplayLayout(
                            mDisplayIdOnDown);

                    if (displayLayoutOnDown == null || currentDisplayLayout == null) {
                        ProtoLog.w(WM_SHELL_PICTURE_IN_PICTURE,
                                "%s: Failed to show drag mirror on connected displays because "
                                        + "displayLayout is null", TAG);
                        return false;
                    }

                    RectF globalDpPipBounds =
                            MultiDisplayDragMoveBoundsCalculator.calculateGlobalDpBoundsForDrag(
                                    displayLayoutOnDown, mPointerPositionOnDown, mStartBounds,
                                    currentDisplayLayout, curPos.x, curPos.y);

                    // Create mirrors on connected displays to simulate dragging PiP across displays
                    mPipDisplayTransferHandler.showDragMirrorOnConnectedDisplays(globalDpPipBounds,
                            touchState.getLastTouchDisplayId());
                    // Set PiP bounds on the focused display in display topology-aware local px
                    mTmpBounds.set(
                            MultiDisplayDragMoveBoundsCalculator.convertGlobalDpToLocalPxForRect(
                                    globalDpPipBounds, currentDisplayLayout));
                } else {
                    // Move the pinned stack freely
                    final PointF lastDelta = touchState.getLastTouchDelta();
                    float lastX = mStartBounds.left + mDelta.x;
                    float lastY = mStartBounds.top + mDelta.y;
                    float left = lastX + lastDelta.x;
                    float top = lastY + lastDelta.y;

                    // Add to the cumulative delta after bounding the position
                    mDelta.x += left - lastX;
                    mDelta.y += top - lastY;

                    mTmpBounds.set(getPossiblyMotionBounds());
                    mTmpBounds.offsetTo((int) left, (int) top);
                }

                mMotionHelper.movePip(mTmpBounds, true /* isDragging */,
                        touchState.getLastTouchDisplayId());

                if (mMovementWithinDismiss) {
                    // Track if movement remains near the bottom edge to identify swipe to dismiss
                    mMovementWithinDismiss = curPos.y >= mPipBoundsState.getMovementBounds().bottom;
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean onUp(PipTouchState touchState) {
            mPipDismissTargetHandler.hideDismissTargetMaybe();
            mPipDismissTargetHandler.setTaskLeash(null);
            if (mPipDesktopState.isDraggingPipAcrossDisplaysEnabled()) {
                mPipDisplayTransferHandler.removeMirrors();
            }

            if (!touchState.isUserInteracting()) {
                return false;
            }

            final PointF vel = touchState.getVelocity();
            int displayIdOnUp = touchState.getLastTouchDisplayId();

            if (touchState.isDragging()) {
                if (mMenuState != MENU_STATE_NONE) {
                    // If the menu is still visible, then just poke the menu so that
                    // it will timeout after the user stops touching it
                    mMenuController.showMenu(mMenuState, mPipBoundsState.getBounds(),
                            true /* allowMenuTimeout */, willResizeMenu(),
                            shouldShowResizeHandle());
                }
                mShouldHideMenuAfterFling = mMenuState == MENU_STATE_NONE;

                // Reset the touch state on up before the fling settles
                mTouchState.reset();
                if (mEnableStash && shouldStash(vel, getPossiblyMotionBounds(), displayIdOnUp)) {
                    mMotionHelper.stashToEdge(vel.x, vel.y, null /* endAction */);
                } else {
                    if (mPipBoundsState.isStashed()) {
                        // Reset stashed state if previously stashed
                        mPipUiEventLogger.log(
                                PipUiEventLogger.PipUiEventEnum.PICTURE_IN_PICTURE_STASH_UNSTASHED);
                        mPipBoundsState.setStashed(STASH_TYPE_NONE);
                    }

                    if (mPipDesktopState.isDraggingPipAcrossDisplaysEnabled()
                            && mDisplayIdOnDown != displayIdOnUp) {
                        mPipDisplayTransferHandler.scheduleMovePipToDisplay(mDisplayIdOnDown,
                                displayIdOnUp, getPossiblyMotionBounds());
                    } else {
                        mMotionHelper.flingToSnapTarget(vel.x, vel.y,
                                this::flingEndAction /* endAction */);
                    }
                }
            } else if (mTouchState.isDoubleTap() && !mPipBoundsState.isStashed()
                    && mMenuState != MENU_STATE_FULL) {
                // If using pinch to zoom, double-tap functions as resizing between max/min size
                if (mPipResizeGestureHandler.isUsingPinchToZoom()) {
                    if (mMenuController.isMenuVisible()) {
                        mMenuController.hideMenu(ANIM_TYPE_NONE, false /* resize */);
                    }

                    // the size to toggle to after a double tap
                    mPipBoundsState.setNormalBounds(getAdjustedNormalBounds());
                    int nextSize = PipDoubleTapHelper
                            .nextSizeSpec(mPipBoundsState, getUserResizeBounds());

                    // actually toggle to the size chosen
                    if (nextSize == PipDoubleTapHelper.SIZE_SPEC_MAX) {
                        mPipResizeGestureHandler.setUserResizeBounds(mPipBoundsState.getBounds());
                        animateToMaximizedState();
                    } else if (nextSize == PipDoubleTapHelper.SIZE_SPEC_DEFAULT) {
                        mPipResizeGestureHandler.setUserResizeBounds(mPipBoundsState.getBounds());
                        animateToNormalSize(null);
                    } else {
                        animateToUnexpandedState(getUserResizeBounds());
                    }
                    mPipBoundsState.setHasUserResizedPip(true);
                } else {
                    // Expand to fullscreen if this is a double tap
                    // the PiP should be frozen until the transition ends
                    setTouchEnabled(false);
                    mMotionHelper.expandLeavePip(false /* skipAnimation */);
                }
            } else if (mMenuState != MENU_STATE_FULL) {
                if (mPipBoundsState.isStashed()) {
                    // Unstash immediately if stashed, and don't wait for the double tap timeout
                    animateToUnStashedState();
                    mPipUiEventLogger.log(
                            PipUiEventLogger.PipUiEventEnum.PICTURE_IN_PICTURE_STASH_UNSTASHED);
                    mPipBoundsState.setStashed(STASH_TYPE_NONE);
                    mTouchState.removeDoubleTapTimeoutCallback();
                } else if (!mTouchState.isWaitingForDoubleTap()) {
                    // User has stalled long enough for this not to be a drag or a double tap,
                    // just expand the menu
                    mMenuController.showMenu(MENU_STATE_FULL, mPipBoundsState.getBounds(),
                            true /* allowMenuTimeout */, willResizeMenu(),
                            shouldShowResizeHandle());
                } else {
                    // Next touch event _may_ be the second tap for the double-tap, schedule a
                    // fallback runnable to trigger the menu if no touch event occurs before the
                    // next tap
                    mTouchState.scheduleDoubleTapTimeoutCallback();
                }
            }
            cleanUpHighPerfSessionMaybe();
            return true;
        }

        private void flingEndAction() {
            if (mShouldHideMenuAfterFling) {
                // If the menu is not visible, then we can still be showing the activity for the
                // dismiss overlay, so just finish it after the animation completes
                mMenuController.hideMenu();
            }
        }

        private boolean shouldStash(PointF vel, Rect motionBounds, int displayIdOnUp) {
            final boolean flingToLeft = vel.x < -mStashVelocityThreshold;
            final boolean flingToRight = vel.x > mStashVelocityThreshold;
            final int offset = motionBounds.width() / 2;
            final boolean droppingOnLeft =
                    motionBounds.left < mPipBoundsState.getDisplayBounds().left - offset;
            final boolean droppingOnRight =
                    motionBounds.right > mPipBoundsState.getDisplayBounds().right + offset;

            // Do not allow stash if the destination edge contains display cutout. We only
            // compare the left and right edges since we do not allow stash on top / bottom.
            final DisplayCutout displayCutout =
                    mPipBoundsState.getDisplayLayout().getDisplayCutout();
            if (displayCutout != null) {
                if ((flingToLeft || droppingOnLeft)
                        && !displayCutout.getBoundingRectLeft().isEmpty()) {
                    return false;
                } else if ((flingToRight || droppingOnRight)
                        && !displayCutout.getBoundingRectRight().isEmpty()) {
                    return false;
                }
            }

            // If user flings the PIP window above the minimum velocity, stash PIP.
            // Only allow stashing to the edge if PIP wasn't previously stashed on the opposite
            // edge.
            final boolean stashFromFlingToEdge =
                    (flingToLeft && mPipBoundsState.getStashedState() != STASH_TYPE_RIGHT)
                    || (flingToRight && mPipBoundsState.getStashedState() != STASH_TYPE_LEFT);

            // If User releases the PIP window while it's out of the display bounds, put
            // PIP into stashed mode.
            final boolean stashFromDroppingOnEdge = droppingOnLeft || droppingOnRight;

            // If dragging PiP across displays is allowed, then ensure that stashing only occurs
            // when no drag mirrors of the window are shown, and the display ID on down and up are
            // the same, meaning that we don't allow stashing while moving PiP across displays
            return (stashFromFlingToEdge || stashFromDroppingOnEdge)
                    && !mPipDisplayTransferHandler.isMirrorShown()
                    && mDisplayIdOnDown == displayIdOnUp;
        }
    }

    private boolean isDismissTargetEnabled(MotionEvent ev) {
        // Only allow dismiss target to be shown and enabled on touch screen and stylus input
        return !mPipDesktopState.isDraggingPipAcrossDisplaysEnabled()
                || ev.getSource() == InputDevice.SOURCE_TOUCHSCREEN
                || ev.getSource() == InputDevice.SOURCE_STYLUS;
    }

    /**
     * Updates the current movement bounds based on whether the menu is currently visible and
     * resized.
     */
    void updateMovementBounds() {
        Rect insetBounds = new Rect();
        mPipBoundsState.setImeVisibility(mIsImeShowing, mIsImeShowing ? mImeHeight : 0);
        mPipBoundsAlgorithm.getInsetBounds(insetBounds);
        mPipBoundsAlgorithm.getMovementBounds(mPipBoundsState.getBounds(),
                insetBounds, mPipBoundsState.getMovementBounds(), mIsImeShowing ? mImeHeight : 0);
        mMotionHelper.onMovementBoundsChanged();

        if (mPipResizeGestureHandler.getUserResizeBounds().isEmpty()) {
            mPipResizeGestureHandler.setUserResizeBounds(getAdjustedNormalBounds());
        }
    }

    private Rect getMovementBounds(Rect curBounds) {
        Rect movementBounds = new Rect();
        Rect insetBounds = new Rect();
        mPipBoundsAlgorithm.getInsetBounds(insetBounds);
        mPipBoundsAlgorithm.getMovementBounds(curBounds, insetBounds,
                movementBounds, mIsImeShowing ? mImeHeight : 0);
        return movementBounds;
    }

    /**
     * @return {@code true} if the menu should be resized on tap because app explicitly specifies
     * PiP window size that is too small to hold all the actions.
     */
    private boolean willResizeMenu() {
        if (!mEnableResize) {
            return false;
        }
        final Size estimatedMinMenuSize = mMenuController.getEstimatedMinMenuSize();
        if (estimatedMinMenuSize == null) {
            ProtoLog.wtf(WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Failed to get estimated menu size", TAG);
            return false;
        }
        final Rect currentBounds = mPipBoundsState.getBounds();
        return currentBounds.width() < estimatedMinMenuSize.getWidth()
                || currentBounds.height() < estimatedMinMenuSize.getHeight();
    }

    /**
     * Returns the PIP bounds if we're not in the middle of a motion operation, or the current,
     * temporary motion bounds otherwise.
     */
    Rect getPossiblyMotionBounds() {
        return mPipBoundsState.getMotionBoundsState().isInMotion()
                ? mPipBoundsState.getMotionBoundsState().getBoundsInMotion()
                : mPipBoundsState.getBounds();
    }

    void setOhmOffset(int offset) {
        mPipResizeGestureHandler.setOhmOffset(offset);
    }

    @Override
    public void onPipTransitionStateChanged(@PipTransitionState.TransitionState int oldState,
            @PipTransitionState.TransitionState int newState,
            @Nullable Bundle extra) {
        switch (newState) {
            case PipTransitionState.ENTERED_PIP:
                onActivityPinned();
                updateMovementBounds();
                mTouchState.setAllowInputEvents(true);
                mTouchState.reset();
                break;
            case PipTransitionState.EXITED_PIP:
                mTouchState.setAllowInputEvents(false);
                onActivityUnpinned();
                break;
            case PipTransitionState.SCHEDULED_BOUNDS_CHANGE:
                mTouchState.setAllowInputEvents(false);
                break;
            case PipTransitionState.CHANGED_PIP_BOUNDS:
                mTouchState.setAllowInputEvents(true);
                mTouchState.reset();
                break;
        }
    }

    /**
     * Dumps the {@link PipTouchHandler} state.
     */
    public void dump(PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        pw.println(prefix + TAG);
        pw.println(innerPrefix + "mMenuState=" + mMenuState);
        pw.println(innerPrefix + "mIsImeShowing=" + mIsImeShowing);
        pw.println(innerPrefix + "mImeHeight=" + mImeHeight);
        pw.println(innerPrefix + "mIsShelfShowing=" + mIsShelfShowing);
        pw.println(innerPrefix + "mShelfHeight=" + mShelfHeight);
        pw.println(innerPrefix + "mSavedSnapFraction=" + mSavedSnapFraction);
        pw.println(innerPrefix + "mMovementBoundsExtraOffsets=" + mMovementBoundsExtraOffsets);
        mPipBoundsAlgorithm.dump(pw, innerPrefix);
        mTouchState.dump(pw, innerPrefix);
        if (mPipResizeGestureHandler != null) {
            mPipResizeGestureHandler.dump(pw, innerPrefix);
        }
    }

}
