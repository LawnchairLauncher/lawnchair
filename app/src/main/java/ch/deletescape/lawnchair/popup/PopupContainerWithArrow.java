package ch.deletescape.lawnchair.popup;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.ShapeDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.DecelerateInterpolator;
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
import ch.deletescape.lawnchair.LogAccelerateInterpolator;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.accessibility.LauncherAccessibilityDelegate;
import ch.deletescape.lawnchair.accessibility.ShortcutMenuAccessibilityDelegate;
import ch.deletescape.lawnchair.anim.PropertyListBuilder;
import ch.deletescape.lawnchair.anim.PropertyResetListener;
import ch.deletescape.lawnchair.badge.BadgeInfo;
import ch.deletescape.lawnchair.config.FeatureFlags;
import ch.deletescape.lawnchair.dragndrop.DragController;
import ch.deletescape.lawnchair.dragndrop.DragLayer;
import ch.deletescape.lawnchair.dragndrop.DragOptions;
import ch.deletescape.lawnchair.graphics.IconPalette;
import ch.deletescape.lawnchair.graphics.TriangleShape;
import ch.deletescape.lawnchair.notification.NotificationItemView;
import ch.deletescape.lawnchair.notification.NotificationKeyData;
import ch.deletescape.lawnchair.shortcuts.DeepShortcutManager;
import ch.deletescape.lawnchair.shortcuts.DeepShortcutView;
import ch.deletescape.lawnchair.shortcuts.ShortcutsItemView;
import ch.deletescape.lawnchair.util.PackageUserKey;

public class PopupContainerWithArrow extends AbstractFloatingView implements DragSource, DragController.DragListener {
    private LauncherAccessibilityDelegate mAccessibilityDelegate;
    private View mArrow;
    private boolean mDeferContainerRemoval;
    private PointF mInterceptTouchDown;
    protected boolean mIsAboveIcon;
    private boolean mIsLeftAligned;
    private final boolean mIsRtl;
    protected final Launcher mLauncher;
    private NotificationItemView mNotificationItemView;
    protected Animator mOpenCloseAnimator;
    protected BubbleTextView mOriginalIcon;
    private AnimatorSet mReduceHeightAnimatorSet;
    public ShortcutsItemView mShortcutsItemView;
    private final int mStartDragThreshold;
    private final Rect mTempRect;

    public PopupContainerWithArrow(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        mTempRect = new Rect();
        mInterceptTouchDown = new PointF();
        mLauncher = Launcher.getLauncher(context);
        mStartDragThreshold = getResources().getDimensionPixelSize(R.dimen.deep_shortcuts_start_drag_threshold);
        mAccessibilityDelegate = new ShortcutMenuAccessibilityDelegate(mLauncher);
        mIsRtl = Utilities.isRtl(getResources());
    }

