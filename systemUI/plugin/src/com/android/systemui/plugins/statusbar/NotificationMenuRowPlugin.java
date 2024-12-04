/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.plugins.statusbar;

import android.annotation.Nullable;
import android.content.Context;
import android.graphics.Point;
import android.service.notification.StatusBarNotification;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.android.systemui.plugins.Plugin;
import com.android.systemui.plugins.annotations.DependsOn;
import com.android.systemui.plugins.annotations.ProvidesInterface;
import com.android.systemui.plugins.statusbar.NotificationMenuRowPlugin.MenuItem;
import com.android.systemui.plugins.statusbar.NotificationMenuRowPlugin.OnMenuEventListener;
import com.android.systemui.plugins.statusbar.NotificationSwipeActionHelper.SnoozeOption;

import java.util.ArrayList;

@ProvidesInterface(action = NotificationMenuRowPlugin.ACTION,
        version = NotificationMenuRowPlugin.VERSION)
@DependsOn(target = OnMenuEventListener.class)
@DependsOn(target = MenuItem.class)
@DependsOn(target = NotificationSwipeActionHelper.class)
@DependsOn(target = SnoozeOption.class)
public interface NotificationMenuRowPlugin extends Plugin {

    public static final String ACTION = "com.android.systemui.action.PLUGIN_NOTIFICATION_MENU_ROW";
    public static final int VERSION = 5;

    @ProvidesInterface(version = OnMenuEventListener.VERSION)
    public interface OnMenuEventListener {
        public static final int VERSION = 1;

        public void onMenuClicked(View row, int x, int y, MenuItem menu);

        public void onMenuReset(View row);

        public void onMenuShown(View row);
    }

    @ProvidesInterface(version = MenuItem.VERSION)
    public interface MenuItem {
        public static final int VERSION = 1;

        public View getMenuView();

        public View getGutsView();

        public String getContentDescription();
    }

    /**
     * @return a list of items to populate the menu 'behind' a notification.
     */
    public ArrayList<MenuItem> getMenuItems(Context context);

    /**
     * @return the {@link MenuItem} to display when a notification is long pressed.
     */
    public MenuItem getLongpressMenuItem(Context context);

    /**
     * @return the {@link MenuItem} to display when feedback icon is pressed.
     */
    public MenuItem getFeedbackMenuItem(Context context);

    /**
     * @return the {@link MenuItem} to display when snooze item is pressed.
     */
    public MenuItem getSnoozeMenuItem(Context context);

    public void setMenuItems(ArrayList<MenuItem> items);

    /**
     * If this returns {@code true}, then the menu row will bind and fade in the notification guts
     * view for the menu item it holds.
     *
     * @see #menuItemToExposeOnSnap()
     * @return whether or not to immediately expose the notification guts
     */
    default boolean shouldShowGutsOnSnapOpen() {
        return false;
    }

    /**
     * When #shouldShowGutsOnExpose is true, this method must return the menu item to expose on
     * #onSnapOpen. Otherwise we will fall back to the default behavior of fading in the menu row
     *
     * @return the {@link MenuItem} containing the NotificationGuts that should be exposed
     */
    @Nullable
    default MenuItem menuItemToExposeOnSnap() {
        return null;
    }

    /**
     * Get the origin for the circular reveal animation when expanding the notification guts. Only
     * used when #shouldShowGutsOnSnapOpen is true
     * @return the x,y coordinates for the start of the animation
     */
    @Nullable
    default Point getRevealAnimationOrigin() {
        return new Point(0, 0);
    }

    public void setMenuClickListener(OnMenuEventListener listener);

    public void setAppName(String appName);

    public void createMenu(ViewGroup parent, StatusBarNotification sbn);

    public void resetMenu();

    public View getMenuView();

    /**
     * Get the target position that a notification row should be snapped open to in order to reveal
     * the menu. This is generally determined by the number of icons in the notification menu and the
     * size of each icon. This method accounts for whether the menu appears on the left or ride side
     * of the parent notification row.
     *

     * @return an int representing the x-offset in pixels that the notification should snap open to.
     * Positive values imply that the notification should be offset to the right to reveal the menu,
     * and negative alues imply that the notification should be offset to the right.
     */
    public int getMenuSnapTarget();

    /**
     * Determines whether or not the menu should be shown in response to user input.
     * @return true if the menu should be shown, false otherwise.
     */
    public boolean shouldShowMenu();

    /**
     * Determines whether the menu is currently visible.
     * @return true if the menu is visible, false otherwise.
     */
    public boolean isMenuVisible();

    /**
     * Determines whether a given movement is towards or away from the current location of the menu.
     * @param movement
     * @return true if the movement is towards the menu, false otherwise.
     */
    public boolean isTowardsMenu(float movement);

    /**
     * Determines whether the menu should snap closed instead of dismissing the
     * parent notification, as a function of its current state.
     *
     * @return true if the menu should snap closed, false otherwise.
     */
    public boolean shouldSnapBack();

    /**
     * Determines whether the menu was previously snapped open to the same side that it is currently
     * being shown on.
     * @return true if the menu is snapped open to the same side on which it currently appears,
     * false otherwise.
     */
    public boolean isSnappedAndOnSameSide();

    /**
     * Determines whether the notification the menu is attached to is able to be dismissed.
     * @return true if the menu's parent notification is dismissable, false otherwise.
     */
    public boolean canBeDismissed();

    /**
     * Determines whether the menu should remain open given its current state, or snap closed.
     * @return true if the menu should remain open, false otherwise.
     */
    public boolean isWithinSnapMenuThreshold();

    /**
     * Determines whether the menu has been swiped far enough to snap open.
     * @return true if the menu has been swiped far enough to open, false otherwise.
     */
    public boolean isSwipedEnoughToShowMenu();

    public default boolean onInterceptTouchEvent(View view, MotionEvent ev) {
        return false;
    }

    public default boolean shouldUseDefaultMenuItems() {
        return false;
    }

    /**
     * Callback used to signal the menu that its parent's translation has changed.
     * @param translation The new x-translation of the menu as a position (not an offset).
     */
    public void onParentTranslationUpdate(float translation);

    /**
     * Callback used to signal the menu that its parent's height has changed.
     */
    public void onParentHeightUpdate();

    /**
     * Callback used to signal the menu that its parent notification has been updated.
     * @param sbn
     */
    public void onNotificationUpdated(StatusBarNotification sbn);

    /**
     * Callback used to signal the menu that a user is moving the parent notification.
     * @param delta The change in the parent notification's position.
     */
    public void onTouchMove(float delta);

    /**
     * Callback used to signal the menu that a user has begun touching its parent notification.
     */
    public void onTouchStart();

    /**
     * Callback used to signal the menu that a user has finished touching its parent notification.
     */
    public void onTouchEnd();

    /**
     * Callback used to signal the menu that it has been snapped closed.
     */
    public void onSnapClosed();

    /**
     * Callback used to signal the menu that it has been snapped open.
     */
    public void onSnapOpen();

    /**
     * Callback used to signal the menu that its parent notification has been dismissed.
     */
    public void onDismiss();

    public default void onConfigurationChanged() { }

}
