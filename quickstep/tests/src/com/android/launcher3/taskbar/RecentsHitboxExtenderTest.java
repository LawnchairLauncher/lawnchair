package com.android.launcher3.taskbar;

import static android.view.MotionEvent.ACTION_DOWN;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.DeviceProfile;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.Supplier;

@RunWith(AndroidJUnit4.class)
public class RecentsHitboxExtenderTest {

    private static final int TASKBAR_OFFSET_Y = 35;
    private static final int BUTTON_WIDTH = 10;
    private static final int BUTTON_HEIGHT = 10;

    private final RecentsHitboxExtender mHitboxExtender = new RecentsHitboxExtender();
    @Mock
    View mMockRecentsButton;
    @Mock
    View mMockRecentsParent;
    @Mock
    DeviceProfile mMockDeviceProfile;
    @Mock
    Handler mMockHandler;
    Context mContext;

    float[] mRecentsCoords = new float[]{0,0};
    private final Supplier<float[]> mSupplier = () -> mRecentsCoords;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = instrumentation.getContext();
        mHitboxExtender.init(mMockRecentsButton, mMockRecentsParent, mMockDeviceProfile, mSupplier,
                mMockHandler);
        when(mMockDeviceProfile.getTaskbarOffsetY()).thenReturn(TASKBAR_OFFSET_Y);
        when(mMockRecentsButton.getContext()).thenReturn(mContext);
        when(mMockRecentsButton.getWidth()).thenReturn(BUTTON_WIDTH);
        when(mMockRecentsButton.getHeight()).thenReturn(BUTTON_HEIGHT);
    }

    @Test
    public void noRecentsButtonClick_notActive() {
        mHitboxExtender.onAnimationProgressToOverview(0);
        mHitboxExtender.onAnimationProgressToOverview(0.5f);
        assertFalse(mHitboxExtender.extendedHitboxEnabled());
    }

    @Test
    public void recentsButtonClick_active() {
        mHitboxExtender.onRecentsButtonClicked();
        mHitboxExtender.onAnimationProgressToOverview(0);
        mHitboxExtender.onAnimationProgressToOverview(0.5f);
        assertTrue(mHitboxExtender.extendedHitboxEnabled());
    }

    @Test
    public void homeToTaskbar_notActive() {
        mHitboxExtender.onAnimationProgressToOverview(1);
        mHitboxExtender.onAnimationProgressToOverview(0.5f);
        assertFalse(mHitboxExtender.extendedHitboxEnabled());
    }

    @Test
    public void animationEndReset() {
        mHitboxExtender.onRecentsButtonClicked();
        mHitboxExtender.onAnimationProgressToOverview(0);
        mHitboxExtender.onAnimationProgressToOverview(0.5f);
        assertTrue(mHitboxExtender.extendedHitboxEnabled());
        mHitboxExtender.onAnimationProgressToOverview(1);
        verify(mMockHandler, times(1)).postDelayed(any(), anyLong());
    }

    @Test
    public void motionWithinHitbox() {
        mHitboxExtender.onRecentsButtonClicked();
        mHitboxExtender.onAnimationProgressToOverview(0);
        mHitboxExtender.onAnimationProgressToOverview(0.5f);
        assertTrue(mHitboxExtender.extendedHitboxEnabled());
        // Center width, past height but w/in offset bounds
        MotionEvent motionEvent = getMotionEvent(ACTION_DOWN,
                BUTTON_WIDTH / 2, BUTTON_HEIGHT + TASKBAR_OFFSET_Y / 2);
        assertTrue(mHitboxExtender.onControllerInterceptTouchEvent(motionEvent));
    }

    @Test
    public void motionOutsideHitbox() {
        mHitboxExtender.onRecentsButtonClicked();
        mHitboxExtender.onAnimationProgressToOverview(0);
        mHitboxExtender.onAnimationProgressToOverview(0.5f);
        assertTrue(mHitboxExtender.extendedHitboxEnabled());
        // Center width, past height and offset
        MotionEvent motionEvent = getMotionEvent(ACTION_DOWN,
                BUTTON_WIDTH / 2, BUTTON_HEIGHT + TASKBAR_OFFSET_Y * 2);
        assertFalse(mHitboxExtender.onControllerInterceptTouchEvent(motionEvent));
    }

    private MotionEvent getMotionEvent(int action, int x, int y) {
        return MotionEvent.obtain(0, 0, action, x, y, 0);
    }
}
