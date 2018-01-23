package com.google.android.apps.nexuslauncher.clock;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.SystemClock;

import com.android.launcher3.FastBitmapDrawable;

import java.util.TimeZone;

public class AutoUpdateClock extends FastBitmapDrawable implements Runnable {
    private ClockLayers mLayers;

    AutoUpdateClock(Bitmap bitmap, ClockLayers layers) {
        super(bitmap);
        mLayers = layers;
    }

    private void rescheduleUpdate() {
        long millisInSecond = 1000L;
        unscheduleSelf(this);
        long uptimeMillis = SystemClock.uptimeMillis();
        scheduleSelf(this, uptimeMillis - uptimeMillis % millisInSecond + millisInSecond);
    }

    void updateLayers(ClockLayers layers) {
        mLayers = layers;
        if (mLayers != null) {
            mLayers.mDrawable.setBounds(getBounds());
        }
        invalidateSelf();
    }

    void setTimeZone(TimeZone timeZone) {
        if (mLayers != null) {
            mLayers.setTimeZone(timeZone);
            invalidateSelf();
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (mLayers == null) {
            return;
        }
        mLayers.updateAngles();
        Rect bounds = getBounds();
        canvas.scale(mLayers.scale, mLayers.scale, bounds.exactCenterX(), bounds.exactCenterY());
        mLayers.mDrawable.draw(canvas);
        rescheduleUpdate();
    }

    @Override
    protected void onBoundsChange(final Rect bounds) {
        super.onBoundsChange(bounds);
        if (mLayers != null) {
            mLayers.mDrawable.setBounds(bounds);
        }
    }

    @Override
    public void run() {
        if (mLayers.updateAngles()) {
            invalidateSelf();
        } else {
            rescheduleUpdate();
        }
    }
}
