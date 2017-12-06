package ch.deletescape.lawnchair;

import android.content.Context;
import android.graphics.Canvas;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.TextView;

import ch.deletescape.lawnchair.views.RecyclerViewFastScroller;

public abstract class BaseRecyclerView extends RecyclerView implements  RecyclerView.OnItemTouchListener {
    private float mContentTranslationY;
    protected RecyclerViewFastScroller mScrollbar;

    public abstract int getCurrentScrollY();

    public abstract void onUpdateScrollbar(int i);

    public abstract String scrollToPositionAtProgress(float f);

    public BaseRecyclerView(Context context) {
        this(context, null);
    }

    public BaseRecyclerView(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public BaseRecyclerView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        mContentTranslationY = 0;
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        addOnItemTouchListener(this);
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewGroup viewGroup = (ViewGroup) getParent();
        this.mScrollbar = viewGroup.findViewById(R.id.fast_scroller);
        this.mScrollbar.setRecyclerView(this, (TextView) viewGroup.findViewById(R.id.fast_scroller_popup));
    }

    public boolean onInterceptTouchEvent(RecyclerView recyclerView, MotionEvent motionEvent) {
        return handleTouchEvent(motionEvent);
    }

    public void onTouchEvent(RecyclerView recyclerView, MotionEvent motionEvent) {
        handleTouchEvent(motionEvent);
    }

    private boolean handleTouchEvent(MotionEvent motionEvent) {
        int left = getLeft() - this.mScrollbar.getLeft();
        int top = getTop() - this.mScrollbar.getTop();
        motionEvent.offsetLocation((float) left, (float) top);
        try {
            boolean handleTouchEvent = this.mScrollbar.handleTouchEvent(motionEvent);
            return handleTouchEvent;
        } finally {
            motionEvent.offsetLocation((float) (-left), (float) (-top));
        }
    }

    public void onRequestDisallowInterceptTouchEvent(boolean z) {
    }

    public int getScrollbarTrackHeight() {
        return (getHeight() - getPaddingTop()) - getPaddingBottom();
    }

    protected int getAvailableScrollBarHeight() {
        return getScrollbarTrackHeight() - this.mScrollbar.getThumbHeight();
    }

    public RecyclerViewFastScroller getScrollBar() {
        return this.mScrollbar;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        boolean translate = Float.compare(mContentTranslationY, 0) != 0;
        if (translate)
            canvas.translate(0, mContentTranslationY);
        super.dispatchDraw(canvas);
        if (translate)
            canvas.translate(0, -mContentTranslationY);
        onUpdateScrollbar(0);
        mScrollbar.draw(canvas);
    }

    protected void synchronizeScrollBarThumbOffsetToViewScroll(int i, int i2) {
        if (i2 <= 0) {
            this.mScrollbar.setThumbOffsetY(-1);
            return;
        }
        this.mScrollbar.setThumbOffsetY((int) ((((float) i) / ((float) i2)) * ((float) getAvailableScrollBarHeight())));
    }

    public boolean supportsFastScrolling() {
        return true;
    }

    public void onFastScrollCompleted() {
    }

    public float getContentTranslationY() {
        return mContentTranslationY;
    }

    public void setContentTranslationY(float f) {
        mContentTranslationY = f;
        invalidate();
    }

    public void reset() {
        mScrollbar.reattachThumbToScroll();
    }
}