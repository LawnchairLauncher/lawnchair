/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.util;

import static android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK;
import static android.os.VibrationEffect.createPredefined;
import static android.provider.Settings.System.HAPTIC_FEEDBACK_ENABLED;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.media.AudioAttributes;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

import androidx.annotation.Nullable;

import com.android.launcher3.Utilities;

/**
 * Wrapper around {@link Vibrator} to easily perform haptic feedback where necessary.
 */
public class VibratorWrapper implements SafeCloseable {

    public static final MainThreadInitializedObject<VibratorWrapper> INSTANCE =
            new MainThreadInitializedObject<>(VibratorWrapper::new);

    public static final AudioAttributes VIBRATION_ATTRS = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();

    public static final VibrationEffect EFFECT_CLICK =
            createPredefined(VibrationEffect.EFFECT_CLICK);

    private static final float LOW_TICK_SCALE = 0.9f;
    private static final float DRAG_TEXTURE_SCALE = 0.03f;
    private static final float DRAG_COMMIT_SCALE = 0.5f;
    private static final float DRAG_BUMP_SCALE = 0.4f;
    private static final int DRAG_TEXTURE_EFFECT_SIZE = 200;

    @Nullable
    private final VibrationEffect mDragEffect;
    @Nullable
    private final VibrationEffect mCommitEffect;
    @Nullable
    private final VibrationEffect mBumpEffect;

    private long mLastDragTime;
    private final int mThresholdUntilNextDragCallMillis;

    /**
     * Haptic when entering overview.
     */
    public static final VibrationEffect OVERVIEW_HAPTIC = EFFECT_CLICK;

    private final Context mContext;
    private final Vibrator mVibrator;
    private final boolean mHasVibrator;

    private ContentObserver mHapticFeedbackObserver;
    private boolean mIsHapticFeedbackEnabled;

