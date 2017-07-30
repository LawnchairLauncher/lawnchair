/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.dynamicui;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.graphics.ColorUtils;
import android.util.Log;
import android.util.Pair;
import android.util.Range;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.WallpaperColorsCompat;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Implementation of tonal color extraction
 **/
public class ColorExtractionAlgorithm {

    public static ColorExtractionAlgorithm newInstance(Context context) {
        return Utilities.getOverrideObject(ColorExtractionAlgorithm.class,
                context.getApplicationContext(), R.string.color_extraction_impl_class);
    }

    private static final String TAG = "Tonal";

    // Used for tonal palette fitting
    private static final float FIT_WEIGHT_H = 1.0f;
    private static final float FIT_WEIGHT_S = 1.0f;
    private static final float FIT_WEIGHT_L = 10.0f;

    public static final int MAIN_COLOR_LIGHT = 0xffb0b0b0;
    public static final int SECONDARY_COLOR_LIGHT = 0xff9e9e9e;
    public static final int MAIN_COLOR_DARK = 0xff212121;
    public static final int SECONDARY_COLOR_DARK = 0xff000000;

    // Temporary variable to avoid allocations
    private float[] mTmpHSL = new float[3];

    public Pair<Integer, Integer> extractInto(WallpaperColorsCompat inWallpaperColors) {
        if (inWallpaperColors == null) {
            return applyFallback(inWallpaperColors);
        }

        final List<Integer> mainColors = getMainColors(inWallpaperColors);
        final int mainColorsSize = mainColors.size();
        final boolean supportsDarkText = (inWallpaperColors.getColorHints() &
                WallpaperColorsCompat.HINT_SUPPORTS_DARK_TEXT) != 0;

        if (mainColorsSize == 0) {
            return applyFallback(inWallpaperColors);
        }
        // Tonal is not really a sort, it takes a color from the extracted
        // palette and finds a best fit amongst a collection of pre-defined
        // palettes. The best fit is tweaked to be closer to the source color
        // and replaces the original palette

        // Get the most preeminent, non-blacklisted color.
        Integer bestColor = 0;
        final float[] hsl = new float[3];
        for (int i = 0; i < mainColorsSize; i++) {
            final int colorValue = mainColors.get(i);
            ColorUtils.RGBToHSL(Color.red(colorValue), Color.green(colorValue),
                    Color.blue(colorValue), hsl);

            // Stop when we find a color that meets our criteria
            if (!isBlacklisted(hsl)) {
                bestColor = colorValue;
                break;
            }
        }

        // Fail if not found
        if (bestColor == null) {
            return applyFallback(inWallpaperColors);
        }

        int colorValue = bestColor;
        ColorUtils.RGBToHSL(Color.red(colorValue), Color.green(colorValue), Color.blue(colorValue),
                hsl);

        // The Android HSL definition requires the hue to go from 0 to 360 but
        // the Material Tonal Palette defines hues from 0 to 1.
        hsl[0] /= 360f;

        // Find the palette that contains the closest color
        TonalPalette palette = findTonalPalette(hsl[0], hsl[1]);
        if (palette == null) {
            Log.w(TAG, "Could not find a tonal palette!");
            return applyFallback(inWallpaperColors);
        }

        // Figure out what's the main color index in the optimal palette
        int fitIndex = bestFit(palette, hsl[0], hsl[1], hsl[2]);
        if (fitIndex == -1) {
            Log.w(TAG, "Could not find best fit!");
            return applyFallback(inWallpaperColors);
        }

        // Generate the 10 colors palette by offsetting each one of them
        float[] h = fit(palette.h, hsl[0], fitIndex,
                Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
        float[] s = fit(palette.s, hsl[1], fitIndex, 0.0f, 1.0f);
        float[] l = fit(palette.l, hsl[2], fitIndex, 0.0f, 1.0f);

        int primaryIndex = fitIndex;
        int mainColor = getColorInt(primaryIndex, h, s, l);

        // We might want use the fallback in case the extracted color is brighter than our
        // light fallback or darker than our dark fallback.
        ColorUtils.colorToHSL(mainColor, mTmpHSL);
        final float mainLuminosity = mTmpHSL[2];
        ColorUtils.colorToHSL(MAIN_COLOR_LIGHT, mTmpHSL);
        final float lightLuminosity = mTmpHSL[2];
        if (mainLuminosity > lightLuminosity) {
            return applyFallback(inWallpaperColors);
        }
        ColorUtils.colorToHSL(MAIN_COLOR_DARK, mTmpHSL);
        final float darkLuminosity = mTmpHSL[2];
        if (mainLuminosity < darkLuminosity) {
            return applyFallback(inWallpaperColors);
        }

        // Dark colors:
        // Stops at 4th color, only lighter if dark text is supported
        if (supportsDarkText) {
            primaryIndex = h.length - 1;
        } else if (fitIndex < 2) {
            primaryIndex = 0;
        } else {
            primaryIndex = Math.min(fitIndex, 3);
        }
        int secondaryIndex = primaryIndex + (primaryIndex >= 2 ? -2 : 2);
        int secondaryColor = getColorInt(secondaryIndex, h, s, l);

        return new Pair<>(mainColor, secondaryColor);
    }

    public static Pair<Integer, Integer> applyFallback(WallpaperColorsCompat inWallpaperColors) {
        boolean light = inWallpaperColors != null
                && (inWallpaperColors.getColorHints()
                    & WallpaperColorsCompat.HINT_SUPPORTS_DARK_TEXT)!= 0;
        int innerColor = light ? MAIN_COLOR_LIGHT : MAIN_COLOR_DARK;
        int outerColor = light ? SECONDARY_COLOR_LIGHT : SECONDARY_COLOR_DARK;
        return new Pair<>(innerColor, outerColor);
    }

    private int getColorInt(int fitIndex, float[] h, float[] s, float[] l) {
        mTmpHSL[0] = fract(h[fitIndex]) * 360.0f;
        mTmpHSL[1] = s[fitIndex];
        mTmpHSL[2] = l[fitIndex];
        return ColorUtils.HSLToColor(mTmpHSL);
    }

    /**
     * Checks if a given color exists in the blacklist
     * @param hsl float array with 3 components (H 0..360, S 0..1 and L 0..1)
     * @return true if color should be avoided
     */
    private boolean isBlacklisted(float[] hsl) {
        for (ColorRange badRange: BLACKLISTED_COLORS) {
            if (badRange.containsColor(hsl[0], hsl[1], hsl[2])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Offsets all colors by a delta, clamping values that go beyond what's
     * supported on the color space.
     * @param data what you want to fit
     * @param v how big should be the offset
     * @param index which index to calculate the delta against
     * @param min minimum accepted value (clamp)
     * @param max maximum accepted value (clamp)
     * @return new shifted palette
     */
    private static float[] fit(float[] data, float v, int index, float min, float max) {
        float[] fitData = new float[data.length];
        float delta = v - data[index];

        for (int i = 0; i < data.length; i++) {
            fitData[i] = Utilities.boundToRange(data[i] + delta, min, max);
        }

        return fitData;
    }

    /**
     * Finds the closest color in a palette, given another HSL color
     *
     * @param palette where to search
     * @param h hue
     * @param s saturation
     * @param l lightness
     * @return closest index or -1 if palette is empty.
     */
    private static int bestFit(@NonNull TonalPalette palette, float h, float s, float l) {
        int minErrorIndex = -1;
        float minError = Float.POSITIVE_INFINITY;

        for (int i = 0; i < palette.h.length; i++) {
            float error =
                    FIT_WEIGHT_H * Math.abs(h - palette.h[i])
                            + FIT_WEIGHT_S * Math.abs(s - palette.s[i])
                            + FIT_WEIGHT_L * Math.abs(l - palette.l[i]);
            if (error < minError) {
                minError = error;
                minErrorIndex = i;
            }
        }

        return minErrorIndex;
    }

    @Nullable
    private static TonalPalette findTonalPalette(float h, float s) {
        // Fallback to a grey palette if the color is too desaturated.
        // This avoids hue shifts.
        if (s < 0.05f) {
            return GREY_PALETTE;
        }

        TonalPalette best = null;
        float error = Float.POSITIVE_INFINITY;

        for (int i = 0; i < TONAL_PALETTES.length; i++) {
            final TonalPalette candidate = TONAL_PALETTES[i];

            if (h >= candidate.minHue && h <= candidate.maxHue) {
                best = candidate;
                break;
            }

            if (candidate.maxHue > 1.0f && h >= 0.0f && h <= fract(candidate.maxHue)) {
                best = candidate;
                break;
            }

            if (candidate.minHue < 0.0f && h >= fract(candidate.minHue) && h <= 1.0f) {
                best = candidate;
                break;
            }

            if (h <= candidate.minHue && candidate.minHue - h < error) {
                best = candidate;
                error = candidate.minHue - h;
            } else if (h >= candidate.maxHue && h - candidate.maxHue < error) {
                best = candidate;
                error = h - candidate.maxHue;
            } else if (candidate.maxHue > 1.0f && h >= fract(candidate.maxHue)
                    && h - fract(candidate.maxHue) < error) {
                best = candidate;
                error = h - fract(candidate.maxHue);
            } else if (candidate.minHue < 0.0f && h <= fract(candidate.minHue)
                    && fract(candidate.minHue) - h < error) {
                best = candidate;
                error = fract(candidate.minHue) - h;
            }
        }

        return best;
    }

    private static float fract(float v) {
        return v - (float) Math.floor(v);
    }

    static class TonalPalette {
        final float[] h;
        final float[] s;
        final float[] l;
        final float minHue;
        final float maxHue;

        TonalPalette(float[] h, float[] s, float[] l) {
            if (h.length != s.length || s.length != l.length) {
                throw new IllegalArgumentException("All arrays should have the same size. h: "
                        + Arrays.toString(h) + " s: " + Arrays.toString(s) + " l: "
                        + Arrays.toString(l));
            }

            this.h = h;
            this.s = s;
            this.l = l;

            float minHue = Float.POSITIVE_INFINITY;
            float maxHue = Float.NEGATIVE_INFINITY;

            for (float v : h) {
                minHue = Math.min(v, minHue);
                maxHue = Math.max(v, maxHue);
            }

            this.minHue = minHue;
            this.maxHue = maxHue;
        }
    }

    // Data definition of Material Design tonal palettes
    // When the sort type is set to TONAL, these palettes are used to find
    // a best fit. Each palette is defined as 22 HSL colors
    private static final TonalPalette[] TONAL_PALETTES = {
            new TonalPalette(
                    new float[] {1f, 1f, 0.991f, 0.991f, 0.9833333333333333f, 0f, 0f, 0f,
                            0.01134380453752181f, 0.015625000000000003f, 0.024193548387096798f,
                            0.027397260273972573f, 0.017543859649122865f},
                    new float[] {1f, 1f, 1f, 1f, 1f, 1f, 1f, 0.8434782608695652f, 1f, 1f, 1f, 1f,
                            1f},
                    new float[] {0.04f, 0.09f, 0.14f, 0.2f, 0.27450980392156865f,
                            0.34901960784313724f, 0.4235294117647059f, 0.5490196078431373f,
                            0.6254901960784314f, 0.6862745098039216f, 0.7568627450980392f,
                            0.8568627450980393f, 0.9254901960784314f}
            ),
            new TonalPalette(
                    new float[] {0.638f, 0.638f, 0.6385767790262171f, 0.6301169590643275f,
                            0.6223958333333334f, 0.6151079136690647f, 0.6065400843881856f,
                            0.5986964618249534f, 0.5910746812386157f, 0.5833333333333334f,
                            0.5748031496062993f, 0.5582010582010583f},
                    new float[] {1f, 1f, 1f, 1f, 0.9014084507042253f, 0.8128654970760234f,
                            0.7979797979797981f, 0.7816593886462883f, 0.778723404255319f, 1f, 1f,
                            1f},
                    new float[] {0.05f, 0.12f, 0.17450980392156862f, 0.2235294117647059f,
                            0.2784313725490196f, 0.3352941176470588f, 0.388235294117647f,
                            0.44901960784313727f, 0.5392156862745098f, 0.6509803921568628f,
                            0.7509803921568627f, 0.8764705882352941f}
            ),
            new TonalPalette(
                    new float[] {0.563f, 0.569f, 0.5666f, 0.5669934640522876f, 0.5748031496062993f,
                            0.5595238095238095f, 0.5473118279569893f, 0.5393258426966292f,
                            0.5315955766192734f, 0.524031007751938f, 0.5154711673699016f,
                            0.508080808080808f, 0.5f},
                    new float[] {1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 0.8847736625514403f, 1f, 1f,
                            1f},
                    new float[] {0.07f, 0.12f, 0.16f, 0.2f, 0.24901960784313726f,
                            0.27450980392156865f, 0.30392156862745096f, 0.34901960784313724f,
                            0.4137254901960784f, 0.47647058823529415f, 0.5352941176470588f,
                            0.6764705882352942f, 0.8f}
            ),
            new TonalPalette(
                    new float[] {0.508f, 0.511f, 0.508f, 0.508f, 0.5082304526748972f,
                            0.5069444444444444f, 0.5f, 0.5f, 0.5f, 0.48724954462659376f,
                            0.4800347222222222f, 0.4755134281200632f, 0.4724409448818897f,
                            0.4671052631578947f},
                    new float[] {1f, 1f, 1f, 1f, 1f, 0.8888888888888887f, 0.9242424242424242f, 1f,
                            1f, 0.8133333333333332f, 0.7868852459016393f, 1f, 1f, 1f},
                    new float[] {0.04f, 0.06f, 0.08f, 0.12f, 0.1588235294117647f,
                            0.21176470588235297f, 0.25882352941176473f, 0.3f, 0.34901960784313724f,
                            0.44117647058823534f, 0.5215686274509804f, 0.5862745098039216f,
                            0.7509803921568627f, 0.8509803921568627f}
            ),
            new TonalPalette(
                    new float[] {0.333f, 0.333f, 0.333f, 0.3333333333333333f, 0.3333333333333333f,
                            0.34006734006734f, 0.34006734006734f, 0.34006734006734f,
                            0.34259259259259256f, 0.3475783475783476f, 0.34767025089605735f,
                            0.3467741935483871f, 0.3703703703703704f},
                    new float[] {0.70f, 0.72f, 0.69f, 0.6703296703296703f, 0.728813559322034f,
                            0.5657142857142856f, 0.5076923076923077f, 0.3944223107569721f,
                            0.6206896551724138f, 0.8931297709923666f, 1f, 1f, 1f},
                    new float[] {0.05f, 0.08f, 0.14f, 0.1784313725490196f, 0.23137254901960785f,
                            0.3431372549019608f, 0.38235294117647056f, 0.49215686274509807f,
                            0.6588235294117647f, 0.7431372549019608f, 0.8176470588235294f,
                            0.8784313725490196f, 0.9294117647058824f}
            ),
            new TonalPalette(
                    new float[] {0.161f, 0.163f, 0.163f, 0.162280701754386f, 0.15032679738562088f,
                            0.15879265091863518f, 0.16236559139784948f, 0.17443868739205526f,
                            0.17824074074074076f, 0.18674698795180725f, 0.18692449355432778f,
                            0.1946778711484594f, 0.18604651162790695f},
                    new float[] {1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f},
                    new float[] {0.05f, 0.08f, 0.11f, 0.14901960784313725f, 0.2f,
                            0.24901960784313726f, 0.30392156862745096f, 0.3784313725490196f,
                            0.4235294117647059f, 0.48823529411764705f, 0.6450980392156863f,
                            0.7666666666666666f, 0.8313725490196078f}
            ),
            new TonalPalette(
                    new float[] {0.108f, 0.105f, 0.105f, 0.105f, 0.10619469026548674f,
                            0.11924686192468618f, 0.13046448087431692f, 0.14248366013071895f,
                            0.1506024096385542f, 0.16220238095238093f, 0.16666666666666666f,
                            0.16666666666666666f, 0.162280701754386f, 0.15686274509803924f},
                    new float[] {1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f},
                    new float[] {0.17f, 0.22f, 0.28f, 0.35f, 0.44313725490196076f,
                            0.46862745098039216f, 0.47843137254901963f, 0.5f, 0.5117647058823529f,
                            0.5607843137254902f, 0.6509803921568628f, 0.7509803921568627f,
                            0.8509803921568627f, 0.9f}
            ),
            new TonalPalette(
                    new float[] {0.036f, 0.036f, 0.036f, 0.036f, 0.03561253561253561f,
                            0.05098039215686275f, 0.07516339869281045f, 0.09477124183006536f,
                            0.1150326797385621f, 0.134640522875817f, 0.14640522875816991f,
                            0.1582397003745319f, 0.15773809523809523f, 0.15359477124183002f},
                    new float[] {1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f},
                    new float[] {0.19f, 0.26f, 0.34f, 0.39f, 0.4588235294117647f, 0.5f, 0.5f, 0.5f,
                            0.5f, 0.5f, 0.5f, 0.6509803921568628f, 0.7803921568627451f, 0.9f}
            ),
            new TonalPalette(
                    new float[] {0.955f, 0.961f, 0.958f, 0.9596491228070175f, 0.9593837535014005f,
                            0.9514767932489452f, 0.943859649122807f, 0.9396825396825397f,
                            0.9395424836601307f, 0.9393939393939394f, 0.9362745098039216f,
                            0.9754098360655739f, 0.9824561403508771f},
                    new float[] {0.87f, 0.85f, 0.85f, 0.84070796460177f, 0.8206896551724138f,
                            0.7979797979797981f, 0.7661290322580644f, 0.9051724137931036f,
                            1f, 1f, 1f, 1f, 1f},
                    new float[] {0.06f, 0.11f, 0.16f, 0.22156862745098038f, 0.2843137254901961f,
                            0.388235294117647f, 0.48627450980392156f, 0.5450980392156863f,
                            0.6f, 0.6764705882352942f, 0.8f, 0.8803921568627451f,
                            0.9254901960784314f}
            ),
            new TonalPalette(
                    new float[] {0.866f, 0.855f, 0.841025641025641f, 0.8333333333333334f,
                            0.8285256410256411f, 0.821522309711286f, 0.8083333333333333f,
                            0.8046594982078853f, 0.8005822416302766f, 0.7842377260981912f,
                            0.7771084337349398f, 0.7747747747747749f},
                    new float[] {1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
                            0.737142857142857f, 0.6434108527131781f, 0.46835443037974644f},
                    new float[] {0.05f, 0.08f, 0.12745098039215685f, 0.15490196078431373f,
                            0.20392156862745098f, 0.24901960784313726f, 0.3137254901960784f,
                            0.36470588235294116f, 0.44901960784313727f, 0.6568627450980392f,
                            0.7470588235294118f, 0.8450980392156863f}
            ),
            new TonalPalette(
                    new float[] {0.925f, 0.93f, 0.938f, 0.947f, 0.955952380952381f,
                            0.9681069958847737f, 0.9760479041916167f, 0.9873563218390804f, 0f, 0f,
                            0.009057971014492771f, 0.026748971193415648f,
                            0.041666666666666616f, 0.05303030303030304f},
                    new float[] {1f, 1f, 1f, 1f, 1f, 0.8350515463917526f, 0.6929460580912863f,
                            0.6387665198237885f, 0.6914893617021276f, 0.7583892617449666f,
                            0.8070175438596495f, 0.9310344827586209f, 1f, 1f},
                    new float[] {0.10f, 0.13f, 0.17f, 0.2f, 0.27450980392156865f,
                            0.3803921568627451f, 0.4725490196078432f, 0.5549019607843138f,
                            0.6313725490196078f, 0.707843137254902f, 0.7764705882352941f,
                            0.8294117647058823f, 0.9058823529411765f, 0.9568627450980391f}
            ),
            new TonalPalette(
                    new float[] {0.733f, 0.736f, 0.744f, 0.7514619883040936f, 0.7679738562091503f,
                            0.7802083333333333f, 0.7844311377245509f, 0.796875f,
                            0.8165618448637316f, 0.8487179487179487f, 0.8582375478927203f,
                            0.8562091503267975f, 0.8666666666666667f},
                    new float[] {1f, 1f, 1f, 1f, 1f, 0.8163265306122449f, 0.6653386454183268f,
                            0.7547169811320753f, 0.929824561403509f, 0.9558823529411766f,
                            0.9560439560439562f, 1f, 1f},
                    new float[] {0.07f, 0.12f, 0.17f, 0.2235294117647059f, 0.3f,
                            0.38431372549019605f, 0.492156862745098f, 0.5843137254901961f,
                            0.6647058823529411f, 0.7333333333333334f, 0.8215686274509804f, 0.9f,
                            0.9411764705882353f}
            ),
            new TonalPalette(
                    new float[] {0.6666666666666666f, 0.6666666666666666f, 0.6666666666666666f,
                            0.6666666666666666f, 0.6666666666666666f, 0.6666666666666666f,
                            0.6666666666666666f, 0.6666666666666666f, 0.6666666666666666f,
                            0.6666666666666666f, 0.6666666666666666f},
                    new float[] {0.25f, 0.24590163934426232f, 0.17880794701986752f,
                            0.14606741573033713f, 0.13761467889908252f, 0.14893617021276592f,
                            0.16756756756756758f, 0.20312500000000017f, 0.26086956521739135f,
                            0.29999999999999966f, 0.5000000000000004f},
                    new float[] {0.18f, 0.2392156862745098f, 0.296078431372549f,
                            0.34901960784313724f, 0.4274509803921569f, 0.5392156862745098f,
                            0.6372549019607843f, 0.7490196078431373f, 0.8196078431372549f,
                            0.8823529411764706f, 0.9372549019607843f}
            ),
            new TonalPalette(
                    new float[] {0.938f, 0.944f, 0.952f, 0.961f, 0.9678571428571429f,
                            0.9944812362030905f, 0f, 0f,
                            0.0047348484848484815f, 0.00316455696202532f, 0f,
                            0.9980392156862745f, 0.9814814814814816f, 0.9722222222222221f},
                    new float[] {1f, 1f, 1f, 1f, 1f, 0.7023255813953488f, 0.6638655462184874f,
                            0.6521739130434782f, 0.7719298245614035f, 0.8315789473684211f,
                            0.6867469879518071f, 0.7264957264957265f, 0.8181818181818182f,
                            0.8181818181818189f},
                    new float[] {0.08f, 0.13f, 0.18f, 0.23f, 0.27450980392156865f,
                            0.4215686274509804f,
                            0.4666666666666667f, 0.503921568627451f, 0.5529411764705883f,
                            0.6274509803921569f, 0.6745098039215687f, 0.7705882352941176f,
                            0.892156862745098f, 0.9568627450980391f}
            ),
            new TonalPalette(
                    new float[] {0.88f, 0.888f, 0.897f, 0.9052287581699346f, 0.9112021857923498f,
                            0.9270152505446624f, 0.9343137254901961f, 0.9391534391534391f,
                            0.9437984496124031f, 0.943661971830986f, 0.9438943894389439f,
                            0.9426229508196722f, 0.9444444444444444f},
                    new float[] {1f, 1f, 1f, 1f, 0.8133333333333332f, 0.7927461139896375f,
                            0.7798165137614679f, 0.7777777777777779f, 0.8190476190476191f,
                            0.8255813953488372f, 0.8211382113821142f, 0.8133333333333336f,
                            0.8000000000000006f},
                    new float[] {0.08f, 0.12f, 0.16f, 0.2f, 0.29411764705882354f,
                            0.3784313725490196f, 0.42745098039215684f, 0.4764705882352941f,
                            0.5882352941176471f, 0.6627450980392157f, 0.7588235294117647f,
                            0.8529411764705882f, 0.9411764705882353f}
            ),
            new TonalPalette(
                    new float[] {0.669f, 0.680f, 0.6884057971014492f, 0.6974789915966387f,
                            0.7079889807162534f, 0.7154471544715447f, 0.7217741935483872f,
                            0.7274143302180687f, 0.7272727272727273f, 0.7258064516129031f,
                            0.7252252252252251f, 0.7333333333333333f},
                    new float[] {0.81f, 0.81f, 0.8214285714285715f, 0.6878612716763006f,
                            0.6080402010050251f, 0.5774647887323943f, 0.5391304347826086f,
                            0.46724890829694316f, 0.4680851063829788f, 0.462686567164179f,
                            0.45679012345678977f, 0.4545454545454551f},
                    new float[] {0.12f, 0.16f, 0.2196078431372549f, 0.33921568627450976f,
                            0.39019607843137255f, 0.4176470588235294f, 0.45098039215686275f,
                            0.5509803921568628f, 0.6313725490196078f, 0.7372549019607844f,
                            0.8411764705882353f, 0.9352941176470588f}
            ),
            new TonalPalette(
                    new float[] {0.6470588235294118f, 0.6516666666666667f, 0.6464174454828661f,
                            0.6441441441441442f, 0.6432748538011696f, 0.6416666666666667f,
                            0.6402439024390243f, 0.6412429378531074f, 0.6435185185185186f,
                            0.6428571428571429f},
                    new float[] {0.8095238095238095f, 0.6578947368421053f, 0.5721925133689839f,
                            0.5362318840579711f, 0.5f, 0.4424778761061947f, 0.44086021505376327f,
                            0.44360902255639095f, 0.4499999999999997f, 0.4375000000000006f},
                    new float[] {0.16470588235294117f, 0.2980392156862745f, 0.36666666666666664f,
                            0.40588235294117647f, 0.44705882352941173f,
                            0.5568627450980392f, 0.6352941176470588f, 0.7392156862745098f,
                            0.8431372549019608f, 0.9372549019607843f}
            ),
            new TonalPalette(
                    new float[] {0.469f, 0.46732026143790845f, 0.4718614718614719f,
                            0.4793650793650794f, 0.48071625344352614f, 0.4829683698296837f,
                            0.484375f, 0.4841269841269842f, 0.48444444444444457f,
                            0.48518518518518516f, 0.4907407407407408f},
                    new float[] {1f, 1f, 1f, 1f, 1f, 1f, 0.6274509803921569f, 0.41832669322709176f,
                            0.41899441340782106f, 0.4128440366972478f, 0.4090909090909088f},
                    new float[] {0.07f, 0.1f, 0.15098039215686274f, 0.20588235294117646f,
                            0.2372549019607843f, 0.26862745098039215f, 0.4f, 0.5078431372549019f,
                            0.6490196078431372f, 0.7862745098039216f, 0.9137254901960784f}
            ),
            new TonalPalette(
                    new float[] {0.542f, 0.5444444444444444f, 0.5555555555555556f,
                            0.5555555555555556f, 0.553763440860215f, 0.5526315789473684f,
                            0.5555555555555556f, 0.5555555555555555f, 0.5555555555555556f,
                            0.5512820512820514f, 0.5666666666666667f},
                    new float[] {0.25f, 0.24590163934426232f, 0.19148936170212766f,
                            0.1791044776119403f, 0.18343195266272191f, 0.18446601941747576f,
                            0.1538461538461539f, 0.15625000000000003f, 0.15328467153284678f,
                            0.15662650602409653f, 0.151515151515151f},
                    new float[] {0.05f, 0.1196078431372549f, 0.1843137254901961f,
                            0.2627450980392157f,
                            0.33137254901960783f, 0.403921568627451f, 0.5411764705882354f,
                            0.6235294117647059f, 0.7313725490196079f, 0.8372549019607843f,
                            0.9352941176470588f}
            ),
            new TonalPalette(
                    new float[] {0.022222222222222223f, 0.02469135802469136f, 0.031249999999999997f,
                            0.03947368421052631f, 0.04166666666666668f,
                            0.043650793650793655f, 0.04411764705882352f, 0.04166666666666652f,
                            0.04444444444444459f, 0.05555555555555529f},
                    new float[] {0.33333333333333337f, 0.2783505154639175f, 0.2580645161290323f,
                            0.25675675675675674f, 0.2528735632183908f, 0.17500000000000002f,
                            0.15315315315315312f, 0.15189873417721522f,
                            0.15789473684210534f, 0.15789473684210542f},
                    new float[] {0.08823529411764705f, 0.19019607843137254f, 0.2431372549019608f,
                            0.2901960784313725f, 0.3411764705882353f, 0.47058823529411764f,
                            0.5647058823529412f, 0.6901960784313725f, 0.8137254901960784f,
                            0.9254901960784314f}
            ),
            new TonalPalette(
                    new float[] {0.027f, 0.03f, 0.038f, 0.044f, 0.050884955752212385f,
                            0.07254901960784313f, 0.0934640522875817f,
                            0.10457516339869281f, 0.11699346405228758f,
                            0.1255813953488372f, 0.1268939393939394f, 0.12533333333333332f,
                            0.12500000000000003f, 0.12777777777777777f},
                    new float[] {1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f},
                    new float[] {0.25f, 0.3f, 0.35f, 0.4f, 0.44313725490196076f, 0.5f, 0.5f, 0.5f,
                            0.5f, 0.5784313725490196f,
                            0.6549019607843137f, 0.7549019607843137f, 0.8509803921568627f,
                            0.9411764705882353f}
            )
    };

    private static final TonalPalette GREY_PALETTE = new TonalPalette(
            new float[]{0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f},
            new float[]{0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f},
            new float[]{0.08f, 0.11f, 0.14901960784313725f, 0.2f, 0.2980392156862745f, 0.4f,
                    0.4980392156862745f, 0.6196078431372549f, 0.7176470588235294f,
                    0.8196078431372549f, 0.9176470588235294f, 0.9490196078431372f}
    );

    @SuppressWarnings("WeakerAccess")
    static final ColorRange[] BLACKLISTED_COLORS = new ColorRange[] {

            // Red
            new ColorRange(
                    new Range<>(0f, 20f) /* H */,
                    new Range<>(0.7f, 1f) /* S */,
                    new Range<>(0.21f, 0.79f)) /* L */,
            new ColorRange(
                    new Range<>(0f, 20f),
                    new Range<>(0.3f, 0.7f),
                    new Range<>(0.355f, 0.653f)),

            // Red Orange
            new ColorRange(
                    new Range<>(20f, 40f),
                    new Range<>(0.7f, 1f),
                    new Range<>(0.28f, 0.643f)),
            new ColorRange(
                    new Range<>(20f, 40f),
                    new Range<>(0.3f, 0.7f),
                    new Range<>(0.414f, 0.561f)),
            new ColorRange(
                    new Range<>(20f, 40f),
                    new Range<>(0f, 3f),
                    new Range<>(0.343f, 0.584f)),

            // Orange
            new ColorRange(
                    new Range<>(40f, 60f),
                    new Range<>(0.7f, 1f),
                    new Range<>(0.173f, 0.349f)),
            new ColorRange(
                    new Range<>(40f, 60f),
                    new Range<>(0.3f, 0.7f),
                    new Range<>(0.233f, 0.427f)),
            new ColorRange(
                    new Range<>(40f, 60f),
                    new Range<>(0f, 0.3f),
                    new Range<>(0.231f, 0.484f)),

            // Yellow 60
            new ColorRange(
                    new Range<>(60f, 80f),
                    new Range<>(0.7f, 1f),
                    new Range<>(0.488f, 0.737f)),
            new ColorRange(
                    new Range<>(60f, 80f),
                    new Range<>(0.3f, 0.7f),
                    new Range<>(0.673f, 0.837f)),

            // Yellow Green 80
            new ColorRange(
                    new Range<>(80f, 100f),
                    new Range<>(0.7f, 1f),
                    new Range<>(0.469f, 0.61f)),

            // Yellow green 100
            new ColorRange(
                    new Range<>(100f, 120f),
                    new Range<>(0.7f, 1f),
                    new Range<>(0.388f, 0.612f)),
            new ColorRange(
                    new Range<>(100f, 120f),
                    new Range<>(0.3f, 0.7f),
                    new Range<>(0.424f, 0.541f)),

            // Green
            new ColorRange(
                    new Range<>(120f, 140f),
                    new Range<>(0.7f, 1f),
                    new Range<>(0.375f, 0.52f)),
            new ColorRange(
                    new Range<>(120f, 140f),
                    new Range<>(0.3f, 0.7f),
                    new Range<>(0.435f, 0.524f)),

            // Green Blue 140
            new ColorRange(
                    new Range<>(140f, 160f),
                    new Range<>(0.7f, 1f),
                    new Range<>(0.496f, 0.641f)),

            // Seafoam
            new ColorRange(
                    new Range<>(160f, 180f),
                    new Range<>(0.7f, 1f),
                    new Range<>(0.496f, 0.567f)),

            // Cyan
            new ColorRange(
                    new Range<>(180f, 200f),
                    new Range<>(0.7f, 1f),
                    new Range<>(0.52f, 0.729f)),

            // Blue
            new ColorRange(
                    new Range<>(220f, 240f),
                    new Range<>(0.7f, 1f),
                    new Range<>(0.396f, 0.571f)),
            new ColorRange(
                    new Range<>(220f, 240f),
                    new Range<>(0.3f, 0.7f),
                    new Range<>(0.425f, 0.551f)),

            // Blue Purple 240
            new ColorRange(
                    new Range<>(240f, 260f),
                    new Range<>(0.7f, 1f),
                    new Range<>(0.418f, 0.639f)),
            new ColorRange(
                    new Range<>(220f, 240f),
                    new Range<>(0.3f, 0.7f),
                    new Range<>(0.441f, 0.576f)),

            // Blue Purple 260
            new ColorRange(
                    new Range<>(260f, 280f),
                    new Range<>(0.3f, 1f), // Bigger range
                    new Range<>(0.461f, 0.553f)),

            // Fuchsia
            new ColorRange(
                    new Range<>(300f, 320f),
                    new Range<>(0.7f, 1f),
                    new Range<>(0.484f, 0.588f)),
            new ColorRange(
                    new Range<>(300f, 320f),
                    new Range<>(0.3f, 0.7f),
                    new Range<>(0.48f, 0.592f)),

            // Pink
            new ColorRange(
                    new Range<>(320f, 340f),
                    new Range<>(0.7f, 1f),
                    new Range<>(0.466f, 0.629f)),

            // Soft red
            new ColorRange(
                    new Range<>(340f, 360f),
                    new Range<>(0.7f, 1f),
                    new Range<>(0.437f, 0.596f))
    };

    /**
     * Representation of an HSL color range.
     * <ul>
     * <li>hsl[0] is Hue [0 .. 360)</li>
     * <li>hsl[1] is Saturation [0...1]</li>
     * <li>hsl[2] is Lightness [0...1]</li>
     * </ul>
     */
    static class ColorRange {
        private Range<Float> mHue;
        private Range<Float> mSaturation;
        private Range<Float> mLightness;

        ColorRange(Range<Float> hue, Range<Float> saturation, Range<Float> lightness) {
            mHue = hue;
            mSaturation = saturation;
            mLightness = lightness;
        }

        boolean containsColor(float h, float s, float l) {
            if (!mHue.contains(h)) {
                return false;
            } else if (!mSaturation.contains(s)) {
                return false;
            } else if (!mLightness.contains(l)) {
                return false;
            }
            return true;
        }

        float[] getCenter() {
            return new float[] {
                    mHue.getLower() + (mHue.getUpper() - mHue.getLower()) / 2f,
                    mSaturation.getLower() + (mSaturation.getUpper() - mSaturation.getLower()) / 2f,
                    mLightness.getLower() + (mLightness.getUpper() - mLightness.getLower()) / 2f
            };
        }

        @Override
        public String toString() {
            return String.format("H: %s, S: %s, L %s", mHue, mSaturation, mLightness);
        }
    }

    private static List<Integer> getMainColors(WallpaperColorsCompat wallpaperColors) {
        LinkedList<Integer> colors = new LinkedList<>();
        if (wallpaperColors.getPrimaryColor() != 0) {
            colors.add(wallpaperColors.getPrimaryColor());
        }
        if (wallpaperColors.getSecondaryColor() != 0) {
            colors.add(wallpaperColors.getSecondaryColor());
        }
        if (wallpaperColors.getTertiaryColor() != 0) {
            colors.add(wallpaperColors.getTertiaryColor());
        }
        return colors;
    }
}
