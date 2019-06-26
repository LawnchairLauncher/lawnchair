package com.google.android.apps.nexuslauncher.allapps;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewConfiguration;
import ch.deletescape.lawnchair.LawnchairUtilsKt;
import ch.deletescape.lawnchair.font.CustomFontManager;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragController.DragListener;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.graphics.BitmapRenderer;
import com.android.launcher3.graphics.DragPreviewProvider;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.touch.ItemLongClickListener;

public class ActionView extends BubbleTextView implements OnLongClickListener {
    private Action mAction;
    private final boolean mIsRTL;
    private final Point mLastTouchPos;
    private int mMeasuredUnspecifiedWidth;
    private int mPos;

    private class MyDragPreviewProvider extends DragPreviewProvider {
        protected final Point mPositionShift = new Point();

        public MyDragPreviewProvider(View view, Point point) {
            super(view, view.getContext());
            this.mPositionShift.set(point.x, point.y);
        }

        public float getScaleAndPosition(Bitmap bitmap, int[] iArr) {
            Launcher launcher = Launcher.getLauncher(this.mView.getContext());
            launcher.getDragLayer().getLocationInDragLayer(this.mView, iArr);
            float iconSize = ((float) ActionView.this.getIconSize()) / ((float) launcher.getDeviceProfile().iconSizePx);
            iArr[0] = (iArr[0] + this.mPositionShift.x) - (bitmap.getWidth() / 2);
            iArr[1] = (iArr[1] + this.mPositionShift.y) - bitmap.getHeight();
            return iconSize;
        }

        public Bitmap createDragBitmap() {
            Rect drawableBounds = DragPreviewProvider.getDrawableBounds(ActionView.this.getIcon());
            float width = drawableBounds.width() > 0 ? ((float) Launcher.getLauncher(this.mView.getContext()).getDeviceProfile().iconSizePx) / ((float) drawableBounds.width()) : 1.0f;
            int width1 = ((int) (((float) drawableBounds.width()) * width)) + this.blurSizeOutline;
            int height = ((int) (((float) drawableBounds.height()) * width)) + this.blurSizeOutline;
            return BitmapRenderer.createHardwareBitmap(width1, height, out -> {
                drawDragView(out, width);
            });
        }
    }

    public ActionView(@NonNull Context context) {
        this(context, null);
    }

    public ActionView(@NonNull Context context, @Nullable AttributeSet attributeSet) {
        super(context, attributeSet);
        this.mLastTouchPos = new Point();
        this.mIsRTL = Utilities.isRtl(getResources());
        setOnClickListener(ItemClickHandler.INSTANCE);
        setOnLongClickListener(this);
        setLongPressTimeout(ViewConfiguration.getLongPressTimeout());
        setCompoundDrawablePadding(getResources().getDimensionPixelSize(R.dimen.action_view_compound_drawable_padding));

        BaseDraggingActivity activity = LawnchairUtilsKt.getBaseDraggingActivityOrNull(context);
        DeviceProfile grid = activity.getDeviceProfile();
        setTextSize(TypedValue.COMPLEX_UNIT_PX, grid.allAppsIconTextSizePx);

        setMaxLines(1);
        setSingleLine(true);
    }

    public void setAction(Action action, int i) {
        this.mAction = action;
        this.mPos = i;
    }

    public Action getAction() {
        return this.mAction;
    }

    public boolean performClick() {
        logClickEvent();
        return super.performClick();
    }

    private void logClickEvent() {
        ActionsController.get(getContext()).getLogger().logClick(this.mAction.id, this.mPos);
    }

    protected void onMeasure(int i, int i2) {
        super.onMeasure(0, i2);
        this.mMeasuredUnspecifiedWidth = getMeasuredWidth();
        super.onMeasure(i, i2);
    }

    protected void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
        super.onTextChanged(charSequence, i, i2, i3);
        requestLayout();
    }

    public void onDraw(Canvas canvas) {
        float measuredWidth = ((float) (getMeasuredWidth() - this.mMeasuredUnspecifiedWidth)) / 2.0f;
        if (measuredWidth > 0.0f) {
            if (this.mIsRTL) {
                measuredWidth *= -1.0f;
            }
            canvas.translate(measuredWidth, 0.0f);
            super.onDraw(canvas);
            canvas.translate(-measuredWidth, 0.0f);
            return;
        }
        super.onDraw(canvas);
    }

    public boolean onTouchEvent(MotionEvent motionEvent) {
        int action = motionEvent.getAction();
        if (action == 0 || action == 2) {
            this.mLastTouchPos.set((int) motionEvent.getX(), (int) motionEvent.getY());
        }
        return super.onTouchEvent(motionEvent);
    }

    public void getIconBounds(Rect rect) {
        int measuredWidth = (getMeasuredWidth() - this.mMeasuredUnspecifiedWidth) / 2;
        int paddingLeft = getPaddingLeft() + measuredWidth;
        int iconSize = getIconSize() + paddingLeft;
        if (this.mIsRTL) {
            iconSize = (getMeasuredWidth() - getPaddingRight()) - measuredWidth;
            paddingLeft = iconSize - getIconSize();
        }
        measuredWidth = getPaddingTop();
        rect.set(paddingLeft, measuredWidth, iconSize, getIconSize() + measuredWidth);
    }

    public boolean onLongClick(final View view) {
        Launcher launcher = Launcher.getLauncher(view.getContext());
        if (!ItemLongClickListener.canStartDrag(launcher)) {
            return false;
        }
        if ((!launcher.isInState(LauncherState.ALL_APPS) && !launcher.isInState(LauncherState.OVERVIEW)) || launcher.getWorkspace().isSwitchingState()) {
            return false;
        }
        final DragController dragController = launcher.getDragController();
        dragController.addDragListener(new DragListener() {
            public void onDragStart(DragObject dragObject, DragOptions dragOptions) {
                view.setVisibility(View.INVISIBLE);
            }

            public void onDragEnd() {
                view.setVisibility(View.VISIBLE);
                dragController.removeDragListener(this);
            }
        });
        DeviceProfile deviceProfile = launcher.getDeviceProfile();
        DragOptions dragOptions = new DragOptions();
        dragOptions.intrinsicIconScaleFactor = ((float) deviceProfile.allAppsIconSizePx) / ((float) deviceProfile.iconSizePx);
        ItemInfo itemInfo = (ItemInfo) view.getTag();
        Point point = new Point();
        point.x = this.mLastTouchPos.x;
        point.y = this.mLastTouchPos.y;
        DragView dragView = launcher.getWorkspace().beginDragShared(view, launcher.getAppsView(),
                itemInfo, new MyDragPreviewProvider(view, point), dragOptions);
        if (dragView == null) return false;
        Rect rect = new Rect();
        getIconBounds(rect);
        dragView.animateShift(((-point.x) + rect.left) + (rect.width() / 2),
                ((-point.y) + rect.top) + rect.height());
        return false;
    }

    @Override
    protected boolean isTextHidden() {
        return false;
    }

    @Override
    protected int getCustomFontType(int display) {
        return CustomFontManager.FONT_ACTION_VIEW;
    }
}
