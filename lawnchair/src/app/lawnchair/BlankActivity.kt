package app.lawnchair

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ModalBottomSheetDefaults
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import app.lawnchair.ui.preferences.components.SystemUi
import app.lawnchair.ui.theme.LawnchairTheme
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class BlankActivity : AppCompatActivity() {

    private val resultReceiver by lazy { intent.getParcelableExtra<ResultReceiver>("callback")!! }
    private var resultSent = false
    private var firstResume = true
    private var targetStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (!intent.hasExtra("dialogTitle")) {
            startTargetActivity()
            return
        }
        setContent {
            SystemUi()
            LawnchairTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ModalBottomSheetDefaults.scrimColor
                ) {
                    AlertDialog(
                        onDismissRequest = { if (!targetStarted) finish() },
                        confirmButton = {
                            Button(onClick = { startTargetActivity() }) {
                                Text(text = intent.getStringExtra("positiveButton")!!)
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { finish() }) {
                                Text(text = stringResource(id = android.R.string.cancel))
                            }
                        },
                        title = {
                            Text(text = intent.getStringExtra("dialogTitle")!!)
                        },
                        text = {
                            Text(text = intent.getStringExtra("dialogMessage")!!)
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (firstResume) {
            firstResume = false
            return
        }
        finish()
    }

    private fun startTargetActivity() {
        when {
            intent.hasExtra("intent") -> {
                if (intent.hasExtra("dialogTitle")) {
                    startActivity(intent.getParcelableExtra("intent"))
                } else {
                    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                        resultReceiver.send(it.resultCode, it.data?.extras)
                        resultSent = true
                        finish()
                    }.launch(intent.getParcelableExtra("intent"))
                }
            }
            else -> {
                finish()
                return
            }
        }
        targetStarted = true
    }

    override fun onDestroy() {
        super.onDestroy()

        if (!resultSent && intent.hasExtra("callback")) {
            resultSent = true
            resultReceiver.send(RESULT_CANCELED, null)
        }
    }

    companion object {

        suspend fun startBlankActivityDialog(
            activity: Activity, targetIntent: Intent,
            dialogTitle: String, dialogMessage: String,
            positiveButton: String
        ) {
            start(activity, targetIntent, bundleOf(
                "intent" to targetIntent,
                "dialogTitle" to dialogTitle,
                "dialogMessage" to dialogMessage,
                "positiveButton" to positiveButton,
            ))
        }

        suspend fun startBlankActivityForResult(activity: Activity, targetIntent: Intent): ActivityResult {
            return start(activity, targetIntent, Bundle.EMPTY)
        }

        private suspend fun start(activity: Activity, targetIntent: Intent, extras: Bundle): ActivityResult {
            return suspendCancellableCoroutine { continuation ->
                val intent = Intent(activity, BlankActivity::class.java)
                    .putExtras(extras)
                    .putExtra("intent", targetIntent)
                val resultReceiver = createResultReceiver {
                    if (continuation.isActive) {
                        continuation.resume(it)
                    }
                }
                activity.startActivity(intent.putExtra("callback", resultReceiver))
            }
        }

        private fun createResultReceiver(callback: (ActivityResult) -> Unit): ResultReceiver {
            return object : ResultReceiver(Handler(Looper.myLooper()!!)) {

                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    val data = Intent()
                    if (resultData != null) {
                        data.putExtras(resultData)
                    }
                    callback(ActivityResult(resultCode, data))
                }
            }
        }
    }
}
