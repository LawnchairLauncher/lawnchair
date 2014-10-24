package com.android.launcher3;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class InsettableFrameLayout extends FrameLayout implements
    ViewGroup.OnHierarchyChangeListener, Insettable {

    protected Rect mInsets = new Rect();

    public InsettableFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnHierarchyChangeListener(this);
    }

    public void setFrameLayoutChildInsets(View child, Rect newInsets, Rect oldInsets) {
        final FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) child.getLayoutParams();
        if (child instanceof Insettable) {
            ((Insettable) child).setInsets(newInsets);
        } else {
            flp.topMargin += (newInsets.top - oldInsets.top);
            flp.leftMargin += (newInsets.left - oldInsets.left);
            flp.rightMargin += (newInsets.right - oldInsets.right);
            flp.bottomMargin += (newInsets.bottom - oldInsets.bottom);
        }
        child.setLayoutParams(flp);
    }

    @Override
    public void setInsets(Rect insets) {
        final int n = getChildCount();
        for (int i = 0; i < n; i++) {
            final View child = getChildAt(i);
            setFrameLayoutChildInsets(child, insets, mInsets);
        }
        mInsets.set(insets);
    }

    @Override
    public void onChildViewAdded(View parent, View child) {
        setFrameLayoutChildInsets(child, mInsets, new Rect());
    }

    @Override
    public void onChildViewRemoved(View parent, View child) {
    }

}
