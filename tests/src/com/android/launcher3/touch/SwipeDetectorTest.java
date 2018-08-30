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

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.launcher3.testcomponent.TouchEventGenerator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyFloat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SwipeDetectorTest {

    private static final String TAG = SwipeDetectorTest.class.getSimpleName();
    public static void L(String s, Object... parts) {
        Log.d(TAG, (parts.length == 0) ? s : String.format(s, parts));
    }

    private TouchEventGenerator mGenerator;
    private SwipeDetector mDetector;
    private int mTouchSlop;

    @Mock
    private SwipeDetector.Listener mMockListener;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mGenerator = new TouchEventGenerator(new TouchEventGenerator.Listener() {
            @Override
            public void onTouchEvent(MotionEvent event) {
                mDetector.onTouchEvent(event);
            }
        });

        mDetector = new SwipeDetector(mTouchSlop, mMockListener, SwipeDetector.VERTICAL);
        mDetector.setDetectableScrollConditions(SwipeDetector.DIRECTION_BOTH, false);
        mTouchSlop = ViewConfiguration.get(InstrumentationRegistry.getTargetContext())
                .getScaledTouchSlop();
        L("mTouchSlop=", mTouchSlop);
    }

    @Test
    public void testDragStart_vertical() throws Exception {
        mGenerator.put(0, 100, 100);
        mGenerator.move(0, 100, 100 + mTouchSlop);
        // TODO: actually calculate the following parameters and do exact value checks.
        verify(mMockListener).onDragStart(anyBoolean());
    }

    @Test
    public void testDragStart_failed() throws Exception {
        mGenerator.put(0, 100, 100);
        mGenerator.move(0, 100 + mTouchSlop, 100);
        // TODO: actually calculate the following parameters and do exact value checks.
        verify(mMockListener, never()).onDragStart(anyBoolean());
    }

    @Test
    public void testDragStart_horizontal() throws Exception {
        mDetector = new SwipeDetector(mTouchSlop, mMockListener, SwipeDetector.HORIZONTAL);
        mDetector.setDetectableScrollConditions(SwipeDetector.DIRECTION_BOTH, false);

        mGenerator.put(0, 100, 100);
        mGenerator.move(0, 100 + mTouchSlop, 100);
        // TODO: actually calculate the following parameters and do exact value checks.
        verify(mMockListener).onDragStart(anyBoolean());
    }

    @Test
    public void testDrag() throws Exception {
        mGenerator.put(0, 100, 100);
        mGenerator.move(0, 100, 100 + mTouchSlop);
        // TODO: actually calculate the following parameters and do exact value checks.
        verify(mMockListener).onDrag(anyFloat(), anyFloat());
    }

    @Test
    public void testDragEnd() throws Exception {
        mGenerator.put(0, 100, 100);
        mGenerator.move(0, 100, 100 + mTouchSlop);
        mGenerator.move(0, 100, 100 + mTouchSlop * 2);
        mGenerator.lift(0);
        // TODO: actually calculate the following parameters and do exact value checks.
        verify(mMockListener).onDragEnd(anyFloat(), anyBoolean());
    }
}
