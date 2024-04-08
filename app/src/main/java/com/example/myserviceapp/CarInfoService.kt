package com.example.myserviceapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.View
import androidx.core.app.NotificationCompat
import android.util.DisplayMetrics
import android.widget.TextView

// 通知チャネルのID
private const val CHANNEL_ID = "carinfo_channel_id"
class CarInfoService : Service() {
    private val TAG = MyService::class.java.simpleName
    private val windowManager: WindowManager by lazy {
        getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    private var overlayView: View? = null
    private lateinit var textViewLeftTemp: TextView
    private lateinit var textViewRightTemp: TextView

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        val channelName = "My Service Channel"
        val channelDescription = "Channel for My Service"
        val channelImportance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID , channelName, channelImportance).apply {
            description = channelDescription
        }
        // 通知マネージャを取得してチャネルをシステムに登録
        val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val action: String = intent.getStringExtra("action") ?: ""

        when (action) {
            "SHOW_OVERLAY" -> {
                Log.d(TAG, "SHOW_OVERLAY")
                if (overlayView == null) {
                    overlayView = LayoutInflater.from(this).inflate(R.layout.carinfo_layout, null, false)
                    textViewLeftTemp = overlayView!!.findViewById(R.id.textViewLeftTemp)
                    textViewRightTemp = overlayView!!.findViewById(R.id.textViewRightTemp)

                    val displayMetrics = DisplayMetrics()

                    // APIレベルに応じたDisplayの取得方法を使用
                    val display = windowManager.defaultDisplay
                    display?.getRealMetrics(displayMetrics)

                    val params = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        140,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.BOTTOM
                    }

                    windowManager.addView(overlayView, params)
                    Log.d(TAG, "Overlay added")
                }
                startForegroundService()
            }
            "HIDE_OVERLAY" -> {
                Log.d(TAG, "HIDE_OVERLAY")
                if (overlayView != null) {
                    windowManager.removeView(overlayView)
                    overlayView = null
                    Log.d(TAG, "Overlay removed")
                }
            }
            "SET_LEFT_TEMP_DELTA" -> {
                val leftTemp: Double = intent.getDoubleExtra("left_temp", 0.0)
                val currentTemp: Double = textViewLeftTemp.text.toString().toDoubleOrNull() ?: 0.0
                val nextTemp:Double = leftTemp + currentTemp
                Log.d(TAG, "SET_LEFT_TEMP_DELTA $currentTemp + $leftTemp = $nextTemp")
                textViewLeftTemp.text = nextTemp.toString()
                textViewRightTemp.text = nextTemp.toString()
            }
            "SET_LEFT_TEMP" -> {
                val leftTemp: Double = intent.getDoubleExtra("left_temp", 0.0)
                Log.d(TAG, "SET_LEFT_TEMP $leftTemp")
                textViewLeftTemp.text = leftTemp.toString()
                textViewRightTemp.text = leftTemp.toString()
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        Log.d(TAG, "Starting the foreground service")
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID )
            .setContentTitle("Service Running")
            .setContentText("This is my service running in foreground")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (overlayView != null) {
            windowManager?.removeView(overlayView)
            overlayView = null
        }
    }
}
