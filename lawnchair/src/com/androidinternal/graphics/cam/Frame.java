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

package com.androidinternal.graphics.cam;

import androidx.annotation.NonNull;

import com.google.android.material.math.MathUtils;

/**
 * The frame, or viewing conditions, where a color was seen. Used, along with a color, to create a
 * color appearance model representing the color.
 *
 * <p>To convert a traditional color to a color appearance model, it requires knowing what
 * conditions the color was observed in. Our perception of color depends on, for example, the tone
 * of the light illuminating the color, how bright that light was, etc.
 *
 * <p>This class is modelled separately from the color appearance model itself because there are a
 * number of calculations during the color => CAM conversion process that depend only on the viewing
 * conditions. Caching those calculations in a Frame instance saves a significant amount of time.
 */
public final class Frame {
    // Standard viewing conditions assumed in RGB specification - Stokes, Anderson, Chandrasekar,
    // Motta - A Standard Default Color Space for the Internet: sRGB, 1996.
    //
    // White point = D65
    // Luminance of adapting field: 200 / Pi / 5, units are cd/m^2.
    //   sRGB ambient illuminance = 64 lux (per sRGB spec). However, the spec notes this is
    //     artificially low and based on monitors in 1990s. Use 200, the sRGB spec says this is the
    //     real average, and a survey of lux values on Wikipedia confirms this is a comfortable
    //     default: somewhere between a very dark overcast day and office lighting.
    //   Per CAM16 introduction paper (Li et al, 2017) Ew = pi * lw, and La = lw * Yb/Yw
    //   Ew = ambient environment luminance, in lux.
    //   Yb/Yw is taken to be midgray, ~20% relative luminance (XYZ Y 18.4, CIELAB L* 50).
    //   Therefore La = (Ew / pi) * .184
    //   La = 200 / pi * .184
    // Image surround to 10 degrees = ~20% relative luminance = CIELAB L* 50
    //
    // Not from sRGB standard:
    // Surround = average, 2.0.
    // Discounting illuminant = false, doesn't occur for self-luminous displays
    public static final Frame DEFAULT =
            Frame.make(
                    CamUtils.WHITE_POINT_D65,
                    (float) (200.0f / Math.PI * CamUtils.yFromLstar(50.0f) / 100.f), 50.0f, 2.0f,
                    false);

    private final float mAw;
    private final float mNbb;
    private final float mNcb;
    private final float mC;
    private final float mNc;
    private final float mN;
    private final float[] mRgbD;
    private final float mFl;
    private final float mFlRoot;
    private final float mZ;

    float getAw() {
        return mAw;
    }

    float getN() {
        return mN;
    }

    float getNbb() {
        return mNbb;
    }

    float getNcb() {
        return mNcb;
    }

    float getC() {
        return mC;
    }

    float getNc() {
        return mNc;
    }

    @NonNull
    float[] getRgbD() {
        return mRgbD;
    }

    float getFl() {
        return mFl;
    }

    float getFlRoot() {
        return mFlRoot;
    }

    float getZ() {
        return mZ;
    }

    private Frame(float n, float aw, float nbb, float ncb, float c, float nc, float[] rgbD,
                  float fl, float fLRoot, float z) {
        mN = n;
        mAw = aw;
        mNbb = nbb;
        mNcb = ncb;
        mC = c;
        mNc = nc;
        mRgbD = rgbD;
        mFl = fl;
        mFlRoot = fLRoot;
        mZ = z;
    }

