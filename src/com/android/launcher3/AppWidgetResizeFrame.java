package com.android.launcher3;

import static android.appwidget.AppWidgetHostView.getDefaultPaddingForWidget;

import static com.android.launcher3.CellLayout.SPRING_LOADED_PROGRESS;
import static com.android.launcher3.LauncherAnimUtils.LAYOUT_HEIGHT;
import static com.android.launcher3.LauncherAnimUtils.LAYOUT_WIDTH;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_WIDGET_RESIZE_COMPLETED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_WIDGET_RESIZE_STARTED;
import static com.android.launcher3.views.BaseDragLayer.LAYOUT_X;
import static com.android.launcher3.views.BaseDragLayer.LAYOUT_Y;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.annotation.Px;

import com.android.launcher3.accessibility.DragViewStateAnnouncer;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.keyboard.ViewGroupFocusHelper;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.InstanceIdSequence;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.PendingRequestArgs;
import com.android.launcher3.views.ArrowTipView;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.util.WidgetSizes;

import java.util.ArrayList;
import java.util.List;

import app.lawnchair.theme.color.ColorTokens;
import app.lawnchair.theme.drawable.DrawableTokens;

public class AppWidgetResizeFrame extends AbstractFloatingView implements View.OnKeyListener {
    private static final int SNAP_DURATION = 150;
    private static final float DIMMED_HANDLE_ALPHA = 0f;
    private static final float RESIZE_THRESHOLD = 0.66f;

    private static final String KEY_RECONFIGURABLE_WIDGET_EDUCATION_TIP_SEEN =
            "launcher.reconfigurable_widget_education_tip_seen";
    private static final Rect sTmpRect = new Rect();
    private static final Rect sTmpRect2 = new Rect();

    private static final int HANDLE_COUNT = 4;
    private static final int INDEX_LEFT = 0;
    private static final int INDEX_TOP = 1;
    private static final int INDEX_RIGHT = 2;
    private static final int INDEX_BOTTOM = 3;
    private static final float MIN_OPACITY_FOR_CELL_LAYOUT_DURING_INVALID_RESIZE = 0.5f;

    private final Launcher mLauncher;
    private final DragViewStateAnnouncer mStateAnnouncer;
    private final FirstFrameAnimatorHelper mFirstFrameAnimatorHelper;

