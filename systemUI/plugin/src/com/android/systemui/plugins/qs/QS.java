/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.systemui.plugins.qs;

import android.view.View;

import androidx.annotation.FloatRange;

import com.android.systemui.plugins.FragmentBase;
import com.android.systemui.plugins.annotations.DependsOn;
import com.android.systemui.plugins.annotations.ProvidesInterface;
import com.android.systemui.plugins.qs.QS.HeightListener;

import java.util.function.Consumer;

/**
 * Fragment that contains QS in the notification shade.  Most of the interface is for
 * handling the expand/collapsing of the view interaction.
 */
@ProvidesInterface(action = QS.ACTION, version = QS.VERSION)
@DependsOn(target = HeightListener.class)
public interface QS extends FragmentBase {

    String ACTION = "com.android.systemui.action.PLUGIN_QS";

    int VERSION = 15;

    String TAG = "QS";

    void setPanelView(HeightListener notificationPanelView);

    void hideImmediately();
    int getQsMinExpansionHeight();
    int getDesiredHeight();
    void setHeightOverride(int desiredHeight);
    void setHeaderClickable(boolean qsExpansionEnabled);
    boolean isCustomizing();
    /** Close the QS customizer, if it is open. */
    void closeCustomizer();
    void setOverscrolling(boolean overscrolling);
    void setExpanded(boolean qsExpanded);
    void setListening(boolean listening);

    /**
     * Set whether QQS/QS is visible or not.
     *
     * This is different from setExpanded, as it will be true when QQS is visible. In particular,
     * it should be false when device is locked and only notifications (in lockscreen) are visible.
     */
    void setQsVisible(boolean qsVisible);
    boolean isShowingDetail();
    void closeDetail();
    void animateHeaderSlidingOut();

    /**
     * Asks QS to update its presentation, according to {@code NotificationPanelViewController}.
     * @param qsExpansionFraction How much each UI element in QS should be expanded (QQS to QS.)
     * @param panelExpansionFraction Whats the expansion of the whole shade.
     * @param headerTranslation How much we should vertically translate QS.
     * @param squishinessFraction Fraction that affects tile height. 0 when collapsed,
     *                            1 when expanded.
     */
    void setQsExpansion(float qsExpansionFraction, float panelExpansionFraction,
            float headerTranslation, float squishinessFraction);
    void setHeaderListening(boolean listening);
    void notifyCustomizeChanged();
    void setContainerController(QSContainerController controller);

    /**
     * Provide an action to collapse if expanded or expand if collapsed.
     * @param action
     */
    void setCollapseExpandAction(Runnable action);

    /**
     * Returns the height difference between the QSPanel container and the QuickQSPanel container
     */
    int getHeightDiff();

    View getHeader();

    default void setHasNotifications(boolean hasNotifications) {
    }

    /** Sets whether the squishiness fraction should be updated on the media host. */
    default void setShouldUpdateSquishinessOnMedia(boolean shouldUpdate) {}

    /**
     * Should touches from the notification panel be disallowed?
     * The notification panel might grab any touches rom QS at any time to collapse the shade.
     * We should disallow that in case we are showing the detail panel.
     */
    default boolean disallowPanelTouches() {
        return isShowingDetail();
    }

    /**
     * If QS should translate as we pull it down, or if it should be static.
     */
    void setInSplitShade(boolean shouldTranslate);

    /**
     * Sets the progress of the transition to full shade on the lockscreen.
     *
     * @param isTransitioningToFullShade
     *        whether the transition to full shade is in progress. This might be {@code true}, even
     *        though {@code qsTransitionFraction} is still 0.
     *        The reason for that is that on some device configurations, the QS transition has a
     *        start delay compared to the overall transition.
     *
     * @param qsTransitionFraction
     *        the fraction of the QS transition progress, from 0 to 1.
     *
     * @param qsSquishinessFraction
     *        the fraction of the QS "squish" transition progress, from 0 to 1.
     */
    default void setTransitionToFullShadeProgress(
            boolean isTransitioningToFullShade,
            @FloatRange(from = 0.0, to = 1.0) float qsTransitionFraction,
            @FloatRange(from = 0.0, to = 1.0) float qsSquishinessFraction) {}

    /**
     * A rounded corner clipping that makes QS feel as if it were behind everything.
     */
    void setFancyClipping(int leftInset, int top, int rightInset, int bottom, int cornerRadius,
            boolean visible, boolean fullWidth);

    /**
     * @return if quick settings is fully collapsed currently
     */
    default boolean isFullyCollapsed() {
        return true;
    }

    /**
     * Add a listener for when the collapsed media visibility changes.
     */
    void setCollapsedMediaVisibilityChangedListener(Consumer<Boolean> listener);

    /**
     * Set a scroll listener for the QSPanel container
     */
    default void setScrollListener(ScrollListener scrollListener) {}

    /**
     * Sets the amount of vertical over scroll that should be performed on QS.
     */
    default void setOverScrollAmount(int overScrollAmount) {}

    /**
     * Sets whether the notification panel is using the full width of the screen. Typically true on
     * small screens and false on large screens.
     */
    void setIsNotificationPanelFullWidth(boolean isFullWidth);

    /**
     * Callback for when QSPanel container is scrolled
     */
    @ProvidesInterface(version = ScrollListener.VERSION)
    interface ScrollListener {
        int VERSION = 1;
        void onQsPanelScrollChanged(int scrollY);
    }

    @ProvidesInterface(version = HeightListener.VERSION)
    interface HeightListener {
        int VERSION = 1;
        void onQsHeightChanged();
    }

}