    /** Create a custom frame. */
    @NonNull
    public static Frame make(@NonNull float[] whitepoint, float adaptingLuminance,
                             float backgroundLstar, float surround, boolean discountingIlluminant) {
        // Transform white point XYZ to 'cone'/'rgb' responses
        float[][] matrix = CamUtils.XYZ_TO_CAM16RGB;
        float[] xyz = whitepoint;
        float rW = (xyz[0] * matrix[0][0]) + (xyz[1] * matrix[0][1]) + (xyz[2] * matrix[0][2]);
        float gW = (xyz[0] * matrix[1][0]) + (xyz[1] * matrix[1][1]) + (xyz[2] * matrix[1][2]);
        float bW = (xyz[0] * matrix[2][0]) + (xyz[1] * matrix[2][1]) + (xyz[2] * matrix[2][2]);

        // Scale input surround, domain (0, 2), to CAM16 surround, domain (0.8, 1.0)
        float f = 0.8f + (surround / 10.0f);
        // "Exponential non-linearity"
        float c = (f >= 0.9) ? MathUtils.lerp(0.59f, 0.69f, ((f - 0.9f) * 10.0f)) : MathUtils.lerp(
                0.525f, 0.59f, ((f - 0.8f) * 10.0f));
        // Calculate degree of adaptation to illuminant
        float d = discountingIlluminant ? 1.0f : f * (1.0f - ((1.0f / 3.6f) * (float) Math.exp(
                (-adaptingLuminance - 42.0f) / 92.0f)));
        // Per Li et al, if D is greater than 1 or less than 0, set it to 1 or 0.
        d = (d > 1.0) ? 1.0f : (d < 0.0) ? 0.0f : d;
        // Chromatic induction factor
        float nc = f;

        // Cone responses to the whitepoint, adjusted for illuminant discounting.
        //
        // Why use 100.0 instead of the white point's relative luminance?
        //
        // Some papers and implementations, for both CAM02 and CAM16, use the Y
        // value of the reference white instead of 100. Fairchild's Color Appearance
        // Models (3rd edition) notes that this is in error: it was included in the
        // CIE 2004a report on CIECAM02, but, later parts of the conversion process
        // account for scaling of appearance relative to the white point relative
        // luminance. This part should simply use 100 as luminance.
        float[] rgbD = new float[]{d * (100.0f / rW) + 1.0f - d, d * (100.0f / gW) + 1.0f - d,
                d * (100.0f / bW) + 1.0f - d, };
        // Luminance-level adaptation factor
        float k = 1.0f / (5.0f * adaptingLuminance + 1.0f);
        float k4 = k * k * k * k;
        float k4F = 1.0f - k4;
        float fl = (k4 * adaptingLuminance) + (0.1f * k4F * k4F * (float) Math.cbrt(
                5.0 * adaptingLuminance));

        // Intermediate factor, ratio of background relative luminance to white relative luminance
        float n = CamUtils.yFromLstar(backgroundLstar) / whitepoint[1];

        // Base exponential nonlinearity
        // note Schlomer 2018 has a typo and uses 1.58, the correct factor is 1.48
        float z = 1.48f + (float) Math.sqrt(n);

        // Luminance-level induction factors
        float nbb = 0.725f / (float) Math.pow(n, 0.2);
        float ncb = nbb;

        // Discounted cone responses to the white point, adjusted for post-chromatic
        // adaptation perceptual nonlinearities.
        float[] rgbAFactors = new float[]{(float) Math.pow(fl * rgbD[0] * rW / 100.0, 0.42),
                (float) Math.pow(fl * rgbD[1] * gW / 100.0, 0.42), (float) Math.pow(
                fl * rgbD[2] * bW / 100.0, 0.42)};

        float[] rgbA = new float[]{(400.0f * rgbAFactors[0]) / (rgbAFactors[0] + 27.13f),
                (400.0f * rgbAFactors[1]) / (rgbAFactors[1] + 27.13f),
                (400.0f * rgbAFactors[2]) / (rgbAFactors[2] + 27.13f), };

        float aw = ((2.0f * rgbA[0]) + rgbA[1] + (0.05f * rgbA[2])) * nbb;

        return new Frame(n, aw, nbb, ncb, c, nc, rgbD, fl, (float) Math.pow(fl, 0.25), z);
    }
}
