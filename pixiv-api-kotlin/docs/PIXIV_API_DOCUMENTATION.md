# Pixiv API 接口文档

> 本文档基于对 [Pixiv-Shaft](https://github.com/CeuiLiSA/Pixiv-Shaft) 项目源码的完整分析整理而成。
> 覆盖官方 app-api、网页 ajax 接口、OAuth 登录、以及 Shaft 自建服务等全部 API 面。
> 用于指导新 Pixiv Android 客户端开发。

---

## 目录

1. [总览](#1-总览)
2. [OAuth 登录鉴权](#2-oauth-登录鉴权)
3. [App API（app-api.pixiv.net）](#3-app-apiapp-apipixivnet)
   - 3.1 [作品/插画](#31-作品插画)
   - 3.2 [排行榜](#32-排行榜)
   - 3.3 [推荐](#33-推荐)
   - 3.4 [搜索](#34-搜索)
   - 3.5 [用户](#35-用户)
   - 3.6 [收藏/书签](#36-收藏书签)
   - 3.7 [关注](#37-关注)
   - 3.8 [评论](#38-评论)
   - 3.9 [小说](#39-小说)
   - 3.10 [系列](#310-系列)
   - 3.11 [追更/动态/通知](#311-追更动态通知)
   - 3.12 [GIF 动图](#312-gif-动图)
   - 3.13 [其他](#313-其他)
4. [Web API（www.pixiv.net）](#4-web-apiwwwpixivnet)
5. [图片 CDN（i.pximg.net）](#5-图片-cdnipximgnet)
6. [Shaft 自建服务](#6-shaft-自建服务)
7. [请求头与签名](#7-请求头与签名)
8. [分页机制](#8-分页机制)
9. [错误处理](#9-错误处理)

---

## 1. 总览

| 服务 | Base URL | 鉴权 | 用途 |
|------|----------|------|------|
| App API | `https://app-api.pixiv.net/` | `Authorization: Bearer <access_token>` | 官方 App 数据接口（插画/小说/用户/收藏…） |
| OAuth | `https://oauth.secure.pixiv.net/` | OAuth Client | 换取/刷新 access_token |
| 网页 API | `https://www.pixiv.net/` | Cookie | 网页端专属功能（搜索详情、tag 筛选、拉黑、首页等） |
| 图片 CDN | `https://i.pximg.net/` | 需 `Referer` 头 | 图片资源 |
| 账户中心 | `https://accounts.pixiv.net/` | OAuth token | 改密码/邮箱/pixiv id |
| Shaft 事件服务 | `BuildConfig.SHAFT_EVENTS_BASE_URL` | HMAC | 站长推荐/广场/事件记录 |
| Shaft 历史服务 | `https://pixshaft.com/` | 无 | 浏览历史云同步 |
| Moon API | `https://shaft.api:8443/`（自定义 DNS） | 无 | 设置同步 |
| GitHub API | `https://api.github.com/` | 无 | 版本更新检查 |
| jsDelivr | `https://cdn.jsdelivr.net/` | 无 | 远程资源 |

### 客户端身份（关键！）

Shaft 使用 **iOS 官方客户端** 的抓包身份调用 app-api（新版 Kotlin 侧）：

```
User-Agent: PixivIOSApp/8.6.10 (iOS 26.5; iPhone16,2)
app-os: ios
app-os-version: 26.5
app-version: 8.6.10
```

旧版 Java 侧用 Android 身份：

```
User-Agent: PixivAndroidApp/5.0.234 (Android <SDK版本>; <机型>)
```

> ⚠️ 新开发客户端建议直接采用 iOS 身份（`filter=for_ios` 能拿到更多字段，如 `is_accept_request`、`badge`、`disabled_links`），与 Shaft 新版保持一致。

---

## 2. OAuth 登录鉴权

### 2.1 Token 端点

```
POST https://oauth.secure.pixiv.net/auth/token
Content-Type: application/x-www-form-urlencoded
```

| 参数 | 说明 |
|------|------|
| `client_id` | OAuth 客户端 ID（Shall 使用 Pixiv Android 官方客户端 ID，见源码 `PixivOAuthConfig.PIXIV_ANDROID`） |
| `client_secret` | OAuth 客户端密钥 |
| `grant_type` | `refresh_token` / `authorization_code` |
| `refresh_token` | 刷新令牌（refresh 时必填） |
| `code` | 授权码（authorization_code 时必填） |
| `code_verifier` | PKCE 验证码（authorization_code 时必填） |
| `include_policy` | `true` |

**响应（AccountResponse / UserModel）：**

```json
{
  "access_token": "...",
  "expires_in": 3600,
  "refresh_token": "...",
  "token_type": "bearer",
  "scope": "...",
  "user": { "id": 31660292, "name": "...", "account": "...", "is_premium": false, ... }
}
```

### 2.2 PKCE 登录流程（推荐）

Shaft 新版使用 PKCE（`com.github.SoxiaLiSA:pixiv-login` 库）：

1. 生成 `code_verifier` + `code_challenge`（SHA-256）
2. 打开登录页 `https://app-api.pixiv.net/web/v1/login?code_challenge=...&code_challenge_method=S256&client=pixiv-android`（Shaft 封装在 `PixivLogin.startLoginUrl()`）
3. 用户授权后回调 URI 带 `code` 与 `state`
4. 用 `code` + `code_verifier` 交换 access_token + refresh_token
5. 之后全部请求带 `Authorization: Bearer <access_token>`

### 2.3 Token 自动刷新

- access_token 约 1 小时过期
- 检测到 HTTP 400 且响应体包含 `"Error occurred at the OAuth process"` → 用 refresh_token 换新 token 并重放原请求
- 响应体包含 `"Invalid refresh token"` → refresh_token 已吊销，强制登出
- 刷新期间需同步锁，避免并发重复刷新

### 2.4 账户编辑（accounts.pixiv.net）

```
POST https://accounts.pixiv.net/api/v2/account/edit
Authorization: Bearer <access_token>
```

| 字段 | 说明 |
|------|------|
| `new_mail_address` | 新邮箱 |
| `new_user_account` | 新 pixiv id |
| `current_password` | 当前密码（必填） |
| `new_password` | 新密码 |

---

## 3. App API（app-api.pixiv.net）

> 所有接口均需 `Authorization: Bearer <access_token>`（匿名公开接口除外）。
> 分页：响应体统一带 `next_url`，翻页直接请求该绝对 URL。

### 3.1 作品/插画

#### 获取作品详情
```
GET /v1/illust/detail?illust_id={id}&filter=for_android
→ IllustSearchResponse { illust: Illust }
```
Shaft 新版：`GET /v1/illust/detail?illust_id={id}` → `SingleIllustResponse`

#### 相关作品
```
GET /v2/illust/related?illust_id={id}&filter=for_android
→ ListIllust { illusts: [Illust], next_url }
```

#### 用户创作的作品
```
GET /v1/user/illusts?user_id={uid}&type={illust|manga|ugoira}&filter=for_ios&offset={int?}
→ IllustResponse { illusts, next_url }
```

#### 最新作品（全站）
```
GET /v1/illust/new?content_type={illust|manga}&filter=for_ios
→ IllustResponse
```

#### 用户收藏的插画
```
GET /v1/user/bookmarks/illust?user_id={uid}&restrict={public|private}&tag={tag?}&filter=for_ios
→ IllustResponse
```

#### GIF/动图元数据（Ugoira）
```
GET /v1/ugoira/metadata?illust_id={id}
→ GifResponse { illust_id, ugoira_metadata: { zip_urls: { medium }, frames: [{ file, delay }] } }
```

### 3.2 排行榜

```
GET /v1/illust/ranking?mode={mode}&date={yyyy-MM-dd?}&filter=for_ios
→ IllustResponse
```

| mode 取值 | 说明 |
|-----------|------|
| `day` | 每日 |
| `week` | 每周 |
| `month` | 每月 |
| `day_male` | 男性向每日 |
| `day_female` | 女性向每日 |
| `week_original` | 每周原创 |
| `week_rookie` | 每周新人 |
| `day_manga` | 每日漫画 |
| `day_r18` | 每日 R18 |
| `week_r18` | 每周 R18 |
| `day_r18g` | 每日 R18G |

小说排行榜：
```
GET /v1/novel/ranking?mode={mode}&date={yyyy-MM-dd?}&filter=for_ios
→ NovelResponse
```

### 3.3 推荐

```
GET /v1/illust/recommended?include_ranking_illusts={bool}&include_privacy_policy=true&filter=for_ios
→ HomeIllustResponse { illusts, ranking_illusts, next_url }
```

- `include_ranking_illusts=true`：首屏额外带横向排行榜预览 `ranking_illusts`
- 漫画：`GET /v1/manga/recommended?...`
- 小说：`GET /v1/novel/recommended?include_privacy_policy=true&filter=for_ios&include_ranking_novels=true` → `NovelRecommendResponse { novels, ranking_novels, next_url }`
- 通用路径（Shaft 动态 type）：`GET /v1/{type}/recommended?...`（type = illust/manga/novel）

#### 推荐用户
```
GET /v1/user/recommended?filter=for_ios
→ UserPreviewResponse { user_previews: [{ user, illusts, is_muted }], next_url }
```

#### 相关用户
```
GET /v1/user/related?seed_user_id={uid}&filter=for_android
→ UserPreviewResponse
```

#### 首页引导（新用户首次登录推荐）
```
GET /v1/walkthrough/illusts
→ IllustResponse
```

#### 热门标签
```
GET /v1/trending-tags/{type}?filter=for_ios
→ TrendingTagsResponse { trend_tags: [{ tag, translated_name, illust }], next_url }
```

#### Pixivision 特辑文章
```
GET /v1/spotlight/articles?category={category}&filter=for_ios
→ ArticlesResponse { spotlight_articles: [{ id, title, thumbnail, article_url, publish_date }], next_url }
```

### 3.4 搜索

#### 搜索插画/漫画
```
GET /v1/search/illust?filter=for_android&include_translated_tag_results=true&merge_plain_keyword_results=true
```

| 参数 | 说明 |
|------|------|
| `word` | 关键词 |
| `sort` | `date_desc`（最新）/ `date_asc`（最旧） |
| `search_target` | `exact_match_for_tags` 标签完全匹配 / `partial_match_for_tags` 标签部分匹配 / `title_and_caption` 标题或简介 |
| `start_date` / `end_date` | 时间范围 `yyyy-MM-dd` |
| `bookmark_num_min` | 最低收藏数（V3 筛选） |
| `tool` | 创作工具 |
| `search_ai_type` | AI 类型筛选（`0`=全部，Shaft 默认） |
| `ratio_pattern` | 宽高比筛选 |
| `content_type` | 内容类型 |
| `width_min/max`、`height_min/max` | 分辨率档位 |
| `lang` | 语言 |

> ⚠️ 不要传 `include_potential_violation_works=false` —— 会让 pixiv 隐藏"疑似违规"作品，导致部分关键词搜不到结果（Shaft issue #906）。

#### 搜索小说
```
GET /v1/search/novel?...&filter=for_android&include_translated_tag_results=true&merge_plain_keyword_results=true
```
小说特有的额外参数：
| 参数 | 说明 |
|------|------|
| `genre` | 题材 |
| `is_original_only` | 仅原创 |
| `is_replaceable_only` | 仅可替换 |
| `text_length_min/max` | 正文长度（字符） |
| `word_count_min/max` | 字数 |
| `reading_time_min/max` | 阅读时长（分钟） |

#### 热门结果预览（搜索页"热门作品"区）
```
GET /v1/search/popular-preview/illust?word=...&sort=...&search_target=...&...
GET /v1/search/popular-preview/novel?word=...&...
```

#### 搜索用户
```
GET /v1/search/user?word={keyword}&filter=for_ios
→ UserPreviewResponse
```

#### 搜索联想（自动补全）
```
GET /v2/search/autocomplete?word={keyword}&merge_plain_keyword_results=true
→ { tags: [{ name, translated_name }] }
```

#### 搜索选项（V3）
```
GET /v1/search/options?word={keyword}&search_target=partial_match_for_tags&...
→ SearchOptionsResponse
```

### 3.5 用户

#### 用户详情
```
GET /v2/user/detail?user_id={uid}&filter=for_ios
→ UserResponse { profile, profile_publicity, user, workspace, disabled_links }
```

> ⚠️ 老版 `v1/user/detail` 已被官方移除，Shaft 统一走 v2 + `filter=for_ios`。

#### 我自己的状态
```
GET /v1/user/me/state
→ SelfProfile { profile: User, user_state: KUserState }
```
`user_state` 字段：`is_mail_authorized` / `has_mail_address` / `has_changed_pixiv_id` / `can_change_pixiv_id` / `has_password` / `require_policy_agreement` / `no_login_method` / `is_user_restricted`

#### 用户关注列表
```
GET /v1/user/following?user_id={uid}&restrict={public|private}&filter=for_android&offset={int?}
→ UserPreviewResponse
```

#### 用户粉丝列表
```
GET /v1/user/follower?user_id={uid}&filter=for_ios
→ UserPreviewResponse
```

#### 好P友（互关）
```
GET /v1/user/mypixiv?user_id={uid}&filter=for_android
→ UserPreviewResponse
```

#### 关注状态详情
```
GET /v1/user/follow/detail?user_id={uid}
→ UserFollowDetail { followed: bool }
```

#### 约稿方案列表（开启接受约稿的画师）
```
GET /v1/user/request-plans?user_id={uid}
→ UserRequestPlansResponse
```

#### 用户收藏标签列表
```
GET /v1/user/bookmark-tags/illust?user_id={uid}&restrict={public|private}
→ { tags: [{ name, count }], next_url }
GET /v1/user/bookmark-tags/novel?user_id={uid}&restrict={public|private}
→ 同上
```

#### 个人资料编辑
```
POST /v1/user/profile/edit  (multipart)
POST /v1/user/workspace/edit  (form: fields)
GET  /v1/user/profile/presets
```

#### 屏蔽列表（账号级本地屏蔽）
```
GET /v1/mute/list
→ MutedHistory { mute_tags: [...], mute_users: [...] }
```

### 3.6 收藏/书签

#### 收藏插画（v2 支持标签）
```
POST /v2/illust/bookmark/add
字段：illust_id, restrict={public|private}, tags[]（可选）
```

#### 取消收藏插画
```
POST /v1/illust/bookmark/delete
字段：illust_id
```

#### 收藏小说 / 取消
```
POST /v2/novel/bookmark/add    字段：novel_id, restrict, tags[]（可选）
POST /v1/novel/bookmark/delete 字段：novel_id
```

#### 查看单个作品的收藏标签
```
GET /v2/illust/bookmark/detail?illust_id={id}
GET /v2/novel/bookmark/detail?novel_id={id}
→ { tags: [{ name, is_registered }] }
```

#### 收藏该作品的用户
```
GET /v1/illust/bookmark/users?illust_id={id}&filter=for_android
→ { user_previews, next_url }
```

#### 小说阅读书签（しおり/marker）
```
POST /v1/novel/marker/add    字段：novel_id, page（1-based）
POST /v1/novel/marker/delete 字段：novel_id
GET  /v2/novel/markers       → 全部阅读书签
```

### 3.7 关注

```
POST /v1/user/follow/add    字段：user_id, restrict={public|private}
POST /v1/user/follow/delete 字段：user_id
```

#### 关注用户的动态流（关注的新作品）
```
GET /v2/illust/follow?restrict={all|public|private}
→ IllustResponse
GET /v1/novel/follow?restrict={all|public|private}
→ NovelResponse
```

#### 好P友的作品流
```
GET /v2/illust/mypixiv
→ IllustResponse
GET /v1/novel/mypixiv
→ { novels, next_url }
```

### 3.8 评论

#### 获取评论（v3 带子回复）
```
GET /v3/illust/comments?illust_id={id}
GET /v3/novel/comments?novel_id={id}
→ CommentResponse { comments: [{ id, comment, date, user, has_replies, stamp }], next_url }
```

#### 回复列表
```
GET /v2/{type}/comment/replies?comment_id={id}    （type = illust/novel）
```

#### 发表评论
```
POST /v1/illust/comment/add
POST /v1/novel/comment/add
字段：illust_id/novel_id, comment, parent_comment_id（可选）, stamp_id（可选，发贴纸评论时 comment 留空）
→ { comment: {...} }
```

#### 删除评论
```
POST /v1/{type}/comment/delete
字段：comment_id
```

#### 评论贴纸目录
```
GET /v1/stamps
→ { stamps: [{ stamp_id, stamp_url }] }
```

### 3.9 小说

#### 小说详情
```
GET /v2/novel/detail?novel_id={id}
→ SingleNovelResponse { novel }
```

#### 小说正文（HTML，webview 接口）
```
GET /webview/v2/novel?id={id}
→ HTML/ResponseBody
```

#### 用户创作的小说
```
GET /v1/user/novels?user_id={uid}
→ NovelResponse
```

#### 用户收藏的小说
```
GET /v1/user/bookmarks/novel?user_id={uid}&restrict={public|private}&tag={tag?}
→ NovelResponse
```

#### 最新小说
```
GET /v1/novel/new
→ NovelResponse
```

### 3.10 系列

#### 小说系列
```
GET /v2/novel/series?series_id={id}&last_order={int?}
→ NovelSeriesResp { novel_series_detail, novel_series_first_novel, novel_series_latest_novel, novels, next_url }
```

#### 漫画系列
```
GET /v1/illust/series?illust_series_id={id}&last_order={int?}
→ IllustSeriesResp { illust_series_detail, illust_series_first_illust, illust_series_latest_illust, illusts, next_url }
```

#### 用户的系列列表
```
GET /v1/user/novel-series?user_id={uid}
GET /v1/user/illust-series?user_id={uid}
```

### 3.11 追更/动态/通知

#### 追更列表（漫画/小说系列）
```
GET /v1/watchlist/manga
GET /v1/watchlist/novel
→ WatchlistResponse { series: [{ id, title, url, latest_content_id, last_published_content_datetime, user, mask_text }], next_url }
```

#### 加入/取消追更
```
POST /v1/watchlist/manga/add    字段：series_id
POST /v1/watchlist/manga/delete 字段：series_id
POST /v1/watchlist/novel/add    字段：series_id
POST /v1/watchlist/novel/delete 字段：series_id
```

#### 通知
```
GET /v1/notification/list
GET /v1/notification/view-more?notification_id={id}
→ NotificationListResponse
```

#### 站内信息（公告）
```
GET /v1/info/latest
GET /v1/info/list?cid={id}
```

### 3.12 GIF 动图

见 [3.1 作品/插画](#31-作品插画) 中的 `/v1/ugoira/metadata`。zip 下载后按 `frames[].file + delay(ms)` 逐帧播放。

### 3.13 其他

#### 举报作品（v2 + 动态 topic 列表）
```
GET  /v1/illust/report/topic-list
POST /v2/illust/report   字段：illust_id, topic_id, description
```

#### IDP 配置
```
GET /idp-urls
→ IdpUrlsResponse
```

#### 通用 GET（任意 URL）
```
GET {next_url}  → 用于所有 next_url 翻页
```

---

## 4. Web API（www.pixiv.net）

> 网页 ajax 接口。需要 Cookie（Shaft 通过设置页同步 PHPSESSID 等），公开作品匿名也可访问。
> 请求头必须带：`User-Agent: Mozilla/5.0 ...`、`Referer: https://www.pixiv.net/`、`Host: www.pixiv.net`、`Cookie: ...`、`x-csrf-token`（写操作）。

### 4.1 网页作品详情（真实宽高）
```
GET /ajax/illust/{illust_id}
GET /ajax/illust/{illust_id}/pages?lang=zh
→ WebResponse<List<WebIllustPage>>   // 每 P 真实原图宽高，app-api 不提供
```

### 4.2 网页用户详情
```
GET /ajax/user/{user_id}?full=1&lang=zh
→ WebResponse<WebUserDetail>
```

### 4.3 首页方块内容
```
GET /ajax/top/{type}?mode=all&lang=zh
→ SquareResponse { body: { thumbnails: { illust }, page: { ... }, tagTranslation } }
```

### 4.4 用户收藏（网页）
```
GET /touch/ajax/user/bookmarks?id={uid}&type={illust|novel}&rest={public|private}&p=1&lang=zh&version=...
→ SquareResponse
```

### 4.5 相关用户（网页）
```
GET /touch/ajax/user/related?id={uid}&type=...&rest=...
→ SquareResponse
```

### 4.6 搜索（网页 - 圈子/同人志搜索）
```
GET /touch/ajax/search/illusts?word={keyword}&include_meta=1&type=all&csw=0&s_mode=s_tag_full&lang=zh&version=...
→ CircleResponse { body: { illusts: [WebIllust], meta, total, lastPage } }
```

### 4.7 Street（街拍式首页）
```
POST /ajax/street/v2/main
Header: x-csrf-token
Body: StreetRequest { k, vhi, vhm, vhn, vhc }
→ StreetResponse { body: { contents: [{ kind, thumbnails, pickup, trendTags }], nextParams } }
```

### 4.8 拉黑（账号级，issue #959）
```
GET  /ajax/block/list?target_id={uid}&offset=0&limit=24&lang=zh
→ WebResponse<BlockListBody> { block_items: [{ userId, label, isBlocked, isTarget }] }
POST /ajax/block/save
Body: { user_id: "123", action: "block" | "unblock" }
Header: x-csrf-token
```

### 4.9 常用标签
```
GET /ajax/tags/frequent/illust?ids[]={id1}&ids[]={id2}&lang=zh
→ WebResponse<List<FrequentTag>>
```

### 4.10 按 Tag 筛选画师作品（RxJava 版 WebApi）
```
GET /ajax/user/{userId}/illusts/tag?tag={tag}&offset={0}&limit={48}&sensitiveFilterMode=userSetting&lang=zh
→ WebResponse<UserTagIllustBody> { works: [{ id, title, illustType, xRestrict, aiType, url, tags, width, height, pageCount, createDate }], total }
```

### 4.11 消息（动态）
```
GET /rpc/index.php?mode=latest_message_threads2&num=10&offset=0
```

---

## 5. 图片 CDN（i.pximg.net）

图片必须带请求头，否则 403：

```
Referer: https://app-api.pixiv.net/   （App API 来源）
User-Agent: 与 API 请求一致
```

### 5.1 URL 变体规则

| 尺寸 | 说明 |
|------|------|
| `square_medium` | 缩略方图 `https://i.pximg.net/c/360x360_70/img-master/.../xx_p0_square1200.jpg` |
| `medium` | 中等 `.../xx_p0_master1200.jpg` |
| `large` | 大图（不超过 1200px） |
| `original` | 原图 `https://i.pximg.net/img-original/img/.../xx_p0.png` |
| `px_16x16` / `px_50x50` / `px_170x170` | 头像小尺寸 |
| 动图 zip | `ugoira_metadata.zip_urls.medium` |

### 5.2 直连（国内加速）

Shaft 支持 Cronet（QUIC/HTTP3）直连：
- Cloudflare CDN IP：`104.16.90.12`、`104.16.91.12`（`i.pximg.net`、`app-api.pixiv.net`）
- 通过 CronetInterceptor 设置 `Host` header 映射到 CDN IP 请求

---

## 6. Shaft 自建服务

### 6.1 ShaftApiV2（事件/榜单/广场）

Base URL = `BuildConfig.SHAFT_EVENTS_BASE_URL`（社区自建服务）

```
GET  /health                      → { ok, service, ts, uptimeSec }

// 站长推荐
GET  /api/v1/trending/works?type={illust|manga|novel}&window={day|week|month}&limit=60&sort={score|bookmark}&include_meta=1&offset=0
→ TrendingWorksResponse { items: [{ target_id, bookmark_count, bean: IllustsBean|null, score, view_count }], next_url }

// 当前最热（实时收藏流）
GET  /api/v1/recent/works?type=...&limit=60&offset=0&window={day|week|month|null}
→ RecentWorksResponse

// 发现页聚合
GET  /api/v1/discover
→ DiscoverResponse { site: { items }, recent: { items } }

// 画师收藏总榜
GET  /api/v1/discover/artists?limit=30&offset=0&sort={total|avg}
→ ArtistRankResponse { user_previews: [{ user, illusts, total_bookmarks, work_count }], next_url }

// 全站浏览量榜
GET  /api/v1/discover/most-viewed?type=illust&limit=30&offset=0

// 全站收藏榜（历史殿堂，first-write-wins 定格值）
GET  /api/v1/discover/most-bookmarked?type=illust&limit=30&offset=0&ai={only|exclude}&year={2026}&q={keyword}&tag={tag}
→ MostBookmarkedResponse

// 标签选择器
GET  /api/v1/discover/tags?type=illust&limit=30&offset=0&q={keyword}
→ TagsResponse { tags: [{ tag, translated, count }] }

// 壁纸榜
GET  /api/v1/discover/wallpapers?screen={phone|desktop}&limit=30&offset=0

// 年代选择器
GET  /api/v1/discover/years?type=illust
→ YearsResponse { years: [{ year, count }] }

// 客户端操作日志
GET  /api/v1/events/history?client_id={sha256}&limit=50&event_type={bookmark|unbookmark|download|follow|unfollow}&before={id?}
→ EventsHistoryResponse { items, next_before }
```

### 6.2 Plaza 广场

> 写请求需 HMAC 签名（`X-Shaft-Sign` = HMAC-SHA256(body, secret)），body 必须保持 canonical 形态。

```
POST   /api/v1/plaza/posts                    （body: uid, ts, sig, text, refs{illust,novel,user}, ref_metas?）
GET    /api/v1/plaza/posts?limit=20&before={id?}&viewer_uid=&viewer_ts=&viewer_sig=
GET    /api/v1/plaza/posts/{id}
DELETE /api/v1/plaza/posts/{id}
GET    /api/v1/plaza/users/{uid}/posts
GET    /api/v1/plaza/users/{uid}/likes?ts=&sig=&limit=&before=     （before 是 like_id）
POST   /api/v1/plaza/posts/{id}/like
DELETE /api/v1/plaza/posts/{id}/like
POST   /api/v1/plaza/posts/{id}/comments
GET    /api/v1/plaza/posts/{id}/comments?limit=20&before={id?}
DELETE /api/v1/plaza/comments/{cid}
```

### 6.3 Pixshaft 历史同步（pixshaft.com）

```
POST   /v1/history/{uid}            body: { items: [{ target_type, target_id, payload }] }  → { upserted, total }
GET    /v1/history/{uid}?type={}&q={}&before={}&limit={}
DELETE /v1/history/{uid}/{type}/{id}
DELETE /v1/history/{uid}?type={}
POST   /v1/history/{uid}/sync-pref   body: { enabled }
```

### 6.4 邮箱绑定备份

```
POST /v1/account/bind/request     body: { email }  → 发送 6 位验证码
POST /v1/account/bind/confirm     body: { email, code, account }
POST /v1/account/restore/request  body: { email }  → { found }
POST /v1/account/restore/confirm  body: { email, code }  → { account, updatedAt }
POST /v1/account/bind/status      body: { uid }
POST /v1/account/bind/delete      body: { uid }
```
> 以上需 `X-Shaft-Sign` 签名（路径含 `/v1/account/`）。

### 6.5 MoonAPI（设置同步）

```
GET /v1/settings/{uid}
PUT /v1/settings/{uid}  body: JsonObject
```
自定义 DNS：`shaft.api` → `111.229.197.181`，`Proxy.NO_PROXY`。

### 6.6 Chat（HTTP 伴侣 + WebSocket）

```
GET  /api/v1/chat/history?room={global|threadId}&limit=50&before={id?}
GET  /api/v1/chat/profile?uid={uid}
POST /api/v1/chat/profile  （X-Shaft-Sign）
GET  /api/v1/chat/stats?room=global
GET  /api/v1/chat/conversations?uid=&ts=&limit=&cursor=  （X-Shaft-Sign）
POST /api/v1/chat/conversations/{room}/read  （X-Shaft-Sign）
```

---

## 7. 请求头与签名

### 7.1 app-api 请求头（新版 iOS 身份）

```
Authorization: Bearer <access_token>        // 登录后
accept-language: zh-CN                      // 视应用语言
app-accept-language: zh-CN
app-os: ios
app-os-version: 26.5
app-version: 8.6.10
user-agent: PixivIOSApp/8.6.10 (iOS 26.5; iPhone16,2)
x-client-time: 2026-08-02T12:34:56+08:00    // ISO8601
x-client-hash: <md5(x-client-time + secret)>
```

### 7.2 请求签名（x-client-time / x-client-hash）

```
时间格式:  SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ")
x-client-hash = MD5(x-client-time + "28c1fdd170a5204386cb1313c7077b34f83e4aaf4aa829ce78c231e05b0bae2c")
```

> ⚠️ 签名密钥来自 pixiv 官方客户端抓包（感谢 upbit/pixivpy issue #83）。密钥可能随官方客户端更新而失效，需要定期抓包更新。

### 7.3 网页 API 请求头

```
Host: www.pixiv.net
Referer: https://www.pixiv.net/
User-Agent: Mozilla/5.0 (Linux; Android 14; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.39 Mobile Safari/537.36
Cookie: PHPSESSID=...; cf_clearance=...
accept-language: zh-CN
x-csrf-token: ...        // 写操作（block/save、street/main）
```

> ⚠️ `cf_clearance` Cookie 绑定 UA，WebView 与 OkHttp 必须使用同一 UA。

---

## 8. 分页机制

App API 采用 **next_url 游标分页**：

```json
{
  "illusts": [...],
  "next_url": "https://app-api.pixiv.net/v1/illust/ranking?mode=day&offset=30"
}
```

- 每页约 30 条
- 请求 `next_url` 时带相同鉴权头即可
- `next_url == null` 表示没有更多数据
- Shaft 封装：`KListShow<T>` 接口（`displayList` + `nextPageUrl`）

### 推荐架构

```kotlin
abstract class PageSource<T> {
    abstract suspend fun loadInitial(): ListPage<T>
    abstract suspend fun loadNext(nextUrl: String): ListPage<T>
    suspend fun fetchPage(): ListPage<T> // 内部处理 next_url
}
```

---

## 9. 错误处理

### 9.1 错误响应格式

```json
{
  "error": {
    "message": "...",
    "reason": "...",
    "user_message": "...",
    "user_message_details": {...}
  }
}
```

### 9.2 常见错误码

| HTTP | 原因 | 处理 |
|------|------|------|
| 400 | OAuth token 过期 / 参数错误 | 刷新 token 重放；`"Invalid refresh token"` 需登出 |
| 401 | 未授权（无 token） | 引导登录 |
| 403 | 无权限 / R18 限制 / 被屏蔽 | 展示提示 |
| 404 | 作品已删除/不存在 | 展示删除提示 |
| 429 | 频率限制 | 退避重试 |

### 9.3 网络层注意

- Shaft 用 HTTP/1.1（Retro.java）或 HTTP/2 + HTTP/1.1（新版 Client）
- 国内网络需代理或直连（Cronet QUIC）
- OkHttp 连接/读/写超时 10s
- Token 刷新需加锁避免并发重复刷新

---

## 附：Shaft 中 API 文件索引

| 文件 | 内容 |
|------|------|
| `ceui/lisa/http/AppApi.java` | 官方 app-api（RxJava，全量） |
| `ceui/loxia/API.kt` | 官方 app-api（Kotlin suspend，新版主用） |
| `ceui/loxia/PixivWebApi.kt` | 网页 ajax（Kotlin） |
| `ceui/lisa/http/WebApi.java` | 网页 ajax（RxJava，tag 筛选） |
| `ceui/lisa/http/AccountTokenApi.java` | OAuth token |
| `ceui/lisa/http/SignApi.java` | 账户编辑 |
| `ceui/loxia/PixshaftApi.kt` | 浏览历史云同步 |
| `ceui/loxia/MoonAPI.kt` | 设置同步 |
| `ceui/lisa/network/ShaftApiV2.kt` | 榜单/广场/事件 |
| `ceui/pixiv/chat/api/ShaftChatApi.kt` | Chat HTTP |
| `ceui/lisa/update/GitHubApi.kt` | 更新检查 |
| `ceui/lisa/http/ResourceApi.java` | jsDelivr 资源 |
| `ceui/loxia/Models.kt` | 核心 DTO（Kotlin） |
| `models/src/main/java/ceui/lisa/models/*.java` | 全量 DTO（Java） |
