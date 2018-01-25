package com.android.launcher3.pageindicators;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import static com.android.launcher3.LauncherState.ALL_APPS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Property;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dynamicui.WallpaperColorInfo;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ControlType;

/**
 * A PageIndicator that briefly shows a fraction of a line when moving between pages in
 * portrait mode. In Landscape simply draws the caret drawable bottom-corner aligned in
 * the drag-layer.
 *
 * The fraction is 1 / number of pages and the position is based on the progress of the page scroll.
 */
public class WorkspacePageIndicator extends PageIndicator implements Insettable, OnClickListener {

    private static final int LINE_ANIMATE_DURATION = ViewConfiguration.getScrollBarFadeDuration();
    private static final int LINE_FADE_DELAY = ViewConfiguration.getScrollDefaultDelay();
    public static final int WHITE_ALPHA = (int) (0.70f * 255);
    public static final int BLACK_ALPHA = (int) (0.65f * 255);

    private static final int LINE_ALPHA_ANIMATOR_INDEX = 0;
    private static final int NUM_PAGES_ANIMATOR_INDEX = 1;
    private static final int TOTAL_SCROLL_ANIMATOR_INDEX = 2;

    private ValueAnimator[] mAnimators = new ValueAnimator[3];

    private final Handler mDelayedLineFadeHandler = new Handler(Looper.getMainLooper());
    private final Launcher mLauncher;
    private final AccessibilityManager mAccessibilityManager;

    private boolean mShouldAutoHide = true;

    // The alpha of the line when it is showing.
    private int mActiveAlpha = 0;
    // The alpha that the line is being animated to or already at (either 0 or mActiveAlpha).
    private int mToAlpha;
    // A float value representing the number of pages, to allow for an animation when it changes.
    private float mNumPagesFloat;
    private int mCurrentScroll;
    private int mTotalScroll;
    private Paint mLinePaint;
    private final int mLineHeight;

    private boolean mIsLandscapeUi;

    private static final Property<WorkspacePageIndicator, Integer> PAINT_ALPHA
            = new Property<WorkspacePageIndicator, Integer>(Integer.class, "paint_alpha") {
        @Override
        public Integer get(WorkspacePageIndicator obj) {
            return obj.mLinePaint.getAlpha();
        }

        @Override
        public void set(WorkspacePageIndicator obj, Integer alpha) {
            obj.mLinePaint.setAlpha(alpha);
            obj.invalidateIfPortrait();
        }
    };

    private static final Property<WorkspacePageIndicator, Float> NUM_PAGES
            = new Property<WorkspacePageIndicator, Float>(Float.class, "num_pages") {
        @Override
        public Float get(WorkspacePageIndicator obj) {
            return obj.mNumPagesFloat;
        }

        @Override
        public void set(WorkspacePageIndicator obj, Float numPages) {
            obj.mNumPagesFloat = numPages;
            obj.invalidateIfPortrait();
        }
    };

    private static final Property<WorkspacePageIndicator, Integer> TOTAL_SCROLL
            = new Property<WorkspacePageIndicator, Integer>(Integer.class, "total_scroll") {
        @Override
        public Integer get(WorkspacePageIndicator obj) {
            return obj.mTotalScroll;
        }

        @Override
        public void set(WorkspacePageIndicator obj, Integer totalScroll) {
            obj.mTotalScroll = totalScroll;
            obj.invalidateIfPortrait();
        }
    };

    private Runnable mHideLineRunnable = () -> animateLineToAlpha(0);

    public WorkspacePageIndicator(Context context) {
        this(context, null);
    }

    public WorkspacePageIndicator(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WorkspacePageIndicator(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        Resources res = context.getResources();
        mLinePaint = new Paint();
        mLinePaint.setAlpha(0);

        mLauncher = Launcher.getLauncher(context);
        mLineHeight = res.getDimensionPixelSize(R.dimen.dynamic_grid_page_indicator_line_height);

        boolean darkText = WallpaperColorInfo.getInstance(context).supportsDarkText();
        mActiveAlpha = darkText ? BLACK_ALPHA : WHITE_ALPHA;
        mLinePaint.setColor(darkText ? Color.BLACK : Color.WHITE);
        mAccessibilityManager = (AccessibilityManager)
                getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mTotalScroll == 0 || mNumPagesFloat == 0 || mIsLandscapeUi) {
            return;
        }

        // Compute and draw line rect.
        float progress = Utilities.boundToRange(((float) mCurrentScroll) / mTotalScroll, 0f, 1f);
        int availableWidth = canvas.getWidth();
        int lineWidth = (int) (availableWidth / mNumPagesFloat);
        int lineLeft = (int) (progress * (availableWidth - lineWidth));
        int lineRight = lineLeft + lineWidth;
        canvas.drawRect(lineLeft, canvas.getHeight() - mLineHeight, lineRight, canvas.getHeight(),
                mLinePaint);
    }

