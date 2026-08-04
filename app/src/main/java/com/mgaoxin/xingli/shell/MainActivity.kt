package com.mgaoxin.xingli.shell

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex

private const val TAG = "XingliShell"

/** 登录页自愈标记：JS 侧 console 输出此前缀，壳侧 onConsoleMessage 捕获后重新自动登录 */
private const val XINGLI_LOGIN_MARKER = "[xingli-login]"

enum class ShellTab(
    val titleRes: Int,
    val url: String,
    val icon: ImageVector,
    /** 壳内自动登录凭据：null 表示不做自动登录 */
    val autoLogin: SiteCredential?,
    /** Hermes Studio 是桌面优先设计，移动 UA 可能触发响应式布局 bug 导致白屏，用桌面 UA 加载 */
    val useDesktopUA: Boolean,
) {
    CHAT(
        titleRes = R.string.tab_chat,
        url = "https://ai.mgaoxin.com/#/hermes/chat",
        icon = Icons.AutoMirrored.Filled.Chat,
        autoLogin = AutoLogin.STUDIO,
        useDesktopUA = true,
    ),
    LIBRARY(
        titleRes = R.string.tab_library,
        url = "https://study.mgaoxin.com/files/",
        icon = Icons.Filled.Book,
        autoLogin = AutoLogin.ALIST,
        useDesktopUA = false,
    ),
    SETTINGS(
        titleRes = R.string.tab_settings,
        url = "https://ai.mgaoxin.com/#/hermes/settings",
        icon = Icons.Filled.Settings,
        autoLogin = AutoLogin.STUDIO,
        useDesktopUA = true,
    ),
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XingliTheme {
                ShellScreen()
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellScreen() {
    val context = LocalContext.current
    var currentTab by rememberSaveable { mutableStateOf(ShellTab.CHAT) }
    // 三个 WebView 实例常驻，切换 Tab 只改可见性不销毁 → 登录态/页面状态全保留
    val webViews = remember { mutableMapOf<ShellTab, WebView>() }
    var loadingTab by remember { mutableStateOf<ShellTab?>(null) }
    // WebView 内核过旧检测（旧内核 → Studio 前端 JS 不执行 → 白屏）
    var webViewTooOld by remember { mutableStateOf(false) }
    // 兼容性/自动登录失败提示条
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        webViewTooOld = WebViewCompat.isTooOld(context)
    }

    BackHandler {
        val wv = webViews[currentTab]
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            // 无历史则最小化退后台（不销毁 WebView，保持会话）
            val activity = wv?.context as? ComponentActivity
            activity?.moveTaskToBack(true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(currentTab.titleRes)) },
                actions = {
                    IconButton(onClick = { webViews[currentTab]?.reload() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "刷新",
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                ShellTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = stringResource(tab.titleRes),
                            )
                        },
                        label = { Text(stringResource(tab.titleRes)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            ShellTab.entries.forEach { tab ->
                AndroidView(
                    factory = { ctx ->
                        webViews.getOrPut(tab) {
                            createWebView(
                                context = ctx,
                                url = tab.url,
                                autoLogin = tab.autoLogin,
                                useDesktopUA = tab.useDesktopUA,
                                detectBootFallback = tab.useDesktopUA,
                                onNotice = { notice = it },
                            ) { loading ->
                                if (loading) loadingTab = tab else if (loadingTab == tab) loadingTab = null
                            }
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .zIndex(if (currentTab == tab) 1f else 0f),
                    update = { view ->
                        view.visibility =
                            if (currentTab == tab) {
                                View.VISIBLE
                            } else {
                                View.INVISIBLE
                            }
                    },
                )
            }
            if (loadingTab != null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
            notice?.let { msg ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(5f),
                    color = Color(0xFFFFF3CD),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = msg,
                            color = Color(0xFF664D03),
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { notice = null }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "关闭提示",
                            )
                        }
                    }
                }
            }
        }
    }

    if (webViewTooOld) {
        AlertDialog(
            onDismissRequest = { webViewTooOld = false },
            title = { Text("WebView 内核过旧") },
            text = {
                Text(
                    "检测到当前「Android System WebView」版本过低（低于 Chrome 90），" +
                        "会话/配置页可能无法正常显示（白屏）。建议更新后重新打开 APP。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        webViewTooOld = false
                        openWebViewStore(context)
                    },
                ) {
                    Text("去更新")
                }
            },
            dismissButton = {
                TextButton(onClick = { webViewTooOld = false }) {
                    Text("继续使用")
                }
            },
        )
    }
}