    public PopupContainerWithArrow(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public PopupContainerWithArrow(Context context) {
        this(context, null, 0);
    }

    public LauncherAccessibilityDelegate getAccessibilityDelegate() {
        return mAccessibilityDelegate;
    }

    public static PopupContainerWithArrow showForIcon(BubbleTextView bubbleTextView) {
        Launcher launcher = Launcher.getLauncher(bubbleTextView.getContext());
        if (getOpen(launcher) != null) {
            bubbleTextView.clearFocus();
            return null;
        }
        ItemInfo itemInfo = (ItemInfo) bubbleTextView.getTag();
        if (!DeepShortcutManager.supportsEdit(itemInfo)) {
            return null;
        }
        PopupDataProvider popupDataProvider = launcher.getPopupDataProvider();
        List shortcutIdsForItem = popupDataProvider.getShortcutIdsForItem(itemInfo);
        List notificationKeysForItem = popupDataProvider.getNotificationKeysForItem(itemInfo);
        List enabledSystemShortcutsForItem = popupDataProvider.getEnabledSystemShortcutsForItem(itemInfo);
        PopupContainerWithArrow popupContainerWithArrow = (PopupContainerWithArrow) launcher.getLayoutInflater().inflate(R.layout.popup_container, launcher.getDragLayer(), false);
        popupContainerWithArrow.setVisibility(INVISIBLE);
        launcher.getDragLayer().addView(popupContainerWithArrow);
        popupContainerWithArrow.populateAndShow(bubbleTextView, shortcutIdsForItem, notificationKeysForItem, enabledSystemShortcutsForItem);
        return popupContainerWithArrow;
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
        if (numNotifications == 0) {
            setContentDescription(getContext().getString(R.string.shortcuts_menu_description,
                    numShortcuts, originalIcon.getContentDescription().toString()));
        } else {
            setContentDescription(getContext().getString(
                    R.string.shortcuts_menu_with_notifications_description, numShortcuts,
                    numNotifications, originalIcon.getContentDescription().toString()));
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
        final int spacing = res.getDimensionPixelSize(R.dimen.popup_items_spacing);
        final LayoutInflater inflater = mLauncher.getLayoutInflater();
        int numItems = itemTypesToPopulate.length;
        for (int i = 0; i < numItems; i++) {
            PopupPopulator.Item itemTypeToPopulate = itemTypesToPopulate[i];
            PopupPopulator.Item nextItemTypeToPopulate =
                    i < numItems - 1 ? itemTypesToPopulate[i + 1] : null;
            final View item = inflater.inflate(itemTypeToPopulate.layoutId, this, false);
            if (itemTypeToPopulate == PopupPopulator.Item.NOTIFICATION) {
                mNotificationItemView = (NotificationItemView) item;
                int footerHeight = notificationFooterHasIcons ?
                        res.getDimensionPixelSize(R.dimen.notification_footer_height) : 0;
                item.findViewById(R.id.footer).getLayoutParams().height = footerHeight;
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
                    addView(mShortcutsItemView);
                }
                mShortcutsItemView.addShortcutView(item, itemTypeToPopulate);
                if (shouldAddBottomMargin) {
                    ((LayoutParams) mShortcutsItemView.getLayoutParams()).bottomMargin = spacing;
                }
            } else {
                addView(item);
                if (shouldAddBottomMargin) {
                    ((LayoutParams) item.getLayoutParams()).bottomMargin = spacing;
                }
            }
        }
    }

    protected PopupItemView getItemViewAt(int i) {
        if (!mIsAboveIcon) {
            i++;
        }
        return (PopupItemView) getChildAt(i);
    }

    protected int getItemCount() {
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
            Animator fadeAnim = ObjectAnimator.ofFloat(popupItemView, View.ALPHA, 1);
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
        Animator arrowScale = createArrowScaleAnim(1).setDuration(arrowScaleDuration);
        arrowScale.setStartDelay(arrowScaleDelay);
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
    }

    private boolean isAlignedWithStart() {
        return !(!this.mIsLeftAligned || this.mIsRtl) || !this.mIsLeftAligned && this.mIsRtl;

    }

    private View addArrowView(int i, int i2, int i3, int i4) {
        int i5 = 0;
        PopupContainerWithArrow.LayoutParams layoutParams = new LayoutParams(i3, i4);
        if (mIsLeftAligned) {
            layoutParams.gravity = Gravity.LEFT;
            layoutParams.leftMargin = i;
        } else {
            layoutParams.gravity = Gravity.RIGHT;
            layoutParams.rightMargin = i;
        }
        if (mIsAboveIcon) {
            layoutParams.topMargin = i2;
        } else {
            layoutParams.bottomMargin = i2;
        }
        View view = new View(getContext());
        if (Gravity.isVertical(((FrameLayout.LayoutParams) getLayoutParams()).gravity)) {
            view.setVisibility(INVISIBLE);
        } else {
            ShapeDrawable shapeDrawable = new ShapeDrawable(TriangleShape.create((float) i3, (float) i4, !mIsAboveIcon));
            Paint paint = shapeDrawable.getPaint();
            paint.setColor(((PopupItemView) getChildAt(mIsAboveIcon ? getChildCount() - 1 : 0)).getArrowColor(mIsAboveIcon));
            paint.setPathEffect(new CornerPathEffect((float) getResources().getDimensionPixelSize(R.dimen.popup_arrow_corner_radius)));
            view.setBackground(shapeDrawable);
            view.setElevation(getElevation());
        }
        if (mIsAboveIcon) {
            i5 = getChildCount();
        }
        addView(view, i5, layoutParams);
        return view;
    }

    @Override
    public View getExtendedTouchView() {
        return mOriginalIcon;
    }

    public DragOptions.PreDragCondition createPreDragCondition(final boolean originIsAllApps) {
        return new DragOptions.PreDragCondition() {
            @Override
            public boolean shouldStartDrag(double d) {
                return d > ((double) PopupContainerWithArrow.this.mStartDragThreshold);
            }

            @Override
            public void onPreDragStart(DropTarget.DragObject dragObject) {
                PopupContainerWithArrow.this.mOriginalIcon.setVisibility(INVISIBLE);
            }

            @Override
            public void onPreDragEnd(DropTarget.DragObject dragObject, boolean makeOriginalVisible) {
                if (makeOriginalVisible || originIsAllApps) {
                    PopupContainerWithArrow.this.mOriginalIcon.setVisibility(VISIBLE);
                    if (!PopupContainerWithArrow.this.mIsAboveIcon) {
                        PopupContainerWithArrow.this.mOriginalIcon.setTextVisibility(false);
                    }
                }
            }
        };
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent motionEvent) {
        boolean z = false;
        if (motionEvent.getAction() == 0) {
            mInterceptTouchDown.set(motionEvent.getX(), motionEvent.getY());
            return false;
        }
        if (Math.hypot((double) (mInterceptTouchDown.x - motionEvent.getX()), (double) (mInterceptTouchDown.y - motionEvent.getY())) > ((double) ViewConfiguration.get(getContext()).getScaledTouchSlop())) {
            z = true;
        }
        return z;
    }

    public void updateNotificationHeader(Set set) {
        if (set.contains(PackageUserKey.fromItemInfo((ItemInfo) mOriginalIcon.getTag()))) {
            updateNotificationHeader();
        }
    }

    private void updateNotificationHeader() {
        BadgeInfo badgeInfoForItem = mLauncher.getPopupDataProvider().getBadgeInfoForItem((ItemInfo) mOriginalIcon.getTag());
        if (mNotificationItemView != null && badgeInfoForItem != null) {
            IconPalette iconPalette;
            if (mOriginalIcon.getIcon() instanceof FastBitmapDrawable) {
                iconPalette = ((FastBitmapDrawable) mOriginalIcon.getIcon()).getIconPalette();
            } else {
                iconPalette = null;
            }
            mNotificationItemView.updateHeader(badgeInfoForItem.getNotificationCount(), iconPalette);
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
            final int spacing = getResources().getDimensionPixelSize(R.dimen.popup_items_spacing);
            removeNotification.play(reduceNotificationViewHeight(
                    mNotificationItemView.getHeightMinusFooter() + spacing, duration));
            final View removeMarginView = mIsAboveIcon ? getItemViewAt(getItemCount() - 2)
                    : mNotificationItemView;
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
            Animator fade = ObjectAnimator.ofFloat(mNotificationItemView, ALPHA, 0)
                    .setDuration(duration);
            fade.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    removeView(mNotificationItemView);
                    mNotificationItemView = null;
                    if (getItemCount() == 0) {
                        close(false);
                        return;
                    }
                }
            });
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

