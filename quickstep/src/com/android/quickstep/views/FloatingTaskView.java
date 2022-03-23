package com.android.quickstep.views;

import static com.android.launcher3.anim.Interpolators.ACCEL;
import static com.android.launcher3.anim.Interpolators.DEACCEL_3;
import static com.android.launcher3.anim.Interpolators.LINEAR;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.util.MultiValueUpdateListener;
import com.android.quickstep.util.TaskCornerRadius;
import com.android.systemui.shared.system.QuickStepContract;

/**
 * Create an instance via
 * {@link #getFloatingTaskView(StatefulActivity, View, Bitmap, Drawable, RectF)} to
 * which will have the thumbnail from the provided existing TaskView overlaying the taskview itself.
 *
 * Can then animate the taskview using
 * {@link #addAnimation(PendingAnimation, RectF, Rect, boolean, boolean)}
 * giving a starting and ending bounds. Currently this is set to use the split placeholder view,
 * but it could be generified.
 *
 * TODO: Figure out how to copy thumbnail data from existing TaskView to this view.
 */
public class FloatingTaskView extends FrameLayout {

    private FloatingTaskThumbnailView mThumbnailView;
    private SplitPlaceholderView mSplitPlaceholderView;
    private RectF mStartingPosition;
    private final StatefulActivity mActivity;
    private final boolean mIsRtl;
    private final FullscreenDrawParams mFullscreenParams;
    private PagedOrientationHandler mOrientationHandler;

    public FloatingTaskView(Context context) {
        this(context, null);
    }

    public FloatingTaskView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FloatingTaskView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mActivity = BaseActivity.fromContext(context);
        mIsRtl = Utilities.isRtl(getResources());
        mFullscreenParams = new FullscreenDrawParams(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mThumbnailView = findViewById(R.id.thumbnail);
        mSplitPlaceholderView = findViewById(R.id.split_placeholder);
        mSplitPlaceholderView.setAlpha(0);
    }

    private void init(StatefulActivity launcher, View originalView, @Nullable Bitmap thumbnail,
            Drawable icon, RectF positionOut) {
        mStartingPosition = positionOut;
        updateInitialPositionForView(originalView);
        final InsettableFrameLayout.LayoutParams lp =
                (InsettableFrameLayout.LayoutParams) getLayoutParams();

        mSplitPlaceholderView.setLayoutParams(new FrameLayout.LayoutParams(lp.width, lp.height));
        setPivotX(0);
        setPivotY(0);

        // Copy bounds of exiting thumbnail into ImageView
        mThumbnailView.setThumbnail(thumbnail);

        mThumbnailView.setVisibility(VISIBLE);

        RecentsView recentsView = launcher.getOverviewPanel();
        mOrientationHandler = recentsView.getPagedOrientationHandler();
        mSplitPlaceholderView.setIcon(icon,
                mContext.getResources().getDimensionPixelSize(R.dimen.split_placeholder_icon_size));
        mSplitPlaceholderView.getIconView().setRotation(mOrientationHandler.getDegreesRotated());
    }

    /**
     * Configures and returns a an instance of {@link FloatingTaskView} initially matching the
     * appearance of {@code originalView}.
     */
    public static FloatingTaskView getFloatingTaskView(StatefulActivity launcher,
            View originalView, @Nullable Bitmap thumbnail, Drawable icon, RectF positionOut) {
        final BaseDragLayer dragLayer = launcher.getDragLayer();
        ViewGroup parent = (ViewGroup) dragLayer.getParent();
        final FloatingTaskView floatingView = (FloatingTaskView) launcher.getLayoutInflater()
                .inflate(R.layout.floating_split_select_view, parent, false);

        floatingView.init(launcher, originalView, thumbnail, icon, positionOut);
        parent.addView(floatingView);
        return floatingView;
    }

    public void updateInitialPositionForView(View originalView) {
        Rect viewBounds = new Rect(0, 0, originalView.getWidth(), originalView.getHeight());
        Utilities.getBoundsForViewInDragLayer(mActivity.getDragLayer(), originalView, viewBounds,
                false /* ignoreTransform */, null /* recycle */,
                mStartingPosition);
        final InsettableFrameLayout.LayoutParams lp = new InsettableFrameLayout.LayoutParams(
                Math.round(mStartingPosition.width()),
                Math.round(mStartingPosition.height()));
        initPosition(mStartingPosition, lp);
        setLayoutParams(lp);
    }

    public void update(RectF bounds, float progress) {
        MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();

        float dX = bounds.left - mStartingPosition.left;
        float dY = bounds.top - lp.topMargin;
        float scaleX = bounds.width() / lp.width;
        float scaleY = bounds.height() / lp.height;

        mFullscreenParams.updateParams(bounds, progress, scaleX, scaleY);

        setTranslationX(dX);
        setTranslationY(dY);
        setScaleX(scaleX);
        setScaleY(scaleY);
        mSplitPlaceholderView.invalidate();
        mThumbnailView.invalidate();

        float childScaleX = 1f / scaleX;
        float childScaleY = 1f / scaleY;
        mOrientationHandler.setPrimaryScale(mSplitPlaceholderView.getIconView(), childScaleX);
        mOrientationHandler.setSecondaryScale(mSplitPlaceholderView.getIconView(), childScaleY);
    }

    public void updateOrientationHandler(PagedOrientationHandler orientationHandler) {
        mOrientationHandler = orientationHandler;
        mSplitPlaceholderView.getIconView().setRotation(mOrientationHandler.getDegreesRotated());
    }

