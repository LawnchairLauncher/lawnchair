/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.quickstep.util;

import static com.airbnb.lottie.LottieProperty.COLOR_FILTER;

import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.model.KeyPath;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Utility class for programmatically updating Lottie animation tokenized colors. */
public final class LottieAnimationColorUtils {

    private LottieAnimationColorUtils() {}

    /**
     * Updates the given Lottie animation's tokenized colors according to the given mapping.
     * <p>
     * Use this method signature only when {@code tokenToColorCodeMap} maps to packed ARBG color
     * integers.
     * <p>
     * @param animationView {@link LottieAnimationView} whose animation's colors need to be updated
     * @param tokenToColorCodeMap A mapping from the color tokens used in the Lottie file used in
     *                            {@code animationView} to packed ARBG color integers.
     */
    public static void updateColors(
            @NonNull LottieAnimationView animationView,
            @NonNull Map<String, Integer> tokenToColorCodeMap) {
        updateColors(animationView, tokenToColorCodeMap, null);
    }

    /**
     * Updates the given Lottie animation's tokenized colors according to the given mapping.
     * <p>
     * Use this method signature with a non-null theme only when {@code tokenToColorCodeMap} maps
     * to color resource references.
     * <p>
     * @param animationView {@link LottieAnimationView} whose animation's colors need to be updated
     * @param tokenToColorCodeMap A mapping from the color tokens used in the Lottie file used in
     *                            {@code animationView} to packed ARBG color integers or color
     *                            resource references.
     * @param theme {@link Theme} to be used when resolving color resource references. {@code null}
     *              iff {@code tokenToColorCodeMap} maps to packed ARBG color integers.
     */
    public static void updateColors(
            @NonNull LottieAnimationView animationView,
            @NonNull Map<String, Integer> tokenToColorCodeMap,
            @Nullable Theme theme) {
        Resources resources = animationView.getResources();
        final Map<String, Integer> tokenToColorMap = theme == null
                // tokenToColorCodeMap maps directly to ARBG values
                ? tokenToColorCodeMap
                // tokenToColorCodeMap maps to color references, build a mapping to resolved colors
                : tokenToColorCodeMap.keySet().stream().collect(Collectors.toMap(
                        Function.identity(),
                        token -> resources.getColor(tokenToColorCodeMap.get(token), theme)));

        animationView.addLottieOnCompositionLoadedListener(
                composition -> tokenToColorMap.forEach(
                        (token, color) -> animationView.addValueCallback(
                                new KeyPath("**", token, "**"),
                                COLOR_FILTER,
                                frameInfo -> new PorterDuffColorFilter(
                                        color, PorterDuff.Mode.SRC_ATOP))));
    }
}
