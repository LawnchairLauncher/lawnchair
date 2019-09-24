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
package com.android.launcher3.util;

import static android.os.VibrationEffect.createPredefined;
import static android.provider.Settings.System.HAPTIC_FEEDBACK_ENABLED;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

/**
 * Wrapper around {@link Vibrator} to easily perform haptic feedback where necessary.
 */
@TargetApi(Build.VERSION_CODES.Q)
public class VibratorWrapper {

    public static final MainThreadInitializedObject<VibratorWrapper> INSTANCE =
            new MainThreadInitializedObject<>(VibratorWrapper::new);

    private static final VibrationEffect EFFECT_CLICK =
            createPredefined(VibrationEffect.EFFECT_CLICK);

    /**
     * Haptic when entering overview.
     */
    public static final VibrationEffect OVERVIEW_HAPTIC = EFFECT_CLICK;

    private final Vibrator mVibrator;
    private final boolean mHasVibrator;

    private boolean mIsHapticFeedbackEnabled;

    public VibratorWrapper(Context context) {
        mVibrator = context.getSystemService(Vibrator.class);
        mHasVibrator = mVibrator.hasVibrator();
        if (mHasVibrator) {
            final ContentResolver resolver = context.getContentResolver();
            mIsHapticFeedbackEnabled = isHapticFeedbackEnabled(resolver);
            final ContentObserver observer = new ContentObserver(MAIN_EXECUTOR.getHandler()) {
                @Override
                public void onChange(boolean selfChange) {
                    mIsHapticFeedbackEnabled = isHapticFeedbackEnabled(resolver);
                }
            };
            resolver.registerContentObserver(Settings.System.getUriFor(HAPTIC_FEEDBACK_ENABLED),
                    false /* notifyForDescendents */, observer);
        } else {
            mIsHapticFeedbackEnabled = false;
        }
    }

    private boolean isHapticFeedbackEnabled(ContentResolver resolver) {
        return Settings.System.getInt(resolver, HAPTIC_FEEDBACK_ENABLED, 0) == 1;
    }

    /** Vibrates with the given effect if haptic feedback is available and enabled. */
    public void vibrate(VibrationEffect vibrationEffect) {
        if (mHasVibrator && mIsHapticFeedbackEnabled) {
            UI_HELPER_EXECUTOR.execute(() -> mVibrator.vibrate(vibrationEffect));
        }
    }
}
