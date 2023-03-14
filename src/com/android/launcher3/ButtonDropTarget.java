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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.InputType;
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
import com.android.launcher3.model.data.ItemInfo;

/**
 * Implements a DropTarget.
 */
public abstract class ButtonDropTarget extends TextView
        implements DropTarget, DragController.DragListener, OnClickListener {

    private static final int[] sTempCords = new int[2];
    private static final int DRAG_VIEW_DROP_DURATION = 285;
    private static final float DRAG_VIEW_HOVER_OVER_OPACITY = 0.65f;
    private static final int MAX_LINES_TEXT_MULTI_LINE = 2;
    private static final int MAX_LINES_TEXT_SINGLE_LINE = 1;

    public static final int TOOLTIP_DEFAULT = 0;
    public static final int TOOLTIP_LEFT = 1;
    public static final int TOOLTIP_RIGHT = 2;

    protected final Launcher mLauncher;

    protected DropTargetBar mDropTargetBar;

    /** Whether this drop target is active for the current drag */
    protected boolean mActive;
    /** Whether an accessible drag is in progress */
    private boolean mAccessibleDrag;
    /** An item must be dragged at least this many pixels before this drop target is enabled. */
    private final int mDragDistanceThreshold;
    /** The size of the drawable shown in the drop target. */
    private final int mDrawableSize;
    /** The padding, in pixels, between the text and drawable. */
    private final int mDrawablePadding;

    protected CharSequence mText;
    protected Drawable mDrawable;
    private boolean mTextVisible = true;
    private boolean mIconVisible = true;
    private boolean mTextMultiLine = true;

    private PopupWindow mToolTip;
    private int mToolTipLocation;

    public ButtonDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ButtonDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLauncher = Launcher.getLauncher(context);

        Resources resources = getResources();
        mDragDistanceThreshold = resources.getDimensionPixelSize(R.dimen.drag_distanceThreshold);
        mDrawableSize = resources.getDimensionPixelSize(R.dimen.drop_target_button_drawable_size);
        mDrawablePadding = resources.getDimensionPixelSize(
                R.dimen.drop_target_button_drawable_padding);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mText = getText();
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
        mDrawable = getContext().getDrawable(resId).mutate();
        mDrawable.setTintList(getTextColors());
        updateIconVisibility();
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
        if (!mAccessibleDrag && !mTextVisible) {
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
                    x = -getMeasuredWidth() - message.getMeasuredWidth() / 2;
                } else {
                    x = getMeasuredWidth() / 2 + message.getMeasuredWidth() / 2;
                }
            }
            mToolTip.showAsDropDown(this, x, y);
        }

        d.dragView.setAlpha(DRAG_VIEW_HOVER_OVER_OPACITY);
        setSelected(true);
        if (d.stateAnnouncer != null) {
            d.stateAnnouncer.cancel();
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
    }

    @Override
    public void onDragOver(DragObject d) {
        // Do nothing
    }

    @Override
    public final void onDragExit(DragObject d) {
        hideTooltip();

        if (!d.dragComplete) {
            d.dragView.setAlpha(1f);
            setSelected(false);
        } else {
            d.dragView.setAlpha(DRAG_VIEW_HOVER_OVER_OPACITY);
        }
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        if (options.isKeyboardDrag) {
            mActive = false;
        } else {
            setupItemInfo(dragObject.dragInfo);
            mActive = supportsDrop(dragObject.dragInfo);
        }
        setVisibility(mActive ? View.VISIBLE : View.GONE);

        mAccessibleDrag = options.isAccessibleDrag;
        setOnClickListener(mAccessibleDrag ? this : null);
    }

    @Override
    public final boolean acceptDrop(DragObject dragObject) {
        return supportsDrop(dragObject.dragInfo);
    }

    /**
     * Setups button for the specified ItemInfo.
     */
    protected abstract void setupItemInfo(ItemInfo info);

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
        setSelected(false);
    }

    /**
     * On drop animate the dropView to the icon.
     */
    @Override
    public void onDrop(final DragObject d, final DragOptions options) {
        if (options.isFlingToDelete) {
            // FlingAnimation handles the animation and then calls completeDrop().
            return;
        }
        final DragLayer dragLayer = mLauncher.getDragLayer();
        final DragView dragView = d.dragView;
        final Rect to = getIconRect(d);
        final float scale = (float) to.width() / dragView.getMeasuredWidth();
        dragView.detachContentView(/* reattachToPreviousParent= */ true);

        mDropTargetBar.deferOnDragEnd();

        Runnable onAnimationEndRunnable = () -> {
            completeDrop(d);
            mDropTargetBar.onDragEnd();
            mLauncher.getStateManager().goToState(NORMAL);
        };

        dragLayer.animateView(d.dragView, to, scale, 0.1f, 0.1f,
                DRAG_VIEW_DROP_DURATION,
                Interpolators.DEACCEL_2, onAnimationEndRunnable,
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
        outRect.bottom += mLauncher.getDeviceProfile().dropTargetDragPaddingPx;

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
        final int bottom = top + height;

        to.set(left, top, right, bottom);

        // Center the destination rect about the trash icon
        final int xOffset = -(viewWidth - width) / 2;
        final int yOffset = -(viewHeight - height) / 2;
        to.offset(xOffset, yOffset);

        return to;
    }

    private void centerIcon() {
        int x = mTextVisible ? 0
                : (getWidth() - getPaddingLeft() - getPaddingRight()) / 2 - mDrawableSize / 2;
        mDrawable.setBounds(x, 0, x + mDrawableSize, mDrawableSize);
    }

    @Override
    public void onClick(View v) {
        mLauncher.getAccessibilityDelegate().handleAccessibleDrop(this, null, null);
    }

    public void setTextVisible(boolean isVisible) {
        CharSequence newText = isVisible ? mText : "";
        if (mTextVisible != isVisible || !TextUtils.equals(newText, getText())) {
            mTextVisible = isVisible;
            setText(newText);
            updateIconVisibility();
        }
    }

    /**
     * Display button text over multiple lines when isMultiLine is true, single line otherwise.
     */
    public void setTextMultiLine(boolean isMultiLine) {
        if (mTextMultiLine != isMultiLine) {
            mTextMultiLine = isMultiLine;
            setSingleLine(!isMultiLine);
            setMaxLines(isMultiLine ? MAX_LINES_TEXT_MULTI_LINE : MAX_LINES_TEXT_SINGLE_LINE);
            int inputType = InputType.TYPE_CLASS_TEXT;
            if (isMultiLine) {
                inputType |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;

            }
            setInputType(inputType);
        }
    }

    protected boolean isTextMultiLine() {
        return mTextMultiLine;
    }

    /**
     * Sets the button icon visible when isVisible is true, hides it otherwise.
     */
    public void setIconVisible(boolean isVisible) {
        if (mIconVisible != isVisible) {
            mIconVisible = isVisible;
            updateIconVisibility();
        }
    }

    private void updateIconVisibility() {
        if (mIconVisible) {
            centerIcon();
        }
        setCompoundDrawablesRelative(mIconVisible ? mDrawable : null, null, null, null);
        setCompoundDrawablePadding(mIconVisible && mTextVisible ? mDrawablePadding : 0);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerIcon();
    }

    public void setToolTipLocation(int location) {
        mToolTipLocation = location;
        hideTooltip();
    }

    /**
     * Returns if the text will be truncated within the provided availableWidth.
     */
    public boolean isTextTruncated(int availableWidth) {
        availableWidth -= getPaddingLeft() + getPaddingRight();
        if (mIconVisible) {
            availableWidth -= mDrawable.getIntrinsicWidth() + getCompoundDrawablePadding();
        }
        if (availableWidth <= 0) {
            return true;
        }
        CharSequence firstLine = TextUtils.ellipsize(mText, getPaint(), availableWidth,
                TextUtils.TruncateAt.END);
        if (!mTextMultiLine) {
            return !TextUtils.equals(mText, firstLine);
        }
        if (TextUtils.equals(mText, firstLine)) {
            // When multi-line is active, if it can display as one line, then text is not truncated.
            return false;
        }
        CharSequence secondLine =
                TextUtils.ellipsize(mText.subSequence(firstLine.length(), mText.length()),
                        getPaint(), availableWidth, TextUtils.TruncateAt.END);
        return !(TextUtils.equals(mText.subSequence(0, firstLine.length()), firstLine)
                && TextUtils.equals(mText.subSequence(firstLine.length(), secondLine.length()),
                secondLine));
    }

    /**
     * Reduce the size of the text until it fits the measured width or reaches a minimum.
     *
     * The minimum size is defined by {@code R.dimen.button_drop_target_min_text_size} and
     * it diminishes by intervals defined by
     * {@code R.dimen.button_drop_target_resize_text_increment}
     * This functionality is very similar to the option
     * {@link TextView#setAutoSizeTextTypeWithDefaults(int)} but can't be used in this view because
     * the layout width is {@code WRAP_CONTENT}.
     *
     * @return The biggest text size in SP that makes the text fit or if the text can't fit returns
     *         the min available value
     */
    public float resizeTextToFit() {
        float minSize = Utilities.pxToSp(getResources()
                .getDimensionPixelSize(R.dimen.button_drop_target_min_text_size));
        float step = Utilities.pxToSp(getResources()
                .getDimensionPixelSize(R.dimen.button_drop_target_resize_text_increment));
        float textSize = Utilities.pxToSp(getTextSize());

        int availableWidth = getMeasuredWidth();
        while (isTextTruncated(availableWidth)) {
            textSize -= step;
            if (textSize < minSize) {
                textSize = minSize;
                setTextSize(textSize);
                break;
            }
            setTextSize(textSize);
        }
        return textSize;
    }
}
