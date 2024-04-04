package com.example.myserviceapp


import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import kotlin.math.abs
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

data class TextResponse(
    @SerializedName("received_text") val receivedText: String,
    @SerializedName("response_text") val responseText: String,
    @SerializedName("intent") val action: IntentAction
)

data class IntentAction(
    @SerializedName("navigation") val navigation: CarNavigation
)

data class CarNavigation(
    @SerializedName("navi_application") val naviName: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double
)

interface ApiService {
    @GET("input")
    fun getTextResponse(@Query("text") text: String): Call<TextResponse>
}

// 通知チャネルのID
private const val CHANNEL_ID = "my_channel_id"

class MyService : Service(), TextToSpeech.OnInitListener {
    private val TAG = MyService::class.java.simpleName
    private var isRunning = false
    private var overlayView: ImageView? = null // オーバーレイとして表示するImageView
    private var loadingOverlayView: ImageView? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isConversationMode = false
    var isAIProcessingConversation = false // 会話処理中かどうか
    private lateinit var audioManager: AudioManager
    private var apiBaseUrl = "http://192.168.1.100:8080/"

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

        // 音声認識中の音声を消去するためのトリック用
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        initializeSpeechRecognizer()
        initializeTextToSpeech()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Log.d(TAG, "TextToSpeech.SUCCESS")
            // TextToSpeechの初期化が成功した場合、言語を設定
            textToSpeech?.language = Locale.JAPANESE
            val textToSpeak = "サービスを開始します"
            speak(textToSpeak)
        } else {
            Log.d(TAG, "ERROR: TextToSpeech.SUCCESS")
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            Log.d(TAG, "Service is starting...")
            // サービスの初期化や前景処理の開始
            startForegroundService()
        } else {
            Log.d(TAG, "Service is already running.")
            // 既にサービスが起動している場合の追加の処理
            when(intent?.action) {
                "ACTION_SHOW_OVERLAY" -> showOverlayImage()  // オーバーレイを表示
                "ACTION_HIDE_OVERLAY" -> hideOverlayImage()  // オーバーレイを非表示 // オーバーレイの表示状態を切り替え
            }
        }
        apiBaseUrl = intent?.getStringExtra("ip_address") ?: apiBaseUrl
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
            setImageResource(R.drawable.robot) // 画像リソースの設定
        }

        val layoutParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.CENTER // 画面中央に配置
            //width = WindowManager.LayoutParams.WRAP_CONTENT
            //height = WindowManager.LayoutParams.WRAP_CONTENT
            width = 512
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

    private fun showLoadingOverlayImage() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        loadingOverlayView = ImageView(this).apply {
            setImageResource(R.drawable.loading) // 画像リソースの設定
        }

        val layoutParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.CENTER // 画面中央に配置
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            //width = 512
        }

        windowManager.addView(loadingOverlayView, layoutParams) // WindowManagerにオーバーレイとして追加
    }

    private fun hideLoadingOverlayImage() {
        loadingOverlayView?.let {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(it) // オーバーレイとして追加したビューを削除
            loadingOverlayView = null // ビューの参照をクリア
        }
    }

    private fun matchStartKeyWord(input: String): Boolean {
        // 正規表現用のパターン文字列をリストで管理
        // ハロー アクセス で呼び出したいが間違って認識した場合も許容する
        // なんどかテストして間違って認識された文字列をリストに含めておく
        val patterns = listOf(
            "(\\\\s|^)ハロー\\s*アクセス(\\\\s|\$)",
            "(\\\\s|^)ハロー\\s*学生(\\\\s|\$)",
            "(\\\\s|^)ハロー\\s*ワークです(\\\\s|\$)",
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

    private fun runOnMainThread(action: () -> Unit) {
        Handler(Looper.getMainLooper()).post(action)
    }
    private fun initializeTextToSpeech() {
        // TextToSpeechの初期化
        textToSpeech = TextToSpeech(this, this).apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    // TTSの読み上げが開始されたときの処理
                    Log.d(TAG, "TTS onStart")
                    runOnMainThread{stopListening()}// 音声認識を一時停止
                }

                override fun onDone(utteranceId: String?) {
                    // TTSの読み上げが終了したときの処理
                    Log.d(TAG, "TTS onDone")
                    finishAIConversationProcessing()
                    runOnMainThread{startListening()} // 音声認識を再開
                }

                override fun onError(utteranceId: String?) {
                    // エラーが発生したときの処理
                    Log.d(TAG, "TTS onError")
                }
            })
        }
    }

    private fun speak(text: String) {
        Log.d(TAG, "speak: $textToSpeech, $text")
        val utteranceId = hashCode().toString() + System.currentTimeMillis()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }
    private fun stopSpeaking() {
        if (textToSpeech?.isSpeaking == true) {
            textToSpeech?.stop()
        }
    }

    private fun initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListenerAdapter(this@MyService) {
                override fun onResults(results: Bundle) {
                    Log.d(TAG, "onResults")
                    unmuteBeepForRecognizer()

                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.let {
                        if (it.isNotEmpty()) {
                            val text = it[0]
                            Log.d(TAG, "Recognized text: $text")

                            if (!isConversationMode && matchStartKeyWord(text)) {
                                Log.d(TAG, "enter AI conversation mode")
                                setConversationMode(true)
                                // エコーキャンセル対応したら削除
                                startListening()
                            } else if (isConversationMode) {
                                Log.d(TAG, "proceeding AI conversation mode")

                                startAIConversationProcessing(text)
                            } else {
                                // AIと会話を始める前
                                Log.d(TAG, "waiting a wake word")
                                // エコーキャンセル対応したら削除
                                startListening()
                            }
                        }
                    }
                    // エコーキャンセル対応するまでは一問一答方式にする
                    // AIからの回答を入力として受け取らないようにする
                    // 別対応としてTTS実行中は入力内容を無視する方法があるそのほうが良いかも
                    // エコーキャンセル対応したら下記を有効にする
                    // startListening()
                }

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

    private fun muteBeepForRecognizer() {
        audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
    }
    private fun unmuteBeepForRecognizer() {
        audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
    }
    private fun startListening() {
        Log.d(TAG, "StartListening!!!")
        muteBeepForRecognizer()

        val lang = "ja-JP"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this@MyService.packageName)
            // putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(intent)
    }
    private fun stopListening() {
        speechRecognizer?.stopListening()
    }
    fun setConversationMode(active: Boolean) {
        if(isConversationMode == active){
            Log.d(TAG, "Conversation mode is same as: $isConversationMode")
            return
        }
        isConversationMode = active
        Log.d(TAG, "Conversation mode set to: $isConversationMode")
        if (isConversationMode) {
            speak("こんにちは！何をお手伝いしましょうか？")
            showOverlayImage()
        } else {
            speak("今日はありがとうございました！ご利用をお待ちしております。")
            hideOverlayImage()
        }
    }

    private fun startNavigation(latitude: Double, longitude: Double) {
        Log.e(TAG, "startNavigation $latitude, $longitude")
        // Google MapsへのURIを作成
        val gmmIntentUri = Uri.parse("google.navigation:q=$latitude,$longitude")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")

        // Google Mapsアプリがインストールされているか確認
        if (mapIntent.resolveActivity(packageManager) != null) {
            mapIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK // サービスからアクティビティを開始するためのフラグ
            startActivity(mapIntent)
            speak("カーナビを起動して目的地を設定しました")
        } else {
            // Google Mapsがインストールされていない場合の処理（オプショナル）
            speak("カーナビの起動に失敗しました")
        }
    }

    fun startAIConversationProcessing(requestText: String) {
        isAIProcessingConversation = true
        showLoadingOverlayImage()
        Log.d(TAG, "Start AI Conversation")
        // 会話処理の開始
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Connect to: $apiBaseUrl")
                val retrofit = Retrofit.Builder()
                    .baseUrl(apiBaseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                val service = retrofit.create(ApiService::class.java)
                val response = service.getTextResponse(requestText).execute()

                if (response.isSuccessful) {
                    val responseData = response.body()
                    Log.d(TAG, "Received data: $responseData")

                    // Speech
                    if (!responseData?.responseText.isNullOrEmpty()) {
                        // テキストが空でない場合、読み上げを行う
                        speak(responseData?.responseText ?: "")
                    }

                    // Intent CarNavigation
                    responseData?.action?.navigation?.let {
                        startNavigation(it.latitude, it.longitude)
                    }
                } else {
                    Log.d(TAG, "Failed to receive data")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during network call", e)
            } finally {
                Log.d(TAG, "Finish AI Conversation")
                hideLoadingOverlayImage()
                // 音声再生する場合は音声再生終了時に呼び出す
                //finishAIConversationProcessing()
            }
        }
    }

    private fun finishAIConversationProcessing() {
        isAIProcessingConversation = false
        // 会話処理の終了
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.d(TAG, "Service onDestroy")
        stopForeground(STOP_FOREGROUND_REMOVE)

        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()

        textToSpeech?.stop()
        textToSpeech?.shutdown()

        overlayView?.let {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(it)
            overlayView = null
        }

        loadingOverlayView?.let {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(it)
            loadingOverlayView = null
        }
    }
}

