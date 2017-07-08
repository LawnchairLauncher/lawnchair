package ch.deletescape.lawnchair.blur;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class BlurDrawable extends Drawable implements BlurWallpaperProvider.Listener {

    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint mTransparentPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint mClipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    private final BlurWallpaperProvider mProvider;
    private final float mRadius;
    private final boolean mAllowTransparencyMode;
    private float mTranslation;
    private float mOffset;
    private Bitmap mWallpaper;
    private boolean mShouldDraw = true;
    private float mOverscroll;
    private boolean mUseTransparency;

    public BlurDrawable(BlurWallpaperProvider provider, float radius, boolean allowTransparencyMode) {
        mProvider = provider;
        mRadius = radius;
        mAllowTransparencyMode = allowTransparencyMode;

        if (radius > 0) {
            mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            mTransparentPaint.setColor(0x45FFFFFF);
            mTransparentPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        }

        onWallpaperChanged();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (!mShouldDraw || mWallpaper == null) return;

        mRect.set(0, 0, canvas.getWidth(), canvas.getHeight());
        if (mRadius > 0) {
            canvas.drawRoundRect(mRect, mRadius, mRadius, mClipPaint);
        }

        if (mUseTransparency) {
            canvas.drawRect(mRect, mTransparentPaint);
        } else {
            canvas.drawBitmap(mWallpaper, - mOffset - mOverscroll, -mTranslation, mPaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        mShouldDraw = alpha == 255;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    public void startListening() {
        mProvider.addListener(this);
    }

    public void stopListening() {
        mProvider.removeListener(this);
    }

    @Override
    public void onWallpaperChanged() {
        mWallpaper = mProvider.getWallpaper();
        if (!mUseTransparency)
            invalidateSelf();
    }

    @Override
    public void onOffsetChanged(float offset) {
        mOffset = offset;
        if (!mUseTransparency)
            invalidateSelf();
    }

    @Override
    public void setUseTransparency(boolean useTransparency) {
        if (!mAllowTransparencyMode) return;
        mUseTransparency = useTransparency;
        invalidateSelf();
    }

    public void setTranslation(float translation) {
        mTranslation = translation;
        if (!mUseTransparency)
            invalidateSelf();
    }

    public void setOverscroll(float progress) {
        mOverscroll = progress;
        if (!mUseTransparency)
            invalidateSelf();
    }
}
