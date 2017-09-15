package ch.deletescape.lawnchair.graphics;

import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.support.v4.graphics.ColorUtils;

import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.util.Themes;

public class IconPalette {
    public final int backgroundColor;
    public final ColorMatrixColorFilter backgroundColorMatrixFilter;
    public final int dominantColor;
    public final ColorMatrixColorFilter saturatedBackgroundColorMatrixFilter;
    public final int secondaryColor;
    public final int textColor;

    private IconPalette(int i) {
        dominantColor = i;
        backgroundColor = dominantColor;
        ColorMatrix colorMatrix = new ColorMatrix();
        Themes.setColorScaleOnMatrix(backgroundColor, colorMatrix);
        backgroundColorMatrixFilter = new ColorMatrixColorFilter(colorMatrix);
        Themes.setColorScaleOnMatrix(getMutedColor(dominantColor, 0.54f), colorMatrix);
        saturatedBackgroundColorMatrixFilter = new ColorMatrixColorFilter(colorMatrix);
        textColor = getTextColorForBackground(backgroundColor);
        secondaryColor = getLowContrastColor(backgroundColor);
    }

    public int getPreloadProgressColor(Context context) {
        float[] fArr = new float[3];
        Color.colorToHSV(dominantColor, fArr);
        if (fArr[1] < 0.2f) {
            return Themes.getColorAccent(context);
        }
        fArr[2] = Math.max(0.6f, fArr[2]);
        return Color.HSVToColor(fArr);
    }

    public static IconPalette fromDominantColor(int i) {
        return new IconPalette(i);
    }

    public static int resolveContrastColor(Context context, int i, int i2) {
        return ensureTextContrast(resolveColor(context, i), i2);
    }

    private static int resolveColor(Context context, int i) {
        if (i == 0) {
            return context.getResources().getColor(R.color.notification_icon_default_color);
        }
        return i;
    }

    private static int ensureTextContrast(int i, int i2) {
        return findContrastColor(i, i2, true, 4.5d);
    }

    private static int findContrastColor(int i, int i2, boolean z, double d) {
        int i3;
        if (z) {
            i3 = i;
        } else {
            i3 = i2;
        }
        if (!z) {
            i2 = i;
        }
        if (ColorUtils.calculateContrast(i3, i2) >= d) {
            return i;
        }
        int i4;
        double[] dArr = new double[3];
        if (z) {
            i4 = i3;
        } else {
            i4 = i2;
        }
        ColorUtils.colorToLAB(i4, dArr);
        double d2 = 0.0d;
        double d3 = dArr[0];
        double d4 = dArr[1];
        double d5 = dArr[2];
        int i5 = 0;
        int i6 = i2;
        int i7 = i3;
        while (i5 < 15 && d3 - d2 > 1.0E-5d) {
            double d6 = (d2 + d3) / 2.0d;
            if (z) {
                i7 = ColorUtils.LABToColor(d6, d4, d5);
            } else {
                i6 = ColorUtils.LABToColor(d6, d4, d5);
            }
            if (ColorUtils.calculateContrast(i7, i6) > d) {
                double d7 = d3;
                d3 = d6;
                d6 = d7;
            } else {
                d3 = d2;
            }
            i5++;
            d2 = d3;
            d3 = d6;
        }
        return ColorUtils.LABToColor(d2, d4, d5);
    }

    private static int getMutedColor(int i, float f) {
        return ColorUtils.compositeColors(ColorUtils.setAlphaComponent(-1, (int) (255.0f * f)), i);
    }

    private static int getTextColorForBackground(int i) {
        return getLighterOrDarkerVersionOfColor(i, 4.5f);
    }

    private static int getLowContrastColor(int i) {
        return getLighterOrDarkerVersionOfColor(i, 1.5f);
    }

    private static int getLighterOrDarkerVersionOfColor(int i, float f) {
        int i2 = -1;
        int arf = ColorUtils.calculateMinimumAlpha(-1, i, f);
        int arf2 = ColorUtils.calculateMinimumAlpha(0x1000000, i, f);
        if (arf >= 0) {
            i2 = ColorUtils.setAlphaComponent(-1, arf);
        } else if (arf2 >= 0) {
            i2 = ColorUtils.setAlphaComponent(0x1000000, arf2);
        }
        return ColorUtils.compositeColors(i2, i);
    }
}