    private ObjectAnimator createArrowScaleAnim(float f) {
        return LauncherAnimUtils.ofPropertyValuesHolder(mArrow, new PropertyListBuilder().scale(f).build());
    }

    public Animator reduceNotificationViewHeight(int i, int i2) {
        if (mReduceHeightAnimatorSet != null) {
            mReduceHeightAnimatorSet.cancel();
        }
        final int i3 = mIsAboveIcon ? i : -i;
        mReduceHeightAnimatorSet = LauncherAnimUtils.createAnimatorSet();
        mReduceHeightAnimatorSet.play(mNotificationItemView.animateHeightRemoval(i));
        AnimatorListener propertyResetListener = new PropertyResetListener(TRANSLATION_Y, 0.0f);
        for (int i4 = 0; i4 < getItemCount(); i4++) {
            PopupItemView itemViewAt = getItemViewAt(i4);
            if (mIsAboveIcon || itemViewAt != mNotificationItemView) {
                Animator duration = ObjectAnimator.ofFloat(itemViewAt, TRANSLATION_Y, new float[]{itemViewAt.getTranslationY() + ((float) i3)}).setDuration((long) i2);
                duration.addListener(propertyResetListener);
                mReduceHeightAnimatorSet.play(duration);
            }
        }
        mReduceHeightAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                if (PopupContainerWithArrow.this.mIsAboveIcon) {
                    PopupContainerWithArrow.this.setTranslationY(PopupContainerWithArrow.this.getTranslationY() + ((float) i3));
                }
                PopupContainerWithArrow.this.mReduceHeightAnimatorSet = null;
            }
        });
        return mReduceHeightAnimatorSet;
    }

    @Override
    public boolean supportsFlingToDelete() {
        return true;
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
        return 1.0f;
    }

    @Override
    public void onFlingToDeleteCompleted() {
        // Do nothing
    }

    @Override
    public void onDropCompleted(View view, DropTarget.DragObject dragObject, boolean z, boolean z2) {
        if (!z2) {
            dragObject.dragView.remove();
            mLauncher.showWorkspace(true);
            mLauncher.getDropTargetBar().onDragEnd();
        }
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions dragOptions) {
        mDeferContainerRemoval = true;
        animateClose();
    }

    @Override
    public void onDragEnd() {
        if (!mIsOpen) {
            if (mOpenCloseAnimator != null) {
                mDeferContainerRemoval = false;
            } else if (mDeferContainerRemoval) {
                closeComplete();
            }
        }
    }

    @Override
    protected void handleClose(boolean z) {
        if (z) {
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
            anim = view.createCloseAnimation(mIsAboveIcon, mIsLeftAligned, duration);
            int animationIndex = mIsAboveIcon ? i - firstOpenItemIndex
                    : numOpenShortcuts - i - 1;
            anim.setStartDelay(stagger * animationIndex);
            Animator fadeAnim = ObjectAnimator.ofFloat(view, View.ALPHA, 0);
            // Don't start fading until the arrow is gone.
            fadeAnim.setStartDelay(stagger * animationIndex + arrowScaleDuration);
            fadeAnim.setDuration(duration - arrowScaleDuration);
            fadeAnim.setInterpolator(fadeInterpolator);
            shortcutAnims.play(fadeAnim);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    view.setVisibility(INVISIBLE);
                }
            });
            shortcutAnims.play(anim);
        }
        Animator arrowAnim = createArrowScaleAnim(0).setDuration(arrowScaleDuration);
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
        mOriginalIcon.forceHideBadge(false);
    }

    protected void closeComplete() {
        if (mOpenCloseAnimator != null) {
            mOpenCloseAnimator.cancel();
            mOpenCloseAnimator = null;
        }
        mIsOpen = false;
        mDeferContainerRemoval = false;
        mOriginalIcon.setTextVisibility(!(((ItemInfo) mOriginalIcon.getTag()).container == -101));
        mOriginalIcon.forceHideBadge(false);
        mLauncher.getDragController().removeDragListener(this);
        mLauncher.getDragLayer().removeView(this);
    }

    @Override
    protected boolean isOfType(int i) {
        return (i & 2) != 0;
    }

    public static PopupContainerWithArrow getOpen(Launcher launcher) {
        return (PopupContainerWithArrow) AbstractFloatingView.getOpenView(launcher, 2);
    }

}