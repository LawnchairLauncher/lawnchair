/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.launcher3.icons;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import com.android.launcher3.FastBitmapDrawable;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper over {@link AdaptiveIconDrawable} to intercept icon flattening logic for dynamic
 * clock icons
 */
@TargetApi(Build.VERSION_CODES.O)
public class ClockDrawableWrapper extends AdaptiveIconDrawable implements BitmapInfo.Extender {

    private static final String TAG = "ClockDrawableWrapper";

    private static final boolean DISABLE_SECONDS = true;

    // Time after which the clock icon should check for an update. The actual invalidate
    // will only happen in case of any change.
    public static final long TICK_MS = DISABLE_SECONDS ? TimeUnit.MINUTES.toMillis(1) : 200L;

    private static final String LAUNCHER_PACKAGE = "com.android.launcher3";
    private static final String ROUND_ICON_METADATA_KEY = LAUNCHER_PACKAGE
            + ".LEVEL_PER_TICK_ICON_ROUND";
    private static final String HOUR_INDEX_METADATA_KEY = LAUNCHER_PACKAGE + ".HOUR_LAYER_INDEX";
    private static final String MINUTE_INDEX_METADATA_KEY = LAUNCHER_PACKAGE
            + ".MINUTE_LAYER_INDEX";
    private static final String SECOND_INDEX_METADATA_KEY = LAUNCHER_PACKAGE
            + ".SECOND_LAYER_INDEX";
    private static final String DEFAULT_HOUR_METADATA_KEY = LAUNCHER_PACKAGE
            + ".DEFAULT_HOUR";
    private static final String DEFAULT_MINUTE_METADATA_KEY = LAUNCHER_PACKAGE
            + ".DEFAULT_MINUTE";
    private static final String DEFAULT_SECOND_METADATA_KEY = LAUNCHER_PACKAGE
            + ".DEFAULT_SECOND";

    /* Number of levels to jump per second for the second hand */
    private static final int LEVELS_PER_SECOND = 10;

    public static final int INVALID_VALUE = -1;

    private final AnimationInfo mAnimationInfo = new AnimationInfo();
    private int mTargetSdkVersion;

    public ClockDrawableWrapper(AdaptiveIconDrawable base) {
        super(base.getBackground(), base.getForeground());
    }

    /**
     * Loads and returns the wrapper from the provided package, or returns null
     * if it is unable to load.
     */
    public static ClockDrawableWrapper forPackage(Context context, String pkg, int iconDpi) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo appInfo =  pm.getApplicationInfo(pkg,
                    PackageManager.MATCH_UNINSTALLED_PACKAGES | PackageManager.GET_META_DATA);
            final Bundle metadata = appInfo.metaData;
            if (metadata == null) {
                return null;
            }
            int drawableId = metadata.getInt(ROUND_ICON_METADATA_KEY, 0);
            if (drawableId == 0) {
                return null;
            }

            Drawable drawable = pm.getResourcesForApplication(appInfo).getDrawableForDensity(
                    drawableId, iconDpi).mutate();
            if (!(drawable instanceof AdaptiveIconDrawable)) {
                return null;
            }

            ClockDrawableWrapper wrapper =
                    new ClockDrawableWrapper((AdaptiveIconDrawable) drawable);
            wrapper.mTargetSdkVersion = appInfo.targetSdkVersion;
            AnimationInfo info = wrapper.mAnimationInfo;

            info.baseDrawableState = drawable.getConstantState();

            info.hourLayerIndex = metadata.getInt(HOUR_INDEX_METADATA_KEY, INVALID_VALUE);
            info.minuteLayerIndex = metadata.getInt(MINUTE_INDEX_METADATA_KEY, INVALID_VALUE);
            info.secondLayerIndex = metadata.getInt(SECOND_INDEX_METADATA_KEY, INVALID_VALUE);

            info.defaultHour = metadata.getInt(DEFAULT_HOUR_METADATA_KEY, 0);
            info.defaultMinute = metadata.getInt(DEFAULT_MINUTE_METADATA_KEY, 0);
            info.defaultSecond = metadata.getInt(DEFAULT_SECOND_METADATA_KEY, 0);

