package com.google.android.apps.nexuslauncher.allapps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.ColorUtils;
import android.text.Layout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Property;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.Interpolator;
import android.widget.LinearLayout;
import ch.deletescape.lawnchair.LawnchairPreferences;
import com.android.launcher3.*;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.allapps.AllAppsStore.OnUpdateListener;
import com.android.launcher3.keyboard.FocusIndicatorHelper;
import com.android.launcher3.keyboard.FocusIndicatorHelper.SimpleFocusIndicatorHelper;
import com.android.launcher3.logging.UserEventDispatcher.LogContainerProvider;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.util.ComponentKeyMapper;
import com.android.launcher3.util.Themes;
import com.android.quickstep.AnimatedFloat;
import com.google.android.apps.nexuslauncher.CustomAppPredictor;

import java.util.ArrayList;
import java.util.List;

public class PredictionRowView extends LinearLayout implements OnDeviceProfileChangeListener, OnUpdateListener, LogContainerProvider {
    static final Property<PredictionRowView, Integer> AM = new Property<PredictionRowView, Integer>(Integer.class, "textAlpha") {
        @Override
        public void set(PredictionRowView object, Integer value) {
            object.ax(value);
        }

        @Override
        public Integer get(PredictionRowView predictionRowView) {
            return predictionRowView.AU;
        }
    };
    private static final Interpolator AN = PredictionRowView::interpolate;
    PredictionsFloatingHeader AD;
    Layout AG;
    public boolean AI;
    private final int AO;
    public final List AP;
    public final ArrayList<ItemInfoWithIcon> AQ;
    private final FocusIndicatorHelper AR;
    private final int AS;
    final int AT;
    int AU;
    final TextPaint AV;
    private final int AW;
    private final int AX;
    private int AY;
    private final int AZ;
    DividerType Ba;
    private boolean mHidden;
    float Bc;
    final AnimatedFloat Bd;
    final AnimatedFloat Be;
    private View Bf;
    private boolean Bg;
    private final Launcher mLauncher;
    private final Paint mPaint;
    private CustomAppPredictor.UiManager mPredictor;

    public void setPredictor(CustomAppPredictor.UiManager predictor) {
        mPredictor = predictor;
    }

    public enum DividerType {
        NONE,
        LINE,
        ALL_APPS_LABEL
    }

    static /* synthetic */ float interpolate(float f) {
        return f < 0.8f ? 0.0f : (f - 0.8f) / 0.2f;
    }

    public PredictionRowView(Context context) {
        this(context, null);
    }

