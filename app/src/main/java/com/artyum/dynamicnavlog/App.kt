package com.artyum.dynamicnavlog

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

const val channelName = "Navlog Service"
const val channelDescription = "Navlog background processing"

class App : Application() {
    companion object {
        const val CHANNEL_ID: String = "serviceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val mChannel = NotificationChannel(CHANNEL_ID, channelName, importance)
        mChannel.description = channelDescription
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(mChannel)
    }
}