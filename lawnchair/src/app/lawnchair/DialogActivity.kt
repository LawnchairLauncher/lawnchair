package app.lawnchair

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ModalBottomSheetDefaults
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import app.lawnchair.ui.preferences.components.SystemUi
import app.lawnchair.ui.theme.LawnchairTheme
import com.google.accompanist.insets.ProvideWindowInsets

class DialogActivity : AppCompatActivity() {

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
                ProvideWindowInsets {
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

        fun getDialogIntent(
            context: Context, targetIntent: Intent,
            dialogTitle: String, dialogMessage: String,
            positiveButton: String, callback: (Int) -> Unit
        ): Intent {
            return Intent(context, DialogActivity::class.java).apply {
                putExtra("intent", targetIntent)
                putExtra("dialogTitle", dialogTitle)
                putExtra("dialogMessage", dialogMessage)
                putExtra("positiveButton", positiveButton)
                putExtra("callback", object : ResultReceiver(Handler(Looper.myLooper()!!)) {

                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        callback(resultCode)
                    }
                })
            }
        }
    }
}
