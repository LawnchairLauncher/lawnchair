package com.android.wm.shell.recents;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;

import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.shared.split.SplitBounds;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class SplitBoundsTest extends ShellTestCase {
    private static final int DEVICE_WIDTH = 100;
    private static final int DEVICE_LENGTH = 200;
    private static final int DIVIDER_SIZE = 20;
    private static final int TASK_ID_1 = 4;
    private static final int TASK_ID_2 = 9;
    private static final int TASK_ID_ILLEGAL = INVALID_TASK_ID;

    // Bounds in screen space
    private final Rect mTopRect = new Rect();
    private final Rect mBottomRect = new Rect();
    private final Rect mLeftRect = new Rect();
    private final Rect mRightRect = new Rect();

    @Before
    public void setup() {
        mTopRect.set(0, 0, DEVICE_WIDTH, DEVICE_LENGTH / 2 - DIVIDER_SIZE / 2);
        mBottomRect.set(0, DEVICE_LENGTH / 2 + DIVIDER_SIZE / 2,
                DEVICE_WIDTH, DEVICE_LENGTH);
        mLeftRect.set(0, 0, DEVICE_WIDTH / 2 - DIVIDER_SIZE / 2, DEVICE_LENGTH);
        mRightRect.set(DEVICE_WIDTH / 2 + DIVIDER_SIZE / 2, 0,
                DEVICE_WIDTH, DEVICE_LENGTH);
    }

    @Test
    public void testVerticalStacked() {
        SplitBounds ssb = new SplitBounds(mTopRect, mBottomRect,
                TASK_ID_1, TASK_ID_2, SNAP_TO_2_50_50);
        assertTrue(ssb.appsStackedVertically);
    }

    @Test
    public void testHorizontalStacked() {
        SplitBounds ssb = new SplitBounds(mLeftRect, mRightRect,
                TASK_ID_1, TASK_ID_2, SNAP_TO_2_50_50);
        assertFalse(ssb.appsStackedVertically);
    }

    @Test
    public void testHorizontalDividerBounds() {
        SplitBounds ssb = new SplitBounds(mTopRect, mBottomRect,
                TASK_ID_1, TASK_ID_2, SNAP_TO_2_50_50);
        Rect dividerBounds = ssb.visualDividerBounds;
        assertEquals(0, dividerBounds.left);
        assertEquals(DEVICE_LENGTH / 2 - DIVIDER_SIZE / 2, dividerBounds.top);
        assertEquals(DEVICE_WIDTH, dividerBounds.right);
        assertEquals(DEVICE_LENGTH / 2 + DIVIDER_SIZE / 2, dividerBounds.bottom);
    }

    @Test
    public void testVerticalDividerBounds() {
        SplitBounds ssb = new SplitBounds(mLeftRect, mRightRect,
                TASK_ID_1, TASK_ID_2, SNAP_TO_2_50_50);
        Rect dividerBounds = ssb.visualDividerBounds;
        assertEquals(DEVICE_WIDTH / 2 - DIVIDER_SIZE / 2, dividerBounds.left);
        assertEquals(0, dividerBounds.top);
        assertEquals(DEVICE_WIDTH / 2 + DIVIDER_SIZE / 2, dividerBounds.right);
        assertEquals(DEVICE_LENGTH, dividerBounds.bottom);
    }

    @Test
    public void testEqualVerticalTaskPercent() {
        SplitBounds ssb = new SplitBounds(mTopRect, mBottomRect,
                TASK_ID_1, TASK_ID_2, SNAP_TO_2_50_50);
        float topPercentSpaceTaken = (float) (DEVICE_LENGTH / 2 - DIVIDER_SIZE / 2) / DEVICE_LENGTH;
        assertEquals(topPercentSpaceTaken, ssb.topTaskPercent, 0.01);
    }

    @Test
    public void testEqualHorizontalTaskPercent() {
        SplitBounds ssb = new SplitBounds(mLeftRect, mRightRect,
                TASK_ID_1, TASK_ID_2, SNAP_TO_2_50_50);
        float leftPercentSpaceTaken = (float) (DEVICE_WIDTH / 2 - DIVIDER_SIZE / 2) / DEVICE_WIDTH;
        assertEquals(leftPercentSpaceTaken, ssb.leftTaskPercent, 0.01);
    }

    @Test
    public void testIllegalTaskIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SplitBounds(mLeftRect, mRightRect,
                        TASK_ID_ILLEGAL, TASK_ID_2, SNAP_TO_2_50_50));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SplitBounds(mLeftRect, mRightRect,
                        TASK_ID_1, TASK_ID_ILLEGAL, SNAP_TO_2_50_50));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SplitBounds(mLeftRect, mRightRect,
                        TASK_ID_ILLEGAL, TASK_ID_ILLEGAL, SNAP_TO_2_50_50));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SplitBounds(mLeftRect, mRightRect,
                        TASK_ID_1, TASK_ID_1, SNAP_TO_2_50_50));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SplitBounds(mLeftRect, mRightRect,
                        TASK_ID_1, TASK_ID_2, Collections.emptyList(), Collections.emptyList(),
                        SNAP_TO_2_50_50));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SplitBounds(mLeftRect, mRightRect,
                        TASK_ID_1, TASK_ID_2, Collections.singletonList(TASK_ID_1),
                        Collections.emptyList(), SNAP_TO_2_50_50));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SplitBounds(mLeftRect, mRightRect,
                        TASK_ID_1, TASK_ID_2, Collections.emptyList(),
                        Collections.singletonList(TASK_ID_2), SNAP_TO_2_50_50));
    }
}