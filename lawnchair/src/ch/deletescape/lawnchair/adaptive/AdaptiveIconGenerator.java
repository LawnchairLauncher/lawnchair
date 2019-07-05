/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.adaptive;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.graphics.ColorUtils;
import android.util.Log;
import android.util.SparseIntArray;
import ch.deletescape.lawnchair.LawnchairPreferences;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.graphics.ColorExtractor;
import com.android.launcher3.graphics.FixedScaleDrawable;
import com.android.launcher3.graphics.IconNormalizer;
import com.android.launcher3.graphics.LauncherIcons;

// TODO: Make this thing async somehow (maybe using some drawable wrappers?)
public class AdaptiveIconGenerator {

    // Average number of derived colors (based on averages with ~100 icons and performance testing)
    private static final int NUMBER_OF_COLORS_GUESSTIMATION = 45;

    // Found after some experimenting, might be improved with some more testing
    private static final float FULL_BLEED_ICON_SCALE = 1.44f;

    private Context context;
    private Drawable icon;
    private final String identifier;
    private final LawnchairPreferences prefs;

    private final boolean extractColor;
    private final boolean treatWhite;

    private boolean ranLoop;
    private boolean shouldWrap;
    private int backgroundColor = Color.WHITE;
    private boolean isFullBleed;
    private boolean fullBleedChecked;
    private boolean matchesMaskShape;
    private boolean isBackgroundWhite;
    private float scale;
    private int height;
    private float aHeight;
    private int width;
    private float aWidth;
    private Drawable result;

    private AdaptiveIconDrawable tmp;

    public AdaptiveIconGenerator(Context context, @NonNull Drawable icon, String identifier) {
        this.context = context;
        this.icon = icon;
        // useful for caching or dirty hacks
        this.identifier = identifier;
        prefs = Utilities.getLawnchairPrefs(context);
        shouldWrap = prefs.getEnableLegacyTreatment();
        extractColor = shouldWrap && prefs.getColorizedLegacyTreatment();
        treatWhite = extractColor && prefs.getEnableWhiteOnlyTreatment();
    }

