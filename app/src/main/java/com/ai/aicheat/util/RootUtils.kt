package com.ai.aicheat.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File

object RootUtils {
    private const val TAG = "RootUtils"

    suspend fun checkRootAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("id\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            Log.e(TAG, "Root check failed", e)
            false
        }
    }

    suspend fun executeCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()

            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val exitValue = process.waitFor()

            CommandResult(exitValue == 0, output, error)
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed: $command", e)
            CommandResult(false, "", e.message ?: "Unknown error")
        }
    }

    /**
     * 静默执行 root 命令（同步，无日志，用于音量补偿等高频场景）
     */
    fun executeRootCommandSilent(command: String) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\nexit\n")
            os.flush()
            os.close()
            process.waitFor()
        } catch (_: Exception) {}
    }

    suspend fun takeScreenshot(outputPath: String): File? = withContext(Dispatchers.IO) {
        try {
            val result = executeCommand("screencap -p $outputPath")
            if (result.success) {
                val file = File(outputPath)
                if (file.exists() && file.length() > 0) {
                    file
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Take screenshot failed", e)
            null
        }
    }

    suspend fun takeScreenshotAsBitmap(): Bitmap? = withContext(Dispatchers.IO) {
        val tempPath = "/data/local/tmp/screenshot_${System.currentTimeMillis()}.png"
        try {
            val file = takeScreenshot(tempPath)
            if (file != null) {
                val bitmap = BitmapFactory.decodeFile(tempPath)
                executeCommand("rm $tempPath")
                bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Take screenshot as bitmap failed", e)
            executeCommand("rm $tempPath")
            null
        }
    }

    fun getInputEventCommand(): String = "getevent -l"

    suspend fun injectKeyEvent(keyCode: Int): Boolean = withContext(Dispatchers.IO) {
        val result = executeCommand("input keyevent $keyCode")
        result.success
    }

    suspend fun copyToClipboard(text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempFile = "/data/local/tmp/clip_${System.currentTimeMillis()}.txt"

            val writeResult = writeTextToFile(text, tempFile)
            if (!writeResult) return@withContext false

            var result = executeCommand("cat $tempFile | cmd clipboard set")
            if (result.success && !result.error.contains("Error") && !result.error.contains("Exception")) {
                executeCommand("rm $tempFile")
                return@withContext true
            }

            val escapedForShell = text.replace("'", "'\"'\"'")
            executeCommand("am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT '$escapedForShell' --activity-no-history --activity-exclude-from-recents com.android.shell 2>/dev/null || true")
            executeCommand("settings put system clipboard_text \"$(cat $tempFile)\"")
            executeCommand("rm $tempFile")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Copy to clipboard failed", e)
            false
        }
    }

    private suspend fun writeTextToFile(text: String, filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("cat > $filePath << 'CLIPBOARD_EOF'\n")
            os.writeBytes(text)
            os.writeBytes("\nCLIPBOARD_EOF\n")
            os.writeBytes("chmod 644 $filePath\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            Log.e(TAG, "Write text to file failed", e)
            false
        }
    }

    suspend fun pasteText(text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val escapedText = text
                .replace("\\", "\\\\")
                .replace(" ", "%s")
                .replace("\n", "")
                .replace("\r", "")
                .replace("\t", "")
            val result = executeCommand("input text \"$escapedText\"")
            result.success
        } catch (e: Exception) {
            Log.e(TAG, "Paste text failed", e)
            false
        }
    }

    data class CommandResult(
        val success: Boolean,
        val output: String,
        val error: String
    )
}