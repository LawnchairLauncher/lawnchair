package com.android.launcher3.reflection;

import com.google.protobuf.nano.InvalidProtocolBufferNanoException;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.compat.UserManagerCompat;
import android.os.Message;
import com.google.protobuf.nano.MessageNano; //a
import com.android.launcher3.userevent.nano.LauncherLogProto;
import android.text.TextUtils;
import android.content.Intent;
import android.content.Context;
import android.os.HandlerThread;
import android.os.Handler;
import com.android.launcher3.reflectionevents.b;

public class l implements Handler.Callback, b
{
    private static final Object LOCK;
    private static l bf;
    private final Handler bb;
    private long bc;
    private j bd;
    private final HandlerThread be;
    private final Context mContext;

    static {
        LOCK = new Object();
    }

    private l(final Context mContext) {
        this.bd = null;
        this.mContext = mContext;
        (this.be = new HandlerThread("reflection-thread")).start();
        this.bb = new Handler(this.be.getLooper(), this);
    }

    public static l getInstance(final Context context) {
        synchronized (l.LOCK) {
            if (l.bf == null) {
                (l.bf = new l(context.getApplicationContext())).aI(true);
            }
            return l.bf;
        }
    }

    private static boolean isLauncherAppTarget(final Intent intent) {
        boolean b = true;
        if (intent != null && "android.intent.action.MAIN".equals(intent.getAction()) && intent.getComponent() != null && TextUtils.isEmpty((CharSequence)intent.getDataString())) {
            if (intent.getCategories() != null && intent.getCategories().size() == (b ? 1 : 0) && intent.hasCategory("android.intent.category.LAUNCHER")) {
                if (intent.getExtras() != null) {
                    b = (intent.getExtras().size() == (b ? 1 : 0) && intent.getExtras().containsKey("profile"));
                }
            }
            else {
                b = false;
            }
            return b;
        }
        return false;
    }

    public void aF(final long n) {
        final int n2 = 3;
        this.bb.removeMessages(n2);
        this.bb.sendEmptyMessageDelayed(n2, n);
    }

    public b aG() {
        return this;
    }

    public void aH(final LauncherLogProto.LauncherEvent launcherEvent, final Intent intent) {
        if (intent == null || !isLauncherAppTarget(intent)) {
            return;
        }
        intent.putExtra("intent_extra_launch_event", MessageNano.toByteArray(launcherEvent));
        Message.obtain(this.bb, 2, intent).sendToTarget();
    }

    public void aI(final boolean b) {
        final int n = 1;
        if (b) {
            this.bb.removeMessages(n);
            this.bb.sendEmptyMessage(0);
        }
        else {
            this.bb.removeMessages(0);
            this.bb.sendEmptyMessage(n);
        }
    }

    @Override
    public boolean handleMessage(final Message message) {
        final boolean b = true;
        Label_0254: {
            switch (message.what) {
                case 0: {
                    if (this.bd == null) {
                        this.bd = f.ah(this.mContext);
                        this.bc = UserManagerCompat.getInstance(this.mContext).getSerialNumberForUser(UserHandleCompat.myUserHandle());
                        this.bd.aD("GEL");
                    }
                    return b;
                }
                case 1: {
                    if (this.bd != null) {
                        this.bd.aA(b);
                        this.bd = null;
                    }
                    return b;
                }
                case 2: {
                    final Intent intent = (Intent)message.obj;
                    Object o = this.bd;
                    if (o != null) {
                        o = this.bd.aC(intent.getComponent(), intent.getLongExtra("profile", this.bc));
                        final String s = "intent_extra_launch_event";
                        final Intent intent2 = intent;
                        final byte[] byteArrayExtra = intent2.getByteArrayExtra(s);
                        try {
                            final LauncherLogProto.LauncherEvent from = LauncherLogProto.LauncherEvent.parseFrom(byteArrayExtra);
                            this.bd.aE("GEL", (String)o, "app_launch", from);
                            return b;
                        }
                        catch (InvalidProtocolBufferNanoException ex) {
                            final LauncherLogProto.LauncherEvent from = null;
                        }
                        break Label_0254;
                    }
                    return b;
                }
                case 3: {
                    if (this.bd != null) {
                        this.bd.aD("GEL");
                    }
                    return b;
                }
            }
        }
        return false;
    }
}
