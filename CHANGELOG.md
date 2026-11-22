### 新增
- 彩色风格开关与 M3 扩展语义色板：外观设置新增「彩色风格」开关（默认关闭 = 纯 M3 单色，关闭时所有点缀统一回退 `primary`）；新增 Custom Color Roles 承载业务语义色（收藏红、成功绿、通知反色容器上的成功/错误色、七色设置点缀），浅色取 tone-40、深色取 tone-80 双档并随主题自动切换；新增对比度单测锁住双档色值
- 我的页账号分组：头像 + 用户名 + `@account` 与退出登录收编为与其它设置完全一致的分组卡（28dp extraLarge 圆角、组内无分割线），整行按压缩放与 SELECT 触觉，未登录/资料未就绪时不显示跳转箭头
- M3 Expressive 交互细化：底部导航选中图标交叉淡变 + 弹起缩放、指示器跨项滑动（自绘 SlidingIndicatorNavigationBar）、胶囊分段滑块位移、卡片按压缩放、收藏红心过冲弹跳，并接入四档语义触觉反馈（同档 100ms 节流）
- 底部导航彩色体系：五个 Tab 各配一色（首页蓝 / 作品紫 / 小说青 / 关注粉 / 我的绿），手机 NavigationBar 与平板 WideNavigationRail 的选中图标、标签、指示器胶囊同步取色

### 优化
- 设置页与全局控件视觉统一：分组卡去分割线改纯留白分层、前置图标改 40dp 圆角方容器 + 同色淡底徽标、下拉菜单统一 16dp 圆角与 `surfaceContainerHigh`、14 处分段控件统一胶囊样式
- 我的页表面层级回归 MD3：移除主题色派生渐变背景，分组卡由 92% alpha 改为不透明 `surfaceContainer`（动态取色 / 深色下对比度不再随壁纸漂移）
- 按 M3 规范重建色体系：补真实 `secondary` / `secondaryContainer` 浅深两套 tone，删除此前「secondary 镜像 primary」的临时规避，FilterChip / Switch 轨道 / 导航指示器恢复 M3 弱强调语义层次
- 字阶与形状口径收敛：补齐 M3 全部 15 个 type role，清除 16 处裸 `fontSize`（含低于最小字阶的 9.5sp / 10sp）；圆角统一走 `AppShapes` / `MaterialTheme.shapes`，不再借用间距 token
- 图片叠加层统一 `ImageScrim`（黑 70%）：角标、底部信息条、追更与下载卡浮层文字对比度最坏情况由 3.3:1 提升到 8.6:1
- 无障碍与触控目标：胶囊分段视觉仍 40dp 但触控扩到 48dp、语义改 `selectable` + `Role.RadioButton` 并补 state layer；下载卡封面浮钮从 28dp 可点改为 48dp 热区 + `Role.Button` + ripple
- 详情页统计块字号与图标整体放大一档（图标 16→20dp、数值 titleSmall→titleMedium、标签 labelMedium→bodyMedium）
- 阅读器与查看器体验：进度恢复不再被 0 锁死、离场进度串行落库、重排按字符锚点重定位、自定义字体导入移出主线程并原子替换；壁纸按目标尺寸采样解码
- 列表三态与骨架：分页失败可重试、空页自动续载（上限 10 页）、榜单 0 条走空态不再无限骨架

### 修复
- COMIC 阅读链四处叠加必崩：去扰改用可变位图绘制（原不可变位图交给 Canvas 每页必抛异常）、首页 / 详情加载标志改由请求内部置放（原先初始 `isLoading=true` 命中早退守卫导致永久骨架）、viewer salt 改 `JsonParser` 逐层取值（原先反射解析私有类，R8 后 Release 恒为 null）、COMIC 客户端加限定符并只在 `comic.pixiv.net` 注入站点头（Cookie 不再发往 CDN）
- 列表闪退：`PagedState` 新增 `keySelector` 去重并在榜单 / 收藏 / 搜索 / 关注 / 评论 / FANBOX 等 15+ 分页源接线，跨页重复 ID 不再触发「Key was already used」
- 下载与导出：三个 Worker 与 `ProgressDownloader`、首屏快照一律透传 `CancellationException`（删除任务后不再被写回 failed 复活、取消时不再删合法快照）；下载失败保留原卡片快照；动图 `MP4` 中间帧包改用独立文件名（不再误删已导出的 ZIP）并按 1% 节流写库
- 小说导出：插图下载失败改 null 占位保持全局块序（修复全书插图错位）、补 WebP 尺寸解析、EPUB 保留真实扩展名与 MIME、DOCX `Content_Types` 元素顺序、章节缓存保留作者 ID、部分分册重试不再退化成整系列
- 会话与安全：新增 `SessionEvents` 使 refresh_token 吊销后 UI 同步登出（原先永久卡在已登录）；会话管理器加锁且已登出不复活；登出清理 WebView Cookie（切号不再串 FANBOX 会话）；禁用明文 HTTP、补数据迁移排除规则、WebView 域名白名单与文件访问关闭
- 收藏 / 关注全局同步：进程级覆盖流驱动卡片、详情、阅读器、用户主页、追更页双向同步，修复详情操作后列表不同步、平板双栏相反状态、卡片复用回弹；收藏页首屏重试绑定修正、关注页旧请求不再清空新画师数据、触底按当前 Tab 分页
- 阅读器：本地文档不再先发无效网络请求、平板小说导出接线、跳转同一位置失效、手势陈旧闭包、页码越界
- 发现 / FANBOX / 评论：搜索与热门请求可取消并按代次丢弃旧结果、用户搜索支持翻页、`{N}users入り` 不再误拼到用户搜索、字符数 / 时长筛选弹输入框、字号滑杆可回到 100%、FANBOX 错误体只读一次、评论切目标重置与发送防抖
- 启动与深链：组合期同步读盘改为只读一次、导航起点固定（登录 / 登出 / 引导完成走清栈切换）、DataStore 损坏自愈、单实例 + `handleDeepLink` 分流、夜间深色启动不再白屏
- i18n：补齐 `card_msg_blocked`、`notification_dismiss` 等三语文案，通知条重复提示重新计时
