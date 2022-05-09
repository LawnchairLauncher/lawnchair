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
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

/**
 * A color appearance model, based on CAM16, extended to use L* as the lightness dimension, and
 * coupled to a gamut mapping algorithm. Creates a color system, enables a digital design system.
 */
public class Cam {
    // The maximum difference between the requested L* and the L* returned.
    private static final float DL_MAX = 0.2f;
    // The maximum color distance, in CAM16-UCS, between a requested color and the color returned.
    private static final float DE_MAX = 1.0f;
    // When the delta between the floor & ceiling of a binary search for chroma is less than this,
    // the binary search terminates.
    private static final float CHROMA_SEARCH_ENDPOINT = 0.4f;
    // When the delta between the floor & ceiling of a binary search for J, lightness in CAM16,
    // is less than this, the binary search terminates.
    private static final float LIGHTNESS_SEARCH_ENDPOINT = 0.01f;

    // CAM16 color dimensions, see getters for documentation.
    private final float mHue;
    private final float mChroma;
    private final float mJ;
    private final float mQ;
    private final float mM;
    private final float mS;

    // Coordinates in UCS space. Used to determine color distance, like delta E equations in L*a*b*.
    private final float mJstar;
    private final float mAstar;
    private final float mBstar;

    /** Hue in CAM16 */
    public float getHue() {
        return mHue;
    }

    /** Chroma in CAM16 */
    public float getChroma() {
        return mChroma;
    }

    /** Lightness in CAM16 */
    public float getJ() {
        return mJ;
    }

    /**
     * Brightness in CAM16.
     *
     * <p>Prefer lightness, brightness is an absolute quantity. For example, a sheet of white paper
     * is much brighter viewed in sunlight than in indoor light, but it is the lightest object under
     * any lighting.
     */
    public float getQ() {
        return mQ;
    }

    /**
     * Colorfulness in CAM16.
     *
     * <p>Prefer chroma, colorfulness is an absolute quantity. For example, a yellow toy car is much
     * more colorful outside than inside, but it has the same chroma in both environments.
     */
    public float getM() {
        return mM;
    }

    /**
     * Saturation in CAM16.
     *
     * <p>Colorfulness in proportion to brightness. Prefer chroma, saturation measures colorfulness
     * relative to the color's own brightness, where chroma is colorfulness relative to white.
     */
    public float getS() {
        return mS;
    }

    /** Lightness coordinate in CAM16-UCS */
    public float getJstar() {
        return mJstar;
    }

    /** a* coordinate in CAM16-UCS */
    public float getAstar() {
        return mAstar;
    }

    /** b* coordinate in CAM16-UCS */
    public float getBstar() {
        return mBstar;
    }

    /** Construct a CAM16 color */
    Cam(float hue, float chroma, float j, float q, float m, float s, float jstar, float astar,
        float bstar) {
        mHue = hue;
        mChroma = chroma;
        mJ = j;
        mQ = q;
        mM = m;
        mS = s;
        mJstar = jstar;
        mAstar = astar;
        mBstar = bstar;
    }

    /**
     * Given a hue & chroma in CAM16, L* in L*a*b*, return an ARGB integer. The chroma of the color
     * returned may, and frequently will, be lower than requested. Assumes the color is viewed in
     * the
     * frame defined by the sRGB standard.
     */
    public static int getInt(float hue, float chroma, float lstar) {
        return getInt(hue, chroma, lstar, Frame.DEFAULT);
    }

    /**
     * Create a color appearance model from a ARGB integer representing a color. It is assumed the
     * color was viewed in the frame defined in the sRGB standard.
     */
    @NonNull
    public static Cam fromInt(int argb) {
        return fromIntInFrame(argb, Frame.DEFAULT);
    }

