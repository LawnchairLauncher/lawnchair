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

package ch.deletescape.lawnchair.popup;

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
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ch.deletescape.lawnchair.AbstractFloatingView;
import ch.deletescape.lawnchair.BubbleTextView;
import ch.deletescape.lawnchair.DragSource;
import ch.deletescape.lawnchair.DropTarget;
import ch.deletescape.lawnchair.FastBitmapDrawable;
import ch.deletescape.lawnchair.ItemInfo;
import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.LauncherAnimUtils;
import ch.deletescape.lawnchair.LauncherModel;
import ch.deletescape.lawnchair.LauncherSettings;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.accessibility.LauncherAccessibilityDelegate;
import ch.deletescape.lawnchair.accessibility.ShortcutMenuAccessibilityDelegate;
import ch.deletescape.lawnchair.allapps.AllAppsIconRowView;
import ch.deletescape.lawnchair.anim.PropertyListBuilder;
import ch.deletescape.lawnchair.anim.PropertyResetListener;
import ch.deletescape.lawnchair.anim.RoundedRectRevealOutlineProvider;
import ch.deletescape.lawnchair.badge.BadgeInfo;
import ch.deletescape.lawnchair.config.FeatureFlags;
import ch.deletescape.lawnchair.dragndrop.DragController;
import ch.deletescape.lawnchair.dragndrop.DragLayer;
import ch.deletescape.lawnchair.dragndrop.DragOptions;
import ch.deletescape.lawnchair.graphics.IconPalette;
import ch.deletescape.lawnchair.graphics.TriangleShape;
import ch.deletescape.lawnchair.notification.NotificationItemView;
import ch.deletescape.lawnchair.notification.NotificationKeyData;
import ch.deletescape.lawnchair.popup.theme.IPopupThemer;
import ch.deletescape.lawnchair.shortcuts.DeepShortcutManager;
import ch.deletescape.lawnchair.shortcuts.DeepShortcutView;
import ch.deletescape.lawnchair.shortcuts.ShortcutsItemView;
import ch.deletescape.lawnchair.util.PackageUserKey;

/**
 * A container for shortcuts to deep links within apps.
 */
