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

        fun startActivityForResult(context: Context, intent: Intent, requestCode: Int, callback: (Int, Bundle?) -> Unit) {
            val foreground = context.lawnchairApp.activityHandler.foregroundActivity ?: context
            foreground.startActivity(Intent(context, BlankActivity::class.java).apply {
                putExtra("intent", intent)
                putExtra("requestCode", requestCode)
                putExtra("callback", object : ResultReceiver(Handler()) {

                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        callback(resultCode, resultData)
                    }
                })
            })
        }
    }
}
