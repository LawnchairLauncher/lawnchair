package com.android.launcher3.reflection.a2;

import android.content.Intent;
import java.util.List;
import com.android.launcher3.reflection.common.nano.b;
import java.util.ArrayList;
import java.util.Calendar;
import android.media.AudioManager;
import android.os.Handler;
import android.content.IntentFilter;
import android.content.Context;
import com.android.launcher3.reflection.common.nano.a;
import com.android.launcher3.reflection.k;

import android.content.BroadcastReceiver;

public class e extends BroadcastReceiver implements k
{
    private boolean L;
    private long M;
    private final a N;
    private boolean O;
    private long P;
    private final Context mContext;

    public e(final a n, final Context mContext) {
        final long n2 = 0L;
        this.P = n2;
        this.M = n2;
        this.mContext = mContext;
        this.N = n;
        final IntentFilter intentFilter = new IntentFilter("android.intent.action.HEADSET_PLUG");
        intentFilter.addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED");
        mContext.registerReceiver((BroadcastReceiver)this, intentFilter, (String)null, new Handler());
        final AudioManager audioManager = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
        this.O = audioManager.isWiredHeadsetOn();
        this.L = (audioManager.isBluetoothA2dpOn() || audioManager.isBluetoothScoOn());
    }

    public void F() {
        this.mContext.unregisterReceiver((BroadcastReceiver)this);
    }

    protected void G(final boolean l) {
        this.L = l;
        this.M = Calendar.getInstance().getTimeInMillis();
        this.H();
    }

    public void H() {
        final long n = 0L;
        final ArrayList<b> list = new ArrayList<b>(4);
        if (this.P > n) {
            final b b = new b();
            b.LL = "headset";
            b.LM = this.P;
            String lk;
            if (this.O) {
                lk = "headset_wired_in";
            }
            else {
                lk = "headset_wired_out";
            }
            b.LK = lk;
            list.add(b);
        }
        if (this.M > n) {
            final b b2 = new b();
            b2.LL = "headset";
            b2.LM = this.M;
            String lk2;
            if (this.L) {
                lk2 = "headset_bluetooth_in";
            }
            else {
                lk2 = "headset_bluetooth_out";
            }
            b2.LK = lk2;
            list.add(b2);
        }
        com.android.launcher3.reflection.common.a.Sz(this.N, "headset", list);
    }

    protected void I(final boolean o) {
        this.O = o;
        this.P = Calendar.getInstance().getTimeInMillis();
        this.H();
    }

    public void onReceive(final Context context, final Intent intent) {
        final boolean b = true;
        final int n = -1;
        if (!intent.getAction().equals("android.intent.action.HEADSET_PLUG") || this.isInitialStickyBroadcast()) {
            if (intent.getAction().equals("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED") && !this.isInitialStickyBroadcast()) {
                switch (intent.getIntExtra("android.bluetooth.profile.extra.STATE", n)) {
                    case 0: {
                        this.G(false);
                        break;
                    }
                    case 2: {
                        this.G(b);
                        break;
                    }
                }
            }
        }
        else {
            switch (intent.getIntExtra("state", n)) {
                case 0: {
                    this.I(false);
                    break;
                }
                case 1: {
                    this.I(b);
                    break;
                }
            }
        }
    }
}