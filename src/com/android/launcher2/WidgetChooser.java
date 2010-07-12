package com.android.launcher2;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;

public class WidgetChooser extends HomeCustomizationItemGallery implements DragSource {
    private DragController mDragController;

    public WidgetChooser(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setDragController(DragController dragger) {
        mDragController = dragger;
    }

    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        AppWidgetProviderInfo info = (AppWidgetProviderInfo)getAdapter().getItem(position);
        try {
            Resources r = mContext.getPackageManager().getResourcesForApplication(info.provider.getPackageName());

            Bitmap bmp = BitmapFactory.decodeResource(r, info.icon);
            final int w = bmp.getWidth();
            final int h = bmp.getHeight();

            // We don't really have an accurate location to use.  This will do.
            int screenX = mMotionDownRawX - (w / 2);
            int screenY = mMotionDownRawY - h;

            AppWidgetProviderInfo appWidgetInfo = (AppWidgetProviderInfo)view.getTag();
            LauncherAppWidgetInfo dragInfo = new LauncherAppWidgetInfo(info.provider);
            // TODO: Is this really the best place to do this?
            dragInfo.minWidth = appWidgetInfo.minWidth;
            dragInfo.minHeight = appWidgetInfo.minHeight;
            mDragController.startDrag(bmp, screenX, screenY,
                    0, 0, w, h, this, dragInfo, DragController.DRAG_ACTION_COPY);
            return true;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    public void onDropCompleted(View target, boolean success) {
    }
}

