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

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.provider.Settings.ACTION_PICTURE_IN_PICTURE_SETTINGS;
import static android.view.accessibility.AccessibilityManager.FLAG_CONTENT_CONTROLS;
import static android.view.accessibility.AccessibilityManager.FLAG_CONTENT_ICONS;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK;

import static com.android.wm.shell.pip.phone.PhonePipMenuController.MENU_STATE_FULL;
import static com.android.wm.shell.pip.phone.PhonePipMenuController.MENU_STATE_NONE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.app.RemoteAction;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.util.Pair;
import android.util.Size;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.pip.PipUiEventLogger;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.animation.Interpolators;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Translucent window that gets started on top of a task in PIP to allow the user to control it.
 */
public class PipMenuView extends FrameLayout {

    private static final String TAG = "PipMenuView";

    private static final int ANIMATION_NONE_DURATION_MS = 0;
    private static final int ANIMATION_HIDE_DURATION_MS = 125;

    /** No animation performed during menu hide. */
    public static final int ANIM_TYPE_NONE = 0;
    /** Fade out the menu until it's invisible. Used when the PIP window remains visible.  */
    public static final int ANIM_TYPE_HIDE = 1;
    /** Fade out the menu in sync with the PIP window. */
    public static final int ANIM_TYPE_DISMISS = 2;

    @IntDef(prefix = { "ANIM_TYPE_" }, value = {
            ANIM_TYPE_NONE,
            ANIM_TYPE_HIDE,
            ANIM_TYPE_DISMISS
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AnimationType {}

    private static final int INITIAL_DISMISS_DELAY = 3500;
    private static final int POST_INTERACTION_DISMISS_DELAY = 2000;
    private static final long MENU_SHOW_ON_EXPAND_START_DELAY = 30;

    private static final float MENU_BACKGROUND_ALPHA = 0.54f;
    private static final float DISABLED_ACTION_ALPHA = 0.54f;

    private int mMenuState;
    private boolean mAllowMenuTimeout = true;
    private boolean mAllowTouches = true;
    private int mDismissFadeOutDurationMs;

    private final List<RemoteAction> mActions = new ArrayList<>();
    private RemoteAction mCloseAction;

    private AccessibilityManager mAccessibilityManager;
    private Drawable mBackgroundDrawable;
    private View mMenuContainer;
    private LinearLayout mActionsGroup;
    private int mBetweenActionPaddingLand;

    private AnimatorSet mMenuContainerAnimator;
    private final PhonePipMenuController mController;
    private final PipUiEventLogger mPipUiEventLogger;

    private ValueAnimator.AnimatorUpdateListener mMenuBgUpdateListener =
            new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    final float alpha = (float) animation.getAnimatedValue();
                    mBackgroundDrawable.setAlpha((int) (MENU_BACKGROUND_ALPHA * alpha * 255));
                }
            };

    private ShellExecutor mMainExecutor;
    private Handler mMainHandler;

    /**
     * Whether the most recent showing of the menu caused a PIP resize, such as when PIP is too
     * small and it is resized on menu show to fit the actions.
     */
    private boolean mDidLastShowMenuResize;
    private final Runnable mHideMenuRunnable = this::hideMenu;

    protected View mViewRoot;
    protected View mSettingsButton;
    protected View mDismissButton;
    protected View mTopEndContainer;
    protected PipMenuIconsAlgorithm mPipMenuIconsAlgorithm;

    // How long the shell will wait for the app to close the PiP if a custom action is set.
    private final int mPipForceCloseDelay;

    // Context for the currently active user. This may differ from the regular systemui Context
    // in cases such as secondary users or HSUM.
    private Context mContextForUser;

