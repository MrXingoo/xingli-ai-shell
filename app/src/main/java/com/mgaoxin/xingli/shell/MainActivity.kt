package com.mgaoxin.xingli.shell

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex

enum class ShellTab(
    val titleRes: Int,
    val url: String,
    val icon: ImageVector,
    val autoLogin: SiteCredential?,
) {
    CHAT(
        titleRes = R.string.tab_chat,
        url = "https://ai.mgaoxin.com/#/hermes/chat",
        icon = Icons.AutoMirrored.Filled.Chat,
        autoLogin = AutoLogin.STUDIO,
    ),
    LIBRARY(
        titleRes = R.string.tab_library,
        url = "https://study.mgaoxin.com/files/",
        icon = Icons.Filled.Book,
        autoLogin = AutoLogin.ALIST,
    ),
    SETTINGS(
        titleRes = R.string.tab_settings,
        url = "https://ai.mgaoxin.com/#/hermes/settings",
        icon = Icons.Filled.Settings,
        autoLogin = AutoLogin.STUDIO,
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
    var currentTab by rememberSaveable { mutableStateOf(ShellTab.CHAT) }
    // 三个 WebView 实例常驻，切换 Tab 只改可见性不销毁 → 登录态/页面状态全保留
    val webViews = remember { mutableMapOf<ShellTab, WebView>() }
    var loadingTab by remember { mutableStateOf<ShellTab?>(null) }

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
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: Context,
    url: String,
    autoLogin: SiteCredential?,
    onLoading: (Boolean) -> Unit,
): WebView {
    return WebView(context).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
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
                // 壳内自动登录：页面加载完检查 token，未登录则注入 (Studio/alist 通用)
                autoLogin?.let { cred -> view?.let { AutoLogin.ensureLoggedIn(it, cred) } }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                super.onReceivedError(view, request, error)
            }
        }
        webChromeClient = WebChromeClient()
        loadUrl(url)
    }
}
