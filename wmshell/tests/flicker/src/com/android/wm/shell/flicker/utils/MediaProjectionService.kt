/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.wm.shell.flicker.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log

class MediaProjectionService : Service() {

    private var mTestBitmap: Bitmap? = null

    private val notificationId: Int = 1
    private val notificationChannelId: String = "MediaProjectionFlickerTest"
    private val notificationChannelName = "FlickerMediaProjectionService"

    var mMessenger: Messenger? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        mMessenger = intent.extras?.getParcelable(
            MediaProjectionUtils.EXTRA_MESSENGER, Messenger::class.java)
        startForeground()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mTestBitmap?.recycle()
        mTestBitmap = null
        sendMessage(MediaProjectionUtils.MSG_SERVICE_DESTROYED)
        super.onDestroy()
    }

    private fun createNotificationIcon(): Icon {
        Log.d(TAG, "createNotification")

        mTestBitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mTestBitmap!!)
        canvas.drawColor(Color.BLUE)
        return Icon.createWithBitmap(mTestBitmap)
    }

    private fun startForeground() {
        Log.d(TAG, "startForeground")
        val channel = NotificationChannel(
            notificationChannelId,
            notificationChannelName, NotificationManager.IMPORTANCE_NONE
        )
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE

        val notificationManager: NotificationManager =
            getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        val notificationBuilder: Notification.Builder =
            Notification.Builder(this, notificationChannelId)

        val notification = notificationBuilder.setOngoing(true)
            .setContentTitle("App is running")
            .setSmallIcon(createNotificationIcon())
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentText("Context")
            .build()

        startForeground(notificationId, notification)
        sendMessage(MediaProjectionUtils.MSG_START_FOREGROUND_DONE)
    }

    fun sendMessage(what: Int) {
        Log.d(TAG, "sendMessage")
        with(Message.obtain()) {
            this.what = what
            try {
                mMessenger!!.send(this)
            } catch (e: RemoteException) {
                Log.d(TAG, "Unable to send message", e)
            }
        }
    }

    companion object {
        private const val TAG: String = "FlickerMediaProjectionService"
    }
}