    @Override
    public void setScroll(int currentScroll, int totalScroll) {
        if (getAlpha() == 0) {
            return;
        }
        animateLineToAlpha(mActiveAlpha);

        mCurrentScroll = currentScroll;
        if (mTotalScroll == 0) {
            mTotalScroll = totalScroll;
        } else if (mTotalScroll != totalScroll) {
            animateToTotalScroll(totalScroll);
        } else {
            invalidateIfPortrait();
        }

        if (mShouldAutoHide) {
            hideAfterDelay();
        }
    }

    private void hideAfterDelay() {
        mDelayedLineFadeHandler.removeCallbacksAndMessages(null);
        mDelayedLineFadeHandler.postDelayed(mHideLineRunnable, LINE_FADE_DELAY);
    }

    @Override
    public void setActiveMarker(int activePage) {
    }

    @Override
    protected void onPageCountChanged() {
        if (Float.compare(mNumPages, mNumPagesFloat) != 0) {
            animateToNumPages(mNumPages);
        }
    }

    public void setShouldAutoHide(boolean shouldAutoHide) {
        mShouldAutoHide = shouldAutoHide;
        if (shouldAutoHide && mLinePaint.getAlpha() > 0) {
            hideAfterDelay();
        } else if (!shouldAutoHide) {
            mDelayedLineFadeHandler.removeCallbacksAndMessages(null);
        }
    }

    private void animateLineToAlpha(int alpha) {
        if (alpha == mToAlpha) {
            // Ignore the new animation if it is going to the same alpha as the current animation.
            return;
        }
        mToAlpha = alpha;
        setupAndRunAnimation(ObjectAnimator.ofInt(this, PAINT_ALPHA, alpha),
                LINE_ALPHA_ANIMATOR_INDEX);
    }

    private void animateToNumPages(int numPages) {
        setupAndRunAnimation(ObjectAnimator.ofFloat(this, NUM_PAGES, numPages),
                NUM_PAGES_ANIMATOR_INDEX);
    }

    private void animateToTotalScroll(int totalScroll) {
        setupAndRunAnimation(ObjectAnimator.ofInt(this, TOTAL_SCROLL, totalScroll),
                TOTAL_SCROLL_ANIMATOR_INDEX);
    }

    /**
     * Starts the given animator and stores it in the provided index in {@link #mAnimators} until
     * the animation ends.
     *
     * If an animator is already at the index (i.e. it is already playing), it is canceled and
     * replaced with the new animator.
     */
    private void setupAndRunAnimation(ValueAnimator animator, final int animatorIndex) {
        if (mAnimators[animatorIndex] != null) {
            mAnimators[animatorIndex].cancel();
        }
        mAnimators[animatorIndex] = animator;
        mAnimators[animatorIndex].addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mAnimators[animatorIndex] = null;
            }
        });
        mAnimators[animatorIndex].setDuration(LINE_ANIMATE_DURATION);
        mAnimators[animatorIndex].start();
    }

    @Override
    public void setInsets(Rect insets) {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        mIsLandscapeUi = grid.isVerticalBarLayout();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();

        if (mIsLandscapeUi) {
            if (insets.left > insets.right) {
                lp.leftMargin = grid.hotseatBarSidePaddingPx;
                lp.rightMargin = insets.right;
                lp.gravity =  Gravity.RIGHT | Gravity.BOTTOM;
            } else {
                lp.leftMargin = insets.left;
                lp.rightMargin = grid.hotseatBarSidePaddingPx;
                lp.gravity = Gravity.LEFT | Gravity.BOTTOM;
            }
            lp.bottomMargin = grid.workspacePadding.bottom;
            lp.width = lp.height = getResources()
                    .getDimensionPixelSize(R.dimen.dynamic_grid_min_page_indicator_size);

            setBackgroundResource(R.drawable.all_apps_handle_landscape);
            setOnFocusChangeListener(mLauncher.mFocusHandler);
            setOnClickListener(this);

        } else {
            lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
            lp.height = grid.pageIndicatorSizePx;
            lp.bottomMargin = grid.hotseatBarSizePx + insets.bottom;
            lp.width = MATCH_PARENT;

            setBackgroundResource(0);
            setOnFocusChangeListener(null);
            setOnClickListener(mAccessibilityManager.isTouchExplorationEnabled() ? this : null);
        }

        setLayoutParams(lp);
    }

    private void invalidateIfPortrait() {
        if (!mIsLandscapeUi) {
            invalidate();
        }
    }

    @Override
    public void onClick(View view) {
        if (!mLauncher.isInState(ALL_APPS)) {
            mLauncher.getUserEventDispatcher().logActionOnControl(
                    Action.Touch.TAP, ControlType.ALL_APPS_BUTTON);
            mLauncher.getStateManager().goToState(ALL_APPS);
        }
    }
}
