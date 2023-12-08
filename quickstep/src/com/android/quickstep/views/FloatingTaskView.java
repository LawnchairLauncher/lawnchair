package com.android.quickstep.views;

import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.app.animation.Interpolators.clampToProgress;
import static com.android.launcher3.AbstractFloatingView.TYPE_TASK_MENU;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseActivity;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.util.AnimUtils;
import com.android.quickstep.util.MultiValueUpdateListener;
import com.android.quickstep.util.SplitAnimationTimings;
import com.android.quickstep.util.TaskCornerRadius;
import com.android.systemui.shared.system.QuickStepContract;

/**
 * Create an instance via
 * {@link #getFloatingTaskView(StatefulActivity, View, Bitmap, Drawable, RectF)} to
 * which will have the thumbnail from the provided existing TaskView overlaying the taskview itself.
 *
 * Can then animate the taskview using
 * {@link #addStagingAnimation(PendingAnimation, RectF, Rect, boolean, boolean)} or
 * {@link #addConfirmAnimation(PendingAnimation, RectF, Rect, boolean, boolean)}
 * giving a starting and ending bounds. Currently this is set to use the split placeholder view,
 * but it could be generified.
 *
 * TODO: Figure out how to copy thumbnail data from existing TaskView to this view.
 */
public class FloatingTaskView extends FrameLayout {

    public static final FloatProperty<FloatingTaskView> PRIMARY_TRANSLATE_OFFSCREEN =
            new FloatProperty<FloatingTaskView>("floatingTaskPrimaryTranslateOffscreen") {
        @Override
        public void setValue(FloatingTaskView view, float translation) {
            ((RecentsView) view.mActivity.getOverviewPanel()).getPagedOrientationHandler()
                    .setFloatingTaskPrimaryTranslation(
                            view,
                            translation,
                            view.mActivity.getDeviceProfile()
                    );
        }

        @Override
        public Float get(FloatingTaskView view) {
            return ((RecentsView) view.mActivity.getOverviewPanel())
                    .getPagedOrientationHandler()
                    .getFloatingTaskPrimaryTranslation(
                            view,
                            view.mActivity.getDeviceProfile()
                    );
        }
    };

    private int mSplitHolderSize;
    private FloatingTaskThumbnailView mThumbnailView;
    private SplitPlaceholderView mSplitPlaceholderView;
    private RectF mStartingPosition;
    private final StatefulActivity mActivity;
    private final boolean mIsRtl;
    private final FullscreenDrawParams mFullscreenParams;
    private PagedOrientationHandler mOrientationHandler;
    @SplitConfigurationOptions.StagePosition
    private int mStagePosition;
    private final Rect mTmpRect = new Rect();

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

