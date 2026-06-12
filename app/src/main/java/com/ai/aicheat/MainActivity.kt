package com.ai.aicheat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.ai.aicheat.service.AIService
import com.ai.aicheat.service.OverlayService
import com.ai.aicheat.service.VolumeKeyService
import com.ai.aicheat.ui.theme.AicheatTheme
import com.ai.aicheat.util.ConfigManager
import com.ai.aicheat.util.RootUtils
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    companion object {
        private const val OVERLAY_PERMISSION_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), OVERLAY_PERMISSION_REQUEST)
        }
        setContent {
            AicheatTheme {
                MainScreen(
                    onStartService = { startMonitorService() },
                    onStopService = { stopMonitorService() },
                    onCheckRoot = { checkRoot() }
                )
            }
        }
    }

    private fun startMonitorService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), OVERLAY_PERMISSION_REQUEST)
            return
        }
        lifecycleScope.launch {
            if (RootUtils.checkRootAccess()) {
                VolumeKeyService.start(this@MainActivity)
                Toast.makeText(this@MainActivity, "服务已启动", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "需要Root权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopMonitorService() {
        VolumeKeyService.stop(this)
        Toast.makeText(this, "服务已停止", Toast.LENGTH_SHORT).show()
    }

    private fun checkRoot() {
        lifecycleScope.launch {
            val hasRoot = RootUtils.checkRootAccess()
            Toast.makeText(this@MainActivity, if (hasRoot) "Root权限: 已获取" else "Root权限: 未获取", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            Toast.makeText(this, if (Settings.canDrawOverlays(this)) "悬浮窗权限已授予" else "需要悬浮窗权限", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onCheckRoot: () -> Unit
) {
    val context = LocalContext.current
    var apiUrl by remember { mutableStateOf(ConfigManager.apiUrl) }
    var apiKey by remember { mutableStateOf(ConfigManager.apiKey) }
    var model by remember { mutableStateOf(ConfigManager.model) }
    var prompt by remember { mutableStateOf(ConfigManager.prompt) }
    var isServiceRunning by remember { mutableStateOf(VolumeKeyService.isRunning()) }
    var overlayAlpha by remember { mutableFloatStateOf(ConfigManager.overlayAlpha) }
    var blockVolumeBar by remember { mutableStateOf(ConfigManager.blockVolumeBar) }
    var useTapDismiss by remember { mutableStateOf(ConfigManager.useTapDismiss) }
    var tapDelayMs by remember { mutableFloatStateOf(ConfigManager.tapDelayMs.toFloat()) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(80.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFB3E5FC),
                            Color(0xFFE1F5FE),
                            Color(0xFFF0F4C3),
                            Color(0xFFFFF9C4)
                        )
                    )
                )
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("AIcheck", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0277BD))
                Text("Root 智能搜题助手", fontSize = 14.sp, color = Color(0xFF666666))
                Spacer(modifier = Modifier.height(4.dp))

                // ===== 服务控制 =====
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("服务状态", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (isServiceRunning) "运行中" else "已停止",
                                fontSize = 13.sp,
                                color = if (isServiceRunning) Color(0xFF43A047) else Color(0xFFE53935)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiButton(onClick = { onStartService(); isServiceRunning = true }, label = "启动", style = MiButtonStyle.Primary)
                            MiButton(onClick = { onStopService(); isServiceRunning = false }, label = "停止", style = MiButtonStyle.Danger)
                            MiButton(onClick = onCheckRoot, label = "检查 Root", style = MiButtonStyle.Secondary)
                        }
                    }
                }

                // ===== API 配置 =====
                GlassCard {
                    Text("API 配置", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))
                    MiTextField(value = apiUrl, onValueChange = { apiUrl = it }, label = "API URL")
                    MiTextField(value = apiKey, onValueChange = { apiKey = it }, label = "API Key")
                    MiTextField(value = model, onValueChange = { model = it }, label = "模型")
                    MiTextField(value = prompt, onValueChange = { prompt = it }, label = "自定义 Prompt", minLines = 3)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("答案透明度: ${(overlayAlpha * 100).roundToInt()}%", fontSize = 13.sp, color = Color(0xFF666666))
                    Slider(
                        value = overlayAlpha,
                        onValueChange = { overlayAlpha = it },
                        onValueChangeFinished = { ConfigManager.overlayAlpha = overlayAlpha; OverlayService.setAlpha(context, overlayAlpha) },
                        valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF0288D1), activeTrackColor = Color(0xFF0288D1))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MiButton(
                        onClick = {
                            ConfigManager.apiUrl = apiUrl; ConfigManager.apiKey = apiKey
                            ConfigManager.model = model; ConfigManager.prompt = prompt
                            AIService.updateConfig(AIService.AIConfig(apiUrl, apiKey, model))
                            Toast.makeText(context, "配置已保存", Toast.LENGTH_SHORT).show()
                        },
                        label = "保存配置", style = MiButtonStyle.Primary, modifier = Modifier.fillMaxWidth()
                    )
                }

                // ===== 屏蔽音量条方案选择 =====
                GlassCard(containerColor = Color(0xFFE3F2FD).copy(alpha = 0.6f)) {
                    Text("屏蔽音量条", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF01579B))
                    Spacer(modifier = Modifier.height(10.dp))

                    // LSPosed 方案开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("LSPosed Hook 方案", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1565C0))
                            Text("需要 LSPosed 框架，Hook SystemUI 隐藏音量条", fontSize = 11.sp, color = Color(0xFF78909C))
                        }
                        Switch(
                            checked = blockVolumeBar,
                            onCheckedChange = {
                                blockVolumeBar = it; ConfigManager.blockVolumeBar = it
                                Toast.makeText(context, if (it) "LSPosed 屏蔽已开启" else "LSPosed 屏蔽已关闭", Toast.LENGTH_SHORT).show()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF0288D1), checkedTrackColor = Color(0xFF0288D1).copy(alpha = 0.3f))
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFFB3E5FC))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 模拟点击方案开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("模拟点击方案", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1565C0))
                            Text("音量键后模拟点击屏幕收起音量条，无需 LSPosed", fontSize = 11.sp, color = Color(0xFF78909C))
                        }
                        Switch(
                            checked = useTapDismiss,
                            onCheckedChange = {
                                useTapDismiss = it; ConfigManager.useTapDismiss = it
                                Toast.makeText(context, if (it) "模拟点击方案已开启" else "模拟点击方案已关闭", Toast.LENGTH_SHORT).show()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF0288D1), checkedTrackColor = Color(0xFF0288D1).copy(alpha = 0.3f))
                        )
                    }

                    // 模拟点击配置（仅在开关打开时显示）
                    if (useTapDismiss) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // 延迟配置
                        Text("点击延迟: ${tapDelayMs.roundToInt()} ms", fontSize = 13.sp, color = Color(0xFF546E7A))
                        Slider(
                            value = tapDelayMs,
                            onValueChange = { tapDelayMs = it },
                            onValueChangeFinished = { ConfigManager.tapDelayMs = tapDelayMs.toLong() },
                            valueRange = 50f..1000f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF26A69A), activeTrackColor = Color(0xFF26A69A))
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("拖动红点选择点击位置", fontSize = 13.sp, color = Color(0xFF546E7A))
                        Spacer(modifier = Modifier.height(8.dp))

                        // 可视化拖拽选择器
                        TapPositionEditor()
                    }
                }

                // ===== 使用说明 =====
                GlassCard(containerColor = Color(0xFFFFF8E1).copy(alpha = 0.7f)) {
                    Text("使用说明", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF795548))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• 需要 Root 权限 + 悬浮窗权限", fontSize = 13.sp, color = Color(0xFF8D6E63))
                    Text("• 音量下键：截图并发送 AI 分析", fontSize = 13.sp, color = Color(0xFF8D6E63))
                    Text("• 音量上键：隐藏答案悬浮窗", fontSize = 13.sp, color = Color(0xFF8D6E63))
                    Text("• 两种方案可同时开启，互不冲突", fontSize = 13.sp, color = Color(0xFF8D6E63))
                    Text("• 答案悬浮窗截图不可见 (FLAG_SECURE)", fontSize = 13.sp, color = Color(0xFF8D6E63))
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// ===== 可视化拖拽点击位置编辑器 =====

