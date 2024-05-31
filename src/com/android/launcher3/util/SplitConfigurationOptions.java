/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.launcher3.util;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_APP_ICON_MENU_SPLIT_LEFT_TOP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_APP_ICON_MENU_SPLIT_RIGHT_BOTTOM;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.IntDef;

import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.ItemInfo;

import java.lang.annotation.Retention;

public final class SplitConfigurationOptions {

    ///////////////////////////////////
    // Taken from
    // frameworks/base/libs/WindowManager/Shell/src/com/android/wm/shell/splitscreen/SplitScreen.java
    /**
     * Stage position isn't specified normally meaning to use what ever it is currently set to.
     */
    public static final int STAGE_POSITION_UNDEFINED = -1;
    /**
     * Specifies that a stage is positioned at the top half of the screen if
     * in portrait mode or at the left half of the screen if in landscape mode.
     */
    public static final int STAGE_POSITION_TOP_OR_LEFT = 0;

    /**
     * Specifies that a stage is positioned at the bottom half of the screen if
     * in portrait mode or at the right half of the screen if in landscape mode.
     */
    public static final int STAGE_POSITION_BOTTOM_OR_RIGHT = 1;

    @Retention(SOURCE)
    @IntDef({STAGE_POSITION_UNDEFINED, STAGE_POSITION_TOP_OR_LEFT, STAGE_POSITION_BOTTOM_OR_RIGHT})
    public @interface StagePosition {}

    /**
     * Stage type isn't specified normally meaning to use what ever the default is.
     * E.g. exit split-screen and launch the app in fullscreen.
     */
    public static final int STAGE_TYPE_UNDEFINED = -1;
    /**
     * The main stage type.
     */
    public static final int STAGE_TYPE_MAIN = 0;

    /**
     * The side stage type.
     */
    public static final int STAGE_TYPE_SIDE = 1;

    @IntDef({STAGE_TYPE_UNDEFINED, STAGE_TYPE_MAIN, STAGE_TYPE_SIDE})
    public @interface StageType {}
    ///////////////////////////////////

    public static class SplitPositionOption {
        public final int iconResId;
        public final int textResId;
        @StagePosition
        public final int stagePosition;

        @StageType
        public final int mStageType;

        public SplitPositionOption(int iconResId, int textResId, int stagePosition, int stageType) {
            this.iconResId = iconResId;
            this.textResId = textResId;
            this.stagePosition = stagePosition;
            mStageType = stageType;
        }
    }

    /**
     * NOTE: Engineers complained about too little ambiguity in the last survey, so there is a class
     * with the same name/functionality in wm.shell.util (which launcher3 cannot be built against)
     *
     * If you make changes here, consider making the same changes there
     * TODO(b/254378592): We really need to consolidate this
     */
    public static class SplitBounds {
        public final Rect leftTopBounds;
        public final Rect rightBottomBounds;
        /** This rect represents the actual gap between the two apps */
        public final Rect visualDividerBounds;
        // This class is orientation-agnostic, so we compute both for later use
        public final float topTaskPercent;
        public final float leftTaskPercent;
        public final float dividerWidthPercent;
        public final float dividerHeightPercent;
        public final int snapPosition;

        /**
         * If {@code true}, that means at the time of creation of this object, the
         * split-screened apps were vertically stacked. This is useful in scenarios like
         * rotation where the bounds won't change, but this variable can indicate what orientation
         * the bounds were originally in
         */
        public final boolean appsStackedVertically;
        /**
         * If {@code true}, that means at the time of creation of this object, the phone was in
         * seascape orientation. This is important on devices with insets, because they do not split
         * evenly -- one of the insets must be slightly larger to account for the inset.
         * From landscape, it is the leftTop task that expands slightly.
         * From seascape, it is the rightBottom task that expands slightly.
         */
        public final boolean initiatedFromSeascape;
        public final int leftTopTaskId;
        public final int rightBottomTaskId;

