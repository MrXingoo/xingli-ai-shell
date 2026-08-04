# 星黎AI 套壳版 (xingli-ai-shell)

星黎AI 的手机 APP —— 纯 WebView 套壳方案（v1.1.0）。

## 方案

师兄拍板：不重写前端，直接把现成 Web 服务套进原生壳。

| Tab | 内容 | 地址 |
|---|---|---|
| 会话 | Hermes Studio 会话页 | https://ai.mgaoxin.com/#/hermes/chat |
| 书房 | AList 文件管理 | https://study.mgaoxin.com/files/ |
| 配置 | Hermes Studio 设置页 | https://ai.mgaoxin.com/#/hermes/settings |

## 技术要点

- Kotlin + Jetpack Compose + Android WebView，无业务逻辑，纯壳
- 三个 WebView 实例常驻，Tab 切换只改可见性（zIndex），**登录态/页面状态全保留**
- 登录态持久化：Cookie + localStorage 默认落盘（非 incognito）
- 返回键：WebView 内后退优先，无历史则退后台（不杀会话）
- 星黎主题：蓝橙二次元配色，跟随系统深浅色
- 正式签名：xingli-ai-release.jks（与 hermes-mobile 原生版同证书，可共存升级）

## 构建

```bash
export ANDROID_HOME=/home/ubuntu/android-sdk
export KEYSTORE_PATH=/home/ubuntu/.hermes/xingli-ai-release.jks
export KEYSTORE_PASSWORD=... KEY_ALIAS=xingli-ai KEY_PASSWORD=...
./gradlew assembleRelease -PversionName=1.1.0 -PversionCode=2
```

## 下载

https://study.mgaoxin.com/dl/xingli-ai-shell-v1.1.0.apk
