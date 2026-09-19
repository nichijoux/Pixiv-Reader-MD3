### 新增
- 小说系列页追更按钮：信息头改「追更 + 下载」双按钮行，追更态由系列详情 watchlist_added 初始化、乐观防连点、失败回滚，全屏路由与平板右栏 pane 共享

### 优化
- 「状态 + 请求进行中」布尔对全面收敛为 ToggleUiState 四态状态机（15 对 / 7 个 ViewModel）：单一流承载状态与防连点，进行中保留旧文案禁用按钮、失败回滚；MessageViewModel 新增 runToggle 骨架（支持按失败种类分文案，如 CSRF 不可用专用提示），UserViewModel 关注/拉黑手写样板收编，删除旧 runOptimisticToggle 骨架
- 死代码清理：ReadingProgressDao 三个零调用方法、COMIC 页缓存清理与本地屏蔽清空等零调用方法、图片质量设置死链（从未生效）、BookmarkEditor 只写不读状态、仅测试使用的 reflectAcrossSpine 内联进测试
- 清理 40 组死字符串资源（×3 语言）：旧筛选面板、旧加载失败文案、排行预览副本、被 reader_msg 取代的追更提示等重构孤儿

### 修复
- 收藏编辑器弹层确认按钮保存中不禁用（BookmarkEditor._saving 从未写入恒为 false）——改由 ViewModel 侧真实保存标志驱动