    /**
     * Create a color appearance model from a ARGB integer representing a color, specifying the
     * frame in which the color was viewed. Prefer Cam.fromInt.
     */
    @NonNull
    public static Cam fromIntInFrame(int argb, @NonNull Frame frame) {
        // Transform ARGB int to XYZ
        float[] xyz = CamUtils.xyzFromInt(argb);

        // Transform XYZ to 'cone'/'rgb' responses
        float[][] matrix = CamUtils.XYZ_TO_CAM16RGB;
        float rT = (xyz[0] * matrix[0][0]) + (xyz[1] * matrix[0][1]) + (xyz[2] * matrix[0][2]);
        float gT = (xyz[0] * matrix[1][0]) + (xyz[1] * matrix[1][1]) + (xyz[2] * matrix[1][2]);
        float bT = (xyz[0] * matrix[2][0]) + (xyz[1] * matrix[2][1]) + (xyz[2] * matrix[2][2]);

        // Discount illuminant
        float rD = frame.getRgbD()[0] * rT;
        float gD = frame.getRgbD()[1] * gT;
        float bD = frame.getRgbD()[2] * bT;

        // Chromatic adaptation
        float rAF = (float) Math.pow(frame.getFl() * Math.abs(rD) / 100.0, 0.42);
        float gAF = (float) Math.pow(frame.getFl() * Math.abs(gD) / 100.0, 0.42);
        float bAF = (float) Math.pow(frame.getFl() * Math.abs(bD) / 100.0, 0.42);
        float rA = Math.signum(rD) * 400.0f * rAF / (rAF + 27.13f);
        float gA = Math.signum(gD) * 400.0f * gAF / (gAF + 27.13f);
        float bA = Math.signum(bD) * 400.0f * bAF / (bAF + 27.13f);

        // redness-greenness
        float a = (float) (11.0 * rA + -12.0 * gA + bA) / 11.0f;
        // yellowness-blueness
        float b = (float) (rA + gA - 2.0 * bA) / 9.0f;

        // auxiliary components
        float u = (20.0f * rA + 20.0f * gA + 21.0f * bA) / 20.0f;
        float p2 = (40.0f * rA + 20.0f * gA + bA) / 20.0f;

        // hue
        float atan2 = (float) Math.atan2(b, a);
        float atanDegrees = atan2 * 180.0f / (float) Math.PI;
        float hue =
                atanDegrees < 0
                        ? atanDegrees + 360.0f
                        : atanDegrees >= 360 ? atanDegrees - 360.0f : atanDegrees;
        float hueRadians = hue * (float) Math.PI / 180.0f;

        // achromatic response to color
        float ac = p2 * frame.getNbb();

        // CAM16 lightness and brightness
        float j = 100.0f * (float) Math.pow(ac / frame.getAw(), frame.getC() * frame.getZ());
        float q =
                4.0f
                        / frame.getC()
                        * (float) Math.sqrt(j / 100.0f)
                        * (frame.getAw() + 4.0f)
                        * frame.getFlRoot();

        // CAM16 chroma, colorfulness, and saturation.
        float huePrime = (hue < 20.14) ? hue + 360 : hue;
        float eHue = 0.25f * (float) (Math.cos(huePrime * Math.PI / 180.0 + 2.0) + 3.8);
        float p1 = 50000.0f / 13.0f * eHue * frame.getNc() * frame.getNcb();
        float t = p1 * (float) Math.sqrt(a * a + b * b) / (u + 0.305f);
        float alpha =
                (float) Math.pow(t, 0.9) * (float) Math.pow(1.64 - Math.pow(0.29, frame.getN()),
                        0.73);
        // CAM16 chroma, colorfulness, saturation
        float c = alpha * (float) Math.sqrt(j / 100.0);
        float m = c * frame.getFlRoot();
        float s = 50.0f * (float) Math.sqrt((alpha * frame.getC()) / (frame.getAw() + 4.0f));

        // CAM16-UCS components
        float jstar = (1.0f + 100.0f * 0.007f) * j / (1.0f + 0.007f * j);
        float mstar = 1.0f / 0.0228f * (float) Math.log(1.0f + 0.0228f * m);
        float astar = mstar * (float) Math.cos(hueRadians);
        float bstar = mstar * (float) Math.sin(hueRadians);

        return new Cam(hue, c, j, q, m, s, jstar, astar, bstar);
    }

    /**
     * Create a CAM from lightness, chroma, and hue coordinates. It is assumed those coordinates
     * were measured in the sRGB standard frame.
     */
    @NonNull
    private static Cam fromJch(float j, float c, float h) {
        return fromJchInFrame(j, c, h, Frame.DEFAULT);
    }

