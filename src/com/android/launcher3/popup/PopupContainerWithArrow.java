/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.ShapeDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherViewPropertyAnimator;
import com.android.launcher3.LogAccelerateInterpolator;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.accessibility.ShortcutMenuAccessibilityDelegate;
import com.android.launcher3.badge.BadgeInfo;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.graphics.IconPalette;
import com.android.launcher3.graphics.TriangleShape;
import com.android.launcher3.notification.NotificationItemView;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.shortcuts.ShortcutDragPreviewProvider;
import com.android.launcher3.util.PackageUserKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import static com.android.launcher3.userevent.nano.LauncherLogProto.ItemType;
import static com.android.launcher3.userevent.nano.LauncherLogProto.Target;

/**
 * A container for shortcuts to deep links within apps.
 */
@TargetApi(Build.VERSION_CODES.N)
public class PopupContainerWithArrow extends AbstractFloatingView
        implements View.OnLongClickListener, View.OnTouchListener, DragSource,
        DragController.DragListener {

    private final Point mIconShift = new Point();
    private final Point mIconLastTouchPos = new Point();

    protected final Launcher mLauncher;
    private final int mStartDragThreshold;
    private LauncherAccessibilityDelegate mAccessibilityDelegate;
    private final boolean mIsRtl;

    protected BubbleTextView mOriginalIcon;
    private final Rect mTempRect = new Rect();
    private PointF mInterceptTouchDown = new PointF();
    private boolean mIsLeftAligned;
    protected boolean mIsAboveIcon;
    private View mArrow;

    protected Animator mOpenCloseAnimator;
    private boolean mDeferContainerRemoval;

    public PopupContainerWithArrow(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(context);

        mStartDragThreshold = getResources().getDimensionPixelSize(
                R.dimen.deep_shortcuts_start_drag_threshold);
        // TODO: make sure the delegate works for all items, not just shortcuts.
        mAccessibilityDelegate = new ShortcutMenuAccessibilityDelegate(mLauncher);
        mIsRtl = Utilities.isRtl(getResources());
    }

    public PopupContainerWithArrow(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PopupContainerWithArrow(Context context) {
        this(context, null, 0);
    }

    public LauncherAccessibilityDelegate getAccessibilityDelegate() {
        return mAccessibilityDelegate;
    }

    /**
     * Shows the notifications and deep shortcuts associated with {@param icon}.
     * @return the container if shown or null.
     */
    public static PopupContainerWithArrow showForIcon(BubbleTextView icon) {
        Launcher launcher = Launcher.getLauncher(icon.getContext());
        if (getOpen(launcher) != null) {
            // There is already an items container open, so don't open this one.
            icon.clearFocus();
            return null;
        }
        ItemInfo itemInfo = (ItemInfo) icon.getTag();
        List<String> shortcutIds = launcher.getPopupDataProvider().getShortcutIdsForItem(itemInfo);
        String[] notificationKeys = launcher.getPopupDataProvider()
                .getNotificationKeysForItem(itemInfo);
        if (shortcutIds.size() > 0 || notificationKeys.length > 0) {
            final PopupContainerWithArrow container =
                    (PopupContainerWithArrow) launcher.getLayoutInflater().inflate(
                            R.layout.popup_container, launcher.getDragLayer(), false);
            container.setVisibility(View.INVISIBLE);
            launcher.getDragLayer().addView(container);
            container.populateAndShow(icon, shortcutIds, notificationKeys);
            return container;
        }
        return null;
    }

    public void populateAndShow(final BubbleTextView originalIcon, final List<String> shortcutIds,
            final String[] notificationKeys) {
        final Resources resources = getResources();
        final int arrowWidth = resources.getDimensionPixelSize(R.dimen.deep_shortcuts_arrow_width);
        final int arrowHeight = resources.getDimensionPixelSize(R.dimen.deep_shortcuts_arrow_height);
        final int arrowHorizontalOffset = resources.getDimensionPixelSize(
                R.dimen.deep_shortcuts_arrow_horizontal_offset);
        final int arrowVerticalOffset = resources.getDimensionPixelSize(
                R.dimen.deep_shortcuts_arrow_vertical_offset);

        // Add dummy views first, and populate with real info when ready.
        PopupPopulator.Item[] itemsToPopulate = PopupPopulator
                .getItemsToPopulate(shortcutIds, notificationKeys);
        addDummyViews(originalIcon, itemsToPopulate, notificationKeys.length > 1);

        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        orientAboutIcon(originalIcon, arrowHeight + arrowVerticalOffset);

        boolean reverseOrder = mIsAboveIcon;
        if (reverseOrder) {
            removeAllViews();
            itemsToPopulate = PopupPopulator.reverseItems(itemsToPopulate);
            addDummyViews(originalIcon, itemsToPopulate, notificationKeys.length > 1);

            measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            orientAboutIcon(originalIcon, arrowHeight + arrowVerticalOffset);
        }

        List<DeepShortcutView> shortcutViews = new ArrayList<>();
        NotificationItemView notificationView = null;
        for (int i = 0; i < getChildCount(); i++) {
            View item = getChildAt(i);
            switch (itemsToPopulate[i]) {
                case SHORTCUT:
                    if (reverseOrder) {
                        shortcutViews.add(0, (DeepShortcutView) item);
                    } else {
                        shortcutViews.add((DeepShortcutView) item);
                    }
                    break;
                case NOTIFICATION:
                    notificationView = (NotificationItemView) item;
                    IconPalette iconPalette = originalIcon.getIconPalette();
                    notificationView.applyColors(iconPalette);
                    break;
            }
        }

        // Add the arrow.
        mArrow = addArrowView(arrowHorizontalOffset, arrowVerticalOffset, arrowWidth, arrowHeight);
        mArrow.setPivotX(arrowWidth / 2);
        mArrow.setPivotY(mIsAboveIcon ? 0 : arrowHeight);
        PopupItemView firstItem = getItemViewAt(mIsAboveIcon ? getItemCount() - 1 : 0);
        mArrow.setBackgroundTintList(firstItem.getAttachedArrowColor());

        animateOpen();

        mOriginalIcon = originalIcon;

        mLauncher.getDragController().addDragListener(this);

        // Load the shortcuts on a background thread and update the container as it animates.
        final Looper workerLooper = LauncherModel.getWorkerLooper();
        new Handler(workerLooper).postAtFrontOfQueue(PopupPopulator.createUpdateRunnable(
                mLauncher, (ItemInfo) originalIcon.getTag(), new Handler(Looper.getMainLooper()),
                this, shortcutIds, shortcutViews, notificationKeys, notificationView));
    }

    private void addDummyViews(BubbleTextView originalIcon,
            PopupPopulator.Item[] itemsToPopulate, boolean secondaryNotificationViewHasIcons) {
        final Resources res = getResources();
        final int spacing = res.getDimensionPixelSize(R.dimen.deep_shortcuts_spacing);
        final LayoutInflater inflater = mLauncher.getLayoutInflater();
        int numItems = itemsToPopulate.length;
        for (int i = 0; i < numItems; i++) {
            final PopupItemView item = (PopupItemView) inflater.inflate(
                    itemsToPopulate[i].layoutId, this, false);
            if (itemsToPopulate[i] == PopupPopulator.Item.NOTIFICATION) {
                int secondaryHeight = secondaryNotificationViewHasIcons ?
                        res.getDimensionPixelSize(R.dimen.notification_footer_height) :
                        res.getDimensionPixelSize(R.dimen.notification_footer_collapsed_height);
                item.findViewById(R.id.footer).getLayoutParams().height = secondaryHeight;
            }
            if (i < numItems - 1) {
                ((LayoutParams) item.getLayoutParams()).bottomMargin = spacing;
            }
            item.setAccessibilityDelegate(mAccessibilityDelegate);
            addView(item);
        }
        // TODO: update this, since not all items are shortcuts
        setContentDescription(getContext().getString(R.string.shortcuts_menu_description,
                numItems, originalIcon.getContentDescription().toString()));
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

    private void animateOpen() {
        setVisibility(View.VISIBLE);
        mIsOpen = true;

        final AnimatorSet shortcutAnims = LauncherAnimUtils.createAnimatorSet();
        final int itemCount = getItemCount();

        final long duration = getResources().getInteger(
                R.integer.config_deepShortcutOpenDuration);
        final long arrowScaleDuration = getResources().getInteger(
                R.integer.config_deepShortcutArrowOpenDuration);
        final long arrowScaleDelay = duration - arrowScaleDuration;
        final long stagger = getResources().getInteger(
                R.integer.config_deepShortcutOpenStagger);
        final TimeInterpolator fadeInterpolator = new LogAccelerateInterpolator(100, 0);

        // Animate shortcuts
        DecelerateInterpolator interpolator = new DecelerateInterpolator();
        for (int i = 0; i < itemCount; i++) {
            final PopupItemView popupItemView = getItemViewAt(i);
            popupItemView.setVisibility(INVISIBLE);
            popupItemView.setAlpha(0);

            Animator anim = popupItemView.createOpenAnimation(mIsAboveIcon, mIsLeftAligned);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    popupItemView.setVisibility(VISIBLE);
                }
            });
            anim.setDuration(duration);
            int animationIndex = mIsAboveIcon ? itemCount - i - 1 : i;
            anim.setStartDelay(stagger * animationIndex);
            anim.setInterpolator(interpolator);
            shortcutAnims.play(anim);

            Animator fadeAnim = new LauncherViewPropertyAnimator(popupItemView).alpha(1);
            fadeAnim.setInterpolator(fadeInterpolator);
            // We want the shortcut to be fully opaque before the arrow starts animating.
            fadeAnim.setDuration(arrowScaleDelay);
            shortcutAnims.play(fadeAnim);
        }
        shortcutAnims.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mOpenCloseAnimator = null;
                Utilities.sendCustomAccessibilityEvent(
                        PopupContainerWithArrow.this,
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                        getContext().getString(R.string.action_deep_shortcut));
            }
        });

        // Animate the arrow
        mArrow.setScaleX(0);
        mArrow.setScaleY(0);
        Animator arrowScale = new LauncherViewPropertyAnimator(mArrow).scaleX(1).scaleY(1);
        arrowScale.setStartDelay(arrowScaleDelay);
        arrowScale.setDuration(arrowScaleDuration);
        shortcutAnims.play(arrowScale);

        mOpenCloseAnimator = shortcutAnims;
        shortcutAnims.start();
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
    private void orientAboutIcon(BubbleTextView icon, int arrowHeight) {
        int width = getMeasuredWidth();
        int height = getMeasuredHeight() + arrowHeight;

        DragLayer dragLayer = mLauncher.getDragLayer();
        dragLayer.getDescendantRectRelativeToSelf(icon, mTempRect);
        Rect insets = dragLayer.getInsets();

        // Align left (right in RTL) if there is room.
        int leftAlignedX = mTempRect.left + icon.getPaddingLeft();
        int rightAlignedX = mTempRect.right - width - icon.getPaddingRight();
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
        int iconWidth = icon.getWidth() - icon.getTotalPaddingLeft() - icon.getTotalPaddingRight();
        iconWidth *= icon.getScaleX();
        Resources resources = getResources();
        int xOffset;
        if (isAlignedWithStart()) {
            // Aligning with the shortcut icon.
            int shortcutIconWidth = resources.getDimensionPixelSize(R.dimen.deep_shortcut_icon_size);
            int shortcutPaddingStart = resources.getDimensionPixelSize(
                    R.dimen.deep_shortcut_padding_start);
            xOffset = iconWidth / 2 - shortcutIconWidth / 2 - shortcutPaddingStart;
        } else {
            // Aligning with the drag handle.
            int shortcutDragHandleWidth = resources.getDimensionPixelSize(
                    R.dimen.deep_shortcut_drag_handle_size);
            int shortcutPaddingEnd = resources.getDimensionPixelSize(
                    R.dimen.deep_shortcut_padding_end);
            xOffset = iconWidth / 2 - shortcutDragHandleWidth / 2 - shortcutPaddingEnd;
        }
        x += mIsLeftAligned ? xOffset : -xOffset;

        // Open above icon if there is room.
        int iconHeight = icon.getIcon().getBounds().height();
        int y = mTempRect.top + icon.getPaddingTop() - height;
        mIsAboveIcon = y > dragLayer.getTop() + insets.top;
        if (!mIsAboveIcon) {
            y = mTempRect.top + icon.getPaddingTop() + iconHeight;
        }

        // Insets are added later, so subtract them now.
        if (mIsRtl) {
            x += insets.right;
        } else {
            x -= insets.left;
        }
        y -= insets.top;

        if (y < dragLayer.getTop() || y + height > dragLayer.getBottom()) {
            // The container is opening off the screen, so just center it in the drag layer instead.
            ((FrameLayout.LayoutParams) getLayoutParams()).gravity = Gravity.CENTER_VERTICAL;
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

        if (x < dragLayer.getLeft() || x + width > dragLayer.getRight()) {
            // If we are still off screen, center horizontally too.
            ((FrameLayout.LayoutParams) getLayoutParams()).gravity |= Gravity.CENTER_HORIZONTAL;
        }

        int gravity = ((FrameLayout.LayoutParams) getLayoutParams()).gravity;
        if (!Gravity.isHorizontal(gravity)) {
            setX(x);
        }
        if (!Gravity.isVertical(gravity)) {
            setY(y);
        }
    }

    private boolean isAlignedWithStart() {
        return mIsLeftAligned && !mIsRtl || !mIsLeftAligned && mIsRtl;
    }

    /**
     * Adds an arrow view pointing at the original icon.
     * @param horizontalOffset the horizontal offset of the arrow, so that it
     *                              points at the center of the original icon
     */
    private View addArrowView(int horizontalOffset, int verticalOffset, int width, int height) {
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
        if (Gravity.isVertical(((FrameLayout.LayoutParams) getLayoutParams()).gravity)) {
            // This is only true if there wasn't room for the container next to the icon,
            // so we centered it instead. In that case we don't want to show the arrow.
            arrowView.setVisibility(INVISIBLE);
        } else {
            ShapeDrawable arrowDrawable = new ShapeDrawable(TriangleShape.create(
                    width, height, !mIsAboveIcon));
            arrowDrawable.getPaint().setColor(Color.WHITE);
            arrowView.setBackground(arrowDrawable);
            arrowView.setElevation(getElevation());
        }
        addView(arrowView, mIsAboveIcon ? getChildCount() : 0, layoutParams);
        return arrowView;
    }

    @Override
    public View getExtendedTouchView() {
        return mOriginalIcon;
    }

    /**
     * Determines when the deferred drag should be started.
     *
     * Current behavior:
     * - Start the drag if the touch passes a certain distance from the original touch down.
     */
    public DragOptions.PreDragCondition createPreDragCondition() {
        return new DragOptions.PreDragCondition() {
            @Override
            public boolean shouldStartDrag(double distanceDragged) {
                return distanceDragged > mStartDragThreshold;
            }

            @Override
            public void onPreDragStart() {
                mOriginalIcon.setVisibility(INVISIBLE);
            }

            @Override
            public void onPreDragEnd(boolean dragStarted) {
                if (!dragStarted) {
                    mOriginalIcon.setVisibility(VISIBLE);
                    mLauncher.getUserEventDispatcher().logDeepShortcutsOpen(mOriginalIcon);
                    if (!mIsAboveIcon) {
                        mOriginalIcon.setTextVisibility(false);
                    }
                }
            }
        };
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

    /**
     * We need to handle touch events to prevent them from falling through to the workspace below.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return true;
    }

    @Override
    public boolean onTouch(View v, MotionEvent ev) {
        // Touched a shortcut, update where it was touched so we can drag from there on long click.
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                mIconLastTouchPos.set((int) ev.getX(), (int) ev.getY());
                break;
        }
        return false;
    }

    public boolean onLongClick(View v) {
        // Return early if this is not initiated from a touch or not the correct view
        if (!v.isInTouchMode() || !(v.getParent() instanceof DeepShortcutView)) return false;
        // Return early if global dragging is not enabled
        if (!mLauncher.isDraggingEnabled()) return false;
        // Return early if an item is already being dragged (e.g. when long-pressing two shortcuts)
        if (mLauncher.getDragController().isDragging()) return false;

        // Long clicked on a shortcut.
        mDeferContainerRemoval = true;
        DeepShortcutView sv = (DeepShortcutView) v.getParent();
        sv.setWillDrawIcon(false);

        // Move the icon to align with the center-top of the touch point
        mIconShift.x = mIconLastTouchPos.x - sv.getIconCenter().x;
        mIconShift.y = mIconLastTouchPos.y - mLauncher.getDeviceProfile().iconSizePx;

        DragView dv = mLauncher.getWorkspace().beginDragShared(
                sv.getBubbleText(), this, sv.getFinalInfo(),
                new ShortcutDragPreviewProvider(sv.getIconView(), mIconShift), new DragOptions());
        dv.animateShift(-mIconShift.x, -mIconShift.y);

        // TODO: support dragging from within folder without having to close it
        AbstractFloatingView.closeOpenContainer(mLauncher, AbstractFloatingView.TYPE_FOLDER);
        return false;
    }

    public void trimNotifications(Map<PackageUserKey, BadgeInfo> updatedBadges) {
        final NotificationItemView notificationView = (NotificationItemView) findViewById(R.id.notification_view);
        if (notificationView == null) {
            return;
        }
        ItemInfo originalInfo = (ItemInfo) mOriginalIcon.getTag();
        BadgeInfo badgeInfo = updatedBadges.get(PackageUserKey.fromItemInfo(originalInfo));
        if (badgeInfo == null || badgeInfo.getNotificationCount() == 0) {
            AnimatorSet removeNotification = LauncherAnimUtils.createAnimatorSet();
            final int duration = getResources().getInteger(
                    R.integer.config_removeNotificationViewDuration);
            final int spacing = getResources().getDimensionPixelSize(R.dimen.deep_shortcuts_spacing);
            removeNotification.play(animateTranslationYBy(notificationView.getHeight() + spacing,
                    duration));
            Animator reduceHeight = notificationView.createRemovalAnimation(duration);
            final View removeMarginView = mIsAboveIcon ? getItemViewAt(getItemCount() - 2)
                    : notificationView;
            if (removeMarginView != null) {
                ValueAnimator removeMargin = ValueAnimator.ofFloat(1, 0).setDuration(duration);
                removeMargin.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        ((MarginLayoutParams) removeMarginView.getLayoutParams()).bottomMargin
                                = (int) (spacing * (float) valueAnimator.getAnimatedValue());
                    }
                });
                removeNotification.play(removeMargin);
            }
            removeNotification.play(reduceHeight);
            Animator fade = new LauncherViewPropertyAnimator(notificationView).alpha(0)
                    .setDuration(duration);
            fade.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    removeView(notificationView);
                    if (getItemCount() == 0) {
                        close(false);
                        return;
                    }
                    View firstItem = getItemViewAt(mIsAboveIcon ? getItemCount() - 1 : 0);
                    mArrow.setBackgroundTintList(firstItem.getBackgroundTintList());
                }
            });
            removeNotification.play(fade);
            final long arrowScaleDuration = getResources().getInteger(
                    R.integer.config_deepShortcutArrowOpenDuration);
            Animator hideArrow = new LauncherViewPropertyAnimator(mArrow)
                    .scaleX(0).scaleY(0).setDuration(arrowScaleDuration);
            hideArrow.setStartDelay(0);
            Animator showArrow = new LauncherViewPropertyAnimator(mArrow)
                    .scaleX(1).scaleY(1).setDuration(arrowScaleDuration);
            showArrow.setStartDelay((long) (duration - arrowScaleDuration * 1.5));
            removeNotification.playSequentially(hideArrow, showArrow);
            removeNotification.start();
            return;
        }
        notificationView.trimNotifications(badgeInfo.getNotificationKeys());
    }

    /**
     * Animates the translationY of this container if it is open above the icon.
     * If it is below the icon, the container already shifts up when the height
     * of a child (e.g. NotificationView) changes, so the translation isn't necessary.
     */
    public @Nullable Animator animateTranslationYBy(int translationY, int duration) {
        if (mIsAboveIcon) {
            return new LauncherViewPropertyAnimator(this)
                    .translationY(getTranslationY() + translationY).setDuration(duration);
        }
        return null;
    }

    @Override
    public boolean supportsAppInfoDropTarget() {
        return true;
    }

    @Override
    public boolean supportsDeleteDropTarget() {
        return false;
    }

    @Override
    public float getIntrinsicIconScaleFactor() {
        return 1f;
    }

    @Override
    public void onDropCompleted(View target, DropTarget.DragObject d, boolean isFlingToDelete,
            boolean success) {
        if (!success) {
            d.dragView.remove();
            mLauncher.showWorkspace(true);
            mLauncher.getDropTargetBar().onDragEnd();
        }
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        // Either the original icon or one of the shortcuts was dragged.
        // Hide the container, but don't remove it yet because that interferes with touch events.
        animateClose();
    }

    @Override
    public void onDragEnd() {
        if (!mIsOpen) {
            if (mOpenCloseAnimator != null) {
                // Close animation is running.
                mDeferContainerRemoval = false;
            } else {
                // Close animation is not running.
                if (mDeferContainerRemoval) {
                    closeComplete();
                }
            }
        }
    }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, Target target, Target targetParent) {
        target.itemType = ItemType.DEEPSHORTCUT;
        // TODO: add target.rank
        targetParent.containerType = ContainerType.DEEPSHORTCUTS;
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
        if (mOpenCloseAnimator != null) {
            mOpenCloseAnimator.cancel();
        }
        mIsOpen = false;

        final AnimatorSet shortcutAnims = LauncherAnimUtils.createAnimatorSet();
        final int itemCount = getItemCount();
        int numOpenShortcuts = 0;
        for (int i = 0; i < itemCount; i++) {
            if (getItemViewAt(i).isOpenOrOpening()) {
                numOpenShortcuts++;
            }
        }
        final long duration = getResources().getInteger(
                R.integer.config_deepShortcutCloseDuration);
        final long arrowScaleDuration = getResources().getInteger(
                R.integer.config_deepShortcutArrowOpenDuration);
        final long stagger = getResources().getInteger(
                R.integer.config_deepShortcutCloseStagger);
        final TimeInterpolator fadeInterpolator = new LogAccelerateInterpolator(100, 0);

        int firstOpenItemIndex = mIsAboveIcon ? itemCount - numOpenShortcuts : 0;
        for (int i = firstOpenItemIndex; i < firstOpenItemIndex + numOpenShortcuts; i++) {
            final PopupItemView view = getItemViewAt(i);
            Animator anim;
            if (view.willDrawIcon()) {
                anim = view.createCloseAnimation(mIsAboveIcon, mIsLeftAligned, duration);
                int animationIndex = mIsAboveIcon ? i - firstOpenItemIndex
                        : numOpenShortcuts - i - 1;
                anim.setStartDelay(stagger * animationIndex);

                Animator fadeAnim = new LauncherViewPropertyAnimator(view).alpha(0);
                // Don't start fading until the arrow is gone.
                fadeAnim.setStartDelay(stagger * animationIndex + arrowScaleDuration);
                fadeAnim.setDuration(duration - arrowScaleDuration);
                fadeAnim.setInterpolator(fadeInterpolator);
                shortcutAnims.play(fadeAnim);
            } else {
                // The view is being dragged. Animate it such that it collapses with the drag view
                anim = view.collapseToIcon();
                anim.setDuration(DragView.VIEW_ZOOM_DURATION);

                // Scale and translate the view to follow the drag view.
                Point iconCenter = view.getIconCenter();
                view.setPivotX(iconCenter.x);
                view.setPivotY(iconCenter.y);

                float scale = ((float) mLauncher.getDeviceProfile().iconSizePx) / view.getHeight();
                LauncherViewPropertyAnimator anim2 = new LauncherViewPropertyAnimator(view)
                        .scaleX(scale)
                        .scaleY(scale)
                        .translationX(mIconShift.x)
                        .translationY(mIconShift.y);
                anim2.setDuration(DragView.VIEW_ZOOM_DURATION);
                shortcutAnims.play(anim2);
            }
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    view.setVisibility(INVISIBLE);
                }
            });
            shortcutAnims.play(anim);
        }
        Animator arrowAnim = new LauncherViewPropertyAnimator(mArrow)
                .scaleX(0).scaleY(0).setDuration(arrowScaleDuration);
        arrowAnim.setStartDelay(0);
        shortcutAnims.play(arrowAnim);

        shortcutAnims.addListener(new AnimatorListenerAdapter() {
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
        mOpenCloseAnimator = shortcutAnims;
        shortcutAnims.start();
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
        boolean isInHotseat = ((ItemInfo) mOriginalIcon.getTag()).container
                == LauncherSettings.Favorites.CONTAINER_HOTSEAT;
        mOriginalIcon.setTextVisibility(!isInHotseat);
        mLauncher.getDragController().removeDragListener(this);
        mLauncher.getDragLayer().removeView(this);
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_POPUP_CONTAINER_WITH_ARROW) != 0;
    }

    /**
     * Returns a DeepShortcutsContainer which is already open or null
     */
    public static PopupContainerWithArrow getOpen(Launcher launcher) {
        return getOpenView(launcher, TYPE_POPUP_CONTAINER_WITH_ARROW);
    }
}
