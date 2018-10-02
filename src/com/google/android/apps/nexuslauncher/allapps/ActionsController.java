package com.google.android.apps.nexuslauncher.allapps;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ApplicationInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;
import android.os.UserHandle;
import android.util.Log;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.LauncherAppsCompat.OnAppsChangedCallbackCompat;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.Preconditions;
import com.google.android.apps.nexuslauncher.allapps.ActionsController.Logger.Data;
import com.google.android.apps.nexuslauncher.allapps.ActionsController.PackageChangeCallback.PackageInfo;
import com.google.android.apps.nexuslauncher.utils.ActionIntentFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ActionsController implements OnSharedPreferenceChangeListener, Callback {

    public static final String AIAI_PACKAGE = "com.google.android.as";
    public static final int MAX_ITEMS = 2;
    private static final Uri i = new Builder().scheme("content")
            .authority("com.google.android.as.allapps.actionsuggestprovider").build();
    private static final Uri j = new Builder().scheme("content")
            .authority("com.google.android.as.allapps.actionloggingprovider").build();
    private static final Uri k = new Builder().scheme("content")
            .authority("com.google.android.as.allapps.actionsettingprovider").build();
    private static final String[] l = new String[]{"action_id",
            ShortcutInfoCompat.EXTRA_SHORTCUT_ID, "expiration_time_millis", "publisher_package",
            "badge_package", "position"};
    private static ActionsController m;
    private final Handler b = new Handler(LauncherModel.getWorkerLooper(), this);
    private final Handler c = new Handler(Looper.getMainLooper(), this);
    private final Context n;
    private final SharedPreferences o;
    private final SharedPreferences p;
    private final ArrayList<Action> q = new ArrayList<>();
    private final ArrayList<Action> r = new ArrayList<>();
    private final ContentObserver s = new ContentObserver(this.c) {
        public void onChange(boolean z) {
            d();
        }
    };
    @SuppressWarnings("ComparatorCombinators")
    private final Comparator<Action> t = (o1, o2) -> Long.compare(o1.position, o2.position);
    private final Logger u = new Logger(this);
    private final LauncherAppsCompat v;
    private UpdateListener w;
    private PackageChangeCallback x = new PackageChangeCallback(this);

    private static class ActionData {

        String A;
        long B;
        String id;
        long position;
        String shortcutId;
        String z;

        private ActionData() {
        }
    }

    public static class Logger {

        public static final int CLICK_EVENT_TYPE = 1;
        public static final int DISMISS_EVENT_TYPE = 3;
        public static final int IMPRESSION_EVENT_TYPE = 2;
        private ActionsController C;

        public static class Data {

            int D;
            String E;
            int F;
            String G;
            long ts;

            private Data() {
            }

            public String toString() {
                StringBuilder stringBuilder = new StringBuilder("eventType:");
                stringBuilder.append(this.D);
                stringBuilder.append(" clickedId:");
                stringBuilder.append(this.E);
                stringBuilder.append(" clickedPos:");
                stringBuilder.append(this.F);
                stringBuilder.append(" top:");
                stringBuilder.append(this.G);
                return stringBuilder.toString();
            }
        }

        Logger(ActionsController actionsController) {
            this.C = actionsController;
        }

        public void logImpression() {
            SharedPreferences d = this.C.p;
            long currentTimeMillis = System.currentTimeMillis();
            int min = Math.min(this.C.r.size(), 2);
            for (int i = 0; i < min; i++) {
                String str = ((Action) this.C.r.get(i)).id;
                if (str != null) {
                    if (d.contains(str)) {
                        String string = d.getString(str, "");
                        String[] split = string.split(",");
                        long parseLong = Long.parseLong(split[0]);
                        for (int i2 = 1; i2 < split.length; i2++) {
                            parseLong += Long.parseLong(split[i2]);
                        }
                        long j = currentTimeMillis - parseLong;
                        Editor edit = d.edit();
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append(string);
                        stringBuilder.append(",");
                        stringBuilder.append(j);
                        edit.putString(str, stringBuilder.toString()).apply();
                    } else {
                        d.edit().putString(str, String.valueOf(currentTimeMillis)).apply();
                    }
                }
            }
        }

        public void logClick(String str, int i) {
            Data data = new Data();
            data.ts = System.currentTimeMillis();
            data.D = 1;
            if (str == null) {
                str = "";
            }
            data.E = str;
            data.F = i + 1;
            Message.obtain(this.C.b, 0, 0, 0, data).sendToTarget();
        }

        public void logDismiss(String str) {
            Data data = new Data();
            data.ts = System.currentTimeMillis();
            data.D = 3;
            if (str == null) {
                str = "";
            }
            data.E = str;
            Message.obtain(this.C.b, 0, 0, 0, data).sendToTarget();
        }
    }

    public class PackageChangeCallback implements OnAppsChangedCallbackCompat {

        final /* synthetic */ ActionsController y;

        public class PackageInfo {

            final /* synthetic */ PackageChangeCallback H;
            String packageName;
            UserHandle user;

            PackageInfo(PackageChangeCallback packageChangeCallback, String str,
                    UserHandle userHandle) {
                this.H = packageChangeCallback;
                this.packageName = str;
                this.user = userHandle;
            }
        }

        public void onShortcutsChanged(String str, List<ShortcutInfoCompat> list,
                UserHandle userHandle) {
        }

        private PackageChangeCallback(ActionsController actionsController) {
            this.y = actionsController;
        }

        public void onPackageRemoved(String str, UserHandle userHandle) {
            onPackageChanged(str, userHandle);
        }

        public void onPackageAdded(String str, UserHandle userHandle) {
            onPackageChanged(str, userHandle);
        }

        public void onPackageChanged(String str, UserHandle userHandle) {
            Message.obtain(this.y.b, 5, 0, 0, new PackageInfo(this, str, userHandle))
                    .sendToTarget();
        }

        public void onPackagesAvailable(String[] strArr, UserHandle userHandle, boolean z) {
            for (String onPackageChanged : strArr) {
                onPackageChanged(onPackageChanged, userHandle);
            }
        }

        public void onPackagesUnavailable(String[] strArr, UserHandle userHandle, boolean z) {
            for (String onPackageChanged : strArr) {
                onPackageChanged(onPackageChanged, userHandle);
            }
        }

        public void onPackagesSuspended(String[] strArr, UserHandle userHandle) {
            for (String onPackageChanged : strArr) {
                onPackageChanged(onPackageChanged, userHandle);
            }
        }

        public void onPackagesUnsuspended(String[] strArr, UserHandle userHandle) {
            for (String onPackageChanged : strArr) {
                onPackageChanged(onPackageChanged, userHandle);
            }
        }
    }

    public interface UpdateListener {

        void onUpdated(ArrayList<Action> arrayList);
    }

    public static ActionsController get(Context context) {
        Preconditions.assertUIThread();
        if (m == null) {
            m = new ActionsController(context.getApplicationContext());
        }
        return m;
    }

    private ActionsController(Context context) {
        this.n = context;
        this.o = Utilities.getPrefs(context);
        this.p = context.getSharedPreferences("pref_file_impressions", 0);
        this.o.registerOnSharedPreferenceChangeListener(this);
        b();
        c();
        this.v = LauncherAppsCompat.getInstance(context);
        this.v.addOnAppsChangedCallback(this.x);
        context.registerReceiver(new BroadcastReceiver() {

            public void onReceive(Context context, Intent intent) {
                c();
            }
        }, ActionIntentFilter.newInstance(AIAI_PACKAGE, "android.intent.action.PACKAGE_ADDED",
                "android.intent.action.PACKAGE_CHANGED", "android.intent.action.PACKAGE_REMOVED",
                "android.intent.action.PACKAGE_DATA_CLEARED",
                "android.intent.action.PACKAGE_RESTARTED"));
    }

    private boolean a() {
        return this.o.getBoolean("pref_show_suggested_actions", true);
    }

    private void b() {
        Message.obtain(this.b, 2, a()).sendToTarget();
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
        if ("pref_show_suggested_actions".equals(str)) {
            b();
            d();
        }
    }

    private void c() {
        ContentResolver contentResolver = this.n.getContentResolver();
        contentResolver.unregisterContentObserver(this.s);
        try {
            contentResolver.registerContentObserver(i, true, this.s);
            d();
            Message.obtain(this.b, 3).sendToTarget();
        } catch (Throwable e) {
            Log.w("ActionsController", "content provider not found", e);
        }
    }

    private static ArrayList<Action> filterEnabled(ArrayList<Action> arrayList) {
        ArrayList<Action> arrayList2 = new ArrayList<>();
        for (Action action : arrayList) {
            if (action.isEnabled) {
                arrayList2.add(action);
            }
        }
        return arrayList2;
    }

    public void updateActionsOnPackageChange(PackageInfo packageInfo) {
        Iterator it = this.q.iterator();
        Object obj = null;
        while (it.hasNext()) {
            Action action = (Action) it.next();
            if (action.shortcut != null && action.shortcutInfo != null && action.badgePackage
                    .equals(packageInfo.packageName) && action.shortcut.getUserHandle()
                    .equals(packageInfo.user)) {
                a(action);
                obj = 1;
            }
        }
        if (obj != null) {
            Message.obtain(this.c, 4, 0, 0, filterEnabled(this.q)).sendToTarget();
        }
    }

    private void d() {
        Message.obtain(this.b, 1).sendToTarget();
    }

    public boolean handleMessage(Message message) {
        switch (message.what) {
            case 0:
                try {
                    this.n.getContentResolver().insert(j, a((Data) message.obj));
                    break;
                } catch (Exception e) {
                    Log.e("ActionsController", "write log failed", e);
                    break;
                }
            case 1:
                Message.obtain(this.c, 4, 0, 0, filterEnabled(e())).sendToTarget();
                break;
            case 2:
                ContentValues contentValues = new ContentValues();
                contentValues.put("enable_action_suggest", (boolean) message.obj);
                try {
                    this.n.getContentResolver().insert(k, contentValues);
                    break;
                } catch (Exception e) {
                    Log.e("ActionsController", "write setting failed", e);
                    break;
                }
            case 3:
                f();
                break;
            case 4:
                this.r.clear();
                //noinspection unchecked
                this.r.addAll((ArrayList) message.obj);
                if (this.w != null) {
                    this.w.onUpdated(this.r);
                    break;
                }
                break;
            case 5:
                updateActionsOnPackageChange((PackageInfo) message.obj);
                break;
            default:
                break;
        }
        return true;
    }

    private ArrayList<com.google.android.apps.nexuslauncher.allapps.Action> e() {
        // TODO: decompile this manually
        /*
        r28 = this;
        r1 = r28;
        r0 = r1.q;
        r0.clear();
        r0 = r28.a();
        if (r0 != 0) goto L_0x0010;
    L_0x000d:
        r0 = r1.q;
        return r0;
    L_0x0010:
        r0 = r1.n;	 Catch:{ Exception -> 0x024e }
        r2 = com.android.launcher3.graphics.LauncherIcons.obtain(r0);	 Catch:{ Exception -> 0x024e }
        r3 = 0;
        r0 = r1.n;	 Catch:{ Throwable -> 0x0243, all -> 0x0240 }
        r0 = r0.getContentResolver();	 Catch:{ Throwable -> 0x0243, all -> 0x0240 }
        r4 = i;	 Catch:{ Throwable -> 0x0243, all -> 0x0240 }
        r4 = r0.acquireUnstableContentProviderClient(r4);	 Catch:{ Throwable -> 0x0243, all -> 0x0240 }
        r6 = i;	 Catch:{ Throwable -> 0x022e, all -> 0x0226 }
        r7 = l;	 Catch:{ Throwable -> 0x022e, all -> 0x0226 }
        r8 = 0;
        r9 = 0;
        r10 = 0;
        r5 = r4;
        r0 = r5.query(r6, r7, r8, r9, r10);	 Catch:{ Throwable -> 0x022e, all -> 0x0226 }
        if (r0 != 0) goto L_0x005d;
    L_0x0031:
        r0 = "ActionsController";
        r5 = "no cursor";
        android.util.Log.e(r0, r5);	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r0 = r1.q;	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        if (r4 == 0) goto L_0x0045;
    L_0x003c:
        a(r3, r4);	 Catch:{ Throwable -> 0x0040, all -> 0x0240 }
        goto L_0x0045;
    L_0x0040:
        r0 = move-exception;
        r3 = r0;
        r4 = r2;
        goto L_0x0246;
    L_0x0045:
        if (r2 == 0) goto L_0x004a;
    L_0x0047:
        a(r3, r2);	 Catch:{ Exception -> 0x024e }
    L_0x004a:
        return r0;
    L_0x004b:
        r0 = move-exception;
        r5 = r3;
    L_0x004d:
        r27 = r4;
        r4 = r2;
        r2 = r27;
        goto L_0x0237;
    L_0x0054:
        r0 = move-exception;
        r5 = r0;
        r27 = r4;
        r4 = r2;
        r2 = r27;
        goto L_0x0235;
    L_0x005d:
        r5 = new com.android.launcher3.util.MultiHashMap;	 Catch:{ Throwable -> 0x022e, all -> 0x0226 }
        r5.<init>();	 Catch:{ Throwable -> 0x022e, all -> 0x0226 }
    L_0x0062:
        r6 = r0.moveToNext();	 Catch:{ Throwable -> 0x022e, all -> 0x0226 }
        r7 = 1;
        if (r6 == 0) goto L_0x00cf;
    L_0x0069:
        r6 = new com.google.android.apps.nexuslauncher.allapps.ActionsController$ActionData;	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r8 = 0;
        r6.<init>();	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r8 = r0.getString(r8);	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r6.id = r8;	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r7 = r0.getString(r7);	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r6.shortcutId = r7;	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r7 = 3;
        r7 = r0.getString(r7);	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r6.z = r7;	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r7 = 4;
        r7 = r0.getString(r7);	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r6.A = r7;	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r7 = 2;
        r7 = r0.getLong(r7);	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r6.B = r7;	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r7 = 5;
        r7 = r0.getLong(r7);	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r6.position = r7;	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r7 = r6.B;	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r9 = 0;
        r7 = (r7 > r9 ? 1 : (r7 == r9 ? 0 : -1));
        if (r7 <= 0) goto L_0x00c9;
    L_0x009f:
        r7 = r6.B;	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r9 = java.lang.System.currentTimeMillis();	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r7 = (r7 > r9 ? 1 : (r7 == r9 ? 0 : -1));
        if (r7 >= 0) goto L_0x00c9;
    L_0x00a9:
        r7 = "ActionsController";
        r8 = new java.lang.StringBuilder;	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r9 = "shortcut expired id=";
        r8.<init>(r9);	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r9 = r6.shortcutId;	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r8.append(r9);	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r9 = " ts=";
        r8.append(r9);	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r9 = r6.B;	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r8.append(r9);	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r6 = r8.toString();	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        android.util.Log.d(r7, r6);	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        goto L_0x0062;
    L_0x00c9:
        r7 = r6.z;	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r5.addToList(r7, r6);	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        goto L_0x0062;
    L_0x00cf:
        r0 = r1.n;	 Catch:{ Throwable -> 0x022e, all -> 0x0226 }
        r0 = com.android.launcher3.shortcuts.DeepShortcutManager.getInstance(r0);	 Catch:{ Throwable -> 0x022e, all -> 0x0226 }
        r5 = r5.entrySet();	 Catch:{ Throwable -> 0x022e, all -> 0x0226 }
        r5 = r5.iterator();	 Catch:{ Throwable -> 0x022e, all -> 0x0226 }
    L_0x00dd:
        r6 = r5.hasNext();	 Catch:{ Throwable -> 0x022e, all -> 0x0226 }
        if (r6 == 0) goto L_0x01f4;
    L_0x00e3:
        r6 = r5.next();	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r6 = (java.util.Map.Entry) r6;	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r8 = r6.getKey();	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r8 = (java.lang.String) r8;	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r6 = r6.getValue();	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r6 = (java.util.ArrayList) r6;	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r9 = new java.util.ArrayList;	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r9.<init>();	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r10 = r6.iterator();	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
    L_0x00fe:
        r11 = r10.hasNext();	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        if (r11 == 0) goto L_0x0110;
    L_0x0104:
        r11 = r10.next();	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r11 = (com.google.android.apps.nexuslauncher.allapps.ActionsController.ActionData) r11;	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r11 = r11.shortcutId;	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r9.add(r11);	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        goto L_0x00fe;
    L_0x0110:
        r10 = android.os.Process.myUserHandle();	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r8 = r0.queryForFullDetails(r8, r9, r10);	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r8 = r8.iterator();	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
    L_0x011c:
        r9 = r8.hasNext();	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        if (r9 == 0) goto L_0x00dd;
    L_0x0122:
        r9 = r8.next();	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r9 = (com.android.launcher3.shortcuts.ShortcutInfoCompat) r9;	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r10 = r1.a(r6, r9);	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        if (r10 == 0) goto L_0x01ad;
    L_0x012e:
        r11 = r9.getShortLabel();	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r11 = android.text.TextUtils.isEmpty(r11);	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        if (r11 == 0) goto L_0x014b;
    L_0x0138:
        r11 = "ActionsController";
        r12 = new java.lang.StringBuilder;	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r13 = "Empty shortcut label: shortcut=";
        r12.<init>(r13);	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r12.append(r9);	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        r12 = r12.toString();	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
        android.util.Log.e(r11, r12);	 Catch:{ Throwable -> 0x0054, all -> 0x004b }
    L_0x014b:
        r15 = new com.android.launcher3.ShortcutInfo;	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r11 = r1.n;	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r15.<init>(r9, r11);	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r11 = r15.runtimeStatusFlags;	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r11 = r11 | 512;
        r15.runtimeStatusFlags = r11;	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r11 = r2.createShortcutIcon(r9, r7, r3);	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r11.applyTo(r15);	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r11 = r1.n;	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r11 = com.android.launcher3.LauncherAppState.getInstance(r11);	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r11 = r11.getIconCache();	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r11 = r2.getShortcutInfoBadge(r9, r11);	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r13 = r1.q;	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r14 = new com.google.android.apps.nexuslauncher.allapps.Action;	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r12 = r10.id;	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r7 = r10.shortcutId;	 Catch:{ Throwable -> 0x0054, all -> 0x01ef }
        r22 = r4;
        r3 = r10.B;	 Catch:{ Throwable -> 0x01a7, all -> 0x01a2 }
        r23 = r0;
        r0 = r10.z;	 Catch:{ Throwable -> 0x01a7, all -> 0x01a2 }
        r24 = r5;
        r5 = r10.A;	 Catch:{ Throwable -> 0x01a7, all -> 0x01a2 }
        r11 = r11.contentDescription;	 Catch:{ Throwable -> 0x01a7, all -> 0x01a2 }
        r25 = r2;
        r1 = r10.position;	 Catch:{ Throwable -> 0x01e5, all -> 0x01da }
        r10 = r14;
        r17 = r11;
        r11 = r12;
        r12 = r7;
        r26 = r6;
        r7 = r13;
        r6 = r14;
        r13 = r3;
        r3 = r15;
        r15 = r0;
        r16 = r5;
        r18 = r9;
        r19 = r3;
        r20 = r1;
        r10.<init>(r11, r12, r13, r15, r16, r17, r18, r19, r20);	 Catch:{ Throwable -> 0x01e5, all -> 0x01da }
        r7.add(r6);	 Catch:{ Throwable -> 0x01e5, all -> 0x01da }
        goto L_0x01ca;
    L_0x01a2:
        r0 = move-exception;
        r4 = r2;
        r2 = r22;
        goto L_0x01e1;
    L_0x01a7:
        r0 = move-exception;
        r5 = r0;
        r4 = r2;
        r2 = r22;
        goto L_0x01ed;
    L_0x01ad:
        r23 = r0;
        r25 = r2;
        r22 = r4;
        r24 = r5;
        r26 = r6;
        r0 = "ActionsController";
        r1 = new java.lang.StringBuilder;	 Catch:{ Throwable -> 0x01e5, all -> 0x01da }
        r2 = "shortcut details not found: shortcut=";
        r1.<init>(r2);	 Catch:{ Throwable -> 0x01e5, all -> 0x01da }
        r1.append(r9);	 Catch:{ Throwable -> 0x01e5, all -> 0x01da }
        r1 = r1.toString();	 Catch:{ Throwable -> 0x01e5, all -> 0x01da }
        android.util.Log.w(r0, r1);	 Catch:{ Throwable -> 0x01e5, all -> 0x01da }
    L_0x01ca:
        r4 = r22;
        r0 = r23;
        r5 = r24;
        r2 = r25;
        r6 = r26;
        r1 = r28;
        r3 = 0;
        r7 = 1;
        goto L_0x011c;
    L_0x01da:
        r0 = move-exception;
        r2 = r22;
        r4 = r25;
        r1 = r28;
    L_0x01e1:
        r3 = 0;
        r5 = 0;
        goto L_0x0237;
    L_0x01e5:
        r0 = move-exception;
        r5 = r0;
        r2 = r22;
        r4 = r25;
        r1 = r28;
    L_0x01ed:
        r3 = 0;
        goto L_0x0235;
    L_0x01ef:
        r0 = move-exception;
        r3 = 0;
        r5 = 0;
        goto L_0x004d;
    L_0x01f4:
        r25 = r2;
        r22 = r4;
        r0 = r1.q;	 Catch:{ Throwable -> 0x021f, all -> 0x0218 }
        r1.b(r0);	 Catch:{ Throwable -> 0x021f, all -> 0x0218 }
        if (r22 == 0) goto L_0x020f;
    L_0x01ff:
        r2 = r22;
        r3 = 0;
        a(r3, r2);	 Catch:{ Throwable -> 0x020a, all -> 0x0206 }
        goto L_0x0210;
    L_0x0206:
        r0 = move-exception;
        r4 = r25;
        goto L_0x0248;
    L_0x020a:
        r0 = move-exception;
        r3 = r0;
        r4 = r25;
        goto L_0x0246;
    L_0x020f:
        r3 = 0;
    L_0x0210:
        if (r25 == 0) goto L_0x0256;
    L_0x0212:
        r4 = r25;
        a(r3, r4);	 Catch:{ Exception -> 0x024e }
        goto L_0x0256;
    L_0x0218:
        r0 = move-exception;
        r2 = r22;
        r4 = r25;
        r3 = 0;
        goto L_0x022c;
    L_0x021f:
        r0 = move-exception;
        r2 = r22;
        r4 = r25;
        r3 = 0;
        goto L_0x0234;
    L_0x0226:
        r0 = move-exception;
        r27 = r4;
        r4 = r2;
        r2 = r27;
    L_0x022c:
        r5 = r3;
        goto L_0x0237;
    L_0x022e:
        r0 = move-exception;
        r27 = r4;
        r4 = r2;
        r2 = r27;
    L_0x0234:
        r5 = r0;
    L_0x0235:
        throw r5;	 Catch:{ all -> 0x0236 }
    L_0x0236:
        r0 = move-exception;
    L_0x0237:
        if (r2 == 0) goto L_0x023f;
    L_0x0239:
        a(r5, r2);	 Catch:{ Throwable -> 0x023d }
        goto L_0x023f;
    L_0x023d:
        r0 = move-exception;
        goto L_0x0245;
    L_0x023f:
        throw r0;	 Catch:{ Throwable -> 0x023d }
    L_0x0240:
        r0 = move-exception;
        r4 = r2;
        goto L_0x0248;
    L_0x0243:
        r0 = move-exception;
        r4 = r2;
    L_0x0245:
        r3 = r0;
    L_0x0246:
        throw r3;	 Catch:{ all -> 0x0247 }
    L_0x0247:
        r0 = move-exception;
    L_0x0248:
        if (r4 == 0) goto L_0x024d;
    L_0x024a:
        a(r3, r4);	 Catch:{ Exception -> 0x024e }
    L_0x024d:
        throw r0;	 Catch:{ Exception -> 0x024e }
    L_0x024e:
        r0 = move-exception;
        r2 = "ActionsController";
        r3 = "error loading actions";
        android.util.Log.e(r2, r3, r0);
    L_0x0256:
        r0 = r1.q;
        return r0;
        */
        return new ArrayList<>();
    }

    private ContentValues a(Data data) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("timestamp", data.ts);
        contentValues.put("event_type", data.D);
        contentValues.put("clicked_type", 0);
        contentValues.put("clicked_id", data.E);
        contentValues.put("clicked_position", data.F);
        contentValues.put("top_suggestions", data.G);
        return contentValues;
    }

    private void f() {
        try {
            Map<String, ?> all = this.p.getAll();
            Data data = new Data();
            data.D = 2;
            Set<String> keySet = all.keySet();
            ArrayList<ContentValues> arrayList = new ArrayList<>();
            for (String str : keySet) {
                String[] split = ((String) all.get(str)).split(",");
                data.ts = Long.parseLong(split[0]);
                data.G = str;
                arrayList.add(a(data));
                for (int i = 1; i < split.length; i++) {
                    data.ts += Long.parseLong(split[i]);
                    arrayList.add(a(data));
                }
            }
            this.n.getContentResolver().bulkInsert(j,
                    arrayList.toArray(new ContentValues[0]));
        } catch (Exception e) {
            Log.e("ActionsController", "write impression logs", e);
        } catch (Throwable th) {
            this.p.edit().clear().apply();
        }
    }

    public ArrayList<Action> getActions() {
        return this.r;
    }

    private void b(ArrayList<Action> arrayList) {
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            Action action = (Action) it.next();
            if (action.shortcut != null) {
                if (action.shortcutInfo != null) {
                    a(action);
                }
            }
            it.remove();
        }
        Collections.sort(arrayList, this.t);
    }

    private void a(Action action) {
        action.isEnabled = false;
        if (action.shortcut.isEnabled()) {
            ApplicationInfo applicationInfo = this.v
                    .getApplicationInfo(action.badgePackage, 0, action.shortcut.getUserHandle());
            if (applicationInfo != null && applicationInfo.enabled && !PackageManagerHelper
                    .isAppSuspended(applicationInfo)) {
                action.isEnabled = true;
            }
        }
    }

    public void setListener(UpdateListener updateListener) {
        this.w = updateListener;
    }

    public Logger getLogger() {
        return this.u;
    }

    private ActionData a(List<ActionData> list, ShortcutInfoCompat shortcutInfoCompat) {
        if (shortcutInfoCompat != null) {
            for (ActionData actionData : list) {
                if (actionData.shortcutId.equals(shortcutInfoCompat.getId())) {
                    return actionData;
                }
            }
        }
        return null;
    }

    public void onActionDismissed(Action action) {
        if (action != null) {
            this.u.logDismiss(action.id);
        }
    }
}