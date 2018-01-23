package com.google.android.apps.nexuslauncher.clock;

import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

import com.android.launcher3.Utilities;

import java.util.Calendar;
import java.util.TimeZone;

public class ClockLayers {
    Drawable mDrawable;
    private LayerDrawable mLayerDrawable;
    private final Calendar mCurrentTime;
    int mHourIndex;
    int mMinuteIndex;
    int mSecondIndex;
    int mDefaultHour;
    int mDefaultMinute;
    int mDefaultSecond;
    float scale;

    ClockLayers() {
        mCurrentTime = Calendar.getInstance();
    }

    @Override
    public ClockLayers clone() {
        ClockLayers ret = null;
        if (mDrawable == null) {
            return null;
        }
        ClockLayers clone = new ClockLayers();
        clone.scale = scale;
        clone.mHourIndex = mHourIndex;
        clone.mMinuteIndex = mMinuteIndex;
        clone.mSecondIndex = mSecondIndex;
        clone.mDefaultHour = mDefaultHour;
        clone.mDefaultMinute = mDefaultMinute;
        clone.mDefaultSecond = mDefaultSecond;
        clone.mDrawable = mDrawable.getConstantState().newDrawable();
        clone.mLayerDrawable = clone.getLayerDrawable();
        if (clone.mLayerDrawable != null) {
            ret = clone;
        }
        return ret;
    }

    boolean updateAngles() {
        mCurrentTime.setTimeInMillis(System.currentTimeMillis());

        int hour = (mCurrentTime.get(Calendar.HOUR) + (12 - mDefaultHour)) % 12;
        int minute = (mCurrentTime.get(Calendar.MINUTE) + (60 - mDefaultMinute)) % 60;
        int second = (mCurrentTime.get(Calendar.SECOND) + (60 - mDefaultSecond)) % 60;

        boolean hasChanged = false;
        if (mHourIndex != -1 && mLayerDrawable.getDrawable(mHourIndex).setLevel(hour * 60 + mCurrentTime.get(Calendar.MINUTE))) {
            hasChanged = true;
        }
        if (mMinuteIndex != -1 && mLayerDrawable.getDrawable(mMinuteIndex).setLevel(minute + mCurrentTime.get(Calendar.HOUR) * 60)) {
            hasChanged = true;
        }
        if (mSecondIndex != -1 && mLayerDrawable.getDrawable(mSecondIndex).setLevel(second * 10)) {
            hasChanged = true;
        }
        return hasChanged;
    }

    void setTimeZone(TimeZone timeZone) {
        mCurrentTime.setTimeZone(timeZone);
    }

    LayerDrawable getLayerDrawable() {
        if (mDrawable instanceof LayerDrawable) {
            return (LayerDrawable) mDrawable;
        }
        if (Utilities.ATLEAST_OREO && mDrawable instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable adaptiveIconDrawable = (AdaptiveIconDrawable) mDrawable;
            if (adaptiveIconDrawable.getForeground() instanceof LayerDrawable) {
                return (LayerDrawable) adaptiveIconDrawable.getForeground();
            }
        }
        return null;
    }
}