    /**
     * Create a CAM from lightness, chroma, and hue coordinates, and also specify the frame in which
     * the color is being viewed.
     */
    @NonNull
    private static Cam fromJchInFrame(float j, float c, float h, Frame frame) {
        float q =
                4.0f
                        / frame.getC()
                        * (float) Math.sqrt(j / 100.0)
                        * (frame.getAw() + 4.0f)
                        * frame.getFlRoot();
        float m = c * frame.getFlRoot();
        float alpha = c / (float) Math.sqrt(j / 100.0);
        float s = 50.0f * (float) Math.sqrt((alpha * frame.getC()) / (frame.getAw() + 4.0f));

        float hueRadians = h * (float) Math.PI / 180.0f;
        float jstar = (1.0f + 100.0f * 0.007f) * j / (1.0f + 0.007f * j);
        float mstar = 1.0f / 0.0228f * (float) Math.log(1.0 + 0.0228 * m);
        float astar = mstar * (float) Math.cos(hueRadians);
        float bstar = mstar * (float) Math.sin(hueRadians);
        return new Cam(h, c, j, q, m, s, jstar, astar, bstar);
    }

    /**
     * Distance in CAM16-UCS space between two colors.
     *
     * <p>Much like L*a*b* was designed to measure distance between colors, the CAM16 standard
     * defined a color space called CAM16-UCS to measure distance between CAM16 colors.
     */
    public float distance(@NonNull Cam other) {
        float dJ = getJstar() - other.getJstar();
        float dA = getAstar() - other.getAstar();
        float dB = getBstar() - other.getBstar();
        double dEPrime = Math.sqrt(dJ * dJ + dA * dA + dB * dB);
        double dE = 1.41 * Math.pow(dEPrime, 0.63);
        return (float) dE;
    }

    /** Returns perceived color as an ARGB integer, as viewed in standard sRGB frame. */
    public int viewedInSrgb() {
        return viewed(Frame.DEFAULT);
    }

    /** Returns color perceived in a frame as an ARGB integer. */
    public int viewed(@NonNull Frame frame) {
        float alpha =
                (getChroma() == 0.0 || getJ() == 0.0)
                        ? 0.0f
                        : getChroma() / (float) Math.sqrt(getJ() / 100.0);

        float t =
                (float) Math.pow(alpha / Math.pow(1.64 - Math.pow(0.29, frame.getN()), 0.73),
                        1.0 / 0.9);
        float hRad = getHue() * (float) Math.PI / 180.0f;

        float eHue = 0.25f * (float) (Math.cos(hRad + 2.0) + 3.8);
        float ac = frame.getAw() * (float) Math.pow(getJ() / 100.0,
                1.0 / frame.getC() / frame.getZ());
        float p1 = eHue * (50000.0f / 13.0f) * frame.getNc() * frame.getNcb();
        float p2 = (ac / frame.getNbb());

        float hSin = (float) Math.sin(hRad);
        float hCos = (float) Math.cos(hRad);

        float gamma =
                23.0f * (p2 + 0.305f) * t / (23.0f * p1 + 11.0f * t * hCos + 108.0f * t * hSin);
        float a = gamma * hCos;
        float b = gamma * hSin;
        float rA = (460.0f * p2 + 451.0f * a + 288.0f * b) / 1403.0f;
        float gA = (460.0f * p2 - 891.0f * a - 261.0f * b) / 1403.0f;
        float bA = (460.0f * p2 - 220.0f * a - 6300.0f * b) / 1403.0f;

        float rCBase = (float) Math.max(0, (27.13 * Math.abs(rA)) / (400.0 - Math.abs(rA)));
        float rC = Math.signum(rA) * (100.0f / frame.getFl()) * (float) Math.pow(rCBase,
                1.0 / 0.42);
        float gCBase = (float) Math.max(0, (27.13 * Math.abs(gA)) / (400.0 - Math.abs(gA)));
        float gC = Math.signum(gA) * (100.0f / frame.getFl()) * (float) Math.pow(gCBase,
                1.0 / 0.42);
        float bCBase = (float) Math.max(0, (27.13 * Math.abs(bA)) / (400.0 - Math.abs(bA)));
        float bC = Math.signum(bA) * (100.0f / frame.getFl()) * (float) Math.pow(bCBase,
                1.0 / 0.42);
        float rF = rC / frame.getRgbD()[0];
        float gF = gC / frame.getRgbD()[1];
        float bF = bC / frame.getRgbD()[2];


        float[][] matrix = CamUtils.CAM16RGB_TO_XYZ;
        float x = (rF * matrix[0][0]) + (gF * matrix[0][1]) + (bF * matrix[0][2]);
        float y = (rF * matrix[1][0]) + (gF * matrix[1][1]) + (bF * matrix[1][2]);
        float z = (rF * matrix[2][0]) + (gF * matrix[2][1]) + (bF * matrix[2][2]);

        int argb = ColorUtils.XYZToColor(x, y, z);
        return argb;
    }

