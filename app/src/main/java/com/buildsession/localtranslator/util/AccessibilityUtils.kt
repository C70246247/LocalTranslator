package com.buildsession.localtranslator.util

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import com.buildsession.localtranslator.FunctionEnhanceAccessibilityServices
import rikka.shizuku.Shizuku

object AccessibilityUtils {

    fun enableAccessibilityService(context: Context) {
        try {
            val serviceName = ComponentName(context, FunctionEnhanceAccessibilityServices::class.java).flattenToString()
            val contentResolver = context.contentResolver
            
            // 1. 获取当前已启用的服务列表
            val enabledServices = AndroidSettings.Secure.getString(contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            
            // 2. 检查自身服务是否已在列表中
            val isAlreadyEnabled = enabledServices.split(':').any { it.equals(serviceName, ignoreCase = true) }
            
            if (!isAlreadyEnabled) {
                // 3. 追加自身服务，不影响其他已开启的服务
                val newServices = if (enabledServices.isEmpty()) serviceName else "$enabledServices:$serviceName"
                
                // 4. 尝试写入设置 (需要 WRITE_SECURE_SETTINGS 权限)
                val success = AndroidSettings.Secure.putString(contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newServices)
                if (success) {
                    AndroidSettings.Secure.putInt(contentResolver, AndroidSettings.Secure.ACCESSIBILITY_ENABLED, 1)
                    Toast.makeText(context, "无障碍服务已自动启用", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "自动启用失败，请手动开启", Toast.LENGTH_SHORT).show()
                }
            } else {
                // 如果已在列表中，确保开关是打开的
                AndroidSettings.Secure.putInt(contentResolver, AndroidSettings.Secure.ACCESSIBILITY_ENABLED, 1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun grantPermissionWithShizuku(context: Context, permission: String = "android.permission.WRITE_SECURE_SETTINGS", onResult: (Boolean, String?) -> Unit) {
        Thread {
            try {
                val command = if (permission == "PROJECT_MEDIA") {
                    arrayOf("appops", "set", context.packageName, "PROJECT_MEDIA", "allow")
                } else {
                    arrayOf("pm", "grant", context.packageName, permission)
                }
                val clazz = Class.forName("rikka.shizuku.Shizuku")
                val method = clazz.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                method.isAccessible = true

                val remoteProcess = method.invoke(null, command, null, null) as rikka.shizuku.ShizukuRemoteProcess
                val exitCode = remoteProcess.waitFor()
                
                Handler(Looper.getMainLooper()).post {
                    if (exitCode == 0) {
                        onResult(true, null)
                    } else {
                        val error = remoteProcess.errorStream.bufferedReader().use { it.readText() }
                        onResult(false, error)
                    }
                }
                remoteProcess.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    onResult(false, e.message)
                }
            }
        }.start()
    }

    fun grantPermissionWithRoot(context: Context, permission: String = "android.permission.WRITE_SECURE_SETTINGS", onResult: (Boolean, String?) -> Unit) {
        Thread {
            try {
                val process = Runtime.getRuntime().exec("su")
                val os = process.outputStream
                val command = if (permission == "PROJECT_MEDIA") {
                    "appops set ${context.packageName} PROJECT_MEDIA allow\n"
                } else {
                    "pm grant ${context.packageName} $permission\n"
                }
                os.write(command.toByteArray())
                os.write("exit\n".toByteArray())
                os.flush()
                os.close()

                val exitCode = process.waitFor()
                Handler(Looper.getMainLooper()).post {
                    if (exitCode == 0) {
                        onResult(true, null)
                    } else {
                        val error = process.errorStream.bufferedReader().use { it.readText() }
                        onResult(false, error)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    onResult(false, e.message)
                }
            }
        }.start()
    }
}
