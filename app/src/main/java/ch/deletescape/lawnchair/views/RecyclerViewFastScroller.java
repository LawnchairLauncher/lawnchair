package ch.deletescape.lawnchair.views;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Property;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.TextView;

import ch.deletescape.lawnchair.BaseRecyclerView;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.graphics.FastScrollThumbDrawable;
import ch.deletescape.lawnchair.util.Themes;

public class RecyclerViewFastScroller extends View {
    private static final Property TRACK_WIDTH = new AnonymousClass1(Integer.class, "width");
    private final boolean mCanThumbDetach;
    private final ViewConfiguration mConfig;
    private final float mDeltaThreshold;
    private int mDownX;
    private int mDownY;
    private int mDy;
    private boolean mIgnoreDragGesture;
    private boolean mIsDragging;
    private boolean mIsThumbDetached;
    private float mLastTouchY;
    private int mLastY;
    private final int mMaxWidth;
    private final int mMinWidth;
    private String mPopupSectionName;
    private TextView mPopupView;
    private boolean mPopupVisible;
    protected BaseRecyclerView mRv;
    protected final int mThumbHeight;
    protected int mThumbOffsetY;
    private final int mThumbPadding;
    private final Paint mThumbPaint;
    protected int mTouchOffsetY;
    private final Paint mTrackPaint;
    private int mWidth;
    private ObjectAnimator mWidthAnimator;

    static final class AnonymousClass1 extends Property<RecyclerViewFastScroller, Integer> {
        AnonymousClass1(Class cls, String str) {
            super(cls, str);
        }

        public Integer get(RecyclerViewFastScroller recyclerViewFastScroller) {
            return recyclerViewFastScroller.mWidth;
        }

        public void set(RecyclerViewFastScroller recyclerViewFastScroller, Integer num) {
            recyclerViewFastScroller.setTrackWidth(num.intValue());
        }
    }

    public RecyclerViewFastScroller(Context context) {
        this(context, null);
    }

