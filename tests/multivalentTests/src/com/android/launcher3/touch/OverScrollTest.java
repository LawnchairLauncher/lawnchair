package com.android.launcher3.touch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OverScrollTest {

    @Test
    public void testDampedScrollZero() {
        assertEquals(0, OverScroll.dampedScroll(0, 1000));
        assertEquals(0, OverScroll.dampedScroll(0, 1000, 0.15f));
    }

    @Test
    public void testDampedScrollProgressionAndResistance() {
        int max = 1000;
        float factor = 0.22f;

        int s1 = OverScroll.dampedScroll(200, max, factor);
        int s2 = OverScroll.dampedScroll(400, max, factor);
        int s3 = OverScroll.dampedScroll(600, max, factor);
        int s4 = OverScroll.dampedScroll(800, max, factor);
        int s5 = OverScroll.dampedScroll(1000, max, factor);
        int s6 = OverScroll.dampedScroll(1500, max, factor);

        // Movement is positive and monotonic
        assertTrue(s1 > 0);
        assertTrue(s2 > s1);
        assertTrue(s3 > s2);
        assertTrue(s4 > s3);
        assertTrue(s5 > s4);

        // Velocity continuously diminishes (diminishing increments)
        int delta1 = s2 - s1;
        int delta2 = s3 - s2;
        int delta3 = s4 - s3;
        int delta4 = s5 - s4;
        assertTrue(delta1 >= delta2);
        assertTrue(delta2 >= delta3);
        assertTrue(delta3 >= delta4);

        // At or beyond max, movement halts at factor * max
        assertEquals(Math.round(factor * max), s5);
        assertEquals(s5, s6);
    }
}
