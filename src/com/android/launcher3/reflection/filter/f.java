package com.android.launcher3.reflection.filter;

import com.android.launcher3.util.Preconditions;
import com.android.launcher3.compat.LauncherActivityInfoCompat;
import java.util.Iterator;
import com.android.launcher3.reflection.b_research;
import java.util.List;
import java.util.regex.Matcher;
import com.android.launcher3.util.ComponentKey;
import android.content.ComponentName;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.reflection.m;
import java.util.Collections;
import java.util.HashMap;
import android.content.Context;
import java.util.Map;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.compat.LauncherAppsCompat;

public class f
{
    private final LauncherAppsCompat A;
    private final UserManagerCompat B;
    private final Map z;

    public f(final Context context) {
        this.B = UserManagerCompat.getInstance(context);
        this.A = LauncherAppsCompat.getInstance(context);
        this.z = Collections.synchronizedMap(new HashMap<Object, Object>());
    }

    private boolean v(final String s) {
        final Matcher matcher = m.bg.matcher(s);
        if (matcher.find()) {
            final String group = matcher.group(1);
            final String group2 = matcher.group(2);
            final String group3 = matcher.group(4);
            UserHandleCompat userHandleCompat = null;
            Label_0084: {
                if (group3 != null) {
                    try {
                        final UserManagerCompat b = this.B;
                        try {
                            userHandleCompat = b.getUserForSerialNumber(Long.parseLong(group3));
                            if (userHandleCompat == null) {
                                return false;
                            }
                            break Label_0084;
                        }
                        catch (NumberFormatException ex) {
                            return false;
                        }
                    }
                    catch (NumberFormatException ex2) {}
                }
                userHandleCompat = UserHandleCompat.myUserHandle();
            }
            final ComponentKey componentKey = new ComponentKey(new ComponentName(group, group2), userHandleCompat);
            Boolean value = (Boolean) this.z.get(componentKey);
            if (value == null) {
                value = this.A.isActivityEnabledForProfile(componentKey.componentName, componentKey.user);
            }
            this.z.put(componentKey, value);
            return value;
        }
        return false;
    }

    public void c(final List list, final List list2) {
        final Iterator<b_research> iterator = list.iterator();
        while (iterator.hasNext()) {
            final b_research b = iterator.next();
            if (!this.v(b.Ld)) {
                if (list2 != null) {
                    list2.add(b);
                }
                iterator.remove();
            }
        }
    }

    public void s(final LauncherActivityInfoCompat launcherActivityInfoCompat, final UserHandleCompat userHandleCompat) {
        this.z.put(new ComponentKey(launcherActivityInfoCompat.getComponentName(), userHandleCompat), true);
    }

    public void t(final String s, final UserHandleCompat userHandleCompat) {
        Preconditions.assertNonUiThread();
        final Iterator<ComponentKey> iterator = this.z.keySet().iterator();
        while (iterator.hasNext()) {
            final ComponentKey componentKey = iterator.next();
            if (s.equals(componentKey.componentName.getPackageName()) && userHandleCompat.equals(componentKey.user)) {
                iterator.remove();
            }
        }
    }

    public void u(final String[] array, final UserHandleCompat userHandleCompat) {
        for (int i = 0; i < array.length; ++i) {
            this.t(array[i], userHandleCompat);
        }
    }
}