        mSplitHolderSize = context.getResources().getDimensionPixelSize(
                R.dimen.split_placeholder_icon_size);
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
        mStagePosition = recentsView.getSplitSelectController().getActiveSplitStagePosition();
        mSplitPlaceholderView.setIcon(icon, mSplitHolderSize);
        mSplitPlaceholderView.getIconView().setRotation(mOrientationHandler.getDegreesRotated());
    }

    /**
     * Configures and returns a an instance of {@link FloatingTaskView} initially matching the
     * appearance of {@code originalView}.
     */
    public static FloatingTaskView getFloatingTaskView(StatefulActivity launcher,
            View originalView, @Nullable Bitmap thumbnail, Drawable icon, RectF positionOut) {
        final ViewGroup dragLayer = launcher.getDragLayer();
        final FloatingTaskView floatingView = (FloatingTaskView) launcher.getLayoutInflater()
                .inflate(R.layout.floating_split_select_view, dragLayer, false);

        floatingView.init(launcher, originalView, thumbnail, icon, positionOut);
        // Add this animating view underneath the existing open task menu view (if there is one)
        View openTaskView = AbstractFloatingView.getOpenView(launcher, TYPE_TASK_MENU);
        int openTaskViewIndex = dragLayer.indexOfChild(openTaskView);
        if (openTaskViewIndex == -1) {
            // Add to top if not
            openTaskViewIndex = dragLayer.getChildCount();
        }
        dragLayer.addView(floatingView, openTaskViewIndex - 1);
        return floatingView;
    }

    public void updateInitialPositionForView(View originalView) {
        if (originalView.getContext() instanceof TaskbarActivityContext) {
            // If original View is a button on the Taskbar, find the on-screen bounds and calculate
            // the equivalent bounds in the DragLayer, so we can set the initial position of
            // this FloatingTaskView and start the split animation at the correct spot.
            originalView.getBoundsOnScreen(mTmpRect);
            mStartingPosition.set(mTmpRect);
            int[] dragLayerPositionRelativeToScreen =
                    mActivity.getDragLayer().getLocationOnScreen();
            mStartingPosition.offset(
                    -dragLayerPositionRelativeToScreen[0],
                    -dragLayerPositionRelativeToScreen[1]);
        } else {
            Rect viewBounds = new Rect(0, 0, originalView.getWidth(), originalView.getHeight());
            Utilities.getBoundsForViewInDragLayer(mActivity.getDragLayer(), originalView,
                    viewBounds, false /* ignoreTransform */, null /* recycle */,
                    mStartingPosition);
        }

        final BaseDragLayer.LayoutParams lp = new BaseDragLayer.LayoutParams(
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

    public void setIcon(Drawable drawable) {
        mSplitPlaceholderView.setIcon(drawable, mSplitHolderSize);
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

    /**
     * Animates a FloatingTaskThumbnailView and its overlapping SplitPlaceholderView when a split
     * is staged.
     */
    public void addStagingAnimation(PendingAnimation animation, RectF startingBounds,
            Rect endBounds, boolean fadeWithThumbnail, boolean isStagedTask) {
        boolean isTablet = mActivity.getDeviceProfile().isTablet;
        boolean splittingFromOverview = fadeWithThumbnail;
        SplitAnimationTimings timings;

        if (isTablet && splittingFromOverview) {
            timings = SplitAnimationTimings.TABLET_OVERVIEW_TO_SPLIT;
        } else if (!isTablet && splittingFromOverview) {
            timings = SplitAnimationTimings.PHONE_OVERVIEW_TO_SPLIT;
        } else {
            // Splitting from Home is currently only available on tablets
            timings = SplitAnimationTimings.TABLET_HOME_TO_SPLIT;
        }

        addAnimation(animation, startingBounds, endBounds, fadeWithThumbnail, isStagedTask,
                timings);
    }

    /**
     * Animates the FloatingTaskThumbnailView and SplitPlaceholderView for the two thumbnails
     * when a split is confirmed.
     */
    public void addConfirmAnimation(PendingAnimation animation, RectF startingBounds,
            Rect endBounds, boolean fadeWithThumbnail, boolean isStagedTask) {
        SplitAnimationTimings timings =
                AnimUtils.getDeviceSplitToConfirmTimings(mActivity.getDeviceProfile().isTablet);

        addAnimation(animation, startingBounds, endBounds, fadeWithThumbnail, isStagedTask,
                timings);
    }

    /**
     * Sets up and builds a split staging animation.
     * Called by {@link #addStagingAnimation(PendingAnimation, RectF, Rect, boolean, boolean)} and
     * {@link #addConfirmAnimation(PendingAnimation, RectF, Rect, boolean, boolean)}.
     */
    public void addAnimation(PendingAnimation animation, RectF startingBounds,
            Rect endBounds, boolean fadeWithThumbnail, boolean isStagedTask,
            SplitAnimationTimings timings) {
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
            // This code block runs for the placeholder view during Overview > OverviewSplitSelect
            // and for the selected (secondary) thumbnail during OverviewSplitSelect > Confirmed

            // FloatingTaskThumbnailView: thumbnail fades out to transparent
            animation.setViewAlpha(mThumbnailView, 0, clampToProgress(LINEAR,
                    timings.getPlaceholderFadeInStartOffset(),
                    timings.getPlaceholderFadeInEndOffset()));

            // SplitPlaceholderView: gray background fades in at same time, then new icon fades in
            fadeInSplitPlaceholder(animation, timings);
        } else if (isStagedTask) {
            // This code block runs for the placeholder view during Normal > OverviewSplitSelect
            // and for the placeholder (primary) thumbnail during OverviewSplitSelect > Confirmed

            // Fade in the placeholder view during Normal > OverviewSplitSelect
            if (mSplitPlaceholderView.getAlpha() == 0) {
                mSplitPlaceholderView.getIconView().setAlpha(0);
                fadeInSplitPlaceholder(animation, timings);
            }

            // No-op for placeholder during OverviewSplitSelect > Confirmed, alpha should be set
        }

        MultiValueUpdateListener listener = new MultiValueUpdateListener() {
            // SplitPlaceholderView: rectangle translates and stretches to new position
            final FloatProp mDx = new FloatProp(0, prop.dX, 0, animDuration,
                    clampToProgress(timings.getStagedRectXInterpolator(),
                            timings.getStagedRectSlideStartOffset(),
                            timings.getStagedRectSlideEndOffset()));
            final FloatProp mDy = new FloatProp(0, prop.dY, 0, animDuration,
                    clampToProgress(timings.getStagedRectYInterpolator(),
                            timings.getStagedRectSlideStartOffset(),
                            timings.getStagedRectSlideEndOffset()));
            final FloatProp mTaskViewScaleX = new FloatProp(1f, prop.finalTaskViewScaleX, 0,
                    animDuration, clampToProgress(timings.getStagedRectScaleXInterpolator(),
                    timings.getStagedRectSlideStartOffset(),
                    timings.getStagedRectSlideEndOffset()));
            final FloatProp mTaskViewScaleY = new FloatProp(1f, prop.finalTaskViewScaleY, 0,
                    animDuration, clampToProgress(timings.getStagedRectScaleYInterpolator(),
                    timings.getStagedRectSlideStartOffset(),
                    timings.getStagedRectSlideEndOffset()));
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

    void fadeInSplitPlaceholder(PendingAnimation animation, SplitAnimationTimings timings) {
        animation.setViewAlpha(mSplitPlaceholderView, 1, clampToProgress(LINEAR,
                timings.getPlaceholderFadeInStartOffset(),
                timings.getPlaceholderFadeInEndOffset()));
        animation.setViewAlpha(mSplitPlaceholderView.getIconView(), 1, clampToProgress(LINEAR,
                timings.getPlaceholderIconFadeInStartOffset(),
                timings.getPlaceholderIconFadeInEndOffset()));
    }

    void drawRoundedRect(Canvas canvas, Paint paint) {
        if (mFullscreenParams == null) {
            return;
        }

        canvas.drawRoundRect(0, 0, getMeasuredWidth(), getMeasuredHeight(),
                mFullscreenParams.mCurrentDrawnCornerRadius / mFullscreenParams.mScaleX,
                mFullscreenParams.mCurrentDrawnCornerRadius / mFullscreenParams.mScaleY,
                paint);
    }

    /**
     * When a split is staged, center the icon in the staging area. Accounts for device insets.
     * @param iconView The icon that should be centered.
     * @param onScreenRectCenterX The x-center of the on-screen staging area (most of the Rect is
     *                        offscreen).
     * @param onScreenRectCenterY The y-center of the on-screen staging area (most of the Rect is
     *                        offscreen).
     */
    void centerIconView(IconView iconView, float onScreenRectCenterX, float onScreenRectCenterY) {
        mOrientationHandler.updateSplitIconParams(iconView, onScreenRectCenterX,
                onScreenRectCenterY, mFullscreenParams.mScaleX, mFullscreenParams.mScaleY,
                iconView.getDrawableWidth(), iconView.getDrawableHeight(),
                mActivity.getDeviceProfile(), mStagePosition);
    }

    public int getStagePosition() {
        return mStagePosition;
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
