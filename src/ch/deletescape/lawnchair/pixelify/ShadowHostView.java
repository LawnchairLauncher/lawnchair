package ch.deletescape.lawnchair.pixelify;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BlurMaskFilter;
import android.graphics.BlurMaskFilter.Blur;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Arrays;

import ch.deletescape.lawnchair.R;

public class ShadowHostView extends FrameLayout {
    private static final int bV = 38;
    private static final int bW = 89;
    private Bitmap bX;
    private final BlurMaskFilter blurMaskFilter;
    private final int bZ;
    private long[] ca;
    private Bitmap cb;
    private final int cc;
    private final Canvas mCanvas;
    private final Paint mPaint;
    private View mView;

    public ShadowHostView(Context context) {
        this(context, null);
    }

    public ShadowHostView(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public ShadowHostView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        mCanvas = new Canvas();
        mPaint = new Paint(3);
        setWillNotDraw(false);
        cc = getResources().getDimensionPixelSize(R.dimen.qsb_shadow_blur_radius);
        bZ = getResources().getDimensionPixelSize(R.dimen.qsb_key_shadow_offset);
        blurMaskFilter = new BlurMaskFilter((float) cc, Blur.NORMAL);
    }

    private boolean applyView(GoogleSearchApp gsa) {
        long[] jArr = new long[]{gsa.gsaUpdateTime, (long) gsa.gsaVersion, (long) gsa.mRemoteViews.getLayoutId()};
        if (mView != null) {
            if (Arrays.equals(ca, jArr)) {
                try {
                    gsa.mRemoteViews.reapply(getContext(), mView);
                    invalidate();
                    return true;
                } catch (Throwable e) {
                    Log.e("ShadowHostView", "View reapply failed", e);
                }
            }
            removeView(mView);
            mView = null;
        }
        try {
            mView = gsa.mRemoteViews.apply(getContext(), this);
            bI(mView);
            ca = jArr;
            addView(mView);
            return true;
        } catch (Throwable e2) {
            Log.e("ShadowHostView", "View apply failed", e2);
            return false;
        }
    }

    private void bI(View view) {
        if (view instanceof TextView) {
            ((TextView) view).setShadowLayer(0.0f, 0.0f, 0.0f, 0);
        } else if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int childCount = viewGroup.getChildCount() - 1; childCount >= 0; childCount--) {
                bI(viewGroup.getChildAt(childCount));
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mView != null && mView.getWidth() > 0 && mView.getHeight() > 0) {
            int width;
            int height;
            float left;
            float top;
            if (bX != null && bX.getHeight() == mView.getHeight()) {
                mCanvas.setBitmap(bX);
                mCanvas.drawColor(0x1000000, Mode.CLEAR);
                mView.draw(mCanvas);
                width = (bX.getWidth() + cc) + cc;
                height = (bX.getHeight() + cc) + cc;
                if (cb != null && cb.getWidth() == width) {
                    mCanvas.setBitmap(cb);
                    mCanvas.drawColor(0x1000000, Mode.CLEAR);
                    mPaint.setMaskFilter(blurMaskFilter);
                    mPaint.setAlpha(100);
                    mCanvas.drawBitmap(bX, (float) cc, (float) cc, mPaint);
                    mCanvas.setBitmap(null);
                    mPaint.setMaskFilter(null);
                    left = (float) (mView.getLeft() - cc);
                    top = (float) (mView.getTop() - cc);
                    mPaint.setAlpha(bV);
                    canvas.drawBitmap(cb, left, top, mPaint);
                    mPaint.setAlpha(bW);
                    canvas.drawBitmap(cb, left, top + ((float) bZ), mPaint);
                }
                cb = Bitmap.createBitmap(width, height, Config.ALPHA_8);
                mCanvas.setBitmap(cb);
                mCanvas.drawColor(0x1000000, Mode.CLEAR);
                mPaint.setMaskFilter(blurMaskFilter);
                mPaint.setAlpha(100);
                mCanvas.drawBitmap(bX, (float) cc, (float) cc, mPaint);
                mCanvas.setBitmap(null);
                mPaint.setMaskFilter(null);
                left = (float) (mView.getLeft() - cc);
                top = (float) (mView.getTop() - cc);
                mPaint.setAlpha(bV);
                canvas.drawBitmap(cb, left, top, mPaint);
                mPaint.setAlpha(bW);
                canvas.drawBitmap(cb, left, top + ((float) bZ), mPaint);
            }
            bX = Bitmap.createBitmap(mView.getWidth(), mView.getHeight(), Config.ALPHA_8);
            mCanvas.setBitmap(bX);
            mCanvas.drawColor(0x1000000, Mode.CLEAR);
            mView.draw(mCanvas);
            width = (bX.getWidth() + cc) + cc;
            height = (bX.getHeight() + cc) + cc;
            if (cb.getHeight() != height) {
                cb = Bitmap.createBitmap(width, height, Config.ALPHA_8);
            }
            mCanvas.setBitmap(cb);
            mCanvas.drawColor(0x1000000, Mode.CLEAR);
            mPaint.setMaskFilter(blurMaskFilter);
            mPaint.setAlpha(100);
            mCanvas.drawBitmap(bX, (float) cc, (float) cc, mPaint);
            mCanvas.setBitmap(null);
            mPaint.setMaskFilter(null);
            left = (float) (mView.getLeft() - cc);
            top = (float) (mView.getTop() - cc);
            mPaint.setAlpha(bV);
            canvas.drawBitmap(cb, left, top, mPaint);
            mPaint.setAlpha(bW);
            canvas.drawBitmap(cb, left, top + ((float) bZ), mPaint);
        }
    }

    public static View bG(GoogleSearchApp gsa, ViewGroup viewGroup, View view) {
        if (gsa == null || gsa.mRemoteViews == null) {
            return null;
        }
        ShadowHostView view2;
        if (view instanceof ShadowHostView) {
            view2 = (ShadowHostView) view;
        } else {
            view2 = (ShadowHostView) LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.shadow_host_view, viewGroup, false);
        }
        if (!view2.applyView(gsa)) {
            view2 = null;
        }
        return view2;
    }
}