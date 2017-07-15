package ch.deletescape.lawnchair.widget;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.View;
import android.widget.RemoteViews;

import ch.deletescape.lawnchair.DeviceProfile;
import ch.deletescape.lawnchair.DragSource;
import ch.deletescape.lawnchair.HolographicOutlineHelper;
import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.LauncherAppState;
import ch.deletescape.lawnchair.PendingAddItemInfo;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.dragndrop.DragOptions;
import ch.deletescape.lawnchair.graphics.DragPreviewProvider;
import ch.deletescape.lawnchair.graphics.LauncherIcons;

public class PendingItemDragHelper extends DragPreviewProvider {
    private final PendingAddItemInfo mAddInfo;
    private RemoteViews mPreview;
    private Bitmap mPreviewBitmap;

    public PendingItemDragHelper(View view) {
        super(view);
        this.mAddInfo = (PendingAddItemInfo) view.getTag();
    }

    public void setPreview(RemoteViews remoteViews) {
        this.mPreview = remoteViews;
    }

    public void startDrag(Rect rect, int i, int i2, Point point, DragSource dragSource, DragOptions dragOptions) {
        int min;
        int i3;
        float width;
        Point point2;
        Rect rect2;
        Bitmap bitmap;
        Launcher launcher = Launcher.getLauncher(this.mView.getContext());
        LauncherAppState instance = LauncherAppState.getInstance();
        Bitmap bmap;
        if (this.mAddInfo instanceof PendingAddWidgetInfo) {
            PendingAddWidgetInfo pendingAddWidgetInfo = (PendingAddWidgetInfo) this.mAddInfo;
            min = Math.min((int) (((float) i) * 1.25f), launcher.getWorkspace().estimateItemSize(pendingAddWidgetInfo, true)[0]);
            int[] iArr = new int[1];
            bmap = instance.getWidgetCache().generateWidgetPreview(launcher, pendingAddWidgetInfo.info, min, null, iArr);
            if (iArr[0] < i) {
                i3 = (i - iArr[0]) / 2;
                if (i > i2) {
                    i3 = (i3 * i2) / i;
                }
                rect.left += i3;
                rect.right -= i3;
            }
            width = ((float) rect.width()) / ((float) bmap.getWidth());
            launcher.getDragController().addDragListener(new WidgetHostViewLoader(launcher, this.mView));
            point2 = null;
            rect2 = null;
            bitmap = bmap;
        } else {
            bmap = LauncherIcons.createScaledBitmapWithoutShadow(((PendingAddShortcutInfo) this.mAddInfo).activityInfo.getFullResIcon(instance.getIconCache()), launcher, 26);
            PendingAddItemInfo pendingAddItemInfo = this.mAddInfo;
            this.mAddInfo.spanY = 1;
            pendingAddItemInfo.spanX = 1;
            width = ((float) launcher.getDeviceProfile().iconSizePx) / ((float) bmap.getWidth());
            point2 = new Point(this.previewPadding / 2, this.previewPadding / 2);
            int[] estimateItemSize = launcher.getWorkspace().estimateItemSize(this.mAddInfo, false);
            DeviceProfile deviceProfile = launcher.getDeviceProfile();
            int i4 = deviceProfile.iconSizePx;
            int dimensionPixelSize = launcher.getResources().getDimensionPixelSize(R.dimen.widget_preview_shortcut_padding);
            rect.left += dimensionPixelSize;
            rect.top = dimensionPixelSize + rect.top;
            rect2 = new Rect();
            rect2.left = (estimateItemSize[0] - i4) / 2;
            rect2.right = rect2.left + i4;
            rect2.top = (((estimateItemSize[1] - i4) - deviceProfile.iconTextSizePx) - deviceProfile.iconDrawablePaddingPx) / 2;
            rect2.bottom = rect2.top + i4;
            bitmap = bmap;
        }
        launcher.getWorkspace().prepareDragWithProvider(this);
        i3 = ((int) (((((float) bitmap.getWidth()) * width) - ((float) bitmap.getWidth())) / 2.0f)) + (point.x + rect.left);
        min = ((int) (((((float) bitmap.getHeight()) * width) - ((float) bitmap.getHeight())) / 2.0f)) + (point.y + rect.top);
        this.mPreviewBitmap = bitmap;
        launcher.getDragController().startDrag(bitmap, i3, min, dragSource, this.mAddInfo, point2, rect2, width, dragOptions);
    }

    @Override
    public Bitmap createDragOutline(Canvas canvas) {
        int i;
        Rect rect2;
        if (this.mAddInfo instanceof PendingAddShortcutInfo) {
            Bitmap createBitmap = Bitmap.createBitmap(this.mPreviewBitmap.getWidth() + this.blurSizeOutline, this.mPreviewBitmap.getHeight() + this.blurSizeOutline, Config.ALPHA_8);
            canvas.setBitmap(createBitmap);
            i = Launcher.getLauncher(this.mView.getContext()).getDeviceProfile().iconSizePx;
            Rect rect = new Rect(0, 0, this.mPreviewBitmap.getWidth(), this.mPreviewBitmap.getHeight());
            rect2 = new Rect(0, 0, i, i);
            rect2.offset(this.blurSizeOutline / 2, this.blurSizeOutline / 2);
            canvas.drawBitmap(this.mPreviewBitmap, rect, rect2, new Paint(2));
            HolographicOutlineHelper.obtain(this.mView.getContext()).applyExpensiveOutlineWithBlur(createBitmap, canvas);
            canvas.setBitmap(null);
            return createBitmap;
        }
        int[] estimateItemSize = Launcher.getLauncher(this.mView.getContext()).getWorkspace().estimateItemSize(this.mAddInfo, false);
        i = estimateItemSize[0];
        int i2 = estimateItemSize[1];
        Bitmap createBitmap2 = Bitmap.createBitmap(i, i2, Config.ALPHA_8);
        canvas.setBitmap(createBitmap2);
        rect2 = new Rect(0, 0, this.mPreviewBitmap.getWidth(), this.mPreviewBitmap.getHeight());
        float min = Math.min(((float) (i - this.blurSizeOutline)) / ((float) this.mPreviewBitmap.getWidth()), ((float) (i2 - this.blurSizeOutline)) / ((float) this.mPreviewBitmap.getHeight()));
        int width = (int) (((float) this.mPreviewBitmap.getWidth()) * min);
        int height = (int) (min * ((float) this.mPreviewBitmap.getHeight()));
        Rect rect3 = new Rect(0, 0, width, height);
        rect3.offset((i - width) / 2, (i2 - height) / 2);
        canvas.drawBitmap(this.mPreviewBitmap, rect2, rect3, null);
        HolographicOutlineHelper.obtain(this.mView.getContext()).applyExpensiveOutlineWithBlur(createBitmap2, canvas);
        canvas.setBitmap(null);
        return createBitmap2;
    }
}