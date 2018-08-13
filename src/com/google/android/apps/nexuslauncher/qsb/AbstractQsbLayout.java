package com.google.android.apps.nexuslauncher.qsb;

import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Process;
import android.provider.Settings.Secure;
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
import android.widget.TextView;
import com.android.launcher3.*;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.graphics.NinePatchDrawHelper;
import com.android.launcher3.graphics.ShadowGenerator.Builder;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.TransformingTouchDelegate;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.google.android.apps.nexuslauncher.NexusLauncherActivity;

@TargetApi(26)
public abstract class AbstractQsbLayout extends FrameLayout implements OnSharedPreferenceChangeListener, OnClickListener, OnLongClickListener, Insettable {
    protected final static String GOOGLE_QSB = "com.google.android.googlequicksearchbox";
    private static final Rect CS = new Rect();
    protected final TextPaint CT;
    protected final Paint CU;
    protected final Paint CV;
    protected final NinePatchDrawHelper CW;
    protected final NexusLauncherActivity mActivity;
    protected final int CY;
    protected final int CZ;
    protected final int Da;
    protected Bitmap Db;
    protected int Dc;
    protected int Dd;
    protected float De;
    protected View mMicIconView;
    protected String Dg;
    protected boolean Dh;
    protected int Di;
    protected boolean Dj;
    private final int Dk;
    private final int Dl;
    private final int Dm;
    private final TransformingTouchDelegate Dn;
    private final boolean Do;
    protected final boolean mIsRtl;
    protected Bitmap mShadowBitmap;

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
        this.CU = new Paint(1);
        this.CV = new Paint(1);
        this.CW = new NinePatchDrawHelper();
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
        this.CV.setColor(-1);
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        dy().registerOnSharedPreferenceChangeListener(this);
        this.Dn.setDelegateView(this.mMicIconView);
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
        SharedPreferences devicePrefs = Utilities.getDevicePrefs(getContext());
        hideMicIcon();
        c(devicePrefs);
        return devicePrefs;
    }

    protected final void dz() {
        this.mMicIconView = findViewById(R.id.mic_icon);
        this.mMicIconView.setOnClickListener(this);
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
        Utilities.getDevicePrefs(getContext()).unregisterOnSharedPreferenceChangeListener(this);
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
        this.De = TypedValue.applyDimension(1, f, getResources().getDisplayMetrics());
        this.CU.setStrokeWidth(this.De);
        this.CU.setStyle(Style.STROKE);
        this.CU.setColor(-4341306);
    }

    public void setInsets(Rect rect) {
        requestLayout();
    }

    protected void onMeasure(int i, int i2) {
        DeviceProfile deviceProfile = this.mActivity.getDeviceProfile();
        int aA = aA(MeasureSpec.getSize(i));
        int i3 = aA / deviceProfile.inv.numHotseatIcons;
        int round = Math.round(0.92f * ((float) deviceProfile.iconSizePx));
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
        if (this.mShadowBitmap == null) {
            this.mShadowBitmap = aB(this.Dc);
        }
    }

    public void draw(Canvas canvas) {
        int i;
        dB();
        Canvas canvas2 = canvas;
        a(this.mShadowBitmap, canvas2);
        if (this.Dj) {
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
                    bitmap = aB(Dd);
                }
                Db = bitmap;
            }
            Bitmap bitmap2 = Db;
            i = a(bitmap2);
            int paddingTop = getPaddingTop() - ((bitmap2.getHeight() - dC()) / 2);
            if (mIsRtl) {
                paddingLeft = getPaddingLeft() - i;
                paddingLeft2 = getPaddingLeft() + i;
                i = dG();
            } else {
                paddingLeft = ((getWidth() - getPaddingRight()) - dG()) - i;
                paddingLeft2 = getWidth() - getPaddingRight();
            }
            CW.draw(bitmap2, canvas2, (float) paddingLeft, (float) paddingTop, (float) (paddingLeft2 + i));
        }
        if (De > 0.0f && mMicIconView.getVisibility() == View.VISIBLE) {
            int i2;
            i = mIsRtl ? getPaddingLeft() : (getWidth() - getPaddingRight()) - dG();
            int paddingTop2 = getPaddingTop();
            int paddingLeft3 = mIsRtl ? getPaddingLeft() + dG() : getWidth() - getPaddingRight();
            int paddingBottom = LauncherAppState.getInstance(getContext()).getInvariantDeviceProfile().iconBitmapSize - getPaddingBottom();
            float f = ((float) (paddingBottom - paddingTop2)) * 0.5f;
            int i3 = (int) (De / 2.0f);
            if (Dj) {
                i2 = i3;
            } else {
                i2 = i3;
                canvas2.drawRoundRect((float) (i + i3), (float) (paddingTop2 + i3), (float) (paddingLeft3 - i3), (float) ((paddingBottom - i3) + 1), f, f, CV);
            }
            canvas2.drawRoundRect((float) (i + i2), (float) (paddingTop2 + i2), (float) (paddingLeft3 - i2), (float) ((paddingBottom - i2) + 1), f, f, CU);
        }
        super.draw(canvas);
    }

    protected final void a(Bitmap bitmap, Canvas canvas) {
        int a = a(bitmap);
        int paddingTop = getPaddingTop() - ((bitmap.getHeight() - dC()) / 2);
        int paddingLeft = getPaddingLeft() - a;
        int width = (getWidth() - getPaddingRight()) + a;
        if (this.mIsRtl) {
            paddingLeft += dF();
        } else {
            width -= dF();
        }
        this.CW.draw(bitmap, canvas, (float) paddingLeft, (float) paddingTop, (float) width);
    }

    private Bitmap aB(int i) {
        float f = (float) LauncherAppState.getInstance(getContext()).getInvariantDeviceProfile().iconBitmapSize;
        return c(0.010416667f * f, f * 0.020833334f, i);
    }

    protected final Bitmap c(float f, float f2, int i) {
        int dC = dC();
        int i2 = dC + 20;
        Builder builder = new Builder(i);
        builder.shadowBlur = f;
        builder.keyShadowDistance = f2;
        if (Do) {
            builder.ambientShadowAlpha = (int) (2.8E-45f * builder.ambientShadowAlpha);
        }
        builder.keyShadowAlpha = builder.ambientShadowAlpha;
        Bitmap pill = builder.createPill(i2, dC);
        if (Utilities.ATLEAST_OREO) {
            return pill.copy(Config.HARDWARE, false);
        }
        return pill;
    }

    protected final int a(Bitmap bitmap) {
        return (bitmap.getWidth() - (dC() + 20)) / 2;
    }

    protected final int dC() {
        return (getHeight() - getPaddingTop()) - getPaddingBottom();
    }

    protected final int dD() {
        return this.Dj ? this.Da : this.Da + this.CY;
    }

    protected final void a(String str, TextView textView) {
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
            if (!this.Dj) {
                return false;
            }
        }
        return true;
    }

    protected final int dF() {
        return this.Dj ? dG() + this.CZ : 0;
    }

    protected final int dG() {
        if (!this.Dj || TextUtils.isEmpty(this.Dg)) {
            return this.Da;
        }
        return (((int) this.CT.measureText(this.Dg)) + this.CY) + this.Da;
    }

    protected final void dH() {
        int dF;
        int i;
        int i2;
        int dG;
        InsetDrawable insetDrawable = (InsetDrawable) getResources().getDrawable(R.drawable.bg_qsb_click_feedback).mutate();
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

    public boolean dI() {
        return false;
    }

    public void onClick(View view) {
        if (view == this.mMicIconView) {
            ComponentName unflattenFromString;
            ContentResolver contentResolver = view.getContext().getContentResolver();
            String string = Secure.getString(contentResolver, "assistant");
            String pkg;
            boolean z = false;
            if (TextUtils.isEmpty(string)) {
                String string2 = Secure.getString(contentResolver, "voice_interaction_service");
                if (TextUtils.isEmpty(string2)) {
                    ResolveInfo info = view.getContext().getPackageManager()
                            .resolveActivity(new Intent("android.intent.action.ASSIST"), PackageManager.MATCH_DEFAULT_ONLY);
                    if (info == null || !"com.google.android.googlequicksearchbox".equals(info.resolvePackageName)) {
                        z = ActivityManagerWrapper.getInstance().showVoiceSession(null, null, 5);
                    }
                    if (!z) {
                        k("android.intent.action.VOICE_ASSIST");
                    }
                }
                pkg = "com.google.android.googlequicksearchbox";
                unflattenFromString = ComponentName.unflattenFromString(string2);
            } else {
                pkg = "com.google.android.googlequicksearchbox";
                unflattenFromString = ComponentName.unflattenFromString(string);
            }
            if (!pkg.equals(unflattenFromString.getPackageName())) {
                z = ActivityManagerWrapper.getInstance().showVoiceSession(null, null, 5);
            }
            if (!z) {
                k("android.intent.action.VOICE_ASSIST");
            }
        }
    }

    protected final void k(String str) {
        try {
            getContext().startActivity(new Intent(str).addFlags(268468224).setPackage("com.google.android.googlequicksearchbox"));
        } catch (ActivityNotFoundException e) {
            LauncherAppsCompat.getInstance(getContext()).showAppDetailsForProfile(new ComponentName("com.google.android.googlequicksearchbox", ".SearchActivity"), Process.myUserHandle());
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
        if ("opa_enabled".equals(str)) {
            hideMicIcon();
            return;
        }
        if ("pref_persistent_flags".equals(str)) {
            c(sharedPreferences);
        }
    }

    private void hideMicIcon() {
//        this.mMicIconView.setVisibility(View.GONE);
//        setTouchDelegate(null);
//        requestLayout();
    }

    protected void c(SharedPreferences sharedPreferences) {
    }

    public boolean onLongClick(View view) {
        if (view != this) {
            return false;
        }
        return dK();
    }

    protected boolean dK() {
        Intent createSettingsIntent = createSettingsIntent();
        if (createSettingsIntent == null) {
            return false;
        }
        startActionMode(new b(this, null, createSettingsIntent), 1);
        return true;
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
//            noGoogleAppSearch();
        }
    }
}