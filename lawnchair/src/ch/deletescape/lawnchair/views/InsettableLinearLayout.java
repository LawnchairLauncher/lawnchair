package ch.deletescape.lawnchair.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.android.launcher3.Insettable;
import com.android.launcher3.R;

public class InsettableLinearLayout extends LinearLayout implements Insettable {

    @ViewDebug.ExportedProperty(category = "launcher")
    protected Rect mInsets = new Rect();

    private boolean mInsetsSet = false;

    public Rect getInsets() {
        return mInsets;
    }

    public InsettableLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setLinearLayoutChildInsets(View child, Rect newInsets, Rect oldInsets) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();

        int childIndex = indexOfChild(child);
        int newTop = childIndex == 0 ? newInsets.top : 0;
        int oldTop = childIndex == 0 ? oldInsets.top : 0;
        int newBottom = childIndex == getChildCount() - 1 ? newInsets.bottom : 0;
        int oldBottom = childIndex == getChildCount() - 1 ? oldInsets.bottom : 0;

        if (child instanceof Insettable) {
            ((Insettable) child).setInsets(new Rect(newInsets.left, newTop, newInsets.right, newBottom));
        } else if (!lp.ignoreInsets) {
            lp.topMargin += (newTop - oldTop);
            lp.leftMargin += (newInsets.left - oldInsets.left);
            lp.rightMargin += (newInsets.right - oldInsets.right);
            lp.bottomMargin += (newBottom - oldBottom);
        }
        child.setLayoutParams(lp);
    }

    @Override
    public void setInsets(Rect insets) {
        if (getOrientation() != VERTICAL) {
            throw new IllegalStateException("Doesn't support horizontal orientation");
        }
        mInsetsSet = true;
        final int n = getChildCount();
        for (int i = 0; i < n; i++) {
            final View child = getChildAt(i);
            setLinearLayoutChildInsets(child, insets, mInsets);
        }
        mInsets.set(insets);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new InsettableLinearLayout.LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    // Override to allow type-checking of LayoutParams.
    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof InsettableLinearLayout.LayoutParams;
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    public static class LayoutParams extends LinearLayout.LayoutParams {
        boolean ignoreInsets = false;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            TypedArray a = c.obtainStyledAttributes(attrs,
                    R.styleable.InsettableFrameLayout_Layout);
            ignoreInsets = a.getBoolean(
                    R.styleable.InsettableFrameLayout_Layout_layout_ignoreInsets, false);
            a.recycle();
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(ViewGroup.LayoutParams lp) {
            super(lp);
        }
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        if (mInsetsSet) {
            throw new IllegalStateException("Cannot modify views after insets are set");
        }
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        if (mInsetsSet) {
            throw new IllegalStateException("Cannot modify views after insets are set");
        }
    }
}
