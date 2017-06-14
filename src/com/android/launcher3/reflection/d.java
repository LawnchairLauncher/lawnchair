package com.android.launcher3.reflection;

import com.android.launcher3.compat.LauncherActivityInfoCompat;
import java.util.Iterator;
import java.util.List;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.util.CachedPackageTracker;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.util.Preconditions;
import android.content.Context;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.reflection.filter.b;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.reflection.filter.f;
import com.android.launcher3.util.CachedPackageTracker;

public class d extends CachedPackageTracker implements k
{
    private final f as;
    private final LauncherAppsCompat at;
    private final com.android.launcher3.reflection.filter.d au;
    private final b av;
    private final j aw;
    private final UserManagerCompat ax;
    private final Context mContext;

    public d(final Context mContext, final j aw, final b av, final f as, final com.android.launcher3.reflection.filter.d au) {
        super(mContext, "package_info");
        this.mContext = mContext;
        this.ax = UserManagerCompat.getInstance(mContext);
        this.at = LauncherAppsCompat.getInstance(mContext);
        this.aw = aw;
        Preconditions.assertNonUiThread();
        this.at.addOnAppsChangedCallback(this);
        this.av = av;
        this.as = as;
        this.au = au;
    }

    public void F() {
        this.at.removeOnAppsChangedCallback(this);
    }

    protected void W(final CachedPackageTracker.LauncherActivityInstallInfo cachedPackageTracker$LauncherActivityInstallInfo, final UserHandleCompat userHandleCompat, final boolean b) {
        this.av.j(1, cachedPackageTracker$LauncherActivityInstallInfo.info, userHandleCompat);
        this.as.s(cachedPackageTracker$LauncherActivityInstallInfo.info, userHandleCompat);
        if (b) {
            this.au.o(cachedPackageTracker$LauncherActivityInstallInfo.info.getComponentName(), this.ax.getSerialNumberForUser(userHandleCompat), cachedPackageTracker$LauncherActivityInstallInfo.installTime);
        }
    }

    protected void onLauncherAppsAdded(final List list, final UserHandleCompat userHandleCompat, final boolean b) {
        final Iterator<CachedPackageTracker.LauncherActivityInstallInfo> iterator = list.iterator();
        while (iterator.hasNext()) {
            this.W(iterator.next(), userHandleCompat, b);
        }
    }

    protected void onLauncherPackageRemoved(final String s, final UserHandleCompat userHandleCompat) {
        this.av.k(0, s, userHandleCompat);
        this.as.t(s, userHandleCompat);
        this.aw.ax(s, this.ax.getSerialNumberForUser(userHandleCompat));
    }

    public void onPackageChanged(final String s, final UserHandleCompat userHandleCompat) {
        this.av.k(-1, s, userHandleCompat);
        this.as.t(s, userHandleCompat);
    }

    public void onPackagesAvailable(final String[] array, final UserHandleCompat userHandleCompat, final boolean b) {
        this.av.l(-1, array, userHandleCompat);
        this.as.u(array, userHandleCompat);
    }

    public void onPackagesSuspended(final String[] array, final UserHandleCompat userHandleCompat) {
        this.av.l(0, array, userHandleCompat);
        this.as.u(array, userHandleCompat);
    }

    public void onPackagesUnavailable(final String[] array, final UserHandleCompat userHandleCompat, final boolean b) {
        this.av.l(0, array, userHandleCompat);
        this.as.u(array, userHandleCompat);
    }

    public void onPackagesUnsuspended(final String[] array, final UserHandleCompat userHandleCompat) {
        this.av.l(-1, array, userHandleCompat);
        this.as.u(array, userHandleCompat);
    }

    public void onShortcutsChanged(final String s, final List list, final UserHandleCompat userHandleCompat) {
    }

    public void processUserApps(final List list, final UserHandleCompat userHandleCompat) {
        for (int i = list.size() - 1; i >= 0; --i) {
            final LauncherActivityInfoCompat launcherActivityInfoCompat = (LauncherActivityInfoCompat) list.get(i);
            this.av.j(1, launcherActivityInfoCompat, userHandleCompat);
            this.as.s(launcherActivityInfoCompat, userHandleCompat);
        }
        super.processUserApps(list, userHandleCompat);
    }
}