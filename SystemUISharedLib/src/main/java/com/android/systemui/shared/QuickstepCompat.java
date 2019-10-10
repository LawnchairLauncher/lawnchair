package com.android.systemui.shared;

import android.os.Build;

import xyz.paphonb.quickstep.compat.ActivityManagerCompat;
import xyz.paphonb.quickstep.compat.InputCompat;
import xyz.paphonb.quickstep.compat.QuickstepCompatFactory;
import xyz.paphonb.quickstep.compat.RecentsCompat;
import xyz.paphonb.quickstep.compat.pie.QuickstepCompatFactoryVP;
import xyz.paphonb.quickstep.compat.ten.QuickstepCompatFactoryVQ;

public class QuickstepCompat {

    private static ActivityManagerCompat sActivityManagerCompat;
    private static RecentsCompat sRecentsModelCompat;
    private static InputCompat sInputCompat;

    static {
        QuickstepCompatFactory factory;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            factory = new QuickstepCompatFactoryVQ();
        } else {
            factory = new QuickstepCompatFactoryVP();
        }
        sActivityManagerCompat = factory.getActivityManagerCompat();
        sRecentsModelCompat = factory.getRecentsModelCompat();
        sInputCompat = factory.getInputCompat();
    }

    public static ActivityManagerCompat getActivityManager() {
        return sActivityManagerCompat;
    }

    public static RecentsCompat getRecentsCompat() {
        return sRecentsModelCompat;
    }

    public static InputCompat getInputCompat() {
        return sInputCompat;
    }
}
