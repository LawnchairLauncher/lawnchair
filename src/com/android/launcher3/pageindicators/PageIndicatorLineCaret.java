package com.android.launcher3.pageindicators;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.graphics.ColorUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Property;
import android.view.ViewConfiguration;
import android.widget.ImageView;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dynamicui.ExtractedColors;
import com.android.launcher3.dynamicui.WallpaperColorInfo;

/**
 * A PageIndicator that briefly shows a fraction of a line when moving between pages.
 *
 * The fraction is 1 / number of pages and the position is based on the progress of the page scroll.
 */
public class PageIndicatorLineCaret extends PageIndicator {
    private static final String TAG = "PageIndicatorLine";

    private static final int[] sTempCoords = new int[2];

    private static final int LINE_ANIMATE_DURATION = ViewConfiguration.getScrollBarFadeDuration();
    private static final int LINE_FADE_DELAY = ViewConfiguration.getScrollDefaultDelay();
    public static final int WHITE_ALPHA = (int) (0.70f * 255);
    public static final int BLACK_ALPHA = (int) (0.65f * 255);

    private static final int LINE_ALPHA_ANIMATOR_INDEX = 0;
    private static final int NUM_PAGES_ANIMATOR_INDEX = 1;
    private static final int TOTAL_SCROLL_ANIMATOR_INDEX = 2;

    private ValueAnimator[] mAnimators = new ValueAnimator[3];

    private final Handler mDelayedLineFadeHandler = new Handler(Looper.getMainLooper());

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
    private Launcher mLauncher;
    private final int mLineHeight;
    private ImageView mAllAppsHandle;

    private static final Property<PageIndicatorLineCaret, Integer> PAINT_ALPHA
            = new Property<PageIndicatorLineCaret, Integer>(Integer.class, "paint_alpha") {
        @Override
        public Integer get(PageIndicatorLineCaret obj) {
            return obj.mLinePaint.getAlpha();
        }

        @Override
        public void set(PageIndicatorLineCaret obj, Integer alpha) {
            obj.mLinePaint.setAlpha(alpha);
            obj.invalidate();
        }
    };

    private static final Property<PageIndicatorLineCaret, Float> NUM_PAGES
            = new Property<PageIndicatorLineCaret, Float>(Float.class, "num_pages") {
        @Override
        public Float get(PageIndicatorLineCaret obj) {
            return obj.mNumPagesFloat;
        }

        @Override
        public void set(PageIndicatorLineCaret obj, Float numPages) {
            obj.mNumPagesFloat = numPages;
            obj.invalidate();
        }
    };

    private static final Property<PageIndicatorLineCaret, Integer> TOTAL_SCROLL
            = new Property<PageIndicatorLineCaret, Integer>(Integer.class, "total_scroll") {
        @Override
        public Integer get(PageIndicatorLineCaret obj) {
            return obj.mTotalScroll;
        }

        @Override
        public void set(PageIndicatorLineCaret obj, Integer totalScroll) {
            obj.mTotalScroll = totalScroll;
            obj.invalidate();
        }
    };

    private Runnable mHideLineRunnable = new Runnable() {
        @Override
        public void run() {
            animateLineToAlpha(0);
        }
    };

    public PageIndicatorLineCaret(Context context) {
        this(context, null);
    }

    public PageIndicatorLineCaret(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PageIndicatorLineCaret(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        Resources res = context.getResources();
        mLinePaint = new Paint();
        mLinePaint.setAlpha(0);

        mLauncher = Launcher.getLauncher(context);
        mLineHeight = res.getDimensionPixelSize(R.dimen.dynamic_grid_page_indicator_line_height);
        setCaretDrawable(new CaretDrawable(context));

        boolean darkText = WallpaperColorInfo.getInstance(context).supportsDarkText();
        mActiveAlpha = darkText ? BLACK_ALPHA : WHITE_ALPHA;
        mLinePaint.setColor(darkText ? Color.BLACK : Color.WHITE);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAllAppsHandle = (ImageView) findViewById(R.id.all_apps_handle);
        mAllAppsHandle.setImageDrawable(getCaretDrawable());
        mAllAppsHandle.setOnClickListener(mLauncher);
        mAllAppsHandle.setOnFocusChangeListener(mLauncher.mFocusHandler);
        mLauncher.setAllAppsButton(mAllAppsHandle);
    }

    @Override
    public void setAccessibilityDelegate(AccessibilityDelegate delegate) {
        mAllAppsHandle.setAccessibilityDelegate(delegate);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mTotalScroll == 0 || mNumPagesFloat == 0) {
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
    public void setContentDescription(CharSequence contentDescription) {
        mAllAppsHandle.setContentDescription(contentDescription);
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
            invalidate();
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

    /**
     * The line's color will be:
     * - mostly opaque white if the hotseat is white (ignoring alpha)
     * - mostly opaque black if the hotseat is black (ignoring alpha)
     */
    public void updateColor(ExtractedColors extractedColors) {
        if (FeatureFlags.LAUNCHER3_GRADIENT_ALL_APPS) {
            return;
        }
        int originalLineAlpha = mLinePaint.getAlpha();
        int color = extractedColors.getColor(ExtractedColors.HOTSEAT_INDEX);
        if (color != Color.TRANSPARENT) {
            color = ColorUtils.setAlphaComponent(color, 255);
            if (color == Color.BLACK) {
                mActiveAlpha = BLACK_ALPHA;
            } else if (color == Color.WHITE) {
                mActiveAlpha = WHITE_ALPHA;
            } else {
                Log.e(TAG, "Setting workspace page indicators to an unsupported color: #"
                        + Integer.toHexString(color));
            }
            mLinePaint.setColor(color);
            mLinePaint.setAlpha(originalLineAlpha);
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
}