    private void loop() {
        if (Utilities.ATLEAST_OREO && shouldWrap) {
            Drawable extractee = icon;
            if (icon instanceof AdaptiveIconDrawable) {
                if (!treatWhite) {
                    onExitLoop();
                    return;
                }
                AdaptiveIconDrawable aid = (AdaptiveIconDrawable) icon;
                // we still check this seperately as this is the only information we need from the background
                if (!ColorExtractor.isSingleColor(aid.getBackground(), Color.WHITE)) {
                    onExitLoop();
                    return;
                }
                isBackgroundWhite = true;
                extractee = aid.getForeground();
            }

            if (extractee == null) {
                Log.e("AdaptiveIconGenerator", "extractee is null, skipping.");
                onExitLoop();
                return;
            }

            LauncherIcons li = LauncherIcons.obtain(context);
            IconNormalizer normalizer = li.getNormalizer();
            li.recycle();

            boolean[] outShape = new boolean[1];
            RectF bounds = new RectF();

            initTmpIfNeeded();
            scale = normalizer.getScale(extractee, bounds, tmp.getIconMask(), outShape, 0xEB);
            matchesMaskShape = outShape[0];

            if (extractee instanceof ColorDrawable) {
                isFullBleed = true;
                fullBleedChecked = true;
            }

            width = extractee.getIntrinsicWidth();
            height = extractee.getIntrinsicHeight();
            aWidth = width * (1 - (bounds.left + bounds.right));
            aHeight = height * (1 - (bounds.top + bounds.bottom));

            // Check if the icon is squareish
            final float ratio = aHeight / aWidth;
            if (ratio < 0.99 || ratio > 1.001) {
                isFullBleed = false;
                fullBleedChecked = true;
            }

            final Bitmap bitmap = Utilities.drawableToBitmap(extractee);
            if (bitmap == null) {
                onExitLoop();
                return;
            }

            if (!bitmap.hasAlpha()) {
                isFullBleed = true;
                fullBleedChecked = true;
            }

            final int size = height * width;
            SparseIntArray rgbScoreHistogram = new SparseIntArray(NUMBER_OF_COLORS_GUESSTIMATION);
            final int[] pixels = new int[size];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            /*
             *   Calculate the number of padding pixels around the actual icon (i)
             *   +----------------+
             *   |      top       |
             *   +---+--------+---+
             *   |   |        |   |
             *   | l |    i   | r |
             *   |   |        |   |
             *   +---+--------+---+
             *   |     bottom     |
             *   +----------------+
             */
            float adjHeight = height - bounds.top - bounds.bottom;
            float l = bounds.left * width * adjHeight;
            float top = bounds.top * height * width;
            float r = bounds.right * width * adjHeight;
            float bottom = bounds.bottom * height * width;
            int addPixels = Math.round(l + top + r + bottom);

            // Any icon with less than 13% transparent pixels (padding excluded) is considered "full-bleed-ish"
            final int maxTransparent = (int) (round(size * .13) + addPixels);

            int highScore = 0;
            int bestRGB = 0;
            int transparentScore = 0;
            for (int pixel : pixels) {
                int alpha = 0xFF & (pixel >> 24);
                if (alpha < 0xDD) {
                    // Drop mostly-transparent pixels.
                    transparentScore++;
                    if (transparentScore > maxTransparent) {
                        isFullBleed = false;
                        fullBleedChecked = true;
                        if (!extractColor) {
                            break;
                        }
                    }
                    continue;
                }
                // Reduce color complexity.
                int rgb = ColorExtractor.posterize(pixel);
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

            // add back the alpha channel
            bestRGB |= 0xff << 24;

            // return early if a mix-in isnt needed
            if (isFullBleed || !fullBleedChecked) {
                // not yet checked = not set to false = has to be full bleed
                isFullBleed = true;
                backgroundColor = bestRGB;
                onExitLoop();
                return;
            }

            if (!extractColor) {
                backgroundColor = Color.WHITE;
                onExitLoop();
                return;
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
            float mixRatio = min(max(1f - (highScore / (float) (size - transparentScore)), .2f), .8f);

            if (singleColor) {
                // Invert ratio for "single color" foreground
                mixRatio = 1f - mixRatio;
            }

            // Vary color mix-in based on lightness and amount of colors
            int fill = (light && !veryLight) || veryDark ? 0xFFFFFFFF : 0xFF333333;
            backgroundColor = ColorUtils.blendARGB(bestRGB, fill, mixRatio);
        }
        onExitLoop();
    }

    private void onExitLoop() {
        ranLoop = true;
        result = genResult();
    }

    private Drawable genResult() {
        if (!Utilities.ATLEAST_OREO || !shouldWrap) {
            return icon;
        }
        if (icon instanceof AdaptiveIconDrawable) {
            if (!treatWhite || !isBackgroundWhite) {
                return icon;
            }
            if (((AdaptiveIconDrawable) icon).getBackground() instanceof ColorDrawable) {
                AdaptiveIconDrawable mutIcon = (AdaptiveIconDrawable) icon.mutate();
                ((ColorDrawable) mutIcon.getBackground()).setColor(backgroundColor);
                return mutIcon;
            }
            return new AdaptiveIconDrawable(new ColorDrawable(backgroundColor), ((AdaptiveIconDrawable) icon).getForeground());
        }
        initTmpIfNeeded();
        ((FixedScaleDrawable) tmp.getForeground()).setDrawable(icon);
        if (matchesMaskShape || isFullBleed) {
            float upScale = max(width / aWidth, height / aHeight);
            ((FixedScaleDrawable) tmp.getForeground()).setScale(FULL_BLEED_ICON_SCALE * upScale);
        } else {
            ((FixedScaleDrawable) tmp.getForeground()).setScale(scale);
        }
        ((ColorDrawable) tmp.getBackground()).setColor(backgroundColor);
        return tmp;
    }

    private void initTmpIfNeeded() {
        if (tmp == null) {
            tmp = (AdaptiveIconDrawable) context.getDrawable(R.drawable.adaptive_icon_drawable_wrapper).mutate();
            tmp.setBounds(0, 0, 1, 1);
        }
    }

    public Drawable getResult() {
        if (!ranLoop) {
            loop();
        }
        return result;
    }
}
