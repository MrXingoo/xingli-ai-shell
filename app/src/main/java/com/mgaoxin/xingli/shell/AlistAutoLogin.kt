package com.mgaoxin.xingli.shell

import android.annotation.SuppressLint
import android.webkit.WebView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 书房页(AList)壳内自动登录：
 * 原生调 /files/api/auth/login 拿 token → 注入 WebView 的 localStorage("token") → 刷新页面。
 *
 * 依据（已从服务器 AList v3 前端 index-*.js 源码确认）：
 * - 登录 token 存储在 localStorage key = "token"
 * - 请求时 axios 拦截器从 localStorage 读 token 塞进 Authorization 头
 *
 * 注意：账号密码硬编码仅限自用私有 APP + 自家服务器（study.mgaoxin.com），
 * 后续版本可改为首次登录后存 SharedPreferences 支持更换账号。
 */
object AlistAutoLogin {
    private const val LOGIN_URL = "https://study.mgaoxin.com/files/api/auth/login"
    private const val USERNAME = "Xingoo"
    private const val PASSWORD = "1jia1=er"

    /** 防重入：页面 reload 会反复触发 onPageFinished */
    private val injectBusy = AtomicBoolean(false)

    /** 若书房页未登录则自动登录并注入；已在登录态则跳过。 */
    @SuppressLint("JavascriptInterface")
    fun ensureLoggedIn(webView: WebView) {
        if (!injectBusy.compareAndSet(false, true)) return
        webView.evaluateJavascript("localStorage.getItem('token')") { value ->
            // evaluateJavascript 返回的是带引号的字符串或 "null"
            val hasToken = value != null &&
                value != "null" &&
                value.isNotBlank() &&
                value != "\"\""
            if (hasToken) {
                injectBusy.set(false)
                return@evaluateJavascript
            }
            doLogin(webView)
        }
    }

    private fun doLogin(webView: WebView) {
        Thread {
            val token = try {
                requestToken()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
            webView.post {
                try {
                    if (token != null) {
                        val escaped = token
                            .replace("\\", "\\\\")
                            .replace("'", "\\'")
                        webView.evaluateJavascript("localStorage.setItem('token', '$escaped')") { _ ->
                            webView.reload()
                        }
                    }
                    // token 获取失败则静默：用户手动登录兜底
                } finally {
                    injectBusy.set(false)
                }
            }
        }.start()
    }

    private fun requestToken(): String? {
        val conn = URL(LOGIN_URL).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            val body = JSONObject()
                .put("username", USERNAME)
                .put("password", PASSWORD)
                .toString()
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code in 200..299) {
                val json = JSONObject(text)
                if (json.optInt("code") == 200) {
                    json.optJSONObject("data")
                        ?.optString("token")
                        ?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    }
}
