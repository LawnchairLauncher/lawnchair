/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3;

import static android.animation.ValueAnimator.areAnimatorsEnabled;

import static com.android.launcher3.anim.Interpolators.DEACCEL_1_5;
import static com.android.launcher3.config.FeatureFlags.SHOW_HOME_GARDENING;
import static com.android.launcher3.dragndrop.DraggableView.DRAGGABLE_ICON;
import static com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.Log;
import android.util.Property;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.IntDef;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.accessibility.DragAndDropAccessibilityDelegate;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.folder.PreviewBackground;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.util.CellAndSpan;
import com.android.launcher3.util.GridOccupancy;
import com.android.launcher3.util.ParcelableSparseArray;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.widget.LauncherAppWidgetHostView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Stack;

public class CellLayout extends ViewGroup {
    private static final String TAG = "CellLayout";
    private static final boolean LOGD = false;

    protected final ActivityContext mActivity;
    @ViewDebug.ExportedProperty(category = "launcher")
    @Thunk int mCellWidth;
    @ViewDebug.ExportedProperty(category = "launcher")
    @Thunk int mCellHeight;
    private int mFixedCellWidth;
    private int mFixedCellHeight;
    @ViewDebug.ExportedProperty(category = "launcher")
    private Point mBorderSpace;

    @ViewDebug.ExportedProperty(category = "launcher")
    private int mCountX;
    @ViewDebug.ExportedProperty(category = "launcher")
    private int mCountY;

    private boolean mDropPending = false;

    // These are temporary variables to prevent having to allocate a new object just to
    // return an (x, y) value from helper functions. Do NOT use them to maintain other state.
    @Thunk final int[] mTmpPoint = new int[2];
    @Thunk final int[] mTempLocation = new int[2];
    final PointF mTmpPointF = new PointF();

    private GridOccupancy mOccupied;
    private GridOccupancy mTmpOccupied;

    private OnTouchListener mInterceptTouchListener;

    private final ArrayList<DelegatedCellDrawing> mDelegatedCellDrawings = new ArrayList<>();
    final PreviewBackground mFolderLeaveBehind = new PreviewBackground();

    private static final int[] BACKGROUND_STATE_ACTIVE = new int[] { android.R.attr.state_active };
    private static final int[] BACKGROUND_STATE_DEFAULT = EMPTY_STATE_SET;
    private final Drawable mBackground;

    // These values allow a fixed measurement to be set on the CellLayout.
    private int mFixedWidth = -1;
    private int mFixedHeight = -1;

    // If we're actively dragging something over this screen, mIsDragOverlapping is true
    private boolean mIsDragOverlapping = false;

    // These arrays are used to implement the drag visualization on x-large screens.
    // They are used as circular arrays, indexed by mDragOutlineCurrent.
    @Thunk final CellLayoutLayoutParams[] mDragOutlines = new CellLayoutLayoutParams[4];
    @Thunk final float[] mDragOutlineAlphas = new float[mDragOutlines.length];
    private final InterruptibleInOutAnimator[] mDragOutlineAnims =
            new InterruptibleInOutAnimator[mDragOutlines.length];

    // Used as an index into the above 3 arrays; indicates which is the most current value.
    private int mDragOutlineCurrent = 0;
    private final Paint mDragOutlinePaint = new Paint();

    @Thunk final ArrayMap<CellLayoutLayoutParams, Animator> mReorderAnimators = new ArrayMap<>();
    @Thunk final ArrayMap<Reorderable, ReorderPreviewAnimation> mShakeAnimators = new ArrayMap<>();

    private boolean mItemPlacementDirty = false;

    // Used to visualize the grid and drop locations
    private boolean mVisualizeCells = false;
    private boolean mVisualizeDropLocation = true;
    private RectF mVisualizeGridRect = new RectF();
    private Paint mVisualizeGridPaint = new Paint();
    private int mGridVisualizationRoundingRadius;
    private float mGridAlpha = 0f;
    private int mGridColor = 0;
    private float mSpringLoadedProgress = 0f;
    private float mScrollProgress = 0f;

    // When a drag operation is in progress, holds the nearest cell to the touch point
    private final int[] mDragCell = new int[2];
    private final int[] mDragCellSpan = new int[2];

    private boolean mDragging = false;

    private final TimeInterpolator mEaseOutInterpolator;
    private final ShortcutAndWidgetContainer mShortcutsAndWidgets;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({WORKSPACE, HOTSEAT, FOLDER})
    public @interface ContainerType{}
    public static final int WORKSPACE = 0;
    public static final int HOTSEAT = 1;
    public static final int FOLDER = 2;

    @ContainerType private final int mContainerType;

    private final float mChildScale = 1f;

    public static final int MODE_SHOW_REORDER_HINT = 0;
    public static final int MODE_DRAG_OVER = 1;
    public static final int MODE_ON_DROP = 2;
    public static final int MODE_ON_DROP_EXTERNAL = 3;
    public static final int MODE_ACCEPT_DROP = 4;
    private static final boolean DESTRUCTIVE_REORDER = false;
    private static final boolean DEBUG_VISUALIZE_OCCUPIED = false;

    private static final float REORDER_PREVIEW_MAGNITUDE = 0.12f;
    private static final int REORDER_ANIMATION_DURATION = 150;
    @Thunk final float mReorderPreviewAnimationMagnitude;

    private final ArrayList<View> mIntersectingViews = new ArrayList<>();
    private final Rect mOccupiedRect = new Rect();
    private final int[] mDirectionVector = new int[2];

    ItemConfiguration mPreviousSolution = null;
    private static final int INVALID_DIRECTION = -100;

    private final Rect mTempRect = new Rect();
    private final RectF mTempRectF = new RectF();
    private final float[] mTmpFloatArray = new float[4];

    private static final Paint sPaint = new Paint();

    // Related to accessible drag and drop
    DragAndDropAccessibilityDelegate mTouchHelper;


    public static final FloatProperty<CellLayout> SPRING_LOADED_PROGRESS =
            new FloatProperty<CellLayout>("spring_loaded_progress") {
                @Override
                public Float get(CellLayout cl) {
                    return cl.getSpringLoadedProgress();
                }

                @Override
                public void setValue(CellLayout cl, float progress) {
                    cl.setSpringLoadedProgress(progress);
                }
            };

    public CellLayout(Context context) {
        this(context, null);
    }

