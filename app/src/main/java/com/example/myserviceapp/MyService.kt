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
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Callback
import retrofit2.Response
import kotlin.math.abs
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import android.view.MotionEvent
import android.provider.Settings
import android.car.Car
import android.car.VehiclePropertyIds.*
import android.car.hardware.property.CarPropertyManager
import android.icu.text.SimpleDateFormat
import org.json.JSONException
import org.json.JSONObject
import java.util.Date

data class TextResponse(
    @SerializedName("received_text") val receivedText: String,
    @SerializedName("response_text") val responseText: String,
    @SerializedName("intent") val action: IntentAction,
)

data class IntentAction(
    @SerializedName("navigation") val navigation: CarNavigation,
    @SerializedName("aircontrol") val aircontrol: AirControl,
    @SerializedName("aircontrol_delta") val aircontrolDelta: AirControlDelta,
)

data class CarNavigation(
    @SerializedName("navi_application") val naviName: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double
)

data class AirControl(
    @SerializedName("temperature") val temperature: Double,
)

data class AirControlDelta(
    @SerializedName("temperature_delta") val temperatureDelta: Double,
)

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
    private lateinit var intentCarInfoService: Intent
    private lateinit var car: Car
    private lateinit var carPropertyManager: CarPropertyManager
    private var apiBaseUrl = "http://192.168.1.100:8080/"
    private lateinit var carInfoJson : JSONObject
    private var carLanguage = "ja"

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

        // Connect to the Car service
        if (isAAOS()) {
            val handler = Handler(Looper.getMainLooper())
            car = Car.createCar(this, handler)
            carPropertyManager = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Log.d(TAG, "TextToSpeech.SUCCESS")
            when (carLanguage) {
                "ja" -> {
                    textToSpeech?.language = Locale.JAPANESE
                    val textToSpeak = "Copilotを開始します"
                    speak(textToSpeak)
                }
                "en" -> {
                    textToSpeech?.language = Locale.ENGLISH
                    val textToSpeak = "Starting Copilot"
                    speak(textToSpeak)
                }
                else -> {
                    Log.d(TAG, "Unsupported language: $carLanguage")
                    // 対応していない言語の場合は英語をデフォルトとする
                    textToSpeech?.language = Locale.ENGLISH
                    val textToSpeak = "Starting Copilot"
                    speak(textToSpeak)
                }
            }
        } else {
            Log.e(TAG, "ERROR: TextToSpeech not initialized")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            Log.d(TAG, "Service is starting...")
            if (!isAAOS()) {
                // ダミーのCarInfo
                intentCarInfoService = Intent(application, CarInfoService::class.java)
            }
            // サービスの初期化や前景処理の開始
            startForegroundService()
        } else {
            Log.d(TAG, "Service is already running.")
            // 既にサービスが起動している場合の追加の処理
            when(intent?.getStringExtra("action") ?: "") {
                "ACTION_SHOW_OVERLAY" -> showOverlayImage()  // オーバーレイを表示
                "ACTION_HIDE_OVERLAY" -> hideOverlayImage()  // オーバーレイを非表示 // オーバーレイの表示状態を切り替え
                "SET_CONVERSATION_MODE" -> {
                    Log.d(TAG, "SET_CONVERSATION_MODE")
                    setConversationMode(true)
                    // エコーキャンセル対応したら削除
                    startListening()
                }
                "CAR_INFO" -> {
                    intent?.getStringExtra("car_info")?.let { Log.d("CAR_INFO Server", it) }
                }
            }
        }

        // 起動時も設定を反映する
        apiBaseUrl = intent?.getStringExtra("ip_address") ?: apiBaseUrl
        // carInfoをJSONオブジェクトとして取得
        val carInfoString = intent?.getStringExtra("car_info")
        carInfoString?.let { info ->
            try {
                carInfoJson = JSONObject(info)
                // JSONオブジェクトからデータを取得
                val vehicleSpeed = carInfoJson.getString("vehicle_speed")
                val fuelLevel = carInfoJson.getString("fuel_level")
                carLanguage = carInfoJson.getString("language")

                // ログ出力や他の処理
                Log.d("CAR_INFO Service", "Vehicle Speed: $vehicleSpeed, Fuel Level: $fuelLevel, Language: $carLanguage")
            } catch (e: JSONException) {
                Log.e("CAR_INFO Service", "Failed to parse car info", e)
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

    private fun isAAOS(): Boolean {
        return (!Settings.canDrawOverlays(this))
    }
    private fun showOverlayImage() {
        if (isAAOS()) {
            return
        }

        // Demo code on Android Tablet
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = ImageView(this).apply {
            setImageResource(R.drawable.robot) // 画像リソースの設定
            // タッチリスナーを設定して、タップイベントを検出
            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // performClickを呼び出して、クリックイベントを模倣
                        view.performClick()
                        // タップされたら強制的に音声出力をキャンセルして会話モードをキャンセル
                        Log.d("YourTag", "Overlay image tapped!")
                        // ここにタップ時の処理を記述
                        stopSpeaking()
                        setConversationMode(false)
                        true // イベントが処理されたことを示す
                    }
                    else -> false // その他のタッチイベントに対してはfalseを返して、処理しない
                }
            }
        }

        val layoutParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            // flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.CENTER // 画面中央に配置
            //width = WindowManager.LayoutParams.WRAP_CONTENT
            //height = WindowManager.LayoutParams.WRAP_CONTENT
            width = 512
        }

        windowManager.addView(overlayView, layoutParams) // WindowManagerにオーバーレイとして追加
    }

    private fun hideOverlayImage() {
        if (isAAOS()) {
            return
        }

        // Demo code on Android Tablet
        overlayView?.let {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(it) // オーバーレイとして追加したビューを削除
            overlayView = null // ビューの参照をクリア
        }
    }

    private fun showLoadingOverlayImage() {
        if (isAAOS()) {
            return
        }

        // Demo code on Android Tablet
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
        if (isAAOS()) {
            return
        }

        // Demo code on Android Tablet
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
        val patterns = when(carLanguage) {
            "ja" -> listOf(
                "(\\\\s|^)ハロー\\s*アクセス(\\\\s|\$)",
                "(\\\\s|^)ハロー\\s*学生(\\\\s|\$)",
                "(\\\\s|^)ハロー\\s*ワークです(\\\\s|\$)",
                "(\\\\s|^)ハロー\\s*ワークスです(\\\\s|\$)",
                "(\\\\s|^)ハロー\\s*ワークス(\\\\s|\$)",
                "(\\\\s|^)ハロー\\s*惑星(\\\\s|\$)",
                "(\\\\s|^)原田\\s*学生(\\\\s|\$)",
            )
            "en" -> listOf(
                "(\\\\s|^)hello\\s*access(\\\\s|\$)",
                "(\\\\s|^)hello\\s*office(\\\\s|\$)",
                "(\\\\s|^)hello\\s*Alexis(\\\\s|\$)",
                "(\\\\s|^)how to\\s*access(\\\\s|\$)",
                "(\\\\s|^)how to\\s*look sis(\\\\s|\$)",
                "(\\\\s|^)cuddle\\s*access(\\\\s|\$)",
                "(\\\\s|^)how\\s*access(\\\\s|\$)",
            )
            else -> listOf(
                "Error",
            )
        }

        // 正規表現オブジェクトの生成、英語の場合は大文字小文字を区別しない
        val regexOptions = if (carLanguage == "en") setOf(RegexOption.IGNORE_CASE) else emptySet()
        val combinedPattern = patterns.joinToString("|").toRegex(regexOptions)

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

    private fun speak(text: String, isAdd: Boolean = false) {
        Log.d(TAG, "speak: $textToSpeech, $text")
        val utteranceId = hashCode().toString() + System.currentTimeMillis()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        if (isAdd) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, params, utteranceId)
        } else {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }
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
                    super.onResults(results)
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

        val lang = when (carLanguage) {
            "ja" -> "ja-JP"
            "en" -> "en-US"
            else -> {
                Log.d(TAG, "Unsupported language: $carLanguage")
                "en-US" // デフォルトとして英語を設定
            }
        }

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
            val message = when (carLanguage) {
                "ja" -> "何をお手伝いしましょうか？"
                "en" -> "How can I assist you?"
                else -> "How can I assist you?"
            }
            speak(message)

            if (isAAOS()) {
                Log.d(TAG, "INFO_FUEL_CAPACITY : ${carPropertyManager.getFloatProperty(INFO_FUEL_CAPACITY, 0)}")
                Log.d(TAG, "FUEL_LEVEL : ${carPropertyManager.getFloatProperty(FUEL_LEVEL, 0)}")
                Log.d(TAG, "FUEL_LEVEL_LOW : ${carPropertyManager.getBooleanProperty(FUEL_LEVEL_LOW, 0)}")
                Log.d(TAG, "CURRENT_GEAR : ${carPropertyManager.getIntProperty(CURRENT_GEAR, 0)}")
            }
            showOverlayImage()
        } else {
            val message = when (carLanguage) {
                "ja" -> "またの依頼をお待ちしています。"
                "en" -> "We look forward to receiving another request."
                else -> "We look forward to receiving another request."
            }
            speak(message, isAdd = true)
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
        setConversationMode((false))
    }

    private fun changeTemperature(temperature: Double){
        if (!isAAOS()) {
            intentCarInfoService.putExtra("action", "SET_LEFT_TEMP")
            intentCarInfoService.putExtra("left_temp", temperature)
            startService(intentCarInfoService)
        } else {
            /*
                システムアプリである必要がある
                Signature|Privileged permission "android.car.permission.CONTROL_CAR_CLIMATE" to read and write property.
            carPropertyManager.setFloatProperty(VehiclePropertyIds.HVAC_TEMPERATURE_SET, 0,
                temperature.toFloat()
            )
             */
        }
        val temp = temperature.toString()
        speak("室内温度を $temp 度に設定しました")
        setConversationMode((false))
    }

    private fun changeTemperatureDelta(temperatureDelta: Double){
        if (!isAAOS()) {
            intentCarInfoService.putExtra("action", "SET_LEFT_TEMP_DELTA")
            intentCarInfoService.putExtra("left_temp", temperatureDelta)
            startService(intentCarInfoService)
        }else {
            /*  Signature|Privileged permission "android.car.permission.CONTROL_CAR_CLIMATE" to read and write property.
            val targetTemp:Float = carPropertyManager.getFloatProperty(VehiclePropertyIds.HVAC_TEMPERATURE_CURRENT, 0) + temperatureDelta.toFloat()
            carPropertyManager.setFloatProperty(/* propertyId = */ VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                /* areaId = */ 0,
                /* val = */ targetTemp
            )
             */
        }
        val temp = temperatureDelta.toString()
        if (temperatureDelta >= 0) {
            speak("室内温度を $temp 度あげました")
        }else {
            speak("室内温度を $temp 度さげました")
        }
        setConversationMode((false))
    }

    // Copilot サーバーとの通信を担う
    interface ApiService {
        @POST("input")
        fun sendTextRequest(@Body request: RequestData): Call<TextResponse>
    }
    data class RequestData(val user_input: String, val car_info: JSONObject)

    fun startAIConversationProcessing(requestText: String) {
        isAIProcessingConversation = true
        showLoadingOverlayImage()
        Log.d(TAG, "Start AI Conversation")
        Log.d(TAG, "apiBaseUrl: $apiBaseUrl")
        Log.d(TAG, "requestText: $requestText")
        Log.d(TAG, "carInfoJson: $carInfoJson")

        val retrofit = Retrofit.Builder()
            .baseUrl(apiBaseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        // 現在の日付と時刻をcatInfoJsonに追加
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        carInfoJson.put("today", currentDate)
        carInfoJson.put("current_time", currentTime)
        carInfoJson.put("fuel_level_description", "Fuel level in %, where 75 means 75%.")
        carInfoJson.put("vehicle_speed_description", "Indicates the current speed of the vehicle. Unit is km. 60 means 60 km.")

        val service = retrofit.create(ApiService::class.java)
        val requestData = RequestData(requestText, carInfoJson)  // リクエストデータのインスタンスを作成
        val call = service.sendTextRequest(requestData)

        call.enqueue(object : Callback<TextResponse> {
            override fun onResponse(call: Call<TextResponse>, response: Response<TextResponse>) {
                hideLoadingOverlayImage()
                if (response.isSuccessful) {
                    val responseData = response.body()
                    Log.d(TAG, "Received data: $responseData")

                    // 以下、応答データに基づいた処理を行う
                    // Speech
                    if (!responseData?.responseText.isNullOrEmpty()) {
                        // テキストが空でない場合、読み上げを行う
                        speak(responseData?.responseText ?: "")
                    }

                    // Intent CarNavigation
                    responseData?.action?.navigation?.let {
                        startNavigation(it.latitude, it.longitude)
                    }

                    // Temperature Delta
                    responseData?.action?.aircontrolDelta?.let {
                        changeTemperatureDelta(it.temperatureDelta)
                    }
                    // Temperature Absolute Value
                    responseData?.action?.aircontrol?.let {
                        changeTemperature(it.temperature)
                    }
                } else {
                    Log.e(TAG, "Failed to receive data with HTTP code ${response.code()}")
                }
                Log.d(TAG, "Finish AI Conversation")
                hideLoadingOverlayImage()
                // 音声再生する場合は音声再生終了時に呼び出す
                //finishAIConversationProcessing()
            }

            override fun onFailure(call: Call<TextResponse>, t: Throwable) {
                hideLoadingOverlayImage()
                Log.e(TAG, "Error during network call", t)
            }
        })
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

        if (isAAOS()) {
            car.disconnect()
        } else {
            // Demo code on Android Tablet
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
}

abstract class RecognitionListenerAdapter(private val service: MyService) : RecognitionListener {
    private val TAG = "RecognitionListener"
    private val silenceThreshold = 12.0f // RMS dBの閾値。この値以下を無音とみなす
    private val silenceTimeout = 5000L // 無音状態がこの時間（ミリ秒）続いたらタイムアウトとする
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
            Log.d(TAG, "isAIProcessingConversation: $service.isAIProcessingConversation")
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
                // Log.d(TAG, "Audio detected and silence ended. (rmsdB:$rmsdB)")
                silenceHandler.removeCallbacks(checkSilenceRunnable)
                isCurrentlySilent = false
            }
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        //Log.d(TAG, "onReadyForSpeech: 音声認識を開始準備が整いました。")
    }

    override fun onBeginningOfSpeech() {
        //Log.d(TAG, "onBeginningOfSpeech: ユーザーが話し始めました。")
        if (isCurrentlySilent) {
            silenceHandler.removeCallbacks(checkSilenceRunnable)
            isCurrentlySilent = false
        }
        return
    }


    override fun onBufferReceived(buffer: ByteArray?) {
        //Log.d(TAG, "onBufferReceived: 音声データのバッファを受け取りました。")
    }

    override fun onEndOfSpeech() {
        //Log.d(TAG, "onEndOfSpeech: ユーザーの話が終了しました。")
        if (isCurrentlySilent) {
            silenceHandler.removeCallbacks(checkSilenceRunnable)
            isCurrentlySilent = false
        }
        return
    }

    override fun onError(error: Int) {
        //Log.d(TAG, "onError: 音声認識エラーが発生しました。エラーコード: $error")
    }

    override fun onResults(results: Bundle) {
        //Log.d(TAG, "onResults: 最終的な認識結果が得られました。")
        if (isCurrentlySilent) {
            silenceHandler.removeCallbacks(checkSilenceRunnable)
            isCurrentlySilent = false
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        //Log.d(TAG, "onPartialResults: 途中結果が得られました。")
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
        //Log.d(TAG, "onEvent: イベントが発生しました。イベントタイプ: $eventType")
    }
}