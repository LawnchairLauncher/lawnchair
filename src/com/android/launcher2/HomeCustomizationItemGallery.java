package com.android.launcher2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.Gallery;

public abstract class HomeCustomizationItemGallery extends Gallery
    implements Gallery.OnItemLongClickListener {

    protected Context mContext;

    protected Launcher mLauncher;

    protected int mMotionDownRawX;
    protected int mMotionDownRawY;

    public HomeCustomizationItemGallery(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLongClickable(true);
        setOnItemLongClickListener(this);
        mContext = context;

        setCallbackDuringFling(false);
    }

    public void setLauncher(Launcher launcher) {
        mLauncher = launcher;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN && mLauncher.isAllAppsVisible()) {
            return false;
        }

        super.onTouchEvent(ev);

        int x = (int) ev.getX();
        int y = (int) ev.getY();

        switch (ev.getAction()) {
        case MotionEvent.ACTION_DOWN:
            mMotionDownRawX = (int) ev.getRawX();
            mMotionDownRawY = (int) ev.getRawY();
        }
        return true;
    }
}