/** 注入 JS：捕获 window.onerror / unhandledrejection / console.error，红条浮层显示 → 白屏时可看到真实报错 */
private const val JS_ERROR_CAPTURE = """
(function () {
  if (window.__xingliErrCaptured) return;
  window.__xingliErrCaptured = true;
  var bar = null;
  function show(msg) {
    try {
      if (!bar) {
        bar = document.createElement('div');
        bar.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:999999;background:#c62828;color:#fff;font-size:12px;padding:6px 10px;white-space:pre-wrap;word-break:break-all;max-height:40%;overflow:auto;';
        document.body.appendChild(bar);
      }
      var line = document.createElement('div');
      line.textContent = msg;
      bar.appendChild(line);
    } catch (e) {}
  }
  window.addEventListener('error', function (e) {
    show('JS错误: ' + (e.message || '') + ' @' + (e.filename || '') + ':' + (e.lineno || ''));
  }, true);
  window.addEventListener('unhandledrejection', function (e) {
    show('Promise错误: ' + (e.reason && e.reason.message ? e.reason.message : String(e.reason)));
  });
  var origErr = console.error;
  console.error = function () {
    try { show('console.error: ' + Array.prototype.slice.call(arguments).map(function(a){return typeof a==='string'?a:JSON.stringify(a);}).join(' ').substring(0, 500)); } catch (e) {}
    origErr.apply(console, arguments);
  };
})();
"""

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: Context,
    url: String,
    autoLogin: SiteCredential?,
    useDesktopUA: Boolean = false,
    detectBootFallback: Boolean = false,
    onNotice: (String) -> Unit,
    onLoading: (Boolean) -> Unit,
): WebView {
    val webView = WebView(context)
    webView.apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            if (useDesktopUA) {
                userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
            }
        }
        // 持久化登录态：Cookie + localStorage 均默认落盘（非 incognito）
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                onLoading(true)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onLoading(false)
                // 注入 JS 错误捕获（白屏排查用）
                view?.evaluateJavascript(JS_ERROR_CAPTURE, null)
                // 注入登录页监听器：SPA 内 token 失效跳登录页不触发 onPageFinished，
                // 靠 MutationObserver 发现登录页 → console 标记 → 壳侧触发重新登录（自愈）
                view?.evaluateJavascript(JS_LOGIN_WATCHER, null)
                // 壳内自动登录（Studio/AList 通用）：页面加载完检查 token，不一致则注入后刷新
                autoLogin?.let { cred ->
                    view?.let {
                        AutoLogin.ensureLoggedIn(
                            it, cred,
                            onFailure = { msg ->
                                Log.w(TAG, msg)
                                onNotice(msg)
                            },
                        )
                    }
                }
                // boot-fallback 残留检测（SPA 未挂载 = JS 没执行 = WebView 内核过旧）。
                // 延迟 1.5s 等动态 import 完成，避免误报。
                if (detectBootFallback) {
                    view?.postDelayed({
                        view.evaluateJavascript(JS_CHECK_BOOT_FALLBACK) { r ->
                            if (r == "true") {
                                Log.w(TAG, "boot-fallback 残留：SPA 未挂载，WebView 内核可能过旧")
                                onNotice("页面未正常加载：WebView 内核可能过旧，请在系统设置中更新「Android System WebView」后重试")
                            }
                        }
                    }, 1500)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                super.onReceivedError(view, request, error)
                Log.e(TAG, "onReceivedError url=${request?.url} code=${error?.errorCode} desc=${error?.description}")
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                val text = message?.message()
                // 登录页自愈：JS 侧发现登录页（token 失效/未登录）→ 壳侧重新自动登录
                if (text?.startsWith(XINGLI_LOGIN_MARKER) == true) {
                    Log.i(TAG, "检测到登录页（${text.removePrefix(XINGLI_LOGIN_MARKER)}），触发重新登录")
                    autoLogin?.let { cred ->
                        AutoLogin.ensureLoggedIn(
                            webView, cred,
                            forceReload = true,
                            onFailure = { msg ->
                                Log.w(TAG, msg)
                                onNotice(msg)
                            },
                        )
                    }
                    return super.onConsoleMessage(message)
                }
                // 全量日志：error/warning 打 Log，方便出问题时定位（白屏/登录失败排查）
                if (message != null) {
                    when (message.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR ->
                            Log.e(TAG, "JS console.error: ${message.message()} @${message.sourceId()}:${message.lineNumber()}")
                        ConsoleMessage.MessageLevel.WARNING ->
                            Log.w(TAG, "JS console.warn: ${message.message()} @${message.sourceId()}:${message.lineNumber()}")
                        else ->
                            Log.d(TAG, "JS console: ${message.message()}")
                    }
                }
                return super.onConsoleMessage(message)
            }
        }
        loadUrl(url)
    }
    return webView
}

