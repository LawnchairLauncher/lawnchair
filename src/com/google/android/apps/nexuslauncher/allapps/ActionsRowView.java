package com.google.android.apps.nexuslauncher.allapps;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.ColorUtils;
import android.text.Layout;
import android.text.Layout.Alignment;
import android.text.StaticLayout.Builder;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import ch.deletescape.lawnchair.allapps.PredictionsDividerLayout;
import ch.deletescape.lawnchair.font.CustomFontManager;
import ch.deletescape.lawnchair.font.FontLoader.FontReceiver;
import ch.deletescape.lawnchair.predictions.LawnchairEventPredictor;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.logging.UserEventDispatcher.LogContainerProvider;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.Themes;
import com.android.quickstep.TouchInteractionService;
import com.google.android.apps.nexuslauncher.allapps.ActionsController.UpdateListener;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class ActionsRowView extends PredictionsDividerLayout implements UpdateListener, LogContainerProvider,
        FontReceiver {
    private static final boolean DEBUG = false;
    private static final String TAG = "ActionsRowView";
    private LauncherAccessibilityDelegate mActionAccessibilityDelegate;
    private ActionsController mActionsController;
    private Layout mAllAppsLabelLayout;
    private boolean mDisabled;
    private boolean mHidden;
    private boolean mIsCollapsed;
    private boolean mIsDarkTheme;
    private final Launcher mLauncher;
    private PredictionsFloatingHeader mParent;
    private boolean mShowAllAppsLabel;
    private int mSpacing;
    private Typeface mAllAppsLabelTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    public ActionsRowView(@NonNull Context context) {
        this(context, null);
    }

    public ActionsRowView(@NonNull Context context, @Nullable AttributeSet attributeSet) {
        super(context, attributeSet);
        this.mIsCollapsed = false;
        setOrientation(LinearLayout.HORIZONTAL);
        this.mLauncher = Launcher.getLauncher(getContext());
        this.mIsDarkTheme = Themes.getAttrBoolean(mLauncher, R.attr.isMainColorDark);
        this.mSpacing = context.getResources().getDimensionPixelSize(R.dimen.all_apps_action_spacing);
        this.mActionAccessibilityDelegate = new LauncherAccessibilityDelegate(this.mLauncher) {
            public void addSupportedActions(View view, AccessibilityNodeInfo accessibilityNodeInfo, boolean z) {
                accessibilityNodeInfo.addAction(this.mActions.get(R.id.action_dismiss_suggestion));
                accessibilityNodeInfo.addAction(this.mActions.get(R.id.dismiss_drop_target_label));
            }

            public boolean performAction(View view, ItemInfo itemInfo, int i) {
                if (i != R.id.action_add_to_workspace) {
                    return super.performAction(view, itemInfo, i);
                }
                final int[] iArr = new int[2];
                final long findSpaceOnWorkspace = findSpaceOnWorkspace(itemInfo, iArr);
                final ItemInfo itemInfo2 = itemInfo;
                ActionsRowView.this.mLauncher.getStateManager().goToState(LauncherState.NORMAL, true,
                        () -> {
                            mLauncher.getModelWriter().addItemToDatabase(
                                    itemInfo2, -100, findSpaceOnWorkspace, iArr[0], iArr[1]);
                            List<ItemInfo> arrayList = new ArrayList<>();
                            arrayList.add(itemInfo2);
                            mLauncher.bindItems(arrayList, true);
                            mLauncher.getDragLayer().announceForAccessibility(ActionsRowView.this.mLauncher.getResources().getString(R.string.item_added_to_workspace));
                        });
                return true;
            }
        };
        this.mActionAccessibilityDelegate.addAccessibilityAction(R.id.action_dismiss_suggestion, R.string.dismiss_drop_target_label);
        
        CustomFontManager.Companion.getInstance(context).setCustomFont(this, CustomFontManager.FONT_DRAWER_TAB);
    }

    public void setup(PredictionsFloatingHeader predictionsFloatingHeader) {
        this.mParent = predictionsFloatingHeader;
        updateVisibility();
    }

    public int getExpectedHeight() {
        if (!shouldDraw()) {
            return 0;
        }
        ActionView actionView = (ActionView) getChildAt(0);
        return (((getPaddingTop() + getPaddingBottom()) + Math.max(actionView.getLineHeight(), actionView.getIconSize())) + actionView.getPaddingTop()) + actionView.getPaddingBottom();
    }

    public void fillInLogContainerData(View view, ItemInfo itemInfo, Target target, Target target2) {
        target2.containerType = 7;
    }

    protected void onMeasure(int i, int i2) {
        DeviceProfile deviceProfile = this.mLauncher.getDeviceProfile();
        i = getRowWidth(MeasureSpec.getSize(i));
        super.onMeasure(MeasureSpec.makeMeasureSpec(((i - (DeviceProfile.calculateCellWidth(i, deviceProfile.inv.numHotseatIcons) - Math.round(((float) deviceProfile.iconSizePx) * 0.92f))) + getPaddingLeft()) + getPaddingRight(), MeasureSpec.EXACTLY), i2);
    }

    private int getRowWidth(int i) {
        if (this.mLauncher.getDeviceProfile().isVerticalBarLayout()) {
            return (i - this.mLauncher.getAppsView().getActiveRecyclerView().getPaddingLeft()) - this.mLauncher.getAppsView().getActiveRecyclerView().getPaddingRight();
        }
        View layout = this.mLauncher.getHotseat().getLayout();
        return (i - layout.getPaddingLeft()) - layout.getPaddingRight();
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.mActionsController = ActionsController.get(getContext());
        if (PackageManagerHelper.isAppEnabled(getContext().getPackageManager(), LawnchairEventPredictor.ACTIONS_PACKAGE, 0)
                && TouchInteractionService.isConnected()) {
            // Only set listener if it is even just remotely possible for us to receive actions
            this.mActionsController.setListener(this);
            onUpdated(this.mActionsController.getActions());
        }
        mLauncher.getUserEventDispatcher().updateActions();
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        this.mActionsController.setListener(null);
    }

    @MainThread
    public void onUpdated(ArrayList<Action> arrayList) {
        int i;
        int min = Math.min(2, arrayList.size());
        if (getChildCount() != min) {
            while (getChildCount() > min) {
                removeViewAt(0);
            }
            while (getChildCount() < min) {
                ActionView actionView = (ActionView) LayoutInflater.from(getContext()).inflate(R.layout.all_apps_actions_view, this, false);
                if (this.mIsDarkTheme) {
                    GradientDrawable gradientDrawable = (GradientDrawable) actionView.getBackground();
                    gradientDrawable.mutate();
                    gradientDrawable.setColor(0x33FFFFFF);
                }
                LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -1);
                layoutParams.weight = 1.0f;
                actionView.setLayoutParams(layoutParams);
                actionView.setAccessibilityDelegate(this.mActionAccessibilityDelegate);
                addView(actionView);
            }
            i = 0;
            while (i < min) {
                ((LinearLayout.LayoutParams) getChildAt(i).getLayoutParams()).setMarginEnd(i < min + -1 ? this.mSpacing : 0);
                i++;
            }
        }
        for (i = 0; i < getChildCount(); i++) {
            ActionView actionView2 = (ActionView) getChildAt(i);
            actionView2.reset();
            if (min > i) {
                actionView2.setVisibility(View.VISIBLE);
                Action action = arrayList.get(i);
                ShortcutInfo shortcutInfo = action.shortcutInfo;
                shortcutInfo.contentDescription = getContext().getString(R.string.suggested_action_content_description,
                        action.contentDescription, action.openingPackageDescription);
                actionView2.applyFromShortcutInfo(shortcutInfo);
                actionView2.setAction(action, i);
                if (TextUtils.isEmpty(actionView2.getText())) {
                    Log.e(TAG, "Empty ActionView text: action=" + action);
                }
            } else {
                actionView2.setVisibility(View.INVISIBLE);
                actionView2.setAction(null, -1);
            }
        }
        updateVisibility();
        this.mParent.headerChanged();
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (this.mShowAllAppsLabel) {
            drawAllAppsHeader(canvas);
        }
    }

    public void setHidden(boolean z) {
        this.mHidden = z;
        updateVisibility();
    }

    public void setDisabled(boolean disabled) {
        this.mDisabled = disabled;
        updateVisibility();
    }

    public void setShowAllAppsLabel(boolean z, boolean force) {
        if (mShowAllAppsLabel != z || force) {
            mShowAllAppsLabel = z;
            setWillNotDraw(!mShowAllAppsLabel);
            int i = 0;
            if (mShowAllAppsLabel) {
                rebuildLabel();
            } else {
                mAllAppsLabelLayout = null;
            }
            int paddingTop = getPaddingTop();
            int paddingRight = getPaddingRight();
            if (mShowAllAppsLabel) {
                i = getAllAppsLayoutFullHeight();
            }
            setPadding(getPaddingLeft(), paddingTop, paddingRight, i);
        }
    }

    @Override
    public void setTypeface(@NotNull Typeface typeface) {
        mAllAppsLabelTypeface = typeface;
        if (mShowAllAppsLabel) {
            rebuildLabel();
            if (isAttachedToWindow()) {
                ((PredictionsFloatingHeader) getParent()).headerChanged();
                invalidate();
            }
        }
    }

    @SuppressLint("NewApi")
    private void rebuildLabel() {
        int normalColor = ContextCompat
                .getColor(getContext(), Themes.getAttrBoolean(getContext(), R.attr.isMainColorDark)
                        ? R.color.all_apps_label_text_dark : R.color.all_apps_label_text);
        int textAlpha = Color.alpha(normalColor);

        TextPaint textPaint = new TextPaint();
        textPaint.setAntiAlias(true);
        textPaint.setTypeface(mAllAppsLabelTypeface);
        textPaint.setColor(ColorUtils.setAlphaComponent(getAllAppsLabelColor(), textAlpha));
        textPaint.setTextSize((float) getResources().getDimensionPixelSize(R.dimen.all_apps_label_text_size));
        CharSequence text = getResources().getText(R.string.all_apps_label);
        mAllAppsLabelLayout = Builder.obtain(text, 0, text.length(), textPaint, Math.round(textPaint.measureText(text.toString()))).setAlignment(
                Alignment.ALIGN_CENTER).setMaxLines(1).setIncludePad(true).build();
    }

    public boolean shouldDraw() {
        return !this.mDisabled && getChildCount() > 0 && !this.mIsCollapsed;
    }

    private void updateVisibility() {
        setVisibility(!shouldDraw() ? View.GONE : mHidden ? View.INVISIBLE : View.VISIBLE);
    }

    public Action getAction(ItemInfo itemInfo) {
        for (int i = 0; i < getChildCount(); i++) {
            if (itemInfo == getChildAt(i).getTag()) {
                return ((ActionView) getChildAt(i)).getAction();
            }
        }
        return null;
    }

    private void drawAllAppsHeader(Canvas canvas) {
        PredictionRowView.drawAllAppsHeader(canvas, this, mAllAppsLabelLayout);
    }

    private int getAllAppsLayoutFullHeight() {
        return (this.mAllAppsLabelLayout.getHeight() + getResources().getDimensionPixelSize(R.dimen.all_apps_label_top_padding)) + getResources().getDimensionPixelSize(R.dimen.all_apps_label_bottom_padding);
    }

    public void setCollapsed(boolean z) {
        if (z != this.mIsCollapsed) {
            this.mIsCollapsed = z;
            updateVisibility();
        }
    }

    @Override
    public void onAllAppsLabelColorChanged() {
        setShowAllAppsLabel(mShowAllAppsLabel, true);
        invalidate();
    }
}