    private final View[] mDragHandles = new View[HANDLE_COUNT];
    private final List<Rect> mSystemGestureExclusionRects = new ArrayList<>(HANDLE_COUNT);
    private final OnAttachStateChangeListener mWidgetViewAttachStateChangeListener =
            new OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View view) {
                    // Do nothing
                }

                @Override
                public void onViewDetachedFromWindow(View view) {
                    // When the app widget view is detached, we should close the resize frame.
                    // An example is when the dragging starts, the widget view is detached from
                    // CellLayout and then reattached to DragLayout.
                    close(false);
                }
            };


    private LauncherAppWidgetHostView mWidgetView;
    private CellLayout mCellLayout;
    private DragLayer mDragLayer;
    private ImageButton mReconfigureButton;

    private Rect mWidgetPadding;

    private final int mBackgroundPadding;
    private final int mTouchTargetWidth;

    private final int[] mDirectionVector = new int[2];
    private final int[] mLastDirectionVector = new int[2];

    private final IntRange mTempRange1 = new IntRange();
    private final IntRange mTempRange2 = new IntRange();

    private final IntRange mDeltaXRange = new IntRange();
    private final IntRange mBaselineX = new IntRange();

    private final IntRange mDeltaYRange = new IntRange();
    private final IntRange mBaselineY = new IntRange();

    private final InstanceId logInstanceId = new InstanceIdSequence().newInstanceId();

    private final ViewGroupFocusHelper mDragLayerRelativeCoordinateHelper;

    /**
     * In the two panel UI, it is not possible to resize a widget to cross its host
     * {@link CellLayout}'s sibling. When this happens, we gradually reduce the opacity of the
     * sibling {@link CellLayout} from 1f to
     * {@link #MIN_OPACITY_FOR_CELL_LAYOUT_DURING_INVALID_RESIZE}.
     */
    private final float mDragAcrossTwoPanelOpacityMargin;

    private boolean mLeftBorderActive;
    private boolean mRightBorderActive;
    private boolean mTopBorderActive;
    private boolean mBottomBorderActive;

    private boolean mHorizontalResizeActive;
    private boolean mVerticalResizeActive;

    private int mRunningHInc;
    private int mRunningVInc;
    private int mMinHSpan;
    private int mMinVSpan;
    private int mMaxHSpan;
    private int mMaxVSpan;
    private int mDeltaX;
    private int mDeltaY;
    private int mDeltaXAddOn;
    private int mDeltaYAddOn;

    private int mTopTouchRegionAdjustment = 0;
    private int mBottomTouchRegionAdjustment = 0;

    private int mXDown, mYDown;

    public AppWidgetResizeFrame(Context context) {
        this(context, null);
    }

    public AppWidgetResizeFrame(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppWidgetResizeFrame(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mLauncher = Launcher.getLauncher(context);
        mStateAnnouncer = DragViewStateAnnouncer.createFor(this);

        mBackgroundPadding = getResources()
                .getDimensionPixelSize(R.dimen.resize_frame_background_padding);
        mTouchTargetWidth = 2 * mBackgroundPadding;
        mFirstFrameAnimatorHelper = new FirstFrameAnimatorHelper(this);

        for (int i = 0; i < HANDLE_COUNT; i++) {
            mSystemGestureExclusionRects.add(new Rect());
        }

        mDragAcrossTwoPanelOpacityMargin = mLauncher.getResources().getDimensionPixelSize(
                R.dimen.resize_frame_invalid_drag_across_two_panel_opacity_margin);
        mDragLayerRelativeCoordinateHelper = new ViewGroupFocusHelper(mLauncher.getDragLayer());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mDragHandles[INDEX_LEFT] = findViewById(R.id.widget_resize_left_handle);
        mDragHandles[INDEX_TOP] = findViewById(R.id.widget_resize_top_handle);
        mDragHandles[INDEX_RIGHT] = findViewById(R.id.widget_resize_right_handle);
        mDragHandles[INDEX_BOTTOM] = findViewById(R.id.widget_resize_bottom_handle);

        int workspaceAccentColor = ColorTokens.WorkspaceAccentColor.resolveColor(getContext());
        for (int i = 0; i < HANDLE_COUNT; i++) {
            ((ImageView) mDragHandles[i]).setColorFilter(workspaceAccentColor);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (Utilities.ATLEAST_Q) {
            for (int i = 0; i < HANDLE_COUNT; i++) {
                View dragHandle = mDragHandles[i];
                mSystemGestureExclusionRects.get(i).set(dragHandle.getLeft(), dragHandle.getTop(),
                        dragHandle.getRight(), dragHandle.getBottom());
            }
            setSystemGestureExclusionRects(mSystemGestureExclusionRects);
        }
    }

    public static void showForWidget(LauncherAppWidgetHostView widget, CellLayout cellLayout) {
        Launcher launcher = Launcher.getLauncher(cellLayout.getContext());
        AbstractFloatingView.closeAllOpenViews(launcher);

        DragLayer dl = launcher.getDragLayer();
        AppWidgetResizeFrame frame = (AppWidgetResizeFrame) launcher.getLayoutInflater()
                .inflate(R.layout.app_widget_resize_frame, dl, false);
        ImageView imageView = frame.findViewById(R.id.widget_resize_frame);
        imageView.setImageDrawable(DrawableTokens.WidgetResizeFrame.resolve(launcher));
        if (widget.hasEnforcedCornerRadius()) {
            float enforcedCornerRadius = widget.getEnforcedCornerRadius();
            Drawable d = imageView.getDrawable();
            if (d instanceof GradientDrawable) {
                GradientDrawable gd = (GradientDrawable) d.mutate();
                gd.setCornerRadius(enforcedCornerRadius);
            }
        }
        frame.setupForWidget(widget, cellLayout, dl);
        ((DragLayer.LayoutParams) frame.getLayoutParams()).customPosition = true;

        dl.addView(frame);
        frame.mIsOpen = true;
        frame.post(() -> frame.snapToWidget(false));
    }

    private void setupForWidget(LauncherAppWidgetHostView widgetView, CellLayout cellLayout,
            DragLayer dragLayer) {
        mCellLayout = cellLayout;
        if (mWidgetView != null) {
            mWidgetView.removeOnAttachStateChangeListener(mWidgetViewAttachStateChangeListener);
        }
        mWidgetView = widgetView;
        mWidgetView.addOnAttachStateChangeListener(mWidgetViewAttachStateChangeListener);
        LauncherAppWidgetProviderInfo info = (LauncherAppWidgetProviderInfo)
                widgetView.getAppWidgetInfo();
        mDragLayer = dragLayer;

        mMinHSpan = info.minSpanX;
        mMinVSpan = info.minSpanY;
        mMaxHSpan = info.maxSpanX;
        mMaxVSpan = info.maxSpanY;

        mWidgetPadding = getDefaultPaddingForWidget(getContext(),
                widgetView.getAppWidgetInfo().provider, null);

        mReconfigureButton = (ImageButton) findViewById(R.id.widget_reconfigure_button);
        if (info.isReconfigurable()) {
            mReconfigureButton.setVisibility(VISIBLE);
            mReconfigureButton.setOnClickListener(view -> {
                mLauncher.setWaitingForResult(
                        PendingRequestArgs.forWidgetInfo(
                                mWidgetView.getAppWidgetId(),
                                // Widget add handler is null since we're reconfiguring an existing
                                // widget.
                                /* widgetHandler= */ null,
                                (ItemInfo) mWidgetView.getTag()));
                mLauncher
                    .getAppWidgetHost()
                    .startConfigActivity(
                            mLauncher,
                            mWidgetView.getAppWidgetId(),
                            Launcher.REQUEST_RECONFIGURE_APPWIDGET);
            });
            if (!hasSeenReconfigurableWidgetEducationTip()) {
                post(() -> {
                    if (showReconfigurableWidgetEducationTip() != null) {
                        mLauncher.getSharedPrefs().edit()
                                .putBoolean(KEY_RECONFIGURABLE_WIDGET_EDUCATION_TIP_SEEN,
                                        true).apply();
                    }
                });
            }
        }

        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) mWidgetView.getLayoutParams();
        ItemInfo widgetInfo = (ItemInfo) mWidgetView.getTag();
        lp.cellX = lp.tmpCellX = widgetInfo.cellX;
        lp.cellY = lp.tmpCellY = widgetInfo.cellY;
        lp.cellHSpan = widgetInfo.spanX;
        lp.cellVSpan = widgetInfo.spanY;
        lp.isLockedToGrid = true;

        // When we create the resize frame, we first mark all cells as unoccupied. The appropriate
        // cells (same if not resized, or different) will be marked as occupied when the resize
        // frame is dismissed.
        mCellLayout.markCellsAsUnoccupiedForView(mWidgetView);

        mLauncher.getStatsLogManager()
                .logger()
                .withInstanceId(logInstanceId)
                .withItemInfo(widgetInfo)
                .log(LAUNCHER_WIDGET_RESIZE_STARTED);

        setOnKeyListener(this);
    }

    public boolean beginResizeIfPointInRegion(int x, int y) {
        mLeftBorderActive = x < mTouchTargetWidth;
        mRightBorderActive = x > getWidth() - mTouchTargetWidth;
        mTopBorderActive = y < mTouchTargetWidth + mTopTouchRegionAdjustment;
        mBottomBorderActive = y > getHeight() - mTouchTargetWidth + mBottomTouchRegionAdjustment;

        boolean anyBordersActive = mLeftBorderActive || mRightBorderActive
                || mTopBorderActive || mBottomBorderActive;

        if (anyBordersActive) {
            mDragHandles[INDEX_LEFT].setAlpha(mLeftBorderActive ? 1.0f : DIMMED_HANDLE_ALPHA);
            mDragHandles[INDEX_RIGHT].setAlpha(mRightBorderActive ? 1.0f :DIMMED_HANDLE_ALPHA);
            mDragHandles[INDEX_TOP].setAlpha(mTopBorderActive ? 1.0f : DIMMED_HANDLE_ALPHA);
            mDragHandles[INDEX_BOTTOM].setAlpha(mBottomBorderActive ? 1.0f : DIMMED_HANDLE_ALPHA);
        }

        if (mLeftBorderActive) {
            mDeltaXRange.set(-getLeft(), getWidth() - 2 * mTouchTargetWidth);
        } else if (mRightBorderActive) {
            mDeltaXRange.set(2 * mTouchTargetWidth - getWidth(), mDragLayer.getWidth() - getRight());
        } else {
            mDeltaXRange.set(0, 0);
        }
        mBaselineX.set(getLeft(), getRight());

        if (mTopBorderActive) {
            mDeltaYRange.set(-getTop(), getHeight() - 2 * mTouchTargetWidth);
        } else if (mBottomBorderActive) {
            mDeltaYRange.set(2 * mTouchTargetWidth - getHeight(), mDragLayer.getHeight() - getBottom());
        } else {
            mDeltaYRange.set(0, 0);
        }
        mBaselineY.set(getTop(), getBottom());

        return anyBordersActive;
    }

    /**
     *  Based on the deltas, we resize the frame.
     */
    public void visualizeResizeForDelta(int deltaX, int deltaY) {
        mDeltaX = mDeltaXRange.clamp(deltaX);
        mDeltaY = mDeltaYRange.clamp(deltaY);

        DragLayer.LayoutParams lp = (DragLayer.LayoutParams) getLayoutParams();
        mDeltaX = mDeltaXRange.clamp(deltaX);
        mBaselineX.applyDelta(mLeftBorderActive, mRightBorderActive, mDeltaX, mTempRange1);
        lp.x = mTempRange1.start;
        lp.width = mTempRange1.size();

        mDeltaY = mDeltaYRange.clamp(deltaY);
        mBaselineY.applyDelta(mTopBorderActive, mBottomBorderActive, mDeltaY, mTempRange1);
        lp.y = mTempRange1.start;
        lp.height = mTempRange1.size();

        resizeWidgetIfNeeded(false);

        // When the widget resizes in multi-window mode, the translation value changes to maintain
        // a center fit. These overrides ensure the resize frame always aligns with the widget view.
        getSnappedRectRelativeToDragLayer(sTmpRect);
        if (mLeftBorderActive) {
            lp.width = sTmpRect.width() + sTmpRect.left - lp.x;
        }
        if (mTopBorderActive) {
            lp.height = sTmpRect.height() + sTmpRect.top - lp.y;
        }
        if (mRightBorderActive) {
            lp.x = sTmpRect.left;
        }
        if (mBottomBorderActive) {
            lp.y = sTmpRect.top;
        }

        // Handle invalid resize across CellLayouts in the two panel UI.
        if (mCellLayout.getParent() instanceof Workspace) {
            Workspace workspace = (Workspace) mCellLayout.getParent();
            CellLayout pairedCellLayout = workspace.getScreenPair(mCellLayout);
            if (pairedCellLayout != null) {
                Rect focusedCellLayoutBound = sTmpRect;
                mDragLayerRelativeCoordinateHelper.viewToRect(mCellLayout, focusedCellLayoutBound);
                Rect resizeFrameBound = sTmpRect2;
                findViewById(R.id.widget_resize_frame).getGlobalVisibleRect(resizeFrameBound);
                float progress = 1f;
                if (workspace.indexOfChild(pairedCellLayout) < workspace.indexOfChild(mCellLayout)
                        && mDeltaX < 0
                        && resizeFrameBound.left < focusedCellLayoutBound.left) {
                    // Resize from right to left.
                    progress = (mDragAcrossTwoPanelOpacityMargin + mDeltaX)
                            / mDragAcrossTwoPanelOpacityMargin;
                } else if (workspace.indexOfChild(pairedCellLayout)
                                > workspace.indexOfChild(mCellLayout)
                        && mDeltaX > 0
                        && resizeFrameBound.right > focusedCellLayoutBound.right) {
                    // Resize from left to right.
                    progress = (mDragAcrossTwoPanelOpacityMargin - mDeltaX)
                            / mDragAcrossTwoPanelOpacityMargin;
                }
                float alpha = Math.max(MIN_OPACITY_FOR_CELL_LAYOUT_DURING_INVALID_RESIZE, progress);
                float springLoadedProgress = Math.min(1f, 1f - progress);
                updateInvalidResizeEffect(mCellLayout, pairedCellLayout, alpha,
                        springLoadedProgress);
            }
        }

        requestLayout();
    }

    private static int getSpanIncrement(float deltaFrac) {
        return Math.abs(deltaFrac) > RESIZE_THRESHOLD ? Math.round(deltaFrac) : 0;
    }

    /**
     *  Based on the current deltas, we determine if and how to resize the widget.
     */
    private void resizeWidgetIfNeeded(boolean onDismiss) {
        DeviceProfile dp = mLauncher.getDeviceProfile();
        float xThreshold = mCellLayout.getCellWidth() + dp.cellLayoutBorderSpacePx.x;
        float yThreshold = mCellLayout.getCellHeight() + dp.cellLayoutBorderSpacePx.y;

        int hSpanInc = getSpanIncrement((mDeltaX + mDeltaXAddOn) / xThreshold - mRunningHInc);
        int vSpanInc = getSpanIncrement((mDeltaY + mDeltaYAddOn) / yThreshold - mRunningVInc);

        if (!onDismiss && (hSpanInc == 0 && vSpanInc == 0)) return;

        mDirectionVector[0] = 0;
        mDirectionVector[1] = 0;

        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) mWidgetView.getLayoutParams();

        int spanX = lp.cellHSpan;
        int spanY = lp.cellVSpan;
        int cellX = lp.useTmpCoords ? lp.tmpCellX : lp.cellX;
        int cellY = lp.useTmpCoords ? lp.tmpCellY : lp.cellY;

        // For each border, we bound the resizing based on the minimum width, and the maximum
        // expandability.
        mTempRange1.set(cellX, spanX + cellX);
        int hSpanDelta = mTempRange1.applyDeltaAndBound(mLeftBorderActive, mRightBorderActive,
                hSpanInc, mMinHSpan, mMaxHSpan, mCellLayout.getCountX(), mTempRange2);
        cellX = mTempRange2.start;
        spanX = mTempRange2.size();
        if (hSpanDelta != 0) {
            mDirectionVector[0] = mLeftBorderActive ? -1 : 1;
        }

        mTempRange1.set(cellY, spanY + cellY);
        int vSpanDelta = mTempRange1.applyDeltaAndBound(mTopBorderActive, mBottomBorderActive,
                vSpanInc, mMinVSpan, mMaxVSpan, mCellLayout.getCountY(), mTempRange2);
        cellY = mTempRange2.start;
        spanY = mTempRange2.size();
        if (vSpanDelta != 0) {
            mDirectionVector[1] = mTopBorderActive ? -1 : 1;
        }

        if (!onDismiss && vSpanDelta == 0 && hSpanDelta == 0) return;

        // We always want the final commit to match the feedback, so we make sure to use the
        // last used direction vector when committing the resize / reorder.
        if (onDismiss) {
            mDirectionVector[0] = mLastDirectionVector[0];
            mDirectionVector[1] = mLastDirectionVector[1];
        } else {
            mLastDirectionVector[0] = mDirectionVector[0];
            mLastDirectionVector[1] = mDirectionVector[1];
        }

        if (mCellLayout.createAreaForResize(cellX, cellY, spanX, spanY, mWidgetView,
                mDirectionVector, onDismiss)) {
            if (mStateAnnouncer != null && (lp.cellHSpan != spanX || lp.cellVSpan != spanY) ) {
                mStateAnnouncer.announce(
                        mLauncher.getString(R.string.widget_resized, spanX, spanY));
            }

            lp.tmpCellX = cellX;
            lp.tmpCellY = cellY;
            lp.cellHSpan = spanX;
            lp.cellVSpan = spanY;
            mRunningVInc += vSpanDelta;
            mRunningHInc += hSpanDelta;

            if (!onDismiss) {
                WidgetSizes.updateWidgetSizeRanges(mWidgetView, mLauncher, spanX, spanY);
            }
        }
        mWidgetView.requestLayout();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        // We are done with resizing the widget. Save the widget size & position to LauncherModel
        resizeWidgetIfNeeded(true);
        mLauncher.getStatsLogManager()
                .logger()
                .withInstanceId(logInstanceId)
                .withItemInfo((ItemInfo) mWidgetView.getTag())
                .log(LAUNCHER_WIDGET_RESIZE_COMPLETED);
    }

    private void onTouchUp() {
        DeviceProfile dp = mLauncher.getDeviceProfile();
        int xThreshold = mCellLayout.getCellWidth() + dp.cellLayoutBorderSpacePx.x;
        int yThreshold = mCellLayout.getCellHeight() + dp.cellLayoutBorderSpacePx.y;

        mDeltaXAddOn = mRunningHInc * xThreshold;
        mDeltaYAddOn = mRunningVInc * yThreshold;
        mDeltaX = 0;
        mDeltaY = 0;

        post(() -> snapToWidget(true));
    }

    /**
     * Returns the rect of this view when the frame is snapped around the widget, with the bounds
     * relative to the {@link DragLayer}.
     */
    private void getSnappedRectRelativeToDragLayer(Rect out) {
        float scale = mWidgetView.getScaleToFit();

        mDragLayer.getViewRectRelativeToSelf(mWidgetView, out);

        int width = 2 * mBackgroundPadding
                + (int) (scale * (out.width() - mWidgetPadding.left - mWidgetPadding.right));
        int height = 2 * mBackgroundPadding
                + (int) (scale * (out.height() - mWidgetPadding.top - mWidgetPadding.bottom));

        int x = (int) (out.left - mBackgroundPadding + scale * mWidgetPadding.left);
        int y = (int) (out.top - mBackgroundPadding + scale * mWidgetPadding.top);

        out.left = x;
        out.top = y;
        out.right = out.left + width;
        out.bottom = out.top + height;
    }

    private void snapToWidget(boolean animate) {
        getSnappedRectRelativeToDragLayer(sTmpRect);
        int newWidth = sTmpRect.width();
        int newHeight = sTmpRect.height();
        int newX = sTmpRect.left;
        int newY = sTmpRect.top;

        // We need to make sure the frame's touchable regions lie fully within the bounds of the
        // DragLayer. We allow the actual handles to be clipped, but we shift the touch regions
        // down accordingly to provide a proper touch target.
        if (newY < 0) {
            // In this case we shift the touch region down to start at the top of the DragLayer
            mTopTouchRegionAdjustment = -newY;
        } else {
            mTopTouchRegionAdjustment = 0;
        }
        if (newY + newHeight > mDragLayer.getHeight()) {
            // In this case we shift the touch region up to end at the bottom of the DragLayer
            mBottomTouchRegionAdjustment = -(newY + newHeight - mDragLayer.getHeight());
        } else {
            mBottomTouchRegionAdjustment = 0;
        }

        final DragLayer.LayoutParams lp = (DragLayer.LayoutParams) getLayoutParams();
        final CellLayout pairedCellLayout;
        if (mCellLayout.getParent() instanceof Workspace) {
            Workspace workspace = (Workspace) mCellLayout.getParent();
            pairedCellLayout = workspace.getScreenPair(mCellLayout);
        } else {
            pairedCellLayout = null;
        }
        if (!animate) {
            lp.width = newWidth;
            lp.height = newHeight;
            lp.x = newX;
            lp.y = newY;
            for (int i = 0; i < HANDLE_COUNT; i++) {
                mDragHandles[i].setAlpha(1f);
            }
            if (pairedCellLayout != null) {
                updateInvalidResizeEffect(mCellLayout, pairedCellLayout, /* alpha= */ 1f,
                        /* springLoadedProgress= */ 0f);
            }
            requestLayout();
        } else {
            ObjectAnimator oa = ObjectAnimator.ofPropertyValuesHolder(lp,
                    PropertyValuesHolder.ofInt(LAYOUT_WIDTH, lp.width, newWidth),
                    PropertyValuesHolder.ofInt(LAYOUT_HEIGHT, lp.height, newHeight),
                    PropertyValuesHolder.ofInt(LAYOUT_X, lp.x, newX),
                    PropertyValuesHolder.ofInt(LAYOUT_Y, lp.y, newY));
            mFirstFrameAnimatorHelper.addTo(oa).addUpdateListener(a -> requestLayout());

            AnimatorSet set = new AnimatorSet();
            set.play(oa);
            for (int i = 0; i < HANDLE_COUNT; i++) {
                set.play(mFirstFrameAnimatorHelper.addTo(
                        ObjectAnimator.ofFloat(mDragHandles[i], ALPHA, 1f)));
            }
            if (pairedCellLayout != null) {
                updateInvalidResizeEffect(mCellLayout, pairedCellLayout, /* alpha= */ 1f,
                        /* springLoadedProgress= */ 0f, /* animatorSet= */ set);
            }
            set.setDuration(SNAP_DURATION);
            set.start();
        }

        setFocusableInTouchMode(true);
        requestFocus();
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        // Clear the frame and give focus to the widget host view when a directional key is pressed.
        if (shouldConsume(keyCode)) {
            close(false);
            mWidgetView.requestFocus();
            return true;
        }
        return false;
    }

    private boolean handleTouchDown(MotionEvent ev) {
        Rect hitRect = new Rect();
        int x = (int) ev.getX();
        int y = (int) ev.getY();

        getHitRect(hitRect);
        if (hitRect.contains(x, y)) {
            if (beginResizeIfPointInRegion(x - getLeft(), y - getTop())) {
                mXDown = x;
                mYDown = y;
                return true;
            }
        }
        return false;
    }

    private boolean isTouchOnReconfigureButton(MotionEvent ev) {
        int xFrame = (int) ev.getX() - getLeft();
        int yFrame = (int) ev.getY() - getTop();
        mReconfigureButton.getHitRect(sTmpRect);
        return sTmpRect.contains(xFrame, yFrame);
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        int x = (int) ev.getX();
        int y = (int) ev.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                return handleTouchDown(ev);
            case MotionEvent.ACTION_MOVE:
                visualizeResizeForDelta(x - mXDown, y - mYDown);
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                visualizeResizeForDelta(x - mXDown, y - mYDown);
                onTouchUp();
                mXDown = mYDown = 0;
                break;
        }
        return true;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN && handleTouchDown(ev)) {
            return true;
        }
        // Keep the resize frame open but let a click on the reconfigure button fall through to the
        // button's OnClickListener.
        if (isTouchOnReconfigureButton(ev)) {
            return false;
        }
        close(false);
        return false;
    }

    @Override
    protected void handleClose(boolean animate) {
        mDragLayer.removeView(this);
        if (mWidgetView != null) {
            mWidgetView.removeOnAttachStateChangeListener(mWidgetViewAttachStateChangeListener);
        }
    }

    private void updateInvalidResizeEffect(CellLayout cellLayout, CellLayout pairedCellLayout,
            float alpha, float springLoadedProgress) {
        updateInvalidResizeEffect(cellLayout, pairedCellLayout, alpha,
                springLoadedProgress, /* animatorSet= */ null);
    }

    private void updateInvalidResizeEffect(CellLayout cellLayout, CellLayout pairedCellLayout,
            float alpha, float springLoadedProgress, @Nullable AnimatorSet animatorSet) {
        int childCount = pairedCellLayout.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = pairedCellLayout.getChildAt(i);
            if (animatorSet != null) {
                animatorSet.play(
                        mFirstFrameAnimatorHelper.addTo(
                                ObjectAnimator.ofFloat(child, ALPHA, alpha)));
            } else {
                child.setAlpha(alpha);
            }
        }
        if (animatorSet != null) {
            animatorSet.play(mFirstFrameAnimatorHelper.addTo(
                    ObjectAnimator.ofFloat(cellLayout, SPRING_LOADED_PROGRESS,
                            springLoadedProgress)));
            animatorSet.play(mFirstFrameAnimatorHelper.addTo(
                    ObjectAnimator.ofFloat(pairedCellLayout, SPRING_LOADED_PROGRESS,
                            springLoadedProgress)));
        } else {
            cellLayout.setSpringLoadedProgress(springLoadedProgress);
            pairedCellLayout.setSpringLoadedProgress(springLoadedProgress);
        }

        boolean shouldShowCellLayoutBorder = springLoadedProgress > 0f;
        if (animatorSet != null) {
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    cellLayout.setIsDragOverlapping(shouldShowCellLayoutBorder);
                    pairedCellLayout.setIsDragOverlapping(shouldShowCellLayoutBorder);
                }
            });
        } else {
            cellLayout.setIsDragOverlapping(shouldShowCellLayoutBorder);
            pairedCellLayout.setIsDragOverlapping(shouldShowCellLayoutBorder);
        }
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_WIDGET_RESIZE_FRAME) != 0;
    }

    /**
     * A mutable class for describing the range of two int values.
     */
    private static class IntRange {

        public int start, end;

        public int clamp(int value) {
            return Utilities.boundToRange(value, start, end);
        }

        public void set(int s, int e) {
            start = s;
            end = e;
        }

        public int size() {
            return end - start;
        }

        /**
         * Moves either the start or end edge (but never both) by {@param delta} and  sets the
         * result in {@param out}
         */
        public void applyDelta(boolean moveStart, boolean moveEnd, int delta, IntRange out) {
            out.start = moveStart ? start + delta : start;
            out.end = moveEnd ? end + delta : end;
        }

        /**
         * Applies delta similar to {@link #applyDelta(boolean, boolean, int, IntRange)},
         * with extra conditions.
         * @param minSize minimum size after with the moving edge should not be shifted any further.
         *                For eg, if delta = -3 when moving the endEdge brings the size to less than
         *                minSize, only delta = -2 will applied
         * @param maxSize maximum size after with the moving edge should not be shifted any further.
         *                For eg, if delta = -3 when moving the endEdge brings the size to greater
         *                than maxSize, only delta = -2 will applied
         * @param maxEnd The maximum value to the end edge (start edge is always restricted to 0)
         * @return the amount of increase when endEdge was moves and the amount of decrease when
         * the start edge was moved.
         */
        public int applyDeltaAndBound(boolean moveStart, boolean moveEnd, int delta,
                int minSize, int maxSize, int maxEnd, IntRange out) {
            applyDelta(moveStart, moveEnd, delta, out);
            if (out.start < 0) {
                out.start = 0;
            }
            if (out.end > maxEnd) {
                out.end = maxEnd;
            }
            if (out.size() < minSize) {
                if (moveStart) {
                    out.start = out.end - minSize;
                } else if (moveEnd) {
                    out.end = out.start + minSize;
                }
            }
            if (out.size() > maxSize) {
                if (moveStart) {
                    out.start = out.end - maxSize;
                } else if (moveEnd) {
                    out.end = out.start + maxSize;
                }
            }
            return moveEnd ? out.size() - size() : size() - out.size();
        }
    }

    /**
     * Returns true only if this utility class handles the key code.
     */
    public static boolean shouldConsume(int keyCode) {
        return (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_MOVE_HOME || keyCode == KeyEvent.KEYCODE_MOVE_END
                || keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_PAGE_DOWN);
    }

    @Nullable private ArrowTipView showReconfigurableWidgetEducationTip() {
        Rect rect = new Rect();
        if (!mReconfigureButton.getGlobalVisibleRect(rect)) {
            return null;
        }
        @Px int tipMargin = mLauncher.getResources()
                .getDimensionPixelSize(R.dimen.widget_reconfigure_tip_top_margin);
        return new ArrowTipView(mLauncher, /* isPointingUp= */ true)
                .showAroundRect(
                        getContext().getString(R.string.reconfigurable_widget_education_tip),
                        /* arrowXCoord= */ rect.left + mReconfigureButton.getWidth() / 2,
                        /* rect= */ rect,
                        /* margin= */ tipMargin);
    }

    private boolean hasSeenReconfigurableWidgetEducationTip() {
        return mLauncher.getSharedPrefs()
                .getBoolean(KEY_RECONFIGURABLE_WIDGET_EDUCATION_TIP_SEEN, false)
                || Utilities.IS_RUNNING_IN_TEST_HARNESS;
    }
}
