package com.android.launcher3.dragndrop;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.WidgetCell;

/**
 * Extension of {@link WidgetCell} which supports generating previews from {@link RemoteViews}
 */
public class LivePreviewWidgetCell extends WidgetCell {

    private RemoteViews mPreview;

    public LivePreviewWidgetCell(Context context) {
        this(context, null);
    }

    public LivePreviewWidgetCell(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LivePreviewWidgetCell(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setPreview(RemoteViews view) {
        mPreview = view;
    }

    @Override
    public void ensurePreview() {
        if (mPreview != null && mActiveRequest == null) {
            Bitmap preview = generateFromRemoteViews(
                    mActivity, mPreview, mItem.widgetInfo, mPresetPreviewSize, new int[1]);
            if (preview != null) {
                applyPreview(preview);
                return;
            }
        }
        super.ensurePreview();
    }

    /**
     * Generates a bitmap by inflating {@param views}.
     * @see com.android.launcher3.WidgetPreviewLoader#generateWidgetPreview
     *
     * TODO: Consider moving this to the background thread.
     */
    public static Bitmap generateFromRemoteViews(BaseActivity activity, RemoteViews views,
            LauncherAppWidgetProviderInfo info, int previewSize, int[] preScaledWidthOut) {

        DeviceProfile dp = activity.getDeviceProfile();
        int viewWidth = dp.cellWidthPx * info.spanX;
        int viewHeight = dp.cellHeightPx * info.spanY;

        final View v;
        try {
            v = views.apply(activity, new FrameLayout(activity));
            v.measure(MeasureSpec.makeMeasureSpec(viewWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(viewHeight, MeasureSpec.EXACTLY));

            viewWidth = v.getMeasuredWidth();
            viewHeight = v.getMeasuredHeight();
            v.layout(0, 0, viewWidth, viewHeight);
        } catch (Exception e) {
            return null;
        }

        preScaledWidthOut[0] = viewWidth;
        final int bitmapWidth, bitmapHeight;
        final float scale;
        if (viewWidth > previewSize) {
            scale = ((float) previewSize) / viewWidth;
            bitmapWidth = previewSize;
            bitmapHeight = (int) (viewHeight * scale);
        } else {
            scale = 1;
            bitmapWidth = viewWidth;
            bitmapHeight = viewHeight;
        }

        Bitmap preview = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(preview);
        c.scale(scale, scale);
        v.draw(c);
        c.setBitmap(null);
        return preview;
    }
}
