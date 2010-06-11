package com.android.launcher2;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Gallery;

public class WidgetChooser extends Gallery
    implements Gallery.OnItemLongClickListener, DragSource {

    Context mContext;

    private Launcher mLauncher;
    private DragController mDragController;
    private WidgetGalleryAdapter mWidgetGalleryAdapter;

    private int mMotionDownRawX;
    private int mMotionDownRawY;

    public WidgetChooser(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLongClickable(true);
        setOnItemLongClickListener(this);
        mContext = context;

        setCallbackDuringFling(false);

        mWidgetGalleryAdapter = new WidgetGalleryAdapter(context);
        setAdapter(mWidgetGalleryAdapter);
    }

    public void onDropCompleted(View target, boolean success) {
        // TODO Auto-generated method stub

    }

    public void setDragController(DragController dragger) {
        mDragController = dragger;
    }

    public void setLauncher(Launcher launcher) {
        mLauncher = launcher;
    }

    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        AppWidgetProviderInfo info = (AppWidgetProviderInfo)mWidgetGalleryAdapter.getItem(position);
        try {
            Resources r = mContext.getPackageManager().getResourcesForApplication(info.provider.getPackageName());

            Bitmap bmp = BitmapFactory.decodeResource(r, info.icon);
            final int w = bmp.getWidth();
            final int h = bmp.getHeight();

            // We don't really have an accurate location to use.  This will do.
            int screenX = mMotionDownRawX - (w / 2);
            int screenY = mMotionDownRawY - h;

            LauncherAppWidgetInfo dragInfo = new LauncherAppWidgetInfo(-1);
            dragInfo.providerName = info.provider;
            mDragController.startDrag(bmp, screenX, screenY,
                    0, 0, w, h, this, dragInfo, DragController.DRAG_ACTION_COPY);
            return true;
        } catch (NameNotFoundException e) {
            return false;
        }
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
