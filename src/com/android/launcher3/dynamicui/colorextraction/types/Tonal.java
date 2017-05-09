package com.android.launcher3.dynamicui.colorextraction.types;

import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.graphics.ColorUtils;
import android.util.Log;
import android.util.Pair;

import com.android.launcher3.dynamicui.colorextraction.ColorExtractor;
import com.android.launcher3.dynamicui.colorextraction.WallpaperColorsCompat;

import java.util.Comparator;


/**
 * Implementation of tonal color extraction
 *
 * TODO remove this class if available by platform
 */
public class Tonal implements ExtractionType {
    private static final String TAG = "Tonal";

    // Used for tonal palette fitting
    private static final float FIT_WEIGHT_H = 1.0f;
    private static final float FIT_WEIGHT_S = 1.0f;
    private static final float FIT_WEIGHT_L = 10.0f;

    private static final float MIN_COLOR_OCCURRENCE = 0.1f;
    private static final float MIN_LUMINOSITY = 0.5f;

    public void extractInto(WallpaperColorsCompat wallpaperColors,
                            ColorExtractor.GradientColors gradientColors) {
        if (wallpaperColors.getColors().size() == 0) {
            return;
        }
        // Tonal is not really a sort, it takes a color from the extracted
        // palette and finds a best fit amongst a collection of pre-defined
        // palettes. The best fit is tweaked to be closer to the source color
        // and replaces the original palette

        // First find the most representative color in the image
        populationSort(wallpaperColors);
        // Calculate total
        int total = 0;
        for (Pair<Color, Integer> weightedColor : wallpaperColors.getColors()) {
            total += weightedColor.second;
        }

        // Get bright colors that occur often enough in this image
        Pair<Color, Integer> bestColor = null;
        float[] hsl = new float[3];
        for (Pair<Color, Integer> weightedColor : wallpaperColors.getColors()) {
            float colorOccurrence = weightedColor.second / (float) total;
            if (colorOccurrence < MIN_COLOR_OCCURRENCE) {
                break;
            }

            int colorValue = weightedColor.first.toArgb();
            ColorUtils.RGBToHSL(Color.red(colorValue), Color.green(colorValue),
                    Color.blue(colorValue), hsl);
            if (hsl[2] > MIN_LUMINOSITY) {
                bestColor = weightedColor;
            }
        }

        // Fallback to first color
        if (bestColor == null) {
            bestColor = wallpaperColors.getColors().get(0);
        }

        int colorValue = bestColor.first.toArgb();
        ColorUtils.RGBToHSL(Color.red(colorValue), Color.green(colorValue), Color.blue(colorValue),
                hsl);
        hsl[0] /= 360.0f; // normalize

        // TODO, we're finding a tonal palette for a hue, not all components
        TonalPalette palette = findTonalPalette(hsl[0]);

        // Fall back to population sort if we couldn't find a tonal palette
        if (palette == null) {
            Log.w(TAG, "Could not find a tonal palette!");
            return;
        }

        int fitIndex = bestFit(palette, hsl[0], hsl[1], hsl[2]);
        if (fitIndex == -1) {
            Log.w(TAG, "Could not find best fit!");
            return;
        }
        float[] h = fit(palette.h, hsl[0], fitIndex,
                Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
        float[] s = fit(palette.s, hsl[1], fitIndex, 0.0f, 1.0f);
        float[] l = fit(palette.l, hsl[2], fitIndex, 0.0f, 1.0f);


        hsl[0] = fract(h[0]) * 360.0f;
        hsl[1] = s[0];
        hsl[2] = l[0];
        gradientColors.setMainColor(ColorUtils.HSLToColor(hsl));

        hsl[0] = fract(h[1]) * 360.0f;
        hsl[1] = s[1];
        hsl[2] = l[1];
        gradientColors.setSecondaryColor(ColorUtils.HSLToColor(hsl));
    }

    private static void populationSort(@NonNull WallpaperColorsCompat wallpaperColors) {
        wallpaperColors.getColors().sort(new Comparator<Pair<Color, Integer>>() {
            @Override
            public int compare(Pair<Color, Integer> a, Pair<Color, Integer> b) {
                return b.second - a.second;
            }
        });
    }

    /**
     * Offsets all colors by a delta, clamping values that go beyond what's
     * supported on the color space.
     * @param data what you want to fit
     * @param v how big should be the offset
     * @param index which index to calculate the delta against
     * @param min minimum accepted value (clamp)
     * @param max maximum accepted value (clamp)
     * @return
     */
    private static float[] fit(float[] data, float v, int index, float min, float max) {
        float[] fitData = new float[data.length];
        float delta = v - data[index];

        for (int i = 0; i < data.length; i++) {
            fitData[i] = constrain(data[i] + delta, min, max);
        }

        return fitData;
    }

    // TODO no MathUtils
    private static float constrain(float x, float min, float max) {
        x = Math.min(x, max);
        x = Math.max(x, min);
        return x;
    }

    /*function adjustSatLumForFit(val, points, fitIndex) {
        var fitValue = lerpBetweenPoints(points, fitIndex);
        var diff = val - fitValue;

        var newPoints = [];
        for (var ii=0; ii<points.length; ii++) {
            var point = [points[ii][0], points[ii][1]];
            point[1] += diff;
            if (point[1] > 1) point[1] = 1;
            if (point[1] < 0) point[1] = 0;
            newPoints[ii] = point;
        }
        return newPoints;
    }*/

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
    private static TonalPalette findTonalPalette(float h) {
        TonalPalette best = null;
        float error = Float.POSITIVE_INFINITY;

        for (TonalPalette candidate : TONAL_PALETTES) {
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
    // a best fist. Each palette is defined as 10 HSL colors
    private static final TonalPalette[] TONAL_PALETTES = {
            // Orange
            new TonalPalette(
                    new float[] { 0.028f, 0.042f, 0.053f, 0.061f, 0.078f, 0.1f, 0.111f, 0.111f, 0.111f, 0.111f },
                    new float[] { 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f },
                    new float[] { 0.5f, 0.53f, 0.54f, 0.55f, 0.535f, 0.52f, 0.5f, 0.63f, 0.75f, 0.85f }
            ),
            // Yellow
            new TonalPalette(
                    new float[] { 0.111f, 0.111f, 0.125f, 0.133f, 0.139f, 0.147f, 0.156f, 0.156f, 0.156f, 0.156f },
                    new float[] { 1f, 0.942f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f },
                    new float[] { 0.43f, 0.484f, 0.535f, 0.555f, 0.57f, 0.575f, 0.595f, 0.715f, 0.78f, 0.885f }
            ),
            // Green
            new TonalPalette(
                    new float[] { 0.325f, 0.336f, 0.353f, 0.353f, 0.356f, 0.356f, 0.356f, 0.356f, 0.356f, 0.356f },
                    new float[] { 1f, 1f, 0.852f, 0.754f, 0.639f, 0.667f, 0.379f, 0.542f, 1f, 1f },
                    new float[] { 0.06f, 0.1f, 0.151f, 0.194f, 0.25f, 0.312f, 0.486f, 0.651f, 0.825f, 0.885f }
            ),
            // Blue
            new TonalPalette(
                    new float[] { 0.631f, 0.603f, 0.592f, 0.586f, 0.572f, 0.544f, 0.519f, 0.519f, 0.519f, 0.519f },
                    new float[] { 0.852f, 1f, 0.887f, 0.852f, 0.871f, 0.907f, 0.949f, 0.934f, 0.903f, 0.815f },
                    new float[] { 0.34f, 0.38f, 0.482f, 0.497f, 0.536f, 0.571f, 0.608f, 0.696f, 0.794f, 0.892f }
            ),
            // Purple
            new TonalPalette(
                    new float[] { 0.839f, 0.831f, 0.825f, 0.819f, 0.803f, 0.803f, 0.772f, 0.772f, 0.772f, 0.772f },
                    new float[] { 1f, 1f, 1f, 1f, 1f, 1f, 0.769f, 0.701f, 0.612f, 0.403f },
                    new float[] { 0.125f, 0.15f, 0.2f, 0.245f, 0.31f, 0.36f, 0.567f, 0.666f, 0.743f, 0.833f }
            ),
            // Red
            new TonalPalette(
                    new float[] { 0.964f, 0.975f, 0.975f, 0.975f, 0.972f, 0.992f, 1.003f, 1.011f, 1.011f, 1.011f },
                    new float[] { 0.869f, 0.802f, 0.739f, 0.903f, 1f, 1f, 1f, 1f, 1f, 1f },
                    new float[] { 0.241f, 0.316f, 0.46f, 0.586f, 0.655f, 0.7f, 0.75f, 0.8f, 0.84f, 0.88f }
            )
    };
}

