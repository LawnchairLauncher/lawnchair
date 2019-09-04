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

import static com.android.launcher3.Utilities.squaredHypot;
import static com.android.launcher3.Utilities.squaredTouchSlop;
import static com.android.launcher3.notification.NotificationMainView.NOTIFICATION_ITEM_INFO;
import static com.android.launcher3.popup.PopupPopulator.MAX_SHORTCUTS;
import static com.android.launcher3.popup.PopupPopulator.MAX_SHORTCUTS_IF_NOTIFICATIONS;
import static com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import static com.android.launcher3.userevent.nano.LauncherLogProto.ItemType;
import static com.android.launcher3.userevent.nano.LauncherLogProto.Target;

import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.ItemInfoWithIcon;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.R;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.accessibility.ShortcutMenuAccessibilityDelegate;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.logging.LoggerUtils;
import com.android.launcher3.notification.NotificationInfo;
import com.android.launcher3.notification.NotificationItemView;
import com.android.launcher3.notification.NotificationKeyData;
import com.android.launcher3.popup.PopupDataProvider.PopupDataChangeListener;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.shortcuts.ShortcutDragPreviewProvider;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.ShortcutUtil;
import com.android.launcher3.views.BaseDragLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A container for shortcuts to deep links and notifications associated with an app.
 */
