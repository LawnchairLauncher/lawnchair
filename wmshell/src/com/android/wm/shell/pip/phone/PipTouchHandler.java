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

import static com.android.internal.config.sysui.SystemUiDeviceConfigFlags.PIP_STASHING;
import static com.android.internal.config.sysui.SystemUiDeviceConfigFlags.PIP_STASH_MINIMUM_VELOCITY_THRESHOLD;
import static com.android.wm.shell.common.pip.PipBoundsState.STASH_TYPE_LEFT;
import static com.android.wm.shell.common.pip.PipBoundsState.STASH_TYPE_NONE;
import static com.android.wm.shell.common.pip.PipBoundsState.STASH_TYPE_RIGHT;
import static com.android.wm.shell.pip.PipAnimationController.TRANSITION_DIRECTION_TO_PIP;
import static com.android.wm.shell.pip.phone.PhonePipMenuController.MENU_STATE_FULL;
import static com.android.wm.shell.pip.phone.PhonePipMenuController.MENU_STATE_NONE;
import static com.android.wm.shell.pip.phone.PipMenuView.ANIM_TYPE_NONE;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.provider.DeviceConfig;
import android.util.Size;
import android.view.DisplayCutout;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.common.FloatingContentCoordinator;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDoubleTapHelper;
import com.android.wm.shell.common.pip.PipPerfHintController;
import com.android.wm.shell.common.pip.PipUiEventLogger;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.common.pip.SizeSpecSource;
import com.android.wm.shell.pip.PipAnimationController;
import com.android.wm.shell.pip.PipTaskOrganizer;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.sysui.ShellInit;

import java.io.PrintWriter;
import java.util.Optional;

/**
 * Manages all the touch handling for PIP on the Phone, including moving, dismissing and expanding
 * the PIP.
 */
public class PipTouchHandler {

    private static final String TAG = "PipTouchHandler";
    private static final float DEFAULT_STASH_VELOCITY_THRESHOLD = 18000.f;

    // Allow PIP to resize to a slightly bigger state upon touch
    private boolean mEnableResize;
    private final Context mContext;
    private final PipBoundsAlgorithm mPipBoundsAlgorithm;
    @NonNull private final PipBoundsState mPipBoundsState;
    @NonNull private final SizeSpecSource mSizeSpecSource;
    private final PipUiEventLogger mPipUiEventLogger;
    private final PipDismissTargetHandler mPipDismissTargetHandler;
    private final PipTaskOrganizer mPipTaskOrganizer;
    private final ShellExecutor mMainExecutor;
    @Nullable private final PipPerfHintController mPipPerfHintController;

    private PipResizeGestureHandler mPipResizeGestureHandler;

    private final PhonePipMenuController mMenuController;
    private final AccessibilityManager mAccessibilityManager;

    /**
     * Whether PIP stash is enabled or not. When enabled, if the user flings toward the edge of the
     * screen, it will be shown in "stashed" mode, where PIP will only show partially.
     */
    private boolean mEnableStash = true;

    private float mStashVelocityThreshold;

    // The reference inset bounds, used to determine the dismiss fraction
    private final Rect mInsetBounds = new Rect();

    // Used to workaround an issue where the WM rotation happens before we are notified, allowing
    // us to send stale bounds
    private int mDeferResizeToNormalBoundsUntilRotation = -1;
    private int mDisplayRotation;

    private final PipAccessibilityInteractionConnection mConnection;

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
    private final PipTouchState mTouchState;
    private final FloatingContentCoordinator mFloatingContentCoordinator;
    private PipMotionHelper mMotionHelper;
    private PipTouchGesture mGesture;