    private VibratorWrapper(Context context) {
        mContext = context;
        mVibrator = context.getSystemService(Vibrator.class);
        mHasVibrator = mVibrator.hasVibrator();
        if (mHasVibrator) {
            final ContentResolver resolver = context.getContentResolver();
            mIsHapticFeedbackEnabled = isHapticFeedbackEnabled(resolver);
            mHapticFeedbackObserver = new ContentObserver(MAIN_EXECUTOR.getHandler()) {
                @Override
                public void onChange(boolean selfChange) {
                    mIsHapticFeedbackEnabled = isHapticFeedbackEnabled(resolver);
                }
            };
            resolver.registerContentObserver(Settings.System.getUriFor(HAPTIC_FEEDBACK_ENABLED),
                    false /* notifyForDescendants */, mHapticFeedbackObserver);
        } else {
            mIsHapticFeedbackEnabled = false;
        }

        if (Utilities.ATLEAST_S && mVibrator.areAllPrimitivesSupported(
                PRIMITIVE_LOW_TICK)) {

            // Drag texture, Commit, and Bump should only be used for premium phones.
            // Before using these haptics make sure check if the device can use it
            VibrationEffect.Composition dragEffect = VibrationEffect.startComposition();
            for (int i = 0; i < DRAG_TEXTURE_EFFECT_SIZE; i++) {
                dragEffect.addPrimitive(
                        PRIMITIVE_LOW_TICK, DRAG_TEXTURE_SCALE);
            }
            mDragEffect = dragEffect.compose();
            mCommitEffect = VibrationEffect.startComposition().addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_TICK, DRAG_COMMIT_SCALE).compose();
            mBumpEffect = VibrationEffect.startComposition().addPrimitive(
                    PRIMITIVE_LOW_TICK, DRAG_BUMP_SCALE).compose();
            int primitiveDuration = mVibrator.getPrimitiveDurations(
                    PRIMITIVE_LOW_TICK)[0];

            mThresholdUntilNextDragCallMillis =
                    DRAG_TEXTURE_EFFECT_SIZE * primitiveDuration + 100;
        } else {
            mDragEffect = null;
            mCommitEffect = null;
            mBumpEffect = null;
            mThresholdUntilNextDragCallMillis = 0;
        }
    }

    @Override
    public void close() {
        if (mHapticFeedbackObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mHapticFeedbackObserver);
        }
    }

    /**
     * This is called when the user swipes to/from all apps. This is meant to be used in between
     * long animation progresses so that it gives a dragging texture effect. For a better
     * experience, this should be used in combination with vibrateForDragCommit().
     */
    public void vibrateForDragTexture() {
        if (mDragEffect == null) {
            return;
        }
        long currentTime = SystemClock.elapsedRealtime();
        long elapsedTimeSinceDrag = currentTime - mLastDragTime;
        if (elapsedTimeSinceDrag >= mThresholdUntilNextDragCallMillis) {
            vibrate(mDragEffect);
            mLastDragTime = currentTime;
        }
    }

    /**
     * This is used when user reaches the commit threshold when swiping to/from from all apps.
     */
    public void vibrateForDragCommit() {
        if (mCommitEffect != null) {
            vibrate(mCommitEffect);
        }
        // resetting dragTexture timestamp to be able to play dragTexture again
        mLastDragTime = 0;
    }

    /**
     * The bump haptic is used to be called at the end of a swipe and only if it the gesture is a
     * FLING going to/from all apps. Client can just call this method elsewhere just for the
     * effect.
     */
    public void vibrateForDragBump() {
        if (mBumpEffect != null) {
            vibrate(mBumpEffect);
        }
    }

    /**
     * This should be used to cancel a haptic in case where the haptic shouldn't be vibrating. For
     * example, when no animation is happening but a vibrator happens to be vibrating still.
     */
    public void cancelVibrate() {
        UI_HELPER_EXECUTOR.execute(mVibrator::cancel);
        // reset dragTexture timestamp to be able to play dragTexture again whenever cancelled
        mLastDragTime = 0;
    }

    private boolean isHapticFeedbackEnabled(ContentResolver resolver) {
        return Settings.System.getInt(resolver, HAPTIC_FEEDBACK_ENABLED, 0) == 1;
    }

    /** Vibrates with the given effect if haptic feedback is available and enabled. */
    public void vibrate(VibrationEffect vibrationEffect) {
        if (mHasVibrator && mIsHapticFeedbackEnabled) {
            UI_HELPER_EXECUTOR.execute(() -> mVibrator.vibrate(vibrationEffect, VIBRATION_ATTRS));
        }
    }

    /**
     * Vibrates with a single primitive, if supported, or use a fallback effect instead. This only
     * vibrates if haptic feedback is available and enabled.
     */
    @SuppressLint("NewApi")
    public void vibrate(int primitiveId, float primitiveScale, VibrationEffect fallbackEffect) {
        if (mHasVibrator && mIsHapticFeedbackEnabled) {
            UI_HELPER_EXECUTOR.execute(() -> {
                if (primitiveId >= 0 && mVibrator.areAllPrimitivesSupported(primitiveId)) {
                    mVibrator.vibrate(VibrationEffect.startComposition()
                            .addPrimitive(primitiveId, primitiveScale)
                            .compose(), VIBRATION_ATTRS);
                } else {
                    mVibrator.vibrate(fallbackEffect, VIBRATION_ATTRS);
                }
            });
        }
    }

    /** Indicates that Taskbar has been invoked. */
    public void vibrateForTaskbarUnstash() {
        if (Utilities.ATLEAST_S && mVibrator.areAllPrimitivesSupported(PRIMITIVE_LOW_TICK)) {
            VibrationEffect primitiveLowTickEffect = VibrationEffect
                    .startComposition()
                    .addPrimitive(PRIMITIVE_LOW_TICK, LOW_TICK_SCALE)
                    .compose();

            vibrate(primitiveLowTickEffect);
        }
    }
}
