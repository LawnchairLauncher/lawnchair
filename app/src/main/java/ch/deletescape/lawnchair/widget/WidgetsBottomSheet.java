package ch.deletescape.lawnchair.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.TextView;

import java.util.List;

import ch.deletescape.lawnchair.AbstractFloatingView;
import ch.deletescape.lawnchair.DropTarget;
import ch.deletescape.lawnchair.Insettable;
import ch.deletescape.lawnchair.ItemInfo;
import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.LauncherAnimUtils;
import ch.deletescape.lawnchair.LauncherAppState;
import ch.deletescape.lawnchair.PagedView;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.allapps.VerticalPullDetector;
import ch.deletescape.lawnchair.anim.PropertyListBuilder;
import ch.deletescape.lawnchair.config.FeatureFlags;
import ch.deletescape.lawnchair.dragndrop.DragController;
import ch.deletescape.lawnchair.dragndrop.DragOptions;
import ch.deletescape.lawnchair.model.WidgetItem;
import ch.deletescape.lawnchair.util.PackageUserKey;
import ch.deletescape.lawnchair.util.TouchController;

public class WidgetsBottomSheet extends AbstractFloatingView implements Insettable, TouchController, VerticalPullDetector.Listener, OnClickListener, OnLongClickListener, DragController.DragListener {
    private Interpolator mFastOutSlowInInterpolator;
    private Rect mInsets;
    private Launcher mLauncher;
    private ObjectAnimator mOpenCloseAnimator;
    private ItemInfo mOriginalItemInfo;
    private PagedView.ScrollInterpolator mScrollInterpolator;
    private int mTranslationYClosed;
    private int mTranslationYOpen;
    private float mTranslationYRange;
    private VerticalPullDetector mVerticalPullDetector;
    private boolean mWasNavBarLight;

    private int mNavHeight;
    private Paint mNavPaint;
    private Path mNavPath;

    final class C05151 extends AnimatorListenerAdapter {
        C05151() {
        }

        @Override
        public void onAnimationEnd(Animator animator) {
            WidgetsBottomSheet.this.mVerticalPullDetector.finishedScrolling();
        }
    }

    final class C05162 extends AnimatorListenerAdapter {
        C05162() {
        }

        @Override
        public void onAnimationEnd(Animator animator) {
            WidgetsBottomSheet.this.mIsOpen = false;
            WidgetsBottomSheet.this.mVerticalPullDetector.finishedScrolling();
            ((ViewGroup) WidgetsBottomSheet.this.getParent()).removeView(WidgetsBottomSheet.this);
            WidgetsBottomSheet.this.setLightNavBar(WidgetsBottomSheet.this.mWasNavBarLight);
        }
    }

