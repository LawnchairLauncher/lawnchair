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

import static com.android.launcher3.popup.PopupPopulator.MAX_SHORTCUTS_IF_NOTIFICATIONS;
import static com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import static com.android.launcher3.userevent.nano.LauncherLogProto.ItemType;
import static com.android.launcher3.userevent.nano.LauncherLogProto.Target;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.R;
import com.android.launcher3.anim.PropertyResetListener;
import com.android.launcher3.badge.BadgeInfo;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.graphics.IconPalette;
import com.android.launcher3.notification.NotificationItemView;
import com.android.launcher3.notification.NotificationKeyData;
import com.android.launcher3.popup.PopupPopulator.Item;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.shortcuts.ShortcutsItemView;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Themes;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A container for shortcuts to deep links and notifications associated with an app.
 */
@TargetApi(Build.VERSION_CODES.N)
public class PopupContainerWithArrow extends BaseActionPopup<BubbleTextView> implements DragSource,
        DragController.DragListener {

    private final int mStartDragThreshold;

    private NotificationItemView mNotificationItemView;
    private AnimatorSet mReduceHeightAnimatorSet;
    private int mNumNotifications;

    public PopupContainerWithArrow(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mStartDragThreshold = getResources().getDimensionPixelSize(
                R.dimen.deep_shortcuts_start_drag_threshold);
    }

    public PopupContainerWithArrow(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PopupContainerWithArrow(Context context) {
        this(context, null, 0);
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
        PopupPopulator.Item[] itemsToPopulate = PopupPopulator
                .getItemsToPopulate(shortcutIds, notificationKeys, systemShortcuts);
        populateAndShow(originalIcon, itemsToPopulate);

        ItemInfo originalItemInfo = (ItemInfo) originalIcon.getTag();
        List<DeepShortcutView> shortcutViews = mShortcutsItemView == null
                ? Collections.EMPTY_LIST
                : mShortcutsItemView.getDeepShortcutViews(mIsAboveIcon);
        List<View> systemShortcutViews = mShortcutsItemView == null
                ? Collections.EMPTY_LIST
                : mShortcutsItemView.getSystemShortcutViews(mIsAboveIcon);
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

        mLauncher.getDragController().addDragListener(this);
        mOriginalIcon.forceHideBadge(true);

        // Load the shortcuts on a background thread and update the container as it animates.
        final Looper workerLooper = LauncherModel.getWorkerLooper();
        new Handler(workerLooper).postAtFrontOfQueue(PopupPopulator.createUpdateRunnable(
                mLauncher, originalItemInfo, new Handler(Looper.getMainLooper()),
                this, shortcutIds, shortcutViews, notificationKeys, mNotificationItemView,
                systemShortcuts, systemShortcutViews));
    }

    @Override
    protected void addDummyViews(Item[] itemTypesToPopulate) {
        mNotificationItemView = null;
        super.addDummyViews(itemTypesToPopulate);
        if (mNumNotifications > 0) {
            mShortcutsItemView.hideShortcuts(mIsAboveIcon, MAX_SHORTCUTS_IF_NOTIFICATIONS);
        }
    }

    @Override
    protected void onViewInflated(View view, Item itemType,
            boolean shouldUnroundTopCorners, boolean shouldUnroundBottomCorners) {
        if (itemType == PopupPopulator.Item.NOTIFICATION) {
            mNotificationItemView = (NotificationItemView) view;
            boolean notificationFooterHasIcons = mNumNotifications > 1;
            int footerHeight = getResources().getDimensionPixelSize(
                    notificationFooterHasIcons ? R.dimen.notification_footer_height
                            : R.dimen.notification_empty_footer_height);
            view.findViewById(R.id.footer).getLayoutParams().height = footerHeight;
            if (notificationFooterHasIcons) {
                mNotificationItemView.findViewById(R.id.divider).setVisibility(VISIBLE);
            }

            int roundedCorners = ROUNDED_TOP_CORNERS | ROUNDED_BOTTOM_CORNERS;
            if (shouldUnroundTopCorners) {
                roundedCorners &= ~ROUNDED_TOP_CORNERS;
                mNotificationItemView.findViewById(R.id.gutter_top).setVisibility(VISIBLE);
            }
            if (shouldUnroundBottomCorners) {
                roundedCorners &= ~ROUNDED_BOTTOM_CORNERS;
                mNotificationItemView.findViewById(R.id.gutter_bottom).setVisibility(VISIBLE);
            }
            int backgroundColor = Themes.getAttrColor(mLauncher, R.attr.popupColorTertiary);
            mNotificationItemView.setBackgroundWithCorners(backgroundColor, roundedCorners);

            mNotificationItemView.getMainView().setAccessibilityDelegate(mAccessibilityDelegate);
        } else if (itemType == PopupPopulator.Item.SHORTCUT) {
            view.setAccessibilityDelegate(mAccessibilityDelegate);
        }

        if (itemType != PopupPopulator.Item.SYSTEM_SHORTCUT_ICON && itemType.isShortcut
                && mNumNotifications > 0) {
            int prevHeight = view.getLayoutParams().height;
            // Condense shortcuts height when there are notifications.
            view.getLayoutParams().height = getResources().getDimensionPixelSize(
                    R.dimen.bg_popup_item_condensed_height);
            if (view instanceof DeepShortcutView) {
                float iconScale = (float) view.getLayoutParams().height / prevHeight;
                ((DeepShortcutView) view).getIconView().setScaleX(iconScale);
                ((DeepShortcutView) view).getIconView().setScaleY(iconScale);
            }
        }
    }

    private void addDummyViews(PopupPopulator.Item[] itemTypesToPopulate, int numNotifications) {
        final Resources res = getResources();
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

            if (itemTypeToPopulate == PopupPopulator.Item.NOTIFICATION) {
                mNotificationItemView = (NotificationItemView) item;
                boolean notificationFooterHasIcons = numNotifications > 1;
                int footerHeight = res.getDimensionPixelSize(
                        notificationFooterHasIcons ? R.dimen.notification_footer_height
                                : R.dimen.notification_empty_footer_height);
                item.findViewById(R.id.footer).getLayoutParams().height = footerHeight;
                if (notificationFooterHasIcons) {
                    mNotificationItemView.findViewById(R.id.divider).setVisibility(VISIBLE);
                }

                int roundedCorners = ROUNDED_TOP_CORNERS | ROUNDED_BOTTOM_CORNERS;
                if (shouldUnroundTopCorners) {
                    roundedCorners &= ~ROUNDED_TOP_CORNERS;
                    mNotificationItemView.findViewById(R.id.gutter_top).setVisibility(VISIBLE);
                }
                if (shouldUnroundBottomCorners) {
                    roundedCorners &= ~ROUNDED_BOTTOM_CORNERS;
                    mNotificationItemView.findViewById(R.id.gutter_bottom).setVisibility(VISIBLE);
                }
                int backgroundColor = Themes.getAttrColor(mLauncher, R.attr.popupColorTertiary);
                mNotificationItemView.setBackgroundWithCorners(backgroundColor, roundedCorners);

                mNotificationItemView.getMainView().setAccessibilityDelegate(mAccessibilityDelegate);
            } else if (itemTypeToPopulate == PopupPopulator.Item.SHORTCUT) {
                item.setAccessibilityDelegate(mAccessibilityDelegate);
            }

            if (itemTypeToPopulate.isShortcut) {
                if (mShortcutsItemView == null) {
                    mShortcutsItemView = (ShortcutsItemView) inflater.inflate(
                            R.layout.shortcuts_item, this, false);
                    addView(mShortcutsItemView);
                    if (shouldUnroundTopCorners) {
                        shortcutsItemRoundedCorners &= ~ROUNDED_TOP_CORNERS;
                    }
                }
                if (itemTypeToPopulate != PopupPopulator.Item.SYSTEM_SHORTCUT_ICON
                        && numNotifications > 0) {
                    int prevHeight = item.getLayoutParams().height;
                    // Condense shortcuts height when there are notifications.
                    item.getLayoutParams().height = res.getDimensionPixelSize(
                            R.dimen.bg_popup_item_condensed_height);
                    if (item instanceof DeepShortcutView) {
                        float iconScale = (float) item.getLayoutParams().height / prevHeight;
                        ((DeepShortcutView) item).getIconView().setScaleX(iconScale);
                        ((DeepShortcutView) item).getIconView().setScaleY(iconScale);
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
        if (numNotifications > 0) {
            mShortcutsItemView.hideShortcuts(mIsAboveIcon, MAX_SHORTCUTS_IF_NOTIFICATIONS);
        }
    }

    @Override
    protected void onWidgetsBound() {
        if (mShortcutsItemView != null) {
            mShortcutsItemView.enableWidgetsIfExist(mOriginalIcon);
        }
    }

    @Override
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
        ItemInfo itemInfo = (ItemInfo) mOriginalIcon.getTag();
        BadgeInfo badgeInfo = mLauncher.getPopupDataProvider().getBadgeInfoForItem(itemInfo);
        if (mNotificationItemView != null && badgeInfo != null) {
            IconPalette palette = mOriginalIcon.getBadgePalette();
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
            // There are no more notifications, so create an animation to remove
            // the notifications view and expand the shortcuts view (if possible).
            AnimatorSet removeNotification = LauncherAnimUtils.createAnimatorSet();
            int hiddenShortcutsHeight = 0;
            if (mShortcutsItemView != null) {
                hiddenShortcutsHeight = mShortcutsItemView.getHiddenShortcutsHeight();
                int backgroundColor = Themes.getAttrColor(mLauncher, R.attr.popupColorPrimary);
                // With notifications gone, all corners of shortcuts item should be rounded.
                mShortcutsItemView.setBackgroundWithCorners(backgroundColor,
                        ROUNDED_TOP_CORNERS | ROUNDED_BOTTOM_CORNERS);
                removeNotification.play(mShortcutsItemView.showAllShortcuts(mIsAboveIcon));
            }
            final int duration = getResources().getInteger(
                    R.integer.config_removeNotificationViewDuration);
            removeNotification.play(adjustItemHeights(mNotificationItemView.getHeightMinusFooter(),
                    hiddenShortcutsHeight, duration));
            Animator fade = ObjectAnimator.ofFloat(mNotificationItemView, ALPHA, 0)
                    .setDuration(duration);
            fade.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    removeView(mNotificationItemView);
                    mNotificationItemView = null;
                    if (getItemCount() == 0) {
                        close(false);
                    }
                }
            });
            removeNotification.play(fade);
            final long arrowScaleDuration = getResources().getInteger(
                    R.integer.config_popupArrowOpenDuration);
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

    public Animator reduceNotificationViewHeight(int heightToRemove, int duration) {
        return adjustItemHeights(heightToRemove, 0, duration);
    }

    /**
     * Animates the height of the notification item and the translationY of other items accordingly.
     */
    public Animator adjustItemHeights(int notificationHeightToRemove, int shortcutHeightToAdd,
            int duration) {
        if (mReduceHeightAnimatorSet != null) {
            mReduceHeightAnimatorSet.cancel();
        }
        final int translateYBy = mIsAboveIcon ? notificationHeightToRemove - shortcutHeightToAdd
                : -notificationHeightToRemove;
        mReduceHeightAnimatorSet = LauncherAnimUtils.createAnimatorSet();
        boolean removingNotification =
                notificationHeightToRemove == mNotificationItemView.getHeightMinusFooter();
        boolean shouldRemoveNotificationHeightFromTop = mIsAboveIcon && removingNotification;
        mReduceHeightAnimatorSet.play(mNotificationItemView.animateHeightRemoval(
                notificationHeightToRemove, shouldRemoveNotificationHeightFromTop));
        PropertyResetListener<View, Float> resetTranslationYListener
                = new PropertyResetListener<>(TRANSLATION_Y, 0f);
        boolean itemIsAfterShortcuts = false;
        for (int i = 0; i < getItemCount(); i++) {
            final PopupItemView itemView = getItemViewAt(i);
            if (itemIsAfterShortcuts) {
                // Every item after the shortcuts item needs to adjust for the new height.
                itemView.setTranslationY(itemView.getTranslationY() - shortcutHeightToAdd);
            }
            if (itemView == mNotificationItemView && (!mIsAboveIcon || removingNotification)) {
                // The notification view is already in the right place.
                continue;
            }
            ValueAnimator translateItem = ObjectAnimator.ofFloat(itemView, TRANSLATION_Y,
                    itemView.getTranslationY() + translateYBy).setDuration(duration);
            translateItem.addListener(resetTranslationYListener);
            mReduceHeightAnimatorSet.play(translateItem);
            if (itemView == mShortcutsItemView) {
                itemIsAfterShortcuts = true;
            }
        }
        if (mIsAboveIcon) {
            // We also need to adjust the arrow position to account for the new shortcuts height.
            mArrow.setTranslationY(mArrow.getTranslationY() - shortcutHeightToAdd);
        }
        mReduceHeightAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mIsAboveIcon) {
                    // All the items, including the notification item, translated down, but the
                    // container itself did not. This means the items would jump back to their
                    // original translation unless we update the container's translationY here.
                    setTranslationY(getTranslationY() + translateYBy);
                    mArrow.setTranslationY(0);
                }
                mReduceHeightAnimatorSet = null;
            }
        });
        return mReduceHeightAnimatorSet;
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
    public void fillInLogContainerData(View v, ItemInfo info, Target target, Target targetParent) {
        target.itemType = ItemType.DEEPSHORTCUT;
        targetParent.containerType = ContainerType.DEEPSHORTCUTS;
    }

    @Override
    protected void prepareCloseAnimator(AnimatorSet closeAnim) {
        // Animate original icon's text back in.
        closeAnim.play(mOriginalIcon.createTextAlphaAnimator(true /* fadeIn */));

        mOriginalIcon.forceHideBadge(false);
        super.prepareCloseAnimator(closeAnim);
    }

    @Override
    protected void closeComplete() {
        mOriginalIcon.setTextVisibility(mOriginalIcon.shouldTextBeVisible());
        mOriginalIcon.forceHideBadge(false);

        mLauncher.getDragController().removeDragListener(this);
        super.closeComplete();
    }
}
