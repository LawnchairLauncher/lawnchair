package ch.deletescape.lawnchair.blur;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;

public class BlurDrawable extends Drawable implements BlurWallpaperProvider.Listener {

    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint mBlurPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint mOpacityPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mColorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mClipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    private final BlurWallpaperProvider mProvider;
    private final float mRadius;
    private final boolean mAllowTransparencyMode;
    private float mTranslation;
    private float mOffset;
    private boolean mShouldDraw = true;
    private float mOverscroll;
    private boolean mUseTransparency;

    private int mDownsampleFactor;
    private int mOverlayColor;

    private Canvas mClipCanvas = new Canvas();

    private View mBlurredView;
    private int mBlurredViewWidth, mBlurredViewHeight;

    private boolean mDownsampleFactorChanged;
    private Bitmap mBitmapToBlur, mBlurredBitmap;
    private Canvas mBlurringCanvas;
    private RenderScript mRenderScript;
    private ScriptIntrinsicBlur mBlurScript;
    private Allocation mBlurInput, mBlurOutput;
    private Bitmap mTempBitmap;
    private boolean mBlurInvalid;

    private float mBlurredX, mBlurredY;
    private boolean mShouldProvideOutline;
    private int mOpacity = 255;
    private boolean mTransparencyEnabled;

    BlurDrawable(BlurWallpaperProvider provider, float radius, boolean allowTransparencyMode) {
        mProvider = provider;
        mRadius = radius;
        mAllowTransparencyMode = allowTransparencyMode;

        if (radius > 0) {
            mColorPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            mBlurPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        }

        mDownsampleFactor = mProvider.getDownsampleFactor();
        initializeRenderScript(mProvider.getContext());
    }

    public void setBlurredView(View blurredView) {
        mBlurredView = blurredView;
    }

