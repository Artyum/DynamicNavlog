package com.artyum.dynamicnavlog

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.IBinder
import com.artyum.dynamicnavlog.App.Companion.CHANNEL_ID

class Service : android.app.Service() {
    private val TAG = "Service"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //val input: String? = intent?.getStringExtra("inputExtra")

        val notificationIntent = Intent(this, MainActivity::class.java)

        //val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.txtBackgroundServiceIsRunning))
            //.setContentText(input)
            .setSmallIcon(R.drawable.ic_flight)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}