    public WidgetsBottomSheet(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public WidgetsBottomSheet(Context context, AttributeSet attributeSet, int i) {
        super(new ContextThemeWrapper(context, getTheme()), attributeSet, i);
        setWillNotDraw(false);
        this.mLauncher = Launcher.getLauncher(context);
        this.mOpenCloseAnimator = LauncherAnimUtils.ofPropertyValuesHolder(this);
        this.mFastOutSlowInInterpolator = new FastOutSlowInInterpolator();
        this.mScrollInterpolator = new PagedView.ScrollInterpolator();
        this.mInsets = new Rect();
        this.mVerticalPullDetector = new VerticalPullDetector(context);
        this.mVerticalPullDetector.setListener(this);

        if (!Utilities.ATLEAST_OREO && !FeatureFlags.INSTANCE.getUseDarkTheme()) {
            setWillNotDraw(false);
            mNavHeight = getResources().getDimensionPixelSize(R.dimen.navigation_bar_height);
            mNavPaint = new Paint();
            mNavPaint.setColor(Color.BLACK);
            mNavPath = new Path();
        }
    }

    private static int getTheme() {
        return FeatureFlags.INSTANCE.getUseDarkTheme() ? R.style.WidgetContainerTheme_Dark : R.style.WidgetContainerTheme;
    }

    @Override
    protected void onMeasure(int i, int i2) {
        super.onMeasure(i, i2);
        this.mTranslationYOpen = 0;
        this.mTranslationYClosed = getMeasuredHeight();
        this.mTranslationYRange = (float) (this.mTranslationYClosed - this.mTranslationYOpen);
    }

    public void populateAndShow(ItemInfo itemInfo) {
        this.mOriginalItemInfo = itemInfo;
        ((TextView) findViewById(R.id.title)).setText(getContext().getString(R.string.widgets_bottom_sheet_title, new Object[]{this.mOriginalItemInfo.title}));
        onWidgetsBound();
        this.mWasNavBarLight = (this.mLauncher.getWindow().getDecorView().getSystemUiVisibility() & 16) != 0;
        this.mLauncher.getDragLayer().addView(this);
        measure(0, 0);
        setTranslationY((float) this.mTranslationYClosed);
        this.mIsOpen = false;
        open(true);
    }

    @Override
    protected void onWidgetsBound() {
        List widgetsForPackageUser = this.mLauncher.getWidgetsForPackageUser(new PackageUserKey(this.mOriginalItemInfo.getTargetComponent().getPackageName(), this.mOriginalItemInfo.user));
        ViewGroup viewGroup = findViewById(R.id.widgets);
        ViewGroup viewGroup2 = viewGroup.findViewById(R.id.widgets_cell_list);
        viewGroup2.removeAllViews();
        for (int i = 0; i < widgetsForPackageUser.size(); i++) {
            WidgetCell addItemCell = addItemCell(viewGroup2);
            addItemCell.applyFromCellItem((WidgetItem) widgetsForPackageUser.get(i), LauncherAppState.getInstance().getWidgetCache());
            addItemCell.ensurePreview();
            addItemCell.setVisibility(VISIBLE);
            if (i < widgetsForPackageUser.size() - 1) {
                addDivider(viewGroup2);
            }
        }
        if (widgetsForPackageUser.size() == 1) {
            ((LayoutParams) viewGroup.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
            return;
        }
        View inflate = LayoutInflater.from(getContext()).inflate(R.layout.widget_list_divider, viewGroup, false);
        inflate.getLayoutParams().width = Utilities.pxFromDp(16.0f, getResources().getDisplayMetrics());
        viewGroup2.addView(inflate, 0);
    }

    private void addDivider(ViewGroup viewGroup) {
        LayoutInflater.from(getContext()).inflate(R.layout.widget_list_divider, viewGroup, true);
    }

    private WidgetCell addItemCell(ViewGroup viewGroup) {
        WidgetCell widgetCell = (WidgetCell) LayoutInflater.from(getContext()).inflate(R.layout.widget_cell, viewGroup, false);
        widgetCell.setOnClickListener(this);
        widgetCell.setOnLongClickListener(this);
        //widgetCell.setAnimatePreview(false);
        viewGroup.addView(widgetCell);
        return widgetCell;
    }

    @Override
    public void onClick(View view) {
        this.mLauncher.getWidgetsView().handleClick();
    }

    @Override
    public boolean onLongClick(View view) {
        this.mLauncher.getDragController().addDragListener(this);
        return this.mLauncher.getWidgetsView().handleLongClick(view);
    }

    private void open(boolean z) {
        if (!this.mIsOpen && !this.mOpenCloseAnimator.isRunning()) {
            this.mIsOpen = true;
            setLightNavBar(true);
            if (z) {
                this.mOpenCloseAnimator.setValues(new PropertyListBuilder().translationY((float) this.mTranslationYOpen).build());
                this.mOpenCloseAnimator.addListener(new C05151());
                this.mOpenCloseAnimator.setInterpolator(this.mFastOutSlowInInterpolator);
                this.mOpenCloseAnimator.start();
            } else {
                setTranslationY((float) this.mTranslationYOpen);
            }
        }
    }

    @Override
    protected void handleClose(boolean z) {
        if (this.mIsOpen && !this.mOpenCloseAnimator.isRunning()) {
            if (z) {
                this.mOpenCloseAnimator.setValues(new PropertyListBuilder().translationY((float) this.mTranslationYClosed).build());
                this.mOpenCloseAnimator.addListener(new C05162());
                this.mOpenCloseAnimator.setInterpolator(this.mVerticalPullDetector.isIdleState() ? this.mFastOutSlowInInterpolator : this.mScrollInterpolator);
                this.mOpenCloseAnimator.start();
            } else {
                setTranslationY((float) this.mTranslationYClosed);
                setLightNavBar(this.mWasNavBarLight);
                this.mIsOpen = false;
            }
        }
    }

    private void setLightNavBar(boolean z) {
        mLauncher.activateLightSystemBars(z, false, true);
    }

    @Override
    protected boolean isOfType(int i) {
        return (i & 4) != 0;
    }

    public static WidgetsBottomSheet getOpen(Launcher launcher) {
        return (WidgetsBottomSheet) AbstractFloatingView.getOpenView(launcher, 4);
    }

    @Override
    public void setInsets(Rect rect) {
        int i = rect.left - this.mInsets.left;
        int i2 = rect.right - this.mInsets.right;
        int i3 = rect.bottom - this.mInsets.bottom;
        this.mInsets.set(rect);
        setPadding(i + getPaddingLeft(), getPaddingTop(), i2 + getPaddingRight(), i3 + getPaddingBottom());
    }

    @Override
    public void onDragStart(boolean z) {
    }

    @Override
    public boolean onDrag(float f, float f2) {
        setTranslationY(Utilities.boundToRange(f, (float) this.mTranslationYOpen, (float) this.mTranslationYClosed));
        return true;
    }

    @Override
    public void onDragEnd(float f, boolean z) {
        if ((!z || f <= 0.0f) && getTranslationY() <= this.mTranslationYRange / 2.0f) {
            this.mIsOpen = false;
            this.mOpenCloseAnimator.setDuration(this.mVerticalPullDetector.calculateDuration(f, (getTranslationY() - ((float) this.mTranslationYOpen)) / this.mTranslationYRange));
            open(true);
            return;
        }
        //this.mScrollInterpolator.setVelocityAtZero(f);
        this.mOpenCloseAnimator.setDuration(this.mVerticalPullDetector.calculateDuration(f, (((float) this.mTranslationYClosed) - getTranslationY()) / this.mTranslationYRange));
        close(true);
    }

    public boolean onControllerTouchEvent(MotionEvent motionEvent) {
        return this.mVerticalPullDetector.onTouchEvent(motionEvent);
    }

    public boolean onControllerInterceptTouchEvent(MotionEvent motionEvent) {
        int i;
        if (this.mVerticalPullDetector.isIdleState()) {
            i = 2;
        } else {
            i = 0;
        }
        this.mVerticalPullDetector.setDetectableScrollConditions(i, false);
        this.mVerticalPullDetector.onTouchEvent(motionEvent);
        return this.mVerticalPullDetector.isDraggingOrSettling();
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions dragOptions) {
        close(true);
    }

    @Override
    public void onDragEnd() {
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        if (mNavPath == null) return;
        int width = r - l, top = b - t - mNavHeight;
        mNavPath.moveTo(0, top);
        mNavPath.lineTo(width, top);
        mNavPath.lineTo(width, b - t);
        mNavPath.lineTo(0, b - t);
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        if (mNavPath == null) return;
        canvas.drawPath(mNavPath, mNavPaint);
    }
}