    /**
     * Given a hue & chroma in CAM16, L* in L*a*b*, and the frame in which the color will be
     * viewed,
     * return an ARGB integer.
     *
     * <p>The chroma of the color returned may, and frequently will, be lower than requested. This
     * is
     * a fundamental property of color that cannot be worked around by engineering. For example, a
     * red
     * hue, with high chroma, and high L* does not exist: red hues have a maximum chroma below 10
     * in
     * light shades, creating pink.
     */
    public static int getInt(float hue, float chroma, float lstar, @NonNull Frame frame) {
        // This is a crucial routine for building a color system, CAM16 itself is not sufficient.
        //
        // * Why these dimensions?
        // Hue and chroma from CAM16 are used because they're the most accurate measures of those
        // quantities. L* from L*a*b* is used because it correlates with luminance, luminance is
        // used to measure contrast for a11y purposes, thus providing a key constraint on what
        // colors
        // can be used.
        //
        // * Why is this routine required to build a color system?
        // In all perceptually accurate color spaces (i.e. L*a*b* and later), `chroma` may be
        // impossible for a given `hue` and `lstar`.
        // For example, a high chroma light red does not exist - chroma is limited to below 10 at
        // light red shades, we call that pink. High chroma light green does exist, but not dark
        // Also, when converting from another color space to RGB, the color may not be able to be
        // represented in RGB. In those cases, the conversion process ends with RGB values
        // outside 0-255
        // The vast majority of color libraries surveyed simply round to 0 to 255. That is not an
        // option for this library, as it distorts the expected luminance, and thus the expected
        // contrast needed for a11y
        //
        // * What does this routine do?
        // Dealing with colors in one color space not fitting inside RGB is, loosely referred to as
        // gamut mapping or tone mapping. These algorithms are traditionally idiosyncratic, there is
        // no universal answer. However, because the intent of this library is to build a system for
        // digital design, and digital design uses luminance to measure contrast/a11y, we have one
        // very important constraint that leads to an objective algorithm: the L* of the returned
        // color _must_ match the requested L*.
        //
        // Intuitively, if the color must be distorted to fit into the RGB gamut, and the L*
        // requested *must* be fulfilled, than the hue or chroma of the returned color will need
        // to be different from the requested hue/chroma.
        //
        // After exploring both options, it was more intuitive that if the requested chroma could
        // not be reached, it used the highest possible chroma. The alternative was finding the
        // closest hue where the requested chroma could be reached, but that is not nearly as
        // intuitive, as the requested hue is so fundamental to the color description.

        // If the color doesn't have meaningful chroma, return a gray with the requested Lstar.
        //
        // Yellows are very chromatic at L = 100, and blues are very chromatic at L = 0. All the
        // other hues are white at L = 100, and black at L = 0. To preserve consistency for users of
        // this system, it is better to simply return white at L* > 99, and black and L* < 0.
        if (chroma < 1.0 || Math.round(lstar) <= 0.0 || Math.round(lstar) >= 100.0) {
            return CamUtils.intFromLstar(lstar);
        }

        hue = hue < 0 ? 0 : Math.min(360, hue);

        // The highest chroma possible. Updated as binary search proceeds.
        float high = chroma;

        // The guess for the current binary search iteration. Starts off at the highest chroma,
        // thus, if a color is possible at the requested chroma, the search can stop after one try.
        float mid = chroma;
        float low = 0.0f;
        boolean isFirstLoop = true;

        Cam answer = null;

        while (Math.abs(low - high) >= CHROMA_SEARCH_ENDPOINT) {
            // Given the current chroma guess, mid, and the desired hue, find J, lightness in
            // CAM16 color space, that creates a color with L* = `lstar` in the L*a*b* color space.
            Cam possibleAnswer = findCamByJ(hue, mid, lstar);

            if (isFirstLoop) {
                if (possibleAnswer != null) {
                    return possibleAnswer.viewed(frame);
                } else {
                    // If this binary search iteration was the first iteration, and this point
                    // has been reached, it means the requested chroma was not available at the
                    // requested hue and L*.
                    // Proceed to a traditional binary search that starts at the midpoint between
                    // the requested chroma and 0.
                    isFirstLoop = false;
                    mid = low + (high - low) / 2.0f;
                    continue;
                }
            }

            if (possibleAnswer == null) {
                // There isn't a CAM16 J that creates a color with L* `lstar`. Try a lower chroma.
                high = mid;
            } else {
                answer = possibleAnswer;
                // It is possible to create a color. Try higher chroma.
                low = mid;
            }

            mid = low + (high - low) / 2.0f;
        }

        // There was no answer: meaning, for the desired hue, there was no chroma low enough to
        // generate a color with the desired L*.
        // All values of L* are possible when there is 0 chroma. Return a color with 0 chroma, i.e.
        // a shade of gray, with the desired L*.
        if (answer == null) {
            return CamUtils.intFromLstar(lstar);
        }

        return answer.viewed(frame);
    }