    public void setOverlayColor(int color) {
        if (mOverlayColor != color) {
            mOverlayColor = color;
            mColorPaint.setColor(color);
            invalidateSelf();
        }
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);

        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) return;
        mTempBitmap = Bitmap.createBitmap(width, height,
                Bitmap.Config.ARGB_8888);
        mClipCanvas.setBitmap(mTempBitmap);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Bitmap toDraw = getBitmap();
        if (!mShouldDraw || toDraw == null) return;

        float blurTranslateX = -mOffset - mOverscroll;
        float translateX = -mOverscroll, translateY = -mTranslation;

        Canvas drawTo = mBlurredView == null ? canvas : mClipCanvas;

        mRect.set(0, 0, canvas.getWidth(), canvas.getHeight());
        if (mRadius > 0) {
            drawTo.drawRoundRect(mRect, mRadius, mRadius, mClipPaint);
        }

        if (mTransparencyEnabled) {
            mOpacityPaint.setColor(mOpacity << 24);
            drawTo.drawRect(mRect, mOpacityPaint);
        }

        drawTo.drawBitmap(toDraw, blurTranslateX, translateY - mProvider.getWallpaperYOffset(), mPaint);

        if (prepare()) {
            if (mBlurInvalid) {
                mBlurInvalid = false;
                mBlurredX = mOverscroll;
                mBlurredY = mTranslation;

                long startTime = System.currentTimeMillis();

                mBlurredView.draw(mBlurringCanvas);
                mBlurringCanvas.drawColor(mProvider.getTintColor());
                if (mOverlayColor != 0)
                    mBlurringCanvas.drawColor(mOverlayColor);
                blur();

                mBlurringCanvas = null;
                mBitmapToBlur = null;
                mBlurInput = null;
                mBlurOutput = null;

                Log.d("BlurView", "Took " + (System.currentTimeMillis() - startTime) + "ms to blur");
            }

            mClipCanvas.save();
            mClipCanvas.translate(mBlurredView.getX() + translateX, mBlurredView.getY() + translateY);
            mClipCanvas.scale(mDownsampleFactor, mDownsampleFactor);
            mClipCanvas.drawBitmap(mBlurredBitmap, 0, 0, mBlurPaint);
            mClipCanvas.restore();
        }

        if (mBlurredView != null) {
            canvas.drawBitmap(mTempBitmap, 0, 0, null);
        } else if (mOverlayColor != 0) {
            canvas.drawRect(mRect, mColorPaint);
        }
    }

    private void initializeRenderScript(Context context) {
        mRenderScript = RenderScript.create(context);
        mBlurScript = ScriptIntrinsicBlur.create(mRenderScript, Element.U8_4(mRenderScript));
    }

    private boolean prepare() {
        if (mBlurredView == null) return false;
        if (!mBlurInvalid) return true;

        final int width = mBlurredView.getWidth();
        final int height = mBlurredView.getHeight();

        if (mBlurringCanvas == null || mDownsampleFactorChanged
                || mBlurredViewWidth != width || mBlurredViewHeight != height) {
            mDownsampleFactorChanged = false;

            mBlurredViewWidth = width;
            mBlurredViewHeight = height;

            int scaledWidth = width / mDownsampleFactor;
            int scaledHeight = height / mDownsampleFactor;

            // The following manipulation is to avoid some RenderScript artifacts at the edge.
            scaledWidth = scaledWidth - scaledWidth % 4 + 4;
            scaledHeight = scaledHeight - scaledHeight % 4 + 4;

            if (mBitmapToBlur == null || mBlurredBitmap == null
                    || mBlurredBitmap.getWidth() != scaledWidth
                    || mBlurredBitmap.getHeight() != scaledHeight) {
                mBitmapToBlur = Bitmap.createBitmap(scaledWidth, scaledHeight,
                        Bitmap.Config.ARGB_8888);
                if (mBitmapToBlur == null) {
                    return false;
                }

                mBlurredBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight,
                        Bitmap.Config.ARGB_8888);
                if (mBlurredBitmap == null) {
                    return false;
                }
            }

            mBlurringCanvas = new Canvas(mBitmapToBlur);
            mBlurringCanvas.scale(1f / mDownsampleFactor, 1f / mDownsampleFactor);
            mBlurInput = Allocation.createFromBitmap(mRenderScript, mBitmapToBlur,
                    Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
            mBlurOutput = Allocation.createTyped(mRenderScript, mBlurInput.getType());
        }
        return true;
    }

    protected void blur() {
        mBlurInput.copyFrom(mBitmapToBlur);
        mBlurScript.setInput(mBlurInput);
        mBlurScript.forEach(mBlurOutput);
        mBlurOutput.copyTo(mBlurredBitmap);
    }

    public Bitmap getBitmap() {
        Bitmap wallpaper = mProvider.getWallpaper();
        if (wallpaper == null || (mUseTransparency && mAllowTransparencyMode))
            return mProvider.getPlaceholder();
        else
            return wallpaper;
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
        mBlurScript.setRadius(mProvider.getBlurRadius());
        mBlurInvalid = true;
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

    @Override
    public void getOutline(@NonNull Outline outline) {
        if (mShouldProvideOutline)
            outline.setRoundRect(getBounds(), mRadius);
    }

    public void setShouldProvideOutline(boolean shouldProvideOutline) {
        mShouldProvideOutline = shouldProvideOutline;
    }

    public void setTranslation(float translation) {
        mTranslation = translation;
        invalidateBlur();
        if (!mUseTransparency)
            invalidateSelf();
    }

    public void setOverscroll(float progress) {
        mOverscroll = progress;
        invalidateBlur();
        if (!mUseTransparency)
            invalidateSelf();
    }

    private void invalidateBlur() {
        mBlurInvalid = mOverscroll != mBlurredX || mTranslation != mBlurredY;
    }

    public void setOpacity(int opacity) {
        if (!mTransparencyEnabled) {
            mTransparencyEnabled = true;
            mColorPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
            mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            mBlurPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        }
        mOpacity = opacity;
    }
}
