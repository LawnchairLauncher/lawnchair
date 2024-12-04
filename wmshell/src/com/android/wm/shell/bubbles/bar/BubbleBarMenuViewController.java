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
package com.android.wm.shell.bubbles.bar;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Icon;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.android.wm.shell.R;
import com.android.wm.shell.bubbles.Bubble;
import com.android.wm.shell.shared.animation.PhysicsAnimator;

import java.util.ArrayList;

/**
 * Manages bubble bar expanded view menu presentation and animations
 */
class BubbleBarMenuViewController {
    private static final float MENU_INITIAL_SCALE = 0.5f;
    private final Context mContext;
    private final ViewGroup mRootView;
    private @Nullable Listener mListener;
    private @Nullable Bubble mBubble;
    private @Nullable BubbleBarMenuView mMenuView;
    /** A transparent view used to intercept touches to collapse menu when presented */
    private @Nullable View mScrimView;
    private @Nullable PhysicsAnimator<BubbleBarMenuView> mMenuAnimator;
    private PhysicsAnimator.SpringConfig mMenuSpringConfig;

    BubbleBarMenuViewController(Context context, ViewGroup rootView) {
        mContext = context;
        mRootView = rootView;
        mMenuSpringConfig = new PhysicsAnimator.SpringConfig(
                SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_LOW_BOUNCY);
    }

    /** Tells if the menu is visible or being animated */
    boolean isMenuVisible() {
        return mMenuView != null && mMenuView.getVisibility() == View.VISIBLE;
    }

    /** Sets menu actions listener */
    void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /** Update menu with bubble */
    void updateMenu(@NonNull Bubble bubble) {
        mBubble = bubble;
    }

    /**
     * Show bubble bar expanded view menu
     * @param animated if should animate transition
     */
    void showMenu(boolean animated) {
        if (mMenuView == null || mScrimView == null) {
            setupMenu();
        }
        cancelAnimations();
        mMenuView.setVisibility(View.VISIBLE);
        mScrimView.setVisibility(View.VISIBLE);
        Runnable endActions = () -> {
            mMenuView.getChildAt(0).requestAccessibilityFocus();
            if (mListener != null) {
                mListener.onMenuVisibilityChanged(true /* isShown */);
            }
        };
        if (animated) {
            animateTransition(true /* show */, endActions);
        } else {
            endActions.run();
        }
    }

    /**
     * Hide bubble bar expanded view menu
     * @param animated if should animate transition
     */
    void hideMenu(boolean animated) {
        if (mMenuView == null || mScrimView == null) return;
        cancelAnimations();
        Runnable endActions = () -> {
            mMenuView.setVisibility(View.GONE);
            mScrimView.setVisibility(View.GONE);
            if (mListener != null) {
                mListener.onMenuVisibilityChanged(false /* isShown */);
            }
        };
        if (animated) {
            animateTransition(false /* show */, endActions);
        } else {
            endActions.run();
        }
    }

    /**
     * Animate show/hide menu transition
     * @param show if should show or hide the menu
     * @param endActions will be called when animation ends
     */
    private void animateTransition(boolean show, Runnable endActions) {
        if (mMenuView == null) return;
        mMenuAnimator = PhysicsAnimator.getInstance(mMenuView);
        mMenuAnimator.setDefaultSpringConfig(mMenuSpringConfig);
        mMenuAnimator
                .spring(DynamicAnimation.ALPHA, show ? 1f : 0f)
                .spring(DynamicAnimation.SCALE_Y, show ? 1f : MENU_INITIAL_SCALE)
                .withEndActions(() -> {
                    mMenuAnimator = null;
                    endActions.run();
                })
                .start();
    }

    /** Cancel running animations */
    private void cancelAnimations() {
        if (mMenuAnimator != null) {
            mMenuAnimator.cancel();
            mMenuAnimator = null;
        }
    }

    /** Sets up and inflate menu views */
    private void setupMenu() {
        // Menu view setup
        mMenuView = (BubbleBarMenuView) LayoutInflater.from(mContext).inflate(
                R.layout.bubble_bar_menu_view, mRootView, false);
        mMenuView.setAlpha(0f);
        mMenuView.setPivotY(0f);
        mMenuView.setScaleY(MENU_INITIAL_SCALE);
        mMenuView.setOnCloseListener(() -> hideMenu(true  /* animated */));
        if (mBubble != null) {
            mMenuView.updateInfo(mBubble);
            mMenuView.updateActions(createMenuActions(mBubble));
        }
        // Scrim view setup
        mScrimView = new View(mContext);
        mScrimView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        mScrimView.setOnClickListener(view -> hideMenu(true  /* animated */));
        // Attach to root view
        mRootView.addView(mScrimView);
        mRootView.addView(mMenuView);
    }

    /**
     * Creates menu actions to populate menu view
     * @param bubble used to create actions depending on bubble type
     */
    private ArrayList<BubbleBarMenuView.MenuAction> createMenuActions(Bubble bubble) {
        ArrayList<BubbleBarMenuView.MenuAction> menuActions = new ArrayList<>();
        Resources resources = mContext.getResources();

        if (bubble.isConversation()) {
            // Don't bubble conversation action
            menuActions.add(new BubbleBarMenuView.MenuAction(
                    Icon.createWithResource(mContext, R.drawable.bubble_ic_stop_bubble),
                    resources.getString(R.string.bubbles_dont_bubble_conversation),
                    view -> {
                        hideMenu(true /* animated */);
                        if (mListener != null) {
                            mListener.onUnBubbleConversation(bubble);
                        }
                    }
            ));
            // Open settings action
            Icon appIcon = bubble.getRawAppBadge() != null ? Icon.createWithBitmap(
                    bubble.getRawAppBadge()) : null;
            menuActions.add(new BubbleBarMenuView.MenuAction(
                    appIcon,
                    resources.getString(R.string.bubbles_app_settings, bubble.getAppName()),
                    view -> {
                        hideMenu(true /* animated */);
                        if (mListener != null) {
                            mListener.onOpenAppSettings(bubble);
                        }
                    }
            ));
        }

        // Dismiss bubble action
        menuActions.add(new BubbleBarMenuView.MenuAction(
                Icon.createWithResource(resources, R.drawable.ic_remove_no_shadow),
                resources.getString(R.string.bubble_dismiss_text),
                ContextCompat.getColor(mContext, R.color.bubble_bar_expanded_view_menu_close),
                view -> {
                    hideMenu(true /* animated */);
                    if (mListener != null) {
                        mListener.onDismissBubble(bubble);
                    }
                }
        ));

        return menuActions;
    }

    /**
     * Bubble bar expanded view menu actions listener
     */
    interface Listener {
        /**
         * Called when manage menu is shown/hidden
         * If animated will be called when animation ends
         */
        void onMenuVisibilityChanged(boolean visible);

        /**
         * Un-bubbles conversation and removes the bubble from the stack
         * This conversation will not be bubbled with new messages
         * @see com.android.wm.shell.bubbles.BubbleController
         */
        void onUnBubbleConversation(Bubble bubble);

        /**
         * Launches app notification bubble settings for the bubble with intent created in:
         * {@code Bubble.getSettingsIntent}
         */
        void onOpenAppSettings(Bubble bubble);

        /**
         * Dismiss bubble and remove it from the bubble stack
         */
        void onDismissBubble(Bubble bubble);
    }
}
