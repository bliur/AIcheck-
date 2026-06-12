package com.ai.aicheat.util

import android.content.Context
import android.content.SharedPreferences
import com.ai.aicheat.App

object ConfigManager {

    private const val PREF_NAME = "aicheat_config"
    private const val KEY_API_URL = "api_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model"
    private const val KEY_PROMPT = "prompt"
    private const val KEY_OVERLAY_ALPHA = "overlay_alpha"
    private const val KEY_BLOCK_VOLUME_BAR = "block_volume_bar"
    private const val KEY_USE_TAP_DISMISS = "use_tap_dismiss"
    private const val KEY_TAP_X_RATIO = "tap_x_ratio"
    private const val KEY_TAP_Y_RATIO = "tap_y_ratio"
    private const val KEY_TAP_DELAY_MS = "tap_delay_ms"

    private const val BLOCK_FLAG_FILE = "/data/local/tmp/aicheat_block_volume"

    private val prefs: SharedPreferences by lazy {
        App.instance.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    var apiUrl: String
        get() = prefs.getString(KEY_API_URL, "https://api.openai.com/v1/chat/completions") ?: ""
        set(value) = prefs.edit().putString(KEY_API_URL, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, "gpt-4o") ?: ""
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    var prompt: String
        get() = prefs.getString(KEY_PROMPT, "请仔细分析这张图片，如果是题目请给出答案，如果是其他内容请简要描述。回答要简洁。") ?: ""
        set(value) = prefs.edit().putString(KEY_PROMPT, value).apply()

    var overlayAlpha: Float
        get() = prefs.getFloat(KEY_OVERLAY_ALPHA, 0.55f)
        set(value) = prefs.edit().putFloat(KEY_OVERLAY_ALPHA, value.coerceIn(0.0f, 1.0f)).apply()

    /** LSPosed 方案开关 (标记文件) */
    var blockVolumeBar: Boolean
        get() = try { java.io.File(BLOCK_FLAG_FILE).exists() } catch (_: Exception) { false }
        set(value) {
            try {
                if (value) {
                    RootUtils.executeRootCommandSilent("touch $BLOCK_FLAG_FILE")
                    RootUtils.executeRootCommandSilent("chmod 644 $BLOCK_FLAG_FILE")
                } else {
                    RootUtils.executeRootCommandSilent("rm -f $BLOCK_FLAG_FILE")
                }
            } catch (_: Exception) {}
        }

    /** 是否使用模拟点击方案替代 LSP */
    var useTapDismiss: Boolean
        get() = prefs.getBoolean(KEY_USE_TAP_DISMISS, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_TAP_DISMISS, value).apply()

    /** 模拟点击 X 比例 (0.0~1.0，相对于屏幕宽度) */
    var tapXRatio: Float
        get() = prefs.getFloat(KEY_TAP_X_RATIO, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_TAP_X_RATIO, value.coerceIn(0.0f, 1.0f)).apply()

    /** 模拟点击 Y 比例 (0.0~1.0，相对于屏幕高度) */
    var tapYRatio: Float
        get() = prefs.getFloat(KEY_TAP_Y_RATIO, 0.92f)
        set(value) = prefs.edit().putFloat(KEY_TAP_Y_RATIO, value.coerceIn(0.0f, 1.0f)).apply()

    /** 模拟点击延迟 (毫秒，音量键按下后多久执行点击) */
    var tapDelayMs: Long
        get() = prefs.getLong(KEY_TAP_DELAY_MS, 300L)
        set(value) = prefs.edit().putLong(KEY_TAP_DELAY_MS, value.coerceIn(50L, 2000L)).apply()

    fun isConfigured(): Boolean {
        return apiUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        RootUtils.executeRootCommandSilent("rm -f $BLOCK_FLAG_FILE")
    }
}