    // Find J, lightness in CAM16 color space, that creates a color with L* = `lstar` in the L*a*b*
    // color space.
    //
    // Returns null if no J could be found that generated a color with L* `lstar`.
    @Nullable
    private static Cam findCamByJ(float hue, float chroma, float lstar) {
        float low = 0.0f;
        float high = 100.0f;
        float mid = 0.0f;
        float bestdL = 1000.0f;
        float bestdE = 1000.0f;

        Cam bestCam = null;
        while (Math.abs(low - high) > LIGHTNESS_SEARCH_ENDPOINT) {
            mid = low + (high - low) / 2;
            // Create the intended CAM color
            Cam camBeforeClip = Cam.fromJch(mid, chroma, hue);
            // Convert the CAM color to RGB. If the color didn't fit in RGB, during the conversion,
            // the initial RGB values will be outside 0 to 255. The final RGB values are clipped to
            // 0 to 255, distorting the intended color.
            int clipped = camBeforeClip.viewedInSrgb();
            float clippedLstar = CamUtils.lstarFromInt(clipped);
            float dL = Math.abs(lstar - clippedLstar);

            // If the clipped color's L* is within error margin...
            if (dL < DL_MAX) {
                // ...check if the CAM equivalent of the clipped color is far away from intended CAM
                // color. For the intended color, use lightness and chroma from the clipped color,
                // and the intended hue. Callers are wondering what the lightness is, they know
                // chroma may be distorted, so the only concern here is if the hue slipped too far.
                Cam camClipped = Cam.fromInt(clipped);
                float dE = camClipped.distance(
                        Cam.fromJch(camClipped.getJ(), camClipped.getChroma(), hue));
                if (dE <= DE_MAX) {
                    bestdL = dL;
                    bestdE = dE;
                    bestCam = camClipped;
                }
            }

            // If there's no error at all, there's no need to search more.
            //
            // Note: this happens much more frequently than expected, but this is a very delicate
            // property which relies on extremely precise sRGB <=> XYZ calculations, as well as fine
            // tuning of the constants that determine error margins and when the binary search can
            // terminate.
            if (bestdL == 0 && bestdE == 0) {
                break;
            }

            if (clippedLstar < lstar) {
                low = mid;
            } else {
                high = mid;
            }
        }

        return bestCam;
    }

}
