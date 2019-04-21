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
package com.android.launcher3.graphics;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.graphics.ColorUtils;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import com.android.launcher3.Utilities;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kotlin.collections.ArraysKt;

import static java.lang.Math.abs;
import static java.lang.Math.min;
import static java.lang.Math.max;
import static java.lang.Math.round;

/**
 * Utility class for extracting colors from a bitmap.
 */
public class ColorExtractor {

    private static final String TAG = "ColorExtractor";

    public static int findDominantColorByHue(Bitmap bitmap) {
        return findDominantColorByHue(bitmap, 20);
    }

    /**
     * This picks a dominant color, looking for high-saturation, high-value, repeated hues.
     *
     * @param bitmap The bitmap to scan
     * @param samples The approximate max number of samples to use.
     */
    public static int findDominantColorByHue(Bitmap bitmap, int samples) {
        final int height = bitmap.getHeight();
        final int width = bitmap.getWidth();
        int sampleStride = (int) Math.sqrt((height * width) / samples);
        if (sampleStride < 1) {
            sampleStride = 1;
        }

        // This is an out-param, for getting the hsv values for an rgb
        float[] hsv = new float[3];

        // First get the best hue, by creating a histogram over 360 hue buckets,
        // where each pixel contributes a score weighted by saturation, value, and alpha.
        float[] hueScoreHistogram = new float[360];
        float highScore = -1;
        int bestHue = -1;

        int[] pixels = new int[samples];
        int pixelCount = 0;

        for (int y = 0; y < height; y += sampleStride) {
            for (int x = 0; x < width; x += sampleStride) {
                int argb = bitmap.getPixel(x, y);
                int alpha = 0xFF & (argb >> 24);
                if (alpha < 0x80) {
                    // Drop mostly-transparent pixels.
                    continue;
                }
                // Remove the alpha channel.
                int rgb = argb | 0xFF000000;
                Color.colorToHSV(rgb, hsv);
                // Bucket colors by the 360 integer hues.
                int hue = (int) hsv[0];
                if (hue < 0 || hue >= hueScoreHistogram.length) {
                    // Defensively avoid array bounds violations.
                    continue;
                }
                if (pixelCount < samples) {
                    pixels[pixelCount++] = rgb;
                }
                float score = hsv[1] * hsv[2];
                hueScoreHistogram[hue] += score;
                if (hueScoreHistogram[hue] > highScore) {
                    highScore = hueScoreHistogram[hue];
                    bestHue = hue;
                }
            }
        }

        SparseArray<Float> rgbScores = new SparseArray<>();
        int bestColor = 0xff000000;
        highScore = -1;
        // Go back over the RGB colors that match the winning hue,
        // creating a histogram of weighted s*v scores, for up to 100*100 [s,v] buckets.
        // The highest-scoring RGB color wins.
        for (int i = 0; i < pixelCount; i++) {
            int rgb = pixels[i];
            Color.colorToHSV(rgb, hsv);
            int hue = (int) hsv[0];
            if (hue == bestHue) {
                float s = hsv[1];
                float v = hsv[2];
                int bucket = (int) (s * 100) + (int) (v * 10000);
                // Score by cumulative saturation * value.
                float score = s * v;
                Float oldTotal = rgbScores.get(bucket);
                float newTotal = oldTotal == null ? score : oldTotal + score;
                rgbScores.put(bucket, newTotal);
                if (newTotal > highScore) {
                    highScore = newTotal;
                    // All the colors in the winning bucket are very similar. Last in wins.
                    bestColor = rgb;
                }
            }
        }
        return bestColor;
    }

    // Average number of derived colors (based on averages with ~100 icons and performance testing)
    private static final int NUMBER_OF_COLORS_GUESSTIMATION = 45;

