/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.popup;

import static com.android.launcher3.popup.PopupPopulator.MAX_SHORTCUTS_IF_NOTIFICATIONS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.CornerPathEffect;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.ShapeDrawable;
import android.os.Build;
import android.support.annotation.IntDef;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.accessibility.ShortcutMenuAccessibilityDelegate;
import com.android.launcher3.anim.PropertyListBuilder;
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.graphics.TriangleShape;
import com.android.launcher3.logging.LoggerUtils;
import com.android.launcher3.notification.NotificationItemView;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.shortcuts.ShortcutsItemView;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Themes;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Set;

/**
 * Base popup container for showing shortcuts to deep links within apps.
 */
@TargetApi(Build.VERSION_CODES.N)
public class BaseActionPopup<V extends TextView> extends AbstractFloatingView {

    public static final int ROUNDED_TOP_CORNERS = 1 << 0;
    public static final int ROUNDED_BOTTOM_CORNERS = 1 << 1;

    @IntDef(flag = true, value = {
            ROUNDED_TOP_CORNERS,
            ROUNDED_BOTTOM_CORNERS
    })
    @Retention(RetentionPolicy.SOURCE)
    public  @interface RoundedCornerFlags {}

    protected final Launcher mLauncher;
    protected final LauncherAccessibilityDelegate mAccessibilityDelegate;
    private final boolean mIsRtl;

    public ShortcutsItemView mShortcutsItemView;

    protected V mOriginalIcon;
    private final Rect mTempRect = new Rect();
    private PointF mInterceptTouchDown = new PointF();
    private boolean mIsLeftAligned;
    protected boolean mIsAboveIcon;
    protected View mArrow;
    private int mGravity;

    protected Animator mOpenCloseAnimator;
    protected boolean mDeferContainerRemoval;
    private final Rect mStartRect = new Rect();
    private final Rect mEndRect = new Rect();

    public BaseActionPopup(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(context);

        mAccessibilityDelegate = new ShortcutMenuAccessibilityDelegate(mLauncher);
        mIsRtl = Utilities.isRtl(getResources());
    }

