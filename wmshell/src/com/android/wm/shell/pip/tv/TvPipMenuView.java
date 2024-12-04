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

package com.android.wm.shell.pip.tv;

import static android.view.KeyEvent.ACTION_UP;
import static android.view.KeyEvent.KEYCODE_BACK;
import static android.view.KeyEvent.KEYCODE_DPAD_CENTER;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP;
import static android.view.KeyEvent.KEYCODE_ENTER;

import static com.android.wm.shell.pip.tv.TvPipAction.ACTION_MOVE;
import static com.android.wm.shell.pip.tv.TvPipMenuController.MODE_ALL_ACTIONS_MENU;
import static com.android.wm.shell.pip.tv.TvPipMenuController.MODE_MOVE_MENU;
import static com.android.wm.shell.pip.tv.TvPipMenuController.MODE_NO_MENU;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.PathShape;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.android.internal.protolog.common.ProtoLog;
import com.android.internal.widget.LinearLayoutManager;
import com.android.internal.widget.RecyclerView;
import com.android.wm.shell.R;
import com.android.wm.shell.common.TvWindowMenuActionButton;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.protolog.ShellProtoLogGroup;

import java.util.List;

/**
 * A View that represents Pip Menu on TV. It's responsible for displaying the Pip menu actions from
 * the TvPipActionsProvider as well as the buttons for manually moving the PiP.
 */
