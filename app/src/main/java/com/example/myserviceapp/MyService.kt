package com.example.myserviceapp


import android.app.Service
import android.app.NotificationManager
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Intent
import android.os.IBinder
import android.content.Context
import androidx.core.app.NotificationCompat
import android.util.Log

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView

// 通知チャネルのID
private const val CHANNEL_ID = "my_channel_id"

class MyService : Service() {
    private val TAG = MyService::class.java.simpleName
    private var isRunning = false
    private var overlayView: ImageView? = null // オーバーレイとして表示するImageView


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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            Log.d(TAG, "Service is starting...")
            // サービスの初期化や前景処理の開始
            startForegroundService()
            showOverlayImage() // オーバーレイ表示のセットアップを呼び出し
        } else {
            Log.d(TAG, "Service is already running.")
            // 既にサービスが起動している場合の追加の処理
            val action = intent?.action
            when(action) {
                "ACTION_SHOW_OVERLAY" -> showOverlayImage()  // オーバーレイを表示
                "ACTION_HIDE_OVERLAY" -> hideOverlayImage()  // オーバーレイを非表示
                "ACTION_TOGGLE_OVERLAY" -> toggleOverlayVisibility()  // オーバーレイの表示状態を切り替え
            }
        }
        return START_STICKY
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

    private fun showOverlayImage() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = ImageView(this).apply {
            setImageResource(R.drawable.animal_chara_radio_penguin) // 画像リソースの設定
        }

        val layoutParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.CENTER // 画面中央に配置
            //width = WindowManager.LayoutParams.WRAP_CONTENT
            //height = WindowManager.LayoutParams.WRAP_CONTENT
            width = 512
            height = 512
        }

        windowManager.addView(overlayView, layoutParams) // WindowManagerにオーバーレイとして追加
    }

    private fun hideOverlayImage() {
        overlayView?.let {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(it) // オーバーレイとして追加したビューを削除
            overlayView = null // ビューの参照をクリア
        }
    }

    private fun toggleOverlayVisibility() {
        if (overlayView == null) {
            showOverlayImage() // オーバーレイを表示
        } else {
            hideOverlayImage() // オーバーレイを非表示
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.d(TAG, "Service onDestroy")
        stopForeground(true)
        overlayView?.let {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(it) // オーバーレイとして追加したビューを削除
            overlayView = null
        }
    }
}
