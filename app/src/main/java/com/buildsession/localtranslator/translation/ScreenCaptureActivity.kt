package com.buildsession.localtranslator.translation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.buildsession.localtranslator.util.MediaProjectionShareManager

class ScreenCaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)
        
        val requester = intent.getStringExtra("requester")
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        
        val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                // 更新全局共享管理器
                MediaProjectionShareManager.setResultData(result.data!!)
                
                // 发送广播或回调给 Service (保留兼容性)
                val intent = Intent("com.buildsession.localtranslator.SCREEN_CAPTURE_PERMISSION_GRANTED").apply {
                    putExtra("result_data", result.data)
                    putExtra("requester", requester)
                    setPackage(packageName)
                }
                sendBroadcast(intent)
            }
            finish()
            overridePendingTransition(0, 0)
        }
        
        launcher.launch(captureIntent)
    }
}
