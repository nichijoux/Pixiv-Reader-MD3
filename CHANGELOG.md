### 新增
- 小说详情标签点击搜索：小说卡/作品卡/详情页标签区统一收敛为 TagChip 组件，点击标签按类型直达对应搜索（onSearchTag 全链穿线，返回可回到原详情）；顺带修复 +N 折叠 chip 空白区域可点击的问题
- APK 新增 x86_64 ABI 架构支持

### 优化
- Compose 重组性能优化：新增 compose_stability.conf 稳定性配置并在 21 个模块统一接入；Room DAO 新增 DownloadStatusRow/ReadLaterTargetRow 轻量投影，下载完成通知器/导出复位/稍后再看订阅避开 payloadJson 大列；历史/稍后再看/下载列表 payloadJson 反序列化结果记忆 + 共享 Gson 实例；RankingList 过滤结果 filterKey 缓存 + 列表稳定 key；新组件 SegmentedButtonLabel 单行缩字标签（阅读器设置/我的页分段接入）
- main?search 深链内层起点直达发现页，修复返回时先落回首页的问题

### 修复
- 标签搜索深链首屏空白：search 挂起等待 DataStore 筛选恢复完成（filtersRestored 信号，消除非会员降级写回被恢复值覆盖导致的渲染分支/数据落点脱节），结果页 pager 补 initialPage 跟随当前类型（小说标签深链直落小说栏，消除首组合类型翻转与重复搜索）
- 热门预览搜索在途先显示骨架屏：新增 popularIllustsLoading/popularNovelsLoading 在途标记，无数据且请求中先渲染骨架，消除先闪「暂无热门作品」再出结果的观感
- 请求签名时间格式线程安全：SimpleDateFormat 改 DateTimeFormatter，UTC 偏移 +09:00 与参考实现一致
- 首页/小说推荐流下拉刷新指示器提前消失
- 评论路由页死代码触底加载
- 系列缓存并发竞态（LAZY 注册 + 条件移除）
- 误启破坏性迁移回退
- 查看器标题不随数据更新