    public PredictionRowView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.AP = new ArrayList();
        this.AQ = new ArrayList();
        this.AV = new TextPaint();
        this.Bc = 0f;
        this.Bd = new AnimatedFloat(this::df);
        this.Be = new AnimatedFloat(this::df);
        this.AI = false;
        this.Bg = false;
        setOrientation(LinearLayout.HORIZONTAL);
        setWillNotDraw(false);
        this.mPaint = new Paint();
        this.mPaint.setColor(Themes.getAttrColor(context, 16843820));
        this.mPaint.setStrokeWidth((float) getResources().getDimensionPixelSize(R.dimen.all_apps_divider_height));
        this.AZ = this.mPaint.getColor();
        this.AR = new SimpleFocusIndicatorHelper(this);
        this.AO = LauncherAppState.getInstance(context).getInvariantDeviceProfile().numColumns;
        this.mLauncher = Launcher.getLauncher(context);
        this.mLauncher.addOnDeviceProfileChangeListener(this);
        this.AS = Themes.getAttrColor(context, 16842808);
        this.AT = Color.alpha(this.AS);
        this.AU = this.AT;
        this.AV.setColor(ContextCompat.getColor(getContext(), Themes.getAttrBoolean(getContext(), R.attr.isMainColorDark) ? R.color.all_apps_label_text_dark : R.color.all_apps_label_text));
        this.AW = this.AV.getColor();
        this.AX = Color.alpha(this.AW);
        this.AY = this.AX;
        da();
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        dd().addUpdateListener(this);
        dd().registerIconContainer(this);
        measure(MeasureSpec.EXACTLY, MeasureSpec.EXACTLY);
    }

    private AllAppsStore dd() {
        return this.mLauncher.getAppsView().getAppsStore();
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        dd().removeUpdateListener(this);
        dd().unregisterIconContainer(this);
    }

    public final void s(boolean z) {
        if (z != this.Bg) {
            this.Bg = z;
            da();
        }
    }

    public final void da() {
        setVisibility((!this.Bg || this.AI) ? View.GONE : View.VISIBLE);
    }

    protected void onMeasure(int i, int i2) {
        super.onMeasure(i, MeasureSpec.makeMeasureSpec(getExpectedHeight(), MeasureSpec.EXACTLY));
    }

    protected void dispatchDraw(Canvas canvas) {
        this.AR.draw(canvas);
        super.dispatchDraw(canvas);
    }

    public final int getExpectedHeight() {
        if (getVisibility() == View.GONE) {
            return 0;
        }
        DeviceProfile dp = Launcher.getLauncher(getContext()).getDeviceProfile();
        LawnchairPreferences prefs = Utilities.getLawnchairPrefs(getContext());
        if (prefs.getDockHide()) {
            return dp.allAppsCellHeightPx;
        } else if (prefs.getDockSearchBar()) {
            return dp.allAppsCellHeightPx + getPaddingTop() + getPaddingBottom();
        } else {
            return dp.hotseatBarSizePx;
        }
    }

    public void onDeviceProfileChanged(DeviceProfile deviceProfile) {
        removeAllViews();
        de();
    }

    public void updatePredictions() {
        ArrayList<ItemInfoWithIcon> predictions = new ArrayList<>();
        for (ComponentKeyMapper mapper : mPredictor.getPredictions()) {
            predictions.add(dd().getApp(mapper.getKey()));
        }
        if (!predictions.equals(AQ)) {
            AQ.clear();
            AQ.addAll(predictions);
            de();
        }
    }

    @Override
    public void onAppsUpdated() {
        updatePredictions();
    }

    private void de() {
        if (this.Bf != null) {
            removeView(this.Bf);
        }
        if (getChildCount() != this.AO) {
            while (getChildCount() > this.AO) {
                removeViewAt(0);
            }
            while (getChildCount() < this.AO) {
                BubbleTextView bubbleTextView = (BubbleTextView) this.mLauncher.getLayoutInflater().inflate(R.layout.all_apps_icon, this, false);
                bubbleTextView.setOnClickListener(ItemClickHandler.INSTANCE);
                bubbleTextView.setOnLongClickListener(ItemLongClickListener.INSTANCE_ALL_APPS);
                bubbleTextView.setLongPressTimeout(ViewConfiguration.getLongPressTimeout());
                bubbleTextView.setOnFocusChangeListener(this.AR);
                LayoutParams layoutParams = (LayoutParams) bubbleTextView.getLayoutParams();
                layoutParams.height = this.mLauncher.getDeviceProfile().allAppsCellHeightPx;
                layoutParams.width = 0;
                layoutParams.weight = 1.0f;
                addView(bubbleTextView);
            }
        }
        int size = this.AQ.size();
        int f = ColorUtils.setAlphaComponent(this.AS, this.AU);
        for (int i = 0; i < getChildCount(); i++) {
            BubbleTextView bubbleTextView2 = (BubbleTextView) getChildAt(i);
            bubbleTextView2.reset();
            if (size > i) {
                bubbleTextView2.setVisibility(View.VISIBLE);
                if (this.AQ.get(i) instanceof AppInfo) {
                    bubbleTextView2.applyFromApplicationInfo((AppInfo) this.AQ.get(i));
                } else if (this.AQ.get(i) instanceof ShortcutInfo) {
                    bubbleTextView2.applyFromShortcutInfo((ShortcutInfo) this.AQ.get(i), false);
                }
                bubbleTextView2.setTextColor(f);
            } else {
                bubbleTextView2.setVisibility(size == 0 ? View.GONE : View.INVISIBLE);
            }
        }
        if (size == 0) {
            if (this.Bf == null) {
                this.Bf = LayoutInflater.from(getContext()).inflate(R.layout.prediction_load_progress, this, false);
            }
            addView(this.Bf);
        } else {
            this.Bf = null;
        }
        this.AD.updateLayout();
    }

    protected void onDraw(Canvas canvas) {
        if (this.Ba == DividerType.LINE) {
            int dimensionPixelSize = getResources().getDimensionPixelSize(R.dimen.dynamic_grid_edge_margin);
            float height = (float) (getHeight() - (getPaddingBottom() / 2));
            Canvas canvas2 = canvas;
            float f = height;
            canvas2.drawLine((float) (getPaddingLeft() + dimensionPixelSize), f, (float) ((getWidth() - getPaddingRight()) - dimensionPixelSize), height, this.mPaint);
            return;
        }
        if (this.Ba == DividerType.ALL_APPS_LABEL) {
            int dimensionPixelSize = (getWidth() / 2) - (this.AG.getWidth() / 2);
            int height2 = (getHeight() - getResources().getDimensionPixelSize(R.dimen.all_apps_label_bottom_padding)) - this.AG.getHeight();
            canvas.translate((float) dimensionPixelSize, (float) height2);
            this.AG.draw(canvas);
            canvas.translate((float) (-dimensionPixelSize), (float) (-height2));
        }
    }

    public final void setHidden(boolean z) {
        this.mHidden = z;
        df();
    }

    public final void ax(int i) {
        this.AU = i;
        int f = ColorUtils.setAlphaComponent(this.AS, this.AU);
        if (this.Bf == null) {
            for (int i2 = 0; i2 < getChildCount(); i2++) {
                ((BubbleTextView) getChildAt(i2)).setTextColor(f);
            }
        }
        i = ColorUtils.setAlphaComponent(this.AZ, Math.round(((float) (Color.alpha(this.AZ) * i)) / 255f));
        if (i != this.mPaint.getColor()) {
            this.mPaint.setColor(i);
            this.AY = Math.round((float) ((this.AX * this.AU) / this.AT));
            this.AV.setColor(ColorUtils.setAlphaComponent(this.AW, this.AY));
            if (this.Ba != DividerType.NONE) {
                invalidate();
            }
        }
    }

    public boolean hasOverlappingRendering() {
        return false;
    }

    void df() {
        setTranslationY((1.0f - this.Be.value) * this.Bc);
        float interpolation = AN.getInterpolation(this.Be.value);
        setAlpha(this.Bd.value * (interpolation + ((1.0f - interpolation) * (this.mHidden ? 0f : 1f))));
    }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, Target target, Target targetParent) {

    }
}