    public PipMenuView(Context context, PhonePipMenuController controller,
            ShellExecutor mainExecutor, Handler mainHandler,
            PipUiEventLogger pipUiEventLogger) {
        super(context, null, 0);
        mContext = context;
        mController = controller;
        mMainExecutor = mainExecutor;
        mMainHandler = mainHandler;
        mPipUiEventLogger = pipUiEventLogger;

        mAccessibilityManager = context.getSystemService(AccessibilityManager.class);
        inflate(context, R.layout.pip_menu, this);

        mPipForceCloseDelay = context.getResources().getInteger(
                R.integer.config_pipForceCloseDelay);

        mBackgroundDrawable = mContext.getDrawable(R.drawable.pip_menu_background);
        mBackgroundDrawable.setAlpha(0);
        mViewRoot = findViewById(R.id.background);
        mViewRoot.setBackground(mBackgroundDrawable);
        mMenuContainer = findViewById(R.id.menu_container);
        mMenuContainer.setAlpha(0);
        mTopEndContainer = findViewById(R.id.top_end_container);
        mSettingsButton = findViewById(R.id.settings);
        mSettingsButton.setAlpha(0);
        mSettingsButton.setOnClickListener((v) -> {
            if (v.getAlpha() != 0) {
                showSettings();
            }
        });
        mDismissButton = findViewById(R.id.dismiss);
        mDismissButton.setAlpha(0);
        mDismissButton.setOnClickListener(v -> dismissPip());
        findViewById(R.id.expand_button).setOnClickListener(v -> {
            if (mMenuContainer.getAlpha() != 0) {
                expandPip();
            }
        });

        findViewById(R.id.resize_handle).setAlpha(0);

        mActionsGroup = findViewById(R.id.actions_group);
        mBetweenActionPaddingLand = getResources().getDimensionPixelSize(
                R.dimen.pip_between_action_padding_land);
        mPipMenuIconsAlgorithm = new PipMenuIconsAlgorithm(mContext);
        mPipMenuIconsAlgorithm.bindViews((ViewGroup) mViewRoot, (ViewGroup) mTopEndContainer,
                findViewById(R.id.resize_handle), mSettingsButton, mDismissButton);
        mDismissFadeOutDurationMs = context.getResources()
                .getInteger(R.integer.config_pipExitAnimationDuration);

        initAccessibility();
        setContextForUser();
    }

