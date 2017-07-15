package ch.deletescape.lawnchair.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.view.ContextThemeWrapper;

public class Themes {
    public static int getColorAccent(Context context) {
        return getAttrColor(context, 16843829);
    }

    public static int getColorPrimary(Context context, int i) {
        return getAttrColor(new ContextThemeWrapper(context, i), 16843827);
    }

    public static int getAttrColor(Context context, int i) {
        TypedArray obtainStyledAttributes = context.obtainStyledAttributes(new int[]{i});
        int color = obtainStyledAttributes.getColor(0, 0);
        obtainStyledAttributes.recycle();
        return color;
    }

    public static int getAlpha(Context context, int i) {
        TypedArray obtainStyledAttributes = context.obtainStyledAttributes(new int[]{i});
        float f = obtainStyledAttributes.getFloat(0, 0.0f);
        obtainStyledAttributes.recycle();
        return (int) ((255.0f * f) + 0.5f);
    }

    public static void setColorScaleOnMatrix(int i, ColorMatrix colorMatrix) {
        colorMatrix.setScale(((float) Color.red(i)) / 255.0f, ((float) Color.green(i)) / 255.0f, ((float) Color.blue(i)) / 255.0f, ((float) Color.alpha(i)) / 255.0f);
    }
}