@TargetApi(Build.VERSION_CODES.N)
public class PopupContainerWithArrow extends AbstractFloatingView implements DragSource,
        DragController.DragListener {

    protected final Launcher mLauncher;
    private final int mStartDragThreshold;
    private LauncherAccessibilityDelegate mAccessibilityDelegate;
    private final boolean mIsRtl;

    public MainItemView mMainItemView;
    public ShortcutsItemView mShortcutsItemView;
    private NotificationItemView mNotificationItemView;

    protected BubbleTextView mOriginalIcon;
    private final Rect mTempRect = new Rect();
    private PointF mInterceptTouchDown = new PointF();
    private boolean mIsLeftAligned;
    private boolean mIsCenterAligned;
    protected boolean mIsAboveIcon;
    private View mArrow;

    protected Animator mOpenCloseAnimator;
    private boolean mDeferContainerRemoval;
    private AnimatorSet mReduceHeightAnimatorSet;

    private IPopupThemer mTheme;

    private final Rect mStartRect = new Rect();
    private final Rect mEndRect = new Rect();

    public PopupContainerWithArrow(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(context);

        mStartDragThreshold = getResources().getDimensionPixelSize(
                R.dimen.deep_shortcuts_start_drag_threshold);
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
        if (!DeepShortcutManager.supportsEdit(itemInfo)) {
            return null;
        }

        PopupDataProvider popupDataProvider = launcher.getPopupDataProvider();
        List<String> shortcutIds = popupDataProvider.getShortcutIdsForItem(itemInfo);
        List<NotificationKeyData> notificationKeys = popupDataProvider
                .getNotificationKeysForItem(itemInfo);
        List<SystemShortcut> systemShortcuts = popupDataProvider
                .getEnabledSystemShortcutsForItem(itemInfo);

        LayoutInflater layoutInflater =
                LayoutInflater.from(FeatureFlags.INSTANCE.applyDarkTheme(launcher, FeatureFlags.DARK_SHORTCUTS));
        final PopupContainerWithArrow container =
                (PopupContainerWithArrow) layoutInflater.inflate(
                        R.layout.popup_container, launcher.getDragLayer(), false);
        container.setTheme(Utilities.getThemer().popupTheme(launcher));
        container.setVisibility(View.INVISIBLE);
        launcher.getDragLayer().addView(container);
        container.populateAndShow(icon, shortcutIds, notificationKeys, systemShortcuts);
        return container;
    }

    public void setTheme(IPopupThemer theme) {
        mTheme = theme;
    }

    public void populateAndShow(final BubbleTextView originalIcon, final List<String> shortcutIds,
                                final List<NotificationKeyData> notificationKeys, List<SystemShortcut> systemShortcuts) {
        final Resources resources = getResources();
        final int arrowWidth = resources.getDimensionPixelSize(R.dimen.popup_arrow_width);
        final int arrowHeight = resources.getDimensionPixelSize(R.dimen.popup_arrow_height);
        final int arrowVerticalOffset = resources.getDimensionPixelSize(
                R.dimen.popup_arrow_vertical_offset);

        mOriginalIcon = originalIcon;

        // Add dummy views first, and populate with real info when ready.
        PopupPopulator.Item[] itemsToPopulate = PopupPopulator
                .getItemsToPopulate(shortcutIds, notificationKeys, systemShortcuts);
        addDummyViews(itemsToPopulate, notificationKeys.size() > 1);

        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        orientAboutIcon(originalIcon, arrowHeight + arrowVerticalOffset);

        boolean reverseOrder = mIsAboveIcon;
        if (reverseOrder) {
            removeAllViews();
            if (mMainItemView != null) {
                mMainItemView.removeAllViews();
                mMainItemView = null;
            }
            mNotificationItemView = null;
            mShortcutsItemView = null;
            itemsToPopulate = PopupPopulator.reverseItems(itemsToPopulate);
            addDummyViews(itemsToPopulate, notificationKeys.size() > 1);

            measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            orientAboutIcon(originalIcon, arrowHeight + arrowVerticalOffset);
        }

        ItemInfo originalItemInfo = (ItemInfo) originalIcon.getTag();
        List<DeepShortcutView> shortcutViews = mShortcutsItemView == null
                ? Collections.<DeepShortcutView>emptyList()
                : mShortcutsItemView.getDeepShortcutViews(reverseOrder);
        List<View> systemShortcutViews = mShortcutsItemView == null
                ? Collections.<View>emptyList()
                : mShortcutsItemView.getSystemShortcutViews(reverseOrder);
        if (mNotificationItemView != null) {
            updateNotificationHeader();
        }

        int numShortcuts = shortcutViews.size() + systemShortcutViews.size();
        int numNotifications = notificationKeys.size();
        if (originalIcon.getContentDescription() != null) {
            if (numNotifications == 0) {
                setContentDescription(getContext().getString(R.string.shortcuts_menu_description,
                        numShortcuts, originalIcon.getContentDescription().toString()));
            } else {
                setContentDescription(getContext().getString(
                        R.string.shortcuts_menu_with_notifications_description, numShortcuts,
                        numNotifications, originalIcon.getContentDescription().toString()));
            }
        }

        // Add the arrow.
        final int arrowHorizontalOffset = resources.getDimensionPixelSize(isAlignedWithStart() ?
                R.dimen.popup_arrow_horizontal_offset_start :
                R.dimen.popup_arrow_horizontal_offset_end);
        mArrow = addArrowView(arrowHorizontalOffset, arrowVerticalOffset, arrowWidth, arrowHeight);
        mArrow.setPivotX(arrowWidth / 2);
        mArrow.setPivotY(mIsAboveIcon ? 0 : arrowHeight);

        animateOpen();

        mLauncher.getDragController().addDragListener(this);
        mOriginalIcon.forceHideBadge(true);

        // Load the shortcuts on a background thread and update the container as it animates.
        final Looper workerLooper = LauncherModel.getWorkerLooper();
        new Handler(workerLooper).postAtFrontOfQueue(PopupPopulator.createUpdateRunnable(
                mLauncher, originalItemInfo, new Handler(Looper.getMainLooper()),
                this, shortcutIds, shortcutViews, notificationKeys, mNotificationItemView,
                systemShortcuts, systemShortcutViews));
    }

    private void addDummyViews(PopupPopulator.Item[] itemTypesToPopulate,
                               boolean notificationFooterHasIcons) {
        final Resources res = getResources();
        final int spacing = res.getDimensionPixelSize(mTheme.getItemSpacing());
        final LayoutInflater inflater = LayoutInflater.from(getContext());
        int corners = PopupItemView.CORNERS_ALL;
        boolean i4, i5;
        int i6;

        int numItems = itemTypesToPopulate.length;
        for (int i = 0; i < numItems; i++) {
            PopupPopulator.Item itemTypeToPopulate = itemTypesToPopulate[i];
            PopupPopulator.Item previousItemTypeToPopulate =
                    i > 0 ? itemTypesToPopulate[i - 1] : null;
            PopupPopulator.Item nextItemTypeToPopulate =
                    i < numItems - 1 ? itemTypesToPopulate[i + 1] : null;
            final View item = inflater.inflate(itemTypeToPopulate.layoutId, this, false);
            i4 = previousItemTypeToPopulate != null && previousItemTypeToPopulate.isShortcut != itemTypeToPopulate.isShortcut;
            i5 = nextItemTypeToPopulate != null && nextItemTypeToPopulate.isShortcut != itemTypeToPopulate.isShortcut;

            if (itemTypeToPopulate == PopupPopulator.Item.NOTIFICATION) {
                mNotificationItemView = (NotificationItemView) item;
                int footerHeight = notificationFooterHasIcons ?
                        res.getDimensionPixelSize(R.dimen.notification_footer_height) :
                        res.getDimensionPixelSize(R.dimen.notification_empty_footer_height);
                if (notificationFooterHasIcons)
                    mNotificationItemView.findViewById(R.id.divider).setVisibility(VISIBLE);
                item.findViewById(R.id.footer).getLayoutParams().height = footerHeight;
                i6 = PopupItemView.CORNERS_ALL;
                if (i4) {
                    i6 = PopupItemView.CORNERS_BOTTOM;
                    mNotificationItemView.findViewById(R.id.gutter_top).setVisibility(VISIBLE);
                }
                if (i5) {
                    i6 &= -PopupItemView.CORNERS_ALL;
                    mNotificationItemView.findViewById(R.id.gutter_bottom).setVisibility(VISIBLE);
                }
                mNotificationItemView.setBackgroundWithCorners(
                        Utilities.resolveAttributeData(getContext(), R.attr.popupColorTertiary), i6);
                mNotificationItemView.getMainView().setAccessibilityDelegate(mAccessibilityDelegate);
            } else if (itemTypeToPopulate == PopupPopulator.Item.SHORTCUT) {
                item.setAccessibilityDelegate(mAccessibilityDelegate);
            }

            boolean shouldAddBottomMargin = nextItemTypeToPopulate != null
                    && itemTypeToPopulate.isShortcut ^ nextItemTypeToPopulate.isShortcut;

            if (itemTypeToPopulate.isShortcut) {
                if (mShortcutsItemView == null) {
                    mShortcutsItemView = (ShortcutsItemView) inflater.inflate(
                            R.layout.shortcuts_item, this, false);
                    addItem(inflater, mShortcutsItemView);
                    if (i4) {
                        corners &= -PopupItemView.CORNERS_BOTTOM;
                    }
                }
                mShortcutsItemView.addShortcutView(item, itemTypeToPopulate);
                if (shouldAddBottomMargin && spacing != 0) {
                    ((LayoutParams) mShortcutsItemView.getLayoutParams()).bottomMargin = spacing;
                }
                if (i5) {
                    corners &= -PopupItemView.CORNERS_ALL;
                }
            } else {
                addItem(inflater, item);
                ((LayoutParams) item.getLayoutParams()).bottomMargin = spacing;
            }
        }
        mShortcutsItemView.setBackgroundWithCorners(Utilities.resolveAttributeData(getContext(), R.attr.popupColorPrimary), corners);
    }

    private void addItem(LayoutInflater inflater, View item) {
        if (mTheme.getWrapInMain()) {
            if (mMainItemView == null) {
                mMainItemView = (MainItemView) inflater.inflate(
                        R.layout.main_item, this, false);
                addView(mMainItemView);
            }
            mMainItemView.addView(item);
        } else {
            addView(item);
        }
    }

    protected PopupItemView getItemViewAt(int index) {
        if (!mIsAboveIcon) {
            // Opening down, so arrow is the first view.
            index++;
        }
        return (PopupItemView) getChildAt(index);
    }

    protected PopupItemView getItemViewInMainAt(int index) {
        return (PopupItemView) mMainItemView.itemContainer.getChildAt(index);
    }

    protected int getItemCount() {
        // All children except the arrow are items.
        return getChildCount() - 1;
    }

    protected int getItemCountInMain() {
        // All children except the arrow are items.
        return mMainItemView.itemContainer.getChildCount();
    }

    private Point computeAnimStartPoint(int y) {
        int x;
        Resources resources = getResources();
        if (mIsLeftAligned != mIsRtl) {
            x = R.dimen.popup_arrow_horizontal_center_start;
        } else {
            x = R.dimen.popup_arrow_horizontal_center_end;
        }
        x = resources.getDimensionPixelSize(x);
        if (!mIsLeftAligned) {
            x = getMeasuredWidth() - x;
        }
        int measuredHeight = ((getMeasuredHeight() - getPaddingTop()) - getPaddingBottom()) - y;
        int paddingTop = getPaddingTop();
        if (!mIsAboveIcon) {
            y = measuredHeight;
        }
        if (mIsCenterAligned) {
            x = getMeasuredWidth() / 2;
        }
        return new Point(x, paddingTop + y);
    }

    private void animateOpen() {
        setVisibility(View.VISIBLE);
        mIsOpen = true;

        final AnimatorSet shortcutAnims = LauncherAnimUtils.createAnimatorSet();
        final int itemCount = getItemCount();

        final long duration = getResources().getInteger(
                R.integer.config_popupOpenCloseDuration);

        TimeInterpolator interpolator = new AccelerateDecelerateInterpolator();
        int totalHeight = 0;
        for (int i = 0; i < itemCount; i++) {
            totalHeight += getItemViewAt(i).getMeasuredHeight();
        }

        Point startPoint = computeAnimStartPoint(totalHeight);
        int y = mIsAboveIcon ? getPaddingTop() : startPoint.y;
        float backgroundRadius = getItemViewAt(0).getBackgroundRadius();
        mStartRect.set(startPoint.x, startPoint.y, startPoint.x, startPoint.y);
        mEndRect.set(0, y, getMeasuredWidth(), totalHeight + y);
        ValueAnimator revealAnim = new RoundedRectRevealOutlineProvider(backgroundRadius,
                backgroundRadius, mStartRect, mEndRect).createRevealAnimator(this, false);
        revealAnim.setDuration(duration);
        revealAnim.setInterpolator(interpolator);
        Animator fadeAnim = ObjectAnimator.ofFloat(this, ALPHA, 0.0f, 1.0f);
        fadeAnim.setDuration(duration);
        fadeAnim.setInterpolator(interpolator);
        shortcutAnims.play(fadeAnim);
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
        Animator arrowScale = createArrowScaleAnim(1).setDuration((long) getResources().getInteger(R.integer.config_popupArrowOpenDuration));
        mOpenCloseAnimator = shortcutAnims;
        shortcutAnims.playSequentially(revealAnim, arrowScale);
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
        int iconWidth = icon.getWidth();
        if (!(icon.getParent() instanceof AllAppsIconRowView))
            iconWidth -= icon.getTotalPaddingLeft() + icon.getTotalPaddingRight();
        iconWidth *= icon.getScaleX();
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

        enforceContainedWithinScreen(x, width);
    }

    private void enforceContainedWithinScreen(int i, int i2) {
        DragLayer dragLayer = mLauncher.getDragLayer();
        boolean z = false;
        if (getTranslationX() + i < 0.0f || getTranslationX() + i2 > dragLayer.getWidth()) {
            setX((float) ((dragLayer.getWidth() / 2) - (getMeasuredWidth() / 2)));
            z = true;
        }
        mIsCenterAligned = z;
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
        } else if (mIsCenterAligned) {
            layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
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
            Paint arrowPaint = arrowDrawable.getPaint();
            // Note that we have to use getChildAt() instead of getItemViewAt(),
            // since the latter expects the arrow which hasn't been added yet.
            PopupItemView itemAttachedToArrow = (PopupItemView)
                    (getChildAt(mIsAboveIcon ? getChildCount() - 1 : 0));
            arrowPaint.setColor(itemAttachedToArrow.getArrowColor(mIsAboveIcon));
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
    public View getExtendedTouchView() {
        return mOriginalIcon;
    }

    /**
     * Determines when the deferred drag should be started.
     *
     * Current behavior:
     * - Start the drag if the touch passes a certain distance from the original touch down.
     */
    public DragOptions.PreDragCondition createPreDragCondition(final boolean originIsAllApps) {
        return new DragOptions.PreDragCondition() {
            @Override
            public boolean shouldStartDrag(double distanceDragged) {
                return distanceDragged > mStartDragThreshold;
            }

            @Override
            public void onPreDragStart(DropTarget.DragObject dragObject) {
                mOriginalIcon.setVisibility(INVISIBLE);
            }

            @Override
            public void onPreDragEnd(DropTarget.DragObject dragObject, boolean dragStarted) {
                if (!dragStarted || originIsAllApps) {
                    mOriginalIcon.setVisibility(VISIBLE);
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
        ItemInfo itemInfo = (ItemInfo) mOriginalIcon.getTag();
        BadgeInfo badgeInfo = mLauncher.getPopupDataProvider().getBadgeInfoForItem(itemInfo);
        if (mNotificationItemView != null && badgeInfo != null) {
            IconPalette palette = ((FastBitmapDrawable) mOriginalIcon.getIcon()).getIconPalette();
            mNotificationItemView.updateHeader(badgeInfo.getNotificationCount(), palette);
        }
    }

    public void trimNotifications(Map<PackageUserKey, BadgeInfo> updatedBadges) {
        if (mNotificationItemView == null) {
            return;
        }
        ItemInfo originalInfo = (ItemInfo) mOriginalIcon.getTag();
        BadgeInfo badgeInfo = updatedBadges.get(PackageUserKey.fromItemInfo(originalInfo));
        if (badgeInfo == null || badgeInfo.getNotificationKeys().size() == 0) {
            AnimatorSet removeNotification = LauncherAnimUtils.createAnimatorSet();
            final int duration = getResources().getInteger(
                    R.integer.config_removeNotificationViewDuration);
            removeNotification.play(reduceNotificationViewHeight(
                    mNotificationItemView.getHeightMinusFooter(), duration));
            if (mShortcutsItemView != null) {
                mShortcutsItemView.setBackgroundWithCorners(
                        Utilities.resolveAttributeData(getContext(), R.attr.popupColorPrimary), PopupItemView.CORNERS_ALL);
            }
            AnimatorListenerAdapter removeNotificationView = new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (mMainItemView != null) {
                        mMainItemView.getItemContainer().removeView(mNotificationItemView);
                    } else {
                        removeView(mNotificationItemView);
                    }
                    mNotificationItemView = null;
                    if (getItemCount() == 0) {
                        close(false);
                    }
                }
            };
            Animator fade = ObjectAnimator.ofFloat(mNotificationItemView, ALPHA, 0)
                    .setDuration(duration);
            fade.addListener(removeNotificationView);
            removeNotification.play(fade);
            final long arrowScaleDuration = getResources().getInteger(
                    R.integer.config_deepShortcutArrowOpenDuration);
            Animator hideArrow = createArrowScaleAnim(0).setDuration(arrowScaleDuration);
            hideArrow.setStartDelay(0);
            Animator showArrow = createArrowScaleAnim(1).setDuration(arrowScaleDuration);
            showArrow.setStartDelay((long) (duration - arrowScaleDuration * 1.5));
            removeNotification.playSequentially(hideArrow, showArrow);
            removeNotification.start();
            return;
        }
        mNotificationItemView.trimNotifications(NotificationKeyData.extractKeysOnly(
                badgeInfo.getNotificationKeys()));
    }

    @Override
    protected void onWidgetsBound() {
        if (mShortcutsItemView != null) {
            mShortcutsItemView.enableWidgetsIfExist(mOriginalIcon);
        }
    }

    private ObjectAnimator createArrowScaleAnim(float scale) {
        return LauncherAnimUtils.ofPropertyValuesHolder(
                mArrow, new PropertyListBuilder().scale(scale).build());
    }

    /**
     * Animates the height of the notification item and the translationY of other items accordingly.
     */
    public Animator reduceNotificationViewHeight(int heightToRemove, int duration) {
        if (mReduceHeightAnimatorSet != null) {
            mReduceHeightAnimatorSet.cancel();
        }
        final int translateYBy = mIsAboveIcon ? heightToRemove : -heightToRemove;
        mReduceHeightAnimatorSet = LauncherAnimUtils.createAnimatorSet();
        PropertyResetListener<View, Float> resetTranslationYListener
                = new PropertyResetListener<>(TRANSLATION_Y, 0f);
        mReduceHeightAnimatorSet.play(mNotificationItemView.animateHeightRemoval(heightToRemove, false));
        for (int i = 0; i < getItemCount(); i++) {
            final PopupItemView itemView = getItemViewAt(i);
            if (!mIsAboveIcon && itemView == mNotificationItemView) {
                // The notification view is already in the right place when container is below icon.
                continue;
            }
            ValueAnimator translateItem = ObjectAnimator.ofFloat(itemView, TRANSLATION_Y,
                    itemView.getTranslationY() + translateYBy).setDuration(duration);
            translateItem.addListener(resetTranslationYListener);
            mReduceHeightAnimatorSet.play(translateItem);
        }
        mReduceHeightAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mIsAboveIcon) {
                    // All the items, including the notification item, translated down, but the
                    // container itself did not. This means the items would jump back to their
                    // original translation unless we update the container's translationY here.
                    setTranslationY(getTranslationY() + translateYBy);
                }
                mReduceHeightAnimatorSet = null;
            }
        });
        return mReduceHeightAnimatorSet;
    }

    @Override
    public boolean supportsFlingToDelete() {
        return false;
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
    public void onFlingToDeleteCompleted() {

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
            if (Utilities.ATLEAST_NOUGAT) {
                Outline outline = new Outline();
                getOutlineProvider().getOutline(this, outline);
                outline.getRect(mEndRect);
            }
            mOpenCloseAnimator.cancel();
        }

        mIsOpen = false;

        final AnimatorSet shortcutAnims = LauncherAnimUtils.createAnimatorSet();
        final int itemCount = getItemCount();
        int totalHeight = 0;
        for (int i = 0; i < itemCount; i++) {
            totalHeight += getItemViewAt(i).getHeight();
        }
        final long duration = (long) getResources().getInteger(R.integer.config_popupOpenCloseDuration);
        final TimeInterpolator interpolator = new AccelerateDecelerateInterpolator();

        Point startPoint = computeAnimStartPoint(totalHeight);
        int y = mIsAboveIcon ? getPaddingTop() : startPoint.y;

        float backgroundRadius = getItemViewAt(0).getBackgroundRadius();
        mStartRect.set(startPoint.x, startPoint.y, startPoint.x, startPoint.y);
        if (mEndRect.isEmpty()) {
            mEndRect.set(0, y, getMeasuredWidth(), totalHeight + y);
        }

        Animator revealAnimator = new RoundedRectRevealOutlineProvider(backgroundRadius,
                backgroundRadius, mStartRect, mEndRect).createRevealAnimator(this, true);
        revealAnimator.setDuration(duration);
        revealAnimator.setInterpolator(interpolator);
        shortcutAnims.play(revealAnimator);

        Animator fadeAnimator = ObjectAnimator.ofFloat(this, ALPHA, 0);
        fadeAnimator.setDuration(duration);
        fadeAnimator.setInterpolator(interpolator);
        shortcutAnims.play(fadeAnimator);

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
        mOriginalIcon.forceHideBadge(false);
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
        mOriginalIcon.forceHideBadge(false);
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