public class PopupContainerWithArrow extends ArrowPopup implements DragSource,
        DragController.DragListener, View.OnLongClickListener,
        View.OnTouchListener, PopupDataChangeListener {

    private final List<DeepShortcutView> mShortcuts = new ArrayList<>();
    private final PointF mInterceptTouchDown = new PointF();
    protected final Point mIconLastTouchPos = new Point();

    private final int mStartDragThreshold;
    private final LauncherAccessibilityDelegate mAccessibilityDelegate;

    private BubbleTextView mOriginalIcon;
    private NotificationItemView mNotificationItemView;
    private int mNumNotifications;

    private ViewGroup mSystemShortcutContainer;

    public PopupContainerWithArrow(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mStartDragThreshold = getResources().getDimensionPixelSize(
                R.dimen.deep_shortcuts_start_drag_threshold);
        mAccessibilityDelegate = new ShortcutMenuAccessibilityDelegate(mLauncher);
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
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mLauncher.getPopupDataProvider().setChangeListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mLauncher.getPopupDataProvider().setChangeListener(null);
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
        return squaredHypot(mInterceptTouchDown.x - ev.getX(), mInterceptTouchDown.y - ev.getY())
                > squaredTouchSlop(getContext());
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mNotificationItemView != null) {
            return mNotificationItemView.onTouchEvent(ev) || super.onTouchEvent(ev);
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
                command, mOriginalIcon, getLogContainerType());
    }

    @Override
    public int getLogContainerType() {
        return ContainerType.DEEPSHORTCUTS;
    }

    public OnClickListener getItemClickListener() {
        return ItemClickHandler.INSTANCE;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            BaseDragLayer dl = getPopupContainer();
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
        if (!ShortcutUtil.supportsShortcuts(itemInfo)) {
            return null;
        }

        final PopupContainerWithArrow container =
                (PopupContainerWithArrow) launcher.getLayoutInflater().inflate(
                        R.layout.popup_container, launcher.getDragLayer(), false);
        container.populateAndShow(icon, itemInfo, SystemShortcutFactory.INSTANCE.get(launcher));
        return container;
    }

    @Override
    protected void onInflationComplete(boolean isReversed) {
        if (isReversed && mNotificationItemView != null) {
            mNotificationItemView.inverseGutterMargin();
        }

        // Update dividers
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

    protected void populateAndShow(
            BubbleTextView icon, ItemInfo item, SystemShortcutFactory factory) {
        PopupDataProvider popupDataProvider = mLauncher.getPopupDataProvider();
        populateAndShow(icon,
                popupDataProvider.getShortcutCountForItem(item),
                popupDataProvider.getNotificationKeysForItem(item),
                factory.getEnabledShortcuts(mLauncher, item));
    }

    public ViewGroup getSystemShortcutContainerForTesting() {
        return mSystemShortcutContainer;
    }

    @TargetApi(Build.VERSION_CODES.P)
    protected void populateAndShow(final BubbleTextView originalIcon, int shortcutCount,
            final List<NotificationKeyData> notificationKeys, List<SystemShortcut> systemShortcuts) {
        mNumNotifications = notificationKeys.size();
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

        if (shortcutCount > 0) {
            if (mNotificationItemView != null) {
                mNotificationItemView.addGutter();
            }

            for (int i = shortcutCount; i > 0; i--) {
                mShortcuts.add(inflateAndAdd(R.layout.deep_shortcut, this));
            }
            updateHiddenShortcuts();

            if (!systemShortcuts.isEmpty()) {
                mSystemShortcutContainer = inflateAndAdd(R.layout.system_shortcut_icons, this);
                for (SystemShortcut shortcut : systemShortcuts) {
                    initializeSystemShortcut(
                            R.layout.system_shortcut_icon_only, mSystemShortcutContainer, shortcut);
                }
            }
        } else if (!systemShortcuts.isEmpty()) {
            if (mNotificationItemView != null) {
                mNotificationItemView.addGutter();
            }

            for (SystemShortcut shortcut : systemShortcuts) {
                initializeSystemShortcut(R.layout.system_shortcut, this, shortcut);
            }
        }

        reorderAndShow(viewsToFlip);

        ItemInfo originalItemInfo = (ItemInfo) originalIcon.getTag();
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setAccessibilityPaneTitle(getTitleForAccessibility());
        }

        mLauncher.getDragController().addDragListener(this);
        mOriginalIcon.setForceHideDot(true);

        // All views are added. Animate layout from now on.
        setLayoutTransition(new LayoutTransition());

        // Load the shortcuts on a background thread and update the container as it animates.
        final Looper workerLooper = LauncherModel.getWorkerLooper();
        new Handler(workerLooper).postAtFrontOfQueue(PopupPopulator.createUpdateRunnable(
                mLauncher, originalItemInfo, new Handler(Looper.getMainLooper()),
                this, mShortcuts, notificationKeys));
    }

    private String getTitleForAccessibility() {
        return getContext().getString(mNumNotifications == 0 ?
                R.string.action_deep_shortcut :
                R.string.shortcuts_menu_with_notifications_description);
    }

    @Override
    protected Pair<View, String> getAccessibilityTarget() {
        return Pair.create(this, "");
    }

    @Override
    protected void getTargetObjectLocation(Rect outPos) {
        getPopupContainer().getDescendantRectRelativeToSelf(mOriginalIcon, outPos);
        outPos.top += mOriginalIcon.getPaddingTop();
        outPos.left += mOriginalIcon.getPaddingLeft();
        outPos.right -= mOriginalIcon.getPaddingRight();
        outPos.bottom = outPos.top + (mOriginalIcon.getIcon() != null
                ? mOriginalIcon.getIcon().getBounds().height()
                : mOriginalIcon.getHeight());
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
    public void onWidgetsBound() {
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
                initializeSystemShortcut(
                        R.layout.system_shortcut_icon_only, mSystemShortcutContainer, widgetInfo);
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

    private void initializeSystemShortcut(int resId, ViewGroup container, SystemShortcut info) {
        View view = inflateAndAdd(
                resId, container, getInsertIndexForSystemShortcut(container, info));
        if (view instanceof DeepShortcutView) {
            // Expanded system shortcut, with both icon and text shown on white background.
            final DeepShortcutView shortcutView = (DeepShortcutView) view;
            info.setIconAndLabelFor(shortcutView.getIconView(), shortcutView.getBubbleText());
        } else if (view instanceof ImageView) {
            // Only the system shortcut icon shows on a gray background header.
            info.setIconAndContentDescriptionFor((ImageView) view);
        }
        view.setTag(info);
        view.setOnClickListener(info.getOnClickListener(mLauncher,
                (ItemInfo) mOriginalIcon.getTag()));
    }

    /**
     * Returns an index for inserting a shortcut into a container.
     */
    private int getInsertIndexForSystemShortcut(ViewGroup container, SystemShortcut shortcut) {
        final View separator = container.findViewById(R.id.separator);

        return separator != null && shortcut.isLeftGroup() ?
                container.indexOfChild(separator) :
                container.getChildCount();
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
     * Updates the notification header if the original icon's dot updated.
     */
    @Override
    public void onNotificationDotsUpdated(Predicate<PackageUserKey> updatedDots) {
        ItemInfo itemInfo = (ItemInfo) mOriginalIcon.getTag();
        PackageUserKey packageUser = PackageUserKey.fromItemInfo(itemInfo);
        if (updatedDots.test(packageUser)) {
            updateNotificationHeader();
        }
    }

    private void updateNotificationHeader() {
        ItemInfoWithIcon itemInfo = (ItemInfoWithIcon) mOriginalIcon.getTag();
        DotInfo dotInfo = mLauncher.getDotInfoForItem(itemInfo);
        if (mNotificationItemView != null && dotInfo != null) {
            mNotificationItemView.updateHeader(
                    dotInfo.getNotificationCount(), itemInfo.iconColor);
        }
    }

    @Override
    public void trimNotifications(Map<PackageUserKey, DotInfo> updatedDots) {
        if (mNotificationItemView == null) {
            return;
        }
        ItemInfo originalInfo = (ItemInfo) mOriginalIcon.getTag();
        DotInfo dotInfo = updatedDots.get(PackageUserKey.fromItemInfo(originalInfo));
        if (dotInfo == null || dotInfo.getNotificationKeys().size() == 0) {
            // No more notifications, remove the notification views and expand all shortcuts.
            mNotificationItemView.removeAllViews();
            mNotificationItemView = null;
            updateHiddenShortcuts();
            updateDividers();
        } else {
            mNotificationItemView.trimNotifications(
                    NotificationKeyData.extractKeysOnly(dotInfo.getNotificationKeys()));
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

    @Override
    protected void onCreateCloseAnimation(AnimatorSet anim) {
        // Animate original icon's text back in.
        anim.play(mOriginalIcon.createTextAlphaAnimator(true /* fadeIn */));
        mOriginalIcon.setForceHideDot(false);
    }

    @Override
    protected void closeComplete() {
        super.closeComplete();
        mOriginalIcon.setTextVisibility(mOriginalIcon.shouldTextBeVisible());
        mOriginalIcon.setForceHideDot(false);
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
