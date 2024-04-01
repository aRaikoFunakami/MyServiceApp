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


class MainActivity : ComponentActivity() {
    private lateinit var intentService: Intent

    // ActivityResultLauncherを初期化
    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (Settings.canDrawOverlays(this)) {
                startService(intentService)
            } else {
                Toast.makeText(application, "Please get overlay permission if you want to start the service", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intentService = Intent(application, MyService::class.java)
        setContent {
            MyServiceAppTheme {
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(onClick = {
                        if (Settings.canDrawOverlays(application)) {
                            // AndroidManifest.xml
                            // <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
                            startService(intentService)
                        } else {
                            launchOverlayPermissionScreen()
                        }
                    }) {
                        Text("Start Service")
                    }

                    Button(onClick = {
                        // オーバーレイの表示・非表示を切り替え
                        // startServiceを呼び出すのでサービスが起動されていない場合はサービスを自動的に起動する
                        startService(intentService.apply {
                            action = "ACTION_TOGGLE_OVERLAY" // オーバーレイの表示状態を切り替えるアクション
                        })
                    }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Toggle Overlay Visibility")
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

    // オーバーレイ許可画面を開く関数
    private fun launchOverlayPermissionScreen() {
        // AndroidManifest.xml
        // <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }
}

