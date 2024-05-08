package com.example.myserviceapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.json.JSONObject


class MainActivity : ComponentActivity() {
    private var TAG = MyService::class.java.simpleName
    private lateinit var intentService: Intent
    private lateinit var intentCarInfoService: Intent
    private var ipAddress = "http://127.0.0.1:8080" // IPアドレスのデフォルト値
    //private var ipAddress = "http://192.168.1.100:8080" // IPアドレスのデフォルト値
    private lateinit var spinner : Spinner

    // 音声録音パーミッションのリクエストに使用するActivityResultLauncherを定義
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // パーミッションが許可された場合、サービスを開始
                checkAndStartService(ipAddress)
            } else {
                Toast.makeText(this, "Recording permission is necessary to start the service", Toast.LENGTH_LONG).show()
            }
        }
    /**
     * オーバーレイパーミッション設定Intentがサポートされているかどうかを確認します。
     * @param context アプリケーションのコンテキスト
     * @return サポートされている場合はtrue、そうでない場合はfalse
     */
    private fun isOverlayPermissionIntentSupported(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        // Intentを処理できるアクティビティがあるかどうかを確認
        val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val result:Boolean = (resolveInfo != null)
        Log.d(TAG, "isOverlayPermissionIntentSupported : $result")
        return result
    }
    // オーバーレイパーミッションの確認とサービスの起動を行う
    private fun checkAndStartService(ipAddress: String) {
        if (Settings.canDrawOverlays(this)) {
            intentService.putExtra("ip_address", ipAddress) // IPアドレスをIntentに追加
            // 車両情報を設定
            val carInfo = JSONObject()
            val vehicleSpeed = findViewById<EditText>(R.id.vehicle_speed).text.toString()
            val fuelLevel = findViewById<EditText>(R.id.fuel_level).text.toString()
            val language = spinner.selectedItem.toString()
            carInfo.put("vehicle_speed", vehicleSpeed)
            carInfo.put("fuel_level", fuelLevel)
            carInfo.put("language", language)
            Log.d("CAR_INFO Client", carInfo.toString())
            intentService.putExtra("car_info", carInfo.toString())
            startService(intentService)
            // ダミーの車両情報表示Serviceを起動する
            intentCarInfoService.putExtra("action", "SHOW_OVERLAY")
            startService(intentCarInfoService)
        } else {
            if (isOverlayPermissionIntentSupported(this)) {
                // オーバーレイパーミッション許可画面を開く
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                // In case of Android Automotive OS
                intentService.putExtra("ip_address", ipAddress) // IPアドレスをIntentに追加
                startService(intentService)
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // XMLレイアウトを使用

        // サービス用のIntentを作成
        intentService = Intent(application, MyService::class.java)
        intentCarInfoService = Intent(application, CarInfoService::class.java)

        // IPアドレスのEditTextから値を取得
        val ipAddressEditText = findViewById<EditText>(R.id.ip_address)

        // サービス開始ボタンの設定
        findViewById<Button>(R.id.start_service_button).setOnClickListener {
            val ipAddress = ipAddressEditText.text.toString()

            // RECORD_AUDIO パーミッションの確認
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                checkAndStartService(ipAddress)
            } else {
                // パーミッションがない場合、リクエスト
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        // サービス停止ボタンの設定
        findViewById<Button>(R.id.stop_service_button).setOnClickListener {
            stopService(intentService)
            stopService(intentCarInfoService)
        }

        // 車両情報の明示的なアップデートボタンの設定
        findViewById<Button>(R.id.apply_car_info_button).setOnClickListener {
            val carInfo = JSONObject()
            // EditTextから入力された値を取得
            val vehicleSpeed = findViewById<EditText>(R.id.vehicle_speed).text.toString()
            val fuelLevel = findViewById<EditText>(R.id.fuel_level).text.toString()
            val language = spinner.selectedItem.toString()

            // 入力値をJSONObjectに追加
            carInfo.put("vehicle_speed", vehicleSpeed)
            carInfo.put("fuel_level", fuelLevel)
            carInfo.put("language", language)
            Log.d("CAR_INFO Client", carInfo.toString())
            intentService.putExtra("action", "CAR_INFO")
            intentService.putExtra("car_info", carInfo.toString())
            startService(intentService)
        }

        spinner = findViewById<Spinner>(R.id.language)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                val selectedItem = parent.getItemAtPosition(position).toString()
                Log.d("MainActivity", "Selected item: $selectedItem")
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // 選択されていない場合の処理
            }
        }
    }
}
