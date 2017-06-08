package ch.deletescape.lawnchair.pixelify;

import android.animation.ObjectAnimator;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;

import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.compat.LauncherAppsCompat;
import ch.deletescape.lawnchair.compat.UserManagerCompat;
import ch.deletescape.lawnchair.util.PackageManagerHelper;

public abstract class C0276c extends FrameLayout implements OnClickListener, OnSharedPreferenceChangeListener {
    private static String bF = "com.google.android.googlequicksearchbox.TEXT_ASSIST";
    private static String bG = "android.intent.action.VOICE_ASSIST";
    protected View bB;
    protected final Launcher bC;
    protected boolean bD;
    protected QsbConnector bE;
    private float bH;
    private ObjectAnimator bI;
    private final BroadcastReceiver bJ = new C0286k(this);
    private boolean bK;
    private int bL = 0;
    private boolean bM;

    protected abstract int aK(boolean z);

    public C0276c(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        this.bC = Launcher.getLauncher(context);
    }

    public void bj(SharedPreferences sharedPreferences) {
        boolean z;
        if (sharedPreferences.getBoolean("opa_enabled", true) || UserManagerCompat.getInstance(getContext()).isDemoUser()) {
            z = false;
        } else {
            z = true;
        }
        this.bD = z;
        int aK = aK(this.bD);
        if (aK != this.bL) {
            this.bL = aK;
            if (this.bB != null) {
                removeView(this.bB);
            }
            this.bB = LayoutInflater.from(getContext()).inflate(this.bL, this, false);
            this.bH = (float) getResources().getDimensionPixelSize(R.dimen.qsb_button_elevation);
            addView(this.bB);
            this.bI = ObjectAnimator.ofFloat(this.bB, "elevation", new float[]{0.0f, this.bH});
            this.bI.setInterpolator(new AccelerateDecelerateInterpolator());
            if (this.bK) {
                bk();
            }
            this.bB.setOnClickListener(this);
            this.bB.setAccessibilityDelegate(new C0281f());
            if (this.bD) {
                this.bB.findViewById(R.id.mic_icon).setOnClickListener(this);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        SharedPreferences sharedPreferences = getContext().getSharedPreferences("com.android.launcher3.device.prefs", 0);
        bj(sharedPreferences);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        sharedPreferences = Utilities.getPrefs(getContext());
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        bp(sharedPreferences);
        getContext().registerReceiver(this.bJ, C0330a.ca("android.intent.action.PACKAGE_CHANGED"));
        bo();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getContext().getSharedPreferences("com.android.launcher3.device.prefs", 0).unregisterOnSharedPreferenceChangeListener(this);
        Utilities.getPrefs(getContext()).unregisterOnSharedPreferenceChangeListener(this);
        getContext().unregisterReceiver(this.bJ);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
        if ("opa_enabled".equals(str)) {
            bj(sharedPreferences);
            bo();
        } else if ("pref_enable_minus_one".equals(str)) {
            bp(sharedPreferences);
            bo();
        }
    }

    protected boolean aM(SharedPreferences sharedPreferences) {
        return sharedPreferences.getBoolean("pref_enable_minus_one", true);
    }

    private void bp(SharedPreferences sharedPreferences) {
        boolean aM = aM(sharedPreferences);
        boolean z;
        if (this.bE != null) {
            z = true;
        } else {
            z = false;
        }
        if (aM && !z) {
            this.bE = (QsbConnector) this.bC.getLayoutInflater().inflate(R.layout.qsb_connector, this, false);
            addView(this.bE, 0);
        } else if (!aM && z) {
            removeView(this.bE);
            this.bE = null;
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.mic_icon) {
            br(bG);
        } else {
            getContext().sendOrderedBroadcast(bm("com.google.nexuslauncher.FAST_TEXT_SEARCH"), null, new C0287l(this), null, 0, null, null);
        }
    }

    private Intent bm(String str) {
        int[] iArr = new int[2];
        this.bB.getLocationOnScreen(iArr);
        Rect rect = new Rect(iArr[0], iArr[1], iArr[0] + this.bB.getWidth(), iArr[1] + this.bB.getHeight());
        Intent intent = new Intent(str);
        aL(rect, intent);
        intent.setSourceBounds(rect);
        View findViewById = findViewById(R.id.mic_icon);
        if (findViewById != null) {
            intent.putExtra("source_mic_offset", bn(findViewById, rect));
        }
        return intent.putExtra("source_round_left", true).putExtra("source_round_right", true).putExtra("source_logo_offset", bn(findViewById(R.id.g_icon), rect)).setPackage("com.google.android.googlequicksearchbox").addFlags(1342177280);
    }

    private Point bn(View view, Rect rect) {
        int[] iArr = new int[2];
        view.getLocationOnScreen(iArr);
        Point point = new Point();
        point.x = (iArr[0] - rect.left) + (view.getWidth() / 2);
        point.y = (iArr[1] - rect.top) + (view.getHeight() / 2);
        return point;
    }

    protected void aL(Rect rect, Intent intent) {
    }

    private void bq() {
        if (hasWindowFocus()) {
            this.bM = true;
        } else {
            bk();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean z) {
        super.onWindowFocusChanged(z);
        if (!z && this.bM) {
            bk();
        } else if (z) {
            bl(true);
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int i) {
        super.onWindowVisibilityChanged(i);
        bl(false);
    }

    private void bk() {
        this.bM = false;
        this.bK = true;
        if (this.bB != null) {
            this.bB.setAlpha(0.0f);
            if (this.bI != null && this.bI.isRunning()) {
                this.bI.end();
            }
        }
        if (this.bE != null) {
            this.bE.setAlpha(0.0f);
        }
    }

    private void bl(boolean z) {
        this.bM = false;
        if (this.bK) {
            this.bK = false;
            if (this.bB != null) {
                this.bB.setAlpha(1.0f);
                if (this.bI != null) {
                    this.bI.start();
                    if (!z) {
                        this.bI.end();
                    }
                }
            }
            if (this.bE != null) {
                this.bE.setAlpha(1.0f);
                this.bE.bc(z);
            }
        }
    }

    private void br(String str) {
        try {
            getContext().startActivity(new Intent(str).addFlags(268468224).setPackage("com.google.android.googlequicksearchbox"));
        } catch (ActivityNotFoundException e) {
            LauncherAppsCompat.getInstance(getContext()).showAppDetailsForProfile(new ComponentName("com.google.android.googlequicksearchbox", ".SearchActivity"), Utilities.myUserHandle());
        }
    }

    private void bo() {
        boolean isAppEnabled = PackageManagerHelper.isAppEnabled(getContext().getPackageManager(), "com.google.android.googlequicksearchbox", 0);
        if (this.bB != null) {
            int i;
            View view = this.bB;
            if (isAppEnabled) {
                i = View.VISIBLE;
            } else {
                i = View.GONE;
            }
            view.setVisibility(i);
        }
        if (this.bE != null) {
            int i = View.VISIBLE;
            QsbConnector qsbConnector = this.bE;
            if (!isAppEnabled) {
                i = View.GONE;
            }
            qsbConnector.setVisibility(i);
        }
    }

    final class C0287l extends BroadcastReceiver {
        final /* synthetic */ C0276c cq;

        C0287l(C0276c c0276c) {
            this.cq = c0276c;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (getResultCode() == 0) {
                this.cq.br(C0276c.bF);
            } else {
                this.cq.bq();
            }
        }
    }

    final class C0286k extends BroadcastReceiver {
        final /* synthetic */ C0276c cp;

        C0286k(C0276c c0276c) {
            this.cp = c0276c;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            this.cp.bo();
        }
    }
}