package com.android.launcher3.dynamicui;

import android.content.Context;
import android.graphics.Color;
import android.support.v4.graphics.ColorUtils;
import android.util.Pair;

import com.android.launcher3.Utilities;
import com.android.launcher3.compat.WallpaperColorsCompat;
import com.android.launcher3.compat.WallpaperManagerCompat;

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

    private final Context mContext;
    private final ArrayList<OnChangeListener> mListeners = new ArrayList<>();
    private final WallpaperManagerCompat mWallpaperManager;
    private final ColorExtractionAlgorithm mExtractionType;
    private int mMainColor;
    private int mSecondaryColor;
    private boolean mIsDark;
    private boolean mSupportsDarkText;
    private boolean mIsTransparent;
    private OnThemeChangeListener mOnThemeChangeListener;

    private WallpaperColorInfo(Context context) {
        mContext = context;
        mWallpaperManager = WallpaperManagerCompat.getInstance(context);
        mWallpaperManager.addOnColorsChangedListener(this);
        mExtractionType = ColorExtractionAlgorithm.newInstance(context);
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

    public boolean supportsDarkText() {
        return mSupportsDarkText;
    }

    public boolean isTransparent() {
        return mIsTransparent;
    }

    @Override
    public void onColorsChanged(WallpaperColorsCompat colors, int which) {
        if ((which & FLAG_SYSTEM) != 0) {
            boolean wasDarkTheme = mIsDark;
            boolean didSupportDarkText = mSupportsDarkText;
            boolean wasTransparent = mIsTransparent;
            update(colors);
            notifyChange(wasDarkTheme != mIsDark || didSupportDarkText != mSupportsDarkText || wasTransparent != mIsTransparent);
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
        int colorHints = Utilities.getThemeHints(mContext, wallpaperColors == null
                ? 0
                : wallpaperColors.getColorHints());
        mSupportsDarkText = (colorHints & WallpaperColorsCompat.HINT_SUPPORTS_DARK_TEXT) > 0;
        mIsDark = (colorHints & WallpaperColorsCompat.HINT_SUPPORTS_DARK_THEME) > 0;
        mIsTransparent = (colorHints & WallpaperColorsCompat.HINT_SUPPORTS_TRANSPARENCY) > 0;
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
