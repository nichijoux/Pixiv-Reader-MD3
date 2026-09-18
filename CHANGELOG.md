### 新增
- 原生 FANBOX 支持：投稿流 + 推荐创作者双页签、帖子详情异构列表（正文/赞助方案/评论楼中楼）、内置 WebView 登录（走完整 SSO 回调、登录模式零拦截防凭证落浏览器、完成后自动回原生），平板 Master-Detail 详情右栏；post.info 被 CF 封锁改经无屏 WebView 桥（懒建/超时/子资源拦截/空闲释放）获取正文、失败退元数据兜底，401 过期引导重登并在返回时自动重试
- pixiv COMIC 原生阅读：首页更新/排行双页签、作品详情、沉浸式竖向阅读器（单页重试）、搜索；阅读两步流经 viewer 页提取随机 salt 生成 X-Client-Hash 签名调 read_v4，正文 CDN 图按 XorShift128+ gridshuffle 逐行置换去扰后落盘缓存，WAF 头三件套拦截器绕过 403；免费内容免登录直达，付费章节锁定提示
- 卡片标签点击按类型跳转搜索：作品标签搜作品、小说标签搜小说，IllustCard 新增紧凑标签行（每标签独立可点，最多 3 个 + N），小说卡标签跨页接线；跨 Tab 搜索与深链通道带类型标记
- 平板侧栏升级 Expressive WideNavigationRail；底部导航再次点击回顶

### 优化
- 新增 core:ranking / core:comment 领域模块，全库约 60 处重复收敛为共享设施（排行榜三胞胎、IllustCard 重复卡、三态视图、toggle 开关等归位）
- 互斥模式 Boolean 全面枚举化（11 处：CardMaskState / CommentTarget / WatchlistSegment / DownloadStatus / ExportFormat / AiFilter / R18Filter / DownloadBadge / BookmarkPrivacy / FeedPhase 等），消除布尔组合状态机
- 标签类型 bool 改 TagType 枚举，搜索链路按枚举传递便于扩展

### 修复
- 小说 Tab 再次点击回顶失效（listState 创建后未挂到 LazyColumn，回顶信号滚动的是脱缰状态）
- 排行右栏收藏 id 与日期时区两处错误
- 平板端小说 Tab 页签改等宽居中（对齐排行页约定）
