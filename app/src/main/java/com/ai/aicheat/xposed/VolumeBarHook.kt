package com.ai.aicheat.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File

class VolumeBarHook : IXposedHookLoadPackage {

    companion object {
        private const val PACKAGE_SYSTEMUI = "com.android.systemui"
        private const val FLAG_FILE = "/data/local/tmp/aicheat_block_volume"

        /**
         * 用最简单的文件存在性判断开关状态
         * 文件存在 = 屏蔽开启，文件不存在 = 屏蔽关闭
         * 完全不依赖 XSharedPreferences
         */
        private fun isBlockEnabled(): Boolean {
            return try {
                val f = File(FLAG_FILE)
                val exists = f.exists()
                exists
            } catch (_: Throwable) {
                false
            }
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != PACKAGE_SYSTEMUI) return

        XposedBridge.log("[AIcheck] Loaded in SystemUI, registering hooks")

        // === Hook 所有可能的音量对话框类 ===
        val classNames = listOf(
            "com.android.systemui.volume.VolumeDialogImpl",
            "miui.systemui.volume.VolumeDialogImpl",
            "com.android.systemui.volume.MiuiVolumeDialogImpl",
            "com.oplus.systemui.volume.VolumeDialogImpl",
            "com.samsung.systemui.volume.VolumeDialogImpl",
            "com.vivo.systemui.volume.VolumeDialogImpl"
        )

        for (className in classNames) {
            try {
                val clazz = XposedHelpers.findClass(className, lpparam.classLoader)
                hookVolumeDialogClass(clazz)
                XposedBridge.log("[AIcheck] Hooked: $className")
            } catch (_: Throwable) {}
        }

        // === Hook Controller ===
        val controllerNames = listOf(
            "com.android.systemui.volume.VolumeDialogControllerImpl",
            "miui.systemui.volume.VolumeDialogControllerImpl",
            "com.oplus.systemui.volume.VolumeDialogControllerImpl"
        )
        for (className in controllerNames) {
            try {
                val clazz = XposedHelpers.findClass(className, lpparam.classLoader)
                try {
                    XposedHelpers.findAndHookMethod(clazz, "showVolumeDialog",
                        Int::class.javaPrimitiveType!!,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (isBlockEnabled()) {
                                    XposedBridge.log("[AIcheck] Blocked showVolumeDialog")
                                    param.result = null
                                }
                            }
                        })
                } catch (_: Throwable) {}
                XposedBridge.log("[AIcheck] Hooked controller: $className")
            } catch (_: Throwable) {}
        }

        // === 兜底：hook WindowManagerGlobal.addView 拦截音量窗口 ===
        try {
            val wmgClass = XposedHelpers.findClass("android.view.WindowManagerGlobal", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(wmgClass, "addView",
                android.view.View::class.java,
                android.view.WindowManager.LayoutParams::class.java,
                android.view.Display::class.java,
                android.view.Window::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!isBlockEnabled()) return
                        try {
                            val params = param.args[1] as? android.view.WindowManager.LayoutParams ?: return
                            val title = params.title?.toString() ?: ""
                            if (title.contains("Volume", ignoreCase = true) ||
                                title.contains("volume", ignoreCase = true) ||
                                title.contains("音量", ignoreCase = true) ||
                                params.type == 2020) {
                                XposedBridge.log("[AIcheck] Blocked volume window: $title (type=${params.type})")
                                param.result = null
                            }
                        } catch (_: Throwable) {}
                    }
                })
            XposedBridge.log("[AIcheck] Hooked WindowManagerGlobal.addView")
        } catch (e: Throwable) {
            XposedBridge.log("[AIcheck] WM hook failed: ${e.message}")
        }

        // === 兜底2：hook Dialog.show ===
        try {
            val dialogClass = XposedHelpers.findClass("android.app.Dialog", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(dialogClass, "show", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!isBlockEnabled()) return
                    try {
                        val ctx = XposedHelpers.callMethod(param.thisObject, "getContext")
                        val className = ctx?.javaClass?.name ?: ""
                        // 只拦截 SystemUI 进程中与音量相关的 Dialog
                        if (className.contains("volume", ignoreCase = true) ||
                            className.contains("Volume", ignoreCase = true)) {
                            XposedBridge.log("[AIcheck] Blocked volume Dialog.show()")
                            param.result = null
                        }
                    } catch (_: Throwable) {}
                }
            })
        } catch (_: Throwable) {}

        XposedBridge.log("[AIcheck] All hooks registered")
    }

    private fun hookVolumeDialogClass(clazz: Class<*>) {
        // show(int)
        try {
            XposedHelpers.findAndHookMethod(clazz, "show",
                Int::class.javaPrimitiveType!!,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isBlockEnabled()) {
                            XposedBridge.log("[AIcheck] Blocked show(int) on ${clazz.simpleName}")
                            param.result = null
                        }
                    }
                })
        } catch (_: Throwable) {}

        // show()
        try {
            XposedHelpers.findAndHookMethod(clazz, "show", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isBlockEnabled()) {
                        XposedBridge.log("[AIcheck] Blocked show() on ${clazz.simpleName}")
                        param.result = null
                    }
                }
            })
        } catch (_: Throwable) {}

        // showH(int)
        try {
            XposedHelpers.findAndHookMethod(clazz, "showH",
                Int::class.javaPrimitiveType!!,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isBlockEnabled()) {
                            XposedBridge.log("[AIcheck] Blocked showH(int) on ${clazz.simpleName}")
                            param.result = null
                        }
                    }
                })
        } catch (_: Throwable) {}

        // showH()
        try {
            XposedHelpers.findAndHookMethod(clazz, "showH", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isBlockEnabled()) {
                        XposedBridge.log("[AIcheck] Blocked showH() on ${clazz.simpleName}")
                        param.result = null
                    }
                }
            })
        } catch (_: Throwable) {}

        // showDialog
        try {
            XposedHelpers.findAndHookMethod(clazz, "showDialog",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isBlockEnabled()) {
                            XposedBridge.log("[AIcheck] Blocked showDialog on ${clazz.simpleName}")
                            param.result = null
                        }
                    }
                })
        } catch (_: Throwable) {}

        // setState
        try {
            XposedHelpers.findAndHookMethod(clazz, "setState",
                Int::class.javaPrimitiveType!!,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isBlockEnabled()) {
                            XposedBridge.log("[AIcheck] Blocked setState on ${clazz.simpleName}")
                            param.result = null
                        }
                    }
                })
        } catch (_: Throwable) {}
    }
}