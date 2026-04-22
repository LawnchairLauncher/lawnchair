package app.lawnchair.util;

import android.widget.OverScroller;

import com.android.launcher3.Utilities;

import java.lang.reflect.Field;

public class OverScrollerCompat {

    public static void setFinalX(OverScroller scroller, int newX) {
        scroller.setFinalX(newX);
        if (!Utilities.ATLEAST_S) {
            Object scrollerX = getField(scroller, "mScrollerX");
            int mFinal = getField(scrollerX, "mFinal");
            int mStart = getField(scrollerX, "mStart");
            setField(scrollerX, "mSplineDistance", mFinal - mStart);
        }
    }

    public static void extendDuration(OverScroller scroller, int extend) {
        scroller.extendDuration(extend);
        if (!Utilities.ATLEAST_S) {
            Object scrollerX = getField(scroller, "mScrollerX");
            Object scrollerY = getField(scroller, "mScrollerY");
            setField(scrollerX, "mSplineDuration", getField(scrollerX, "mDuration"));
            setField(scrollerY, "mSplineDuration", getField(scrollerY, "mDuration"));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object obj, String name) {
        try {
            Field field = obj.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(obj);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to get field: " + name, e);
        }
    }

    private static void setField(Object obj, String name, Object value) {
        try {
            Field field = obj.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // ignore
        }
    }
}