    // Temp vars
    private final Rect mTmpBounds = new Rect();

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
            ShellInit shellInit,
            PhonePipMenuController menuController,
            PipBoundsAlgorithm pipBoundsAlgorithm,
            @NonNull PipBoundsState pipBoundsState,
            @NonNull SizeSpecSource sizeSpecSource,
            PipTaskOrganizer pipTaskOrganizer,
            PipMotionHelper pipMotionHelper,
            FloatingContentCoordinator floatingContentCoordinator,
            PipUiEventLogger pipUiEventLogger,
            ShellExecutor mainExecutor,
            Optional<PipPerfHintController> pipPerfHintControllerOptional) {
        mContext = context;
        mMainExecutor = mainExecutor;
        mPipPerfHintController = pipPerfHintControllerOptional.orElse(null);
        mAccessibilityManager = context.getSystemService(AccessibilityManager.class);
        mPipBoundsAlgorithm = pipBoundsAlgorithm;
        mPipBoundsState = pipBoundsState;
        mSizeSpecSource = sizeSpecSource;
        mPipTaskOrganizer = pipTaskOrganizer;
        mMenuController = menuController;
        mPipUiEventLogger = pipUiEventLogger;
        mFloatingContentCoordinator = floatingContentCoordinator;
        mMenuController.addListener(new PipMenuListener());
        mGesture = new DefaultPipTouchGesture();
        mMotionHelper = pipMotionHelper;
        mPipDismissTargetHandler = new PipDismissTargetHandler(context, pipUiEventLogger,
                mMotionHelper, mainExecutor);
        mTouchState = new PipTouchState(ViewConfiguration.get(context),
                () -> {
                    if (mPipBoundsState.isStashed()) {
                        animateToUnStashedState();
                        mPipUiEventLogger.log(
                                PipUiEventLogger.PipUiEventEnum.PICTURE_IN_PICTURE_STASH_UNSTASHED);
                        mPipBoundsState.setStashed(STASH_TYPE_NONE);
                    } else {
                        mMenuController.showMenuWithPossibleDelay(MENU_STATE_FULL,
                                mPipBoundsState.getBounds(), true /* allowMenuTimeout */,
                                willResizeMenu(),
                                shouldShowResizeHandle());
                    }
                },
                menuController::hideMenu,
                mainExecutor);
        mPipResizeGestureHandler =
                new PipResizeGestureHandler(context, pipBoundsAlgorithm, pipBoundsState,
                        mMotionHelper, mTouchState, pipTaskOrganizer, mPipDismissTargetHandler,
                        this::updateMovementBounds, pipUiEventLogger,
                        menuController, mainExecutor, mPipPerfHintController);
        mConnection = new PipAccessibilityInteractionConnection(mContext, pipBoundsState,
                mMotionHelper, pipTaskOrganizer, mPipBoundsAlgorithm.getSnapAlgorithm(),
                this::onAccessibilityShowMenu, this::updateMovementBounds,
                this::animateToUnStashedState, mainExecutor);
        mPipBoundsState.addOnAspectRatioChangedCallback(this::updateMinMaxSize);

        // TODO(b/181599115): This should really be initializes as part of the pip controller, but
        // until all PIP implementations derive from the controller, just initialize the touch handler
        // if it is needed
        if (!PipUtils.isPip2ExperimentEnabled()) {
            shellInit.addInitCallback(this::onInit, this);
        }
    }

    /**
     * Called when the touch handler is initialized.
     */
    public void onInit() {
        Resources res = mContext.getResources();
        mEnableResize = res.getBoolean(R.bool.config_pipEnableResizeForMenu);
        reloadResources();

        mMotionHelper.init();
        mPipResizeGestureHandler.init();
        mPipDismissTargetHandler.init();

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

    public PipTransitionController getTransitionHandler() {
        return mPipTaskOrganizer.getTransitionController();
    }

    private void reloadResources() {
        final Resources res = mContext.getResources();
        mBottomOffsetBufferPx = res.getDimensionPixelSize(R.dimen.pip_bottom_offset_buffer);
        mImeOffset = res.getDimensionPixelSize(R.dimen.pip_ime_offset);
        mPipDismissTargetHandler.updateMagneticTargetSize();
    }

    public void onOverlayChanged() {
        // onOverlayChanged is triggered upon theme change, update the dismiss target accordingly.
        mPipDismissTargetHandler.init();
    }

    private boolean shouldShowResizeHandle() {
        return false;
    }

    public void setTouchGesture(PipTouchGesture gesture) {
        mGesture = gesture;
    }

    public void setTouchEnabled(boolean enabled) {
        mTouchState.setAllowTouches(enabled);
    }

    public void showPictureInPictureMenu() {
        // Only show the menu if the user isn't currently interacting with the PiP
        if (!mTouchState.isUserInteracting()) {
            mMenuController.showMenu(MENU_STATE_FULL, mPipBoundsState.getBounds(),
                    false /* allowMenuTimeout */, willResizeMenu(),
                    shouldShowResizeHandle());
        }
    }

    public void onActivityPinned() {
        mPipDismissTargetHandler.createOrUpdateDismissTarget();

        mPipResizeGestureHandler.onActivityPinned();
        mFloatingContentCoordinator.onContentAdded(mMotionHelper);
    }

    public void onActivityUnpinned(ComponentName topPipActivity) {
        if (topPipActivity == null) {
            // Clean up state after the last PiP activity is removed
            mPipDismissTargetHandler.cleanUpDismissTarget();

            mFloatingContentCoordinator.onContentRemoved(mMotionHelper);
        }
        mPipResizeGestureHandler.onActivityUnpinned();
    }

    public void onPinnedStackAnimationEnded(
            @PipAnimationController.TransitionDirection int direction) {
        // Always synchronize the motion helper bounds once PiP animations finish
        mMotionHelper.synchronizePinnedStackBounds();
        updateMovementBounds();
        if (direction == TRANSITION_DIRECTION_TO_PIP) {
            // Set the initial bounds as the user resize bounds.
            mPipResizeGestureHandler.setUserResizeBounds(mPipBoundsState.getBounds());
        }
    }

    public void onConfigurationChanged() {
        mPipResizeGestureHandler.onConfigurationChanged();
        mMotionHelper.synchronizePinnedStackBounds();
        reloadResources();

        if (mPipTaskOrganizer.isInPip()) {
            // Recreate the dismiss target for the new orientation.
            mPipDismissTargetHandler.createOrUpdateDismissTarget();
        }
    }

    public void onImeVisibilityChanged(boolean imeVisible, int imeHeight) {
        mIsImeShowing = imeVisible;
        mImeHeight = imeHeight;
    }

    public void onShelfVisibilityChanged(boolean shelfVisible, int shelfHeight) {
        mIsShelfShowing = shelfVisible;
        mShelfHeight = shelfHeight;
    }

    /**
     * Called when SysUI state changed.
     *
     * @param isSysUiStateValid Is SysUI valid or not.
     */
    public void onSystemUiStateChanged(boolean isSysUiStateValid) {
        mPipResizeGestureHandler.onSystemUiStateChanged(isSysUiStateValid);
    }

    public void adjustBoundsForRotation(Rect outBounds, Rect curBounds, Rect insetBounds) {
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

    public void onMovementBoundsChanged(Rect insetBounds, Rect normalBounds, Rect curBounds,
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

        updatePipSizeConstraints(normalBounds, aspectRatio);

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
        mInsetBounds.set(insetBounds);
        updateMovementBounds();
        mMovementBoundsExtraOffsets = extraOffset;
        mConnection.onMovementBoundsChanged(normalBounds, mPipBoundsState.getExpandedBounds(),
                mPipBoundsState.getNormalMovementBounds(),
                mPipBoundsState.getExpandedMovementBounds());

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
     * Update the values for min/max allowed size of picture in picture window based on the aspect
     * ratio.
     * @param aspectRatio aspect ratio to use for the calculation of min/max size
     */
    public void updateMinMaxSize(float aspectRatio) {
        updatePipSizeConstraints(mPipBoundsState.getNormalBounds(),
                aspectRatio);
    }

    private void updatePipSizeConstraints(Rect normalBounds,
            float aspectRatio) {
        if (mPipResizeGestureHandler.isUsingPinchToZoom()) {
            updatePinchResizeSizeConstraints(aspectRatio);
        } else {
            mPipResizeGestureHandler.updateMinSize(normalBounds.width(), normalBounds.height());
            mPipResizeGestureHandler.updateMaxSize(mPipBoundsState.getExpandedBounds().width(),
                    mPipBoundsState.getExpandedBounds().height());
        }
    }

    private void updatePinchResizeSizeConstraints(float aspectRatio) {
        mPipBoundsState.updateMinMaxSize(aspectRatio);
        mPipResizeGestureHandler.updateMinSize(mPipBoundsState.getMinSize().x,
                mPipBoundsState.getMinSize().y);
        mPipResizeGestureHandler.updateMaxSize(mPipBoundsState.getMaxSize().x,
                mPipBoundsState.getMaxSize().y);
    }

    /**
     * TODO Add appropriate description
     */
    public void onRegistrationChanged(boolean isRegistered) {
        if (isRegistered) {
            mConnection.register(mAccessibilityManager);
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

        // Ignore the motion event When the entry animation is waiting to be started
        if (!mTouchState.isUserInteracting() && mPipTaskOrganizer.isEntryScheduled()) {
            ProtoLog.wtf(WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Waiting to start the entry animation, skip the motion event.", TAG);
            return true;
        }

        // Update the touch state
        mTouchState.onTouchEvent(ev);

        boolean shouldDeliverToMenu = mMenuState != MENU_STATE_NONE;

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN: {
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

                if (mGesture.onUp(mTouchState)) {
                    break;
                }

                // Fall through to clean up
            }
            case MotionEvent.ACTION_CANCEL: {
                shouldDeliverToMenu = !mTouchState.startedDragging() && !mTouchState.isDragging();
                mTouchState.reset();
                break;
            }
            case MotionEvent.ACTION_HOVER_ENTER:
                // If Touch Exploration is enabled, some a11y services (e.g. Talkback) is probably
                // on and changing MotionEvents into HoverEvents.
                // Let's not enable menu show/hide for a11y services.
                if (!mAccessibilityManager.isTouchExplorationEnabled()) {
                    mTouchState.removeHoverExitTimeoutCallback();
                    mMenuController.showMenu(MENU_STATE_FULL, mPipBoundsState.getBounds(),
                            false /* allowMenuTimeout */, false /* willResizeMenu */,
                            shouldShowResizeHandle());
                }
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

    private void animateToMaximizedState(Runnable callback) {
        Rect maxMovementBounds = new Rect();
        Rect maxBounds = new Rect(0, 0, mPipBoundsState.getMaxSize().x,
                mPipBoundsState.getMaxSize().y);
        mPipBoundsAlgorithm.getMovementBounds(maxBounds, mInsetBounds, maxMovementBounds,
                mIsImeShowing ? mImeHeight : 0);
        mSavedSnapFraction = mMotionHelper.animateToExpandedState(maxBounds,
                mPipBoundsState.getMovementBounds(), maxMovementBounds,
                callback);
    }

    private void animateToNormalSize(Runnable callback) {
        // Save the current bounds as the user-resize bounds.
        mPipResizeGestureHandler.setUserResizeBounds(mPipBoundsState.getBounds());

        final Size minMenuSize = mMenuController.getEstimatedMinMenuSize();
        final Rect normalBounds = mPipBoundsState.getNormalBounds();
        final Rect destBounds = mPipBoundsAlgorithm.adjustNormalBoundsToFitMenu(normalBounds,
                minMenuSize);
        Rect restoredMovementBounds = new Rect();
        mPipBoundsAlgorithm.getMovementBounds(destBounds,
                mInsetBounds, restoredMovementBounds, mIsImeShowing ? mImeHeight : 0);
        mSavedSnapFraction = mMotionHelper.animateToExpandedState(destBounds,
                mPipBoundsState.getMovementBounds(), restoredMovementBounds, callback);
    }

    private void animateToUnexpandedState(Rect restoreBounds) {
        Rect restoredMovementBounds = new Rect();
        mPipBoundsAlgorithm.getMovementBounds(restoreBounds,
                mInsetBounds, restoredMovementBounds, mIsImeShowing ? mImeHeight : 0);
        mMotionHelper.animateToUnexpandedState(restoreBounds, mSavedSnapFraction,
                restoredMovementBounds, mPipBoundsState.getMovementBounds(), false /* immediate */);
        mSavedSnapFraction = -1f;
    }

    private void animateToUnStashedState() {
        final Rect pipBounds = mPipBoundsState.getBounds();
        final boolean onLeftEdge = pipBounds.left < mPipBoundsState.getDisplayBounds().left;
        final Rect unStashedBounds = new Rect(0, pipBounds.top, 0, pipBounds.bottom);
        unStashedBounds.left = onLeftEdge ? mInsetBounds.left
                : mInsetBounds.right - pipBounds.width();
        unStashedBounds.right = onLeftEdge ? mInsetBounds.left + pipBounds.width()
                : mInsetBounds.right;
        mMotionHelper.animateToUnStashedBounds(unStashedBounds);
    }

    /**
     * @return the motion helper.
     */
    public PipMotionHelper getMotionHelper() {
        return mMotionHelper;
    }

    @VisibleForTesting
    public PipResizeGestureHandler getPipResizeGestureHandler() {
        return mPipResizeGestureHandler;
    }

    @VisibleForTesting
    public void setPipResizeGestureHandler(PipResizeGestureHandler pipResizeGestureHandler) {
        mPipResizeGestureHandler = pipResizeGestureHandler;
    }

    @VisibleForTesting
    public void setPipMotionHelper(PipMotionHelper pipMotionHelper) {
        mMotionHelper = pipMotionHelper;
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
        private final Point mStartPosition = new Point();
        private final PointF mDelta = new PointF();
        private boolean mShouldHideMenuAfterFling;

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
            mStartPosition.set(bounds.left, bounds.top);
            mMovementWithinDismiss = touchState.getDownTouchPosition().y
                    >= mPipBoundsState.getMovementBounds().bottom;
            mMotionHelper.setSpringingToTouch(false);
            mPipDismissTargetHandler.setTaskLeash(mPipTaskOrganizer.getSurfaceControl());

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
                mPipDismissTargetHandler.showDismissTargetMaybe();
            }

            if (touchState.isDragging()) {
                mPipBoundsState.setHasUserMovedPip(true);

                // Move the pinned stack freely
                final PointF lastDelta = touchState.getLastTouchDelta();
                float lastX = mStartPosition.x + mDelta.x;
                float lastY = mStartPosition.y + mDelta.y;
                float left = lastX + lastDelta.x;
                float top = lastY + lastDelta.y;

                // Add to the cumulative delta after bounding the position
                mDelta.x += left - lastX;
                mDelta.y += top - lastY;

                mTmpBounds.set(getPossiblyMotionBounds());
                mTmpBounds.offsetTo((int) left, (int) top);
                mMotionHelper.movePip(mTmpBounds, true /* isDragging */);

                final PointF curPos = touchState.getLastTouchPosition();
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

            if (!touchState.isUserInteracting()) {
                return false;
            }

            final PointF vel = touchState.getVelocity();

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
                if (mEnableStash && shouldStash(vel, getPossiblyMotionBounds())) {
                    mMotionHelper.stashToEdge(vel.x, vel.y, this::stashEndAction /* endAction */);
                } else {
                    if (mPipBoundsState.isStashed()) {
                        // Reset stashed state if previously stashed
                        mPipUiEventLogger.log(
                                PipUiEventLogger.PipUiEventEnum.PICTURE_IN_PICTURE_STASH_UNSTASHED);
                        mPipBoundsState.setStashed(STASH_TYPE_NONE);
                    }
                    mMotionHelper.flingToSnapTarget(vel.x, vel.y,
                            this::flingEndAction /* endAction */);
                }
            } else if (mTouchState.isDoubleTap() && !mPipBoundsState.isStashed()
                    && mMenuState != MENU_STATE_FULL) {
                // If using pinch to zoom, double-tap functions as resizing between max/min size
                if (mPipResizeGestureHandler.isUsingPinchToZoom()) {
                    final boolean toExpand = mPipBoundsState.getBounds().width()
                            < mPipBoundsState.getMaxSize().x
                            && mPipBoundsState.getBounds().height()
                            < mPipBoundsState.getMaxSize().y;
                    if (mMenuController.isMenuVisible()) {
                        mMenuController.hideMenu(ANIM_TYPE_NONE, false /* resize */);
                    }

                    // the size to toggle to after a double tap
                    int nextSize = PipDoubleTapHelper
                            .nextSizeSpec(mPipBoundsState, getUserResizeBounds());

                    // actually toggle to the size chosen
                    if (nextSize == PipDoubleTapHelper.SIZE_SPEC_MAX) {
                        mPipResizeGestureHandler.setUserResizeBounds(mPipBoundsState.getBounds());
                        animateToMaximizedState(null);
                    } else if (nextSize == PipDoubleTapHelper.SIZE_SPEC_DEFAULT) {
                        mPipResizeGestureHandler.setUserResizeBounds(mPipBoundsState.getBounds());
                        animateToNormalSize(null);
                    } else {
                        animateToUnexpandedState(getUserResizeBounds());
                    }
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

        private void stashEndAction() {
            if (mPipBoundsState.getBounds().left < 0
                    && mPipBoundsState.getStashedState() != STASH_TYPE_LEFT) {
                mPipUiEventLogger.log(
                        PipUiEventLogger.PipUiEventEnum.PICTURE_IN_PICTURE_STASH_LEFT);
                mPipBoundsState.setStashed(STASH_TYPE_LEFT);
            } else if (mPipBoundsState.getBounds().left >= 0
                    && mPipBoundsState.getStashedState() != STASH_TYPE_RIGHT) {
                mPipUiEventLogger.log(
                        PipUiEventLogger.PipUiEventEnum.PICTURE_IN_PICTURE_STASH_RIGHT);
                mPipBoundsState.setStashed(STASH_TYPE_RIGHT);
            }
            mMenuController.hideMenu();
        }

        private void flingEndAction() {
            if (mShouldHideMenuAfterFling) {
                // If the menu is not visible, then we can still be showing the activity for the
                // dismiss overlay, so just finish it after the animation completes
                mMenuController.hideMenu();
            }
        }

        private boolean shouldStash(PointF vel, Rect motionBounds) {
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

            return stashFromFlingToEdge || stashFromDroppingOnEdge;
        }
    }

    /**
     * Updates the current movement bounds based on whether the menu is currently visible and
     * resized.
     */
    private void updateMovementBounds() {
        mPipBoundsAlgorithm.getMovementBounds(mPipBoundsState.getBounds(),
                mInsetBounds, mPipBoundsState.getMovementBounds(), mIsImeShowing ? mImeHeight : 0);
        mMotionHelper.onMovementBoundsChanged();
    }

    private Rect getMovementBounds(Rect curBounds) {
        Rect movementBounds = new Rect();
        mPipBoundsAlgorithm.getMovementBounds(curBounds, mInsetBounds,
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
