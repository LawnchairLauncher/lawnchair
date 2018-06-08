/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.launcher3;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import static com.android.launcher3.LauncherState.NORMAL;

import android.animation.AnimatorSet;
import android.animation.FloatArrayEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.accessibility.AccessibilityEvent;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.Thunk;

/**
 * Implements a DropTarget.
 */
public abstract class ButtonDropTarget extends TextView
        implements DropTarget, DragController.DragListener, OnClickListener {

    private static final int[] sTempCords = new int[2];
    private static final int DRAG_VIEW_DROP_DURATION = 285;

    public static final int TOOLTIP_DEFAULT = 0;
    public static final int TOOLTIP_LEFT = 1;
    public static final int TOOLTIP_RIGHT = 2;

    protected final Launcher mLauncher;

    private int mBottomDragPadding;
    protected DropTargetBar mDropTargetBar;

    /** Whether this drop target is active for the current drag */
    protected boolean mActive;
    /** Whether an accessible drag is in progress */
    private boolean mAccessibleDrag;
    /** An item must be dragged at least this many pixels before this drop target is enabled. */
    private final int mDragDistanceThreshold;

    /** The paint applied to the drag view on hover */
    protected int mHoverColor = 0;

    protected CharSequence mText;
    protected ColorStateList mOriginalTextColor;
    protected Drawable mDrawable;
    private boolean mTextVisible = true;

    private PopupWindow mToolTip;
    private int mToolTipLocation;

    private AnimatorSet mCurrentColorAnim;
    @Thunk ColorMatrix mSrcFilter, mDstFilter, mCurrentFilter;

    public ButtonDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ButtonDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLauncher = Launcher.getLauncher(context);

        Resources resources = getResources();
        mBottomDragPadding = resources.getDimensionPixelSize(R.dimen.drop_target_drag_padding);
        mDragDistanceThreshold = resources.getDimensionPixelSize(R.dimen.drag_distanceThreshold);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mText = getText();
        mOriginalTextColor = getTextColors();
        setContentDescription(mText);
    }

    protected void updateText(int resId) {
        setText(resId);
        mText = getText();
        setContentDescription(mText);
    }

    protected void setDrawable(int resId) {
        // We do not set the drawable in the xml as that inflates two drawables corresponding to
        // drawableLeft and drawableStart.
        if (mTextVisible) {
            setCompoundDrawablesRelativeWithIntrinsicBounds(resId, 0, 0, 0);
            mDrawable = getCompoundDrawablesRelative()[0];
        } else {
            setCompoundDrawablesRelativeWithIntrinsicBounds(0, resId, 0, 0);
            mDrawable = getCompoundDrawablesRelative()[1];
        }
    }

    public void setDropTargetBar(DropTargetBar dropTargetBar) {
        mDropTargetBar = dropTargetBar;
    }

    private void hideTooltip() {
        if (mToolTip != null) {
            mToolTip.dismiss();
            mToolTip = null;
        }
    }

    @Override
    public final void onDragEnter(DragObject d) {
        if (!d.accessibleDrag && !mTextVisible) {
            // Show tooltip
            hideTooltip();

            TextView message = (TextView) LayoutInflater.from(getContext()).inflate(
                    R.layout.drop_target_tool_tip, null);
            message.setText(mText);

            mToolTip = new PopupWindow(message, WRAP_CONTENT, WRAP_CONTENT);
            int x = 0, y = 0;
            if (mToolTipLocation != TOOLTIP_DEFAULT) {
                y = -getMeasuredHeight();
                message.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
                if (mToolTipLocation == TOOLTIP_LEFT) {
                    x = - getMeasuredWidth() - message.getMeasuredWidth() / 2;
                } else {
                    x = getMeasuredWidth() / 2 + message.getMeasuredWidth() / 2;
                }
            }
            mToolTip.showAsDropDown(this, x, y);
        }

        d.dragView.setColor(mHoverColor);
        animateTextColor(mHoverColor);
        if (d.stateAnnouncer != null) {
            d.stateAnnouncer.cancel();
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
    }

    @Override
    public void onDragOver(DragObject d) {
        // Do nothing
    }

    protected void resetHoverColor() {
        animateTextColor(mOriginalTextColor.getDefaultColor());
    }

    private void animateTextColor(int targetColor) {
        if (mCurrentColorAnim != null) {
            mCurrentColorAnim.cancel();
        }

        mCurrentColorAnim = new AnimatorSet();
        mCurrentColorAnim.setDuration(DragView.COLOR_CHANGE_DURATION);

        if (mSrcFilter == null) {
            mSrcFilter = new ColorMatrix();
            mDstFilter = new ColorMatrix();
            mCurrentFilter = new ColorMatrix();
        }

        int defaultTextColor = mOriginalTextColor.getDefaultColor();
        Themes.setColorChangeOnMatrix(defaultTextColor, getTextColor(), mSrcFilter);
        Themes.setColorChangeOnMatrix(defaultTextColor, targetColor, mDstFilter);

        ValueAnimator anim1 = ValueAnimator.ofObject(
                new FloatArrayEvaluator(mCurrentFilter.getArray()),
                mSrcFilter.getArray(), mDstFilter.getArray());
        anim1.addUpdateListener((anim) -> {
            mDrawable.setColorFilter(new ColorMatrixColorFilter(mCurrentFilter));
            invalidate();
        });

        mCurrentColorAnim.play(anim1);
        mCurrentColorAnim.play(ObjectAnimator.ofArgb(this, "textColor", targetColor));
        mCurrentColorAnim.start();
    }

    @Override
    public final void onDragExit(DragObject d) {
        hideTooltip();

        if (!d.dragComplete) {
            d.dragView.setColor(0);
            resetHoverColor();
        } else {
            // Restore the hover color
            d.dragView.setColor(mHoverColor);
        }
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        mActive = supportsDrop(dragObject.dragInfo);
        mDrawable.setColorFilter(null);
        if (mCurrentColorAnim != null) {
            mCurrentColorAnim.cancel();
            mCurrentColorAnim = null;
        }
        setTextColor(mOriginalTextColor);
        setVisibility(mActive ? View.VISIBLE : View.GONE);

        mAccessibleDrag = options.isAccessibleDrag;
        setOnClickListener(mAccessibleDrag ? this : null);
    }

    @Override
    public final boolean acceptDrop(DragObject dragObject) {
        return supportsDrop(dragObject.dragInfo);
    }

    protected abstract boolean supportsDrop(ItemInfo info);

    public abstract boolean supportsAccessibilityDrop(ItemInfo info, View view);

    @Override
    public boolean isDropEnabled() {
        return mActive && (mAccessibleDrag ||
                mLauncher.getDragController().getDistanceDragged() >= mDragDistanceThreshold);
    }

    @Override
    public void onDragEnd() {
        mActive = false;
        setOnClickListener(null);
    }

    /**
     * On drop animate the dropView to the icon.
     */
    @Override
    public void onDrop(final DragObject d, final DragOptions options) {
        final DragLayer dragLayer = mLauncher.getDragLayer();
        final Rect from = new Rect();
        dragLayer.getViewRectRelativeToSelf(d.dragView, from);

        final Rect to = getIconRect(d);
        final float scale = (float) to.width() / from.width();
        mDropTargetBar.deferOnDragEnd();

        Runnable onAnimationEndRunnable = () -> {
            completeDrop(d);
            mDropTargetBar.onDragEnd();
            mLauncher.getStateManager().goToState(NORMAL);
        };

        dragLayer.animateView(d.dragView, from, to, scale, 1f, 1f, 0.1f, 0.1f,
                DRAG_VIEW_DROP_DURATION,
                Interpolators.DEACCEL_2, Interpolators.LINEAR, onAnimationEndRunnable,
                DragLayer.ANIMATION_END_DISAPPEAR, null);
    }

    public abstract int getAccessibilityAction();

    @Override
    public void prepareAccessibilityDrop() { }

    public abstract void onAccessibilityDrop(View view, ItemInfo item);

    public abstract void completeDrop(DragObject d);

    @Override
    public void getHitRectRelativeToDragLayer(android.graphics.Rect outRect) {
        super.getHitRect(outRect);
        outRect.bottom += mBottomDragPadding;

        sTempCords[0] = sTempCords[1] = 0;
        mLauncher.getDragLayer().getDescendantCoordRelativeToSelf(this, sTempCords);
        outRect.offsetTo(sTempCords[0], sTempCords[1]);
    }

    public Rect getIconRect(DragObject dragObject) {
        int viewWidth = dragObject.dragView.getMeasuredWidth();
        int viewHeight = dragObject.dragView.getMeasuredHeight();
        int drawableWidth = mDrawable.getIntrinsicWidth();
        int drawableHeight = mDrawable.getIntrinsicHeight();
        DragLayer dragLayer = mLauncher.getDragLayer();

        // Find the rect to animate to (the view is center aligned)
        Rect to = new Rect();
        dragLayer.getViewRectRelativeToSelf(this, to);

        final int width = drawableWidth;
        final int height = drawableHeight;

        final int left;
        final int right;

        if (Utilities.isRtl(getResources())) {
            right = to.right - getPaddingRight();
            left = right - width;
        } else {
            left = to.left + getPaddingLeft();
            right = left + width;
        }

        final int top = to.top + (getMeasuredHeight() - height) / 2;
        final int bottom = top +  height;

        to.set(left, top, right, bottom);

        // Center the destination rect about the trash icon
        final int xOffset = -(viewWidth - width) / 2;
        final int yOffset = -(viewHeight - height) / 2;
        to.offset(xOffset, yOffset);

        return to;
    }

    @Override
    public void onClick(View v) {
        mLauncher.getAccessibilityDelegate().handleAccessibleDrop(this, null, null);
    }

    public int getTextColor() {
        return getTextColors().getDefaultColor();
    }

    public void setTextVisible(boolean isVisible) {
        CharSequence newText = isVisible ? mText : "";
        if (mTextVisible != isVisible || !TextUtils.equals(newText, getText())) {
            mTextVisible = isVisible;
            setText(newText);
            if (mTextVisible) {
                setCompoundDrawablesRelativeWithIntrinsicBounds(mDrawable, null, null, null);
            } else {
                setCompoundDrawablesRelativeWithIntrinsicBounds(null, mDrawable, null, null);
            }
        }
    }

    public void setToolTipLocation(int location) {
        mToolTipLocation = location;
        hideTooltip();
    }

    public boolean isTextTruncated(int availableWidth) {
        availableWidth -= (getPaddingLeft() + getPaddingRight() + mDrawable.getIntrinsicWidth()
                + getCompoundDrawablePadding());
        CharSequence displayedText = TextUtils.ellipsize(mText, getPaint(), availableWidth,
                TextUtils.TruncateAt.END);
        return !mText.equals(displayedText);
    }

    public abstract Target getDropTargetForLogging();
}
