### 新增
- 阅读器平板双页显示 + 折页式仿真翻页
- 阅读器正文插图点击全屏查看
- 阅读器底栏进度胶囊快速跳页 + 上/下一章圆钮
- 个人主页头部可折叠：下滑收起头像/简介/统计行，分区 Tab 钉住顶栏

### 优化
- 主题层迁移 Material 3 Expressive（MaterialExpressiveTheme + Expressive 形状/动效体系）
- 组件 Expressive 化：加载/进度指示器（LoadingIndicator + 波浪进度条）、查看器/阅读器浮动工具栏 + 章节圆钮、我的页与筛选三选一 SegmentedButton、详情页关注 pill 按钮、设置页 28dp 分组面板、ConfirmDialog/搜索框/浮钮弹性按压
- 排行榜前三名次徽标与引导页/登录页图标采用 MaterialShapes 有机形状 + 入场弹性
- 排行榜入口卡图标改圆形底；榜单加载骨架对齐真实瀑布流错落布局 + 1s 呼吸脉冲
- 全项目 deprecated API 用法与未使用 import 清理

### 修复
- 手机端仿真向前翻重写为书脊卷角，阅读器沉浸式覆盖状态栏
- 仿真翻页正面补铺纸底 + 背面文字随折叠即时露出
- 阅读器工具行恢复全宽沉浸条样式（Expressive 仅保留章节圆钮弹性按压）
- 语言/排序下拉菜单锚定行尾值区（右侧展开而非整行左缘）
- 个人主页折叠区收窄为简介+统计行（头像/名称/关注·拉黑行常驻）

### 依赖
- material3 单独跟随 1.5.0-alpha27 线（Material 3 Expressive 完整 API：motionScheme/LoadingIndicator/WavyProgress/FloatingToolbar）
