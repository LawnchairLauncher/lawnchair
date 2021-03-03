package com.android.launcher3.dragndrop;

import static com.android.launcher3.Utilities.ATLEAST_S;

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.WidgetPreviewLoader;
import com.android.launcher3.icons.BitmapRenderer;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.WidgetCell;

/**
 * Extension of {@link WidgetCell} which supports generating previews from {@link RemoteViews}
 */
public class LivePreviewWidgetCell extends WidgetCell {

    private RemoteViews mPreview;

    private AppWidgetHostView mPreviewAppWidgetHostView;

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

    public RemoteViews getPreview() {
        return mPreview;
    }

    /** Resets any resource. This should be called before recycling this view. */
    @Override
    public void clear() {
        super.clear();
        mPreview = null;
        mPreviewAppWidgetHostView = null;
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

        if (mPreviewAppWidgetHostView != null) {
            Bitmap preview = generateFromView(mActivity, mPreviewAppWidgetHostView,
                    mItem.widgetInfo, mPreviewWidth, new int[1]);
            if (preview != null) {
                applyPreview(preview);
                return;
            }
        }
        super.ensurePreview();
    }

    @Override
    public void applyFromCellItem(WidgetItem item, WidgetPreviewLoader loader) {
        if (ATLEAST_S
                && mPreview == null
                && item.widgetInfo != null
                && item.widgetInfo.previewLayout != Resources.ID_NULL) {
            mPreviewAppWidgetHostView = new AppWidgetHostView(getContext());
            LauncherAppWidgetProviderInfo launcherAppWidgetProviderInfo =
                    LauncherAppWidgetProviderInfo.fromProviderInfo(getContext(),
                            item.widgetInfo.clone());
            // A hack to force the initial layout to be the preview layout since there is no API for
            // rendering a preview layout for work profile apps yet. For non-work profile layout, a
            // proper solution is to use RemoteViews(PackageName, LayoutId).
            launcherAppWidgetProviderInfo.initialLayout = item.widgetInfo.previewLayout;
            mPreviewAppWidgetHostView.setAppWidget(/* appWidgetId= */ -1,
                    launcherAppWidgetProviderInfo);
            mPreviewAppWidgetHostView.setPadding(/* left= */ 0, /* top= */0, /* right= */
                    0, /* bottom= */ 0);
            mPreviewAppWidgetHostView.updateAppWidget(/* remoteViews= */ null);
        }

        super.applyFromCellItem(item, loader);
    }

    /**
     * Generates a bitmap by inflating {@param views}.
     * @see com.android.launcher3.WidgetPreviewLoader#generateWidgetPreview
     *
     * TODO: Consider moving this to the background thread.
     */
    public static Bitmap generateFromRemoteViews(BaseActivity activity, RemoteViews views,
            LauncherAppWidgetProviderInfo info, int previewSize, int[] preScaledWidthOut) {
        try {
            return generateFromView(activity, views.apply(activity, new FrameLayout(activity)),
                    info, previewSize, preScaledWidthOut);
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap generateFromView(BaseActivity activity, View v,
            LauncherAppWidgetProviderInfo info, int previewSize, int[] preScaledWidthOut) {

        DeviceProfile dp = activity.getDeviceProfile();
        int viewWidth = dp.cellWidthPx * info.spanX;
        int viewHeight = dp.cellHeightPx * info.spanY;

        v.measure(MeasureSpec.makeMeasureSpec(viewWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(viewHeight, MeasureSpec.EXACTLY));

        viewWidth = v.getMeasuredWidth();
        viewHeight = v.getMeasuredHeight();
        v.layout(0, 0, viewWidth, viewHeight);

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

        return BitmapRenderer.createSoftwareBitmap(bitmapWidth, bitmapHeight, c -> {
            c.scale(scale, scale);
            v.draw(c);
        });
    }
}