            LayerDrawable foreground = (LayerDrawable) wrapper.getForeground();
            int layerCount = foreground.getNumberOfLayers();
            if (info.hourLayerIndex < 0 || info.hourLayerIndex >= layerCount) {
                info.hourLayerIndex = INVALID_VALUE;
            }
            if (info.minuteLayerIndex < 0 || info.minuteLayerIndex >= layerCount) {
                info.minuteLayerIndex = INVALID_VALUE;
            }
            if (info.secondLayerIndex < 0 || info.secondLayerIndex >= layerCount) {
                info.secondLayerIndex = INVALID_VALUE;
            } else if (DISABLE_SECONDS) {
                foreground.setDrawable(info.secondLayerIndex, null);
                info.secondLayerIndex = INVALID_VALUE;
            }
            return wrapper;
        } catch (Exception e) {
            Log.d(TAG, "Unable to load clock drawable info", e);
        }
        return null;
    }

    @Override
    public BitmapInfo getExtendedInfo(Bitmap bitmap, int color, BaseIconFactory iconFactory) {
        iconFactory.disableColorExtraction();
        float [] scale = new float[1];
        AdaptiveIconDrawable background = new AdaptiveIconDrawable(
                getBackground().getConstantState().newDrawable(), null);
        BitmapInfo bitmapInfo = iconFactory.createBadgedIconBitmap(background,
                Process.myUserHandle(), mTargetSdkVersion, false, scale);

        return new ClockBitmapInfo(bitmap, color, scale[0], mAnimationInfo, bitmapInfo.icon);
    }

    @Override
    public void prepareToDrawOnUi() {
        mAnimationInfo.applyTime(Calendar.getInstance(), (LayerDrawable) getForeground());
    }

    private static class AnimationInfo {

        public ConstantState baseDrawableState;

        public int hourLayerIndex;
        public int minuteLayerIndex;
        public int secondLayerIndex;
        public int defaultHour;
        public int defaultMinute;
        public int defaultSecond;

        boolean applyTime(Calendar time, LayerDrawable foregroundDrawable) {
            time.setTimeInMillis(System.currentTimeMillis());

            // We need to rotate by the difference from the default time if one is specified.
            int convertedHour = (time.get(Calendar.HOUR) + (12 - defaultHour)) % 12;
            int convertedMinute = (time.get(Calendar.MINUTE) + (60 - defaultMinute)) % 60;
            int convertedSecond = (time.get(Calendar.SECOND) + (60 - defaultSecond)) % 60;

            boolean invalidate = false;
            if (hourLayerIndex != INVALID_VALUE) {
                final Drawable hour = foregroundDrawable.getDrawable(hourLayerIndex);
                if (hour.setLevel(convertedHour * 60 + time.get(Calendar.MINUTE))) {
                    invalidate = true;
                }
            }

            if (minuteLayerIndex != INVALID_VALUE) {
                final Drawable minute = foregroundDrawable.getDrawable(minuteLayerIndex);
                if (minute.setLevel(time.get(Calendar.HOUR) * 60 + convertedMinute)) {
                    invalidate = true;
                }
            }

            if (secondLayerIndex != INVALID_VALUE) {
                final Drawable second = foregroundDrawable.getDrawable(secondLayerIndex);
                if (second.setLevel(convertedSecond * LEVELS_PER_SECOND)) {
                    invalidate = true;
                }
            }

            return invalidate;
        }
    }

    private static class ClockBitmapInfo extends BitmapInfo implements FastBitmapDrawable.Factory {

        public final float scale;
        public final int offset;
        public final AnimationInfo animInfo;
        public final Bitmap mFlattenedBackground;

        ClockBitmapInfo(Bitmap icon, int color, float scale, AnimationInfo animInfo,
                Bitmap background) {
            super(icon, color);
            this.scale = scale;
            this.animInfo = animInfo;
            this.offset = (int) Math.ceil(ShadowGenerator.BLUR_FACTOR * icon.getWidth());
            this.mFlattenedBackground = background;
        }

        @Override
        public FastBitmapDrawable newDrawable() {
            return new ClockIconDrawable(this);
        }
    }

    private static class ClockIconDrawable extends FastBitmapDrawable implements Runnable {

        private final Calendar mTime = Calendar.getInstance();

        private final ClockBitmapInfo mInfo;

        private final AdaptiveIconDrawable mFullDrawable;
        private final LayerDrawable mForeground;

        ClockIconDrawable(ClockBitmapInfo clockInfo) {
            super(clockInfo);

            mInfo = clockInfo;

            mFullDrawable = (AdaptiveIconDrawable) mInfo.animInfo.baseDrawableState.newDrawable();
            mForeground = (LayerDrawable) mFullDrawable.getForeground();
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            mFullDrawable.setBounds(bounds);
        }

        @Override
        public void drawInternal(Canvas canvas, Rect bounds) {
            if (mInfo == null) {
                super.drawInternal(canvas, bounds);
                return;
            }
            // draw the background that is already flattened to a bitmap
            canvas.drawBitmap(mInfo.mFlattenedBackground, null, bounds, mPaint);

            // prepare and draw the foreground
            mInfo.animInfo.applyTime(mTime, mForeground);

            canvas.scale(mInfo.scale, mInfo.scale,
                    bounds.exactCenterX() + mInfo.offset, bounds.exactCenterY() + mInfo.offset);
            canvas.clipPath(mFullDrawable.getIconMask());
            mForeground.draw(canvas);

            reschedule();
        }

        @Override
        protected void updateFilter() {
            super.updateFilter();
            mFullDrawable.setColorFilter(mPaint.getColorFilter());
        }

        @Override
        public void run() {
            if (mInfo.animInfo.applyTime(mTime, mForeground)) {
                invalidateSelf();
            } else {
                reschedule();
            }
        }

        @Override
        public boolean setVisible(boolean visible, boolean restart) {
            boolean result = super.setVisible(visible, restart);
            if (visible) {
                reschedule();
            } else {
                unscheduleSelf(this);
            }
            return result;
        }

        private void reschedule() {
            if (!isVisible()) {
                return;
            }

            unscheduleSelf(this);
            final long upTime = SystemClock.uptimeMillis();
            final long step = TICK_MS; /* tick every 200 ms */
            scheduleSelf(this, upTime - ((upTime % step)) + step);
        }

        @Override
        public ConstantState getConstantState() {
            return new ClockConstantState(mInfo, isDisabled());
        }

        private static class ClockConstantState extends MyConstantState {

            private final ClockBitmapInfo mInfo;

            ClockConstantState(ClockBitmapInfo info, boolean isDisabled) {
                super(info.icon, info.color, isDisabled);
                mInfo = info;
            }

            @Override
            public FastBitmapDrawable newDrawable() {
                ClockIconDrawable drawable = new ClockIconDrawable(mInfo);
                drawable.setIsDisabled(mIsDisabled);
                return drawable;
            }
        }
    }
}
