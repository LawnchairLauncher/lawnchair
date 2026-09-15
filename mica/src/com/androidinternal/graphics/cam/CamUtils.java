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


import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

/**
 * Collection of methods for transforming between color spaces.
 *
 * <p>Methods are named $xFrom$Y. For example, lstarFromInt() returns L* from an ARGB integer.
 *
 * <p>These methods, generally, convert colors between the L*a*b*, XYZ, and sRGB spaces.
 *
 * <p>L*a*b* is a perceptually accurate color space. This is particularly important in the L*
 * dimension: it measures luminance and unlike lightness measures traditionally used in UI work via
 * RGB or HSL, this luminance transitions smoothly, permitting creation of pleasing shades of a
 * color, and more pleasing transitions between colors.
 *
 * <p>XYZ is commonly used as an intermediate color space for converting between one color space to
 * another. For example, to convert RGB to L*a*b*, first RGB is converted to XYZ, then XYZ is
 * convered to L*a*b*.
 *
 * <p>sRGB is a "specification originated from work in 1990s through cooperation by Hewlett-Packard
 * and Microsoft, and it was designed to be a standard definition of RGB for the internet, which it
 * indeed became...The standard is based on a sampling of computer monitors at the time...The whole
 * idea of sRGB is that if everyone assumed that RGB meant the same thing, then the results would be
 * consistent, and reasonably good. It worked." - Fairchild, Color Models and Systems: Handbook of
 * Color Psychology, 2015
 */
public final class CamUtils {
    private CamUtils() {
    }

    // Transforms XYZ color space coordinates to 'cone'/'RGB' responses in CAM16.
    static final float[][] XYZ_TO_CAM16RGB = {
            {0.401288f, 0.650173f, -0.051461f},
            {-0.250268f, 1.204414f, 0.045854f},
            {-0.002079f, 0.048952f, 0.953127f}
    };

    // Transforms 'cone'/'RGB' responses in CAM16 to XYZ color space coordinates.
    static final float[][] CAM16RGB_TO_XYZ = {
            {1.86206786f, -1.01125463f, 0.14918677f},
            {0.38752654f, 0.62144744f, -0.00897398f},
            {-0.01584150f, -0.03412294f, 1.04996444f}
    };

    // Need this, XYZ coordinates in internal ColorUtils are private

    // sRGB specification has D65 whitepoint - Stokes, Anderson, Chandrasekar, Motta - A Standard
    // Default Color Space for the Internet: sRGB, 1996
    static final float[] WHITE_POINT_D65 = {95.047f, 100.0f, 108.883f};

    // This is a more precise sRGB to XYZ transformation matrix than traditionally
    // used. It was derived using Schlomer's technique of transforming the xyY
    // primaries to XYZ, then applying a correction to ensure mapping from sRGB
    // 1, 1, 1 to the reference white point, D65.
    static final float[][] SRGB_TO_XYZ = {
            {0.41233895f, 0.35762064f, 0.18051042f},
            {0.2126f, 0.7152f, 0.0722f},
            {0.01932141f, 0.11916382f, 0.95034478f}
    };

    static int intFromLstar(float lstar) {
        if (lstar < 1) {
            return 0xff000000;
        } else if (lstar > 99) {
            return 0xffffffff;
        }

        // XYZ to LAB conversion routine, assume a and b are 0.
        float fy = (lstar + 16.0f) / 116.0f;

        // fz = fx = fy because a and b are 0
        float fz = fy;
        float fx = fy;

        float kappa = 24389f / 27f;
        float epsilon = 216f / 24389f;
        boolean lExceedsEpsilonKappa = (lstar > 8.0f);
        float yT = lExceedsEpsilonKappa ? fy * fy * fy : lstar / kappa;
        boolean cubeExceedEpsilon = (fy * fy * fy) > epsilon;
        float xT = cubeExceedEpsilon ? fx * fx * fx : (116f * fx - 16f) / kappa;
        float zT = cubeExceedEpsilon ? fz * fz * fz : (116f * fx - 16f) / kappa;

        return ColorUtils.XYZToColor(xT * CamUtils.WHITE_POINT_D65[0],
                yT * CamUtils.WHITE_POINT_D65[1], zT * CamUtils.WHITE_POINT_D65[2]);
    }

    /** Returns L* from L*a*b*, perceptual luminance, from an ARGB integer (ColorInt). */
    public static float lstarFromInt(int argb) {
        return lstarFromY(yFromInt(argb));
    }

    static float lstarFromY(float y) {
        y = y / 100.0f;
        final float e = 216.f / 24389.f;
        float yIntermediate;
        if (y <= e) {
            return ((24389.f / 27.f) * y);
        } else {
            yIntermediate = (float) Math.cbrt(y);
        }
        return 116.f * yIntermediate - 16.f;
    }

    static float yFromInt(int argb) {
        final float r = linearized(Color.red(argb));
        final float g = linearized(Color.green(argb));
        final float b = linearized(Color.blue(argb));
        float[][] matrix = SRGB_TO_XYZ;
        float y = (r * matrix[1][0]) + (g * matrix[1][1]) + (b * matrix[1][2]);
        return y;
    }

    @NonNull
    static float[] xyzFromInt(int argb) {
        final float r = linearized(Color.red(argb));
        final float g = linearized(Color.green(argb));
        final float b = linearized(Color.blue(argb));

        float[][] matrix = SRGB_TO_XYZ;
        float x = (r * matrix[0][0]) + (g * matrix[0][1]) + (b * matrix[0][2]);
        float y = (r * matrix[1][0]) + (g * matrix[1][1]) + (b * matrix[1][2]);
        float z = (r * matrix[2][0]) + (g * matrix[2][1]) + (b * matrix[2][2]);
        return new float[]{x, y, z};
    }

    static float yFromLstar(float lstar) {
        float ke = 8.0f;
        if (lstar > ke) {
            return (float) Math.pow(((lstar + 16.0) / 116.0), 3) * 100f;
        } else {
            return lstar / (24389f / 27f) * 100f;
        }
    }

    static float linearized(int rgbComponent) {
        float normalized = (float) rgbComponent / 255.0f;

        if (normalized <= 0.04045f) {
            return (normalized / 12.92f) * 100.0f;
        } else {
            return (float) Math.pow(((normalized + 0.055f) / 1.055f), 2.4f) * 100.0f;
        }
    }
}
