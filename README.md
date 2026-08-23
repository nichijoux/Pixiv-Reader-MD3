# PixivReader

<p align="center">
  <em>非官方 Pixiv 安卓客户端</em>
</p>

**[English](./README-en.md) | 简体中文**

> ⚠️ 本项目为**非官方**第三方客户端，与 Pixiv Inc. 无关。所有插画、漫画、小说作品版权归原作者或 Pixiv 所有。使用本应用产生的账号风险（如 Pixiv 对第三方客户端的风控、OAuth 登录限制等）由使用者自行承担，请遵守 Pixiv 服务条款。

## 功能

- **插画 / 漫画**：瀑布流浏览、作品详情、大图缩放、ugoira 动态插画播放、排行榜（滑动切段 + 触底分页）。
- **小说**：在线阅读、小说排行榜、本地 **TXT / EPUB / Markdown** 导入阅读、导出（PDF / TXT）。
- **评论**：文本评论 + 文字表情 + pixiv 贴纸（stamp）。
- **搜索**：插画 / 小说搜索（跨 Tab 传入搜索词）。
- **历史**：浏览历史、搜索历史。
- **下载**：插画 / 分页下载，WorkManager 后台任务、进度跟踪、完成通知。
- **收藏 / 关注 / 书架 / 书签**。
- **个性化**：深色模式 + 动态取色（Material 3）、应用内 **中 / 英** 语言切换。

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Kotlin 2.1 |
| UI | Jetpack Compose（Material 3） |
| 架构 | 单 Activity + Compose Navigation，MVVM |
| DI | Hilt |
| 数据 | Room、DataStore、MMKV（会话 token） |
| 网络 | Retrofit / OkHttp / Gson（`lib:pixivapi` 为 vendor 的 pixiv API 封装模块） |
| 后台 | WorkManager（下载、导出） |
| 解析 | jsoup；PDFBox（PDF 导出）；Android-OpenCC（简繁转换） |
| 图片 | Coil（自动 Referer） |

模块依赖（硬约束）：`app → feature/* → core/ui → core/network → core/database · datastore · model → core/common`，`feature` 之间禁止互相依赖，共享逻辑下沉 core。

## 构建

- **环境要求**：Android 8.0（API 26）及以上；JDK 21；Gradle 8.14.3（wrapper）。
- 命令行构建（Windows，无 Android Studio）：

```powershell
# 设置 JDK（按本机路径修改）
$env:JAVA_HOME = "C:\Users\<user>\.jdks\jbr-21.0.11"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# 快速编译（推荐验证手段）
.\gradlew.bat :app:compileDebugKotlin --console=plain

# 构建 Debug APK
.\gradlew.bat :app:assembleDebug --console=plain

# 单测（改动涉及模块时）
.\gradlew.bat :core:novel:testDebugUnitTest :core:network:testDebugUnitTest --console=plain
```

## 发布

推 `v*` 版本 tag（如 `v1.2.3`）时 GitHub Actions 自动构建 Release APK，并按 **ABI 拆分**为 `arm64-v8a`（ARMv8）与 `armeabi-v7a`（其它 ARM）两个瘦身包，上传到 GitHub Releases。Release 签名需在仓库 Secrets 配置 `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`（未配置则产出未签名 APK）。

## 致谢 / Attribution

- 受 **[Pixiv-Shaft](https://github.com/CeuiLiSA/Pixiv-Shaft)（MIT）** 及 pixiv 客户端生态启发，部分 API 封装/登录思路源自/参考该生态。
- 第三方依赖各自遵循其许可证：`lib:pixivapi`（vendor 的 pixiv API 封装，上游源码）、`pixiv-login`、Room、MMKV、Coil、PDFBox 等。

## License

项目**尚未**添加开源许可证（License 待定）。发布前请：
1. 核实 `lib/pixivapi`（vendor 的 pixiv API 封装）与 `pixiv-login` 的许可证；
2. 若为宽松许可（MIT/Apache-2.0），可选用 MIT；若无法确认，建议 GPL-3.0 以兼容任一上游。

---

*本项目仅供学习交流。请尊重作者与 Pixiv 的权益，勿用于商业用途。*
