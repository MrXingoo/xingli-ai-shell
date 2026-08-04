package com.mgaoxin.xingli.shell

import android.content.Context
import android.util.Log
import android.webkit.WebSettings

/**
 * WebView 内核版本检测。
 *
 * 背景：Hermes Studio 前端只有现代 ES Module 构建（无 Vite legacy 降级包，最低要求
 * Chrome 87+），旧内核 WebView 直接 JS 不执行 → boot-fallback 白屏。AList 自带 legacy
 * 降级包所以老内核也能渲染。检测到过旧内核时引导用户更新 Android System WebView。
 */
object WebViewCompat {
    private const val TAG = "WebViewCompat"

    /** 解析 WebView 内核主版本号；无法确定时返回 -1（调用方按"不误报"处理） */
    fun chromeVersion(context: Context): Int {
        // 常见 WebView provider 包名（小米系可能是 com.miui.webkit，三星是 sec.android）
        val providers = listOf(
            "com.google.android.webview",
            "com.android.webview",
            "com.miui.webkit",
            "com.sec.android.app.sbrowser",
            "org.chromium.webview_shell",
        )
        for (pkg in providers) {
            try {
                val info = context.packageManager.getPackageInfo(pkg, 0)
                val v = info.versionName
                    ?.let { Regex("(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }
                if (v != null && v > 0) return v
            } catch (_: Exception) {
                // 包不存在，试下一个
            }
        }
        // 兜底：默认 UA 解析 Chrome/xx
        return try {
            val ua = WebSettings.getDefaultUserAgent(context)
            Regex("Chrome/(\\d+)").find(ua)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        } catch (e: Exception) {
            Log.w(TAG, "解析默认 UA 失败: ${e.message}")
            -1
        }
    }

    /** 内核是否过旧（低于 minChrome）。无法确定版本时返回 false，避免误报 */
    fun isTooOld(context: Context, minChrome: Int = 90): Boolean {
        val v = chromeVersion(context)
        return v in 1 until minChrome
    }
}
