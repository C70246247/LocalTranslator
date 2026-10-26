package com.buildsession.localtranslator.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * MediaProjection 共享管理器，用于协调 STT 音频捕获和 OCR 屏幕捕获。
 * 支持多个业务共享同一个 MediaProjection 实例，并根据引用计数管理生命周期。
 */
object MediaProjectionShareManager {
    private const val TAG = "MediaProjectionManager"
    
    private var resultData: Intent? = null
    private var mediaProjection: MediaProjection? = null
    private val activeUsers = mutableSetOf<String>()

    /**
     * 设置授权成功的 Intent 数据
     */
    fun setResultData(intent: Intent) {
        Log.d(TAG, "setResultData: data updated")
        resultData = intent
    }

    /**
     * 检查是否已经拥有授权数据
     */
    fun hasResultData(): Boolean = resultData != null

    /**
     * 获取当前的授权数据
     */
    fun getResultData(): Intent? = resultData

    /**
     * 清除授权数据，通常在授权失效时调用
     */
    fun clearResultData() {
        Log.d(TAG, "clearResultData")
        resultData = null
    }

    /**
     * 获取或创建 MediaProjection 实例
     * @param context 上下文
     * @param userTag 业务标识（如 "STT", "OCR"）
     */
    @Synchronized
    fun acquireMediaProjection(context: Context, userTag: String): MediaProjection? {
        Log.d(TAG, "acquireMediaProjection by $userTag, current users: $activeUsers")
        activeUsers.add(userTag)
        
        if (mediaProjection != null) {
            Log.d(TAG, "Returning existing MediaProjection for $userTag")
            return mediaProjection
        }

        val data = resultData ?: run {
            Log.e(TAG, "No resultData available for $userTag")
            activeUsers.remove(userTag)
            return null
        }

        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        return try {
            val projection = projectionManager.getMediaProjection(Activity.RESULT_OK, data)
            if (projection == null) {
                Log.e(TAG, "getMediaProjection returned null for $userTag")
                activeUsers.remove(userTag)
                return null
            }

            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection.Callback: onStop")
                    synchronized(this@MediaProjectionShareManager) {
                        mediaProjection = null
                        activeUsers.clear()
                    }
                }
            }, Handler(Looper.getMainLooper()))
            
            mediaProjection = projection
            Log.d(TAG, "Created new MediaProjection for $userTag")
            projection
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get MediaProjection for $userTag", e)
            activeUsers.remove(userTag)
            null
        }
    }

    /**
     * 释放 MediaProjection 实例
     * @param userTag 业务标识
     * @param forceStop 是否强制停止（通常 OCR 在完成单次任务后需要根据情况决定是否停止）
     */
    @Synchronized
    fun releaseMediaProjection(userTag: String, forceStop: Boolean = false) {
        Log.d(TAG, "releaseMediaProjection by $userTag, forceStop: $forceStop, current users: $activeUsers")
        activeUsers.remove(userTag)
        
        if (forceStop || activeUsers.isEmpty()) {
            if (activeUsers.isNotEmpty()) {
                Log.d(TAG, "Force stopping MediaProjection while other users still active: $activeUsers")
            }
            stopInternal()
        } else {
            Log.d(TAG, "MediaProjection kept alive because other users are active: $activeUsers")
        }
    }

    private fun stopInternal() {
        Log.d(TAG, "stopInternal: stopping MediaProjection")
        mediaProjection?.stop()
        mediaProjection = null
        // 通常停止后，原有的 resultData 也会失效
        resultData = null
    }

    /**
     * 检查是否有业务正在使用 MediaProjection
     */
    @Synchronized
    fun isAnyUserActive(): Boolean = activeUsers.isNotEmpty()

    /**
     * 检查特定业务是否正在使用
     */
    @Synchronized
    fun isUserActive(userTag: String): Boolean = activeUsers.contains(userTag)
}
