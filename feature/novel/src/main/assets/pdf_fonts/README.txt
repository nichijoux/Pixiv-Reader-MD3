本目录字体用于小说 PDF 导出的排版渲染（feature:novel NovelExporter.loadPdfFonts）。

DroidSansFallbackFull.ttf（主中文字体）
- 来源：AOSP（Android Open Source Project）frameworks/base data/fonts/DroidSansFallbackFull.ttf
  （android-8.1.0_r1 标签），https://android.googlesource.com/platform/frameworks/base
- 许可：Apache License 2.0（见 https://www.apache.org/licenses/LICENSE-2.0）
- 字体版权：Ascender Corporation / Google Inc.，详见 AOSP frameworks/base NOTICE 文件
- 选择原因：TrueType（glyf）轮廓，pdfbox-android 2.0.27 仅支持嵌入 glyf 字体
  （系统 CJK 字体均为 CFF/OTF 轮廓，嵌入会抛 "OTF fonts do not have a glyf table"）；
  覆盖简繁中文、日文假名、谚文及常用标点。JVM 单测验证 pdfbox 可正确解析其 cmap
  （PdfFontTest：glyf 表存在、中文/标点 getGlyphId 非 0）。

DejaVuSans.ttf（符号补充字体）
- 来源：DejaVu Fonts 2.37 官方发布（dejavu-fonts-ttf-2.37），https://dejavu-fonts.github.io/
- 许可：双许可（Bitstream Vera License / Public Domain），可自由再分发
- 作用：覆盖 CJK 字体缺失的 Dingbats 等符号区字形（❤ U+2764、★ U+2605 等），
  渲染时按字符自动选用（多字体渲染：第一个能编码该字符的字体）。

为什么不用系统字体：Android 系统 CJK 字体全部为 CFF/OTF 轮廓，pdfbox-android 2.0.27
只支持嵌入 TrueType（glyf）轮廓（子集化与完整嵌入路径均读 glyf 表，CFF 会在 save 阶段
抛 "OTF fonts do not have a glyf table"）；系统符号字体（如 NotoSansSymbols）存在时仍会
被自动发现并补充（见 PdfFonts.systemSymbolCandidates，CFF 轮廓经 hasCffTable 过滤）。