public class TvPipMenuView extends FrameLayout implements TvPipActionsProvider.Listener,
        TvPipMenuEduTextDrawer.Listener {
    private static final String TAG = "TvPipMenuView";

    private final TvPipMenuView.Listener mListener;

    private final TvPipActionsProvider mTvPipActionsProvider;

    private final RecyclerView mActionButtonsRecyclerView;
    private final LinearLayoutManager mButtonLayoutManager;
    private final RecyclerViewAdapter mRecyclerViewAdapter;

    private final View mPipFrameView;
    private final View mMenuFrameView;
    private final View mPipView;

    private final View mPipBackground;
    private final View mDimLayer;

    private final TvPipMenuEduTextDrawer mEduTextDrawer;
    private final ViewGroup mEduTextContainer;

    private final int mPipMenuOuterSpace;
    private final int mPipMenuBorderWidth;

    private final int mButtonStartEndOffset;

    private final int mPipMenuFadeAnimationDuration;
    private final int mResizeAnimationDuration;

    private final ImageView mArrowUp;
    private final ImageView mArrowRight;
    private final ImageView mArrowDown;
    private final ImageView mArrowLeft;
    private final TvWindowMenuActionButton mA11yDoneButton;

    private final int mArrowElevation;

    private @TvPipMenuController.TvPipMenuMode int mCurrentMenuMode = MODE_NO_MENU;
    private final Rect mCurrentPipBounds = new Rect();
    private int mCurrentPipGravity;

    private final AccessibilityManager mA11yManager;
    private final Handler mMainHandler;

    public TvPipMenuView(@NonNull Context context, @NonNull Handler mainHandler,
            @NonNull Listener listener, TvPipActionsProvider tvPipActionsProvider) {
        super(context, null, 0, 0);
        inflate(context, R.layout.tv_pip_menu, this);

        mMainHandler = mainHandler;
        mListener = listener;
        mA11yManager = context.getSystemService(AccessibilityManager.class);

        mActionButtonsRecyclerView = findViewById(R.id.tv_pip_menu_action_buttons);
        mButtonLayoutManager = new LinearLayoutManager(mContext);
        mActionButtonsRecyclerView.setLayoutManager(mButtonLayoutManager);
        mActionButtonsRecyclerView.setPreserveFocusAfterLayout(true);

        mTvPipActionsProvider = tvPipActionsProvider;
        mRecyclerViewAdapter = new RecyclerViewAdapter(tvPipActionsProvider.getActionsList());
        mActionButtonsRecyclerView.setAdapter(mRecyclerViewAdapter);

        tvPipActionsProvider.addListener(this);

        mMenuFrameView = findViewById(R.id.tv_pip_menu_frame);
        mPipFrameView = findViewById(R.id.tv_pip_border);
        mPipView = findViewById(R.id.tv_pip);

        mPipBackground = findViewById(R.id.tv_pip_menu_background);
        mDimLayer = findViewById(R.id.tv_pip_menu_dim_layer);

        mArrowUp = findViewById(R.id.tv_pip_menu_arrow_up);
        mArrowRight = findViewById(R.id.tv_pip_menu_arrow_right);
        mArrowDown = findViewById(R.id.tv_pip_menu_arrow_down);
        mArrowLeft = findViewById(R.id.tv_pip_menu_arrow_left);
        mA11yDoneButton = findViewById(R.id.tv_pip_menu_done_button);

        final Resources res = context.getResources();
        mResizeAnimationDuration = res.getInteger(R.integer.config_pipResizeAnimationDuration);
        mPipMenuFadeAnimationDuration =
                res.getInteger(R.integer.tv_window_menu_fade_animation_duration);
        mPipMenuOuterSpace = res.getDimensionPixelSize(R.dimen.pip_menu_outer_space);
        mPipMenuBorderWidth = res.getDimensionPixelSize(R.dimen.pip_menu_border_width);
        mArrowElevation = res.getDimensionPixelSize(R.dimen.pip_menu_arrow_elevation);
        mButtonStartEndOffset = res.getDimensionPixelSize(R.dimen.pip_menu_button_start_end_offset);

        initMoveArrows();

        mEduTextDrawer = new TvPipMenuEduTextDrawer(mContext, mainHandler, this);
        mEduTextContainer = (ViewGroup) findViewById(R.id.tv_pip_menu_edu_text_container);
        mEduTextContainer.addView(mEduTextDrawer);
    }

    private void initMoveArrows() {
        final int arrowSize =
                mContext.getResources().getDimensionPixelSize(R.dimen.pip_menu_arrow_size);
        final Path arrowPath = createArrowPath(arrowSize);

        final ShapeDrawable arrowDrawable = new ShapeDrawable();
        arrowDrawable.setShape(new PathShape(arrowPath, arrowSize, arrowSize));
        arrowDrawable.setTint(mContext.getResources().getColor(R.color.tv_pip_menu_arrow_color));

        final ViewOutlineProvider arrowOutlineProvider = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setPath(createArrowPath(view.getMeasuredHeight()));
            }
        };

        initArrow(mArrowRight, arrowOutlineProvider, arrowDrawable, 0);
        initArrow(mArrowDown, arrowOutlineProvider, arrowDrawable, 90);
        initArrow(mArrowLeft, arrowOutlineProvider, arrowDrawable, 180);
        initArrow(mArrowUp, arrowOutlineProvider, arrowDrawable, 270);
    }

    /**
     * Creates a Path for a movement arrow in the MODE_MOVE_MENU. The resulting Path is a simple
     * right-pointing triangle with its tip in the center of a size x size square:
     *  _ _ _ _ _
     * |*        |
     * |* *      |
     * |*   *    |
     * |* *      |
     * |* _ _ _ _|
     *
     */
    private Path createArrowPath(int size) {
        final Path triangle = new Path();
        triangle.lineTo(0, size);
        triangle.lineTo(size / 2, size / 2);
        triangle.close();
        return triangle;
    }

    private void initArrow(View v, ViewOutlineProvider arrowOutlineProvider, Drawable arrowDrawable,
            int rotation) {
        v.setOutlineProvider(arrowOutlineProvider);
        v.setBackground(arrowDrawable);
        v.setRotation(rotation);
        v.setElevation(mArrowElevation);
    }

    private void setButtonPadding(boolean vertical) {
        if (vertical) {
            mActionButtonsRecyclerView.setPadding(
                    0, mButtonStartEndOffset, 0, mButtonStartEndOffset);
        } else {
            mActionButtonsRecyclerView.setPadding(
                    mButtonStartEndOffset, 0, mButtonStartEndOffset, 0);
        }
    }

    void onPipTransitionToTargetBoundsStarted(Rect targetBounds) {
        if (targetBounds == null) {
            return;
        }

        // Fade out content by fading in view on top.
        if (mCurrentPipBounds != null) {
            boolean ratioChanged = PipUtils.aspectRatioChanged(
                    mCurrentPipBounds.width() / (float) mCurrentPipBounds.height(),
                    targetBounds.width() / (float) targetBounds.height());
            if (ratioChanged) {
                mPipBackground.animate()
                        .alpha(1f)
                        .setInterpolator(TvPipInterpolators.EXIT)
                        .setDuration(mResizeAnimationDuration / 2)
                        .start();
            }
        }

        // Update buttons.
        final boolean vertical = targetBounds.height() > targetBounds.width();
        final boolean orientationChanged =
                vertical != (mButtonLayoutManager.getOrientation() == LinearLayoutManager.VERTICAL);
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: onPipTransitionToTargetBoundsStarted(), orientation changed %b",
                TAG, orientationChanged);
        if (!orientationChanged) {
            return;
        }

        if (mCurrentMenuMode == MODE_ALL_ACTIONS_MENU) {
            // Fade out while orientation change is ongoing and fade back in once transition is
            // finished.
            mActionButtonsRecyclerView.animate()
                    .alpha(0)
                    .setInterpolator(TvPipInterpolators.EXIT)
                    .setDuration(mResizeAnimationDuration / 2);
        } else {
            mButtonLayoutManager.setOrientation(vertical
                    ? LinearLayoutManager.VERTICAL : LinearLayoutManager.HORIZONTAL);
            setButtonPadding(vertical);
        }
    }

    void onPipTransitionFinished(boolean enterTransition) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: onPipTransitionFinished()", TAG);

        // Fade in content by fading out view on top (faded out at every aspect ratio change).
        mPipBackground.animate()
                .alpha(0f)
                .setDuration(mResizeAnimationDuration / 2)
                .setInterpolator(TvPipInterpolators.ENTER)
                .start();

        if (enterTransition) {
            mEduTextDrawer.init();
        }

        boolean vertical = mCurrentPipBounds.height() > mCurrentPipBounds.width();
        mButtonLayoutManager.setOrientation(
                vertical ? LinearLayoutManager.VERTICAL : LinearLayoutManager.HORIZONTAL);
        setButtonPadding(vertical);
        if (mCurrentMenuMode == MODE_ALL_ACTIONS_MENU
                && mActionButtonsRecyclerView.getAlpha() != 1f) {
            mActionButtonsRecyclerView.animate()
                    .alpha(1)
                    .setInterpolator(TvPipInterpolators.ENTER)
                    .setDuration(mResizeAnimationDuration / 2);
        }
    }

    /**
     * Also updates the button gravity.
     */
    void setPipBounds(Rect updatedPipBounds) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: updateLayout, width: %s, height: %s", TAG, updatedPipBounds.width(),
                updatedPipBounds.height());
        if (updatedPipBounds.equals(mCurrentPipBounds)) return;

        mCurrentPipBounds.set(updatedPipBounds);
        updatePipFrameBounds();
    }

    /**
     * Update mPipFrameView's bounds according to the new pip window bounds. We can't
     * make mPipFrameView match_parent, because the pip menu might contain other content around
     * the pip window (e.g. edu text).
     * TvPipMenuView needs to account for this so that it can draw a white border around the whole
     * pip menu when it gains focus.
     */
    private void updatePipFrameBounds() {
        if (mPipFrameView.getVisibility() == VISIBLE) {
            final ViewGroup.LayoutParams pipFrameParams = mPipFrameView.getLayoutParams();
            if (pipFrameParams != null) {
                pipFrameParams.width = mCurrentPipBounds.width() + 2 * mPipMenuBorderWidth;
                pipFrameParams.height = mCurrentPipBounds.height() + 2 * mPipMenuBorderWidth;
                mPipFrameView.setLayoutParams(pipFrameParams);
            }
        }

        final ViewGroup.LayoutParams pipViewParams = mPipView.getLayoutParams();
        if (pipViewParams != null) {
            pipViewParams.width = mCurrentPipBounds.width();
            pipViewParams.height = mCurrentPipBounds.height();
            mPipView.setLayoutParams(pipViewParams);
        }

        // Keep focused button within the visible area while the PiP is changing size. Otherwise,
        // the button would lose focus which would cause a need for scrolling and re-focusing after
        // the animation finishes, which does not look good.
        View focusedChild = mActionButtonsRecyclerView.getFocusedChild();
        if (focusedChild != null) {
            mActionButtonsRecyclerView.scrollToPosition(
                    mActionButtonsRecyclerView.getChildLayoutPosition(focusedChild));
        }
    }

    Rect getPipMenuContainerBounds(Rect pipBounds) {
        final Rect menuUiBounds = new Rect(pipBounds);
        menuUiBounds.inset(-mPipMenuOuterSpace, -mPipMenuOuterSpace);
        menuUiBounds.bottom += mEduTextDrawer.getEduTextDrawerHeight();
        return menuUiBounds;
    }

    void transitionToMenuMode(int menuMode) {
        switch (menuMode) {
            case MODE_NO_MENU:
                hideAllUserControls();
                break;
            case MODE_MOVE_MENU:
                showMoveMenu();
                break;
            case MODE_ALL_ACTIONS_MENU:
                showAllActionsMenu();
                break;
            default:
                throw new IllegalArgumentException(
                        "Unknown TV PiP menu mode: "
                        + TvPipMenuController.getMenuModeString(mCurrentMenuMode));
        }

        mCurrentMenuMode = menuMode;
    }

    private void showMoveMenu() {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE, "%s: showMoveMenu()", TAG);

        if (mCurrentMenuMode == MODE_MOVE_MENU) return;

        showMovementHints();
        setMenuButtonsVisible(false);
        setFrameHighlighted(true);

        animateAlphaTo(mA11yManager.isEnabled() ? 1f : 0f, mDimLayer);

        mEduTextDrawer.closeIfNeeded();
    }

    void resetMenu() {
        scrollToFirstAction();
    }

    private void showAllActionsMenu() {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: showAllActionsMenu()", TAG);

        if (mCurrentMenuMode == MODE_ALL_ACTIONS_MENU) return;

        setMenuButtonsVisible(true);
        hideMovementHints();
        setFrameHighlighted(true);
        animateAlphaTo(1f, mDimLayer);
        mEduTextDrawer.closeIfNeeded();

        if (mCurrentMenuMode == MODE_MOVE_MENU) {
            refocusButton(mTvPipActionsProvider.getFirstIndexOfAction(ACTION_MOVE));
        }

    }

    private void scrollToFirstAction() {
        // Clearing the focus here is necessary to allow a smooth scroll even if the first action
        // is currently not visible.
        final View focusedChild = mActionButtonsRecyclerView.getFocusedChild();
        if (focusedChild != null) {
            focusedChild.clearFocus();
        }

        mButtonLayoutManager.scrollToPosition(0);
        mActionButtonsRecyclerView.post(() -> refocusButton(0));
    }

    /**
     * @return true if focus was requested, false if focus request could not be carried out due to
     * the view for the position not being available (scrolling beforehand will be necessary).
     */
    private boolean refocusButton(int position) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: refocusButton, position: %d", TAG, position);

        View itemToFocus = mButtonLayoutManager.findViewByPosition(position);
        if (itemToFocus != null) {
            itemToFocus.requestFocus();
            itemToFocus.requestAccessibilityFocus();
        }
        return itemToFocus != null;
    }

    private void hideAllUserControls() {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: hideAllUserControls()", TAG);

        if (mCurrentMenuMode == MODE_NO_MENU) return;

        setMenuButtonsVisible(false);
        hideMovementHints();
        setFrameHighlighted(false);
        animateAlphaTo(0f, mDimLayer);
    }

    void setPipGravity(int gravity) {
        mCurrentPipGravity = gravity;
        if (mCurrentMenuMode == MODE_MOVE_MENU) {
            showMovementHints();
        }
    }

    private void animateAlphaTo(float alpha, View view) {
        if (view.getAlpha() == alpha) {
            return;
        }
        view.animate()
                .alpha(alpha)
                .setInterpolator(alpha == 0f ? TvPipInterpolators.EXIT : TvPipInterpolators.ENTER)
                .setDuration(mPipMenuFadeAnimationDuration)
                .withStartAction(() -> {
                    if (alpha != 0) {
                        view.setVisibility(VISIBLE);
                    }
                })
                .withEndAction(() -> {
                    if (alpha == 0) {
                        view.setVisibility(GONE);
                    }
                });
    }

    @Override
    public void onActionsChanged(int added, int updated, int startIndex) {
        mRecyclerViewAdapter.notifyItemRangeChanged(startIndex, updated);
        if (added > 0) {
            mRecyclerViewAdapter.notifyItemRangeInserted(startIndex + updated, added);
        } else if (added < 0) {
            mRecyclerViewAdapter.notifyItemRangeRemoved(startIndex + updated, -added);
        }
    }

    @Override
    public void onCloseEduTextAnimationStart() {
        mListener.onCloseEduText();
    }

    @Override
    public void onCloseEduTextAnimationEnd() {
        mEduTextDrawer.setVisibility(GONE);
        mPipFrameView.setVisibility(GONE);
        mEduTextContainer.setVisibility(GONE);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == ACTION_UP) {
            if (event.getKeyCode() == KEYCODE_BACK) {
                mListener.onExitCurrentMenuMode();
                return true;
            }
            switch (event.getKeyCode()) {
                case KEYCODE_DPAD_UP:
                case KEYCODE_DPAD_DOWN:
                case KEYCODE_DPAD_LEFT:
                case KEYCODE_DPAD_RIGHT:
                    mListener.onUserInteracting();
                    if (mCurrentMenuMode == MODE_MOVE_MENU && !mA11yManager.isEnabled()) {
                        mListener.onPipMovement(event.getKeyCode());
                        return true;
                    }
                    break;
                case KEYCODE_ENTER:
                case KEYCODE_DPAD_CENTER:
                    mListener.onUserInteracting();
                    if (mCurrentMenuMode == MODE_MOVE_MENU && !mA11yManager.isEnabled()) {
                        mListener.onExitCurrentMenuMode();
                        return true;
                    }
                    break;
                default:
                    // Dispatch key event as normal below
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * Shows user hints for moving the PiP, e.g. arrows.
     */
    public void showMovementHints() {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: showMovementHints(), position: %s", TAG, Gravity.toString(mCurrentPipGravity));
        animateAlphaTo(checkGravity(mCurrentPipGravity, Gravity.BOTTOM) ? 1f : 0f, mArrowUp);
        animateAlphaTo(checkGravity(mCurrentPipGravity, Gravity.TOP) ? 1f : 0f, mArrowDown);
        animateAlphaTo(checkGravity(mCurrentPipGravity, Gravity.RIGHT) ? 1f : 0f, mArrowLeft);
        animateAlphaTo(checkGravity(mCurrentPipGravity, Gravity.LEFT) ? 1f : 0f, mArrowRight);

        boolean a11yEnabled = mA11yManager.isEnabled();
        setArrowA11yEnabled(mArrowUp, a11yEnabled, KEYCODE_DPAD_UP);
        setArrowA11yEnabled(mArrowDown, a11yEnabled, KEYCODE_DPAD_DOWN);
        setArrowA11yEnabled(mArrowLeft, a11yEnabled, KEYCODE_DPAD_LEFT);
        setArrowA11yEnabled(mArrowRight, a11yEnabled, KEYCODE_DPAD_RIGHT);

        animateAlphaTo(a11yEnabled ? 1f : 0f, mA11yDoneButton);
        if (a11yEnabled) {
            mA11yDoneButton.setVisibility(VISIBLE);
            mA11yDoneButton.setOnClickListener(v -> {
                mListener.onExitCurrentMenuMode();
            });
            mA11yDoneButton.requestFocus();
            mA11yDoneButton.requestAccessibilityFocus();
        }
    }

    private void setArrowA11yEnabled(View arrowView, boolean enabled, int keycode) {
        arrowView.setClickable(enabled);
        if (enabled) {
            arrowView.setOnClickListener(v -> {
                mListener.onPipMovement(keycode);
            });
        }
    }

    private boolean checkGravity(int gravity, int feature) {
        return (gravity & feature) == feature;
    }

    /**
     * Hides user hints for moving the PiP, e.g. arrows.
     */
    public void hideMovementHints() {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: hideMovementHints()", TAG);

        if (mCurrentMenuMode != MODE_MOVE_MENU) return;

        animateAlphaTo(0, mArrowUp);
        animateAlphaTo(0, mArrowRight);
        animateAlphaTo(0, mArrowDown);
        animateAlphaTo(0, mArrowLeft);
        animateAlphaTo(0, mA11yDoneButton);
    }

    /**
     * Show or hide the pip buttons menu.
     */
    private void setMenuButtonsVisible(boolean visible) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: showUserActions: %b", TAG, visible);
        animateAlphaTo(visible ? 1 : 0, mActionButtonsRecyclerView);
    }

    private void setFrameHighlighted(boolean highlighted) {
        mMenuFrameView.setActivated(highlighted);
    }

    private class RecyclerViewAdapter extends
            RecyclerView.Adapter<RecyclerViewAdapter.ButtonViewHolder> {

        private final List<TvPipAction> mActionList;

        RecyclerViewAdapter(List<TvPipAction> actionList) {
            this.mActionList = actionList;
        }

        @NonNull
        @Override
        public ButtonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ButtonViewHolder(new TvWindowMenuActionButton(mContext));
        }

        @Override
        public void onBindViewHolder(@NonNull ButtonViewHolder holder, int position) {
            TvPipAction action = mActionList.get(position);
            action.populateButton(holder.mButton, mMainHandler);
        }

        @Override
        public int getItemCount() {
            return mActionList.size();
        }

        private class ButtonViewHolder extends RecyclerView.ViewHolder implements OnClickListener {
            TvWindowMenuActionButton mButton;

            ButtonViewHolder(@NonNull View itemView) {
                super(itemView);
                mButton = (TvWindowMenuActionButton) itemView;
                mButton.setOnClickListener(this);
            }

            @Override
            public void onClick(View v) {
                TvPipAction action = mActionList.get(
                        mActionButtonsRecyclerView.getChildLayoutPosition(v));
                if (action != null) {
                    action.executeAction();
                }
            }
        }
    }

    interface Listener {

        /**
         * Called when any button (that affects the menu) on current menu mode was pressed.
         */
        void onUserInteracting();

        /**
         * Called when a button for exiting the current menu mode was pressed.
         */
        void onExitCurrentMenuMode();

        /**
         * Called when a button to move the PiP in a certain direction, indicated by keycode.
         */
        void onPipMovement(int keycode);

        /**
         *  The edu text closing impacts the size of the Picture-in-Picture window and influences
         *  how it is positioned on the screen.
         */
        void onCloseEduText();
    }
}
