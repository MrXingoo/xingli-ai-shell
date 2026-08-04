package com.mgaoxin.xingli.shell

import android.annotation.SuppressLint
import android.webkit.WebView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 统一壳内自动登录：原生调两站登录接口拿 token → 注入 WebView 的 localStorage → reload。
 *
 * 支持两站（经服务器前端源码与实测接口确认）：
 *  - Studio  (ai.mgaoxin.com)        : POST /api/auth/login {username,password}
 *                                      返回 {token,userId,theme}，token 为 JWT。
 *                                      前端存 localStorage["hermes_api_key"]，请求塞 Authorization: Bearer <token>。
 *  - AList   (study.mgaoxin.com/files) : POST /files/api/auth/login {username,password}
 *                                      返回 {code:200, data:{token}}。
 *                                      前端存 localStorage["token"]，请求塞 Authorization 头。
 *
 * 账号密码硬编码仅限自用私有 APP + 自家服务器；后续版本可改 SharedPreferences 支持换账号。
 */
data class SiteCredential(
    val name: String,          // 站点名（日志用）
    val loginUrl: String,      // 登录接口
    val tokenLocalKey: String, // 前端存 token 的 localStorage key
    val username: String,
    val password: String,
)

object AutoLogin {
    private const val USERNAME = "Xingoo"
    private const val PASSWORD = "1jia1=er"

    val STUDIO = SiteCredential(
        name = "Studio",
        loginUrl = "https://ai.mgaoxin.com/api/auth/login",
        tokenLocalKey = "hermes_api_key",
        username = USERNAME,
        password = PASSWORD,
    )

    val ALIST = SiteCredential(
        name = "AList",
        loginUrl = "https://study.mgaoxin.com/files/api/auth/login",
        tokenLocalKey = "token",
        username = USERNAME,
        password = PASSWORD,
    )

    /** 防重入：页面 reload 会反复触发 onPageFinished */
    private val injectBusy = AtomicBoolean(false)

    /**
     * 若 WebView 当前页面未登录则自动登录并注入；已在登录态则跳过。
     * 返回 true 表示「已触发自动登录流程」，false 表示已登录或无需处理。
     */
    @SuppressLint("JavascriptInterface")
    fun ensureLoggedIn(webView: WebView, cred: SiteCredential): Boolean {
        if (!injectBusy.compareAndSet(false, true)) return false

        val gate = CountDownLatch(1)
        // 同步读 localStorage，避免异步回调在 reload 后丢失现场
        webView.evaluateJavascript(
            "localStorage.getItem('${cred.tokenLocalKey}')"
        ) { value ->
            val hasToken = value != null &&
                value != "null" &&
                value.isNotBlank() &&
                value != "\"\""
            gate.countDown()
            if (hasToken) {
                injectBusy.set(false)
            } else {
                doLogin(webView, cred)
            }
        }
        return true
    }

    private fun doLogin(webView: WebView, cred: SiteCredential) {
        Thread {
            val token = try {
                requestToken(cred)
            } catch (e: Exception) {
                android.util.Log.w("AutoLogin", "登录意外失败: ${e.message}")
                null
            }
            webView.post {
                try {
                    if (token != null) {
                        val escaped = token
                            .replace("\\", "\\\\")
                            .replace("'", "\\'")
                        webView.evaluateJavascript(
                            "localStorage.setItem('${cred.tokenLocalKey}', '$escaped')"
                        ) { _ ->
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

    private fun requestToken(cred: SiteCredential): String? {
        val conn = URL(cred.loginUrl).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            val body = JSONObject()
                .put("username", cred.username)
                .put("password", cred.password)
                .toString()
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code in 200..299) {
                // Studio: {"token":"<jwt>","userId":1,...}
                // AList:  {"code":200,"data":{"token":"<jwt>"},...}
                val json = JSONObject(text)
                if (!json.isNull("token")) {
                    json.optString("token").takeIf { it.isNotBlank() }
                } else if (json.optInt("code") == 200) {
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
