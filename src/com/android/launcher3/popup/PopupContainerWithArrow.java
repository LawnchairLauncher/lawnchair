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

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_SHORTCUTS;
import static com.android.launcher3.Utilities.squaredHypot;
import static com.android.launcher3.Utilities.squaredTouchSlop;
import static com.android.launcher3.popup.PopupPopulator.MAX_SHORTCUTS;
import static com.android.launcher3.popup.PopupPopulator.MAX_SHORTCUTS_IF_NOTIFICATIONS;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.accessibility.ShortcutMenuAccessibilityDelegate;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.notification.NotificationContainer;
import com.android.launcher3.notification.NotificationInfo;
import com.android.launcher3.notification.NotificationKeyData;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.shortcuts.ShortcutDragPreviewProvider;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.ShortcutUtil;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A container for shortcuts to deep links and notifications associated with an app.
 *
 * @param <T> The activity on with the popup shows
 */
public class PopupContainerWithArrow<T extends Context & ActivityContext>
        extends ArrowPopup<T> implements DragSource, DragController.DragListener {

    private final List<DeepShortcutView> mShortcuts = new ArrayList<>();
    private final PointF mInterceptTouchDown = new PointF();

    private final int mStartDragThreshold;

    private BubbleTextView mOriginalIcon;
    private int mNumNotifications;
    private NotificationContainer mNotificationContainer;
    private int mContainerWidth;

    private ViewGroup mWidgetContainer;

    private ViewGroup mDeepShortcutContainer;

    private ViewGroup mSystemShortcutContainer;

    protected PopupItemDragHandler mPopupItemDragHandler;
    protected LauncherAccessibilityDelegate mAccessibilityDelegate;

    public PopupContainerWithArrow(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mStartDragThreshold = getResources().getDimensionPixelSize(
                R.dimen.deep_shortcuts_start_drag_threshold);
        mContainerWidth = getResources().getDimensionPixelSize(R.dimen.bg_popup_item_width);
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
        if (mNotificationContainer != null
                && mNotificationContainer.onInterceptSwipeEvent(ev)) {
            return true;
        }
        // Stop sending touch events to deep shortcut views if user moved beyond touch slop.
        return squaredHypot(mInterceptTouchDown.x - ev.getX(), mInterceptTouchDown.y - ev.getY())
                > squaredTouchSlop(getContext());
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mNotificationContainer != null) {
            return mNotificationContainer.onSwipeEvent(ev) || super.onTouchEvent(ev);
        }
        return super.onTouchEvent(ev);
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_ACTION_POPUP) != 0;
    }

    public OnClickListener getItemClickListener() {
        return (view) -> {
            mActivityContext.getItemOnClickListener().onClick(view);
        };
    }

    public void setPopupItemDragHandler(PopupItemDragHandler popupItemDragHandler) {
        mPopupItemDragHandler = popupItemDragHandler;
    }

    public PopupItemDragHandler getItemDragHandler() {
        return mPopupItemDragHandler;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            BaseDragLayer dl = getPopupContainer();
            if (!dl.isEventOverView(this, ev)) {
                // TODO: add WW log if want to log if tap closed deep shortcut container.
                close(true);

                // We let touches on the original icon go through so that users can launch
                // the app with one tap if they don't find a shortcut they want.
                return mOriginalIcon == null || !dl.isEventOverView(mOriginalIcon, ev);
            }
        }
        return false;
    }

    @Override
    protected void setChildColor(View view, int color, AnimatorSet animatorSetOut) {
        super.setChildColor(view, color, animatorSetOut);
        if (view.getId() == R.id.notification_container && mNotificationContainer != null) {
            mNotificationContainer.updateBackgroundColor(color, animatorSetOut);
        }
    }

    /**
     * Returns true if we can show the container.
     *
     * @deprecated Left here since some dependent projects are using this method
     */
    @Deprecated
    public static boolean canShow(View icon, ItemInfo item) {
        return icon instanceof BubbleTextView && ShortcutUtil.supportsShortcuts(item);
    }

    /**
     * Shows the notifications and deep shortcuts associated with a Launcher {@param icon}.
     * @return the container if shown or null.
     */
    public static PopupContainerWithArrow<Launcher> showForIcon(BubbleTextView icon) {
        Launcher launcher = Launcher.getLauncher(icon.getContext());
        if (getOpen(launcher) != null) {
            // There is already an items container open, so don't open this one.
            icon.clearFocus();
            return null;
        }
        ItemInfo item = (ItemInfo) icon.getTag();
        if (!ShortcutUtil.supportsShortcuts(item)) {
            return null;
        }

        final PopupContainerWithArrow<Launcher> container =
                (PopupContainerWithArrow) launcher.getLayoutInflater().inflate(
                        R.layout.popup_container, launcher.getDragLayer(), false);
        container.configureForLauncher(launcher);

        PopupDataProvider popupDataProvider = launcher.getPopupDataProvider();
        container.populateAndShow(icon,
                popupDataProvider.getShortcutCountForItem(item),
                popupDataProvider.getNotificationKeysForItem(item),
                launcher.getSupportedShortcuts()
                        .map(s -> s.getShortcut(launcher, item, icon))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));
        launcher.refreshAndBindWidgetsForPackageUser(PackageUserKey.fromItemInfo(item));
        container.requestFocus();
        return container;
    }

    private void configureForLauncher(Launcher launcher) {
        addOnAttachStateChangeListener(new LauncherPopupLiveUpdateHandler(
                launcher, (PopupContainerWithArrow<Launcher>) this));
        mPopupItemDragHandler = new LauncherPopupItemDragHandler(launcher, this);
        mAccessibilityDelegate = new ShortcutMenuAccessibilityDelegate(launcher);
        launcher.getDragController().addDragListener(this);
        addPreDrawForColorExtraction(launcher);
    }

    @Override
    protected List<View> getChildrenForColorExtraction() {
        return Arrays.asList(mSystemShortcutContainer, mWidgetContainer, mDeepShortcutContainer,
                mNotificationContainer);
    }

    private void initializeSystemShortcuts(List<SystemShortcut> shortcuts) {
        if (shortcuts.isEmpty()) {
            return;
        }
        // If there is only 1 shortcut, add it to its own container so it can show text and icon
        if (shortcuts.size() == 1) {
            initializeSystemShortcut(R.layout.system_shortcut, this, shortcuts.get(0));
            return;
        }
        mSystemShortcutContainer = inflateAndAdd(R.layout.system_shortcut_icons, this);
        for (SystemShortcut shortcut : shortcuts) {
            initializeSystemShortcut(
                    R.layout.system_shortcut_icon_only, mSystemShortcutContainer,
                    shortcut);
        }
    }

    @TargetApi(Build.VERSION_CODES.P)
    public void populateAndShow(final BubbleTextView originalIcon, int shortcutCount,
            final List<NotificationKeyData> notificationKeys, List<SystemShortcut> shortcuts) {
        mNumNotifications = notificationKeys.size();
        mOriginalIcon = originalIcon;

        boolean hasDeepShortcuts = shortcutCount > 0;
        mContainerWidth = getResources().getDimensionPixelSize(R.dimen.bg_popup_item_width);

        // Add views
        if (mNumNotifications > 0) {
            // Add notification entries
            if (mNotificationContainer == null) {
                mNotificationContainer = findViewById(R.id.notification_container);
                mNotificationContainer.setVisibility(VISIBLE);
                mNotificationContainer.setPopupView(this);
            } else {
                mNotificationContainer.setVisibility(GONE);
            }
            updateNotificationHeader();
        }
        int viewsToFlip = getChildCount();
        mSystemShortcutContainer = this;
        if (mDeepShortcutContainer == null) {
            mDeepShortcutContainer = findViewById(R.id.deep_shortcuts_container);
        }
        if (hasDeepShortcuts) {
            // Remove the widget shortcut fom the list
            List<SystemShortcut> systemShortcuts = shortcuts
                    .stream()
                    .filter(shortcut -> !(shortcut instanceof SystemShortcut.Widgets))
                    .collect(Collectors.toList());
            Optional<SystemShortcut.Widgets> widgetShortcutOpt = shortcuts
                    .stream()
                    .filter(shortcut -> shortcut instanceof SystemShortcut.Widgets)
                    .map(SystemShortcut.Widgets.class::cast)
                    .findFirst();

            // if there are deep shortcuts, we might want to increase the width of shortcuts to fit
            // horizontally laid out system shortcuts.
            mContainerWidth = Math.max(mContainerWidth,
                    systemShortcuts.size() * getResources()
                            .getDimensionPixelSize(R.dimen.system_shortcut_header_icon_touch_size)
            );

            mDeepShortcutContainer.setVisibility(View.VISIBLE);

            for (int i = shortcutCount; i > 0; i--) {
                DeepShortcutView v = inflateAndAdd(R.layout.deep_shortcut, mDeepShortcutContainer);
                v.getLayoutParams().width = mContainerWidth;
                mShortcuts.add(v);
            }
            updateHiddenShortcuts();

            if (widgetShortcutOpt.isPresent()) {
                if (mWidgetContainer == null) {
                    mWidgetContainer = inflateAndAdd(R.layout.widget_shortcut_container,
                            this);
                }
                initializeWidgetShortcut(mWidgetContainer, widgetShortcutOpt.get());
            }

            initializeSystemShortcuts(systemShortcuts);
        } else {
            mDeepShortcutContainer.setVisibility(View.GONE);
            if (!shortcuts.isEmpty()) {
                for (SystemShortcut shortcut : shortcuts) {
                    initializeSystemShortcut(R.layout.system_shortcut, this, shortcut);
                }
            }
        }

        reorderAndShow(viewsToFlip);

        ItemInfo originalItemInfo = (ItemInfo) originalIcon.getTag();
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setAccessibilityPaneTitle(getTitleForAccessibility());
        }

        mOriginalIcon.setForceHideDot(true);

        // All views are added. Animate layout from now on.
        setLayoutTransition(new LayoutTransition());

        // Load the shortcuts on a background thread and update the container as it animates.
        MODEL_EXECUTOR.getHandler().postAtFrontOfQueue(PopupPopulator.createUpdateRunnable(
                mActivityContext, originalItemInfo, new Handler(Looper.getMainLooper()),
                this, mShortcuts, notificationKeys));
    }

    protected NotificationContainer getNotificationContainer() {
        return mNotificationContainer;
    }

    protected BubbleTextView getOriginalIcon() {
        return mOriginalIcon;
    }

    protected ViewGroup getSystemShortcutContainer() {
        return mSystemShortcutContainer;
    }

    protected ViewGroup getWidgetContainer() {
        return mWidgetContainer;
    }

    protected void setWidgetContainer(ViewGroup widgetContainer) {
        mWidgetContainer = widgetContainer;
    }

    private String getTitleForAccessibility() {
        return getContext().getString(mNumNotifications == 0 ?
                R.string.action_deep_shortcut :
                R.string.shortcuts_menu_with_notifications_description);
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
        if (mNotificationContainer != null) {
            mNotificationContainer.applyNotificationInfos(notificationInfos);
        }
    }

    protected void updateHiddenShortcuts() {
        int allowedCount = mNotificationContainer != null
                ? MAX_SHORTCUTS_IF_NOTIFICATIONS : MAX_SHORTCUTS;

        int total = mShortcuts.size();
        for (int i = 0; i < total; i++) {
            DeepShortcutView view = mShortcuts.get(i);
            view.setVisibility(i >= allowedCount ? GONE : VISIBLE);
        }
    }

    protected void initializeWidgetShortcut(ViewGroup container, SystemShortcut info) {
        View view = initializeSystemShortcut(R.layout.system_shortcut, container, info);
        view.getLayoutParams().width = mContainerWidth;
    }

    protected View initializeSystemShortcut(int resId, ViewGroup container, SystemShortcut info) {
        View view = inflateAndAdd(
                resId, container, getInsertIndexForSystemShortcut(container, info));
        if (view instanceof DeepShortcutView) {
            // Expanded system shortcut, with both icon and text shown on white background.
            final DeepShortcutView shortcutView = (DeepShortcutView) view;
            info.setIconAndLabelFor(shortcutView.getIconView(), shortcutView.getBubbleText());
        } else if (view instanceof ImageView) {
            // Only the system shortcut icon shows on a gray background header.
            info.setIconAndContentDescriptionFor((ImageView) view);
            view.setTooltipText(view.getContentDescription());
        }
        view.setTag(info);
        view.setOnClickListener(info);
        return view;
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
    public DragOptions.PreDragCondition createPreDragCondition(boolean updateIconUi) {
        return new DragOptions.PreDragCondition() {

            @Override
            public boolean shouldStartDrag(double distanceDragged) {
                return distanceDragged > mStartDragThreshold;
            }

            @Override
            public void onPreDragStart(DropTarget.DragObject dragObject) {
                if (!updateIconUi) {
                    return;
                }
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
                if (!updateIconUi) {
                    return;
                }
                mOriginalIcon.setIconVisible(true);
                if (dragStarted) {
                    // Make sure we keep the original icon hidden while it is being dragged.
                    mOriginalIcon.setVisibility(INVISIBLE);
                } else {
                    // TODO: add WW logging if want to add logging for long press on popup
                    //  container.
                    //  mLauncher.getUserEventDispatcher().logDeepShortcutsOpen(mOriginalIcon);
                    if (!mIsAboveIcon) {
                        // Show the icon but keep the text hidden.
                        mOriginalIcon.setVisibility(VISIBLE);
                        mOriginalIcon.setTextVisibility(false);
                    }
                }
            }
        };
    }

    protected void updateNotificationHeader() {
        ItemInfoWithIcon itemInfo = (ItemInfoWithIcon) mOriginalIcon.getTag();
        DotInfo dotInfo = mActivityContext.getDotInfoForItem(itemInfo);
        if (mNotificationContainer != null && dotInfo != null) {
            mNotificationContainer.updateHeader(dotInfo.getNotificationCount());
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
    protected void onCreateCloseAnimation(AnimatorSet anim) {
        // Animate original icon's text back in.
        anim.play(mOriginalIcon.createTextAlphaAnimator(true /* fadeIn */));
        mOriginalIcon.setForceHideDot(false);
    }

    @Override
    protected void closeComplete() {
        super.closeComplete();
        if (mActivityContext.getDragController() != null) {
            mActivityContext.getDragController().removeDragListener(this);
        }
        PopupContainerWithArrow openPopup = getOpen(mActivityContext);
        if (openPopup == null || openPopup.mOriginalIcon != mOriginalIcon) {
            mOriginalIcon.setTextVisibility(mOriginalIcon.shouldTextBeVisible());
            mOriginalIcon.setForceHideDot(false);
        }
    }

    /**
     * Returns a PopupContainerWithArrow which is already open or null
     */
    public static <T extends Context & ActivityContext> PopupContainerWithArrow getOpen(T context) {
        return getOpenView(context, TYPE_ACTION_POPUP);
    }

    /**
     * Dismisses the popup if it is no longer valid
     */
    public static void dismissInvalidPopup(BaseDraggingActivity activity) {
        PopupContainerWithArrow popup = getOpen(activity);
        if (popup != null && (!popup.mOriginalIcon.isAttachedToWindow()
                || !ShortcutUtil.supportsShortcuts((ItemInfo) popup.mOriginalIcon.getTag()))) {
            popup.animateClose();
        }
    }

    /**
     * Handler to control drag-and-drop for popup items
     */
    public interface PopupItemDragHandler extends OnLongClickListener, OnTouchListener { }

    /**
     * Drag and drop handler for popup items in Launcher activity
     */
    public static class LauncherPopupItemDragHandler implements PopupItemDragHandler {

        protected final Point mIconLastTouchPos = new Point();
        private final Launcher mLauncher;
        private final PopupContainerWithArrow mContainer;

        LauncherPopupItemDragHandler(Launcher launcher, PopupContainerWithArrow container) {
            mLauncher = launcher;
            mContainer = container;
        }

        @Override
        public boolean onTouch(View v, MotionEvent ev) {
            // Touched a shortcut, update where it was touched so we can drag from there on
            // long click.
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

            DraggableView draggableView = DraggableView.ofType(DraggableView.DRAGGABLE_ICON);
            WorkspaceItemInfo itemInfo = sv.getFinalInfo();
            itemInfo.container = CONTAINER_SHORTCUTS;
            DragView dv = mLauncher.getWorkspace().beginDragShared(sv.getIconView(), draggableView,
                    mContainer, itemInfo,
                    new ShortcutDragPreviewProvider(sv.getIconView(), iconShift),
                    new DragOptions());
            dv.animateShift(-iconShift.x, -iconShift.y);

            // TODO: support dragging from within folder without having to close it
            AbstractFloatingView.closeOpenContainer(mLauncher, AbstractFloatingView.TYPE_FOLDER);
            return false;
        }
    }
}