@Composable
fun TapPositionEditor() {
    var xRatio by remember { mutableFloatStateOf(ConfigManager.tapXRatio) }
    var yRatio by remember { mutableFloatStateOf(ConfigManager.tapYRatio) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    // 手机外框比例 9:19.5 (模拟手机)
    val phoneRatio = 19.5f / 9f
    val phoneWidthDp = 200.dp
    val phoneHeightDp = phoneWidthDp * phoneRatio
    val phoneWidthPx = with(density) { phoneWidthDp.toPx() }
    val phoneHeightPx = with(density) { phoneHeightDp.toPx() }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // 手机外框
        Box(
            modifier = Modifier
                .width(phoneWidthDp)
                .height(phoneHeightDp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF263238))
                .onSizeChanged { containerSize = it }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val x = (change.position.x / size.width).coerceIn(0f, 1f)
                        val y = (change.position.y / size.height).coerceIn(0f, 1f)
                        xRatio = x
                        yRatio = y
                        ConfigManager.tapXRatio = x
                        ConfigManager.tapYRatio = y
                    }
                }
        ) {
            // 模拟屏幕内容区
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF37474F), Color(0xFF455A64))
                        )
                    )
            ) {
                // 模拟状态栏
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .background(Color(0xFF1B2631))
                )
                // 模拟底部导航栏
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color(0xFF1B2631))
                )
                // 模拟音量条 (半透明)
                Box(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 60.dp)
                        .width(40.dp)
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                )
                // 模拟内容线
                repeat(5) { i ->
                    Box(
                        modifier = Modifier
                            .padding(start = 20.dp, top = (100 + i * 40).dp, end = 20.dp)
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                    )
                }
            }

            // 红点 - 可拖动的点击位置
            val dotSize = 28.dp
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { (xRatio * containerSize.width).toDp() } - dotSize / 2,
                        y = with(density) { (yRatio * containerSize.height).toDp() } - dotSize / 2
                    )
                    .size(dotSize)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color(0xFFEF5350))
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }

    // 坐标显示
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        "点击位置: X=${(xRatio * 100).roundToInt()}%  Y=${(yRatio * 100).roundToInt()}%",
        fontSize = 12.sp,
        color = Color(0xFF78909C),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

