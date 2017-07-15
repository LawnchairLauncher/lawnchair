package ch.deletescape.lawnchair.popup;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import ch.deletescape.lawnchair.LogAccelerateInterpolator;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.util.PillRevealOutlineProvider;

public abstract class PopupItemView extends FrameLayout implements AnimatorUpdateListener {
    protected static final Point sTempPoint = new Point();
    private final Paint mBackgroundClipPaint;
    protected View mIconView;
    protected final boolean mIsRtl;
    private final Matrix mMatrix;
    private float mOpenAnimationProgress;
    protected final Rect mPillRect;
    private Bitmap mRoundedCornerBitmap;

    final class C04861 extends AnimatorListenerAdapter {
        C04861() {
        }

        @Override
        public void onAnimationEnd(Animator animator) {
            PopupItemView.this.mOpenAnimationProgress = 0.0f;
        }
    }

    class CloseInterpolator extends LogAccelerateInterpolator {
        private float mRemainingProgress;
        private float mStartProgress;

        public CloseInterpolator(float f) {
            super(100, 0);
            mStartProgress = 1.0f - f;
            mRemainingProgress = f;
        }

        @Override
        public float getInterpolation(float f) {
            return mStartProgress + (super.getInterpolation(f) * mRemainingProgress);
        }
    }

    class ZoomRevealOutlineProvider extends PillRevealOutlineProvider {
        private final float mArrowCenter;
        private final float mFullHeight;
        private final boolean mPivotLeft;
        private final View mTranslateView;
        private final float mTranslateX;
        private final float mTranslateYMultiplier;
        private final View mZoomView;

        public ZoomRevealOutlineProvider(int i, int i2, Rect rect, PopupItemView popupItemView, View view, boolean z, boolean z2, float f) {
            super(i, i2, rect, popupItemView.getBackgroundRadius());
            mTranslateView = popupItemView;
            mZoomView = view;
            mFullHeight = (float) rect.height();
            mTranslateYMultiplier = z ? 0.5f : -0.5f;
            mPivotLeft = z2;
            mTranslateX = z2 ? f : ((float) rect.right) - f;
            mArrowCenter = f;
        }

        @Override
        public void setProgress(float f) {
            super.setProgress(f);
            if (mZoomView != null) {
                mZoomView.setScaleX(f);
                mZoomView.setScaleY(f);
            }
            mTranslateView.setTranslationY((mFullHeight - ((float) mOutline.height())) * mTranslateYMultiplier);
            float min = Math.min((float) mOutline.width(), mArrowCenter);
            mTranslateView.setTranslationX(mTranslateX - (mPivotLeft ? min + ((float) mOutline.left) : ((float) mOutline.right) - min));
        }
    }

    public abstract int getArrowColor(boolean z);

    public PopupItemView(Context context) {
        this(context, null, 0);
    }

    public PopupItemView(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public PopupItemView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        mBackgroundClipPaint = new Paint(5);
        mMatrix = new Matrix();
        mPillRect = new Rect();
        int backgroundRadius = (int) getBackgroundRadius();
        mRoundedCornerBitmap = Bitmap.createBitmap(backgroundRadius, backgroundRadius, Config.ALPHA_8);
        Canvas canvas = new Canvas();
        canvas.setBitmap(mRoundedCornerBitmap);
        canvas.drawArc(0.0f, 0.0f, (float) (backgroundRadius * 2), (float) (backgroundRadius * 2), 180.0f, 90.0f, true, mBackgroundClipPaint);
        mBackgroundClipPaint.setXfermode(new PorterDuffXfermode(Mode.DST_IN));
        mIsRtl = Utilities.isRtl(getResources());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIconView = findViewById(R.id.popup_item_icon);
    }

    @Override
    protected void onMeasure(int i, int i2) {
        super.onMeasure(i, i2);
        mPillRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int saveLayer = canvas.saveLayer(0.0f, 0.0f, (float) getWidth(), (float) getHeight(), null);
        super.dispatchDraw(canvas);
        int width = mRoundedCornerBitmap.getWidth();
        int height = mRoundedCornerBitmap.getHeight();
        mMatrix.reset();
        canvas.drawBitmap(mRoundedCornerBitmap, mMatrix, mBackgroundClipPaint);
        mMatrix.setRotate(90.0f, (float) (width / 2), (float) (height / 2));
        mMatrix.postTranslate((float) (canvas.getWidth() - width), 0.0f);
        canvas.drawBitmap(mRoundedCornerBitmap, mMatrix, mBackgroundClipPaint);
        mMatrix.setRotate(180.0f, (float) (width / 2), (float) (height / 2));
        mMatrix.postTranslate((float) (canvas.getWidth() - width), (float) (canvas.getHeight() - height));
        canvas.drawBitmap(mRoundedCornerBitmap, mMatrix, mBackgroundClipPaint);
        mMatrix.setRotate(270.0f, (float) (width / 2), (float) (height / 2));
        mMatrix.postTranslate(0.0f, (float) (canvas.getHeight() - height));
        canvas.drawBitmap(mRoundedCornerBitmap, mMatrix, mBackgroundClipPaint);
        canvas.restoreToCount(saveLayer);
    }

    public Animator createOpenAnimation(boolean z, boolean z2) {
        int i;
        Point iconCenter = getIconCenter();
        Resources resources = getResources();
        if (mIsRtl ^ z2) {
            i = R.dimen.popup_arrow_horizontal_center_start;
        } else {
            i = R.dimen.popup_arrow_horizontal_center_end;
        }
        ValueAnimator createRevealAnimator = new ZoomRevealOutlineProvider(iconCenter.x, iconCenter.y, mPillRect, this, mIconView, z, z2, (float) resources.getDimensionPixelSize(i)).createRevealAnimator(this, false);
        mOpenAnimationProgress = 0.0f;
        createRevealAnimator.addUpdateListener(this);
        return createRevealAnimator;
    }

    @Override
    public void onAnimationUpdate(ValueAnimator valueAnimator) {
        mOpenAnimationProgress = valueAnimator.getAnimatedFraction();
    }

    public boolean isOpenOrOpening() {
        return mOpenAnimationProgress > 0.0f;
    }

    public Animator createCloseAnimation(boolean z, boolean z2, long j) {
        int i;
        Point iconCenter = getIconCenter();
        Resources resources = getResources();
        if (mIsRtl ^ z2) {
            i = R.dimen.popup_arrow_horizontal_center_start;
        } else {
            i = R.dimen.popup_arrow_horizontal_center_end;
        }
        Animator createRevealAnimator = new ZoomRevealOutlineProvider(iconCenter.x, iconCenter.y, mPillRect, this, mIconView, z, z2, (float) resources.getDimensionPixelSize(i)).createRevealAnimator(this, true);
        createRevealAnimator.setDuration((long) (((float) j) * mOpenAnimationProgress));
        createRevealAnimator.setInterpolator(new CloseInterpolator(mOpenAnimationProgress));
        createRevealAnimator.addListener(new C04861());
        return createRevealAnimator;
    }

    public Point getIconCenter() {
        sTempPoint.y = getMeasuredHeight() / 2;
        sTempPoint.x = getResources().getDimensionPixelSize(R.dimen.bg_popup_item_height) / 2;
        if (Utilities.isRtl(getResources())) {
            sTempPoint.x = getMeasuredWidth() - sTempPoint.x;
        }
        return sTempPoint;
    }

    protected float getBackgroundRadius() {
        return (float) getResources().getDimensionPixelSize(R.dimen.bg_round_rect_radius);
    }
}