/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.views;

import static android.content.Context.ACCESSIBILITY_SERVICE;
import static android.view.MotionEvent.ACTION_DOWN;

import static androidx.core.graphics.ColorUtils.compositeColors;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.anim.Interpolators.ACCEL_DEACCEL;
import static com.android.launcher3.anim.Interpolators.DEACCEL;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.Interpolators.clampToProgress;
import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;
import static com.android.launcher3.util.SystemUiController.UI_STATE_SCRIM_VIEW;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.RectEvaluator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.IntProperty;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityManager.AccessibilityStateChangeListener;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.statemanager.StateManager.StateListener;
import com.android.launcher3.uioverrides.WallpaperColorInfo;
import com.android.launcher3.uioverrides.WallpaperColorInfo.OnChangeListener;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ControlType;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.MultiValueAlpha.AlphaProperty;
import com.android.launcher3.util.Themes;
import com.android.launcher3.widget.WidgetsFullSheet;

import java.util.List;

/**
 * Simple scrim which draws a flat color
 */
public class ScrimView<T extends Launcher> extends View implements Insettable, OnChangeListener,
        AccessibilityStateChangeListener {

    public static final IntProperty<ScrimView> DRAG_HANDLE_ALPHA =
            new IntProperty<ScrimView>("dragHandleAlpha") {

                @Override
                public Integer get(ScrimView scrimView) {
                    return scrimView.mDragHandleAlpha;
                }

                @Override
                public void setValue(ScrimView scrimView, int value) {
                    scrimView.setDragHandleAlpha(value);
                }
            };
    private static final int WALLPAPERS = R.string.wallpaper_button_text;
    private static final int WIDGETS = R.string.widget_button_text;
    private static final int SETTINGS = R.string.settings_button_text;
    private static final int ALPHA_CHANNEL_COUNT = 1;

    private static final long DRAG_HANDLE_BOUNCE_DURATION_MS = 300;
    // How much to delay before repeating the bounce.
    private static final long DRAG_HANDLE_BOUNCE_DELAY_MS = 200;
    // Repeat this many times (i.e. total number of bounces is 1 + this).
    private static final int DRAG_HANDLE_BOUNCE_REPEAT_COUNT = 2;

    private final Rect mTempRect = new Rect();
    private final int[] mTempPos = new int[2];

    protected final T mLauncher;
    private final WallpaperColorInfo mWallpaperColorInfo;
    private final AccessibilityManager mAM;
    protected final int mEndScrim;
    protected final boolean mIsScrimDark;

    private final StateListener<LauncherState> mAccessibilityLauncherStateListener =
            new StateListener<LauncherState>() {
        @Override
        public void onStateTransitionComplete(LauncherState finalState) {
            setImportantForAccessibility(finalState == ALL_APPS
                    ? IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    : IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        }
    };

    protected float mMaxScrimAlpha;

    protected float mProgress = 1;
    protected int mScrimColor;

    protected int mCurrentFlatColor;
    protected int mEndFlatColor;
    protected int mEndFlatColorAlpha;

    protected final Point mDragHandleSize;
    private final int mDragHandleTouchSize;
    private final int mDragHandlePaddingInVerticalBarLayout;
    protected float mDragHandleOffset;
    private final Rect mDragHandleBounds;
    private final RectF mHitRect = new RectF();
    private ObjectAnimator mDragHandleAnim;

    private final MultiValueAlpha mMultiValueAlpha;

    private final AccessibilityHelper mAccessibilityHelper;
    @Nullable
    protected Drawable mDragHandle;

    private int mDragHandleAlpha = 255;

    public ScrimView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mLauncher = Launcher.cast(Launcher.getLauncher(context));
        mWallpaperColorInfo = WallpaperColorInfo.INSTANCE.get(context);
        mEndScrim = Themes.getAttrColor(context, R.attr.allAppsScrimColor);
        mIsScrimDark = ColorUtils.calculateLuminance(mEndScrim) < 0.5f;

        mMaxScrimAlpha = 0.7f;

        Resources res = context.getResources();
        mDragHandleSize = new Point(res.getDimensionPixelSize(R.dimen.vertical_drag_handle_width),
                res.getDimensionPixelSize(R.dimen.vertical_drag_handle_height));
        mDragHandleBounds = new Rect(0, 0, mDragHandleSize.x, mDragHandleSize.y);
        mDragHandleTouchSize = res.getDimensionPixelSize(R.dimen.vertical_drag_handle_touch_size);
        mDragHandlePaddingInVerticalBarLayout = context.getResources()
                .getDimensionPixelSize(R.dimen.vertical_drag_handle_padding_in_vertical_bar_layout);

        mAccessibilityHelper = createAccessibilityHelper();
        ViewCompat.setAccessibilityDelegate(this, mAccessibilityHelper);

        mAM = (AccessibilityManager) context.getSystemService(ACCESSIBILITY_SERVICE);
        setFocusable(false);
        mMultiValueAlpha = new MultiValueAlpha(this, ALPHA_CHANNEL_COUNT);
    }

    public AlphaProperty getAlphaProperty(int index) {
        return mMultiValueAlpha.getProperty(index);
    }

    @NonNull
    protected AccessibilityHelper createAccessibilityHelper() {
        return new AccessibilityHelper();
    }

    @Override
    public void setInsets(Rect insets) {
        updateDragHandleBounds();
        updateDragHandleVisibility();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updateDragHandleBounds();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mWallpaperColorInfo.addOnChangeListener(this);
        onExtractedColorsChanged(mWallpaperColorInfo);

        mAM.addAccessibilityStateChangeListener(this);
        onAccessibilityStateChanged(mAM.isEnabled());
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mWallpaperColorInfo.removeOnChangeListener(this);
        mAM.removeAccessibilityStateChangeListener(this);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public void onExtractedColorsChanged(WallpaperColorInfo wallpaperColorInfo) {
        mScrimColor = wallpaperColorInfo.getMainColor();
        mEndFlatColor = compositeColors(mEndScrim, setColorAlphaBound(
                mScrimColor, Math.round(mMaxScrimAlpha * 255)));
        mEndFlatColorAlpha = Color.alpha(mEndFlatColor);
        updateColors();
        invalidate();
    }

    public void setProgress(float progress) {
        if (mProgress != progress) {
            mProgress = progress;
            stopDragHandleEducationAnim();
            updateColors();
            updateSysUiColors();
            updateDragHandleAlpha();
            invalidate();
        }
    }

    public void reInitUi() { }

    protected void updateColors() {
        mCurrentFlatColor = mProgress >= 1 ? 0 : setColorAlphaBound(
                mEndFlatColor, Math.round((1 - mProgress) * mEndFlatColorAlpha));
    }

    protected void updateSysUiColors() {
        // Use a light system UI (dark icons) if all apps is behind at least half of the
        // status bar.
        boolean forceChange = mProgress <= 0.1f;
        if (forceChange) {
            mLauncher.getSystemUiController().updateUiState(UI_STATE_SCRIM_VIEW, !mIsScrimDark);
        } else {
            mLauncher.getSystemUiController().updateUiState(UI_STATE_SCRIM_VIEW, 0);
        }
    }

    protected void updateDragHandleAlpha() {
        if (mDragHandle != null) {
            mDragHandle.setAlpha(mDragHandleAlpha);
        }
    }

    private void setDragHandleAlpha(int alpha) {
        if (alpha != mDragHandleAlpha) {
            mDragHandleAlpha = alpha;
            if (mDragHandle != null) {
                mDragHandle.setAlpha(mDragHandleAlpha);
                invalidate();
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mCurrentFlatColor != 0) {
            canvas.drawColor(mCurrentFlatColor);
        }
        drawDragHandle(canvas);
    }

    protected void drawDragHandle(Canvas canvas) {
        if (mDragHandle != null) {
            canvas.translate(0, -mDragHandleOffset);
            mDragHandle.draw(canvas);
            canvas.translate(0, mDragHandleOffset);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean superHandledTouch = super.onTouchEvent(event);
        if (event.getAction() == ACTION_DOWN) {
            if (!superHandledTouch && mHitRect.contains(event.getX(), event.getY())) {
                if (startDragHandleEducationAnim()) {
                    return true;
                }
            }
            stopDragHandleEducationAnim();
        }
        return superHandledTouch;
    }

    /**
     * Animates the drag handle to demonstrate how to get to all apps.
     * @return Whether the animation was started (false if drag handle is invisible).
     */
    public boolean startDragHandleEducationAnim() {
        stopDragHandleEducationAnim();

        if (mDragHandle == null || mDragHandle.getAlpha() != 255) {
            return false;
        }

        final Drawable drawable = mDragHandle;
        mDragHandle = null;

        Rect bounds = new Rect(mDragHandleBounds);
        bounds.offset(0, -(int) mDragHandleOffset);
        drawable.setBounds(bounds);

        Rect topBounds = new Rect(bounds);
        topBounds.offset(0, -bounds.height());

        Rect invalidateRegion = new Rect(bounds);
        invalidateRegion.top = topBounds.top;

        final float progressToReachTop = 0.6f;
        Keyframe frameTop = Keyframe.ofObject(progressToReachTop, topBounds);
        frameTop.setInterpolator(DEACCEL);
        Keyframe frameBot = Keyframe.ofObject(1, bounds);
        frameBot.setInterpolator(ACCEL_DEACCEL);
        PropertyValuesHolder holder = PropertyValuesHolder.ofKeyframe("bounds",
                Keyframe.ofObject(0, bounds), frameTop, frameBot);
        holder.setEvaluator(new RectEvaluator());

        mDragHandleAnim = ObjectAnimator.ofPropertyValuesHolder(drawable, holder);
        long totalBounceDuration = DRAG_HANDLE_BOUNCE_DURATION_MS + DRAG_HANDLE_BOUNCE_DELAY_MS;
        // The bounce finishes by this progress, the rest of the duration just delays next bounce.
        float delayStartProgress = 1f - (float) DRAG_HANDLE_BOUNCE_DELAY_MS / totalBounceDuration;
        mDragHandleAnim.addUpdateListener((v) -> invalidate(invalidateRegion));
        mDragHandleAnim.setDuration(totalBounceDuration);
        mDragHandleAnim.setInterpolator(clampToProgress(LINEAR, 0, delayStartProgress));
        mDragHandleAnim.setRepeatCount(DRAG_HANDLE_BOUNCE_REPEAT_COUNT);
        getOverlay().add(drawable);

        mDragHandleAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDragHandleAnim = null;
                getOverlay().remove(drawable);
                updateDragHandleVisibility(drawable);
            }
        });
        mDragHandleAnim.start();
        return true;
    }

    private void stopDragHandleEducationAnim() {
        if (mDragHandleAnim != null) {
            mDragHandleAnim.end();
        }
    }

    protected void updateDragHandleBounds() {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        final int left;
        final int width = getMeasuredWidth();
        final int top = getMeasuredHeight() - mDragHandleSize.y - grid.getInsets().bottom;
        final int topMargin;

        if (grid.isVerticalBarLayout()) {
            topMargin = grid.workspacePadding.bottom + mDragHandlePaddingInVerticalBarLayout;
            if (grid.isSeascape()) {
                left = width - grid.getInsets().right - mDragHandleSize.x
                        - mDragHandlePaddingInVerticalBarLayout;
            } else {
                left = grid.getInsets().left + mDragHandlePaddingInVerticalBarLayout;
            }
        } else {
            left = Math.round((width - mDragHandleSize.x) / 2f);
            topMargin = grid.hotseatBarSizePx;
        }
        mDragHandleBounds.offsetTo(left, top - topMargin);
        mHitRect.set(mDragHandleBounds);
        // Inset outwards to increase touch size.
        mHitRect.inset((mDragHandleSize.x - mDragHandleTouchSize) / 2f,
                (mDragHandleSize.y - mDragHandleTouchSize) / 2f);

        if (mDragHandle != null) {
            mDragHandle.setBounds(mDragHandleBounds);
        }
    }

    @Override
    public void onAccessibilityStateChanged(boolean enabled) {
        StateManager<LauncherState> stateManager = mLauncher.getStateManager();
        stateManager.removeStateListener(mAccessibilityLauncherStateListener);

        if (enabled) {
            stateManager.addStateListener(mAccessibilityLauncherStateListener);
            mAccessibilityLauncherStateListener.onStateTransitionComplete(stateManager.getState());
        } else {
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        updateDragHandleVisibility();
    }

    public void updateDragHandleVisibility() {
        updateDragHandleVisibility(null);
    }

    private void updateDragHandleVisibility(@Nullable Drawable recycle) {
        boolean visible = shouldDragHandleBeVisible();
        boolean wasVisible = mDragHandle != null;
        if (visible != wasVisible) {
            if (visible) {
                mDragHandle = recycle != null ? recycle :
                        mLauncher.getDrawable(R.drawable.drag_handle_indicator_shadow);
                mDragHandle.setBounds(mDragHandleBounds);

                updateDragHandleAlpha();
            } else {
                mDragHandle = null;
            }
            invalidate();
        }
    }

    protected boolean shouldDragHandleBeVisible() {
        return mLauncher.getDeviceProfile().isVerticalBarLayout() || mAM.isEnabled();
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        return mAccessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mAccessibilityHelper.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Override
    public void onFocusChanged(boolean gainFocus, int direction,
            Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        mAccessibilityHelper.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
    }

    protected class AccessibilityHelper extends ExploreByTouchHelper {

        private static final int DRAG_HANDLE_ID = 1;

        public AccessibilityHelper() {
            super(ScrimView.this);
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {
            return  mHitRect.contains((int) x, (int) y)
                    ? DRAG_HANDLE_ID : INVALID_ID;
        }

        @Override
        protected void getVisibleVirtualViews(List<Integer> virtualViewIds) {
            virtualViewIds.add(DRAG_HANDLE_ID);
        }

        @Override
        protected void onPopulateNodeForVirtualView(int virtualViewId,
                AccessibilityNodeInfoCompat node) {
            node.setContentDescription(getContext().getString(R.string.all_apps_button_label));
            node.setBoundsInParent(mDragHandleBounds);

            getLocationOnScreen(mTempPos);
            mTempRect.set(mDragHandleBounds);
            mTempRect.offset(mTempPos[0], mTempPos[1]);
            node.setBoundsInScreen(mTempRect);

            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
            node.setClickable(true);
            node.setFocusable(true);

            if (mLauncher.isInState(NORMAL)) {
                Context context = getContext();
                if (Utilities.isWallpaperAllowed(context)) {
                    node.addAction(
                            new AccessibilityActionCompat(WALLPAPERS, context.getText(WALLPAPERS)));
                }
                node.addAction(new AccessibilityActionCompat(WIDGETS, context.getText(WIDGETS)));
                node.addAction(new AccessibilityActionCompat(SETTINGS, context.getText(SETTINGS)));
            }
        }

        @Override
        protected boolean onPerformActionForVirtualView(
                int virtualViewId, int action, Bundle arguments) {
            if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
                mLauncher.getUserEventDispatcher().logActionOnControl(
                        Action.Touch.TAP, ControlType.ALL_APPS_BUTTON,
                        mLauncher.getStateManager().getState().containerType);
                mLauncher.getStateManager().goToState(ALL_APPS);
                return true;
            } else if (action == WALLPAPERS) {
                return OptionsPopupView.startWallpaperPicker(ScrimView.this);
            } else if (action == WIDGETS) {
                int originalImportanceForAccessibility = getImportantForAccessibility();
                setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                WidgetsFullSheet widgetsFullSheet = OptionsPopupView.openWidgets(mLauncher);
                if (widgetsFullSheet == null) {
                    setImportantForAccessibility(originalImportanceForAccessibility);
                    return false;
                }
                widgetsFullSheet.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View view) {}

                    @Override
                    public void onViewDetachedFromWindow(View view) {
                        setImportantForAccessibility(originalImportanceForAccessibility);
                        widgetsFullSheet.removeOnAttachStateChangeListener(this);
                    }
                });
                return true;
            } else if (action == SETTINGS) {
                return OptionsPopupView.startSettings(ScrimView.this);
            }

            return false;
        }
    }

    /**
     * @return The top of this scrim view, or {@link Float#MAX_VALUE} if there's no distinct top.
     */
    public float getVisualTop() {
        return Float.MAX_VALUE;
    }
}
