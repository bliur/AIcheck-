package com.ai.aicheat.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import com.ai.aicheat.util.ConfigManager

class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"

        const val ACTION_SHOW_TEXT = "com.ai.aicheat.SHOW_TEXT"
        const val ACTION_HIDE_TEXT = "com.ai.aicheat.HIDE_TEXT"
        const val ACTION_TOGGLE_TEXT = "com.ai.aicheat.TOGGLE_TEXT"
        const val ACTION_SET_ALPHA = "com.ai.aicheat.SET_ALPHA"

        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_ALPHA = "extra_alpha"

        private var instance: OverlayService? = null
        private var isHiddenByUser = false
        private var lastAnswerText = ""

        fun showText(context: Context, text: String) {
            isHiddenByUser = false
            lastAnswerText = text
            context.startService(Intent(context, OverlayService::class.java).apply {
                action = ACTION_SHOW_TEXT
                putExtra(EXTRA_TEXT, text)
            })
        }

        fun hideText(context: Context) {
            isHiddenByUser = true
            context.startService(Intent(context, OverlayService::class.java).apply {
                action = ACTION_HIDE_TEXT
            })
        }

        /** 切换显示/隐藏 (音量上键使用) */
        fun toggleText(context: Context) {
            if (isHiddenByUser && lastAnswerText.isNotEmpty()) {
                // 当前是隐藏状态 → 重新显示
                isHiddenByUser = false
                context.startService(Intent(context, OverlayService::class.java).apply {
                    action = ACTION_SHOW_TEXT
                    putExtra(EXTRA_TEXT, lastAnswerText)
                })
            } else if (!isHiddenByUser && lastAnswerText.isNotEmpty()) {
                // 当前是显示状态 → 隐藏
                hideText(context)
            }
        }

        fun setAlpha(context: Context, alpha: Float) {
            context.startService(Intent(context, OverlayService::class.java).apply {
                action = ACTION_SET_ALPHA
                putExtra(EXTRA_ALPHA, alpha)
            })
        }

        fun hasAnswer(): Boolean = lastAnswerText.isNotEmpty()
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var textView: TextView? = null
    private var currentText = ""
    private lateinit var markwon: Markwon

    override fun onCreate() {
        super.onCreate()
        instance = this
        createOverlayView()
        initMarkwon()
        setOverlayAlpha(ConfigManager.overlayAlpha)
        Log.d(TAG, "OverlayService created")
    }

    private fun initMarkwon() {
        val textSize = textView?.textSize ?: 36f
        markwon = Markwon.builder(this)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(JLatexMathPlugin.create(textSize) { builder ->
                builder.inlinesEnabled(true)
                builder.blocksEnabled(true)
            })
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_TEXT -> {
                val text = intent.getStringExtra(EXTRA_TEXT) ?: ""
                currentText = text
                textView?.post {
                    try { markwon.setMarkdown(textView!!, text) } catch (_: Exception) { textView?.text = text }
                    overlayView?.visibility = View.VISIBLE
                }
            }
            ACTION_HIDE_TEXT -> {
                textView?.post { overlayView?.visibility = View.GONE }
            }
            ACTION_TOGGLE_TEXT -> {
                // 直接在 companion object 的 toggleText 中处理
            }
            ACTION_SET_ALPHA -> {
                val alpha = intent.getFloatExtra(EXTRA_ALPHA, ConfigManager.overlayAlpha)
                setOverlayAlpha(alpha)
            }
        }
        return START_STICKY
    }

    private fun createOverlayView() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        textView = TextView(this).apply {
            setTextColor(0x99FFFFFF.toInt())
            textSize = 12f
            setPadding(16, 8, 16, 8)
            setBackgroundColor(0x33300000)
            gravity = Gravity.START
            maxLines = 15
        }
        overlayView = textView

        val params = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_SECURE
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 100
        }

        try {
            windowManager?.addView(overlayView, params)
            overlayView?.visibility = View.GONE
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create overlay", e)
        }
    }

    fun setOverlayAlpha(alpha: Float) {
        textView?.post { textView?.alpha = alpha }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { windowManager?.removeView(overlayView) } catch (_: Exception) {}
        instance = null
    }
}