/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.uioverrides;

import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.touch.SwipeDetector.DIRECTION_NEGATIVE;
import static com.android.launcher3.touch.SwipeDetector.DIRECTION_POSITIVE;
import static com.android.launcher3.touch.SwipeDetector.HORIZONTAL;
import static com.android.launcher3.touch.SwipeDetector.VERTICAL;
import static com.android.quickstep.TouchInteractionService.EDGE_NAV_BAR;

import android.graphics.Rect;
import android.metrics.LogMaker;
import android.view.MotionEvent;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.Launcher;
import com.android.launcher3.anim.SpringAnimationHandler;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.util.VerticalSwipeController;
import com.android.quickstep.RecentsView;

class EventLogTags {
    private EventLogTags() {
    }  // don't instantiate

    /** 524292 sysui_multi_action (content|4) */
    public static final int SYSUI_MULTI_ACTION = 524292;

    public static void writeSysuiMultiAction(Object[] content) {
        android.util.EventLog.writeEvent(SYSUI_MULTI_ACTION, content);
    }
}

class MetricsLogger {
    private static MetricsLogger sMetricsLogger;

    private static MetricsLogger getLogger() {
        if (sMetricsLogger == null) {
            sMetricsLogger = new MetricsLogger();
        }
        return sMetricsLogger;
    }

    protected void saveLog(Object[] rep) {
        EventLogTags.writeSysuiMultiAction(rep);
    }

    public void write(LogMaker content) {
        if (content.getType() == 0/*MetricsEvent.TYPE_UNKNOWN*/) {
            content.setType(4/*MetricsEvent.TYPE_ACTION*/);
        }
        saveLog(content.serialize());
    }
}

/**
 * Extension of {@link VerticalSwipeController} to go from NORMAL to OVERVIEW.
 */
public class EdgeSwipeController extends VerticalSwipeController implements
        OnDeviceProfileChangeListener {

    private static final Rect sTempRect = new Rect();

    private final MetricsLogger mMetricsLogger = new MetricsLogger();

    public EdgeSwipeController(Launcher l) {
        super(l, NORMAL, OVERVIEW, l.getDeviceProfile().isVerticalBarLayout()
                ? HORIZONTAL : VERTICAL);
        l.addOnDeviceProfileChangeListener(this);
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        mDetector.updateDirection(dp.isVerticalBarLayout() ? HORIZONTAL : VERTICAL);
    }

    @Override
    protected boolean shouldInterceptTouch(MotionEvent ev) {
        return mLauncher.isInState(NORMAL) && (ev.getEdgeFlags() & EDGE_NAV_BAR) != 0;
    }

    @Override
    protected int getSwipeDirection(MotionEvent ev) {
        return isTransitionFlipped() ? DIRECTION_NEGATIVE : DIRECTION_POSITIVE;
    }

    @Override
    protected boolean isTransitionFlipped() {
        return mLauncher.getDeviceProfile().isSeascape();
    }

    @Override
    protected void onTransitionComplete(boolean wasFling, boolean stateChanged) {
        if (stateChanged && mToState instanceof OverviewState) {
            // Mimic ActivityMetricsLogger.logAppTransitionMultiEvents() logging for
            // "Recents" activity for app transition tests.
            final LogMaker builder = new LogMaker(761/*APP_TRANSITION*/);
            builder.setPackageName("com.android.systemui");
            builder.addTaggedData(871/*FIELD_CLASS_NAME*/,
                    "com.android.systemui.recents.RecentsActivity");
            builder.addTaggedData(319/*APP_TRANSITION_DELAY_MS*/,
                    0/* zero time */);
            mMetricsLogger.write(builder);
        }
        // TODO: Log something
    }

    @Override
    protected void initSprings() {
        mSpringHandlers = new SpringAnimationHandler[0];
    }

    @Override
    protected float getShiftRange() {
        return getShiftRange(mLauncher);
    }

    public static float getShiftRange(Launcher launcher) {
        RecentsView.getPageRect(launcher.getDeviceProfile(), launcher, sTempRect);
        DragLayer dl = launcher.getDragLayer();
        Rect insets = dl.getInsets();
        DeviceProfile dp = launcher.getDeviceProfile();

        if (dp.isVerticalBarLayout()) {
            if (dp.isSeascape()) {
                return insets.left + sTempRect.left;
            } else {
                return dl.getWidth() - sTempRect.right + insets.right;
            }
        } else {
            return dl.getHeight() - sTempRect.bottom + insets.bottom;
        }
    }
}