// ===== MIUI 风格组件 =====

@Composable
fun GlassCard(
    containerColor: Color = Color.White.copy(alpha = 0.55f),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

enum class MiButtonStyle { Primary, Secondary, Danger }

@Composable
fun MiButton(
    onClick: () -> Unit,
    label: String,
    style: MiButtonStyle = MiButtonStyle.Primary,
    modifier: Modifier = Modifier
) {
    val bg = when (style) {
        MiButtonStyle.Primary -> Brush.horizontalGradient(listOf(Color(0xFF4FC3F7), Color(0xFF0288D1)))
        MiButtonStyle.Secondary -> Brush.horizontalGradient(listOf(Color(0xFFE0E0E0), Color(0xFFBDBDBD)))
        MiButtonStyle.Danger -> Brush.horizontalGradient(listOf(Color(0xFFEF5350), Color(0xFFD32F2F)))
    }
    val textColor = if (style == MiButtonStyle.Secondary) Color(0xFF555555) else Color.White

    Button(
        onClick = onClick,
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(bg),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        elevation = null
    ) {
        Text(label, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = minLines == 1,
        minLines = minLines,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF0288D1),
            cursorColor = Color(0xFF0288D1),
            focusedContainerColor = Color.White.copy(alpha = 0.5f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.3f)
        )
    )
    Spacer(modifier = Modifier.height(8.dp))
}