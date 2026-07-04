package app.lawnchair.bugreport

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.lawnchair.util.requireSystemService
import com.android.launcher3.R
import com.android.launcher3.Utilities
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

class UploaderService : Service() {

    private val lock = Any()

    @Volatile private var job: Job? = null
    private var latestStartId = 0
    private val scope = CoroutineScope(Dispatchers.IO) + CoroutineName("UploaderService")
    private val uploadQueue: Queue<BugReport> = ConcurrentLinkedQueue()

    override fun onBind(intent: Intent): IBinder {
        TODO("not implemented")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_REDELIVER_INTENT
        val report = intent.getParcelableExtra<BugReport>("report") ?: return START_STICKY
        synchronized(lock) {
            uploadQueue.offer(report)
            latestStartId = startId
            if (job == null) {
                job = scope.launch { startUpload() }
            }
        }
        return START_STICKY
    }

    private suspend fun startUpload() {
        while (true) {
            // Atomically take the next report, or clear the job and stop looping once
            // the queue is drained, so a report enqueued concurrently is never dropped.
            var report = synchronized(lock) {
                uploadQueue.poll() ?: run {
                    job = null
                    null
                }
            } ?: break
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
        // Only stop the service if no new worker was started while draining.
        synchronized(lock) {
            if (job == null) {
                stopSelf(latestStartId)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        Log.d("DUS", "onCreate")

        val notificationManager: NotificationManager = requireSystemService()
        notificationManager.createNotificationChannel(
            NotificationChannel(
                BugReportReceiver.STATUS_CHANNEL_ID,
                getString(R.string.status_channel_name),
                NotificationManager.IMPORTANCE_NONE,
            ),
        )

        val notification = NotificationCompat.Builder(this, BugReportReceiver.STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bug_notification)
            .setContentTitle(getString(R.string.dogbin_uploading))
            .setColor(ContextCompat.getColor(this, R.color.bugNotificationColor))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        if (Utilities.ATLEAST_U) {
            startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(101, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
    }
}
