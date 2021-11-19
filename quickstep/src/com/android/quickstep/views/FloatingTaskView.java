package com.android.quickstep.views;

import static com.android.launcher3.anim.Interpolators.ACCEL;
import static com.android.launcher3.anim.Interpolators.DEACCEL_3;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.systemui.shared.system.QuickStepContract.supportsRoundedCornersOnWindows;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.util.MultiValueUpdateListener;

/**
 * Create an instance via {@link #getFloatingTaskView(StatefulActivity, TaskView, RectF)} to
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
    private final Launcher mLauncher;
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
        mLauncher = Launcher.getLauncher(context);
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

    private void init(StatefulActivity launcher, TaskView originalView, RectF positionOut) {
        mStartingPosition = positionOut;
        updateInitialPositionForView(originalView);
        final InsettableFrameLayout.LayoutParams lp =
                (InsettableFrameLayout.LayoutParams) getLayoutParams();

        mSplitPlaceholderView.setLayoutParams(new FrameLayout.LayoutParams(lp.width, lp.height));
        positionOut.round(mOutline);
        setPivotX(0);
        setPivotY(0);

        // Copy bounds of exiting thumbnail into ImageView
        TaskThumbnailView thumbnail = originalView.getThumbnail();
        mImageView.setImageBitmap(thumbnail.getThumbnail());
        mImageView.setVisibility(VISIBLE);

        mOrientationHandler = originalView.getRecentsView().getPagedOrientationHandler();
        mSplitPlaceholderView.setIconView(originalView.getIconView(),
                launcher.getDeviceProfile().overviewTaskIconDrawableSizePx);
        mSplitPlaceholderView.getIconView().setRotation(mOrientationHandler.getDegreesRotated());
    }

    /**
     * Configures and returns a an instance of {@link FloatingTaskView} initially matching the
     * appearance of {@code originalView}.
     */
    public static FloatingTaskView getFloatingTaskView(StatefulActivity launcher,
            TaskView originalView, RectF positionOut) {
        final BaseDragLayer dragLayer = launcher.getDragLayer();
        ViewGroup parent = (ViewGroup) dragLayer.getParent();
        final FloatingTaskView floatingView = (FloatingTaskView) launcher.getLayoutInflater()
                .inflate(R.layout.floating_split_select_view, parent, false);

        floatingView.init(launcher, originalView, positionOut);
        parent.addView(floatingView);
        return floatingView;
    }

    public void updateInitialPositionForView(TaskView originalView) {
        View thumbnail = originalView.getThumbnail();
        Rect viewBounds = new Rect(0, 0, thumbnail.getWidth(), thumbnail.getHeight());
        Utilities.getBoundsForViewInDragLayer(mLauncher.getDragLayer(), thumbnail, viewBounds,
                true /* ignoreTransform */, null /* recycle */,
                mStartingPosition);
        mStartingPosition.offset(originalView.getTranslationX(), originalView.getTranslationY());
        final InsettableFrameLayout.LayoutParams lp = new InsettableFrameLayout.LayoutParams(
                Math.round(mStartingPosition.width()),
                Math.round(mStartingPosition.height()));
        initPosition(mStartingPosition, lp);
        setLayoutParams(lp);
    }

    // TODO(194414938) set correct corner radii
    public void update(RectF position, float progress, float windowRadius) {
        MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();

        float dX = mIsRtl
                ? position.left - (lp.getMarginStart() - lp.width)
                : position.left - lp.getMarginStart();
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

    protected void initPosition(RectF pos, InsettableFrameLayout.LayoutParams lp) {
        mStartingPosition.set(pos);
        lp.ignoreInsets = true;
        // Position the floating view exactly on top of the original
        lp.topMargin = Math.round(pos.top);
        if (mIsRtl) {
            lp.setMarginStart(Math.round(mLauncher.getDeviceProfile().widthPx - pos.right));
        } else {
            lp.setMarginStart(Math.round(pos.left));
        }
        // Set the properties here already to make sure they are available when running the first
        // animation frame.
        int left = mIsRtl
                ? mLauncher.getDeviceProfile().widthPx - lp.getMarginStart() - lp.width
                : lp.leftMargin;
        layout(left, lp.topMargin, left + lp.width, lp.topMargin + lp.height);
    }

    public void addAnimation(PendingAnimation animation, RectF startingBounds, Rect endBounds,
            View viewToCover, boolean fadeWithThumbnail) {
        final BaseDragLayer dragLayer = mLauncher.getDragLayer();
        int[] dragLayerBounds = new int[2];
        dragLayer.getLocationOnScreen(dragLayerBounds);
        SplitOverlayProperties prop = new SplitOverlayProperties(endBounds,
                startingBounds, viewToCover, dragLayerBounds[0],
                dragLayerBounds[1]);

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
            final FloatProp mTaskViewScaleX = new FloatProp(prop.initialTaskViewScaleX,
                    prop.finalTaskViewScaleX, 0, animDuration, LINEAR);
            final FloatProp mTaskViewScaleY = new FloatProp(prop.initialTaskViewScaleY,
                    prop.finalTaskViewScaleY, 0, animDuration, LINEAR);
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

        private final float initialTaskViewScaleX;
        private final float initialTaskViewScaleY;
        private final float finalTaskViewScaleX;
        private final float finalTaskViewScaleY;
        private final float dX;
        private final float dY;

        SplitOverlayProperties(Rect endBounds, RectF startTaskViewBounds, View view,
                int dragLayerLeft, int dragLayerTop) {
            float maxScaleX = endBounds.width() / startTaskViewBounds.width();
            float maxScaleY = endBounds.height() / startTaskViewBounds.height();

            initialTaskViewScaleX = view.getScaleX();
            initialTaskViewScaleY = view.getScaleY();
            finalTaskViewScaleX = maxScaleX;
            finalTaskViewScaleY = maxScaleY;

            // Animate the app icon to the center of the window bounds in screen coordinates.
            float centerX = endBounds.centerX() - dragLayerLeft;
            float centerY = endBounds.centerY() - dragLayerTop;

            dX = centerX - startTaskViewBounds.centerX();
            dY = centerY - startTaskViewBounds.centerY();
        }
    }
}