    protected void initPosition(RectF pos, InsettableFrameLayout.LayoutParams lp) {
        mStartingPosition.set(pos);
        lp.ignoreInsets = true;
        // Position the floating view exactly on top of the original
        lp.topMargin = Math.round(pos.top);
        if (mIsRtl) {
            lp.setMarginStart(mActivity.getDeviceProfile().widthPx - Math.round(pos.right));
        } else {
            lp.setMarginStart(Math.round(pos.left));
        }

        // Set the properties here already to make sure they are available when running the first
        // animation frame.
        int left = (int) pos.left;
        layout(left, lp.topMargin, left + lp.width, lp.topMargin + lp.height);
    }

    public void addAnimation(PendingAnimation animation, RectF startingBounds, Rect endBounds,
            boolean fadeWithThumbnail, boolean isStagedTask) {
        mFullscreenParams.setIsStagedTask(isStagedTask);
        final BaseDragLayer dragLayer = mActivity.getDragLayer();
        int[] dragLayerBounds = new int[2];
        dragLayer.getLocationOnScreen(dragLayerBounds);
        SplitOverlayProperties prop = new SplitOverlayProperties(endBounds,
                startingBounds, dragLayerBounds[0], dragLayerBounds[1]);

        ValueAnimator transitionAnimator = ValueAnimator.ofFloat(0, 1);
        animation.add(transitionAnimator);
        long animDuration = animation.getDuration();
        RectF floatingTaskViewBounds = new RectF();

        if (fadeWithThumbnail) {
            animation.addFloat(mSplitPlaceholderView, SplitPlaceholderView.ALPHA_FLOAT,
                    0, 1, ACCEL);
            animation.addFloat(mThumbnailView, LauncherAnimUtils.VIEW_ALPHA,
                    1, 0, DEACCEL_3);
        }

        MultiValueUpdateListener listener = new MultiValueUpdateListener() {
            final FloatProp mDx = new FloatProp(0, prop.dX, 0, animDuration, LINEAR);
            final FloatProp mDy = new FloatProp(0, prop.dY, 0, animDuration, LINEAR);
            final FloatProp mTaskViewScaleX = new FloatProp(1f, prop.finalTaskViewScaleX, 0,
                    animDuration, LINEAR);
            final FloatProp mTaskViewScaleY = new FloatProp(1f, prop.finalTaskViewScaleY, 0,
                    animDuration, LINEAR);
            @Override
            public void onUpdate(float percent, boolean initOnly) {
                // Calculate the icon position.
                floatingTaskViewBounds.set(startingBounds);
                floatingTaskViewBounds.offset(mDx.value, mDy.value);
                Utilities.scaleRectFAboutCenter(floatingTaskViewBounds, mTaskViewScaleX.value,
                        mTaskViewScaleY.value);

                update(floatingTaskViewBounds, percent);
            }
        };
        transitionAnimator.addUpdateListener(listener);
    }

    public void drawRoundedRect(Canvas canvas, Paint paint) {
        if (mFullscreenParams == null) {
            return;
        }

        canvas.drawRoundRect(0, 0, getMeasuredWidth(), getMeasuredHeight(),
                mFullscreenParams.mCurrentDrawnCornerRadius / mFullscreenParams.mScaleX,
                mFullscreenParams.mCurrentDrawnCornerRadius / mFullscreenParams.mScaleY,
                paint);
    }

    public float getFullscreenScaleX() {
        return mFullscreenParams.mScaleX;
    }

    public float getFullscreenScaleY() {
        return mFullscreenParams.mScaleY;
    }

    private static class SplitOverlayProperties {

        private final float finalTaskViewScaleX;
        private final float finalTaskViewScaleY;
        private final float dX;
        private final float dY;

        SplitOverlayProperties(Rect endBounds, RectF startTaskViewBounds,
                int dragLayerLeft, int dragLayerTop) {
            float maxScaleX = endBounds.width() / startTaskViewBounds.width();
            float maxScaleY = endBounds.height() / startTaskViewBounds.height();

            finalTaskViewScaleX = maxScaleX;
            finalTaskViewScaleY = maxScaleY;

            // Animate to the center of the window bounds in screen coordinates.
            float centerX = endBounds.centerX() - dragLayerLeft;
            float centerY = endBounds.centerY() - dragLayerTop;

            dX = centerX - startTaskViewBounds.centerX();
            dY = centerY - startTaskViewBounds.centerY();
        }
    }

    public static class FullscreenDrawParams {

        private final float mCornerRadius;
        private final float mWindowCornerRadius;
        public boolean mIsStagedTask;
        public final RectF mBounds = new RectF();
        public float mCurrentDrawnCornerRadius;
        public float mScaleX = 1;
        public float mScaleY = 1;

        public FullscreenDrawParams(Context context) {
            mCornerRadius = TaskCornerRadius.get(context);
            mWindowCornerRadius = QuickStepContract.getWindowCornerRadius(context);

            mCurrentDrawnCornerRadius = mCornerRadius;
        }

        public void updateParams(RectF bounds, float progress, float scaleX, float scaleY) {
            mBounds.set(bounds);
            mScaleX = scaleX;
            mScaleY = scaleY;
            mCurrentDrawnCornerRadius = mIsStagedTask ? mWindowCornerRadius :
                    Utilities.mapRange(progress, mCornerRadius, mWindowCornerRadius);
        }

        public void setIsStagedTask(boolean isStagedTask) {
            mIsStagedTask = isStagedTask;
        }
    }
}