        public SplitBounds(Rect leftTopBounds, Rect rightBottomBounds, int leftTopTaskId,
                int rightBottomTaskId, int snapPosition) {
            this.leftTopBounds = leftTopBounds;
            this.rightBottomBounds = rightBottomBounds;
            this.leftTopTaskId = leftTopTaskId;
            this.rightBottomTaskId = rightBottomTaskId;
            this.snapPosition = snapPosition;

            if (rightBottomBounds.top > leftTopBounds.top) {
                // vertical apps, horizontal divider
                this.visualDividerBounds = new Rect(leftTopBounds.left, leftTopBounds.bottom,
                        leftTopBounds.right, rightBottomBounds.top);
                appsStackedVertically = true;
                initiatedFromSeascape = false;
            } else {
                // horizontal apps, vertical divider
                this.visualDividerBounds = new Rect(leftTopBounds.right, leftTopBounds.top,
                        rightBottomBounds.left, leftTopBounds.bottom);
                appsStackedVertically = false;
                // The following check is unreliable on devices without insets
                // (initiatedFromSeascape will always be set to false.) This happens to be OK for
                // all our current uses, but should be refactored.
                // TODO: Create a more reliable check, or refactor how splitting works on devices
                //  with insets.
                if (rightBottomBounds.width() > leftTopBounds.width()) {
                    initiatedFromSeascape = true;
                } else {
                    initiatedFromSeascape = false;
                }
            }

            float totalWidth = rightBottomBounds.right - leftTopBounds.left;
            float totalHeight = rightBottomBounds.bottom - leftTopBounds.top;
            leftTaskPercent = leftTopBounds.width() / totalWidth;
            topTaskPercent = leftTopBounds.height() / totalHeight;
            dividerWidthPercent = visualDividerBounds.width() / totalWidth;
            dividerHeightPercent = visualDividerBounds.height() / totalHeight;
        }

        @Override
        public String toString() {
            return "LeftTop: " + leftTopBounds + ", taskId: " + leftTopTaskId + "\n"
                    + "RightBottom: " + rightBottomBounds + ", taskId: " + rightBottomTaskId +  "\n"
                    + "Divider: " + visualDividerBounds + "\n"
                    + "AppsVertical? " + appsStackedVertically + "\n"
                    + "snapPosition: " + snapPosition;
        }
    }

    public static class SplitStageInfo {
        public int taskId = -1;
        @StagePosition
        public int stagePosition = STAGE_POSITION_UNDEFINED;
        @StageType
        public int stageType = STAGE_TYPE_UNDEFINED;

        @Override
        public String toString() {
            return "SplitStageInfo { taskId=" + taskId
                    + ", stagePosition=" + stagePosition + ", stageType=" + stageType + " }";
        }
    }

    public static StatsLogManager.EventEnum getLogEventForPosition(@StagePosition int position) {
        return position == STAGE_POSITION_TOP_OR_LEFT
                ? LAUNCHER_APP_ICON_MENU_SPLIT_LEFT_TOP
                : LAUNCHER_APP_ICON_MENU_SPLIT_RIGHT_BOTTOM;
    }

    public static @StagePosition int getOppositeStagePosition(@StagePosition int position) {
        if (position == STAGE_POSITION_UNDEFINED) {
            return position;
        }
        return position == STAGE_POSITION_TOP_OR_LEFT ? STAGE_POSITION_BOTTOM_OR_RIGHT
                : STAGE_POSITION_TOP_OR_LEFT;
    }

    public static class SplitSelectSource {

        /** Keep in sync w/ ActivityTaskManager#INVALID_TASK_ID (unreference-able) */
        private static final int INVALID_TASK_ID = -1;

        private View view;
        private Drawable drawable;
        public final Intent intent;
        public final SplitPositionOption position;
        public final ItemInfo itemInfo;
        public final StatsLogManager.EventEnum splitEvent;
        /** Represents the taskId of the first app to start in split screen */
        public int alreadyRunningTaskId = INVALID_TASK_ID;
        /**
         * If {@code true}, animates the view represented by {@link #alreadyRunningTaskId} into the
         * split placeholder view
         */
        public boolean animateCurrentTaskDismissal;

        public SplitSelectSource(View view, Drawable drawable, Intent intent,
                SplitPositionOption position, ItemInfo itemInfo,
                StatsLogManager.EventEnum splitEvent) {
            this.view = view;
            this.drawable = drawable;
            this.intent = intent;
            this.position = position;
            this.itemInfo = itemInfo;
            this.splitEvent = splitEvent;
        }

        public Drawable getDrawable() {
            return drawable;
        }

        public View getView() {
            return view;
        }
    }
}
