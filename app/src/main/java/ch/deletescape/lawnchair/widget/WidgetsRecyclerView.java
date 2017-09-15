package ch.deletescape.lawnchair.widget;

import android.content.Context;
import android.support.v7.widget.LinearLayoutManager;
import android.util.AttributeSet;
import android.view.View;

import ch.deletescape.lawnchair.BaseRecyclerView;

public class WidgetsRecyclerView extends BaseRecyclerView {
    private WidgetsListAdapter mAdapter;

    public WidgetsRecyclerView(Context context) {
        this(context, null);
    }

    public WidgetsRecyclerView(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public WidgetsRecyclerView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        addOnItemTouchListener(this);
        setLayoutManager(new LinearLayoutManager(getContext()));
    }

    @Override
    public int getFastScrollerTrackColor(int i) {
        return -1;
    }

    @Override
    public void setAdapter(Adapter c0280q) {
        super.setAdapter(c0280q);
        this.mAdapter = (WidgetsListAdapter) c0280q;
    }

    @Override
    public String scrollToPositionAtProgress(float f) {
        if (isModelNotReady()) {
            return "";
        }
        float f2;
        stopScroll();
        float itemCount = ((float) this.mAdapter.getItemCount()) * f;
        ((LinearLayoutManager) getLayoutManager()).scrollToPositionWithOffset(0, (int) (-(((float) getAvailableScrollHeight()) * f)));
        if (f == 1.0f) {
            f2 = itemCount - 1.0f;
        } else {
            f2 = itemCount;
        }
        return this.mAdapter.getSectionName((int) f2);
    }

    @Override
    public void onUpdateScrollbar(int i) {
        if (!isModelNotReady()) {
            int currentScrollY = getCurrentScrollY();
            if (currentScrollY < 0) {
                this.mScrollbar.setThumbOffsetY(-1);
            } else {
                synchronizeScrollBarThumbOffsetToViewScroll(currentScrollY, getAvailableScrollHeight());
            }
        }
    }

    @Override
    public int getCurrentScrollY() {
        if (isModelNotReady() || getChildCount() == 0) {
            return -1;
        }
        View childAt = getChildAt(0);
        int childPosition = getChildAdapterPosition(childAt) * childAt.getMeasuredHeight();
        return (childPosition + getPaddingTop()) - getLayoutManager().getDecoratedTop(childAt);
    }

    @Override
    protected int getAvailableScrollHeight() {
        return (((getChildAt(0).getMeasuredHeight() * this.mAdapter.getItemCount()) + getPaddingTop()) + getPaddingBottom()) - getHeight();
    }

    private boolean isModelNotReady() {
        return this.mAdapter.getItemCount() == 0;
    }
}