abstract class RecognitionListenerAdapter(private val service: MyService) : RecognitionListener {
    private val TAG = "RecognitionListener"
    private val silenceThreshold = 12.0f // RMS dBの閾値。この値以下を無音とみなす
    private val silenceTimeout = 10000L // 無音状態がこの時間（ミリ秒）続いたらタイムアウトとする
    private var isCurrentlySilent = false // 現在無音状態かどうか

    private val silenceHandler = Handler(Looper.getMainLooper())
    private val checkSilenceRunnable: Runnable = Runnable {
        // AIとの会話が終了して silenceTimeout が経過したら会話モードを終了する
        if (!service.isAIProcessingConversation) {
            service.setConversationMode(false)
            Log.d(TAG, "Exit conversation mode with AI because of timeout ($silenceTimeout)")
        }
        isCurrentlySilent = false // 無音状態をリセット
    }

    override fun onRmsChanged(rmsdB: Float) {
        if (service.isAIProcessingConversation){
            if (isCurrentlySilent) {
                silenceHandler.removeCallbacks(checkSilenceRunnable)
                isCurrentlySilent = false
            }
            return
        }

        if (abs(rmsdB) < silenceThreshold) {
            if (!isCurrentlySilent) {
                // 無音状態の開始を検出
                Log.d(TAG, "Detects the onset of a silence condition. (rmsdB:$rmsdB)")
                isCurrentlySilent = true
                silenceHandler.postDelayed(checkSilenceRunnable, silenceTimeout)
            }
            // 以降は無音状態が続く限り、何もしない（タイマーの再スケジュールは行わない）
        } else {
            if (isCurrentlySilent) {
                // 音声が検出され、無音状態が終了した
                Log.d(TAG, "Audio detected and silence ended. (rmsdB:$rmsdB)")
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
        if (isCurrentlySilent) {
            silenceHandler.removeCallbacks(checkSilenceRunnable)
            isCurrentlySilent = false
        }
        return
    }


    override fun onBufferReceived(buffer: ByteArray?) {
        Log.d(TAG, "onBufferReceived: 音声データのバッファを受け取りました。")
    }

    override fun onEndOfSpeech() {
        Log.d(TAG, "onEndOfSpeech: ユーザーの話が終了しました。")
        if (isCurrentlySilent) {
            silenceHandler.removeCallbacks(checkSilenceRunnable)
            isCurrentlySilent = false
        }
        return
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