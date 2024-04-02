package com.example.myserviceapp

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.compose.setContent
import android.os.Bundle
import androidx.activity.ComponentActivity
import android.provider.Settings
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.example.myserviceapp.ui.theme.MyServiceAppTheme
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest


class MainActivity : ComponentActivity() {
    private lateinit var intentService: Intent

    // 音声録音パーミッションのリクエストに使用するActivityResultLauncherを定義
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                checkAndStartService()
            } else {
                Toast.makeText(this, "Recording permission is necessary to start the service", Toast.LENGTH_LONG).show()
            }
        }

    // オーバーレイパーミッションの確認とサービスの起動を行う
    private fun checkAndStartService() {
        if (Settings.canDrawOverlays(this)) {
            startService(intentService)
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

        setContent {
            MyServiceAppTheme {
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(onClick = {
                        // RECORD_AUDIO パーミッションの確認
                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            checkAndStartService()
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
                    }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Stop Service")
                    }
                }
            }
        }
    }
}

