/*
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

package ch.deletescape.lawnchair

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.ResultReceiver

class BlankActivity : Activity() {

    private val requestCode by lazy { intent.getIntExtra("requestCode", 0) }
    private val resultReceiver by lazy { intent.getParcelableExtra("callback") as ResultReceiver }
    private var resultSent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.hasExtra("intent")) {
            startActivityForResult(intent.getParcelableExtra("intent"), requestCode)
        } else {
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == this.requestCode) {
            resultReceiver.send(resultCode, data?.extras)
            resultSent = true
            finish()
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun finish() {
        if (!resultSent)
            resultReceiver.send(RESULT_CANCELED, null)
        super.finish()
    }

    companion object {

        fun startActivityForResult(context: Context, targetIntent: Intent, requestCode: Int,
                                   flags: Int, callback: (Int, Bundle?) -> Unit) {
            val intent = Intent(context, BlankActivity::class.java).apply {
                putExtra("intent", targetIntent)
                putExtra("requestCode", requestCode)
                putExtra("callback", object : ResultReceiver(Handler()) {

                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        callback(resultCode, resultData)
                    }
                })
            }
            val foreground = context.lawnchairApp.activityHandler.foregroundActivity ?: context
            foreground.startActivity(intent)
        }
    }
}
