package com.google.android.apps.nexuslauncher.allapps;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.util.AttributeSet;
import android.util.Property;
import ch.deletescape.lawnchair.LawnchairPreferences;
import com.android.launcher3.*;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.allapps.FloatingHeaderView;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.anim.PropertySetter;
import com.android.quickstep.AnimatedFloat;
import com.google.android.apps.nexuslauncher.CustomAppPredictor;
import com.google.android.apps.nexuslauncher.allapps.PredictionRowView.DividerType;

public class PredictionsFloatingHeader extends FloatingHeaderView implements Insettable, CustomAppPredictor.UiManager.Listener {
    private static final Property<PredictionsFloatingHeader, Float> CONTENT_ALPHA =
            new Property<PredictionsFloatingHeader, Float>(Float.class, "contentAlpha") {
                @Override
                public Float get(PredictionsFloatingHeader predictionsFloatingHeader) {
                    return predictionsFloatingHeader.mContentAlpha;
                }

                @Override
                public void set(PredictionsFloatingHeader object, Float value) {
                    object.mContentAlpha = value;
                    object.mTabLayout.setAlpha(value);
                }
            };
    private boolean AF;
    public boolean AI;
    private final CustomAppPredictor.UiManager Bl;
    private final int Bm;
    public PredictionRowView Bn;
    private float mContentAlpha = 1f;
    private boolean mIsVerticalLayout;
    private boolean mPredictionsEnabled;

    public PredictionsFloatingHeader(Context context) {
        this(context, null);
    }

    public PredictionsFloatingHeader(Context context, AttributeSet attrs) {
        super(context, attrs);

        Bm = context.getResources().getDimensionPixelSize(R.dimen.all_apps_header_top_padding);
        Bl = ((CustomAppPredictor) Launcher.getLauncher(context).getUserEventDispatcher()).getUiManager();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        Bn = findViewById(R.id.prediction_row);
        Bn.setPredictor(Bl);
    }

    @Override
    public void setup(AllAppsContainerView.AdapterHolder[] mAH, boolean tabsHidden) {
        boolean z2 = this.Bl.isEnabled();
        Bn.AD = this;
        Bn.s(z2);
        mTabsHidden = tabsHidden;
        dg();
        super.setup(mAH, tabsHidden);
    }

    private void dg() {
        mPredictionsEnabled = Bl.isEnabled();
        int i = 0;
        DividerType dividerType = DividerType.NONE;
        if (Utilities.ATLEAST_MARSHMALLOW && this.mTabsHidden) {
            dividerType = DividerType.ALL_APPS_LABEL;
        } else if (this.mTabsHidden) {
            dividerType = DividerType.LINE;
        }
        PredictionRowView predictionRowView = this.Bn;
        predictionRowView.s(mPredictionsEnabled);
        if (predictionRowView.Ba != dividerType) {
            if (dividerType == DividerType.ALL_APPS_LABEL) {
                predictionRowView.AV.setAntiAlias(true);
                predictionRowView.AV.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                predictionRowView.AV.setTextSize((float) predictionRowView.getResources().getDimensionPixelSize(R.dimen.all_apps_label_text_size));
                CharSequence text = predictionRowView.getResources().getText(R.string.all_apps_label);
                predictionRowView.AG = StaticLayout.Builder.obtain(text, 0, text.length(), predictionRowView.AV, Math.round(predictionRowView.AV.measureText(text.toString()))).setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(1).setIncludePad(true).build();
            } else {
                predictionRowView.AG = null;
            }
        }
        predictionRowView.Ba = dividerType;
        int dimensionPixelSize = predictionRowView.Ba == DividerType.LINE ? predictionRowView.getResources().getDimensionPixelSize(R.dimen.all_apps_prediction_row_divider_height) : predictionRowView.Ba == DividerType.ALL_APPS_LABEL ? (predictionRowView.AG.getHeight() + predictionRowView.getResources().getDimensionPixelSize(R.dimen.all_apps_label_top_padding)) + predictionRowView.getResources().getDimensionPixelSize(R.dimen.all_apps_label_bottom_padding) : 0;
        predictionRowView.setPadding(predictionRowView.getPaddingLeft(), predictionRowView.getPaddingTop(), predictionRowView.getPaddingRight(), dimensionPixelSize);
        dimensionPixelSize = Bn.getExpectedHeight();
        mMaxTranslation = dimensionPixelSize + i;
    }

    @Override
    public int getMaxTranslation() {
        if (mMaxTranslation == 0 && mTabsHidden) {
            return getResources().getDimensionPixelSize(R.dimen.all_apps_search_bar_bottom_padding);
        }
        if (mMaxTranslation <= 0 || !mTabsHidden) {
            return mMaxTranslation;
        }
        return mMaxTranslation + getPaddingTop();
    }

    @Override
    public void setInsets(Rect insets) {
        DeviceProfile profile = Launcher.getLauncher(getContext()).getDeviceProfile();
        int i = profile.desiredWorkspaceLeftRightMarginPx + profile.cellLayoutPaddingLeftRightPx;
        Bn.setPadding(i, Bn.getPaddingTop(), i, Bn.getPaddingBottom());
        mIsVerticalLayout = profile.isVerticalBarLayout();
    }

    public final void updateLayout() {
        int oldMaxTranslation = mMaxTranslation;
        dg();
        if (mMaxTranslation != oldMaxTranslation) {
            Launcher.getLauncher(getContext()).getAppsView().setupHeader();
        }
    }

    @Override
    protected void applyScroll(int uncappedY, int currentY) {
        if (uncappedY < currentY - Bm) {
            Bn.setHidden(true);
            return;
        }
        Bn.setHidden(false);
        LawnchairPreferences prefs = Utilities.getLawnchairPrefs(getContext());
        Bn.Bc = uncappedY - (prefs.getDockSearchBar() ? 0 : Bm);
        Bn.df();
    }

    @Override
    public void setContentVisibility(boolean hasHeader, boolean hasContent, PropertySetter setter) {
        if (hasHeader && !hasContent && AI) {
            Launcher.getLauncher(getContext()).getAppsView().getSearchUiManager().resetSearch();
        }
        allowTouchForwarding(hasContent);
        float f = 1.0f;
        setter.setFloat(this, CONTENT_ALPHA, hasContent ? 1.0f : 0.0f, Interpolators.LINEAR);
        PredictionRowView predictionRowView = this.Bn;
        int i = 0;
        int i2 = predictionRowView.getAlpha() > 0.0f ? 1 : 0;
        if (!hasHeader) {
            i = predictionRowView.AU;
        } else if (hasContent) {
            i = predictionRowView.AT;
        }
        if (i2 == 0) {
            predictionRowView.ax(i);
        } else {
            setter.setInt(predictionRowView, PredictionRowView.AM, i, Interpolators.LINEAR);
        }
        Property<AnimatedFloat, Float> property = AnimatedFloat.VALUE;
        hasContent = hasHeader && !hasContent;
        setter.setFloat(predictionRowView.Be, property, hasContent ? 1f : 0f, Interpolators.LINEAR);
        Property<AnimatedFloat, Float> property2 = AnimatedFloat.VALUE;
        if (!hasHeader) {
            f = 0.0f;
        }
        setter.setFloat(predictionRowView.Bd, property2, f, Interpolators.LINEAR);
    }

    @Override
    public boolean hasVisibleContent() {
        return Bl.isEnabled();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        Bl.addListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        Bl.removeListener(this);
    }

    @Override
    public void onPredictionsUpdated() {
        post(Bn::updatePredictions);
    }
}