    public CellLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CellLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CellLayout, defStyle, 0);
        mContainerType = a.getInteger(R.styleable.CellLayout_containerType, WORKSPACE);
        a.recycle();

        // A ViewGroup usually does not draw, but CellLayout needs to draw a rectangle to show
        // the user where a dragged item will land when dropped.
        setWillNotDraw(false);
        setClipToPadding(false);
        mActivity = ActivityContext.lookupContext(context);
        DeviceProfile deviceProfile = mActivity.getDeviceProfile();

        resetCellSizeInternal(deviceProfile);

        mCountX = deviceProfile.inv.numColumns;
        mCountY = deviceProfile.inv.numRows;
        mOccupied =  new GridOccupancy(mCountX, mCountY);
        mTmpOccupied = new GridOccupancy(mCountX, mCountY);

        mFolderLeaveBehind.mDelegateCellX = -1;
        mFolderLeaveBehind.mDelegateCellY = -1;

        setAlwaysDrawnWithCacheEnabled(false);

        Resources res = getResources();

        mBackground = getContext().getDrawable(R.drawable.bg_celllayout);
        mBackground.setCallback(this);
        mBackground.setAlpha(0);

        mGridColor = Themes.getAttrColor(getContext(), R.attr.workspaceAccentColor);
        mGridVisualizationRoundingRadius =
                res.getDimensionPixelSize(R.dimen.grid_visualization_rounding_radius);
        mReorderPreviewAnimationMagnitude = (REORDER_PREVIEW_MAGNITUDE * deviceProfile.iconSizePx);

        // Initialize the data structures used for the drag visualization.
        mEaseOutInterpolator = Interpolators.DEACCEL_2_5; // Quint ease out
        mDragCell[0] = mDragCell[1] = -1;
        mDragCellSpan[0] = mDragCellSpan[1] = -1;
        for (int i = 0; i < mDragOutlines.length; i++) {
            mDragOutlines[i] = new CellLayoutLayoutParams(0, 0, 0, 0, -1);
        }
        mDragOutlinePaint.setColor(Themes.getAttrColor(context, R.attr.workspaceTextColor));

        // When dragging things around the home screens, we show a green outline of
        // where the item will land. The outlines gradually fade out, leaving a trail
        // behind the drag path.
        // Set up all the animations that are used to implement this fading.
        final int duration = res.getInteger(R.integer.config_dragOutlineFadeTime);
        final float fromAlphaValue = 0;
        final float toAlphaValue = (float)res.getInteger(R.integer.config_dragOutlineMaxAlpha);

        Arrays.fill(mDragOutlineAlphas, fromAlphaValue);

        for (int i = 0; i < mDragOutlineAnims.length; i++) {
            final InterruptibleInOutAnimator anim =
                    new InterruptibleInOutAnimator(duration, fromAlphaValue, toAlphaValue);
            anim.getAnimator().setInterpolator(mEaseOutInterpolator);
            final int thisIndex = i;
            anim.getAnimator().addUpdateListener(new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    // If an animation is started and then stopped very quickly, we can still
                    // get spurious updates we've cleared the tag. Guard against this.
                    mDragOutlineAlphas[thisIndex] = (Float) animation.getAnimatedValue();
                    CellLayout.this.invalidate();
                }
            });
            // The animation holds a reference to the drag outline bitmap as long is it's
            // running. This way the bitmap can be GCed when the animations are complete.
            mDragOutlineAnims[i] = anim;
        }

        mShortcutsAndWidgets = new ShortcutAndWidgetContainer(context, mContainerType);
        mShortcutsAndWidgets.setCellDimensions(mCellWidth, mCellHeight, mCountX, mCountY,
                mBorderSpace);
        addView(mShortcutsAndWidgets);
    }

    /**
     * Sets or clears a delegate used for accessible drag and drop
     */
    public void setDragAndDropAccessibilityDelegate(DragAndDropAccessibilityDelegate delegate) {
        setOnClickListener(delegate);
        ViewCompat.setAccessibilityDelegate(this, delegate);

        mTouchHelper = delegate;
        int accessibilityFlag = mTouchHelper != null
                ? IMPORTANT_FOR_ACCESSIBILITY_YES : IMPORTANT_FOR_ACCESSIBILITY_NO;
        setImportantForAccessibility(accessibilityFlag);
        getShortcutsAndWidgets().setImportantForAccessibility(accessibilityFlag);

        // ExploreByTouchHelper sets focusability. Clear it when the delegate is cleared.
        setFocusable(delegate != null);
        // Invalidate the accessibility hierarchy
        if (getParent() != null) {
            getParent().notifySubtreeAccessibilityStateChanged(
                    this, this, AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE);
        }
    }

    /**
     * Returns the currently set accessibility delegate
     */
    public DragAndDropAccessibilityDelegate getDragAndDropAccessibilityDelegate() {
        return mTouchHelper;
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        // Always attempt to dispatch hover events to accessibility first.
        if (mTouchHelper != null && mTouchHelper.dispatchHoverEvent(event)) {
            return true;
        }
        return super.dispatchHoverEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return mTouchHelper != null
                || (mInterceptTouchListener != null && mInterceptTouchListener.onTouch(this, ev));
    }

    public void enableHardwareLayer(boolean hasLayer) {
        mShortcutsAndWidgets.setLayerType(hasLayer ? LAYER_TYPE_HARDWARE : LAYER_TYPE_NONE, sPaint);
    }

    public boolean isHardwareLayerEnabled() {
        return mShortcutsAndWidgets.getLayerType() == LAYER_TYPE_HARDWARE;
    }

    /**
     * Change sizes of cells
     *
     * @param width  the new width of the cells
     * @param height the new height of the cells
     */
    public void setCellDimensions(int width, int height) {
        mFixedCellWidth = mCellWidth = width;
        mFixedCellHeight = mCellHeight = height;
        mShortcutsAndWidgets.setCellDimensions(mCellWidth, mCellHeight, mCountX, mCountY,
                mBorderSpace);
    }

    private void resetCellSizeInternal(DeviceProfile deviceProfile) {
        switch (mContainerType) {
            case FOLDER:
                mBorderSpace = new Point(deviceProfile.folderCellLayoutBorderSpacePx,
                        deviceProfile.folderCellLayoutBorderSpacePx);
                break;
            case HOTSEAT:
                mBorderSpace = new Point(deviceProfile.hotseatBorderSpace,
                        deviceProfile.hotseatBorderSpace);
                break;
            case WORKSPACE:
            default:
                mBorderSpace = new Point(deviceProfile.cellLayoutBorderSpacePx);
                break;
        }

        mCellWidth = mCellHeight = -1;
        mFixedCellWidth = mFixedCellHeight = -1;
    }

    /**
     * Reset the cell sizes and border space
     */
    public void resetCellSize(DeviceProfile deviceProfile) {
        resetCellSizeInternal(deviceProfile);
        requestLayout();
    }

    public void setGridSize(int x, int y) {
        mCountX = x;
        mCountY = y;
        mOccupied = new GridOccupancy(mCountX, mCountY);
        mTmpOccupied = new GridOccupancy(mCountX, mCountY);
        mShortcutsAndWidgets.setCellDimensions(mCellWidth, mCellHeight, mCountX, mCountY,
                mBorderSpace);
        requestLayout();
    }

    // Set whether or not to invert the layout horizontally if the layout is in RTL mode.
    public void setInvertIfRtl(boolean invert) {
        mShortcutsAndWidgets.setInvertIfRtl(invert);
    }

    public void setDropPending(boolean pending) {
        mDropPending = pending;
    }

    public boolean isDropPending() {
        return mDropPending;
    }

    void setIsDragOverlapping(boolean isDragOverlapping) {
        if (mIsDragOverlapping != isDragOverlapping) {
            mIsDragOverlapping = isDragOverlapping;
            mBackground.setState(mIsDragOverlapping
                    ? BACKGROUND_STATE_ACTIVE : BACKGROUND_STATE_DEFAULT);
            invalidate();
        }
    }

    @Override
    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        ParcelableSparseArray jail = getJailedArray(container);
        super.dispatchSaveInstanceState(jail);
        container.put(R.id.cell_layout_jail_id, jail);
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        super.dispatchRestoreInstanceState(getJailedArray(container));
    }

    /**
     * Wrap the SparseArray in another Parcelable so that the item ids do not conflict with our
     * our internal resource ids
     */
    private ParcelableSparseArray getJailedArray(SparseArray<Parcelable> container) {
        final Parcelable parcelable = container.get(R.id.cell_layout_jail_id);
        return parcelable instanceof ParcelableSparseArray ?
                (ParcelableSparseArray) parcelable : new ParcelableSparseArray();
    }

    public boolean getIsDragOverlapping() {
        return mIsDragOverlapping;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // When we're large, we are either drawn in a "hover" state (ie when dragging an item to
        // a neighboring page) or with just a normal background (if backgroundAlpha > 0.0f)
        // When we're small, we are either drawn normally or in the "accepts drops" state (during
        // a drag). However, we also drag the mini hover background *over* one of those two
        // backgrounds
        if (mBackground.getAlpha() > 0) {
            mBackground.draw(canvas);
        }

        if (DEBUG_VISUALIZE_OCCUPIED) {
            Rect cellBounds = new Rect();
            // Will contain the bounds of the cell including spacing between cells.
            Rect cellBoundsWithSpacing = new Rect();
            int[] targetCell = new int[2];
            int[] cellCenter = new int[2];
            Paint debugPaint = new Paint();
            debugPaint.setStrokeWidth(Utilities.dpToPx(1));
            for (int x = 0; x < mCountX; x++) {
                for (int y = 0; y < mCountY; y++) {
                    if (!mOccupied.cells[x][y]) {
                        continue;
                    }
                    targetCell[0] = x;
                    targetCell[1] = y;

                    boolean canCreateFolder = canCreateFolder(getChildAt(x, y));
                    cellToRect(x, y, 1, 1, cellBounds);
                    cellBoundsWithSpacing.set(cellBounds);
                    cellBoundsWithSpacing.inset(-mBorderSpace.x / 2, -mBorderSpace.y / 2);
                    getWorkspaceCellVisualCenter(x, y, cellCenter);

                    canvas.save();
                    canvas.clipRect(cellBoundsWithSpacing);

                    // Draw reorder drag target.
                    debugPaint.setColor(Color.RED);
                    canvas.drawCircle(cellCenter[0], cellCenter[1],
                            getReorderRadius(targetCell, 1, 1), debugPaint);

                    // Draw folder creation drag target.
                    if (canCreateFolder) {
                        debugPaint.setColor(Color.GREEN);
                        canvas.drawCircle(cellCenter[0], cellCenter[1],
                                getFolderCreationRadius(targetCell), debugPaint);
                    }

                    canvas.restore();
                }
            }
        }

        for (int i = 0; i < mDelegatedCellDrawings.size(); i++) {
            DelegatedCellDrawing cellDrawing = mDelegatedCellDrawings.get(i);
            cellToPoint(cellDrawing.mDelegateCellX, cellDrawing.mDelegateCellY, mTempLocation);
            canvas.save();
            canvas.translate(mTempLocation[0], mTempLocation[1]);
            cellDrawing.drawUnderItem(canvas);
            canvas.restore();
        }

        if (mFolderLeaveBehind.mDelegateCellX >= 0 && mFolderLeaveBehind.mDelegateCellY >= 0) {
            cellToPoint(mFolderLeaveBehind.mDelegateCellX,
                    mFolderLeaveBehind.mDelegateCellY, mTempLocation);
            canvas.save();
            canvas.translate(mTempLocation[0], mTempLocation[1]);
            mFolderLeaveBehind.drawLeaveBehind(canvas);
            canvas.restore();
        }

        if (mVisualizeCells || mVisualizeDropLocation) {
            visualizeGrid(canvas);
        }
    }

    /**
     * Returns whether dropping an icon on the given View can create (or add to) a folder.
     */
    private boolean canCreateFolder(View child) {
        return child instanceof DraggableView
                && ((DraggableView) child).getViewType() == DRAGGABLE_ICON;
    }

    /**
     * Indicates the progress of the Workspace entering the SpringLoaded state; allows the
     * CellLayout to update various visuals for this state.
     *
     * @param progress
     */
    public void setSpringLoadedProgress(float progress) {
        if (Float.compare(progress, mSpringLoadedProgress) != 0) {
            mSpringLoadedProgress = progress;
            if (!SHOW_HOME_GARDENING.get()) {
                updateBgAlpha();
            }
            setGridAlpha(progress);
        }
    }

    /**
     * See setSpringLoadedProgress
     * @return progress
     */
    public float getSpringLoadedProgress() {
        return mSpringLoadedProgress;
    }

    private void updateBgAlpha() {
        mBackground.setAlpha((int) (mSpringLoadedProgress * 255));
    }

    /**
     * Set the progress of this page's scroll
     *
     * @param progress 0 if the screen is centered, +/-1 if it is to the right / left respectively
     */
    public void setScrollProgress(float progress) {
        if (Float.compare(Math.abs(progress), mScrollProgress) != 0) {
            mScrollProgress = Math.abs(progress);
            if (!SHOW_HOME_GARDENING.get()) {
                updateBgAlpha();
            }
        }
    }

    private void setGridAlpha(float gridAlpha) {
        if (Float.compare(gridAlpha, mGridAlpha) != 0) {
            mGridAlpha = gridAlpha;
            invalidate();
        }
    }

    protected void visualizeGrid(Canvas canvas) {
        DeviceProfile dp = mActivity.getDeviceProfile();
        int paddingX = Math.min((mCellWidth - dp.iconSizePx) / 2, dp.gridVisualizationPaddingX);
        int paddingY = Math.min((mCellHeight - dp.iconSizePx) / 2, dp.gridVisualizationPaddingY);
        mVisualizeGridRect.set(paddingX, paddingY,
                mCellWidth - paddingX,
                mCellHeight - paddingY);

        mVisualizeGridPaint.setStrokeWidth(8);
        int paintAlpha = (int) (120 * mGridAlpha);
        mVisualizeGridPaint.setColor(ColorUtils.setAlphaComponent(mGridColor, paintAlpha));

        if (mVisualizeCells) {
            for (int i = 0; i < mCountX; i++) {
                for (int j = 0; j < mCountY; j++) {
                    int transX = i * mCellWidth + (i * mBorderSpace.x) + getPaddingLeft()
                            + paddingX;
                    int transY = j * mCellHeight + (j * mBorderSpace.y) + getPaddingTop()
                            + paddingY;

                    mVisualizeGridRect.offsetTo(transX, transY);
                    mVisualizeGridPaint.setStyle(Paint.Style.FILL);
                    canvas.drawRoundRect(mVisualizeGridRect, mGridVisualizationRoundingRadius,
                            mGridVisualizationRoundingRadius, mVisualizeGridPaint);
                }
            }
        }

        if (mVisualizeDropLocation && !SHOW_HOME_GARDENING.get()) {
            for (int i = 0; i < mDragOutlines.length; i++) {
                final float alpha = mDragOutlineAlphas[i];
                if (alpha <= 0) continue;

                mVisualizeGridPaint.setAlpha(255);
                int x = mDragOutlines[i].cellX;
                int y = mDragOutlines[i].cellY;
                int spanX = mDragOutlines[i].cellHSpan;
                int spanY = mDragOutlines[i].cellVSpan;

                // TODO b/194414754 clean this up, reconcile with cellToRect
                mVisualizeGridRect.set(paddingX, paddingY,
                        mCellWidth * spanX + mBorderSpace.x * (spanX - 1) - paddingX,
                        mCellHeight * spanY + mBorderSpace.y * (spanY - 1) - paddingY);

                int transX = x * mCellWidth + (x * mBorderSpace.x)
                        + getPaddingLeft() + paddingX;
                int transY = y * mCellHeight + (y * mBorderSpace.y)
                        + getPaddingTop() + paddingY;

                mVisualizeGridRect.offsetTo(transX, transY);

                mVisualizeGridPaint.setStyle(Paint.Style.STROKE);
                mVisualizeGridPaint.setColor(Color.argb((int) (alpha),
                        Color.red(mGridColor), Color.green(mGridColor), Color.blue(mGridColor)));

                canvas.drawRoundRect(mVisualizeGridRect, mGridVisualizationRoundingRadius,
                        mGridVisualizationRoundingRadius, mVisualizeGridPaint);
            }
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        for (int i = 0; i < mDelegatedCellDrawings.size(); i++) {
            DelegatedCellDrawing bg = mDelegatedCellDrawings.get(i);
            cellToPoint(bg.mDelegateCellX, bg.mDelegateCellY, mTempLocation);
            canvas.save();
            canvas.translate(mTempLocation[0], mTempLocation[1]);
            bg.drawOverItem(canvas);
            canvas.restore();
        }
    }

    /**
     * Add Delegated cell drawing
     */
    public void addDelegatedCellDrawing(DelegatedCellDrawing bg) {
        mDelegatedCellDrawings.add(bg);
    }

    /**
     * Remove item from DelegatedCellDrawings
     */
    public void removeDelegatedCellDrawing(DelegatedCellDrawing bg) {
        mDelegatedCellDrawings.remove(bg);
    }

    public void setFolderLeaveBehindCell(int x, int y) {
        View child = getChildAt(x, y);
        mFolderLeaveBehind.setup(getContext(), mActivity, null,
                child.getMeasuredWidth(), child.getPaddingTop());

        mFolderLeaveBehind.mDelegateCellX = x;
        mFolderLeaveBehind.mDelegateCellY = y;
        invalidate();
    }

    public void clearFolderLeaveBehind() {
        mFolderLeaveBehind.mDelegateCellX = -1;
        mFolderLeaveBehind.mDelegateCellY = -1;
        invalidate();
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    public void restoreInstanceState(SparseArray<Parcelable> states) {
        try {
            dispatchRestoreInstanceState(states);
        } catch (IllegalArgumentException ex) {
            if (FeatureFlags.IS_STUDIO_BUILD) {
                throw ex;
            }
            // Mismatched viewId / viewType preventing restore. Skip restore on production builds.
            Log.e(TAG, "Ignoring an error while restoring a view instance state", ex);
        }
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();

        // Cancel long press for all children
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            child.cancelLongPress();
        }
    }

    public void setOnInterceptTouchListener(View.OnTouchListener listener) {
        mInterceptTouchListener = listener;
    }

    public int getCountX() {
        return mCountX;
    }

    public int getCountY() {
        return mCountY;
    }

    public boolean acceptsWidget() {
        return mContainerType == WORKSPACE;
    }

    /**
     * Adds the given view to the CellLayout
     *
     * @param child view to add.
     * @param index index of the CellLayout children where to add the view.
     * @param childId id of the view.
     * @param params represent the logic of the view on the CellLayout.
     * @param markCells if the occupied cells should be marked or not
     * @return if adding the view was successful
     */
    public boolean addViewToCellLayout(View child, int index, int childId,
            CellLayoutLayoutParams params, boolean markCells) {
        final CellLayoutLayoutParams lp = params;

        // Hotseat icons - remove text
        if (child instanceof BubbleTextView) {
            BubbleTextView bubbleChild = (BubbleTextView) child;
            bubbleChild.setTextVisibility(mContainerType != HOTSEAT);
        }

        child.setScaleX(mChildScale);
        child.setScaleY(mChildScale);

        // Generate an id for each view, this assumes we have at most 256x256 cells
        // per workspace screen
        if (lp.cellX >= 0 && lp.cellX <= mCountX - 1 && lp.cellY >= 0 && lp.cellY <= mCountY - 1) {
            // If the horizontal or vertical span is set to -1, it is taken to
            // mean that it spans the extent of the CellLayout
            if (lp.cellHSpan < 0) lp.cellHSpan = mCountX;
            if (lp.cellVSpan < 0) lp.cellVSpan = mCountY;

            child.setId(childId);
            if (LOGD) {
                Log.d(TAG, "Adding view to ShortcutsAndWidgetsContainer: " + child);
            }
            mShortcutsAndWidgets.addView(child, index, lp);

            if (markCells) markCellsAsOccupiedForView(child);

            return true;
        }
        return false;
    }

    @Override
    public void removeAllViews() {
        mOccupied.clear();
        mShortcutsAndWidgets.removeAllViews();
    }

    @Override
    public void removeAllViewsInLayout() {
        if (mShortcutsAndWidgets.getChildCount() > 0) {
            mOccupied.clear();
            mShortcutsAndWidgets.removeAllViewsInLayout();
        }
    }

    @Override
    public void removeView(View view) {
        markCellsAsUnoccupiedForView(view);
        mShortcutsAndWidgets.removeView(view);
    }

    @Override
    public void removeViewAt(int index) {
        markCellsAsUnoccupiedForView(mShortcutsAndWidgets.getChildAt(index));
        mShortcutsAndWidgets.removeViewAt(index);
    }

    @Override
    public void removeViewInLayout(View view) {
        markCellsAsUnoccupiedForView(view);
        mShortcutsAndWidgets.removeViewInLayout(view);
    }

    @Override
    public void removeViews(int start, int count) {
        for (int i = start; i < start + count; i++) {
            markCellsAsUnoccupiedForView(mShortcutsAndWidgets.getChildAt(i));
        }
        mShortcutsAndWidgets.removeViews(start, count);
    }

    @Override
    public void removeViewsInLayout(int start, int count) {
        for (int i = start; i < start + count; i++) {
            markCellsAsUnoccupiedForView(mShortcutsAndWidgets.getChildAt(i));
        }
        mShortcutsAndWidgets.removeViewsInLayout(start, count);
    }

    /**
     * Given a point, return the cell that strictly encloses that point
     * @param x X coordinate of the point
     * @param y Y coordinate of the point
     * @param result Array of 2 ints to hold the x and y coordinate of the cell
     */
    public void pointToCellExact(int x, int y, int[] result) {
        final int hStartPadding = getPaddingLeft();
        final int vStartPadding = getPaddingTop();

        result[0] = (x - hStartPadding) / (mCellWidth + mBorderSpace.x);
        result[1] = (y - vStartPadding) / (mCellHeight + mBorderSpace.y);

        final int xAxis = mCountX;
        final int yAxis = mCountY;

        if (result[0] < 0) result[0] = 0;
        if (result[0] >= xAxis) result[0] = xAxis - 1;
        if (result[1] < 0) result[1] = 0;
        if (result[1] >= yAxis) result[1] = yAxis - 1;
    }

    /**
     * Given a cell coordinate, return the point that represents the upper left corner of that cell
     *
     * @param cellX X coordinate of the cell
     * @param cellY Y coordinate of the cell
     *
     * @param result Array of 2 ints to hold the x and y coordinate of the point
     */
    void cellToPoint(int cellX, int cellY, int[] result) {
        cellToRect(cellX, cellY, 1, 1, mTempRect);
        result[0] = mTempRect.left;
        result[1] = mTempRect.top;
    }

    /**
     * Given a cell coordinate, return the point that represents the center of the cell
     *
     * @param cellX X coordinate of the cell
     * @param cellY Y coordinate of the cell
     *
     * @param result Array of 2 ints to hold the x and y coordinate of the point
     */
    void cellToCenterPoint(int cellX, int cellY, int[] result) {
        regionToCenterPoint(cellX, cellY, 1, 1, result);
    }

    /**
     * Given a cell coordinate and span return the point that represents the center of the region
     *
     * @param cellX X coordinate of the cell
     * @param cellY Y coordinate of the cell
     *
     * @param result Array of 2 ints to hold the x and y coordinate of the point
     */
    void regionToCenterPoint(int cellX, int cellY, int spanX, int spanY, int[] result) {
        cellToRect(cellX, cellY, spanX, spanY, mTempRect);
        result[0] = mTempRect.centerX();
        result[1] = mTempRect.centerY();
    }

    /**
     * Returns the distance between the given coordinate and the visual center of the given cell.
     */
    public float getDistanceFromWorkspaceCellVisualCenter(float x, float y, int[] cell) {
        getWorkspaceCellVisualCenter(cell[0], cell[1], mTmpPoint);
        return (float) Math.hypot(x - mTmpPoint[0], y - mTmpPoint[1]);
    }

    private void getWorkspaceCellVisualCenter(int cellX, int cellY, int[] outPoint) {
        View child = getChildAt(cellX, cellY);
        if (child instanceof DraggableView) {
            DraggableView draggableChild = (DraggableView) child;
            if (draggableChild.getViewType() == DRAGGABLE_ICON) {
                cellToPoint(cellX, cellY, outPoint);
                draggableChild.getWorkspaceVisualDragBounds(mTempRect);
                mTempRect.offset(outPoint[0], outPoint[1]);
                outPoint[0] = mTempRect.centerX();
                outPoint[1] = mTempRect.centerY();
                return;
            }
        }
        cellToCenterPoint(cellX, cellY, outPoint);
    }

    /**
     * Returns the max distance from the center of a cell that can accept a drop to create a folder.
     */
    public float getFolderCreationRadius(int[] targetCell) {
        DeviceProfile grid = mActivity.getDeviceProfile();
        float iconVisibleRadius = ICON_VISIBLE_AREA_FACTOR * grid.iconSizePx / 2;
        // Halfway between reorder radius and icon.
        return (getReorderRadius(targetCell, 1, 1) + iconVisibleRadius) / 2;
    }

    /**
     * Returns the max distance from the center of a cell that will start to reorder on drag over.
     */
    public float getReorderRadius(int[] targetCell, int spanX, int spanY) {
        int[] centerPoint = mTmpPoint;
        getWorkspaceCellVisualCenter(targetCell[0], targetCell[1], centerPoint);

        Rect cellBoundsWithSpacing = mTempRect;
        cellToRect(targetCell[0], targetCell[1], spanX, spanY, cellBoundsWithSpacing);
        cellBoundsWithSpacing.inset(-mBorderSpace.x / 2, -mBorderSpace.y / 2);

        if (canCreateFolder(getChildAt(targetCell[0], targetCell[1])) && spanX == 1 && spanY == 1) {
            // Take only the circle in the smaller dimension, to ensure we don't start reordering
            // too soon before accepting a folder drop.
            int minRadius = centerPoint[0] - cellBoundsWithSpacing.left;
            minRadius = Math.min(minRadius, centerPoint[1] - cellBoundsWithSpacing.top);
            minRadius = Math.min(minRadius, cellBoundsWithSpacing.right - centerPoint[0]);
            minRadius = Math.min(minRadius, cellBoundsWithSpacing.bottom - centerPoint[1]);
            return minRadius;
        }
        // Take up the entire cell, including space between this cell and the adjacent ones.
        // Multiply by span to scale radius
        return (float) Math.hypot(spanX * cellBoundsWithSpacing.width() / 2f,
                spanY * cellBoundsWithSpacing.height() / 2f);
    }

    public int getCellWidth() {
        return mCellWidth;
    }

    public int getCellHeight() {
        return mCellHeight;
    }

    public void setFixedSize(int width, int height) {
        mFixedWidth = width;
        mFixedHeight = height;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize =  MeasureSpec.getSize(heightMeasureSpec);
        int childWidthSize = widthSize - (getPaddingLeft() + getPaddingRight());
        int childHeightSize = heightSize - (getPaddingTop() + getPaddingBottom());

        if (mFixedCellWidth < 0 || mFixedCellHeight < 0) {
            int cw = DeviceProfile.calculateCellWidth(childWidthSize, mBorderSpace.x,
                    mCountX);
            int ch = DeviceProfile.calculateCellHeight(childHeightSize, mBorderSpace.y,
                    mCountY);
            if (cw != mCellWidth || ch != mCellHeight) {
                mCellWidth = cw;
                mCellHeight = ch;
                mShortcutsAndWidgets.setCellDimensions(mCellWidth, mCellHeight, mCountX, mCountY,
                        mBorderSpace);
            }
        }

        int newWidth = childWidthSize;
        int newHeight = childHeightSize;
        if (mFixedWidth > 0 && mFixedHeight > 0) {
            newWidth = mFixedWidth;
            newHeight = mFixedHeight;
        } else if (widthSpecMode == MeasureSpec.UNSPECIFIED || heightSpecMode == MeasureSpec.UNSPECIFIED) {
            throw new RuntimeException("CellLayout cannot have UNSPECIFIED dimensions");
        }

        mShortcutsAndWidgets.measure(
                MeasureSpec.makeMeasureSpec(newWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(newHeight, MeasureSpec.EXACTLY));

        int maxWidth = mShortcutsAndWidgets.getMeasuredWidth();
        int maxHeight = mShortcutsAndWidgets.getMeasuredHeight();
        if (mFixedWidth > 0 && mFixedHeight > 0) {
            setMeasuredDimension(maxWidth, maxHeight);
        } else {
            setMeasuredDimension(widthSize, heightSize);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int left = getPaddingLeft();
        left += (int) Math.ceil(getUnusedHorizontalSpace() / 2f);
        int right = r - l - getPaddingRight();
        right -= (int) Math.ceil(getUnusedHorizontalSpace() / 2f);

        int top = getPaddingTop();
        int bottom = b - t - getPaddingBottom();

        // Expand the background drawing bounds by the padding baked into the background drawable
        mBackground.getPadding(mTempRect);
        mBackground.setBounds(
                left - mTempRect.left - getPaddingLeft(),
                top - mTempRect.top - getPaddingTop(),
                right + mTempRect.right + getPaddingRight(),
                bottom + mTempRect.bottom + getPaddingBottom());

        mShortcutsAndWidgets.layout(left, top, right, bottom);
    }

    /**
     * Returns the amount of space left over after subtracting padding and cells. This space will be
     * very small, a few pixels at most, and is a result of rounding down when calculating the cell
     * width in {@link DeviceProfile#calculateCellWidth(int, int, int)}.
     */
    public int getUnusedHorizontalSpace() {
        return getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - (mCountX * mCellWidth)
                - ((mCountX - 1) * mBorderSpace.x);
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || (who == mBackground);
    }

    public ShortcutAndWidgetContainer getShortcutsAndWidgets() {
        return mShortcutsAndWidgets;
    }

    public View getChildAt(int cellX, int cellY) {
        return mShortcutsAndWidgets.getChildAt(cellX, cellY);
    }

    public boolean animateChildToPosition(final View child, int cellX, int cellY, int duration,
            int delay, boolean permanent, boolean adjustOccupied) {
        ShortcutAndWidgetContainer clc = getShortcutsAndWidgets();

        if (clc.indexOfChild(child) != -1 && (child instanceof Reorderable)) {
            final CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            final ItemInfo info = (ItemInfo) child.getTag();
            final Reorderable item = (Reorderable) child;

            // We cancel any existing animations
            if (mReorderAnimators.containsKey(lp)) {
                mReorderAnimators.get(lp).cancel();
                mReorderAnimators.remove(lp);
            }


            if (adjustOccupied) {
                GridOccupancy occupied = permanent ? mOccupied : mTmpOccupied;
                occupied.markCells(lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan, false);
                occupied.markCells(cellX, cellY, lp.cellHSpan, lp.cellVSpan, true);
            }

            // Compute the new x and y position based on the new cellX and cellY
            // We leverage the actual layout logic in the layout params and hence need to modify
            // state and revert that state.
            final int oldX = lp.x;
            final int oldY = lp.y;
            lp.isLockedToGrid = true;
            if (permanent) {
                lp.cellX = info.cellX = cellX;
                lp.cellY = info.cellY = cellY;
            } else {
                lp.tmpCellX = cellX;
                lp.tmpCellY = cellY;
            }
            clc.setupLp(child);
            final int newX = lp.x;
            final int newY = lp.y;
            lp.x = oldX;
            lp.y = oldY;
            lp.isLockedToGrid = false;
            // End compute new x and y

            item.getReorderPreviewOffset(mTmpPointF);
            final float initPreviewOffsetX = mTmpPointF.x;
            final float initPreviewOffsetY = mTmpPointF.y;
            final float finalPreviewOffsetX = newX - oldX;
            final float finalPreviewOffsetY = newY - oldY;


            // Exit early if we're not actually moving the view
            if (finalPreviewOffsetX == 0 && finalPreviewOffsetY == 0
                    && initPreviewOffsetX == 0 && initPreviewOffsetY == 0) {
                lp.isLockedToGrid = true;
                return true;
            }

            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(duration);
            mReorderAnimators.put(lp, va);

            va.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float r = (Float) animation.getAnimatedValue();
                    float x = (1 - r) * initPreviewOffsetX + r * finalPreviewOffsetX;
                    float y = (1 - r) * initPreviewOffsetY + r * finalPreviewOffsetY;
                    item.setReorderPreviewOffset(x, y);
                }
            });
            va.addListener(new AnimatorListenerAdapter() {
                boolean cancelled = false;
                public void onAnimationEnd(Animator animation) {
                    // If the animation was cancelled, it means that another animation
                    // has interrupted this one, and we don't want to lock the item into
                    // place just yet.
                    if (!cancelled) {
                        lp.isLockedToGrid = true;
                        item.setReorderPreviewOffset(0, 0);
                        child.requestLayout();
                    }
                    if (mReorderAnimators.containsKey(lp)) {
                        mReorderAnimators.remove(lp);
                    }
                }
                public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                }
            });
            va.setStartDelay(delay);
            va.start();
            return true;
        }
        return false;
    }

    void visualizeDropLocation(int cellX, int cellY, int spanX, int spanY,
            DropTarget.DragObject dragObject) {
        if (mDragCell[0] != cellX || mDragCell[1] != cellY || mDragCellSpan[0] != spanX
                || mDragCellSpan[1] != spanY) {
            mDragCell[0] = cellX;
            mDragCell[1] = cellY;
            mDragCellSpan[0] = spanX;
            mDragCellSpan[1] = spanY;

            // Apply color extraction on a widget when dragging.
            applyColorExtractionOnWidget(dragObject, mDragCell, spanX, spanY);

            final int oldIndex = mDragOutlineCurrent;
            mDragOutlineAnims[oldIndex].animateOut();
            mDragOutlineCurrent = (oldIndex + 1) % mDragOutlines.length;

            CellLayoutLayoutParams cell = mDragOutlines[mDragOutlineCurrent];
            cell.cellX = cellX;
            cell.cellY = cellY;
            cell.cellHSpan = spanX;
            cell.cellVSpan = spanY;

            mDragOutlineAnims[mDragOutlineCurrent].animateIn();
            invalidate();

            if (dragObject.stateAnnouncer != null) {
                dragObject.stateAnnouncer.announce(getItemMoveDescription(cellX, cellY));
            }

        }
    }

    /** Applies the local color extraction to a dragging widget object. */
    private void applyColorExtractionOnWidget(DropTarget.DragObject dragObject, int[] targetCell,
            int spanX, int spanY) {
        // Apply local extracted color if the DragView is an AppWidgetHostViewDrawable.
        View view = dragObject.dragView.getContentView();
        if (view instanceof LauncherAppWidgetHostView) {
            int screenId = getWorkspace().getIdForScreen(this);
            cellToRect(targetCell[0], targetCell[1], spanX, spanY, mTempRect);

            ((LauncherAppWidgetHostView) view).handleDrag(mTempRect, this, screenId);
        }
    }

    @SuppressLint("StringFormatMatches")
    public String getItemMoveDescription(int cellX, int cellY) {
        if (mContainerType == HOTSEAT) {
            return getContext().getString(R.string.move_to_hotseat_position,
                    Math.max(cellX, cellY) + 1);
        } else {
            Workspace<?> workspace = getWorkspace();
            int row = cellY + 1;
            int col = workspace.mIsRtl ? mCountX - cellX : cellX + 1;
            int panelCount = workspace.getPanelCount();
            int screenId = workspace.getIdForScreen(this);
            int pageIndex = workspace.getPageIndexForScreenId(screenId);
            if (panelCount > 1) {
                // Increment the column if the target is on the right side of a two panel home
                col += (pageIndex % panelCount) * mCountX;
            }
            return getContext().getString(R.string.move_to_empty_cell_description, row, col,
                    workspace.getPageDescription(pageIndex));
        }
    }

    private Workspace<?> getWorkspace() {
        return Launcher.cast(mActivity).getWorkspace();
    }

    public void clearDragOutlines() {
        final int oldIndex = mDragOutlineCurrent;
        mDragOutlineAnims[oldIndex].animateOut();
        mDragCell[0] = mDragCell[1] = -1;
    }

    /**
     * Find a vacant area that will fit the given bounds nearest the requested
     * cell location. Uses Euclidean distance to score multiple vacant areas.
     *
     * @param pixelX The X location at which you want to search for a vacant area.
     * @param pixelY The Y location at which you want to search for a vacant area.
     * @param minSpanX The minimum horizontal span required
     * @param minSpanY The minimum vertical span required
     * @param spanX Horizontal span of the object.
     * @param spanY Vertical span of the object.
     * @param result Array in which to place the result, or null (in which case a new array will
     *        be allocated)
     * @return The X, Y cell of a vacant area that can contain this object,
     *         nearest the requested location.
     */
    int[] findNearestVacantArea(int pixelX, int pixelY, int minSpanX, int minSpanY, int spanX,
            int spanY, int[] result, int[] resultSpan) {
        return findNearestArea(pixelX, pixelY, minSpanX, minSpanY, spanX, spanY, false,
                result, resultSpan);
    }

    /**
     * Find a vacant area that will fit the given bounds nearest the requested
     * cell location. Uses Euclidean distance to score multiple vacant areas.
     * @param relativeXPos The X location relative to the Cell layout at which you want to search
     *                     for a vacant area.
     * @param relativeYPos The Y location relative to the Cell layout at which you want to search
     *                     for a vacant area.
     * @param minSpanX The minimum horizontal span required
     * @param minSpanY The minimum vertical span required
     * @param spanX Horizontal span of the object.
     * @param spanY Vertical span of the object.
     * @param ignoreOccupied If true, the result can be an occupied cell
     * @param result Array in which to place the result, or null (in which case a new array will
     *        be allocated)
     * @return The X, Y cell of a vacant area that can contain this object,
     *         nearest the requested location.
     */
    private int[] findNearestArea(int relativeXPos, int relativeYPos, int minSpanX, int minSpanY,
            int spanX, int spanY, boolean ignoreOccupied, int[] result, int[] resultSpan) {
        // For items with a spanX / spanY > 1, the passed in point (relativeXPos, relativeYPos)
        // corresponds to the center of the item, but we are searching based on the top-left cell,
        // so we translate the point over to correspond to the top-left.
        relativeXPos = (int) (relativeXPos - (mCellWidth + mBorderSpace.x) * (spanX - 1) / 2f);
        relativeYPos = (int) (relativeYPos - (mCellHeight + mBorderSpace.y) * (spanY - 1) / 2f);

        // Keep track of best-scoring drop area
        final int[] bestXY = result != null ? result : new int[2];
        double bestDistance = Double.MAX_VALUE;
        final Rect bestRect = new Rect(-1, -1, -1, -1);
        final Stack<Rect> validRegions = new Stack<>();

        final int countX = mCountX;
        final int countY = mCountY;

        if (minSpanX <= 0 || minSpanY <= 0 || spanX <= 0 || spanY <= 0 ||
                spanX < minSpanX || spanY < minSpanY) {
            return bestXY;
        }

        for (int y = 0; y < countY - (minSpanY - 1); y++) {
            inner:
            for (int x = 0; x < countX - (minSpanX - 1); x++) {
                int ySize = -1;
                int xSize = -1;
                if (!ignoreOccupied) {
                    // First, let's see if this thing fits anywhere
                    for (int i = 0; i < minSpanX; i++) {
                        for (int j = 0; j < minSpanY; j++) {
                            if (mOccupied.cells[x + i][y + j]) {
                                continue inner;
                            }
                        }
                    }
                    xSize = minSpanX;
                    ySize = minSpanY;

                    // We know that the item will fit at _some_ acceptable size, now let's see
                    // how big we can make it. We'll alternate between incrementing x and y spans
                    // until we hit a limit.
                    boolean incX = true;
                    boolean hitMaxX = xSize >= spanX;
                    boolean hitMaxY = ySize >= spanY;
                    while (!(hitMaxX && hitMaxY)) {
                        if (incX && !hitMaxX) {
                            for (int j = 0; j < ySize; j++) {
                                if (x + xSize > countX -1 || mOccupied.cells[x + xSize][y + j]) {
                                    // We can't move out horizontally
                                    hitMaxX = true;
                                }
                            }
                            if (!hitMaxX) {
                                xSize++;
                            }
                        } else if (!hitMaxY) {
                            for (int i = 0; i < xSize; i++) {
                                if (y + ySize > countY - 1 || mOccupied.cells[x + i][y + ySize]) {
                                    // We can't move out vertically
                                    hitMaxY = true;
                                }
                            }
                            if (!hitMaxY) {
                                ySize++;
                            }
                        }
                        hitMaxX |= xSize >= spanX;
                        hitMaxY |= ySize >= spanY;
                        incX = !incX;
                    }
                }
                final int[] cellXY = mTmpPoint;
                cellToCenterPoint(x, y, cellXY);

                // We verify that the current rect is not a sub-rect of any of our previous
                // candidates. In this case, the current rect is disqualified in favour of the
                // containing rect.
                Rect currentRect = new Rect(x, y, x + xSize, y + ySize);
                boolean contained = false;
                for (Rect r : validRegions) {
                    if (r.contains(currentRect)) {
                        contained = true;
                        break;
                    }
                }
                validRegions.push(currentRect);
                double distance = Math.hypot(cellXY[0] - relativeXPos,  cellXY[1] - relativeYPos);

                if ((distance <= bestDistance && !contained) ||
                        currentRect.contains(bestRect)) {
                    bestDistance = distance;
                    bestXY[0] = x;
                    bestXY[1] = y;
                    if (resultSpan != null) {
                        resultSpan[0] = xSize;
                        resultSpan[1] = ySize;
                    }
                    bestRect.set(currentRect);
                }
            }
        }

        // Return -1, -1 if no suitable location found
        if (bestDistance == Double.MAX_VALUE) {
            bestXY[0] = -1;
            bestXY[1] = -1;
        }
        return bestXY;
    }

    private void copySolutionToTempState(ItemConfiguration solution, View dragView) {
        mTmpOccupied.clear();

        int childCount = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            if (child == dragView) continue;
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            CellAndSpan c = solution.map.get(child);
            if (c != null) {
                lp.tmpCellX = c.cellX;
                lp.tmpCellY = c.cellY;
                lp.cellHSpan = c.spanX;
                lp.cellVSpan = c.spanY;
                mTmpOccupied.markCells(c, true);
            }
        }
        mTmpOccupied.markCells(solution, true);
    }

    private void animateItemsToSolution(ItemConfiguration solution, View dragView, boolean
            commitDragView) {

        GridOccupancy occupied = DESTRUCTIVE_REORDER ? mOccupied : mTmpOccupied;
        occupied.clear();

        int childCount = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            if (child == dragView) continue;
            CellAndSpan c = solution.map.get(child);
            if (c != null) {
                animateChildToPosition(child, c.cellX, c.cellY, REORDER_ANIMATION_DURATION, 0,
                        DESTRUCTIVE_REORDER, false);
                occupied.markCells(c, true);
            }
        }
        if (commitDragView) {
            occupied.markCells(solution, true);
        }
    }


    // This method starts or changes the reorder preview animations
    private void beginOrAdjustReorderPreviewAnimations(ItemConfiguration solution,
            View dragView, int mode) {
        int childCount = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            if (child == dragView) continue;
            CellAndSpan c = solution.map.get(child);
            boolean skip = mode == ReorderPreviewAnimation.MODE_HINT && solution.intersectingViews
                    != null && !solution.intersectingViews.contains(child);


            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            if (c != null && !skip && (child instanceof Reorderable)) {
                ReorderPreviewAnimation rha = new ReorderPreviewAnimation((Reorderable) child,
                        mode, lp.cellX, lp.cellY, c.cellX, c.cellY, c.spanX, c.spanY);
                rha.animate();
            }
        }
    }

    private static final Property<ReorderPreviewAnimation, Float> ANIMATION_PROGRESS =
            new Property<ReorderPreviewAnimation, Float>(float.class, "animationProgress") {
                @Override
                public Float get(ReorderPreviewAnimation anim) {
                    return anim.animationProgress;
                }

                @Override
                public void set(ReorderPreviewAnimation anim, Float progress) {
                    anim.setAnimationProgress(progress);
                }
            };

    // Class which represents the reorder preview animations. These animations show that an item is
    // in a temporary state, and hint at where the item will return to.
    class ReorderPreviewAnimation {
        final Reorderable child;
        float finalDeltaX;
        float finalDeltaY;
        float initDeltaX;
        float initDeltaY;
        final float finalScale;
        float initScale;
        final int mode;
        boolean repeating = false;
        private static final int PREVIEW_DURATION = 300;
        private static final int HINT_DURATION = Workspace.REORDER_TIMEOUT;

        private static final float CHILD_DIVIDEND = 4.0f;

        public static final int MODE_HINT = 0;
        public static final int MODE_PREVIEW = 1;

        float animationProgress = 0;
        ValueAnimator a;

        public ReorderPreviewAnimation(Reorderable child, int mode, int cellX0, int cellY0,
                int cellX1, int cellY1, int spanX, int spanY) {
            regionToCenterPoint(cellX0, cellY0, spanX, spanY, mTmpPoint);
            final int x0 = mTmpPoint[0];
            final int y0 = mTmpPoint[1];
            regionToCenterPoint(cellX1, cellY1, spanX, spanY, mTmpPoint);
            final int x1 = mTmpPoint[0];
            final int y1 = mTmpPoint[1];
            final int dX = x1 - x0;
            final int dY = y1 - y0;

            this.child = child;
            this.mode = mode;
            finalDeltaX = 0;
            finalDeltaY = 0;

            child.getReorderBounceOffset(mTmpPointF);
            initDeltaX = mTmpPointF.x;
            initDeltaY = mTmpPointF.y;
            initScale = child.getReorderBounceScale();
            finalScale = mChildScale - (CHILD_DIVIDEND / child.getView().getWidth()) * initScale;

            int dir = mode == MODE_HINT ? -1 : 1;
            if (dX == dY && dX == 0) {
            } else {
                if (dY == 0) {
                    finalDeltaX = -dir * Math.signum(dX) * mReorderPreviewAnimationMagnitude;
                } else if (dX == 0) {
                    finalDeltaY = -dir * Math.signum(dY) * mReorderPreviewAnimationMagnitude;
                } else {
                    double angle = Math.atan( (float) (dY) / dX);
                    finalDeltaX = (int) (-dir * Math.signum(dX)
                            * Math.abs(Math.cos(angle) * mReorderPreviewAnimationMagnitude));
                    finalDeltaY = (int) (-dir * Math.signum(dY)
                            * Math.abs(Math.sin(angle) * mReorderPreviewAnimationMagnitude));
                }
            }
        }

        void setInitialAnimationValuesToBaseline() {
            initScale = mChildScale;
            initDeltaX = 0;
            initDeltaY = 0;
        }

        void animate() {
            boolean noMovement = (finalDeltaX == 0) && (finalDeltaY == 0);

            if (mShakeAnimators.containsKey(child)) {
                ReorderPreviewAnimation oldAnimation = mShakeAnimators.get(child);
                mShakeAnimators.remove(child);

                if (noMovement) {
                    // A previous animation for this item exists, and no new animation will exist.
                    // Finish the old animation smoothly.
                    oldAnimation.finishAnimation();
                    return;
                } else {
                    // A previous animation for this item exists, and a new one will exist. Stop
                    // the old animation in its tracks, and proceed with the new one.
                    oldAnimation.cancel();
                }
            }
            if (noMovement) {
                return;
            }

            ValueAnimator va = ObjectAnimator.ofFloat(this, ANIMATION_PROGRESS, 0, 1);
            a = va;

            // Animations are disabled in power save mode, causing the repeated animation to jump
            // spastically between beginning and end states. Since this looks bad, we don't repeat
            // the animation in power save mode.
            if (areAnimatorsEnabled()) {
                va.setRepeatMode(ValueAnimator.REVERSE);
                va.setRepeatCount(ValueAnimator.INFINITE);
            }

            va.setDuration(mode == MODE_HINT ? HINT_DURATION : PREVIEW_DURATION);
            va.setStartDelay((int) (Math.random() * 60));
            va.addListener(new AnimatorListenerAdapter() {
                public void onAnimationRepeat(Animator animation) {
                    // We make sure to end only after a full period
                    setInitialAnimationValuesToBaseline();
                    repeating = true;
                }
            });
            mShakeAnimators.put(child, this);
            va.start();
        }

        private void setAnimationProgress(float progress) {
            animationProgress = progress;
            float r1 = (mode == MODE_HINT && repeating) ? 1.0f : animationProgress;
            float x = r1 * finalDeltaX + (1 - r1) * initDeltaX;
            float y = r1 * finalDeltaY + (1 - r1) * initDeltaY;
            child.setReorderBounceOffset(x, y);
            float s = animationProgress * finalScale + (1 - animationProgress) * initScale;
            child.setReorderBounceScale(s);
        }

        private void cancel() {
            if (a != null) {
                a.cancel();
            }
        }

        /**
         * Smoothly returns the item to its baseline position / scale
         */
        @Thunk void finishAnimation() {
            if (a != null) {
                a.cancel();
            }

            setInitialAnimationValuesToBaseline();
            ValueAnimator va = ObjectAnimator.ofFloat(this, ANIMATION_PROGRESS,
                    animationProgress, 0);
            a = va;
            a.setInterpolator(DEACCEL_1_5);
            a.setDuration(REORDER_ANIMATION_DURATION);
            a.start();
        }
    }

    private void completeAndClearReorderPreviewAnimations() {
        for (ReorderPreviewAnimation a: mShakeAnimators.values()) {
            a.finishAnimation();
        }
        mShakeAnimators.clear();
    }

    private void commitTempPlacement(View dragView) {
        mTmpOccupied.copyTo(mOccupied);

        int screenId = getWorkspace().getIdForScreen(this);
        int container = Favorites.CONTAINER_DESKTOP;

        if (mContainerType == HOTSEAT) {
            screenId = -1;
            container = Favorites.CONTAINER_HOTSEAT;
        }

        int childCount = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            ItemInfo info = (ItemInfo) child.getTag();
            // We do a null check here because the item info can be null in the case of the
            // AllApps button in the hotseat.
            if (info != null && child != dragView) {
                final boolean requiresDbUpdate = (info.cellX != lp.tmpCellX
                        || info.cellY != lp.tmpCellY || info.spanX != lp.cellHSpan
                        || info.spanY != lp.cellVSpan);

                info.cellX = lp.cellX = lp.tmpCellX;
                info.cellY = lp.cellY = lp.tmpCellY;
                info.spanX = lp.cellHSpan;
                info.spanY = lp.cellVSpan;

                if (requiresDbUpdate) {
                    Launcher.cast(mActivity).getModelWriter().modifyItemInDatabase(info, container,
                            screenId, info.cellX, info.cellY, info.spanX, info.spanY);
                }
            }
        }
    }

    private void setUseTempCoords(boolean useTempCoords) {
        int childCount = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < childCount; i++) {
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) mShortcutsAndWidgets.getChildAt(
                    i).getLayoutParams();
            lp.useTmpCoords = useTempCoords;
        }
    }

    /**
     * Returns a "reorder" where we simply drop the item in the closest empty space, without moving
     * any other item in the way.
     *
     * @param pixelX X coordinate in pixels in the screen
     * @param pixelY Y coordinate in pixels in the screen
     * @param spanX horizontal cell span
     * @param spanY vertical cell span
     * @return the configuration that represents the found reorder
     */
    private ItemConfiguration closestEmptySpaceReorder(int pixelX, int pixelY, int minSpanX,
            int minSpanY, int spanX, int spanY) {
        ItemConfiguration solution = new ItemConfiguration();
        int[] result = new int[2];
        int[] resultSpan = new int[2];
        findNearestVacantArea(pixelX, pixelY, minSpanX, minSpanY, spanX, spanY, result,
                resultSpan);
        if (result[0] >= 0 && result[1] >= 0) {
            copyCurrentStateToSolution(solution, false);
            solution.cellX = result[0];
            solution.cellY = result[1];
            solution.spanX = resultSpan[0];
            solution.spanY = resultSpan[1];
            solution.isSolution = true;
        } else {
            solution.isSolution = false;
        }
        return solution;
    }

    // For a given cell and span, fetch the set of views intersecting the region.
    private void getViewsIntersectingRegion(int cellX, int cellY, int spanX, int spanY,
            View dragView, Rect boundingRect, ArrayList<View> intersectingViews) {
        if (boundingRect != null) {
            boundingRect.set(cellX, cellY, cellX + spanX, cellY + spanY);
        }
        intersectingViews.clear();
        Rect r0 = new Rect(cellX, cellY, cellX + spanX, cellY + spanY);
        Rect r1 = new Rect();
        final int count = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            if (child == dragView) continue;
            CellLayoutLayoutParams
                    lp = (CellLayoutLayoutParams) child.getLayoutParams();
            r1.set(lp.cellX, lp.cellY, lp.cellX + lp.cellHSpan, lp.cellY + lp.cellVSpan);
            if (Rect.intersects(r0, r1)) {
                mIntersectingViews.add(child);
                if (boundingRect != null) {
                    boundingRect.union(r1);
                }
            }
        }
    }

    boolean isNearestDropLocationOccupied(int pixelX, int pixelY, int spanX, int spanY,
            View dragView, int[] result) {
        result = findNearestAreaIgnoreOccupied(pixelX, pixelY, spanX, spanY, result);
        getViewsIntersectingRegion(result[0], result[1], spanX, spanY, dragView, null,
                mIntersectingViews);
        return !mIntersectingViews.isEmpty();
    }

    void revertTempState() {
        completeAndClearReorderPreviewAnimations();
        if (isItemPlacementDirty() && !DESTRUCTIVE_REORDER) {
            final int count = mShortcutsAndWidgets.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = mShortcutsAndWidgets.getChildAt(i);
                CellLayoutLayoutParams
                        lp = (CellLayoutLayoutParams) child.getLayoutParams();
                if (lp.tmpCellX != lp.cellX || lp.tmpCellY != lp.cellY) {
                    lp.tmpCellX = lp.cellX;
                    lp.tmpCellY = lp.cellY;
                    animateChildToPosition(child, lp.cellX, lp.cellY, REORDER_ANIMATION_DURATION,
                            0, false, false);
                }
            }
            setItemPlacementDirty(false);
        }
    }

    boolean createAreaForResize(int cellX, int cellY, int spanX, int spanY,
            View dragView, int[] direction, boolean commit) {
        int[] pixelXY = new int[2];
        regionToCenterPoint(cellX, cellY, spanX, spanY, pixelXY);

        // First we determine if things have moved enough to cause a different layout
        ItemConfiguration swapSolution = findReorderSolution(pixelXY[0], pixelXY[1], spanX, spanY,
                spanX,  spanY, direction, dragView,  true,  new ItemConfiguration());

        setUseTempCoords(true);
        if (swapSolution != null && swapSolution.isSolution) {
            // If we're just testing for a possible location (MODE_ACCEPT_DROP), we don't bother
            // committing anything or animating anything as we just want to determine if a solution
            // exists
            copySolutionToTempState(swapSolution, dragView);
            setItemPlacementDirty(true);
            animateItemsToSolution(swapSolution, dragView, commit);

            if (commit) {
                commitTempPlacement(null);
                completeAndClearReorderPreviewAnimations();
                setItemPlacementDirty(false);
            } else {
                beginOrAdjustReorderPreviewAnimations(swapSolution, dragView,
                        ReorderPreviewAnimation.MODE_PREVIEW);
            }
            mShortcutsAndWidgets.requestLayout();
        }
        return swapSolution.isSolution;
    }

    /**
     * Find a vacant area that will fit the given bounds nearest the requested
     * cell location, and will also weigh in a suggested direction vector of the
     * desired location. This method computers distance based on unit grid distances,
     * not pixel distances.
     *
     * @param cellX The X cell nearest to which you want to search for a vacant area.
     * @param cellY The Y cell nearest which you want to search for a vacant area.
     * @param spanX Horizontal span of the object.
     * @param spanY Vertical span of the object.
     * @param direction The favored direction in which the views should move from x, y
     * @param occupied The array which represents which cells in the CellLayout are occupied
     * @param blockOccupied The array which represents which cells in the specified block (cellX,
     *        cellY, spanX, spanY) are occupied. This is used when try to move a group of views.
     * @param result Array in which to place the result, or null (in which case a new array will
     *        be allocated)
     * @return The X, Y cell of a vacant area that can contain this object,
     *         nearest the requested location.
     */
    private int[] findNearestArea(int cellX, int cellY, int spanX, int spanY, int[] direction,
            boolean[][] occupied, boolean blockOccupied[][], int[] result) {
        // Keep track of best-scoring drop area
        final int[] bestXY = result != null ? result : new int[2];
        float bestDistance = Float.MAX_VALUE;
        int bestDirectionScore = Integer.MIN_VALUE;

        final int countX = mCountX;
        final int countY = mCountY;

        for (int y = 0; y < countY - (spanY - 1); y++) {
            inner:
            for (int x = 0; x < countX - (spanX - 1); x++) {
                // First, let's see if this thing fits anywhere
                for (int i = 0; i < spanX; i++) {
                    for (int j = 0; j < spanY; j++) {
                        if (occupied[x + i][y + j] && (blockOccupied == null || blockOccupied[i][j])) {
                            continue inner;
                        }
                    }
                }

                float distance = (float) Math.hypot(x - cellX, y - cellY);
                int[] curDirection = mTmpPoint;
                computeDirectionVector(x - cellX, y - cellY, curDirection);
                // The direction score is just the dot product of the two candidate direction
                // and that passed in.
                int curDirectionScore = direction[0] * curDirection[0] +
                        direction[1] * curDirection[1];
                if (Float.compare(distance,  bestDistance) < 0 ||
                        (Float.compare(distance, bestDistance) == 0
                                && curDirectionScore > bestDirectionScore)) {
                    bestDistance = distance;
                    bestDirectionScore = curDirectionScore;
                    bestXY[0] = x;
                    bestXY[1] = y;
                }
            }
        }

        // Return -1, -1 if no suitable location found
        if (bestDistance == Float.MAX_VALUE) {
            bestXY[0] = -1;
            bestXY[1] = -1;
        }
        return bestXY;
    }

    private boolean addViewToTempLocation(View v, Rect rectOccupiedByPotentialDrop,
            int[] direction, ItemConfiguration currentState) {
        CellAndSpan c = currentState.map.get(v);
        boolean success = false;
        mTmpOccupied.markCells(c, false);
        mTmpOccupied.markCells(rectOccupiedByPotentialDrop, true);

        findNearestArea(c.cellX, c.cellY, c.spanX, c.spanY, direction,
                mTmpOccupied.cells, null, mTempLocation);

        if (mTempLocation[0] >= 0 && mTempLocation[1] >= 0) {
            c.cellX = mTempLocation[0];
            c.cellY = mTempLocation[1];
            success = true;
        }
        mTmpOccupied.markCells(c, true);
        return success;
    }

    private boolean pushViewsToTempLocation(ArrayList<View> views, Rect rectOccupiedByPotentialDrop,
            int[] direction, View dragView, ItemConfiguration currentState) {

        ViewCluster cluster = new ViewCluster(views, currentState);
        Rect clusterRect = cluster.getBoundingRect();
        int whichEdge;
        int pushDistance;
        boolean fail = false;

        // Determine the edge of the cluster that will be leading the push and how far
        // the cluster must be shifted.
        if (direction[0] < 0) {
            whichEdge = ViewCluster.LEFT;
            pushDistance = clusterRect.right - rectOccupiedByPotentialDrop.left;
        } else if (direction[0] > 0) {
            whichEdge = ViewCluster.RIGHT;
            pushDistance = rectOccupiedByPotentialDrop.right - clusterRect.left;
        } else if (direction[1] < 0) {
            whichEdge = ViewCluster.TOP;
            pushDistance = clusterRect.bottom - rectOccupiedByPotentialDrop.top;
        } else {
            whichEdge = ViewCluster.BOTTOM;
            pushDistance = rectOccupiedByPotentialDrop.bottom - clusterRect.top;
        }

        // Break early for invalid push distance.
        if (pushDistance <= 0) {
            return false;
        }

        // Mark the occupied state as false for the group of views we want to move.
        for (View v: views) {
            CellAndSpan c = currentState.map.get(v);
            mTmpOccupied.markCells(c, false);
        }

        // We save the current configuration -- if we fail to find a solution we will revert
        // to the initial state. The process of finding a solution modifies the configuration
        // in place, hence the need for revert in the failure case.
        currentState.save();

        // The pushing algorithm is simplified by considering the views in the order in which
        // they would be pushed by the cluster. For example, if the cluster is leading with its
        // left edge, we consider sort the views by their right edge, from right to left.
        cluster.sortConfigurationForEdgePush(whichEdge);

        while (pushDistance > 0 && !fail) {
            for (View v: currentState.sortedViews) {
                // For each view that isn't in the cluster, we see if the leading edge of the
                // cluster is contacting the edge of that view. If so, we add that view to the
                // cluster.
                if (!cluster.views.contains(v) && v != dragView) {
                    if (cluster.isViewTouchingEdge(v, whichEdge)) {
                        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) v.getLayoutParams();
                        if (!lp.canReorder) {
                            // The push solution includes the all apps button, this is not viable.
                            fail = true;
                            break;
                        }
                        cluster.addView(v);
                        CellAndSpan c = currentState.map.get(v);

                        // Adding view to cluster, mark it as not occupied.
                        mTmpOccupied.markCells(c, false);
                    }
                }
            }
            pushDistance--;

            // The cluster has been completed, now we move the whole thing over in the appropriate
            // direction.
            cluster.shift(whichEdge, 1);
        }

        boolean foundSolution = false;
        clusterRect = cluster.getBoundingRect();

        // Due to the nature of the algorithm, the only check required to verify a valid solution
        // is to ensure that completed shifted cluster lies completely within the cell layout.
        if (!fail && clusterRect.left >= 0 && clusterRect.right <= mCountX && clusterRect.top >= 0 &&
                clusterRect.bottom <= mCountY) {
            foundSolution = true;
        } else {
            currentState.restore();
        }

        // In either case, we set the occupied array as marked for the location of the views
        for (View v: cluster.views) {
            CellAndSpan c = currentState.map.get(v);
            mTmpOccupied.markCells(c, true);
        }

        return foundSolution;
    }

    /**
     * This helper class defines a cluster of views. It helps with defining complex edges
     * of the cluster and determining how those edges interact with other views. The edges
     * essentially define a fine-grained boundary around the cluster of views -- like a more
     * precise version of a bounding box.
     */
    private class ViewCluster {
        final static int LEFT = 1 << 0;
        final static int TOP = 1 << 1;
        final static int RIGHT = 1 << 2;
        final static int BOTTOM = 1 << 3;

        final ArrayList<View> views;
        final ItemConfiguration config;
        final Rect boundingRect = new Rect();

        final int[] leftEdge = new int[mCountY];
        final int[] rightEdge = new int[mCountY];
        final int[] topEdge = new int[mCountX];
        final int[] bottomEdge = new int[mCountX];
        int dirtyEdges;
        boolean boundingRectDirty;

        @SuppressWarnings("unchecked")
        public ViewCluster(ArrayList<View> views, ItemConfiguration config) {
            this.views = (ArrayList<View>) views.clone();
            this.config = config;
            resetEdges();
        }

        void resetEdges() {
            for (int i = 0; i < mCountX; i++) {
                topEdge[i] = -1;
                bottomEdge[i] = -1;
            }
            for (int i = 0; i < mCountY; i++) {
                leftEdge[i] = -1;
                rightEdge[i] = -1;
            }
            dirtyEdges = LEFT | TOP | RIGHT | BOTTOM;
            boundingRectDirty = true;
        }

        void computeEdge(int which) {
            int count = views.size();
            for (int i = 0; i < count; i++) {
                CellAndSpan cs = config.map.get(views.get(i));
                switch (which) {
                    case LEFT:
                        int left = cs.cellX;
                        for (int j = cs.cellY; j < cs.cellY + cs.spanY; j++) {
                            if (left < leftEdge[j] || leftEdge[j] < 0) {
                                leftEdge[j] = left;
                            }
                        }
                        break;
                    case RIGHT:
                        int right = cs.cellX + cs.spanX;
                        for (int j = cs.cellY; j < cs.cellY + cs.spanY; j++) {
                            if (right > rightEdge[j]) {
                                rightEdge[j] = right;
                            }
                        }
                        break;
                    case TOP:
                        int top = cs.cellY;
                        for (int j = cs.cellX; j < cs.cellX + cs.spanX; j++) {
                            if (top < topEdge[j] || topEdge[j] < 0) {
                                topEdge[j] = top;
                            }
                        }
                        break;
                    case BOTTOM:
                        int bottom = cs.cellY + cs.spanY;
                        for (int j = cs.cellX; j < cs.cellX + cs.spanX; j++) {
                            if (bottom > bottomEdge[j]) {
                                bottomEdge[j] = bottom;
                            }
                        }
                        break;
                }
            }
        }

        boolean isViewTouchingEdge(View v, int whichEdge) {
            CellAndSpan cs = config.map.get(v);

            if ((dirtyEdges & whichEdge) == whichEdge) {
                computeEdge(whichEdge);
                dirtyEdges &= ~whichEdge;
            }

            switch (whichEdge) {
                case LEFT:
                    for (int i = cs.cellY; i < cs.cellY + cs.spanY; i++) {
                        if (leftEdge[i] == cs.cellX + cs.spanX) {
                            return true;
                        }
                    }
                    break;
                case RIGHT:
                    for (int i = cs.cellY; i < cs.cellY + cs.spanY; i++) {
                        if (rightEdge[i] == cs.cellX) {
                            return true;
                        }
                    }
                    break;
                case TOP:
                    for (int i = cs.cellX; i < cs.cellX + cs.spanX; i++) {
                        if (topEdge[i] == cs.cellY + cs.spanY) {
                            return true;
                        }
                    }
                    break;
                case BOTTOM:
                    for (int i = cs.cellX; i < cs.cellX + cs.spanX; i++) {
                        if (bottomEdge[i] == cs.cellY) {
                            return true;
                        }
                    }
                    break;
            }
            return false;
        }

        void shift(int whichEdge, int delta) {
            for (View v: views) {
                CellAndSpan c = config.map.get(v);
                switch (whichEdge) {
                    case LEFT:
                        c.cellX -= delta;
                        break;
                    case RIGHT:
                        c.cellX += delta;
                        break;
                    case TOP:
                        c.cellY -= delta;
                        break;
                    case BOTTOM:
                    default:
                        c.cellY += delta;
                        break;
                }
            }
            resetEdges();
        }

        public void addView(View v) {
            views.add(v);
            resetEdges();
        }

        public Rect getBoundingRect() {
            if (boundingRectDirty) {
                config.getBoundingRectForViews(views, boundingRect);
            }
            return boundingRect;
        }

        final PositionComparator comparator = new PositionComparator();
        class PositionComparator implements Comparator<View> {
            int whichEdge = 0;
            public int compare(View left, View right) {
                CellAndSpan l = config.map.get(left);
                CellAndSpan r = config.map.get(right);
                switch (whichEdge) {
                    case LEFT:
                        return (r.cellX + r.spanX) - (l.cellX + l.spanX);
                    case RIGHT:
                        return l.cellX - r.cellX;
                    case TOP:
                        return (r.cellY + r.spanY) - (l.cellY + l.spanY);
                    case BOTTOM:
                    default:
                        return l.cellY - r.cellY;
                }
            }
        }

        public void sortConfigurationForEdgePush(int edge) {
            comparator.whichEdge = edge;
            Collections.sort(config.sortedViews, comparator);
        }
    }

    // This method tries to find a reordering solution which satisfies the push mechanic by trying
    // to push items in each of the cardinal directions, in an order based on the direction vector
    // passed.
    private boolean attemptPushInDirection(ArrayList<View> intersectingViews, Rect occupied,
            int[] direction, View ignoreView, ItemConfiguration solution) {
        if ((Math.abs(direction[0]) + Math.abs(direction[1])) > 1) {
            // If the direction vector has two non-zero components, we try pushing
            // separately in each of the components.
            int temp = direction[1];
            direction[1] = 0;

            if (pushViewsToTempLocation(intersectingViews, occupied, direction,
                    ignoreView, solution)) {
                return true;
            }
            direction[1] = temp;
            temp = direction[0];
            direction[0] = 0;

            if (pushViewsToTempLocation(intersectingViews, occupied, direction,
                    ignoreView, solution)) {
                return true;
            }
            // Revert the direction
            direction[0] = temp;

            // Now we try pushing in each component of the opposite direction
            direction[0] *= -1;
            direction[1] *= -1;
            temp = direction[1];
            direction[1] = 0;
            if (pushViewsToTempLocation(intersectingViews, occupied, direction,
                    ignoreView, solution)) {
                return true;
            }

            direction[1] = temp;
            temp = direction[0];
            direction[0] = 0;
            if (pushViewsToTempLocation(intersectingViews, occupied, direction,
                    ignoreView, solution)) {
                return true;
            }
            // revert the direction
            direction[0] = temp;
            direction[0] *= -1;
            direction[1] *= -1;

        } else {
            // If the direction vector has a single non-zero component, we push first in the
            // direction of the vector
            if (pushViewsToTempLocation(intersectingViews, occupied, direction,
                    ignoreView, solution)) {
                return true;
            }
            // Then we try the opposite direction
            direction[0] *= -1;
            direction[1] *= -1;
            if (pushViewsToTempLocation(intersectingViews, occupied, direction,
                    ignoreView, solution)) {
                return true;
            }
            // Switch the direction back
            direction[0] *= -1;
            direction[1] *= -1;

            // If we have failed to find a push solution with the above, then we try
            // to find a solution by pushing along the perpendicular axis.

            // Swap the components
            int temp = direction[1];
            direction[1] = direction[0];
            direction[0] = temp;
            if (pushViewsToTempLocation(intersectingViews, occupied, direction,
                    ignoreView, solution)) {
                return true;
            }

            // Then we try the opposite direction
            direction[0] *= -1;
            direction[1] *= -1;
            if (pushViewsToTempLocation(intersectingViews, occupied, direction,
                    ignoreView, solution)) {
                return true;
            }
            // Switch the direction back
            direction[0] *= -1;
            direction[1] *= -1;

            // Swap the components back
            temp = direction[1];
            direction[1] = direction[0];
            direction[0] = temp;
        }
        return false;
    }

    /*
     * Returns a pair (x, y), where x,y are in {-1, 0, 1} corresponding to vector between
     * the provided point and the provided cell
     */
    private void computeDirectionVector(float deltaX, float deltaY, int[] result) {
        double angle = Math.atan(deltaY / deltaX);

        result[0] = 0;
        result[1] = 0;
        if (Math.abs(Math.cos(angle)) > 0.5f) {
            result[0] = (int) Math.signum(deltaX);
        }
        if (Math.abs(Math.sin(angle)) > 0.5f) {
            result[1] = (int) Math.signum(deltaY);
        }
    }

    /* This seems like it should be obvious and straight-forward, but when the direction vector
    needs to match with the notion of the dragView pushing other views, we have to employ
    a slightly more subtle notion of the direction vector. The question is what two points is
    the vector between? The center of the dragView and its desired destination? Not quite, as
    this doesn't necessarily coincide with the interaction of the dragView and items occupying
    those cells. Instead we use some heuristics to often lock the vector to up, down, left
    or right, which helps make pushing feel right.
    */
    private void getDirectionVectorForDrop(int dragViewCenterX, int dragViewCenterY, int spanX,
            int spanY, View dragView, int[] resultDirection) {

        //TODO(adamcohen) b/151776141 use the items visual center for the direction vector
        int[] targetDestination = new int[2];

        findNearestAreaIgnoreOccupied(dragViewCenterX, dragViewCenterY, spanX, spanY,
                targetDestination);
        Rect dragRect = new Rect();
        cellToRect(targetDestination[0], targetDestination[1], spanX, spanY, dragRect);
        dragRect.offset(dragViewCenterX - dragRect.centerX(), dragViewCenterY - dragRect.centerY());

        Rect dropRegionRect = new Rect();
        getViewsIntersectingRegion(targetDestination[0], targetDestination[1], spanX, spanY,
                dragView, dropRegionRect, mIntersectingViews);

        int dropRegionSpanX = dropRegionRect.width();
        int dropRegionSpanY = dropRegionRect.height();

        cellToRect(dropRegionRect.left, dropRegionRect.top, dropRegionRect.width(),
                dropRegionRect.height(), dropRegionRect);

        int deltaX = (dropRegionRect.centerX() - dragViewCenterX) / spanX;
        int deltaY = (dropRegionRect.centerY() - dragViewCenterY) / spanY;

        if (dropRegionSpanX == mCountX || spanX == mCountX) {
            deltaX = 0;
        }
        if (dropRegionSpanY == mCountY || spanY == mCountY) {
            deltaY = 0;
        }

        if (deltaX == 0 && deltaY == 0) {
            // No idea what to do, give a random direction.
            resultDirection[0] = 1;
            resultDirection[1] = 0;
        } else {
            computeDirectionVector(deltaX, deltaY, resultDirection);
        }
    }

    private boolean addViewsToTempLocation(ArrayList<View> views, Rect rectOccupiedByPotentialDrop,
            int[] direction, View dragView, ItemConfiguration currentState) {
        if (views.size() == 0) return true;

        boolean success = false;
        Rect boundingRect = new Rect();
        // We construct a rect which represents the entire group of views passed in
        currentState.getBoundingRectForViews(views, boundingRect);

        // Mark the occupied state as false for the group of views we want to move.
        for (View v: views) {
            CellAndSpan c = currentState.map.get(v);
            mTmpOccupied.markCells(c, false);
        }

        GridOccupancy blockOccupied = new GridOccupancy(boundingRect.width(), boundingRect.height());
        int top = boundingRect.top;
        int left = boundingRect.left;
        // We mark more precisely which parts of the bounding rect are truly occupied, allowing
        // for interlocking.
        for (View v: views) {
            CellAndSpan c = currentState.map.get(v);
            blockOccupied.markCells(c.cellX - left, c.cellY - top, c.spanX, c.spanY, true);
        }

        mTmpOccupied.markCells(rectOccupiedByPotentialDrop, true);

        findNearestArea(boundingRect.left, boundingRect.top, boundingRect.width(),
                boundingRect.height(), direction,
                mTmpOccupied.cells, blockOccupied.cells, mTempLocation);

        // If we successfully found a location by pushing the block of views, we commit it
        if (mTempLocation[0] >= 0 && mTempLocation[1] >= 0) {
            int deltaX = mTempLocation[0] - boundingRect.left;
            int deltaY = mTempLocation[1] - boundingRect.top;
            for (View v: views) {
                CellAndSpan c = currentState.map.get(v);
                c.cellX += deltaX;
                c.cellY += deltaY;
            }
            success = true;
        }

        // In either case, we set the occupied array as marked for the location of the views
        for (View v: views) {
            CellAndSpan c = currentState.map.get(v);
            mTmpOccupied.markCells(c, true);
        }
        return success;
    }

    private boolean rearrangementExists(int cellX, int cellY, int spanX, int spanY, int[] direction,
            View ignoreView, ItemConfiguration solution) {
        // Return early if get invalid cell positions
        if (cellX < 0 || cellY < 0) return false;

        mIntersectingViews.clear();
        mOccupiedRect.set(cellX, cellY, cellX + spanX, cellY + spanY);

        // Mark the desired location of the view currently being dragged.
        if (ignoreView != null) {
            CellAndSpan c = solution.map.get(ignoreView);
            if (c != null) {
                c.cellX = cellX;
                c.cellY = cellY;
            }
        }
        Rect r0 = new Rect(cellX, cellY, cellX + spanX, cellY + spanY);
        Rect r1 = new Rect();
        for (View child: solution.map.keySet()) {
            if (child == ignoreView) continue;
            CellAndSpan c = solution.map.get(child);
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            r1.set(c.cellX, c.cellY, c.cellX + c.spanX, c.cellY + c.spanY);
            if (Rect.intersects(r0, r1)) {
                if (!lp.canReorder) {
                    return false;
                }
                mIntersectingViews.add(child);
            }
        }

        solution.intersectingViews = new ArrayList<>(mIntersectingViews);

        // First we try to find a solution which respects the push mechanic. That is,
        // we try to find a solution such that no displaced item travels through another item
        // without also displacing that item.
        if (attemptPushInDirection(mIntersectingViews, mOccupiedRect, direction, ignoreView,
                solution)) {
            return true;
        }

        // Next we try moving the views as a block, but without requiring the push mechanic.
        if (addViewsToTempLocation(mIntersectingViews, mOccupiedRect, direction, ignoreView,
                solution)) {
            return true;
        }

        // Ok, they couldn't move as a block, let's move them individually
        for (View v : mIntersectingViews) {
            if (!addViewToTempLocation(v, mOccupiedRect, direction, solution)) {
                return false;
            }
        }
        return true;
    }

    private ItemConfiguration findReorderSolution(int pixelX, int pixelY, int minSpanX, int minSpanY,
            int spanX, int spanY, int[] direction, View dragView, boolean decX,
            ItemConfiguration solution) {
        // Copy the current state into the solution. This solution will be manipulated as necessary.
        copyCurrentStateToSolution(solution, false);
        // Copy the current occupied array into the temporary occupied array. This array will be
        // manipulated as necessary to find a solution.
        mOccupied.copyTo(mTmpOccupied);

        // We find the nearest cell into which we would place the dragged item, assuming there's
        // nothing in its way.
        int result[] = new int[2];
        result = findNearestAreaIgnoreOccupied(pixelX, pixelY, spanX, spanY, result);

        boolean success;
        // First we try the exact nearest position of the item being dragged,
        // we will then want to try to move this around to other neighbouring positions
        success = rearrangementExists(result[0], result[1], spanX, spanY, direction, dragView,
                solution);

        if (!success) {
            // We try shrinking the widget down to size in an alternating pattern, shrink 1 in
            // x, then 1 in y etc.
            if (spanX > minSpanX && (minSpanY == spanY || decX)) {
                return findReorderSolution(pixelX, pixelY, minSpanX, minSpanY, spanX - 1, spanY,
                        direction, dragView, false, solution);
            } else if (spanY > minSpanY) {
                return findReorderSolution(pixelX, pixelY, minSpanX, minSpanY, spanX, spanY - 1,
                        direction, dragView, true, solution);
            }
            solution.isSolution = false;
        } else {
            solution.isSolution = true;
            solution.cellX = result[0];
            solution.cellY = result[1];
            solution.spanX = spanX;
            solution.spanY = spanY;
        }
        return solution;
    }

    private void copyCurrentStateToSolution(ItemConfiguration solution, boolean temp) {
        int childCount = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            CellAndSpan c;
            if (temp) {
                c = new CellAndSpan(lp.tmpCellX, lp.tmpCellY, lp.cellHSpan, lp.cellVSpan);
            } else {
                c = new CellAndSpan(lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan);
            }
            solution.add(child, c);
        }
    }

    /**
     * Returns a "reorder" if there is empty space without rearranging anything.
     *
     * @param pixelX X coordinate in pixels in the screen
     * @param pixelY Y coordinate in pixels in the screen
     * @param spanX horizontal cell span
     * @param spanY vertical cell span
     * @param dragView view being dragged in reorder
     * @return the configuration that represents the found reorder
     */
    public ItemConfiguration dropInPlaceSolution(int pixelX, int pixelY, int spanX,
            int spanY, View dragView) {
        int[] result = new int[2];
        if (isNearestDropLocationOccupied(pixelX, pixelY, spanX, spanY, dragView, result)) {
            result[0] = result[1] = -1;
        }
        ItemConfiguration solution = new ItemConfiguration();
        copyCurrentStateToSolution(solution, false);
        solution.isSolution = result[0] != -1;
        if (!solution.isSolution) {
            return solution;
        }
        solution.cellX = result[0];
        solution.cellY = result[1];
        solution.spanX = spanX;
        solution.spanY = spanY;
        return solution;
    }

    /**
     * When the user drags an Item in the workspace sometimes we need to move the items already in
     * the workspace to make space for the new item, this function return a solution for that
     * reorder.
     *
     * @param pixelX X coordinate in the screen of the dragView in pixels
     * @param pixelY Y coordinate in the screen of the dragView in pixels
     * @param minSpanX minimum horizontal span the item can be shrunk to
     * @param minSpanY minimum vertical span the item can be shrunk to
     * @param spanX occupied horizontal span
     * @param spanY occupied vertical span
     * @param dragView the view of the item being draged
     * @return returns a solution for the given parameters, the solution contains all the icons and
     *         the locations they should be in the given solution.
     */
    public ItemConfiguration calculateReorder(int pixelX, int pixelY, int minSpanX, int minSpanY,
            int spanX, int spanY, View dragView) {
        getDirectionVectorForDrop(pixelX, pixelY, spanX, spanY, dragView, mDirectionVector);

        ItemConfiguration dropInPlaceSolution = dropInPlaceSolution(pixelX, pixelY, spanX, spanY,
                dragView);

        // Find a solution involving pushing / displacing any items in the way
        ItemConfiguration swapSolution = findReorderSolution(pixelX, pixelY, minSpanX, minSpanY,
                spanX,  spanY, mDirectionVector, dragView,  true,  new ItemConfiguration());

        // We attempt the approach which doesn't shuffle views at all
        ItemConfiguration closestSpaceSolution = closestEmptySpaceReorder(pixelX, pixelY, minSpanX,
                minSpanY, spanX, spanY);

        // If the reorder solution requires resizing (shrinking) the item being dropped, we instead
        // favor a solution in which the item is not resized, but
        if (swapSolution.isSolution && swapSolution.area() >= closestSpaceSolution.area()) {
            return swapSolution;
        } else if (closestSpaceSolution.isSolution) {
            return closestSpaceSolution;
        } else if (dropInPlaceSolution.isSolution) {
            return dropInPlaceSolution;
        }
        return null;
    }

    int[] performReorder(int pixelX, int pixelY, int minSpanX, int minSpanY, int spanX, int spanY,
            View dragView, int[] result, int[] resultSpan, int mode) {
        if (resultSpan == null) {
            resultSpan = new int[]{-1, -1};
        }
        if (result == null) {
            result = new int[]{-1, -1};
        }

        ItemConfiguration finalSolution = null;
        // We want the solution to match the animation of the preview and to match the drop so we
        // only recalculate in mode MODE_SHOW_REORDER_HINT because that the first one to run in the
        // reorder cycle.
        if (mode == MODE_SHOW_REORDER_HINT || mPreviousSolution == null) {
            finalSolution = calculateReorder(pixelX, pixelY, minSpanX, minSpanY, spanX, spanY,
                    dragView);
            mPreviousSolution = finalSolution;
        } else {
            finalSolution = mPreviousSolution;
            // We reset this vector after drop
            if (mode == MODE_ON_DROP || mode == MODE_ON_DROP_EXTERNAL) {
                mPreviousSolution = null;
            }
        }

        if (finalSolution == null || !finalSolution.isSolution) {
            result[0] = result[1] = resultSpan[0] = resultSpan[1] = -1;
        } else {
            result[0] = finalSolution.cellX;
            result[1] = finalSolution.cellY;
            resultSpan[0] = finalSolution.spanX;
            resultSpan[1] = finalSolution.spanY;
            performReorder(finalSolution, dragView, mode);
        }
        return result;
    }

    /**
     * Animates and submits in the DB the given ItemConfiguration depending of the mode.
     *
     * @param solution represents widgets on the screen which the Workspace will animate to and
     * would be submitted to the database.
     * @param dragView view which is being dragged over the workspace that trigger the reorder
     * @param mode depending on the mode different animations would be played and depending on the
     *             mode the solution would be submitted or not the database.
     *             The possible modes are {@link MODE_SHOW_REORDER_HINT}, {@link MODE_DRAG_OVER},
     *             {@link MODE_ON_DROP}, {@link MODE_ON_DROP_EXTERNAL}, {@link  MODE_ACCEPT_DROP}
     *             defined in {@link CellLayout}.
     */
    void performReorder(ItemConfiguration solution, View dragView, int mode) {
        if (mode == MODE_SHOW_REORDER_HINT) {
            beginOrAdjustReorderPreviewAnimations(solution, dragView,
                    ReorderPreviewAnimation.MODE_HINT);
            return;
        }
        // If we're just testing for a possible location (MODE_ACCEPT_DROP), we don't bother
        // committing anything or animating anything as we just want to determine if a solution
        // exists
        if (mode == MODE_DRAG_OVER || mode == MODE_ON_DROP || mode == MODE_ON_DROP_EXTERNAL) {
            if (!DESTRUCTIVE_REORDER) {
                setUseTempCoords(true);
            }

            if (!DESTRUCTIVE_REORDER) {
                copySolutionToTempState(solution, dragView);
            }
            setItemPlacementDirty(true);
            animateItemsToSolution(solution, dragView, mode == MODE_ON_DROP);

            if (!DESTRUCTIVE_REORDER
                    && (mode == MODE_ON_DROP || mode == MODE_ON_DROP_EXTERNAL)) {
                // Since the temp solution didn't update dragView, don't commit it either
                commitTempPlacement(dragView);
                completeAndClearReorderPreviewAnimations();
                setItemPlacementDirty(false);
            } else {
                beginOrAdjustReorderPreviewAnimations(solution, dragView,
                        ReorderPreviewAnimation.MODE_PREVIEW);
            }
        }

        if (mode == MODE_ON_DROP && !DESTRUCTIVE_REORDER) {
            setUseTempCoords(false);
        }

        mShortcutsAndWidgets.requestLayout();
    }

    void setItemPlacementDirty(boolean dirty) {
        mItemPlacementDirty = dirty;
    }
    boolean isItemPlacementDirty() {
        return mItemPlacementDirty;
    }

    private static class ItemConfiguration extends CellAndSpan {
        final ArrayMap<View, CellAndSpan> map = new ArrayMap<>();
        private final ArrayMap<View, CellAndSpan> savedMap = new ArrayMap<>();
        final ArrayList<View> sortedViews = new ArrayList<>();
        ArrayList<View> intersectingViews;
        boolean isSolution = false;

        void save() {
            // Copy current state into savedMap
            for (View v: map.keySet()) {
                savedMap.get(v).copyFrom(map.get(v));
            }
        }

        void restore() {
            // Restore current state from savedMap
            for (View v: savedMap.keySet()) {
                map.get(v).copyFrom(savedMap.get(v));
            }
        }

        void add(View v, CellAndSpan cs) {
            map.put(v, cs);
            savedMap.put(v, new CellAndSpan());
            sortedViews.add(v);
        }

        int area() {
            return spanX * spanY;
        }

        void getBoundingRectForViews(ArrayList<View> views, Rect outRect) {
            boolean first = true;
            for (View v: views) {
                CellAndSpan c = map.get(v);
                if (first) {
                    outRect.set(c.cellX, c.cellY, c.cellX + c.spanX, c.cellY + c.spanY);
                    first = false;
                } else {
                    outRect.union(c.cellX, c.cellY, c.cellX + c.spanX, c.cellY + c.spanY);
                }
            }
        }
    }

    /**
     * Find a starting cell position that will fit the given bounds nearest the requested
     * cell location. Uses Euclidean distance to score multiple vacant areas.
     *
     * @param pixelX The X location at which you want to search for a vacant area.
     * @param pixelY The Y location at which you want to search for a vacant area.
     * @param spanX Horizontal span of the object.
     * @param spanY Vertical span of the object.
     * @param result Previously returned value to possibly recycle.
     * @return The X, Y cell of a vacant area that can contain this object,
     *         nearest the requested location.
     */
    public int[] findNearestAreaIgnoreOccupied(int pixelX, int pixelY, int spanX, int spanY,
            int[] result) {
        return findNearestArea(pixelX, pixelY, spanX, spanY, spanX, spanY, true, result, null);
    }

    boolean existsEmptyCell() {
        return findCellForSpan(null, 1, 1);
    }

    /**
     * Finds the upper-left coordinate of the first rectangle in the grid that can
     * hold a cell of the specified dimensions. If intersectX and intersectY are not -1,
     * then this method will only return coordinates for rectangles that contain the cell
     * (intersectX, intersectY)
     *
     * @param cellXY The array that will contain the position of a vacant cell if such a cell
     *               can be found.
     * @param spanX The horizontal span of the cell we want to find.
     * @param spanY The vertical span of the cell we want to find.
     *
     * @return True if a vacant cell of the specified dimension was found, false otherwise.
     */
    public boolean findCellForSpan(int[] cellXY, int spanX, int spanY) {
        if (cellXY == null) {
            cellXY = new int[2];
        }
        return mOccupied.findVacantCell(cellXY, spanX, spanY);
    }

    /**
     * A drag event has begun over this layout.
     * It may have begun over this layout (in which case onDragChild is called first),
     * or it may have begun on another layout.
     */
    void onDragEnter() {
        mDragging = true;
        mPreviousSolution = null;
    }

    /**
     * Called when drag has left this CellLayout or has been completed (successfully or not)
     */
    void onDragExit() {
        // This can actually be called when we aren't in a drag, e.g. when adding a new
        // item to this layout via the customize drawer.
        // Guard against that case.
        if (mDragging) {
            mDragging = false;
        }

        // Invalidate the drag data
        mPreviousSolution = null;
        mDragCell[0] = mDragCell[1] = -1;
        mDragCellSpan[0] = mDragCellSpan[1] = -1;
        mDragOutlineAnims[mDragOutlineCurrent].animateOut();
        mDragOutlineCurrent = (mDragOutlineCurrent + 1) % mDragOutlineAnims.length;
        revertTempState();
        setIsDragOverlapping(false);
    }

    /**
     * Mark a child as having been dropped.
     * At the beginning of the drag operation, the child may have been on another
     * screen, but it is re-parented before this method is called.
     *
     * @param child The child that is being dropped
     */
    void onDropChild(View child) {
        if (child != null) {
            CellLayoutLayoutParams
                    lp = (CellLayoutLayoutParams) child.getLayoutParams();
            lp.dropped = true;
            child.requestLayout();
            markCellsAsOccupiedForView(child);
        }
    }

    /**
     * Computes a bounding rectangle for a range of cells
     *
     * @param cellX X coordinate of upper left corner expressed as a cell position
     * @param cellY Y coordinate of upper left corner expressed as a cell position
     * @param cellHSpan Width in cells
     * @param cellVSpan Height in cells
     * @param resultRect Rect into which to put the results
     */
    public void cellToRect(int cellX, int cellY, int cellHSpan, int cellVSpan, Rect resultRect) {
        final int cellWidth = mCellWidth;
        final int cellHeight = mCellHeight;

        // We observe a shift of 1 pixel on the x coordinate compared to the actual cell coordinates
        final int hStartPadding = getPaddingLeft()
                + (int) Math.ceil(getUnusedHorizontalSpace() / 2f);
        final int vStartPadding = getPaddingTop();

        int x = hStartPadding + (cellX * mBorderSpace.x) + (cellX * cellWidth);
        int y = vStartPadding + (cellY * mBorderSpace.y) + (cellY * cellHeight);

        int width = cellHSpan * cellWidth + ((cellHSpan - 1) * mBorderSpace.x);
        int height = cellVSpan * cellHeight + ((cellVSpan - 1) * mBorderSpace.y);

        resultRect.set(x, y, x + width, y + height);
    }

    public void markCellsAsOccupiedForView(View view) {
        if (view instanceof LauncherAppWidgetHostView
                && view.getTag() instanceof LauncherAppWidgetInfo) {
            LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) view.getTag();
            mOccupied.markCells(info.cellX, info.cellY, info.spanX, info.spanY, true);
            return;
        }
        if (view == null || view.getParent() != mShortcutsAndWidgets) return;
        CellLayoutLayoutParams
                lp = (CellLayoutLayoutParams) view.getLayoutParams();
        mOccupied.markCells(lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan, true);
    }

    public void markCellsAsUnoccupiedForView(View view) {
        if (view instanceof LauncherAppWidgetHostView
                && view.getTag() instanceof LauncherAppWidgetInfo) {
            LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) view.getTag();
            mOccupied.markCells(info.cellX, info.cellY, info.spanX, info.spanY, false);
            return;
        }
        if (view == null || view.getParent() != mShortcutsAndWidgets) return;
        CellLayoutLayoutParams
                lp = (CellLayoutLayoutParams) view.getLayoutParams();
        mOccupied.markCells(lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan, false);
    }

    public int getDesiredWidth() {
        return getPaddingLeft() + getPaddingRight() + (mCountX * mCellWidth)
                + ((mCountX - 1) * mBorderSpace.x);
    }

    public int getDesiredHeight()  {
        return getPaddingTop() + getPaddingBottom() + (mCountY * mCellHeight)
                + ((mCountY - 1) * mBorderSpace.y);
    }

    public boolean isOccupied(int x, int y) {
        if (x < mCountX && y < mCountY) {
            return mOccupied.cells[x][y];
        } else {
            throw new RuntimeException("Position exceeds the bound of this CellLayout");
        }
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new CellLayoutLayoutParams(getContext(), attrs);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof CellLayoutLayoutParams;
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new CellLayoutLayoutParams(p);
    }

    // This class stores info for two purposes:
    // 1. When dragging items (mDragInfo in Workspace), we store the View, its cellX & cellY,
    //    its spanX, spanY, and the screen it is on
    // 2. When long clicking on an empty cell in a CellLayout, we save information about the
    //    cellX and cellY coordinates and which page was clicked. We then set this as a tag on
    //    the CellLayout that was long clicked
    public static final class CellInfo extends CellAndSpan {
        public final View cell;
        final int screenId;
        final int container;

        public CellInfo(View v, ItemInfo info) {
            cellX = info.cellX;
            cellY = info.cellY;
            spanX = info.spanX;
            spanY = info.spanY;
            cell = v;
            screenId = info.screenId;
            container = info.container;
        }

        @Override
        public String toString() {
            return "Cell[view=" + (cell == null ? "null" : cell.getClass())
                    + ", x=" + cellX + ", y=" + cellY + "]";
        }
    }

    /**
     * A Delegated cell Drawing for drawing on CellLayout
     */
    public abstract static class DelegatedCellDrawing {
        public int mDelegateCellX;
        public int mDelegateCellY;

        /**
         * Draw under CellLayout
         */
        public abstract void drawUnderItem(Canvas canvas);

        /**
         * Draw over CellLayout
         */
        public abstract void drawOverItem(Canvas canvas);
    }

    /**
     * Returns whether an item can be placed in this CellLayout (after rearranging and/or resizing
     * if necessary).
     */
    public boolean hasReorderSolution(ItemInfo itemInfo) {
        int[] cellPoint = new int[2];
        // Check for a solution starting at every cell.
        for (int cellX = 0; cellX < getCountX(); cellX++) {
            for (int cellY = 0; cellY < getCountY(); cellY++) {
                cellToPoint(cellX, cellY, cellPoint);
                if (findReorderSolution(cellPoint[0], cellPoint[1], itemInfo.minSpanX,
                        itemInfo.minSpanY, itemInfo.spanX, itemInfo.spanY, mDirectionVector, null,
                        true, new ItemConfiguration()).isSolution) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Finds solution to accept hotseat migration to cell layout. commits solution if commitConfig
     */
    public boolean makeSpaceForHotseatMigration(boolean commitConfig) {
        int[] cellPoint = new int[2];
        int[] directionVector = new int[]{0, -1};
        cellToPoint(0, mCountY, cellPoint);
        ItemConfiguration configuration = new ItemConfiguration();
        if (findReorderSolution(cellPoint[0], cellPoint[1], mCountX, 1, mCountX, 1,
                directionVector, null, false, configuration).isSolution) {
            if (commitConfig) {
                copySolutionToTempState(configuration, null);
                commitTempPlacement(null);
                // undo marking cells occupied since there is actually nothing being placed yet.
                mOccupied.markCells(0, mCountY - 1, mCountX, 1, false);
            }
            return true;
        }
        return false;
    }

    /**
     * returns a copy of cell layout's grid occupancy
     */
    public GridOccupancy cloneGridOccupancy() {
        GridOccupancy occupancy = new GridOccupancy(mCountX, mCountY);
        mOccupied.copyTo(occupancy);
        return occupancy;
    }

    public boolean isRegionVacant(int x, int y, int spanX, int spanY) {
        return mOccupied.isRegionVacant(x, y, spanX, spanY);
    }
}
