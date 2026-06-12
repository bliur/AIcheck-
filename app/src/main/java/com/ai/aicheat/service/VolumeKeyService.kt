package com.ai.aicheat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.ai.aicheat.MainActivity
import com.ai.aicheat.util.ConfigManager
import com.ai.aicheat.util.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

class VolumeKeyService : LifecycleService() {

    companion object {
        private const val TAG = "VolumeKeyService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "volume_key_service"
        private const val KEY_VOLUME_DOWN = "KEY_VOLUMEDOWN"
        private const val KEY_VOLUME_UP = "KEY_VOLUMEUP"

        private var instance: VolumeKeyService? = null

        fun isRunning(): Boolean = instance != null

        fun start(context: Context) {
            val intent = Intent(context, VolumeKeyService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VolumeKeyService::class.java))
        }
    }

    private var keyMonitorJob: Job? = null
    private var monitorProcess: Process? = null
    private var isProcessing = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastActionTime = 0L
    private val actionDebounce = 800L

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        startService(Intent(this, OverlayService::class.java))
        startKeyMonitor()
        Log.d(TAG, "VolumeKeyService started")
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "后台服务", NotificationManager.IMPORTANCE_LOW).apply {
                description = "保持应用后台运行"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("服务运行中")
            .setContentText("音量键已改为搜题/隐藏答案")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    // ===== 按键监听 =====

    private fun startKeyMonitor() {
        keyMonitorJob?.cancel()
        keyMonitorJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting key monitor...")
                val process = Runtime.getRuntime().exec("su")
                monitorProcess = process
                val os = DataOutputStream(process.outputStream)
                os.writeBytes("getevent -l\n")
                os.flush()
                val reader = BufferedReader(InputStreamReader(process.inputStream))

                while (isActive) {
                    val line = reader.readLine() ?: break

                    if (line.contains(KEY_VOLUME_DOWN) && line.contains("DOWN")) {
                        val now = System.currentTimeMillis()
                        if (now - lastActionTime >= actionDebounce) {
                            lastActionTime = now
                            Log.d(TAG, "Volume DOWN -> search")
                            dismissVolumeBarIfNeeded()
                            onVolumeDownPressed()
                        }
                    }

                    if (line.contains(KEY_VOLUME_UP) && line.contains("DOWN")) {
                        val now = System.currentTimeMillis()
                        if (now - lastActionTime >= actionDebounce) {
                            lastActionTime = now
                            Log.d(TAG, "Volume UP -> hide")
                            dismissVolumeBarIfNeeded()
                            onVolumeUpPressed()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Key monitor error", e)
                delay(3000)
                if (isActive) startKeyMonitor()
            }
        }
    }

    /**
     * 根据配置选择屏蔽方案：
     * - 模拟点击方案：延迟后在用户自定义位置模拟触摸
     * - LSPosed方案：不做额外操作，依赖 Xposed hook (由系统层面生效)
     */
    private fun dismissVolumeBarIfNeeded() {
        if (!ConfigManager.useTapDismiss) return

        try {
            val delayMs = ConfigManager.tapDelayMs
            val xRatio = ConfigManager.tapXRatio
            val yRatio = ConfigManager.tapYRatio

            Thread.sleep(delayMs)

            // 获取屏幕分辨率
            val sizeProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "wm size"))
            val sizeOutput = sizeProcess.inputStream.bufferedReader().readText().trim()
            sizeProcess.waitFor()

            val sizeStr = Regex("""(\d+)x(\d+)""").find(sizeOutput)?.value ?: "1080x2400"
            val parts = sizeStr.split("x")
            val w = parts[0].toInt()
            val h = parts[1].toInt()

            val tapX = (w * xRatio).toInt()
            val tapY = (h * yRatio).toInt()

            RootUtils.executeRootCommandSilent("input tap $tapX $tapY")
            Log.d(TAG, "Volume bar dismissed via tap at ($tapX, $tapY) after ${delayMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "dismissVolumeBar error", e)
        }
    }

    // ===== 搜题逻辑 =====

    private fun onVolumeDownPressed() {
        if (isProcessing) {
            Log.d(TAG, "Already processing, skip")
            return
        }
        isProcessing = true

        lifecycleScope.launch {
            try {
                OverlayService.showText(this@VolumeKeyService, "正在处理...")
                delay(300)

                val bitmap = withContext(Dispatchers.IO) {
                    try { RootUtils.takeScreenshotAsBitmap() } catch (e: Exception) {
                        Log.e(TAG, "Screenshot error", e); null
                    }
                }

                if (bitmap == null) {
                    OverlayService.showText(this@VolumeKeyService, "截图失败")
                    return@launch
                }

                OverlayService.showText(this@VolumeKeyService, "正在分析...")

                val result = withContext(Dispatchers.IO) {
                    try { AIService.analyzeScreenshot(bitmap) } catch (e: Exception) {
                        Log.e(TAG, "AI error", e); Result.failure<String>(e)
                    }
                }

                result.onSuccess { response ->
                    val safe = if (response.isNullOrBlank()) "无结果" else response.take(2000)
                    OverlayService.showText(this@VolumeKeyService, safe)
                    copyToClipboardSafe(safe)
                }

                result.onFailure { error ->
                    OverlayService.showText(this@VolumeKeyService, "分析失败: ${error.message ?: "未知错误"}")
                }

                try { bitmap.recycle() } catch (_: Exception) {}

            } catch (e: Exception) {
                Log.e(TAG, "onVolumeDownPressed error", e)
                try { OverlayService.showText(this@VolumeKeyService, "处理失败") } catch (_: Exception) {}
            } finally {
                isProcessing = false
            }
        }
    }

    private fun onVolumeUpPressed() {
        // Toggle: 显示/隐藏当前答案
        try { OverlayService.toggleText(this) } catch (e: Exception) {
            Log.e(TAG, "toggleText failed", e)
        }
    }

    private fun copyToClipboardSafe(text: String) {
        mainHandler.post {
            try {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("AI Response", text))
            } catch (e: Exception) {
                Log.e(TAG, "Clipboard failed", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        keyMonitorJob?.cancel()
        try { monitorProcess?.destroy() } catch (_: Exception) {}
        instance = null
        Log.d(TAG, "VolumeKeyService stopped")
    }
}