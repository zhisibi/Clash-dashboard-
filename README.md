# Clash 面板（Zashboard WebView 版）

一个轻量的安卓 App，把开源面板 [Zashboard](https://github.com/Zephyruso/zashboard) 打包进 APK，用来管理 **mihomo / Clash** 的 `external-controller` API。

> App **不包含 mihomo 内核**，需要配合已运行的 mihomo / Clash 客户端使用（如 FlClash、Clash Meta for Android、OpenClash 等）。
> 如需原生实现，见 Kotlin 版：[Clash-dashboard-cotlin](https://github.com/zhisibi/Clash-dashboard-cotlin)。

## 特点

- 内置 Zashboard 静态资源（`app/assets/dist`，2026-09-23 版本），**离线可打开**
- App 内启动本地静态服务 `127.0.0.1:27900` 提供页面，WebView 加载，兼容 Zashboard 全部功能
- 支持明文 HTTP 后端（如 `127.0.0.1:9090`）
- 日志、配置导出保存到系统“下载”目录
- 无第三方依赖，APK 体积小

## 使用

1. 在代理客户端中开启外部控制器（external-controller），记下地址、端口和密钥
2. 安装 APK（需允许“安装未知来源应用”）
3. 首次打开，在 Zashboard 的后端设置里填写 `127.0.0.1:9090` 和密钥

## 项目结构

```
app/
├── AndroidManifest.xml
├── assets/dist/           # Zashboard 构建产物
├── res/                   # 图标、主题
└── src/net/zash/panel/
    ├── MainActivity.java  # WebView 容器、下载/导出处理
    └── LocalServer.java   # 本地静态文件服务
build.sh                   # 构建脚本
```

## 构建

不使用 Gradle，直接调用 Android 构建工具：

依赖：JDK 11+、`aapt`、`zipalign`、`apksigner`，以及放在 `tools/` 下的：
- `tools/android-34/android.jar`
- `tools/r8.jar`（提供 D8）

```bash
./build.sh
```

输出：`build/ClashPanel.apk`。首次构建会在 `tools/release.keystore` 生成签名（`tools/` 不提交到仓库）。

> 升级安装必须使用同一个签名文件，否则无法覆盖安装。

## 更新 Zashboard

从 Zashboard 的 Releases 下载 `dist.zip`，解压覆盖 `app/assets/dist/` 后重新构建即可。

## 许可

Zashboard 及其依赖的许可见 `app/assets/dist/THIRD_PARTY_NOTICES.md`。
