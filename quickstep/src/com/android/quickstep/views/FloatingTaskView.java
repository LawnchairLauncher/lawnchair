package com.android.quickstep.views;

import static com.android.launcher3.anim.Interpolators.ACCEL;
import static com.android.launcher3.anim.Interpolators.DEACCEL_3;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.systemui.shared.system.QuickStepContract.supportsRoundedCornersOnWindows;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

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

import java.util.function.Consumer;

/**
 * Create an instance via
 * {@link #getFloatingTaskView(StatefulActivity, View, Bitmap, Drawable, RectF, Consumer)} to
 * which will have the thumbnail from the provided existing TaskView overlaying the taskview itself.
 *
 * Can then animate the taskview using
 * {@link #addAnimation(PendingAnimation, RectF, Rect, View, boolean)}
 * giving a starting and ending bounds. Currently this is set to use the split placeholder view,
 * but it could be generified.
 *
 * TODO: Figure out how to copy thumbnail data from existing TaskView to this view.
 */
public class FloatingTaskView extends FrameLayout {

    private SplitPlaceholderView mSplitPlaceholderView;
    private RectF mStartingPosition;
    private final StatefulActivity mActivity;
    private final boolean mIsRtl;
    private final Rect mOutline = new Rect();
    private PagedOrientationHandler mOrientationHandler;
    private ImageView mImageView;

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
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mImageView = findViewById(R.id.thumbnail);
        mImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mImageView.setLayerType(LAYER_TYPE_HARDWARE, null);
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
        positionOut.round(mOutline);
        setPivotX(0);
        setPivotY(0);

        // Copy bounds of exiting thumbnail into ImageView
        mImageView.setImageBitmap(thumbnail);
        mImageView.setVisibility(VISIBLE);

        RecentsView recentsView = launcher.getOverviewPanel();
        mOrientationHandler = recentsView.getPagedOrientationHandler();
        mSplitPlaceholderView.setIcon(icon,
                launcher.getDeviceProfile().overviewTaskIconDrawableSizePx);
        mSplitPlaceholderView.getIconView().setRotation(mOrientationHandler.getDegreesRotated());
    }

    /**
     * Configures and returns a an instance of {@link FloatingTaskView} initially matching the
     * appearance of {@code originalView}.
     *
     * @param additionalOffsetter optional, to set additional offsets to the FloatingTaskView
     *                               to account for translations. If {@code null} then the
     *                               translation values from originalView will be used
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

    // TODO(194414938) set correct corner radii
    public void update(RectF position, float progress, float windowRadius) {
        MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();

        float dX = position.left - mStartingPosition.left;
        float dY = position.top - lp.topMargin;

        setTranslationX(dX);
        setTranslationY(dY);

        float scaleX = position.width() / lp.width;
        float scaleY = position.height() / lp.height;
        setScaleX(scaleX);
        setScaleY(scaleY);
        float childScaleX = 1f / scaleX;
        float childScaleY = 1f / scaleY;

        invalidate();
        // TODO(194414938) seems like this scale value could be fine tuned, some stretchiness
        mImageView.setScaleX(1f / scaleX + scaleX * progress);
        mImageView.setScaleY(1f / scaleY + scaleY * progress);
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
            boolean fadeWithThumbnail) {
        final BaseDragLayer dragLayer = mActivity.getDragLayer();
        int[] dragLayerBounds = new int[2];
        dragLayer.getLocationOnScreen(dragLayerBounds);
        SplitOverlayProperties prop = new SplitOverlayProperties(endBounds,
                startingBounds, dragLayerBounds[0], dragLayerBounds[1]);

        ValueAnimator transitionAnimator = ValueAnimator.ofFloat(0, 1);
        animation.add(transitionAnimator);
        long animDuration = animation.getDuration();
        Rect crop = new Rect();
        RectF floatingTaskViewBounds = new RectF();
        final float initialWindowRadius = supportsRoundedCornersOnWindows(getResources())
                ? Math.max(crop.width(), crop.height()) / 2f
                : 0f;

        if (fadeWithThumbnail) {
            animation.addFloat(mSplitPlaceholderView, SplitPlaceholderView.ALPHA_FLOAT,
                    0, 1, ACCEL);
            animation.addFloat(mImageView, LauncherAnimUtils.VIEW_ALPHA,
                    1, 0, DEACCEL_3);
        }

        MultiValueUpdateListener listener = new MultiValueUpdateListener() {
            final FloatProp mWindowRadius = new FloatProp(initialWindowRadius,
                    initialWindowRadius, 0, animDuration, LINEAR);
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

                update(floatingTaskViewBounds, percent, mWindowRadius.value * 1);
            }
        };
        transitionAnimator.addUpdateListener(listener);
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
}
