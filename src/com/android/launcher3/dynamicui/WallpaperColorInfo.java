package com.android.launcher3.dynamicui;

import android.content.Context;
import android.graphics.Color;
import android.support.v4.graphics.ColorUtils;
import android.util.Pair;

import com.android.launcher3.compat.WallpaperColorsCompat;
import com.android.launcher3.compat.WallpaperManagerCompat;
import com.android.launcher3.dynamicui.colorextraction.types.ExtractionType;
import com.android.launcher3.dynamicui.colorextraction.types.Tonal;

import java.util.ArrayList;

import static android.app.WallpaperManager.FLAG_SYSTEM;

public class WallpaperColorInfo implements WallpaperManagerCompat.OnColorsChangedListenerCompat {

    private static final int FALLBACK_COLOR = Color.WHITE;
    private static final Object sInstanceLock = new Object();
    private static WallpaperColorInfo sInstance;

    public static WallpaperColorInfo getInstance(Context context) {
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                sInstance = new WallpaperColorInfo(context.getApplicationContext());
            }
            return sInstance;
        }
    }

    private final ArrayList<OnChangeListener> mListeners = new ArrayList<>();
    private final WallpaperManagerCompat mWallpaperManager;
    private final ExtractionType mExtractionType;
    private int mMainColor;
    private int mSecondaryColor;
    private boolean mIsDark;
    private OnThemeChangeListener mOnThemeChangeListener;

    private WallpaperColorInfo(Context context) {
        mWallpaperManager = WallpaperManagerCompat.getInstance(context);
        mWallpaperManager.addOnColorsChangedListener(this);
        mExtractionType = new Tonal(); // TODO create and use DefaultExtractionLogic
        update(mWallpaperManager.getWallpaperColors(FLAG_SYSTEM));
    }

    public int getMainColor() {
        return mMainColor;
    }

    public int getSecondaryColor() {
        return mSecondaryColor;
    }

    public boolean isDark() {
        return mIsDark;
    }

    @Override
    public void onColorsChanged(WallpaperColorsCompat colors, int which) {
        if (which == FLAG_SYSTEM) {
            boolean wasDarkTheme = mIsDark;
            update(colors);
            notifyChange(wasDarkTheme != mIsDark);
        }
    }

    private void update(WallpaperColorsCompat wallpaperColors) {
        Pair<Integer, Integer> colors = mExtractionType.extractInto(wallpaperColors);
        if (colors != null) {
            mMainColor = colors.first;
            mSecondaryColor = colors.second;
        } else {
            mMainColor = FALLBACK_COLOR;
            mSecondaryColor = FALLBACK_COLOR;
        }
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(mMainColor, hsl);
        mIsDark = hsl[2] < 0.2f;
    }

    public void setOnThemeChangeListener(OnThemeChangeListener onThemeChangeListener) {
        this.mOnThemeChangeListener = onThemeChangeListener;
    }

    public void addOnChangeListener(OnChangeListener listener) {
        mListeners.add(listener);
    }

    public void removeOnChangeListener(OnChangeListener listener) {
        mListeners.remove(listener);
    }

    public void notifyChange(boolean themeChanged) {
        if (themeChanged) {
            if (mOnThemeChangeListener != null) {
                mOnThemeChangeListener.onThemeChanged();
            }
        } else {
            for (OnChangeListener listener : mListeners) {
                listener.onExtractedColorsChanged(this);
            }
        }
    }

    public interface OnChangeListener {
        void onExtractedColorsChanged(WallpaperColorInfo wallpaperColorInfo);
    }

    public interface OnThemeChangeListener {
        void onThemeChanged();
    }
}
