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
import android.animation.LayoutTransition;
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
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.ItemInfoWithIcon;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.accessibility.ShortcutMenuAccessibilityDelegate;
import com.android.launcher3.anim.RevealOutlineAnimation;
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider;
import com.android.launcher3.badge.BadgeInfo;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.graphics.TriangleShape;
import com.android.launcher3.logging.LoggerUtils;
import com.android.launcher3.notification.NotificationInfo;
import com.android.launcher3.notification.NotificationItemView;
import com.android.launcher3.notification.NotificationKeyData;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.shortcuts.ShortcutDragPreviewProvider;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Themes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.launcher3.compat.AccessibilityManagerCompat.sendCustomAccessibilityEvent;
import static com.android.launcher3.notification.NotificationMainView.NOTIFICATION_ITEM_INFO;
import static com.android.launcher3.popup.PopupPopulator.MAX_SHORTCUTS;
import static com.android.launcher3.popup.PopupPopulator.MAX_SHORTCUTS_IF_NOTIFICATIONS;
import static com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import static com.android.launcher3.userevent.nano.LauncherLogProto.ItemType;
import static com.android.launcher3.userevent.nano.LauncherLogProto.Target;

/**
 * A container for shortcuts to deep links and notifications associated with an app.
 */
@TargetApi(Build.VERSION_CODES.N)
public class PopupContainerWithArrow extends AbstractFloatingView implements DragSource,
        DragController.DragListener, View.OnLongClickListener,
        View.OnTouchListener {

    private final List<DeepShortcutView> mShortcuts = new ArrayList<>();
    private final PointF mInterceptTouchDown = new PointF();
    private final Rect mTempRect = new Rect();
    private final Point mIconLastTouchPos = new Point();

    private final int mStartDragThreshold;
    private final LayoutInflater mInflater;
    private final float mOutlineRadius;
    private final Launcher mLauncher;
    private final LauncherAccessibilityDelegate mAccessibilityDelegate;
    private final boolean mIsRtl;

    private final int mArrayOffset;
    private final View mArrow;

    private BubbleTextView mOriginalIcon;
    private NotificationItemView mNotificationItemView;

    private ViewGroup mSystemShortcutContainer;

    private boolean mIsLeftAligned;
    protected boolean mIsAboveIcon;
    private int mNumNotifications;
    private int mGravity;

    protected Animator mOpenCloseAnimator;
    protected boolean mDeferContainerRemoval;
    private final Rect mStartRect = new Rect();
    private final Rect mEndRect = new Rect();

    public PopupContainerWithArrow(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mStartDragThreshold = getResources().getDimensionPixelSize(
                R.dimen.deep_shortcuts_start_drag_threshold);
        mInflater = LayoutInflater.from(context);
        mOutlineRadius = getResources().getDimension(R.dimen.bg_round_rect_radius);
        mLauncher = Launcher.getLauncher(context);
        mAccessibilityDelegate = new ShortcutMenuAccessibilityDelegate(mLauncher);
        mIsRtl = Utilities.isRtl(getResources());

        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mOutlineRadius);
            }
        });

        // Initialize arrow view
        final Resources resources = getResources();
        final int arrowWidth = resources.getDimensionPixelSize(R.dimen.popup_arrow_width);
        final int arrowHeight = resources.getDimensionPixelSize(R.dimen.popup_arrow_height);
        mArrow = new View(context);
        mArrow.setLayoutParams(new DragLayer.LayoutParams(arrowWidth, arrowHeight));
        mArrayOffset = resources.getDimensionPixelSize(R.dimen.popup_arrow_vertical_offset);
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

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mInterceptTouchDown.set(ev.getX(), ev.getY());
        }
        if (mNotificationItemView != null
                && mNotificationItemView.onInterceptTouchEvent(ev)) {
            return true;
        }
        // Stop sending touch events to deep shortcut views if user moved beyond touch slop.
        return Math.hypot(mInterceptTouchDown.x - ev.getX(), mInterceptTouchDown.y - ev.getY())
                > ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mNotificationItemView != null) {
            return mNotificationItemView.onTouchEvent(ev);
        }
        return super.onTouchEvent(ev);
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_ACTION_POPUP) != 0;
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

    @Override
    protected void handleClose(boolean animate) {
        if (animate) {
            animateClose();
        } else {
            closeComplete();
        }
    }

    public  <T extends View> T inflateAndAdd(int resId) {
        View view = mInflater.inflate(resId, this, false);
        addView(view);
        return (T) view;
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
        if (!DeepShortcutManager.supportsShortcuts(itemInfo)) {
            return null;
        }

        PopupDataProvider popupDataProvider = launcher.getPopupDataProvider();
        List<String> shortcutIds = popupDataProvider.getShortcutIdsForItem(itemInfo);
        List<NotificationKeyData> notificationKeys = popupDataProvider
                .getNotificationKeysForItem(itemInfo);
        List<SystemShortcut> systemShortcuts = popupDataProvider
                .getEnabledSystemShortcutsForItem(itemInfo);

        final PopupContainerWithArrow container =
                (PopupContainerWithArrow) launcher.getLayoutInflater().inflate(
                        R.layout.popup_container, launcher.getDragLayer(), false);
        container.populateAndShow(icon, shortcutIds, notificationKeys, systemShortcuts);
        return container;
    }

    private void populateAndShow(final BubbleTextView originalIcon, final List<String> shortcutIds,
            final List<NotificationKeyData> notificationKeys, List<SystemShortcut> systemShortcuts) {
        mNumNotifications = notificationKeys.size();

        setVisibility(View.INVISIBLE);
        mLauncher.getDragLayer().addView(this);

        mOriginalIcon = originalIcon;

        // Add views
        if (mNumNotifications > 0) {
            // Add notification entries
            View.inflate(getContext(), R.layout.notification_content, this);
            mNotificationItemView = new NotificationItemView(this);
            if (mNumNotifications == 1) {
                mNotificationItemView.removeFooter();
            }
            updateNotificationHeader();
        }
        int viewsToFlip = getChildCount();
        mSystemShortcutContainer = this;

        if (!shortcutIds.isEmpty()) {
            if (mNotificationItemView != null) {
                mNotificationItemView.addGutter();
            }

            for (int i = shortcutIds.size(); i > 0; i--) {
                mShortcuts.add(inflateAndAdd(R.layout.deep_shortcut));
            }
            updateHiddenShortcuts();

            if (!systemShortcuts.isEmpty()) {
                mSystemShortcutContainer = inflateAndAdd(R.layout.system_shortcut_icons);
                for (SystemShortcut shortcut : systemShortcuts) {
                    View view = mInflater.inflate(R.layout.system_shortcut_icon_only,
                            mSystemShortcutContainer, false);
                    mSystemShortcutContainer.addView(view);
                    initializeSystemShortcut(view, shortcut);
                }
            }
        } else if (!systemShortcuts.isEmpty()) {
            if (mNotificationItemView != null) {
                mNotificationItemView.addGutter();
            }

            for (SystemShortcut shortcut : systemShortcuts) {
                initializeSystemShortcut(inflateAndAdd(R.layout.system_shortcut), shortcut);
            }
        }
        orientAboutIcon();

        boolean reverseOrder = mIsAboveIcon;
        if (reverseOrder) {
            int count = getChildCount();
            ArrayList<View> allViews = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                if (i == viewsToFlip) {
                    Collections.reverse(allViews);
                }
                allViews.add(getChildAt(i));
            }
            Collections.reverse(allViews);
            removeAllViews();
            for (int i = 0; i < count; i++) {
                addView(allViews.get(i));
            }
            if (mNotificationItemView != null) {
                mNotificationItemView.inverseGutterMargin();
            }

            orientAboutIcon();
        }
        updateDividers();

        // Add the arrow.
        final Resources res = getResources();
        final int arrowCenterOffset = res.getDimensionPixelSize(isAlignedWithStart()
                ? R.dimen.popup_arrow_horizontal_center_start
                : R.dimen.popup_arrow_horizontal_center_end);
        final int halfArrowWidth = res.getDimensionPixelSize(R.dimen.popup_arrow_width) / 2;
        mLauncher.getDragLayer().addView(mArrow);
        DragLayer.LayoutParams arrowLp = (DragLayer.LayoutParams) mArrow.getLayoutParams();
        if (mIsLeftAligned) {
            mArrow.setX(getX() + arrowCenterOffset - halfArrowWidth);
        } else {
            mArrow.setX(getX() + getMeasuredWidth() - arrowCenterOffset - halfArrowWidth);
        }

        if (Gravity.isVertical(mGravity)) {
            // This is only true if there wasn't room for the container next to the icon,
            // so we centered it instead. In that case we don't want to show the arrow.
            mArrow.setVisibility(INVISIBLE);
        } else {
            ShapeDrawable arrowDrawable = new ShapeDrawable(TriangleShape.create(
                    arrowLp.width, arrowLp.height, !mIsAboveIcon));
            Paint arrowPaint = arrowDrawable.getPaint();
            arrowPaint.setColor(Themes.getAttrColor(mLauncher, R.attr.popupColorPrimary));
            // The corner path effect won't be reflected in the shadow, but shouldn't be noticeable.
            int radius = getResources().getDimensionPixelSize(R.dimen.popup_arrow_corner_radius);
            arrowPaint.setPathEffect(new CornerPathEffect(radius));
            mArrow.setBackground(arrowDrawable);
            mArrow.setElevation(getElevation());
        }

        mArrow.setPivotX(arrowLp.width / 2);
        mArrow.setPivotY(mIsAboveIcon ? 0 : arrowLp.height);

        animateOpen();

        ItemInfo originalItemInfo = (ItemInfo) originalIcon.getTag();
        int numShortcuts = mShortcuts.size() + systemShortcuts.size();
        if (mNumNotifications == 0) {
            setContentDescription(getContext().getString(R.string.shortcuts_menu_description,
                    numShortcuts, originalIcon.getContentDescription().toString()));
        } else {
            setContentDescription(getContext().getString(
                    R.string.shortcuts_menu_with_notifications_description, numShortcuts,
                    mNumNotifications, originalIcon.getContentDescription().toString()));
        }

        mLauncher.getDragController().addDragListener(this);
        mOriginalIcon.forceHideBadge(true);

        // All views are added. Animate layout from now on.
        setLayoutTransition(new LayoutTransition());

        // Load the shortcuts on a background thread and update the container as it animates.
        final Looper workerLooper = LauncherModel.getWorkerLooper();
        new Handler(workerLooper).postAtFrontOfQueue(PopupPopulator.createUpdateRunnable(
                mLauncher, originalItemInfo, new Handler(Looper.getMainLooper()),
                this, shortcutIds, mShortcuts, notificationKeys));
    }

    protected boolean isAlignedWithStart() {
        return mIsLeftAligned && !mIsRtl || !mIsLeftAligned && mIsRtl;
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
    protected void orientAboutIcon() {
        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        int width = getMeasuredWidth();
        int extraVerticalSpace = mArrow.getLayoutParams().height + mArrayOffset
                + getResources().getDimensionPixelSize(R.dimen.popup_vertical_padding);
        int height = getMeasuredHeight() + extraVerticalSpace;

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
            y = mTempRect.top + mOriginalIcon.getPaddingTop() + iconHeight + extraVerticalSpace;
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
        if (Gravity.isVertical(mGravity)) {
            return;
        }

        DragLayer.LayoutParams lp = (DragLayer.LayoutParams) getLayoutParams();
        DragLayer.LayoutParams arrowLp = (DragLayer.LayoutParams) mArrow.getLayoutParams();
        if (mIsAboveIcon) {
            arrowLp.gravity = lp.gravity = Gravity.BOTTOM;
            lp.bottomMargin =
                    mLauncher.getDragLayer().getHeight() - y - getMeasuredHeight() - insets.top;
            arrowLp.bottomMargin = lp.bottomMargin - arrowLp.height - mArrayOffset - insets.bottom;
        } else {
            arrowLp.gravity = lp.gravity = Gravity.TOP;
            lp.topMargin = y + insets.top;
            arrowLp.topMargin = lp.topMargin - insets.top - arrowLp.height - mArrayOffset;
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        // enforce contained is within screen
        DragLayer dragLayer = mLauncher.getDragLayer();
        if (getTranslationX() + l < 0 || getTranslationX() + r > dragLayer.getWidth()) {
            // If we are still off screen, center horizontally too.
            mGravity |= Gravity.CENTER_HORIZONTAL;
        }

        if (Gravity.isHorizontal(mGravity)) {
            setX(dragLayer.getWidth() / 2 - getMeasuredWidth() / 2);
            mArrow.setVisibility(INVISIBLE);
        }
        if (Gravity.isVertical(mGravity)) {
            setY(dragLayer.getHeight() / 2 - getMeasuredHeight() / 2);
        }
    }

    protected void animateOpen() {
        setVisibility(View.VISIBLE);
        mIsOpen = true;

        final AnimatorSet openAnim = LauncherAnimUtils.createAnimatorSet();
        final Resources res = getResources();
        final long revealDuration = (long) res.getInteger(R.integer.config_popupOpenCloseDuration);
        final TimeInterpolator revealInterpolator = new AccelerateDecelerateInterpolator();

        // Rectangular reveal.
        final ValueAnimator revealAnim = createOpenCloseOutlineProvider()
                .createRevealAnimator(this, false);
        revealAnim.setDuration(revealDuration);
        revealAnim.setInterpolator(revealInterpolator);

        Animator fadeIn = ObjectAnimator.ofFloat(this, ALPHA, 0, 1);
        fadeIn.setDuration(revealDuration);
        fadeIn.setInterpolator(revealInterpolator);
        openAnim.play(fadeIn);

        // Animate the arrow.
        mArrow.setScaleX(0);
        mArrow.setScaleY(0);
        Animator arrowScale = ObjectAnimator.ofFloat(mArrow, LauncherAnimUtils.SCALE_PROPERTY, 1)
                .setDuration(res.getInteger(R.integer.config_popupArrowOpenDuration));

        openAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mOpenCloseAnimator = null;
                sendCustomAccessibilityEvent(
                        PopupContainerWithArrow.this,
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                        getContext().getString(R.string.action_deep_shortcut));
            }
        });

        mOpenCloseAnimator = openAnim;
        openAnim.playSequentially(revealAnim, arrowScale);
        openAnim.start();
    }

    public void applyNotificationInfos(List<NotificationInfo> notificationInfos) {
        mNotificationItemView.applyNotificationInfos(notificationInfos);
    }

    private void updateHiddenShortcuts() {
        int allowedCount = mNotificationItemView != null
                ? MAX_SHORTCUTS_IF_NOTIFICATIONS : MAX_SHORTCUTS;
        int originalHeight = getResources().getDimensionPixelSize(R.dimen.bg_popup_item_height);
        int itemHeight = mNotificationItemView != null ?
                getResources().getDimensionPixelSize(R.dimen.bg_popup_item_condensed_height)
                : originalHeight;
        float iconScale = ((float) itemHeight) / originalHeight;

        int total = mShortcuts.size();
        for (int i = 0; i < total; i++) {
            DeepShortcutView view = mShortcuts.get(i);
            view.setVisibility(i >= allowedCount ? GONE : VISIBLE);
            view.getLayoutParams().height = itemHeight;
            view.getIconView().setScaleX(iconScale);
            view.getIconView().setScaleY(iconScale);
        }
    }

    private void updateDividers() {
        int count = getChildCount();
        DeepShortcutView lastView = null;
        for (int i = 0; i < count; i++) {
            View view = getChildAt(i);
            if (view.getVisibility() == VISIBLE && view instanceof DeepShortcutView) {
                if (lastView != null) {
                    lastView.setDividerVisibility(VISIBLE);
                }
                lastView = (DeepShortcutView) view;
                lastView.setDividerVisibility(INVISIBLE);
            }
        }
    }

    @Override
    protected void onWidgetsBound() {
        ItemInfo itemInfo = (ItemInfo) mOriginalIcon.getTag();
        SystemShortcut widgetInfo = new SystemShortcut.Widgets();
        View.OnClickListener onClickListener = widgetInfo.getOnClickListener(mLauncher, itemInfo);
        View widgetsView = null;
        int count = mSystemShortcutContainer.getChildCount();
        for (int i = 0; i < count; i++) {
            View systemShortcutView = mSystemShortcutContainer.getChildAt(i);
            if (systemShortcutView.getTag() instanceof SystemShortcut.Widgets) {
                widgetsView = systemShortcutView;
                break;
            }
        }

        if (onClickListener != null && widgetsView == null) {
            // We didn't have any widgets cached but now there are some, so enable the shortcut.
            if (mSystemShortcutContainer != this) {
                View view = mInflater.inflate(R.layout.system_shortcut_icon_only,
                        mSystemShortcutContainer, false);
                mSystemShortcutContainer.addView(view);
                initializeSystemShortcut(view, widgetInfo);
            } else {
                // If using the expanded system shortcut (as opposed to just the icon), we need to
                // reopen the container to ensure measurements etc. all work out. While this could
                // be quite janky, in practice the user would typically see a small flicker as the
                // animation restarts partway through, and this is a very rare edge case anyway.
                close(false);
                PopupContainerWithArrow.showForIcon(mOriginalIcon);
            }
        } else if (onClickListener == null && widgetsView != null) {
            // No widgets exist, but we previously added the shortcut so remove it.
            if (mSystemShortcutContainer != this) {
                mSystemShortcutContainer.removeView(widgetsView);
            } else {
                close(false);
                PopupContainerWithArrow.showForIcon(mOriginalIcon);
            }
        }
    }

    private void initializeSystemShortcut(View view, SystemShortcut info) {
        if (view instanceof DeepShortcutView) {
            // Expanded system shortcut, with both icon and text shown on white background.
            final DeepShortcutView shortcutView = (DeepShortcutView) view;
            shortcutView.getIconView().setBackgroundResource(info.iconResId);
            shortcutView.getBubbleText().setText(info.labelResId);
        } else if (view instanceof ImageView) {
            // Only the system shortcut icon shows on a gray background header.
            final ImageView shortcutIcon = (ImageView) view;
            shortcutIcon.setImageResource(info.iconResId);
            shortcutIcon.setContentDescription(getContext().getText(info.labelResId));
        }
        view.setTag(info);
        view.setOnClickListener(info.getOnClickListener(mLauncher,
                (ItemInfo) mOriginalIcon.getTag()));
    }

    protected int getIconHeightForPopupPlacement() {
        return mOriginalIcon.getIcon() != null
                ? mOriginalIcon.getIcon().getBounds().height()
                : mOriginalIcon.getHeight();
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
            public void onPreDragStart(DropTarget.DragObject dragObject) {
                if (mIsAboveIcon) {
                    // Hide only the icon, keep the text visible.
                    mOriginalIcon.setIconVisible(false);
                    mOriginalIcon.setVisibility(VISIBLE);
                } else {
                    // Hide both the icon and text.
                    mOriginalIcon.setVisibility(INVISIBLE);
                }
            }

            @Override
            public void onPreDragEnd(DropTarget.DragObject dragObject, boolean dragStarted) {
                mOriginalIcon.setIconVisible(true);
                if (dragStarted) {
                    // Make sure we keep the original icon hidden while it is being dragged.
                    mOriginalIcon.setVisibility(INVISIBLE);
                } else {
                    mLauncher.getUserEventDispatcher().logDeepShortcutsOpen(mOriginalIcon);
                    if (!mIsAboveIcon) {
                        // Show the icon but keep the text hidden.
                        mOriginalIcon.setVisibility(VISIBLE);
                        mOriginalIcon.setTextVisibility(false);
                    }
                }
            }
        };
    }

    /**
     * Updates the notification header if the original icon's badge updated.
     */
    public void updateNotificationHeader(Set<PackageUserKey> updatedBadges) {
        ItemInfo itemInfo = (ItemInfo) mOriginalIcon.getTag();
        PackageUserKey packageUser = PackageUserKey.fromItemInfo(itemInfo);
        if (updatedBadges.contains(packageUser)) {
            updateNotificationHeader();
        }
    }

    private void updateNotificationHeader() {
        ItemInfoWithIcon itemInfo = (ItemInfoWithIcon) mOriginalIcon.getTag();
        BadgeInfo badgeInfo = mLauncher.getPopupDataProvider().getBadgeInfoForItem(itemInfo);
        if (mNotificationItemView != null && badgeInfo != null) {
            mNotificationItemView.updateHeader(
                    badgeInfo.getNotificationCount(), itemInfo.iconColor);
        }
    }

    public void trimNotifications(Map<PackageUserKey, BadgeInfo> updatedBadges) {
        if (mNotificationItemView == null) {
            return;
        }
        ItemInfo originalInfo = (ItemInfo) mOriginalIcon.getTag();
        BadgeInfo badgeInfo = updatedBadges.get(PackageUserKey.fromItemInfo(originalInfo));
        if (badgeInfo == null || badgeInfo.getNotificationKeys().size() == 0) {
            // No more notifications, remove the notification views and expand all shortcuts.
            mNotificationItemView.removeAllViews();
            mNotificationItemView = null;
            updateHiddenShortcuts();
            updateDividers();
        } else {
            mNotificationItemView.trimNotifications(
                    NotificationKeyData.extractKeysOnly(badgeInfo.getNotificationKeys()));
        }
    }

    @Override
    public void onDropCompleted(View target, DragObject d, boolean success) {  }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        // Either the original icon or one of the shortcuts was dragged.
        // Hide the container, but don't remove it yet because that interferes with touch events.
        mDeferContainerRemoval = true;
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
        if (info == NOTIFICATION_ITEM_INFO) {
            target.itemType = ItemType.NOTIFICATION;
        } else {
            target.itemType = ItemType.DEEPSHORTCUT;
            target.rank = info.rank;
        }
        targetParent.containerType = ContainerType.DEEPSHORTCUTS;
    }

    protected void animateClose() {
        if (!mIsOpen) {
            return;
        }
        mEndRect.setEmpty();
        if (getOutlineProvider() instanceof RevealOutlineAnimation) {
            ((RevealOutlineAnimation) getOutlineProvider()).getOutline(mEndRect);
        }
        if (mOpenCloseAnimator != null) {
            mOpenCloseAnimator.cancel();
        }
        mIsOpen = false;

        final AnimatorSet closeAnim = LauncherAnimUtils.createAnimatorSet();
        // Hide the arrow
        closeAnim.play(ObjectAnimator.ofFloat(mArrow, LauncherAnimUtils.SCALE_PROPERTY, 0));
        closeAnim.play(ObjectAnimator.ofFloat(mArrow, ALPHA, 0));

        // Animate original icon's text back in.
        closeAnim.play(mOriginalIcon.createTextAlphaAnimator(true /* fadeIn */));
        mOriginalIcon.forceHideBadge(false);

        final Resources res = getResources();
        final TimeInterpolator revealInterpolator = new AccelerateDecelerateInterpolator();

        // Rectangular reveal (reversed).
        final ValueAnimator revealAnim = createOpenCloseOutlineProvider()
                .createRevealAnimator(this, true);
        revealAnim.setInterpolator(revealInterpolator);
        closeAnim.play(revealAnim);

        Animator fadeOut = ObjectAnimator.ofFloat(this, ALPHA, 0);
        fadeOut.setInterpolator(revealInterpolator);
        closeAnim.play(fadeOut);
        closeAnim.setDuration((long) res.getInteger(R.integer.config_popupOpenCloseDuration));

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

    private RoundedRectRevealOutlineProvider createOpenCloseOutlineProvider() {
        int arrowCenterX = getResources().getDimensionPixelSize(mIsLeftAligned ^ mIsRtl ?
                R.dimen.popup_arrow_horizontal_center_start:
                R.dimen.popup_arrow_horizontal_center_end);
        if (!mIsLeftAligned) {
            arrowCenterX = getMeasuredWidth() - arrowCenterX;
        }
        int arrowCenterY = mIsAboveIcon ? getMeasuredHeight() : 0;

        mStartRect.set(arrowCenterX, arrowCenterY, arrowCenterX, arrowCenterY);
        if (mEndRect.isEmpty()) {
            mEndRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
        }

        return new RoundedRectRevealOutlineProvider
                (mOutlineRadius, mOutlineRadius, mStartRect, mEndRect);
    }

    /**
     * Closes the popup without animation.
     */
    private void closeComplete() {
        mOriginalIcon.setTextVisibility(mOriginalIcon.shouldTextBeVisible());
        mOriginalIcon.forceHideBadge(false);

        mLauncher.getDragController().removeDragListener(this);
        if (mOpenCloseAnimator != null) {
            mOpenCloseAnimator.cancel();
            mOpenCloseAnimator = null;
        }
        mIsOpen = false;
        mDeferContainerRemoval = false;
        mLauncher.getDragLayer().removeView(this);
        mLauncher.getDragLayer().removeView(mArrow);
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

    @Override
    public boolean onLongClick(View v) {
        if (!ItemLongClickListener.canStartDrag(mLauncher)) return false;
        // Return early if not the correct view
        if (!(v.getParent() instanceof DeepShortcutView)) return false;

        // Long clicked on a shortcut.
        DeepShortcutView sv = (DeepShortcutView) v.getParent();
        sv.setWillDrawIcon(false);

        // Move the icon to align with the center-top of the touch point
        Point iconShift = new Point();
        iconShift.x = mIconLastTouchPos.x - sv.getIconCenter().x;
        iconShift.y = mIconLastTouchPos.y - mLauncher.getDeviceProfile().iconSizePx;

        DragView dv = mLauncher.getWorkspace().beginDragShared(sv.getIconView(),
                this, sv.getFinalInfo(),
                new ShortcutDragPreviewProvider(sv.getIconView(), iconShift), new DragOptions());
        dv.animateShift(-iconShift.x, -iconShift.y);

        // TODO: support dragging from within folder without having to close it
        AbstractFloatingView.closeOpenContainer(mLauncher, AbstractFloatingView.TYPE_FOLDER);
        return false;
    }

    /**
     * Returns a PopupContainerWithArrow which is already open or null
     */
    public static PopupContainerWithArrow getOpen(Launcher launcher) {
        return getOpenView(launcher, TYPE_ACTION_POPUP);
    }
}