/** 检测 Studio 前端 SPA 是否挂载：boot-fallback 残留或 #app 为空 = JS 未执行（旧内核白屏） */
private const val JS_CHECK_BOOT_FALLBACK = """
(function () {
  var app = document.getElementById('app');
  if (!app) return false;
  if (app.children.length === 0) return true;
  var fb = app.querySelector('.boot-fallback');
  return !!(fb && app.children.length <= 1);
})()
"""

/**
 * 登录页监听器（自愈核心）：SPA 内部路由跳转不触发 onPageFinished，
 * token 被前端清除（401）后停在登录页时，靠 MutationObserver 发现登录表单 →
 * console 打标记 → 壳侧 onConsoleMessage 捕获 → 重新自动登录 → 注入 token + reload。
 *
 * 判定（均从服务器前端源码确认，避免误触发）：
 *  - Studio (ai.mgaoxin.com)：登录卡片含 .login-view / .login-card（LoginView 组件）
 *  - AList (study.mgaoxin.com)：登录页 URL 路径含 /@login（路由源码 "/@login"）
 * 10 秒冷却防止 token 注入失败时无限循环刷新。
 */
private const val JS_LOGIN_WATCHER = """
(function () {
  if (window.__xingliLoginWatcher) return;
  window.__xingliLoginWatcher = true;
  var lastFire = 0;
  function isLoginPage() {
    try {
      if (location.pathname.indexOf('@login') >= 0) return true;
      if (document.querySelector('.login-view')) return true;
      if (document.querySelector('.login-card')) return true;
    } catch (e) {}
    return false;
  }
  function check() {
    try {
      if (!isLoginPage()) return;
      var now = Date.now();
      if (now - lastFire < 10000) return; // 10s 冷却，防循环
      lastFire = now;
      console.log('${XINGLI_LOGIN_MARKER}' + location.href);
    } catch (e) {}
  }
  var mo = new MutationObserver(check);
  mo.observe(document.documentElement, { childList: true, subtree: true });
  setInterval(check, 3000);
  check();
})()
"""

/** 引导去应用商店更新 Android System WebView */
private fun openWebViewStore(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.webview"))
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.webview"),
                ),
            )
        } catch (e: Exception) {
            Log.e(TAG, "打开应用商店失败", e)
        }
    }
}
