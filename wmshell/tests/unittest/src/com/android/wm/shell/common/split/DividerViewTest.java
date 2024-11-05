/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.wm.shell.common.split;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;

import static com.android.internal.config.sysui.SystemUiDeviceConfigFlags.CURSOR_HOVER_STATES_ENABLED;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.SystemClock;
import android.provider.DeviceConfig;
import android.view.InputDevice;
import android.view.InsetsState;
import android.view.MotionEvent;

import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link DividerView} */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class DividerViewTest extends ShellTestCase {
    private @Mock SplitWindowManager.ParentContainerCallbacks mCallbacks;
    private @Mock SplitLayout.SplitLayoutHandler mSplitLayoutHandler;
    private @Mock DisplayController mDisplayController;
    private @Mock DisplayImeController mDisplayImeController;
    private @Mock ShellTaskOrganizer mTaskOrganizer;
    private SplitLayout mSplitLayout;
    private DividerView mDividerView;

    @Before
    @UiThreadTest
    public void setup() {
        MockitoAnnotations.initMocks(this);
        Configuration configuration = getConfiguration();
        mSplitLayout = new SplitLayout("TestSplitLayout", mContext, configuration,
                mSplitLayoutHandler, mCallbacks, mDisplayController, mDisplayImeController,
                mTaskOrganizer, SplitLayout.PARALLAX_NONE);
        SplitWindowManager splitWindowManager = new SplitWindowManager("TestSplitWindowManager",
                mContext,
                configuration, mCallbacks);
        splitWindowManager.init(mSplitLayout, new InsetsState(), false /* isRestoring */);
        mDividerView = spy((DividerView) splitWindowManager.getDividerView());
    }

    @Test
    @UiThreadTest
    public void testHoverDividerView() {
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_SYSTEMUI, CURSOR_HOVER_STATES_ENABLED,
                "true", false);

        Rect dividerBounds = mSplitLayout.getDividerBounds();
        int x = dividerBounds.centerX();
        int y = dividerBounds.centerY();
        long downTime = SystemClock.uptimeMillis();
        mDividerView.onHoverEvent(getMotionEvent(downTime, MotionEvent.ACTION_HOVER_ENTER, x, y));

        verify(mDividerView, times(1)).setHovering();

        mDividerView.onHoverEvent(getMotionEvent(downTime, MotionEvent.ACTION_HOVER_EXIT, x, y));

        verify(mDividerView, times(1)).releaseHovering();

        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_SYSTEMUI, CURSOR_HOVER_STATES_ENABLED,
                "false", false);
    }

    private static MotionEvent getMotionEvent(long eventTime, int action, float x, float y) {
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = MotionEvent.TOOL_TYPE_UNKNOWN;

        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.pressure = 1;
        coords.size = 1;
        coords.x = x;
        coords.y = y;

        return MotionEvent.obtain(eventTime, eventTime, action, 1,
                new MotionEvent.PointerProperties[]{properties},
                new MotionEvent.PointerCoords[]{coords}, 0, 0, 1.0f, 1.0f, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0);
    }

    private static Configuration getConfiguration() {
        final Configuration configuration = new Configuration();
        configuration.unset();
        configuration.orientation = ORIENTATION_LANDSCAPE;
        configuration.windowConfiguration.setRotation(0);
        configuration.windowConfiguration.setBounds(new Rect(0, 0, 1080, 2160));
        return configuration;
    }
}
