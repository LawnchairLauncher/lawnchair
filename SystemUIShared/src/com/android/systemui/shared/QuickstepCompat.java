package com.android.systemui.shared;

import android.annotation.SuppressLint;
import android.os.Build;

import app.lawnchair.compatlib.ActivityManagerCompat;
import app.lawnchair.compatlib.QuickstepCompatFactory;
import app.lawnchair.compatlib.eleven.QuickstepCompatFactoryVR;
import app.lawnchair.compatlib.twelve.QuickstepCompatFactoryVS;

public class QuickstepCompat {

    private static final QuickstepCompatFactory sFactory;
    private static final ActivityManagerCompat sActivityManagerCompat;

    @SuppressLint("AnnotateVersionCheck")
    public static final boolean ATLEAST_S = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;

    static {
        if (ATLEAST_S) {
            sFactory = new QuickstepCompatFactoryVS();
        } else {
            sFactory = new QuickstepCompatFactoryVR();
        }
        sActivityManagerCompat = sFactory.getActivityManagerCompat();
    }

    public static QuickstepCompatFactory getFactory() {
        return sFactory;
    }

    public static ActivityManagerCompat getActivityManagerCompat() {
        return sActivityManagerCompat;
    }
}
