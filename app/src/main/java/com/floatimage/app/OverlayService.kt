package com.floatimage.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder

class OverlayService : Service() {
    companion object { @Volatile var running = false }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() { super.onCreate(); running = true }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("ov", "Floating images", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this, 0, Intent(this, OverlayService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "ov")
            .setContentTitle("Floating images are on")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop all", stop)
            .build()
        startForeground(1, n)
        if (i?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onDestroy() {
        Overlays.removeAll(applicationContext)
        running = false
        super.onDestroy()
    }
}
