package app.lawnchair.compat

import android.os.Build
import android.util.Log
import app.lawnchair.compatlib.ActivityManagerCompat
import app.lawnchair.compatlib.ActivityOptionsCompat
import app.lawnchair.compatlib.QuickstepCompatFactory
import app.lawnchair.compatlib.RemoteTransitionCompat
import app.lawnchair.compatlib.eleven.QuickstepCompatFactoryVR
import app.lawnchair.compatlib.fourteen.QuickstepCompatFactoryVU
import app.lawnchair.compatlib.ten.QuickstepCompatFactoryVQ
import app.lawnchair.compatlib.thirteen.QuickstepCompatFactoryVT
import app.lawnchair.compatlib.twelve.QuickstepCompatFactoryVS

object LawnchairQuickstepCompat {

    val TAG: String = "LawnchairQuickstepCompat"
    
    @JvmField
    val ATLEAST_Q: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @JvmField
    val ATLEAST_R: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    @JvmField
    val ATLEAST_S: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    @JvmField
    val ATLEAST_T: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    @JvmField
    val ATLEAST_U: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE


    @JvmStatic
    val factory: QuickstepCompatFactory = if (ATLEAST_U) {
        QuickstepCompatFactoryVU()
    } else if (ATLEAST_T) {
        QuickstepCompatFactoryVT()
    } else if (ATLEAST_S) {
        QuickstepCompatFactoryVS()
    } else if (ATLEAST_R) {
        QuickstepCompatFactoryVR()
    } else {
        QuickstepCompatFactoryVQ()
    }

    /**
     * For rom Android 13 empty recent checker
     *  TODO
     */
    @JvmStatic
    val isDecember2022Patch: Boolean
        get() {
            val december2022Patch = "2022-12"
            val currentSecurityPatch = Build.VERSION.SECURITY_PATCH
            val currentBuildNumber = Build.ID
            val currentYearMonth = currentSecurityPatch.substring(0, 7)
            Log.d(TAG, ": $currentBuildNumber $currentYearMonth")
            return (currentYearMonth <= december2022Patch && ATLEAST_T) 
                    || (currentBuildNumber.contains("TQ1") && ATLEAST_T)
                    || (currentBuildNumber.contains("TQ2") && ATLEAST_T)
        }

    @JvmStatic
    val activityManagerCompat: ActivityManagerCompat = factory.activityManagerCompat

    @JvmStatic
    val activityOptionsCompat: ActivityOptionsCompat = factory.activityOptionsCompat

    @JvmStatic
    val remoteTransitionCompat: RemoteTransitionCompat = factory.remoteTransitionCompat
}