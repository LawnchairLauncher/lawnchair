package ch.deletescape.lawnchair;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import ch.deletescape.lawnchair.dragndrop.DragLayer;


public abstract class AbstractFloatingView extends LinearLayout {
    protected boolean mIsOpen;

    protected abstract void handleClose(boolean z);

    protected abstract boolean isOfType(int i);

    public AbstractFloatingView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    public AbstractFloatingView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        return true;
    }

    public final void close(boolean z) {
        handleClose((!Utilities.isPowerSaverOn(getContext())) & z);
    }

    public ExtendedEditText getActiveTextView() {
        return null;
    }

    public View getExtendedTouchView() {
        return null;
    }

    public final boolean isOpen() {
        return this.mIsOpen;
    }

    protected void onWidgetsBound() {
    }

    protected static AbstractFloatingView getOpenView(Launcher launcher, int i) {
        DragLayer dragLayer = launcher.getDragLayer();
        for (int childCount = dragLayer.getChildCount() - 1; childCount >= 0; childCount--) {
            View childAt = dragLayer.getChildAt(childCount);
            if (childAt instanceof AbstractFloatingView) {
                AbstractFloatingView abstractFloatingView = (AbstractFloatingView) childAt;
                if (abstractFloatingView.isOfType(i) && abstractFloatingView.isOpen()) {
                    return abstractFloatingView;
                }
            }
        }
        return null;
    }

    public static void closeOpenContainer(Launcher launcher, int i) {
        AbstractFloatingView openView = getOpenView(launcher, i);
        if (openView != null) {
            openView.close(true);
        }
    }

    public static void closeAllOpenViews(Launcher launcher, boolean z) {
        DragLayer dragLayer = launcher.getDragLayer();
        for (int childCount = dragLayer.getChildCount() - 1; childCount >= 0; childCount--) {
            View childAt = dragLayer.getChildAt(childCount);
            if (childAt instanceof AbstractFloatingView) {
                ((AbstractFloatingView) childAt).close(z);
            }
        }
    }

    public static void closeAllOpenViews(Launcher launcher) {
        closeAllOpenViews(launcher, true);
    }

    public static AbstractFloatingView getTopOpenView(Launcher launcher) {
        return getOpenView(launcher, 7);
    }
}