    public RecyclerViewFastScroller(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public RecyclerViewFastScroller(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        this.mDy = 0;
        this.mTrackPaint = new Paint();
        this.mTrackPaint.setColor(Themes.getAttrColor(context, 16842806));
        this.mTrackPaint.setAlpha(30);
        this.mThumbPaint = new Paint();
        this.mThumbPaint.setAntiAlias(true);
        this.mThumbPaint.setColor(Themes.getColorAccent(context));
        this.mThumbPaint.setStyle(Style.FILL);
        Resources resources = getResources();
        int dimensionPixelSize = resources.getDimensionPixelSize(R.dimen.fastscroll_track_min_width);
        this.mMinWidth = dimensionPixelSize;
        this.mWidth = dimensionPixelSize;
        this.mMaxWidth = resources.getDimensionPixelSize(R.dimen.fastscroll_track_max_width);
        this.mThumbPadding = resources.getDimensionPixelSize(R.dimen.fastscroll_thumb_padding);
        this.mThumbHeight = resources.getDimensionPixelSize(R.dimen.fastscroll_thumb_height);
        this.mConfig = ViewConfiguration.get(context);
        this.mDeltaThreshold = resources.getDisplayMetrics().density * 4.0f;
        TypedArray obtainStyledAttributes = context.obtainStyledAttributes(attributeSet, R.styleable.RecyclerViewFastScroller, i, 0);
        this.mCanThumbDetach = obtainStyledAttributes.getBoolean(0, false);
        obtainStyledAttributes.recycle();
    }

    public void setRecyclerView(BaseRecyclerView baseRecyclerView, TextView textView) {
        this.mRv = baseRecyclerView;
        this.mRv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            public void onScrolled(RecyclerView recyclerView, int i, int i2) {
                RecyclerViewFastScroller.this.mDy = i2;
                RecyclerViewFastScroller.this.mRv.onUpdateScrollbar(i2);
            }
        });
        this.mPopupView = textView;
        this.mPopupView.setBackground(new FastScrollThumbDrawable(this.mThumbPaint, Utilities.isRtl(getResources())));
    }

    public void reattachThumbToScroll() {
        this.mIsThumbDetached = false;
    }

    public void setThumbOffsetY(int i) {
        if (this.mThumbOffsetY != i) {
            this.mThumbOffsetY = i;
            invalidate();
        }
    }

    public int getThumbOffsetY() {
        return this.mThumbOffsetY;
    }

    private void setTrackWidth(int i) {
        if (this.mWidth != i) {
            this.mWidth = i;
            invalidate();
        }
    }

    public int getThumbHeight() {
        return this.mThumbHeight;
    }

    public boolean isDraggingThumb() {
        return this.mIsDragging;
    }

    public boolean isThumbDetached() {
        return this.mIsThumbDetached;
    }

    public boolean handleTouchEvent(MotionEvent motionEvent) {
        boolean i = false;
        int x = (int) motionEvent.getX();
        int y = (int) motionEvent.getY();
        switch (motionEvent.getAction()) {
            case 0:
                this.mDownX = x;
                this.mLastY = y;
                this.mDownY = y;
                if (((float) Math.abs(this.mDy)) < this.mDeltaThreshold && this.mRv.getScrollState() != 0) {
                    this.mRv.stopScroll();
                }
                if (!isNearThumb(x, y)) {
                    if (this.mRv.supportsFastScrolling() && isNearScrollBar(this.mDownX)) {
                        calcTouchOffsetAndPrepToFastScroll(this.mDownY, this.mLastY);
                        updateFastScrollSectionNameAndThumbOffset(this.mLastY, y);
                        break;
                    }
                }
                this.mTouchOffsetY = this.mDownY - this.mThumbOffsetY;
                break;
            case 1:
            case 3:
                this.mRv.onFastScrollCompleted();
                this.mTouchOffsetY = 0;
                this.mLastTouchY = 0.0f;
                this.mIgnoreDragGesture = false;
                if (this.mIsDragging) {
                    this.mIsDragging = false;
                    animatePopupVisibility(false);
                    showActiveScrollbar(false);
                    break;
                }
                break;
            case 2:
                this.mLastY = y;
                boolean z = this.mIgnoreDragGesture;
                if (Math.abs(y - this.mDownY) > this.mConfig.getScaledPagingTouchSlop()) {
                    i = true;
                }
                this.mIgnoreDragGesture = i | z;
                if (!this.mIsDragging && (!this.mIgnoreDragGesture) && this.mRv.supportsFastScrolling() && isNearThumb(this.mDownX, this.mLastY) && Math.abs(y - this.mDownY) > this.mConfig.getScaledTouchSlop()) {
                    calcTouchOffsetAndPrepToFastScroll(this.mDownY, this.mLastY);
                }
                if (this.mIsDragging) {
                    updateFastScrollSectionNameAndThumbOffset(this.mLastY, y);
                    break;
                }
                break;
        }
        return this.mIsDragging;
    }

    private void calcTouchOffsetAndPrepToFastScroll(int i, int i2) {
        this.mRv.getParent().requestDisallowInterceptTouchEvent(true);
        this.mIsDragging = true;
        if (this.mCanThumbDetach) {
            this.mIsThumbDetached = true;
        }
        this.mTouchOffsetY += i2 - i;
        animatePopupVisibility(true);
        showActiveScrollbar(true);
    }

    private void updateFastScrollSectionNameAndThumbOffset(int i, int i2) {
        int scrollbarTrackHeight = this.mRv.getScrollbarTrackHeight() - this.mThumbHeight;
        float max = (float) Math.max(0, Math.min(scrollbarTrackHeight, i2 - this.mTouchOffsetY));
        String scrollToPositionAtProgress = this.mRv.scrollToPositionAtProgress(max / ((float) scrollbarTrackHeight));
        if (!scrollToPositionAtProgress.equals(this.mPopupSectionName)) {
            this.mPopupSectionName = scrollToPositionAtProgress;
            this.mPopupView.setText(scrollToPositionAtProgress);
        }
        animatePopupVisibility(!scrollToPositionAtProgress.isEmpty());
        updatePopupY(i);
        this.mLastTouchY = max;
        setThumbOffsetY((int) this.mLastTouchY);
    }

    public void onDraw(Canvas canvas) {
        if (this.mThumbOffsetY >= 0) {
            int save = canvas.save(1);
            canvas.translate((float) (getWidth() / 2), (float) this.mRv.getPaddingTop());
            float f = (float) (this.mWidth / 2);
            canvas.drawRoundRect(-f, 0.0f, f, (float) this.mRv.getScrollbarTrackHeight(), (float) this.mWidth, (float) this.mWidth, this.mTrackPaint);
            canvas.translate(0.0f, (float) this.mThumbOffsetY);
            f += (float) this.mThumbPadding;
            float f2 = (float) ((this.mWidth + this.mThumbPadding) + this.mThumbPadding);
            canvas.drawRoundRect(-f, 0.0f, f, (float) this.mThumbHeight, f2, f2, this.mThumbPaint);
            canvas.restoreToCount(save);
        }
    }

    private void showActiveScrollbar(boolean z) {
        if (this.mWidthAnimator != null) {
            this.mWidthAnimator.cancel();
        }
        Property property = TRACK_WIDTH;
        int[] iArr = new int[1];
        iArr[0] = z ? this.mMaxWidth : this.mMinWidth;
        this.mWidthAnimator = ObjectAnimator.ofInt(this, property, iArr);
        this.mWidthAnimator.setDuration(150);
        this.mWidthAnimator.start();
    }

    private boolean isNearThumb(int i, int i2) {
        int paddingTop = (i2 - this.mRv.getPaddingTop()) - this.mThumbOffsetY;
        if (i < 0 || i >= getWidth() || paddingTop < 0 || paddingTop > this.mThumbHeight) {
            return false;
        }
        return true;
    }

    public boolean shouldBlockIntercept(int i, int i2) {
        return isNearThumb(i, i2);
    }

    public boolean isNearScrollBar(int i) {
        return i >= (getWidth() - this.mMaxWidth) / 2 && i <= (getWidth() + this.mMaxWidth) / 2;
    }

    private void animatePopupVisibility(boolean z) {
        if (this.mPopupVisible != z) {
            this.mPopupVisible = z;
            this.mPopupView.animate().cancel();
            this.mPopupView.animate().alpha(z ? 1.0f : 0.0f).setDuration((long) (z ? 200 : 150)).start();
        }
    }

    private void updatePopupY(int i) {
        int height = this.mPopupView.getHeight();
        this.mPopupView.setTranslationY(Utilities.boundToRange((((float) i) - (((float) height) * 0.75f)) + ((float) this.mRv.getPaddingTop()), (float) this.mMaxWidth, (float) ((this.mRv.getScrollbarTrackHeight() - this.mMaxWidth) - height)));
    }
}