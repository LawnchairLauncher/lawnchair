/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.touch;

import static com.android.launcher3.touch.SingleAxisSwipeDetector.DIRECTION_BOTH;
import static com.android.launcher3.touch.SingleAxisSwipeDetector.DIRECTION_NEGATIVE;
import static com.android.launcher3.touch.SingleAxisSwipeDetector.DIRECTION_POSITIVE;
import static com.android.launcher3.touch.SingleAxisSwipeDetector.HORIZONTAL;
import static com.android.launcher3.touch.SingleAxisSwipeDetector.VERTICAL;

import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyFloat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.util.Log;
import android.view.ViewConfiguration;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.testcomponent.TouchEventGenerator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SingleAxisSwipeDetectorTest {

    private static final String TAG = SingleAxisSwipeDetectorTest.class.getSimpleName();
    public static void L(String s, Object... parts) {
        Log.d(TAG, (parts.length == 0) ? s : String.format(s, parts));
    }

    private TouchEventGenerator mGenerator;
    private SingleAxisSwipeDetector mDetector;
    private int mTouchSlop;

    @Mock
    private SingleAxisSwipeDetector.Listener mMockListener;

    @Mock
    private ViewConfiguration mMockConfig;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mGenerator = new TouchEventGenerator((ev) -> mDetector.onTouchEvent(ev));
        ViewConfiguration orgConfig = ViewConfiguration
                .get(InstrumentationRegistry.getTargetContext());
        doReturn(orgConfig.getScaledMaximumFlingVelocity()).when(mMockConfig)
                .getScaledMaximumFlingVelocity();

        mDetector = new SingleAxisSwipeDetector(mMockConfig, mMockListener, VERTICAL, false);
        mDetector.setDetectableScrollConditions(DIRECTION_BOTH, false);
        mTouchSlop = orgConfig.getScaledTouchSlop();
        doReturn(mTouchSlop).when(mMockConfig).getScaledTouchSlop();

        L("mTouchSlop=", mTouchSlop);
    }

    @Test
    public void testDragStart_verticalPositive() {
        mDetector = new SingleAxisSwipeDetector(mMockConfig, mMockListener, VERTICAL, false);
        mDetector.setDetectableScrollConditions(DIRECTION_POSITIVE, false);
        mGenerator.put(0, 100, 100);
        mGenerator.move(0, 100, 100 - mTouchSlop);
        // TODO: actually calculate the following parameters and do exact value checks.
        verify(mMockListener).onDragStart(anyBoolean());
    }

    @Test
    public void testDragStart_verticalNegative() {
        mDetector = new SingleAxisSwipeDetector(mMockConfig, mMockListener, VERTICAL, false);
        mDetector.setDetectableScrollConditions(DIRECTION_NEGATIVE, false);
        mGenerator.put(0, 100, 100);
        mGenerator.move(0, 100, 100 + mTouchSlop);
        // TODO: actually calculate the following parameters and do exact value checks.
        verify(mMockListener).onDragStart(anyBoolean());
    }

    @Test
    public void testDragStart_failed() {
        mGenerator.put(0, 100, 100);
        mGenerator.move(0, 100 + mTouchSlop, 100);
        // TODO: actually calculate the following parameters and do exact value checks.
        verify(mMockListener, never()).onDragStart(anyBoolean());
    }

    @Test
    public void testDragStart_horizontalPositive() {
        mDetector = new SingleAxisSwipeDetector(mMockConfig, mMockListener, HORIZONTAL, false);
        mDetector.setDetectableScrollConditions(DIRECTION_POSITIVE, false);

        mGenerator.put(0, 100, 100);
        mGenerator.move(0, 100 + mTouchSlop, 100);
        // TODO: actually calculate the following parameters and do exact value checks.
        verify(mMockListener).onDragStart(anyBoolean());
    }

    @Test
    public void testDragStart_horizontalNegative() {
        mDetector = new SingleAxisSwipeDetector(mMockConfig, mMockListener, HORIZONTAL, false);
        mDetector.setDetectableScrollConditions(DIRECTION_NEGATIVE, false);

        mGenerator.put(0, 100, 100);
        mGenerator.move(0, 100 - mTouchSlop, 100);
        // TODO: actually calculate the following parameters and do exact value checks.
        verify(mMockListener).onDragStart(anyBoolean());
    }

    @Test
    public void testDragStart_horizontalRtlPositive() {
        mDetector = new SingleAxisSwipeDetector(mMockConfig, mMockListener, HORIZONTAL, true);
        mDetector.setDetectableScrollConditions(DIRECTION_POSITIVE, false);

        mGenerator.put(0, 100, 100);
        mGenerator.move(0, 100 - mTouchSlop, 100);
        // TODO: actually calculate the following parameters and do exact value checks.
        verify(mMockListener).onDragStart(anyBoolean());
    }

    @Test
    public void testDragStart_horizontalRtlNegative() {
        mDetector = new SingleAxisSwipeDetector(mMockConfig, mMockListener, HORIZONTAL, true);
        mDetector.setDetectableScrollConditions(DIRECTION_NEGATIVE, false);

        mGenerator.put(0, 100, 100);
        mGenerator.move(0, 100 + mTouchSlop, 100);
        // TODO: actually calculate the following parameters and do exact value checks.
        verify(mMockListener).onDragStart(anyBoolean());
    }

    @Test
    public void testDrag() {
        mGenerator.put(0, 100, 100);
        mGenerator.move(0, 100, 100 + mTouchSlop);
        // TODO: actually calculate the following parameters and do exact value checks.
        verify(mMockListener).onDrag(anyFloat(), anyObject());
    }

    @Test
    public void testDragEnd() {
        mGenerator.put(0, 100, 100);
        mGenerator.move(0, 100, 100 + mTouchSlop);
        mGenerator.move(0, 100, 100 + mTouchSlop * 2);
        mGenerator.lift(0);
        // TODO: actually calculate the following parameters and do exact value checks.
        verify(mMockListener).onDragEnd(anyFloat());
    }
}
