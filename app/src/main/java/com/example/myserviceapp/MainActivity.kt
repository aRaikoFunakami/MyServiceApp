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

class MainActivity : ComponentActivity() {
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

    // オーバーレイパーミッションの確認とサービスの起動を行う
    private fun checkAndStartService(ipAddress: String) {
        if (Settings.canDrawOverlays(this)) {
            intentService.putExtra("ip_address", ipAddress) // IPアドレスをIntentに追加
            startService(intentService)
            intentCarInfoService.putExtra("action", "SHOW_OVERLAY")
            startService(intentCarInfoService)
        } else {
            Toast.makeText(this, "Overlay permission is necessary to start the service", Toast.LENGTH_LONG).show()
            // オーバーレイパーミッション許可画面を開く
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
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
