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
package com.android.launcher3.icons;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.SparseArray;
import java.util.Arrays;

/**
 * Utility class for extracting colors from a bitmap.
 */
public class ColorExtractor {

    private final int NUM_SAMPLES = 20;
    private final float[] mTmpHsv = new float[3];
    private final float[] mTmpHueScoreHistogram = new float[360];
    private final int[] mTmpPixels = new int[NUM_SAMPLES];
    private final SparseArray<Float> mTmpRgbScores = new SparseArray<>();

    /**
     * This picks a dominant color, looking for high-saturation, high-value, repeated hues.
     * @param bitmap The bitmap to scan
     */
    public int findDominantColorByHue(Bitmap bitmap) {
        return findDominantColorByHue(bitmap, NUM_SAMPLES);
    }

    /**
     * This picks a dominant color, looking for high-saturation, high-value, repeated hues.
     * @param bitmap The bitmap to scan
     */
    public int findDominantColorByHue(Bitmap bitmap, int samples) {
        final int height = bitmap.getHeight();
        final int width = bitmap.getWidth();
        int sampleStride = (int) Math.sqrt((height * width) / samples);
        if (sampleStride < 1) {
            sampleStride = 1;
        }

        // This is an out-param, for getting the hsv values for an rgb
        float[] hsv = mTmpHsv;
        Arrays.fill(hsv, 0);

        // First get the best hue, by creating a histogram over 360 hue buckets,
        // where each pixel contributes a score weighted by saturation, value, and alpha.
        float[] hueScoreHistogram = mTmpHueScoreHistogram;
        Arrays.fill(hueScoreHistogram, 0);
        float highScore = -1;
        int bestHue = -1;

        int[] pixels = mTmpPixels;
        Arrays.fill(pixels, 0);
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

        SparseArray<Float> rgbScores = mTmpRgbScores;
        rgbScores.clear();
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
}
