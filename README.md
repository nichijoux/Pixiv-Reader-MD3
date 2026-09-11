# PixivReader

<p align="center">
  <em>非官方 Pixiv 安卓客户端</em>
</p>

**[English](./README-en.md) | 简体中文**

> 🌐 **在线发布页**：<https://nichijoux.github.io/Pixiv-Reader-MD3/> —— APK 下载、安装方法与多端界面预览。

> ⚠️ 本项目为**非官方**第三方客户端，与 Pixiv Inc. 无关。所有插画、漫画、小说作品版权归原作者或 Pixiv 所有。使用本应用产生的账号风险（如 Pixiv 对第三方客户端的风控、OAuth 登录限制等）由使用者自行承担，请遵守 Pixiv 服务条款。

## 功能

- **插画 / 漫画**：瀑布流浏览、作品详情、大图缩放、ugoira 动态插画播放与导出（**MP4 视频 / ZIP 帧包**）、排行榜（日 / 周 / 月 / 男性向 / 女性向 / 新人 / R-18 滑动切段 + 触底分页）。
- **小说**：在线阅读（阅读进度记忆）、小说排行榜、本地 **TXT / EPUB / Markdown** 导入阅读、导出（PDF / TXT）。
- **发现 / 生态**：插画 / 小说检索与热门标签、pixivision 特辑，画师榜 / AI 榜 / 年代榜 / 壁纸榜；FANBOX / COMIC / 私信统一跳转系统浏览器。
- **追更**：小说 / 漫画系列追更、系列详情页、追更管理（小说 / 漫画分段 + 行内取消）。
- **评论**：文本评论 + 文字表情 + pixiv 贴纸（stamp），可删除自己的评论。
- **历史 / 稍后再看 / 屏蔽**：浏览历史与搜索历史；卡片长按加入稍后再看（管理页支持作品 / 小说分段与一键清空）；卡片长按屏蔽作品（整卡模糊遮罩 + 屏蔽管理页）。
- **下载**：插画 / 分页下载，WorkManager 后台任务、进度跟踪、完成通知、断网排队与失败重试。
- **收藏 / 关注 / 书签**：收藏编辑器支持公开 / 私密与标签编辑。
- **个性化**：深色模式 + 动态取色（Material 3）、应用内 **简体中文 / 繁体中文 / English** 语言切换、字体缩放。
- **其他**：首页信息流快照（冷启动秒开、断网可离线浏览）、应用内更新检查（GitHub Releases + changelog 渲染）、`pixiv://` 深链。

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Kotlin 2.4 |
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

- **环境要求**：Android 8.0（API 26）及以上；JDK 21；Gradle 9.7.1（wrapper）。
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

- 受 **[Pixiv-Shaft](https://github.com/CeuiLiSA/Pixiv-Shaft)（GPL-2.0）** 及 pixiv 客户端生态启发，部分 API 封装/登录思路源自/参考该生态。
- 第三方依赖各自遵循其许可证：`lib:pixivapi`（vendor 的 pixiv API 封装，上游源码）、`pixiv-login`、Room、MMKV、Coil、PDFBox 等。

## License

本项目遵循 **GPL-2.0**（见 [LICENSE](./LICENSE)）。
第三方依赖各自遵循其许可证，发布前请核实 `lib:pixivapi`（vendor 的 pixiv API 封装）与 `pixiv-login` 的条款。

---

*本项目仅供学习交流。请尊重作者与 Pixiv 的权益，勿用于商业用途。*