    private void initAccessibility() {
        this.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                String label = getResources().getString(R.string.pip_menu_title);
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(ACTION_CLICK, label));
            }

            @Override
            public boolean performAccessibilityAction(View host, int action, Bundle args) {
                if (action == ACTION_CLICK && mMenuState != MENU_STATE_FULL) {
                    mController.showMenu();
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            hideMenu();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!mAllowTouches) {
            return false;
        }

        if (mAllowMenuTimeout) {
            repostDelayedHide(POST_INTERACTION_DISMISS_DELAY);
        }

        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (mAllowMenuTimeout) {
            repostDelayedHide(POST_INTERACTION_DISMISS_DELAY);
        }

        return super.dispatchGenericMotionEvent(event);
    }

    void showMenu(int menuState, Rect stackBounds, boolean allowMenuTimeout,
            boolean resizeMenuOnShow, boolean withDelay, boolean showResizeHandle) {
        mAllowMenuTimeout = allowMenuTimeout;
        mDidLastShowMenuResize = resizeMenuOnShow;
        if (mMenuState != menuState) {
            // Disallow touches if the menu needs to resize while showing, and we are transitioning
            // to/from a full menu state.
            boolean disallowTouchesUntilAnimationEnd = resizeMenuOnShow
                    && (mMenuState == MENU_STATE_FULL || menuState == MENU_STATE_FULL);
            mAllowTouches = !disallowTouchesUntilAnimationEnd;
            cancelDelayedHide();
            if (mMenuContainerAnimator != null) {
                mMenuContainerAnimator.cancel();
            }
            mMenuContainerAnimator = new AnimatorSet();
            ObjectAnimator menuAnim = ObjectAnimator.ofFloat(mMenuContainer, View.ALPHA,
                    mMenuContainer.getAlpha(), 1f);
            menuAnim.addUpdateListener(mMenuBgUpdateListener);
            ObjectAnimator settingsAnim = ObjectAnimator.ofFloat(mSettingsButton, View.ALPHA,
                    mSettingsButton.getAlpha(), 1f);
            ObjectAnimator dismissAnim = ObjectAnimator.ofFloat(mDismissButton, View.ALPHA,
                    mDismissButton.getAlpha(), 1f);
            if (menuState == MENU_STATE_FULL) {
                mMenuContainerAnimator.playTogether(menuAnim, settingsAnim, dismissAnim);
            }
            mMenuContainerAnimator.setInterpolator(Interpolators.ALPHA_IN);
            mMenuContainerAnimator.setDuration(ANIMATION_HIDE_DURATION_MS);
            mMenuContainerAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mAllowTouches = true;
                    notifyMenuStateChangeFinish(menuState);
                    if (allowMenuTimeout) {
                        repostDelayedHide(INITIAL_DISMISS_DELAY);
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    mAllowTouches = true;
                }
            });
            if (withDelay) {
                // starts the menu container animation after window expansion is completed
                notifyMenuStateChangeStart(menuState, resizeMenuOnShow, () -> {
                    if (mMenuContainerAnimator == null) {
                        return;
                    }
                    mMenuContainerAnimator.setStartDelay(MENU_SHOW_ON_EXPAND_START_DELAY);
                    setVisibility(VISIBLE);
                    mMenuContainerAnimator.start();
                });
            } else {
                notifyMenuStateChangeStart(menuState, resizeMenuOnShow, null);
                setVisibility(VISIBLE);
                mMenuContainerAnimator.start();
            }
            updateActionViews(menuState, stackBounds);
        } else {
            // If we are already visible, then just start the delayed dismiss and unregister any
            // existing input consumers from the previous drag
            if (allowMenuTimeout) {
                repostDelayedHide(POST_INTERACTION_DISMISS_DELAY);
            }
        }
    }

    /**
     * Different from {@link #hideMenu()}, this function does not try to finish this menu activity
     * and instead, it fades out the controls by setting the alpha to 0 directly without menu
     * visibility callbacks invoked.
     */
    void fadeOutMenu() {
        mMenuContainer.setAlpha(0f);
        mSettingsButton.setAlpha(0f);
        mDismissButton.setAlpha(0f);
    }

    void pokeMenu() {
        cancelDelayedHide();
    }

    void updateMenuLayout(Rect bounds) {
        mPipMenuIconsAlgorithm.onBoundsChanged(bounds);
    }

    void hideMenu() {
        hideMenu(null);
    }

    void hideMenu(Runnable animationEndCallback) {
        hideMenu(animationEndCallback, true /* notifyMenuVisibility */, mDidLastShowMenuResize,
                ANIM_TYPE_HIDE);
    }

    void hideMenu(boolean resize, @AnimationType int animationType) {
        hideMenu(null /* animationFinishedRunnable */, true /* notifyMenuVisibility */, resize,
                animationType);
    }

    void hideMenu(final Runnable animationFinishedRunnable, boolean notifyMenuVisibility,
            boolean resize, @AnimationType int animationType) {
        if (mMenuState != MENU_STATE_NONE) {
            cancelDelayedHide();
            if (notifyMenuVisibility) {
                notifyMenuStateChangeStart(MENU_STATE_NONE, resize, null);
            }
            mMenuContainerAnimator = new AnimatorSet();
            ObjectAnimator menuAnim = ObjectAnimator.ofFloat(mMenuContainer, View.ALPHA,
                    mMenuContainer.getAlpha(), 0f);
            menuAnim.addUpdateListener(mMenuBgUpdateListener);
            ObjectAnimator settingsAnim = ObjectAnimator.ofFloat(mSettingsButton, View.ALPHA,
                    mSettingsButton.getAlpha(), 0f);
            ObjectAnimator dismissAnim = ObjectAnimator.ofFloat(mDismissButton, View.ALPHA,
                    mDismissButton.getAlpha(), 0f);
            mMenuContainerAnimator.playTogether(menuAnim, settingsAnim, dismissAnim);
            mMenuContainerAnimator.setInterpolator(Interpolators.ALPHA_OUT);
            mMenuContainerAnimator.setDuration(getFadeOutDuration(animationType));
            mMenuContainerAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    setVisibility(GONE);
                    if (notifyMenuVisibility) {
                        notifyMenuStateChangeFinish(MENU_STATE_NONE);
                    }
                    if (animationFinishedRunnable != null) {
                        animationFinishedRunnable.run();
                    }
                }
            });
            mMenuContainerAnimator.start();
        }
    }

    /**
     * @return Estimated minimum {@link Size} to hold the actions.
     * See also {@link #updateActionViews(int, Rect)}
     */
    Size getEstimatedMinMenuSize() {
        final int pipActionSize = getResources().getDimensionPixelSize(R.dimen.pip_action_size);
        // the minimum width would be (2 * pipActionSize) since we have settings and dismiss button
        // on the top action container.
        final int width = Math.max(2, mActions.size()) * pipActionSize;
        final int height = getResources().getDimensionPixelSize(R.dimen.pip_expand_action_size)
                + getResources().getDimensionPixelSize(R.dimen.pip_action_padding)
                + getResources().getDimensionPixelSize(R.dimen.pip_expand_container_edge_margin);
        return new Size(width, height);
    }

    void setActions(Rect stackBounds, @Nullable List<RemoteAction> actions,
            @Nullable RemoteAction closeAction) {
        mActions.clear();
        if (actions != null && !actions.isEmpty()) {
            mActions.addAll(actions);
        }
        mCloseAction = closeAction;
        if (mMenuState == MENU_STATE_FULL) {
            updateActionViews(mMenuState, stackBounds);
        }
    }

    private void updateActionViews(int menuState, Rect stackBounds) {
        ViewGroup expandContainer = findViewById(R.id.expand_container);
        ViewGroup actionsContainer = findViewById(R.id.actions_container);
        actionsContainer.setOnTouchListener((v, ev) -> {
            // Do nothing, prevent click through to parent
            return true;
        });

        // Update the expand button only if it should show with the menu
        expandContainer.setVisibility(menuState == MENU_STATE_FULL
                ? View.VISIBLE
                : View.INVISIBLE);

        FrameLayout.LayoutParams expandedLp =
                (FrameLayout.LayoutParams) expandContainer.getLayoutParams();
        if (mActions.isEmpty() || menuState == MENU_STATE_NONE) {
            actionsContainer.setVisibility(View.INVISIBLE);

            // Update the expand container margin to adjust the center of the expand button to
            // account for the existence of the action container
            expandedLp.topMargin = 0;
            expandedLp.bottomMargin = 0;
        } else {
            actionsContainer.setVisibility(View.VISIBLE);
            if (mActionsGroup != null) {
                // Ensure we have as many buttons as actions
                final LayoutInflater inflater = LayoutInflater.from(mContext);
                while (mActionsGroup.getChildCount() < mActions.size()) {
                    final PipMenuActionView actionView = (PipMenuActionView) inflater.inflate(
                            R.layout.pip_menu_action, mActionsGroup, false);
                    mActionsGroup.addView(actionView);
                }

                // Update the visibility of all views
                for (int i = 0; i < mActionsGroup.getChildCount(); i++) {
                    mActionsGroup.getChildAt(i).setVisibility(i < mActions.size()
                            ? View.VISIBLE
                            : View.GONE);
                }

                // Recreate the layout
                final boolean isLandscapePip = stackBounds != null
                        && (stackBounds.width() > stackBounds.height());
                for (int i = 0; i < mActions.size(); i++) {
                    final RemoteAction action = mActions.get(i);
                    final PipMenuActionView actionView =
                            (PipMenuActionView) mActionsGroup.getChildAt(i);
                    final boolean isCloseAction = mCloseAction != null && Objects.equals(
                            mCloseAction.getActionIntent(), action.getActionIntent());

                    final int iconType = action.getIcon().getType();
                    if (iconType == Icon.TYPE_URI || iconType == Icon.TYPE_URI_ADAPTIVE_BITMAP) {
                        // Disallow loading icon from content URI
                        actionView.setImageDrawable(null);
                    } else {
                        // TODO: Check if the action drawable has changed before we reload it
                        action.getIcon().loadDrawableAsync(mContextForUser, d -> {
                            if (d != null) {
                                d.setTint(Color.WHITE);
                                actionView.setImageDrawable(d);
                            }
                        }, mMainHandler);
                    }
                    actionView.setCustomCloseBackgroundVisibility(
                            isCloseAction ? View.VISIBLE : View.GONE);
                    actionView.setContentDescription(action.getContentDescription());
                    if (action.isEnabled()) {
                        actionView.setOnClickListener(
                                v -> onActionViewClicked(action.getActionIntent(), isCloseAction));
                    }
                    actionView.setEnabled(action.isEnabled());
                    actionView.setAlpha(action.isEnabled() ? 1f : DISABLED_ACTION_ALPHA);

                    // Update the margin between actions
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)
                            actionView.getLayoutParams();
                    lp.leftMargin = (isLandscapePip && i > 0) ? mBetweenActionPaddingLand : 0;
                }
            }

            // Update the expand container margin to adjust the center of the expand button to
            // account for the existence of the action container
            expandedLp.topMargin = getResources().getDimensionPixelSize(
                    R.dimen.pip_action_padding);
            expandedLp.bottomMargin = getResources().getDimensionPixelSize(
                    R.dimen.pip_expand_container_edge_margin);
        }
        expandContainer.requestLayout();
    }

    /**
     * Sets the Context for the current user. If the user is the same as systemui, then simply
     * use systemui Context.
     */
    private void setContextForUser() {
        int userId = ActivityManager.getCurrentUser();

        if (mContext.getUserId() != userId) {
            try {
                mContextForUser = mContext.createPackageContextAsUser(mContext.getPackageName(),
                        Context.CONTEXT_RESTRICTED, new UserHandle(userId));
            } catch (PackageManager.NameNotFoundException e) {
                // Shouldn't happen, use systemui context as backup
                ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: Failed to get context for user. Sysui userid=%d,"
                                + " current userid=%d, error=%s",
                        TAG,
                        mContext.getUserId(),
                        userId,
                        e);
                mContextForUser = mContext;
            }
        } else {
            mContextForUser = mContext;
        }
    }

    private void notifyMenuStateChangeStart(int menuState, boolean resize, Runnable callback) {
        mController.onMenuStateChangeStart(menuState, resize, callback);
    }

    private void notifyMenuStateChangeFinish(int menuState) {
        mMenuState = menuState;
        mController.onMenuStateChangeFinish(menuState);
    }

    private void expandPip() {
        // Do not notify menu visibility when hiding the menu, the controller will do this when it
        // handles the message
        hideMenu(mController::onPipExpand, false /* notifyMenuVisibility */, true /* resize */,
                ANIM_TYPE_HIDE);
        mPipUiEventLogger.log(
                PipUiEventLogger.PipUiEventEnum.PICTURE_IN_PICTURE_EXPAND_TO_FULLSCREEN);
    }

    private void dismissPip() {
        if (mMenuState != MENU_STATE_NONE) {
            // Do not call hideMenu() directly. Instead, let the menu controller handle it just as
            // any other dismissal that will update the touch state and fade out the PIP task
            // and the menu view at the same time.
            mController.onPipDismiss();
            mPipUiEventLogger.log(PipUiEventLogger.PipUiEventEnum.PICTURE_IN_PICTURE_TAP_TO_REMOVE);
        }
    }

    /**
     * Execute the {@link PendingIntent} attached to the {@link PipMenuActionView}.
     * If the given {@link PendingIntent} matches {@link #mCloseAction}, we need to make sure
     * the PiP is removed after a certain timeout in case the app does not respond in a
     * timely manner.
     */
    private void onActionViewClicked(@NonNull PendingIntent intent, boolean isCloseAction) {
        try {
            intent.send();
        } catch (PendingIntent.CanceledException e) {
            ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Failed to send action, %s", TAG, e);
        }
        if (isCloseAction) {
            mPipUiEventLogger.log(PipUiEventLogger.PipUiEventEnum.PICTURE_IN_PICTURE_CUSTOM_CLOSE);
            mAllowTouches = false;
            mMainExecutor.executeDelayed(() -> {
                hideMenu();
                // TODO: it's unsafe to call onPipDismiss with a delay here since
                // we may have a different PiP by the time this runnable is executed.
                mController.onPipDismiss();
                mAllowTouches = true;
            }, mPipForceCloseDelay);
        }
    }


    private void showSettings() {
        final Pair<ComponentName, Integer> topPipActivityInfo =
                PipUtils.getTopPipActivity(mContext);
        if (topPipActivityInfo.first != null) {
            final Intent settingsIntent = new Intent(ACTION_PICTURE_IN_PICTURE_SETTINGS,
                    Uri.fromParts("package", topPipActivityInfo.first.getPackageName(), null));
            settingsIntent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
            mContext.startActivityAsUser(settingsIntent, UserHandle.of(topPipActivityInfo.second));
            mPipUiEventLogger.log(PipUiEventLogger.PipUiEventEnum.PICTURE_IN_PICTURE_SHOW_SETTINGS);
        }
    }

    private void cancelDelayedHide() {
        mMainExecutor.removeCallbacks(mHideMenuRunnable);
    }

    private void repostDelayedHide(int delay) {
        int recommendedTimeout = mAccessibilityManager.getRecommendedTimeoutMillis(delay,
                FLAG_CONTENT_ICONS | FLAG_CONTENT_CONTROLS);
        mMainExecutor.removeCallbacks(mHideMenuRunnable);
        mMainExecutor.executeDelayed(mHideMenuRunnable, recommendedTimeout);
    }

    private long getFadeOutDuration(@AnimationType int animationType) {
        switch (animationType) {
            case ANIM_TYPE_NONE:
                return ANIMATION_NONE_DURATION_MS;
            case ANIM_TYPE_HIDE:
                return ANIMATION_HIDE_DURATION_MS;
            case ANIM_TYPE_DISMISS:
                return mDismissFadeOutDurationMs;
            default:
                throw new IllegalStateException("Invalid animation type " + animationType);
        }
    }
}
