# 小猫影视源播放器 Android

这是一个可直接导入 Android Studio 的安卓工程，用 `WebView + Android Bridge` 方式执行 `xiaopengwl/xiaomaojs` 仓库里的 `drpy/t3-js` 影视源。

播放器层已经切到 `ArtPlayer 5.4.0`，并内置：

- `hls.js 1.6.16`：用于 `.m3u8` 播放
- `crypto-js 4.2.0`：用于运行依赖 `CryptoJS` 的源

## 已实现

- 内置读取仓库中的 `.js` 影视源
- 首页推荐、分类、搜索、详情、选集
- 支持执行 `推荐 / 一级 / 搜索 / 二级 / lazy / 预处理` 这类 `js:` 规则
- 支持常见选择器规则和对象型二级规则
- 内置源管理，可粘贴自定义 `var rule = {...}` 保存到本地
- 内置 `ArtPlayer` 播放器，支持 HLS
- 保留原生播放器和外部播放器兜底
- 首页和播放层已做成科技感运营面板风格 UI
- 现已补成更像流媒体产品的移动端结构：顶部频道导航 + 固定底栏 + 首页推荐流 + 发现页 + 搜索页 + 源库页 + 我的页

## 目录

- `app/src/main/assets/sources`：内置影视源
- `app/src/main/assets/web`：应用前端页面和规则执行逻辑
- `app/src/main/assets/web/vendor`：内置播放器和运行时依赖
- `app/src/main/java/com/xiaomao/player`：Android 容器、网络桥、播放器

## 打开方式

1. 用 Android Studio 打开 [xiaomao-android-app](D:/项目/xiaomao-android-app)。
2. 等待 Gradle 同步。
3. 连接安卓手机或启动模拟器后运行。

## 在线打包 APK

仓库里已经附带 GitHub Actions：

1. 把 `xiaomao-android-app` 目录本身作为仓库根目录上传，或者把整个工作区一起推到 GitHub 都可以。
2. 打开 `Actions`。
3. 运行 `Android Debug APK`。
4. 在 `Artifacts` 下载 `app-debug-apk`。

## 当前限制

- 当前环境没有 Android SDK / Gradle，所以我没法在本机直接编译 APK。
- 某些依赖更完整 drpy 运行时的源，仍可能需要继续补更多内置 API、站点特定兼容逻辑或请求代理能力。
- 仓库 README 提到的旧版原生 `app` 目录并不在当前主分支里，所以这里采用的是新的安卓壳实现。
