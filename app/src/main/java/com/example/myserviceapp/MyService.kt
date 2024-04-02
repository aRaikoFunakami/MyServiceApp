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
import android.os.Handler
import android.os.Looper

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView

import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener


// 通知チャネルのID
private const val CHANNEL_ID = "my_channel_id"

class MyService : Service() {
    private val TAG = MyService::class.java.simpleName
    private var isRunning = false
    private var overlayView: ImageView? = null // オーバーレイとして表示するImageView
    private var speechRecognizer: SpeechRecognizer? = null
    private var isConversationMode = false
    var isAIProcessingConversation = false // 会話処理中かどうか

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

        initializeSpeechRecognizer()
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

    private fun matchStartKeyWord(input: String): Boolean {
        // 正規表現用のパターン文字列をリストで管理
        // ハロー アクセス で呼び出したいが間違って認識した場合も許容する
        // なんどかテストして間違って認識された文字列をリストに含めておく
        val patterns = listOf(
            "(\\\\s|^)ハロー\\s*アクセス(\\\\s|\$)",
            "(\\\\s|^)ハロー\\s*学生(\\\\s|\$)",
            "(\\\\s|^)ハロー\\s*ワークスです(\\\\s|\$)",
            "(\\\\s|^)ハロー\\s*ワークス(\\\\s|\$)",
            "(\\\\s|^)ハロー\\s*惑星(\\\\s|\$)",
            "(\\\\s|^)原田\\s*学生(\\\\s|\$)",
        )

        // パターンリストを一つの正規表現に結合
        val combinedPattern = patterns.joinToString("|").toRegex()

        // いずれかのパターンにマッチした場合はtrue、そうでない場合はfalseを返す
        return combinedPattern.containsMatchIn(input)
    }
    private fun initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListenerAdapter(this@MyService) {
                override fun onResults(results: Bundle) {
                    Log.d(TAG, "onResults")
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.let {
                        if (it.isNotEmpty()) {
                            val text = it[0]
                            Log.d(TAG, "Recognized text: $text")
                            if (matchStartKeyWord(text)) {
                                setConversationMode(true)
                                Log.d(TAG, "isConversationMode mode: $isConversationMode")
                            }
                        }
                    }
                    startListening()
                }
                /*
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.let { results ->
                        Log.d(TAG, "onPartialResults: 途中結果が得られました。")
                        val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.let {
                            if (it.isNotEmpty()) {
                                val text = it[0]
                                Log.d(TAG, "Recognized text: $text")
                                if (matchStartKeyWord(text)) {
                                    isConversationMode = true
                                    Log.d(TAG, "isConversationMode mode: $isConversationMode")
                                }
                            }
                        }
                    }
                }
                */
                override fun onError(error: Int) {
                    Log.d(TAG, "onError: 音声認識エラーが発生しました。エラーコード: $error")
                    if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                        // 音声入力が認識されなかった場合の処理
                        Log.d(TAG, "音声が認識されませんでした。リスニングを再開します。")
                        startListening()
                    }
                }
            })
        }
        startListening()
    }
    private fun startListening() {
        Log.d(TAG, "StartListening!!!")
        val lang = "ja-JP"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this@MyService.packageName)
            // putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(intent)
    }

    fun setConversationMode(active: Boolean) {
        isConversationMode = active
        Log.d(TAG, "Conversation mode set to: $isConversationMode")
    }
    fun startConversationProcessing() {
        isAIProcessingConversation = true
        // 会話処理の開始
    }

    fun finishConversationProcessing() {
        isAIProcessingConversation = false
        // 会話処理の終了
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.d(TAG, "Service onDestroy")
        stopForeground(true)

        speechRecognizer?.stopListening() // SpeechRecognizerのリスニングを停止
        speechRecognizer?.destroy() // SpeechRecognizerのリソースを解放
        overlayView?.let {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(it) // オーバーレイとして追加したビューを削除
            overlayView = null
        }
    }
}

abstract class RecognitionListenerAdapter(private val service: MyService) : RecognitionListener {
    private val TAG = "RecognitionListener"
    private val silenceThreshold = 4.0f // RMS dBの閾値。この値以下を無音とみなす
    private val silenceTimeout = 10000L // 無音状態がこの時間（ミリ秒）続いたらタイムアウトとする
    private var isCurrentlySilent = false // 現在無音状態かどうか

    private val silenceHandler = Handler(Looper.getMainLooper())
    private val checkSilenceRunnable: Runnable = object : Runnable {
        override fun run() {
            // 一定時間無音状態が続いたときの処理
            Log.d("SpeechRecognizer", "無音状態が${silenceTimeout}ミリ秒以上続きました。")

            // 会話のためにAIが処理を進めている場合には無音時間が続いても無視する
            // AI処理が終了してから無音状態が一定時間続いた場合のみ会話モードを終了
            if (!service.isAIProcessingConversation)
            {
                service.setConversationMode(false)
            }
            // 条件に関わらず、一定時間後に再度チェックを行うようスケジュール
            // これにより、checkSilenceRunnableの定期的な実行が保証される
            silenceHandler.postDelayed(this, silenceTimeout)
            isCurrentlySilent = false // 無音状態をリセット
        }
    }

    override fun onRmsChanged(rmsdB: Float) {
        //Log.d(TAG, "onRmsChanged: 受信音声のRMS変化が検出されました。RMS dB: $rmsdB")
        if (Math.abs(rmsdB) < silenceThreshold) {
            if (!isCurrentlySilent) {
                // 無音状態の開始を検出
                isCurrentlySilent = true
                silenceHandler.postDelayed(checkSilenceRunnable, silenceTimeout)
            }
            // 以降は無音状態が続く限り、何もしない（タイマーの再スケジュールは行わない）
        } else {
            if (isCurrentlySilent) {
                // 音声が検出され、無音状態が終了した
                silenceHandler.removeCallbacks(checkSilenceRunnable)
                isCurrentlySilent = false
            }
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "onReadyForSpeech: 音声認識を開始準備が整いました。")
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "onBeginningOfSpeech: ユーザーが話し始めました。")
    }


    override fun onBufferReceived(buffer: ByteArray?) {
        Log.d(TAG, "onBufferReceived: 音声データのバッファを受け取りました。")
    }

    override fun onEndOfSpeech() {
        Log.d(TAG, "onEndOfSpeech: ユーザーの話が終了しました。")
    }

    override fun onError(error: Int) {
        Log.d(TAG, "onError: 音声認識エラーが発生しました。エラーコード: $error")
    }

    override fun onResults(results: Bundle) {
        Log.d(TAG, "onResults: 最終的な認識結果が得られました。")
        // 既存の処理を維持
    }

    override fun onPartialResults(partialResults: Bundle?) {
        Log.d(TAG, "onPartialResults: 途中結果が得られました。")
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
        Log.d(TAG, "onEvent: イベントが発生しました。イベントタイプ: $eventType")
    }
}