    public BaseActionPopup(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaseActionPopup(Context context) {
        this(context, null, 0);
    }

    public LauncherAccessibilityDelegate getAccessibilityDelegate() {
        return mAccessibilityDelegate;
    }

    protected PopupItemView getItemViewAt(int index) {
        if (!mIsAboveIcon) {
            // Opening down, so arrow is the first view.
            index++;
        }
        return (PopupItemView) getChildAt(index);
    }

    protected int getItemCount() {
        // All children except the arrow are items.
        return getChildCount() - 1;
    }

    protected void animateOpen() {
        setVisibility(View.VISIBLE);
        mIsOpen = true;

        final AnimatorSet openAnim = LauncherAnimUtils.createAnimatorSet();
        final Resources res = getResources();
        final long revealDuration = (long) res.getInteger(R.integer.config_popupOpenCloseDuration);
        final TimeInterpolator revealInterpolator = new AccelerateDecelerateInterpolator();

        // Rectangular reveal.
        int itemsTotalHeight = 0;
        for (int i = 0; i < getItemCount(); i++) {
            itemsTotalHeight += getItemViewAt(i).getMeasuredHeight();
        }
        Point startPoint = computeAnimStartPoint(itemsTotalHeight);
        int top = mIsAboveIcon ? getPaddingTop() : startPoint.y;
        float radius = getItemViewAt(0).getBackgroundRadius();
        mStartRect.set(startPoint.x, startPoint.y, startPoint.x, startPoint.y);
        mEndRect.set(0, top, getMeasuredWidth(), top + itemsTotalHeight);
        final ValueAnimator revealAnim = new RoundedRectRevealOutlineProvider
                (radius, radius, mStartRect, mEndRect).createRevealAnimator(this, false);
        revealAnim.setDuration(revealDuration);
        revealAnim.setInterpolator(revealInterpolator);

        Animator fadeIn = ObjectAnimator.ofFloat(this, ALPHA, 0, 1);
        fadeIn.setDuration(revealDuration);
        fadeIn.setInterpolator(revealInterpolator);
        openAnim.play(fadeIn);

        // Animate the arrow.
        mArrow.setScaleX(0);
        mArrow.setScaleY(0);
        Animator arrowScale = createArrowScaleAnim(1).setDuration(res.getInteger(
                R.integer.config_popupArrowOpenDuration));

        openAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mOpenCloseAnimator = null;
                Utilities.sendCustomAccessibilityEvent(
                        BaseActionPopup.this,
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                        getContext().getString(R.string.action_deep_shortcut));
            }
        });

        mOpenCloseAnimator = openAnim;
        openAnim.playSequentially(revealAnim, arrowScale);
        openAnim.start();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        enforceContainedWithinScreen(l, r);
    }

    private void enforceContainedWithinScreen(int left, int right) {
        DragLayer dragLayer = mLauncher.getDragLayer();
        if (getTranslationX() + left < 0 ||
                getTranslationX() + right > dragLayer.getWidth()) {
            // If we are still off screen, center horizontally too.
            mGravity |= Gravity.CENTER_HORIZONTAL;
        }

        if (Gravity.isHorizontal(mGravity)) {
            setX(dragLayer.getWidth() / 2 - getMeasuredWidth() / 2);
        }
        if (Gravity.isVertical(mGravity)) {
            setY(dragLayer.getHeight() / 2 - getMeasuredHeight() / 2);
        }
    }

    /**
     * Returns the point at which the center of the arrow merges with the first popup item.
     */
    private Point computeAnimStartPoint(int itemsTotalHeight) {
        int arrowCenterX = getResources().getDimensionPixelSize(mIsLeftAligned ^ mIsRtl ?
                R.dimen.popup_arrow_horizontal_center_start:
                R.dimen.popup_arrow_horizontal_center_end);
        if (!mIsLeftAligned) {
            arrowCenterX = getMeasuredWidth() - arrowCenterX;
        }
        int arrowHeight = getMeasuredHeight() - getPaddingTop() - getPaddingBottom()
                - itemsTotalHeight;
        // The y-coordinate of edge between the arrow and the first popup item.
        int arrowEdge = getPaddingTop() + (mIsAboveIcon ? itemsTotalHeight : arrowHeight);
        return new Point(arrowCenterX, arrowEdge);
    }

    /**
     * Orients this container above or below the given icon, aligning with the left or right.
     *
     * These are the preferred orientations, in order (RTL prefers right-aligned over left):
     * - Above and left-aligned
     * - Above and right-aligned
     * - Below and left-aligned
     * - Below and right-aligned
     *
     * So we always align left if there is enough horizontal space
     * and align above if there is enough vertical space.
     */
    protected void orientAboutIcon(int arrowHeight) {
        int width = getMeasuredWidth();
        int height = getMeasuredHeight() + arrowHeight;

        DragLayer dragLayer = mLauncher.getDragLayer();
        dragLayer.getDescendantRectRelativeToSelf(mOriginalIcon, mTempRect);
        Rect insets = dragLayer.getInsets();

        // Align left (right in RTL) if there is room.
        int leftAlignedX = mTempRect.left + mOriginalIcon.getPaddingLeft();
        int rightAlignedX = mTempRect.right - width - mOriginalIcon.getPaddingRight();
        int x = leftAlignedX;
        boolean canBeLeftAligned = leftAlignedX + width + insets.left
                < dragLayer.getRight() - insets.right;
        boolean canBeRightAligned = rightAlignedX > dragLayer.getLeft() + insets.left;
        if (!canBeLeftAligned || (mIsRtl && canBeRightAligned)) {
            x = rightAlignedX;
        }
        mIsLeftAligned = x == leftAlignedX;
        if (mIsRtl) {
            x -= dragLayer.getWidth() - width;
        }

        // Offset x so that the arrow and shortcut icons are center-aligned with the original icon.
        int iconWidth = mOriginalIcon.getWidth()
                - mOriginalIcon.getTotalPaddingLeft() - mOriginalIcon.getTotalPaddingRight();
        iconWidth *= mOriginalIcon.getScaleX();
        Resources resources = getResources();
        int xOffset;
        if (isAlignedWithStart()) {
            // Aligning with the shortcut icon.
            int shortcutIconWidth = resources.getDimensionPixelSize(R.dimen.deep_shortcut_icon_size);
            int shortcutPaddingStart = resources.getDimensionPixelSize(
                    R.dimen.popup_padding_start);
            xOffset = iconWidth / 2 - shortcutIconWidth / 2 - shortcutPaddingStart;
        } else {
            // Aligning with the drag handle.
            int shortcutDragHandleWidth = resources.getDimensionPixelSize(
                    R.dimen.deep_shortcut_drag_handle_size);
            int shortcutPaddingEnd = resources.getDimensionPixelSize(
                    R.dimen.popup_padding_end);
            xOffset = iconWidth / 2 - shortcutDragHandleWidth / 2 - shortcutPaddingEnd;
        }
        x += mIsLeftAligned ? xOffset : -xOffset;

        // Open above icon if there is room.
        int iconHeight = getIconHeightForPopupPlacement();
        int y = mTempRect.top + mOriginalIcon.getPaddingTop() - height;
        mIsAboveIcon = y > dragLayer.getTop() + insets.top;
        if (!mIsAboveIcon) {
            y = mTempRect.top + mOriginalIcon.getPaddingTop() + iconHeight;
        }

        // Insets are added later, so subtract them now.
        if (mIsRtl) {
            x += insets.right;
        } else {
            x -= insets.left;
        }
        y -= insets.top;

        mGravity = 0;
        if (y + height > dragLayer.getBottom() - insets.bottom) {
            // The container is opening off the screen, so just center it in the drag layer instead.
            mGravity = Gravity.CENTER_VERTICAL;
            // Put the container next to the icon, preferring the right side in ltr (left in rtl).
            int rightSide = leftAlignedX + iconWidth - insets.left;
            int leftSide = rightAlignedX - iconWidth - insets.left;
            if (!mIsRtl) {
                if (rightSide + width < dragLayer.getRight()) {
                    x = rightSide;
                    mIsLeftAligned = true;
                } else {
                    x = leftSide;
                    mIsLeftAligned = false;
                }
            } else {
                if (leftSide > dragLayer.getLeft()) {
                    x = leftSide;
                    mIsLeftAligned = false;
                } else {
                    x = rightSide;
                    mIsLeftAligned = true;
                }
            }
            mIsAboveIcon = true;
        }

        setX(x);
        setY(y);
    }

    protected int getIconHeightForPopupPlacement() {
        return mOriginalIcon.getHeight();
    }

    protected boolean isAlignedWithStart() {
        return mIsLeftAligned && !mIsRtl || !mIsLeftAligned && mIsRtl;
    }

    /**
     * Adds an arrow view pointing at the original icon.
     * @param horizontalOffset the horizontal offset of the arrow, so that it
     *                              points at the center of the original icon
     */
    protected View addArrowView(int horizontalOffset, int verticalOffset, int width, int height) {
        LayoutParams layoutParams = new LayoutParams(width, height);
        if (mIsLeftAligned) {
            layoutParams.gravity = Gravity.LEFT;
            layoutParams.leftMargin = horizontalOffset;
        } else {
            layoutParams.gravity = Gravity.RIGHT;
            layoutParams.rightMargin = horizontalOffset;
        }
        if (mIsAboveIcon) {
            layoutParams.topMargin = verticalOffset;
        } else {
            layoutParams.bottomMargin = verticalOffset;
        }

        View arrowView = new View(getContext());
        if (Gravity.isVertical(mGravity)) {
            // This is only true if there wasn't room for the container next to the icon,
            // so we centered it instead. In that case we don't want to show the arrow.
            arrowView.setVisibility(INVISIBLE);
        } else {
            ShapeDrawable arrowDrawable = new ShapeDrawable(TriangleShape.create(
                    width, height, !mIsAboveIcon));
            Paint arrowPaint = arrowDrawable.getPaint();
            arrowPaint.setColor(Themes.getAttrColor(mLauncher, R.attr.popupColorPrimary));
            // The corner path effect won't be reflected in the shadow, but shouldn't be noticeable.
            int radius = getResources().getDimensionPixelSize(R.dimen.popup_arrow_corner_radius);
            arrowPaint.setPathEffect(new CornerPathEffect(radius));
            arrowView.setBackground(arrowDrawable);
            arrowView.setElevation(getElevation());
        }
        addView(arrowView, mIsAboveIcon ? getChildCount() : 0, layoutParams);
        return arrowView;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mInterceptTouchDown.set(ev.getX(), ev.getY());
            return false;
        }
        // Stop sending touch events to deep shortcut views if user moved beyond touch slop.
        return Math.hypot(mInterceptTouchDown.x - ev.getX(), mInterceptTouchDown.y - ev.getY())
                > ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    protected ObjectAnimator createArrowScaleAnim(float scale) {
        return LauncherAnimUtils.ofPropertyValuesHolder(
                mArrow, new PropertyListBuilder().scale(scale).build());
    }

    @Override
    protected void handleClose(boolean animate) {
        if (animate) {
            animateClose();
        } else {
            closeComplete();
        }
    }

    protected void animateClose() {
        if (!mIsOpen) {
            return;
        }
        mEndRect.setEmpty();
        if (mOpenCloseAnimator != null) {
            Outline outline = new Outline();
            getOutlineProvider().getOutline(this, outline);
            outline.getRect(mEndRect);
            mOpenCloseAnimator.cancel();
        }
        mIsOpen = false;

        final AnimatorSet closeAnim = LauncherAnimUtils.createAnimatorSet();
        prepareCloseAnimator(closeAnim);

        closeAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mOpenCloseAnimator = null;
                if (mDeferContainerRemoval) {
                    setVisibility(INVISIBLE);
                } else {
                    closeComplete();
                }
            }
        });
        mOpenCloseAnimator = closeAnim;
        closeAnim.start();
    }

    protected void prepareCloseAnimator(AnimatorSet closeAnim) {
        final Resources res = getResources();
        final TimeInterpolator revealInterpolator = new AccelerateDecelerateInterpolator();

        // Rectangular reveal (reversed).
        int itemsTotalHeight = 0;
        for (int i = 0; i < getItemCount(); i++) {
            itemsTotalHeight += getItemViewAt(i).getMeasuredHeight();
        }
        Point startPoint = computeAnimStartPoint(itemsTotalHeight);
        int top = mIsAboveIcon ? getPaddingTop() : startPoint.y;
        float radius = getItemViewAt(0).getBackgroundRadius();
        mStartRect.set(startPoint.x, startPoint.y, startPoint.x, startPoint.y);
        if (mEndRect.isEmpty()) {
            mEndRect.set(0, top, getMeasuredWidth(), top + itemsTotalHeight);
        }
        final ValueAnimator revealAnim = new RoundedRectRevealOutlineProvider(
                radius, radius, mStartRect, mEndRect).createRevealAnimator(this, true);
        revealAnim.setInterpolator(revealInterpolator);
        closeAnim.play(revealAnim);

        Animator fadeOut = ObjectAnimator.ofFloat(this, ALPHA, 0);
        fadeOut.setInterpolator(revealInterpolator);
        closeAnim.play(fadeOut);
        closeAnim.setDuration((long) res.getInteger(R.integer.config_popupOpenCloseDuration));
    }

    /**
     * Closes the folder without animation.
     */
    protected void closeComplete() {
        if (mOpenCloseAnimator != null) {
            mOpenCloseAnimator.cancel();
            mOpenCloseAnimator = null;
        }
        mIsOpen = false;
        mDeferContainerRemoval = false;
        mLauncher.getDragLayer().removeView(this);
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_ACTION_POPUP) != 0;
    }

    /**
     * Returns a DeepShortcutsContainer which is already open or null
     */
    public static BaseActionPopup getOpen(Launcher launcher) {
        return getOpenView(launcher, TYPE_ACTION_POPUP);
    }

    @Override
    public void logActionCommand(int command) {
        mLauncher.getUserEventDispatcher().logActionCommand(
                command, mOriginalIcon, ContainerType.DEEPSHORTCUTS);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            DragLayer dl = mLauncher.getDragLayer();
            if (!dl.isEventOverView(this, ev)) {
                mLauncher.getUserEventDispatcher().logActionTapOutside(
                        LoggerUtils.newContainerTarget(ContainerType.DEEPSHORTCUTS));
                close(true);

                // We let touches on the original icon go through so that users can launch
                // the app with one tap if they don't find a shortcut they want.
                return mOriginalIcon == null || !dl.isEventOverView(mOriginalIcon, ev);
            }
        }
        return false;
    }

    public void populateAndShow(V originalIcon, PopupPopulator.Item[] itemsToPopulate) {
        setVisibility(View.INVISIBLE);
        mLauncher.getDragLayer().addView(this);

        final Resources resources = getResources();
        final int arrowWidth = resources.getDimensionPixelSize(R.dimen.popup_arrow_width);
        final int arrowHeight = resources.getDimensionPixelSize(R.dimen.popup_arrow_height);
        final int arrowVerticalOffset = resources.getDimensionPixelSize(
                R.dimen.popup_arrow_vertical_offset);

        mOriginalIcon = originalIcon;

        // Add dummy views first, and populate with real info when ready.
        addDummyViews(itemsToPopulate);

        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        orientAboutIcon(arrowHeight + arrowVerticalOffset);

        boolean reverseOrder = mIsAboveIcon;
        if (reverseOrder) {
            removeAllViews();
            mShortcutsItemView = null;
            itemsToPopulate = PopupPopulator.reverseItems(itemsToPopulate);
            addDummyViews(itemsToPopulate);

            measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            orientAboutIcon(arrowHeight + arrowVerticalOffset);
        }

        // Add the arrow.
        final int arrowHorizontalOffset = resources.getDimensionPixelSize(isAlignedWithStart() ?
                R.dimen.popup_arrow_horizontal_offset_start :
                R.dimen.popup_arrow_horizontal_offset_end);
        mArrow = addArrowView(arrowHorizontalOffset, arrowVerticalOffset, arrowWidth, arrowHeight);
        mArrow.setPivotX(arrowWidth / 2);
        mArrow.setPivotY(mIsAboveIcon ? 0 : arrowHeight);

        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        animateOpen();
    }

    protected void addDummyViews(PopupPopulator.Item[] itemTypesToPopulate) {
        final LayoutInflater inflater = mLauncher.getLayoutInflater();
        int shortcutsItemRoundedCorners = ROUNDED_TOP_CORNERS | ROUNDED_BOTTOM_CORNERS;
        int numItems = itemTypesToPopulate.length;
        for (int i = 0; i < numItems; i++) {
            PopupPopulator.Item itemTypeToPopulate = itemTypesToPopulate[i];
            PopupPopulator.Item prevItemTypeToPopulate =
                    i > 0 ? itemTypesToPopulate[i - 1] : null;
            PopupPopulator.Item nextItemTypeToPopulate =
                    i < numItems - 1 ? itemTypesToPopulate[i + 1] : null;
            final View item = inflater.inflate(itemTypeToPopulate.layoutId, this, false);

            boolean shouldUnroundTopCorners = prevItemTypeToPopulate != null
                    && itemTypeToPopulate.isShortcut ^ prevItemTypeToPopulate.isShortcut;
            boolean shouldUnroundBottomCorners = nextItemTypeToPopulate != null
                    && itemTypeToPopulate.isShortcut ^ nextItemTypeToPopulate.isShortcut;

            onViewInflated(item, itemTypeToPopulate,
                    shouldUnroundTopCorners, shouldUnroundBottomCorners);

            if (itemTypeToPopulate.isShortcut) {
                if (mShortcutsItemView == null) {
                    mShortcutsItemView = (ShortcutsItemView) inflater.inflate(
                            R.layout.shortcuts_item, this, false);
                    addView(mShortcutsItemView);
                    if (shouldUnroundTopCorners) {
                        shortcutsItemRoundedCorners &= ~ROUNDED_TOP_CORNERS;
                    }
                }
                mShortcutsItemView.addShortcutView(item, itemTypeToPopulate);
                if (shouldUnroundBottomCorners) {
                    shortcutsItemRoundedCorners &= ~ROUNDED_BOTTOM_CORNERS;
                }
            } else {
                addView(item);
            }
        }
        int backgroundColor = Themes.getAttrColor(mLauncher, R.attr.popupColorPrimary);
        mShortcutsItemView.setBackgroundWithCorners(backgroundColor, shortcutsItemRoundedCorners);
    }

    protected void onViewInflated(View view, PopupPopulator.Item itemType,
            boolean shouldUnroundTopCorners, boolean shouldUnroundBottomCorners) {

    }
}