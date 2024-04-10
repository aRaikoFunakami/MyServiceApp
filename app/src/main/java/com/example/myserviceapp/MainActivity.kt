package com.example.myserviceapp

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.myserviceapp.ui.theme.MyServiceAppTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.net.Uri
import androidx.core.content.ContextCompat
import android.content.Context
import android.util.Log

class MainActivity : ComponentActivity() {
    private var TAG = MyService::class.java.simpleName
    private lateinit var intentService: Intent
    private lateinit var intentCarInfoService: Intent
    private var ipAddress = "http://127.0.0.1:8080" // IPアドレスのデフォルト値
    //private var ipAddress = "http://192.168.1.100:8080" // IPアドレスのデフォルト値

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
            startService(intentService)
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
                // In case of Android Automotive OS (AAOS)
                intentService.putExtra("ip_address", ipAddress) // IPアドレスをIntentに追加
                startService(intentService)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intentService = Intent(application, MyService::class.java)
        intentCarInfoService = Intent(application, CarInfoService::class.java)

        setContent {
            val ipState = remember { mutableStateOf(ipAddress) }

            MyServiceAppTheme {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = ipState.value,
                        onValueChange = { ipState.value = it },
                        label = { Text("IP Address") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), // UrlからUriに変更
                        modifier = Modifier.padding(bottom = 16.dp) // ボタンとの間隔を広げる
                    )
                    Button(onClick = {
                        // RECORD_AUDIO パーミッションの確認
                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            checkAndStartService(ipAddress)
                        } else {
                            // パーミッションがない場合、リクエスト
                            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }) {
                        Text("Start Service")
                    }

                    Button(onClick = {
                        // サービスを停止
                        stopService(intentService)
                        stopService(intentCarInfoService)
                    }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Stop Service")
                    }
                }
            }
        }
    }
}
