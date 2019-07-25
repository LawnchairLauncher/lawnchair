/*
 *     Copyright (c) 2017-2019 the Lawnchair team
 *     Copyright (c)  2019 oldosfan (would)
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

package ch.deletescape.lawnchair.clockhide

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import ch.deletescape.lawnchair.LawnchairLauncher
import ch.deletescape.lawnchair.root.RootHelperManager
import java.util.concurrent.Executors

@Deprecated("No longer used and scheduled for removal")
class ClockhideService : Service() {

    val executor =  Executors.newSingleThreadExecutor()

    @SuppressLint("WrongConstant")
    /* This fixes weird lint errors with return statements in lambdas */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
       executor.submit {
            while (true) {
                if (!Thread.interrupted()) {
                    if (!LawnchairLauncher.getLauncher(
                                    this.applicationContext).hasWindowFocus() && RootHelperManager.isAvailable) {
                        RootHelperManager.getInstance(this.applicationContext).run {
                            try {
                                it.iconBlacklistPreference =
                                        it.iconBlacklistPreference.remove("clock")
                            } catch (e: RemoteException) {
                                e.printStackTrace()
                            }
                        }
                    } else if (RootHelperManager.isAvailable) {
                        RootHelperManager.getInstance(this.applicationContext).run {
                            try {
                                it.iconBlacklistPreference = it.iconBlacklistPreference.add("clock")
                            } catch (e: RemoteException) {
                                e.printStackTrace()
                            }
                        }
                    }
                } else {
                    break
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        executor.shutdownNow()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