    /**
     * This picks a dominant color judging by how often it appears and modifies it to provide
     * sufficient contrast to the pbitmap.
     *
     * @param bitmap The bitmap to scan
     */
    public static int generateBackgroundColor(Bitmap bitmap, boolean addFillIn) {
        if (bitmap == null) {
            return Color.WHITE;
        }
        final int height = bitmap.getHeight();
        final int width = bitmap.getWidth();
        final int size = height * width;

        SparseIntArray rgbScoreHistogram = new SparseIntArray(NUMBER_OF_COLORS_GUESSTIMATION);
        final int[] pixels = new int[size];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int highScore = 0;
        int bestRGB = 0;
        int transparentScore = 0;
        for (int pixel : pixels) {
            int alpha = 0xFF & (pixel >> 24);
            if (alpha < 0xDD) {
                // Drop mostly-transparent pixels.
                transparentScore++;
                continue;
            }
            // Reduce color complexity.
            int rgb = posterize(pixel);
            if (rgb < 0) {
                // Defensively avoid array bounds violations.
                continue;
            }
            int currentScore = rgbScoreHistogram.get(rgb) + 1;
            rgbScoreHistogram.append(rgb, currentScore);
            if (currentScore > highScore) {
                highScore = currentScore;
                bestRGB = rgb;
            }
        }

        // return early if a mix-in isnt needed
        if (!addFillIn) {
            return bestRGB | 0xff << 24;
        }

        // Convert to HSL to get the lightness
        final float[] hsl = new float[3];
        ColorUtils.colorToHSL(bestRGB, hsl);
        float lightness = hsl[2];

        // "single color"
        boolean singleColor = rgbScoreHistogram.size() <= 2;
        boolean light = lightness > .5;
        // Apply dark background to mostly white icons
        boolean veryLight = lightness > .75 && singleColor;
        // Apply light background to mostly dark icons
        boolean veryDark = lightness < .30 && singleColor;

        // Adjust color to reach suitable contrast depending on the relationship between the colors
        float ratio = min(max(1f - (highScore / (float) (size - transparentScore)), .2f), .8f);

        if (singleColor) {
            // Invert ratio for "single color" foreground
            ratio = 1f - ratio;
        }

        // Vary color mix-in based on lightness and amount of colors
        int fill = (light && !veryLight) ||  veryDark? 0xFFFFFFFF : 0xFF333333;
        int background = ColorUtils.blendARGB(bestRGB | 0xff << 24, fill, ratio);
        return background | 0xff << 24;
    }

    public static boolean isSingleColor(Drawable drawable, int color) {
        if (drawable == null) return true;
        final int testColor = posterize(color);
        if (drawable instanceof ColorDrawable) {
            return posterize(((ColorDrawable) drawable).getColor()) == testColor;
        }
        final Bitmap bitmap = Utilities.drawableToBitmap(drawable);
        if (bitmap == null) {
            return false;
        }
        final int height = bitmap.getHeight();
        final int width = bitmap.getWidth();

        int[] pixels = new int[height * width];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        Set<Integer> set = new HashSet<>(ArraysKt.asList(pixels));
        Integer[] distinctPixels = new Integer[set.size()];
        set.toArray(distinctPixels);

        for (int pixel : distinctPixels) {
            if (testColor != posterize(pixel)) {
                return false;
            }
        }
        return true;
    }

    private static final int MAGIC_NUMBER = 25;

    /*
     * References:
     * https://www.cs.umb.edu/~jreyes/csit114-fall-2007/project4/filters.html#posterize
     * https://github.com/gitgraghu/image-processing/blob/master/src/Effects/Posterize.java
     */
    private static int posterize(int rgb) {
        int red = (0xff & (rgb >> 16));
        int green = (0xff & (rgb >> 8));
        int blue = (0xff & rgb);
        red -= red % MAGIC_NUMBER;
        green -= green % MAGIC_NUMBER;
        blue -= blue % MAGIC_NUMBER;
        if (red < 0) {
            red = 0;
        }
        if (green < 0) {
            green = 0;
        }
        if (blue < 0) {
            blue = 0;
        }
        return red << 16 | green << 8 | blue;
    }

    /**
     * Checks if a given icon can be considered "full-bleed-ish"
     * @param drawable the icon
     * @param bounds actual bounds (derived from IconNormalizer)
     * @return
     */
    public static boolean isFullBleed(Drawable drawable, RectF bounds) {
        if (drawable == null) return false;
        if (drawable instanceof ColorDrawable) return true;

        // Check if the icon is squareish
        final float ratio = (drawable.getIntrinsicHeight() * (1 - (bounds.top + bounds.bottom)) /
                (drawable.getIntrinsicWidth() * (1 - (bounds.left + bounds.right))));
        if (ratio < 0.99 || ratio > 1.001) return false;

        final Bitmap bitmap = Utilities.drawableToBitmap(drawable);
        if (bitmap == null) {
            return false;
        }
        if (!bitmap.hasAlpha()) {
            return true;
        }

        final int height = bitmap.getHeight();
        final int width = bitmap.getWidth();
        final int size = height * width;

        int[] pixels = new int[height * width];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        // Calculate number of padding pixels
        // TODO: make this calculation a bit more readable
        float adjHeight = height - bounds.top - bounds.bottom;
        int addPixels = Math.round(bounds.left * width * adjHeight + bounds.top * height * width + bounds.right * width * adjHeight + bounds.bottom * height * width);


        // Any icon with less than 2% transparent pixels (padding excluded) is considered "full-bleed-ish"
        final int maxTransparent = (int) (round(size * .035) + addPixels);
        int count = 0;

        for (int pixel : pixels) {
            int alpha = 0xFF & (pixel >> 24);
            if (alpha < 0xDD) {
                count++;
                // return as soon as we pass the limit of transparent pixels
                if (count > maxTransparent) {
                    return false;
                }
            }
        }
        return true;
    }
}
