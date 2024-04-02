package app.lawnchair.bugreport

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.android.launcher3.R
import java.util.LinkedList
import java.util.Queue
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

class UploaderService : Service() {

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO) + CoroutineName("UploaderService")
    private val uploadQueue: Queue<BugReport> = LinkedList()

    override fun onBind(intent: Intent): IBinder {
        TODO("not implemented")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_REDELIVER_INTENT
        uploadQueue.offer(intent.getParcelableExtra("report"))
        if (job == null) {
            job = scope.launch {
                startUpload()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private suspend fun startUpload() {
        while (uploadQueue.isNotEmpty()) {
            var report = uploadQueue.poll()!!
            try {
                report = report.copy(link = UploaderUtils.upload(report))
            } catch (e: Throwable) {
                Log.d("UploaderService", "failed to upload bug report", e)
                report = report.copy(uploadError = true)
            } finally {
                sendBroadcast(
                    Intent(this, BugReportReceiver::class.java)
                        .setAction(BugReportReceiver.UPLOAD_COMPLETE_ACTION)
                        .putExtra("report", report),
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        Log.d("DUS", "onCreate")

        startForeground(
            101,
            NotificationCompat.Builder(this, BugReportReceiver.STATUS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bug_notification)
                .setContentTitle(getString(R.string.dogbin_uploading))
                .setColor(ContextCompat.getColor(this, R.color.bugNotificationColor))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build(),
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
    }
}
