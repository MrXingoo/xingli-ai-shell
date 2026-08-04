package com.mgaoxin.xingli.shell

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * 统一壳内自动登录：原生调两站登录接口拿 token → 注入 WebView 的 localStorage → reload。
 *
 * 支持两站（经服务器前端源码与实测接口确认）：
 *  - Studio  (ai.mgaoxin.com)        : POST /api/auth/login {username,password}
 *                                      返回 {token,userId,theme}，token 为 JWT。
 *                                      前端存 localStorage["hermes_api_key"]，请求塞 Authorization: Bearer ***
 *  - AList   (study.mgaoxin.com/files) : POST /files/api/auth/login {username,password}
 *                                      返回 {code:200, data:{token}}。
 *                                      前端存 localStorage["token"]，请求塞 Authorization 头。
 *
 * 关键：不信任 localStorage 里已有的旧 token——旧 token 可能已失效（服务端重启/过期），
 * 只检查"有没有 token"会导致误判已登录，前端用失效 token 请求 → 401 → 跳登录页。
 * 因此每次页面加载完成都重新登录拿新 token，与现有值对比：不同才覆盖注入并刷新（防死循环）。
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
    private const val TAG = "AutoLogin"
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

    /** 防重入（按站点隔离）：页面 reload 会反复触发 onPageFinished；三个 WebView 常驻，全局互斥会让后加载的站点被跳过 */
    private val busySites = ConcurrentHashMap.newKeySet<String>()

    /**
     * 页面加载完成后调用：重新登录拿新 token，与 localStorage 现有 token 对比。
     * - 相同 → 已是有效 token，跳过（防死循环）
     * - 不同/为空 → 覆盖注入新 token 并 reload
     */
    @SuppressLint("JavascriptInterface")
    fun ensureLoggedIn(webView: WebView, cred: SiteCredential, onFailure: ((String) -> Unit)? = null) {
        if (!busySites.add(cred.name)) return
        Thread {
            val freshToken = try {
                requestToken(cred)
            } catch (e: Exception) {
                Log.w(TAG, "${cred.name} 登录意外失败: ${e.message}")
                null
            }
            webView.post {
                try {
                    if (freshToken != null) {
                        webView.evaluateJavascript("localStorage.getItem('${cred.tokenLocalKey}')") { current ->
                            // evaluateJavascript 返回 JSON 序列化值（字符串带引号，null 为 "null"）
                            val cur = current
                                ?.trim()
                                ?.removeSurrounding("\"")
                                ?.takeIf { it != "null" && it.isNotEmpty() }
                            if (cur != freshToken) {
                                Log.i(TAG, "${cred.name}: token 不一致，注入新 token 并刷新")
                                val escaped = freshToken
                                    .replace("\\", "\\\\")
                                    .replace("'", "\\'")
                                webView.evaluateJavascript(
                                    "localStorage.setItem('${cred.tokenLocalKey}', '$escaped')"
                                ) { _ ->
                                    webView.reload()
                                }
                            } else {
                                Log.i(TAG, "${cred.name}: token 已是最新，跳过")
                            }
                        }
                    } else {
                        // token 获取失败：提示用户手动登录兜底（不再静默）
                        onFailure?.invoke("${cred.name} 自动登录失败（网络或服务器异常），请手动登录")
                    }
                } finally {
                    busySites.remove(cred.name)
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
                    Log.e(TAG, "${cred.name} 登录业务失败: ${json.optString("message")}")
                    null
                }
            } else {
                Log.e(TAG, "${cred.name} 登录接口 HTTP $code: $text")
                null
            }
        } finally {
            conn.disconnect()
        }
    }
}
