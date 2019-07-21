/*
 *     Copyright (C) 2019 Lawnchair Team.
 *
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.customnavbar

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.support.v4.content.ContextCompat
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.runOnMainThread
import ch.deletescape.lawnchair.util.LawnchairSingletonHolder
import ch.deletescape.lawnchair.util.extensions.e
import com.android.launcher3.LauncherAppState
import com.android.launcher3.util.PackageManagerHelper
import xyz.paphonb.systemuituner.ICustomNavBar

class CustomNavBar(private val context: Context) {

    val isInstalled get() = PackageManagerHelper.isAppEnabled(context.packageManager, PACKAGE, 0)
    val testVersionInstalled = context.packageManager.resolveService(CustomNavBar.serviceIntent, 0) != null
    val isPermissionGranted get() = ContextCompat.checkSelfPermission(context, MODIFY_NAVBAR_PERMISSION) == PackageManager.PERMISSION_GRANTED
    var enableIntegration by context.lawnchairPrefs.BooleanPref("pref_cnbIntegration", false) {
        rebindService()
    }
    private var cnbService: ICustomNavBar? = null
    private val cnbServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, service: IBinder) {
            cnbService = ICustomNavBar.Stub.asInterface(service).also {
                if (isBackButtonHidden) {
                    it.setBackButtonHidden(isBackButtonHidden)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            cnbService = null
        }
    }

    var backButtonAlpha = 0f
        set(value) {
            field = value
            isBackButtonHidden = value < .5f
        }
    private var isBackButtonHidden = false
        set(value) {
            if (field != value) {
                field = value
                if (value) rebindService()
                cnbService?.setBackButtonHidden(value)
                runOnMainThread(::forceUpdateLightNavBar)
            }
        }

    init {
        if (!testVersionInstalled || !isPermissionGranted) {
            enableIntegration = false
        }
        rebindService()
    }

    private fun forceUpdateLightNavBar() {
        if (!isBackButtonHidden) {
            LauncherAppState.getInstance(context)
                    .launcher?.systemUiController?.forceUpdateLightNavBar()
        }
    }

    private fun rebindService() {
        if (enableIntegration && cnbService == null) {
            try {
                context.bindService(serviceIntent, cnbServiceConnection, Context.BIND_AUTO_CREATE)
            } catch (t: Throwable) {
                e("Failed to rebind", t)
            }
        } else if (!enableIntegration && cnbService != null) {
            cnbService?.setBackButtonHidden(false)
            cnbService = null
            context.unbindService(cnbServiceConnection)
        }
    }

    companion object : LawnchairSingletonHolder<CustomNavBar>(::CustomNavBar) {

        const val PACKAGE = "xyz.paphonb.systemuituner"
        private const val NAVBAR_SERVICE_ACTION = "$PACKAGE.intent.action.NAVBAR_SERVICE"
        const val MODIFY_NAVBAR_PERMISSION = "$PACKAGE.permission.MODIFY_NAVBAR"

        private val serviceIntent = Intent(NAVBAR_SERVICE_ACTION).setPackage(PACKAGE)!!
    }
}
