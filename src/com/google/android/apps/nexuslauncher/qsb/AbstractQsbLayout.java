package com.google.android.apps.nexuslauncher.qsb;

import static java.lang.Math.round;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Process;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.launcher3.graphics.IconShape;
import ch.deletescape.lawnchair.globalsearch.SearchProvider;
import ch.deletescape.lawnchair.globalsearch.SearchProviderController;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.graphics.NinePatchDrawHelper;
import com.android.launcher3.graphics.ShadowGenerator.Builder;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.TransformingTouchDelegate;
import com.google.android.apps.nexuslauncher.NexusLauncherActivity;

public abstract class AbstractQsbLayout extends FrameLayout implements OnSharedPreferenceChangeListener,
        OnClickListener, OnLongClickListener, Insettable, SearchProviderController.OnProviderChangeListener {
    protected final static String GOOGLE_QSB = "com.google.android.googlequicksearchbox";
    private static final Rect CS = new Rect();
    protected final TextPaint CT;
    protected final Paint mMicStrokePaint;
    protected final Paint CV;
    protected final NinePatchDrawHelper mShadowHelper;
    protected final NinePatchDrawHelper mClearShadowHelper;
    protected final NexusLauncherActivity mActivity;
    protected final int CY;
    protected final int CZ;
    protected final int Da;
    protected Bitmap Db;
    protected int Dc;
    protected int Dd;
    public float micStrokeWidth;
    private ImageView mLogoIconView;
    protected ImageView mMicIconView;
    protected String Dg;
    protected boolean Dh;
    protected int Di;
    protected boolean mUseTwoBubbles;
    private final int Dk;
    private final int Dl;
    private final int Dm;
    private final TransformingTouchDelegate Dn;
    private final boolean Do;
    protected final boolean mIsRtl;
    protected Bitmap mShadowBitmap;
    protected Bitmap mClearBitmap;
    private boolean mShowAssistant;

    private float mRadius = -1.0f;

    public abstract void startSearch(String str, int i);

    protected abstract int aA(int i);

    public abstract void l(String str);

    public AbstractQsbLayout(Context context) {
        this(context, null);
    }

    public AbstractQsbLayout(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public AbstractQsbLayout(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        this.CT = new TextPaint();
        this.mMicStrokePaint = new Paint(1);
        this.CV = new Paint(1);
        this.mShadowHelper = new NinePatchDrawHelper();
        this.mClearShadowHelper = new NinePatchDrawHelper();
        this.mClearShadowHelper.paint.setXfermode(new PorterDuffXfermode(Mode.DST_OUT));
        this.Di = 0;
        this.mActivity = (NexusLauncherActivity) Launcher.getLauncher(context);
        this.Do = Themes.getAttrBoolean(this.mActivity, R.attr.isWorkspaceDarkText);
        setOnLongClickListener(this);
        this.Dk = getResources().getDimensionPixelSize(R.dimen.qsb_doodle_tap_target_logo_width);
        this.Da = getResources().getDimensionPixelSize(R.dimen.qsb_mic_width);
        this.CY = getResources().getDimensionPixelSize(R.dimen.qsb_text_spacing);
        this.CZ = getResources().getDimensionPixelSize(R.dimen.qsb_two_bubble_gap);
        this.CT.setTextSize((float) getResources().getDimensionPixelSize(R.dimen.qsb_hint_text_size));
        this.Dl = getResources().getDimensionPixelSize(R.dimen.qsb_shadow_margin);
        this.Dm = getResources().getDimensionPixelSize(R.dimen.qsb_max_hint_length);
        this.mIsRtl = Utilities.isRtl(getResources());
        this.Dn = new TransformingTouchDelegate(this);
        setTouchDelegate(this.Dn);
        this.CV.setColor(Color.WHITE);
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        dy().registerOnSharedPreferenceChangeListener(this);
        this.Dn.setDelegateView(this.mMicIconView);
        SearchProviderController.Companion.getInstance(getContext()).addOnProviderChangeListener(this);
    }

    public boolean onTouchEvent(MotionEvent motionEvent) {
        if (motionEvent.getActionMasked() == 0) {
            View findViewById = findViewById(R.id.g_icon);
            int i = 0;
            int i2 = 1;
            if (this.mIsRtl) {
                if (Float.compare(motionEvent.getX(), (float) (dI() ? getWidth() - this.Dk : findViewById.getLeft())) >= 0) {
                    i = 1;
                }
            } else {
                if (Float.compare(motionEvent.getX(), (float) (dI() ? this.Dk : findViewById.getRight())) <= 0) {
                    i = 1;
                }
            }
            if (i == 0) {
                i2 = 2;
            }
            this.Di = i2;
        }
        return super.onTouchEvent(motionEvent);
    }

    protected final SharedPreferences dy() {
        dz();
        SharedPreferences devicePrefs = Utilities.getPrefs(getContext());
        loadPreferences(devicePrefs);
        return devicePrefs;
    }

    protected final void dz() {
        mLogoIconView = findViewById(R.id.g_icon);
        mMicIconView = findViewById(R.id.mic_icon);
        mMicIconView.setOnClickListener(this);
        mLogoIconView.setOnClickListener(this);
    }

    protected void onLayout(boolean z, int i, int i2, int i3, int i4) {
        super.onLayout(z, i, i2, i3, i4);
        this.mMicIconView.getHitRect(CS);
        if (this.mIsRtl) {
            CS.left -= this.Dl;
        } else {
            CS.right += this.Dl;
        }
        Dn.setBounds(CS.left, CS.top, CS.right, CS.bottom);
    }

    protected void onDetachedFromWindow() {
        Utilities.getPrefs(getContext()).unregisterOnSharedPreferenceChangeListener(this);
        SearchProviderController.Companion.getInstance(getContext()).removeOnProviderChangeListener(this);
        super.onDetachedFromWindow();
    }

    public final void ay(int i) {
        if (this.Dc != i) {
            this.Dc = i;
            this.mShadowBitmap = null;
            invalidate();
        }
    }

    public final void az(int i) {
        Dd = i;
        if (Dd != Dc || Db != mShadowBitmap) {
            Db = null;
            invalidate();
        }
    }

    public final void h(float f) {
        this.micStrokeWidth = TypedValue.applyDimension(1, f, getResources().getDisplayMetrics());
        this.mMicStrokePaint.setStrokeWidth(this.micStrokeWidth);
        this.mMicStrokePaint.setStyle(Style.STROKE);
        this.mMicStrokePaint.setColor(0xFFBDC1C6);
    }

    public void setInsets(Rect rect) {
        requestLayout();
    }

    protected void onMeasure(int i, int i2) {
        DeviceProfile deviceProfile = this.mActivity.getDeviceProfile();
        int aA = aA(MeasureSpec.getSize(i));
        int i3 = aA / deviceProfile.inv.numHotseatIcons;
        int round = round(0.92f * ((float) deviceProfile.iconSizePx));
        setMeasuredDimension(((aA - (i3 - round)) + getPaddingLeft()) + getPaddingRight(), MeasureSpec.getSize(i2));
        for (aA = getChildCount() - 1; aA >= 0; aA--) {
            View childAt = getChildAt(aA);
            measureChildWithMargins(childAt, i, 0, i2, 0);
            if (childAt.getMeasuredWidth() <= round) {
                LayoutParams layoutParams = (LayoutParams) childAt.getLayoutParams();
                int measuredWidth = (round - childAt.getMeasuredWidth()) / 2;
                layoutParams.rightMargin = measuredWidth;
                layoutParams.leftMargin = measuredWidth;
            }
        }
    }

    protected final Bitmap dA() {
        dB();
        return this.mShadowBitmap;
    }

    final void dB() {
        if (mShadowBitmap == null) {
            mShadowBitmap = aB(this.Dc, true);
            mClearBitmap = null;
            if (Color.alpha(Dc) != 255) {
                mClearBitmap = aB(0xFF000000, false);
            }
        }
    }

    protected void clearMainPillBg(Canvas canvas) {

    }

    protected void clearPillBg(Canvas canvas, int left, int top, int right) {

    }

    public void draw(Canvas canvas) {
        int i;
        dB();
        clearMainPillBg(canvas);
        a(this.mShadowBitmap, canvas);
        if (this.mUseTwoBubbles) {
            int paddingLeft;
            int paddingLeft2;
            if (Db == null) {
                Bitmap bitmap;
                if (Dc == Dd) {
                    i = 1;
                } else {
                    i = 0;
                }
                if (i != 0) {
                    bitmap = mShadowBitmap;
                } else {
                    bitmap = aB(Dd, true);
                }
                Db = bitmap;
            }
            Bitmap bitmap2 = Db;
            i = a(bitmap2);
            int paddingTop = getPaddingTop() - ((bitmap2.getHeight() - getHeightWithoutPadding()) / 2);
            if (mIsRtl) {
                paddingLeft = getPaddingLeft() - i;
                paddingLeft2 = getPaddingLeft() + i;
                i = dG();
            } else {
                paddingLeft = ((getWidth() - getPaddingRight()) - dG()) - i;
                paddingLeft2 = getWidth() - getPaddingRight();
            }
            clearPillBg(canvas, paddingLeft, paddingTop, paddingLeft2 + i);
            mShadowHelper.draw(bitmap2, canvas, (float) paddingLeft, (float) paddingTop, (float) (paddingLeft2 + i));
        }
        if (micStrokeWidth > 0.0f && mMicIconView.getVisibility() == View.VISIBLE) {
            float i2;
            i = mIsRtl ? getPaddingLeft() : (getWidth() - getPaddingRight()) - dG();
            int paddingTop2 = getPaddingTop();
            int paddingLeft3 = mIsRtl ? getPaddingLeft() + dG() : getWidth() - getPaddingRight();
            int paddingBottom = LauncherAppState.getInstance(getContext()).getInvariantDeviceProfile().iconBitmapSize - getPaddingBottom();
            float f = ((float) (paddingBottom - paddingTop2)) * 0.5f;
            float i3 = micStrokeWidth / 2.0f;
            if (mUseTwoBubbles) {
                i2 = i3;
            } else {
                i2 = i3;
                canvas.drawRoundRect(i + i3, paddingTop2 + i3, paddingLeft3 - i3, (paddingBottom - i3) + 1, f, f, CV);
            }
            canvas.drawRoundRect(i + i2, paddingTop2 + i2, paddingLeft3 - i2, (paddingBottom - i2) + 1, f, f, mMicStrokePaint);
        }
        super.draw(canvas);
    }

    protected final void a(Bitmap bitmap, Canvas canvas) {
        drawPill(mShadowHelper, bitmap, canvas);
    }

    protected final void drawPill(NinePatchDrawHelper helper, Bitmap bitmap, Canvas canvas) {
        int a = a(bitmap);
        int left = getPaddingLeft() - a;
        int top = getPaddingTop() - ((bitmap.getHeight() - getHeightWithoutPadding()) / 2);
        int right = (getWidth() - getPaddingRight()) + a;
        if (mIsRtl) {
            left += dF();
        } else {
            right -= dF();
        }
        helper.draw(bitmap, canvas, (float) left, (float) top, (float) right);
    }

    private Bitmap aB(int i, boolean withShadow) {
        float f = (float) LauncherAppState.getInstance(getContext()).getInvariantDeviceProfile().iconBitmapSize;
        return c(0.010416667f * f, f * 0.020833334f, i, withShadow);
    }

    protected final Bitmap c(float f, float f2, int i, boolean withShadow) {
        int dC = getHeightWithoutPadding();
        int i2 = dC + 20;
        Builder builder = new Builder(i);
        builder.shadowBlur = f;
        builder.keyShadowDistance = f2;
        if (Do && this instanceof HotseatQsbWidget) {
            builder.ambientShadowAlpha *= 2;
        }
        if (!withShadow) {
            builder.ambientShadowAlpha = 0;
        }
        builder.keyShadowAlpha = builder.ambientShadowAlpha;
        Bitmap pill;
        if (mRadius < 0) {
            TypedValue edgeRadius = IconShape.getShape().getAttrValue(R.attr.qsbEdgeRadius);
            if (edgeRadius != null) {
                pill = builder.createPill(i2, dC,
                        edgeRadius.getDimension(getResources().getDisplayMetrics()));
            } else {
                pill = builder.createPill(i2, dC);
            }
        } else {
            pill = builder.createPill(i2, dC, mRadius);
        }
        if (Utilities.ATLEAST_OREO) {
            return pill.copy(Config.HARDWARE, false);
        }
        return pill;
    }

    protected final int a(Bitmap bitmap) {
        return (bitmap.getWidth() - (getHeightWithoutPadding() + 20)) / 2;
    }

    protected final int getHeightWithoutPadding() {
        return (getHeight() - getPaddingTop()) - getPaddingBottom();
    }

    protected final int dD() {
        return this.mUseTwoBubbles ? this.Da : this.Da + this.CY;
    }

    protected final void setHintText(String str, TextView textView) {
        String str2;
        if (TextUtils.isEmpty(str) || !dE()) {
            str2 = str;
        } else {
            str2 = TextUtils.ellipsize(str, this.CT, (float) this.Dm, TruncateAt.END).toString();
        }
        this.Dg = str2;
        textView.setText(this.Dg);
        int i = 17;
        if (dE()) {
            i = 8388629;
            if (this.mIsRtl) {
                textView.setPadding(dD(), 0, 0, 0);
            } else {
                textView.setPadding(0, 0, dD(), 0);
            }
        }
        textView.setGravity(i);
        ((LayoutParams) textView.getLayoutParams()).gravity = i;
        textView.setContentDescription(str);
    }

    protected final boolean dE() {
        if (!this.Dh) {
            if (!this.mUseTwoBubbles) {
                return false;
            }
        }
        return true;
    }

    protected final int dF() {
        return this.mUseTwoBubbles ? dG() + this.CZ : 0;
    }

    protected final int dG() {
        if (!this.mUseTwoBubbles || TextUtils.isEmpty(this.Dg)) {
            return this.Da;
        }
        return (Math.round(this.CT.measureText(this.Dg)) + this.CY) + this.Da;
    }

    protected final void dH() {
        int dF;
        int i;
        int i2;
        int dG;
        InsetDrawable insetDrawable = (InsetDrawable) createRipple().mutate();
        RippleDrawable rippleDrawable = (RippleDrawable) insetDrawable.getDrawable();
        if (this.mIsRtl) {
            dF = dF();
        } else {
            dF = 0;
        }
        if (this.mIsRtl) {
            i = 0;
        } else {
            i = dF();
        }
        rippleDrawable.setLayerInset(0, dF, 0, i, 0);
        setBackground(insetDrawable);
        RippleDrawable rippleDrawable2 = (RippleDrawable) rippleDrawable.getConstantState().newDrawable().mutate();
        rippleDrawable2.setLayerInset(0, 0, this.Dl, 0, this.Dl);
        this.mMicIconView.setBackground(rippleDrawable2);
        this.mMicIconView.getLayoutParams().width = dG();
        if (this.mIsRtl) {
            i2 = 0;
        } else {
            i2 = dG() - this.Da;
        }
        if (this.mIsRtl) {
            dG = dG() - this.Da;
        } else {
            dG = 0;
        }
        this.mMicIconView.setPadding(i2, 0, dG, 0);
        this.mMicIconView.requestLayout();
    }

    private InsetDrawable createRipple() {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(getCornerRadius());
        shape.setColor(ContextCompat.getColor(getContext(), android.R.color.white));

        ColorStateList rippleColor = ContextCompat.getColorStateList(getContext(), R.color.focused_background);
        RippleDrawable ripple = new RippleDrawable(rippleColor, null, shape);
        return new InsetDrawable(ripple, getResources().getDimensionPixelSize(R.dimen.qsb_shadow_margin));
    }

    protected float getCornerRadius() {
        return getCornerRadius(getContext(),
                Utilities.pxFromDp(100, getResources().getDisplayMetrics()));
    }

    public static float getCornerRadius(Context context, float defaultRadius) {
        float radius = round(Utilities.getLawnchairPrefs(context).getSearchBarRadius());
        if (radius > 0f) {
            return radius;
        }
        TypedValue edgeRadius = IconShape.getShape().getAttrValue(R.attr.qsbEdgeRadius);
        if (edgeRadius != null) {
            return edgeRadius.getDimension(context.getResources().getDisplayMetrics());
        } else {
            return defaultRadius;
        }
    }

    public boolean dI() {
        return false;
    }

    public void onClick(View view) {
        SearchProviderController controller = SearchProviderController.Companion
                .getInstance(mActivity);
        SearchProvider provider = controller.getSearchProvider();
        if (view == mMicIconView) {
            if (controller.isGoogle()) {
                fallbackSearch(mShowAssistant ? Intent.ACTION_VOICE_COMMAND : "android.intent.action.VOICE_ASSIST");
            } else if(mShowAssistant && provider.getSupportsAssistant()) {
                provider.startAssistant(intent -> {
                    getContext().startActivity(intent);
                    return null;
                });
            } else if(provider.getSupportsVoiceSearch()) {
                provider.startVoiceSearch(intent -> {
                    getContext().startActivity(intent);
                    return null;
                });
            }
        } else if (view == mLogoIconView) {
            if (provider.getSupportsFeed() && logoCanOpenFeed()) {
                provider.startFeed(intent -> {
                    getContext().startActivity(intent);
                    return null;
                });
            } else {
                startSearch("", Di);
            }
        }
    }

    protected boolean logoCanOpenFeed() {
        return true;
    }

    protected final void k(String str) {
        try {
            getContext().startActivity(new Intent(str).addFlags(268468224).setPackage("com.google.android.googlequicksearchbox"));
        } catch (ActivityNotFoundException e) {
            LauncherAppsCompat.getInstance(getContext()).showAppDetailsForProfile(new ComponentName("com.google.android.googlequicksearchbox", ".SearchActivity"), Process.myUserHandle());
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case "opa_enabled":
            case "opa_assistant":
            case "pref_bubbleSearchStyle":
                loadPreferences(sharedPreferences);
        }
        if (key.equals("pref_searchbarRadius")) {
            mShadowBitmap = null;
            loadPreferences(sharedPreferences);
        }
    }

    @Override
    public void onSearchProviderChanged() {
        loadPreferences(Utilities.getPrefs(getContext()));
    }

    protected void loadPreferences(SharedPreferences sharedPreferences) {
        post(() -> {
            mShowAssistant = sharedPreferences.getBoolean("opa_assistant", true);
            mLogoIconView.setImageDrawable(getIcon());
            mMicIconView.setVisibility(sharedPreferences.getBoolean("opa_enabled", true) ? View.VISIBLE : View.GONE);
            mMicIconView.setImageDrawable(getMicIcon());
            mUseTwoBubbles = useTwoBubbles();
            mRadius = Utilities.getLawnchairPrefs(getContext()).getSearchBarRadius();
            invalidate();
        });
    }

    protected Drawable getIcon() {
        return getIcon(true);
    }

    protected Drawable getIcon(boolean colored) {
        SearchProvider provider = SearchProviderController.Companion.getInstance(getContext()).getSearchProvider();
        return provider.getIcon(colored);
    }

    protected Drawable getMicIcon() {
        return getMicIcon(true);
    }

    protected Drawable getMicIcon(boolean colored){
        SearchProvider provider = SearchProviderController.Companion.getInstance(getContext()).getSearchProvider();
        if (mShowAssistant && provider.getSupportsAssistant()){
            return provider.getAssistantIcon(colored);
        } else if (provider.getSupportsVoiceSearch()) {
            return provider.getVoiceIcon(colored);
        } else {
            mMicIconView.setVisibility(GONE);
            return new ColorDrawable(Color.TRANSPARENT);
        }
    }

    public boolean onLongClick(View view) {
        if (view != this) {
            return false;
        }
        return dK();
    }

    protected boolean dK() {
        String clipboardText = getClipboardText();
        Intent settingsBroadcast = createSettingsBroadcast();
        Intent settingsIntent = createSettingsIntent();
        if (settingsIntent == null && settingsBroadcast == null && clipboardText == null) {
            return false;
        }
        if (Utilities.ATLEAST_MARSHMALLOW) {
            startActionMode(new QsbActionMode(this, clipboardText, settingsBroadcast, settingsIntent), 1);
        }
        return true;
    }

    @Nullable
    protected String getClipboardText() {
        ClipboardManager clipboardManager = ContextCompat
                .getSystemService(getContext(), ClipboardManager.class);
        ClipData primaryClip = clipboardManager.getPrimaryClip();
        if (primaryClip != null) {
            for (int i = 0; i < primaryClip.getItemCount(); i++) {
                CharSequence text = primaryClip.getItemAt(i).coerceToText(getContext());
                if (!TextUtils.isEmpty(text)) {
                    return text.toString();
                }
            }
        }
        return null;
    }

    protected Intent createSettingsBroadcast() {
        return null;
    }

    protected Intent createSettingsIntent() {
        return null;
    }

    public int dL() {
        return 0;
    }

    protected void fallbackSearch(String action) {
        try {
            getContext().startActivity(new Intent(action)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .setPackage(GOOGLE_QSB));
        } catch (ActivityNotFoundException e) {
            noGoogleAppSearch();
        }
    }

    protected void noGoogleAppSearch() {
    }

    public boolean useTwoBubbles() {
        return mMicIconView.getVisibility() == View.VISIBLE && Utilities
                .getLawnchairPrefs(mActivity).getDualBubbleSearch();
    }
}
