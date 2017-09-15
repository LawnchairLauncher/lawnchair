package ch.deletescape.lawnchair.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.SimpleOnStylusPressListener;
import ch.deletescape.lawnchair.StylusEventHelper;
import ch.deletescape.lawnchair.WidgetPreviewLoader;
import ch.deletescape.lawnchair.model.WidgetItem;

public class WidgetCell extends LinearLayout implements OnLayoutChangeListener {
    protected WidgetPreviewLoader.PreviewLoadRequest mActiveRequest;
    protected final Launcher launcher;
    private boolean mAnimatePreview;
    private int mCellSize;
    protected WidgetItem mItem;
    protected int mPresetPreviewSize;
    private StylusEventHelper mStylusEventHelper;
    private TextView mWidgetDims;
    private WidgetImageView mWidgetImage;
    private TextView mWidgetName;
    private WidgetPreviewLoader mWidgetPreviewLoader;

    public WidgetCell(Context context) {
        this(context, null);
    }

    public WidgetCell(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public WidgetCell(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        this.mAnimatePreview = true;
        this.launcher = Launcher.getLauncher(context);
        this.mStylusEventHelper = new StylusEventHelper(new SimpleOnStylusPressListener(this), this);
        setContainerWidth();
        setLayoutParams(new LayoutParams(0, 0));
        setWillNotDraw(false);
        setClipToPadding(false);
        setAccessibilityDelegate(this.launcher.getAccessibilityDelegate());
    }

    private void setContainerWidth() {
        this.mCellSize = (int) (((float) this.launcher.getDeviceProfile().cellWidthPx) * 2.6f);
        this.mPresetPreviewSize = (int) (((float) this.mCellSize) * 0.8f);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        this.mWidgetImage = findViewById(R.id.widget_preview);
        this.mWidgetName = findViewById(R.id.widget_name);
        this.mWidgetDims = findViewById(R.id.widget_dims);
    }

    public void clear() {
        this.mWidgetImage.animate().cancel();
        this.mWidgetImage.setBitmap(null);
        this.mWidgetName.setText(null);
        this.mWidgetDims.setText(null);
        if (this.mActiveRequest != null) {
            this.mActiveRequest.cancel();
            this.mActiveRequest = null;
        }
    }

    public void applyFromCellItem(WidgetItem widgetItem, WidgetPreviewLoader widgetPreviewLoader) {
        this.mItem = widgetItem;
        this.mWidgetName.setText(this.mItem.label);
        this.mWidgetDims.setText(getContext().getString(R.string.widget_dims_format, new Object[]{this.mItem.spanX, this.mItem.spanY}));
        this.mWidgetDims.setContentDescription(getContext().getString(R.string.widget_accessible_dims_format, this.mItem.spanX, this.mItem.spanY));
        this.mWidgetPreviewLoader = widgetPreviewLoader;
        if (widgetItem.activityInfo != null) {
            setTag(new PendingAddShortcutInfo(widgetItem.activityInfo));
        } else {
            setTag(new PendingAddWidgetInfo(launcher, widgetItem.widgetInfo));
        }
    }

    public WidgetImageView getWidgetView() {
        return this.mWidgetImage;
    }

    public void setAnimatePreview(boolean z) {
        this.mAnimatePreview = z;
    }

    public void applyPreview(Bitmap bitmap) {
        applyPreview(bitmap, true);
    }

    public void applyPreview(Bitmap bitmap, boolean z) {
        if (bitmap != null) {
            this.mWidgetImage.setBitmap(bitmap);
            if (this.mAnimatePreview) {
                this.mWidgetImage.setAlpha(0.0f);
                this.mWidgetImage.animate().alpha(1.0f).setDuration(90);
                return;
            }
            this.mWidgetImage.setAlpha(1.0f);
        }
    }

    public void ensurePreview() {
        ensurePreview(true);
    }

    public void ensurePreview(boolean z) {
        if (this.mActiveRequest == null) {
            this.mActiveRequest = this.mWidgetPreviewLoader.getPreview(this.mItem, this.mPresetPreviewSize, this.mPresetPreviewSize, this);
        }
    }

    @Override
    public void onLayoutChange(View view, int i, int i2, int i3, int i4, int i5, int i6, int i7, int i8) {
        removeOnLayoutChangeListener(this);
        ensurePreview();
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        boolean onTouchEvent = super.onTouchEvent(motionEvent);
        return this.mStylusEventHelper.onMotionEvent(motionEvent) || onTouchEvent;
    }

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        params.height = mCellSize;
        params.width = mCellSize;
        super.setLayoutParams(params);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return WidgetCell.class.getName();
    }
}