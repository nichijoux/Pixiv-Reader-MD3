# App 混淆规则（R8）
# 基础规则来自 proguard-android-optimize.txt（native 方法名、View 构造器等系统规则）；
# Hilt/Room/Compose/OkHttp/Retrofit/Gson(2.11+) 等库自带 consumer 规则，无需在此手写。

# Gson 反序列化依赖字段上的运行时注解（@SerializedName 等）保留
-keepattributes *Annotation*

# ── Gson 反射模型 ────────────────────────────────────────────────
# lib:pixivapi 模型大量经 Gson 反射序列化：payloadJson（历史/下载/稍后再看/Feed 快照、
# 动图/小说导出断点）、MMKV 会话（AccountResponse）。字段名即 JSON 键，
# 一旦混淆重命名，反序列化拿到全 null（Gson UnsafeAllocator 兜底还会直接 NPE）。
-keep class com.pixiv.api.model.** { *; }

# core:common 卡片快照数据：历史/稍后再看/下载 payloadJson 的小说卡（NovelCardData）
-keep class com.pixiv.reader.core.common.model.** { *; }

# ── 第三方可选路径（编译期可见、运行时不会触达，仅消除 R8 missing-class 报错）──
# pdfbox 的 JPEG2000 滤镜（JPXFilter）引用的可选解码库，项目未打包：
# 导出 PDF 的插图只有 jpg/webp→jpg，不会触达 JPX 解码路径
-dontwarn com.gemalto.jp2.**
# pdfbox/fontbox 对 javax.xml / AWT 残留引用
-dontwarn javax.xml.**
-dontwarn java.awt.**
