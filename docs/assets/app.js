/* ═══════════════════════════════════════════════════════════
   PixivReader 发布页脚本
   职责：Release 数据拉取 / 更新日志渲染 / 中英切换 / 轻交互。
   无任何依赖；所有网络请求均为只读的 GitHub 公开 API。
   ═══════════════════════════════════════════════════════════ */
(() => {
  'use strict';

  const REPO = 'nichijoux/Pixiv-Reader-MD3';
  const API_URL = `https://api.github.com/repos/${REPO}/releases/latest`;
  const RELEASES_URL = `https://github.com/${REPO}/releases`;
  const CACHE_KEY = 'pr_release_cache';

  /* ── 英文文案字典（中文为默认语言，直接写在 HTML 中） ── */
  const I18N = {
    'a11y.skip': 'Skip to main content',
    'meta.title': 'PixivReader — Unofficial Pixiv client for Android · APK Download',
    'meta.desc': 'PixivReader: an unofficial Pixiv client for Android with Material 3 design. Illustrations, manga and novels in one app — rankings, offline downloads and tablet-ready adaptive layouts. Free and open source (GPL-2.0), ~10 MB APK.',
    'nav.features': 'Features',
    'nav.ui': 'Screens',
    'nav.download': 'Download',
    'nav.install': 'Get started',
    'nav.faq': 'FAQ',
    'nav.github': 'View source on GitHub',
    'nav.menu': 'Open menu',
    'nav.langAria': 'Switch to 中文',
    'nav.pageAria': 'Page navigation',
    'hero.subtitle': 'Unofficial Pixiv client for Android<br>Material 3 design · Free &amp; open source',
    'hero.desc': 'Illustrations, manga and novels in one place: waterfall browsing, rankings, offline downloads and adaptive tablet layouts — all open source.',
    'hero.cta.download': 'Download latest APK',
    'hero.cta.source': 'View on GitHub',
    'hero.meta.license': 'GPL-2.0 open source',
    'hero.meta.noad': 'No ads · No IAP',
    'hero.meta.local': 'Data stays on device',
    'hero.phone.label': 'Phone',
    'trust.1': 'Open source & auditable (GPL-2.0)',
    'trust.2': 'No ads · No in-app purchases',
    'trust.3': 'Data stays on your device',
    'trust.4': 'Connects directly to the pixiv API',
    'feat.kicker': 'Features',
    'feat.title': 'One client for your whole reading flow',
    'feat.desc': 'Illustrations · manga · novels · downloads · interaction — every core feature is open source.',
    'feat.more': 'Show all features',
    'feat.less': 'Collapse',
    'feat.1.t': 'Illustrations & manga',
    'feat.1.d': 'Material 3 waterfall browsing with lossless zoom; ugoira playback with one-tap export to MP4 video or ZIP frame packs.',
    'feat.2.t': 'Complete rankings',
    'feat.2.d': 'Daily / weekly / monthly / male / female / rookie / R-18 with swipeable sections and infinite scroll — plus artist, AI, era and wallpaper rankings.',
    'feat.3.t': 'Novel reading',
    'feat.3.d': 'Online reading with progress memory and four themes; import local TXT / EPUB / Markdown, export PDF / TXT.',
    'feat.4.t': 'Discovery',
    'feat.4.d': 'Illust & novel search, trending tags and pixivision specials; FANBOX / COMIC / messages open in your browser.',
    'feat.5.t': 'Series follows',
    'feat.5.d': 'Follow novel / manga series with detail pages, chapter management and inline unfollow.',
    'feat.6.t': 'Comments & stickers',
    'feat.6.d': 'Comments with emoji tags and official pixiv stickers; delete your own comments anytime.',
    'feat.7.t': 'History · Read later · Blocking',
    'feat.7.d': 'Full browse and search history; long-press any card to save it for later or block it on the spot.',
    'feat.8.t': 'Offline downloads',
    'feat.8.d': 'WorkManager background downloads with progress tracking, completion notifications, offline queueing and retry.',
    'feat.9.t': 'Bookmarks & follows',
    'feat.9.d': 'Bookmark editor with public / private visibility and tags, plus a clear following list.',
    'feat.10.t': 'Personalization',
    'feat.10.d': 'Dark theme + Material You dynamic color; Simplified / Traditional Chinese and English; font scaling.',
    'feat.11.t': 'Fast & reliable',
    'feat.11.d': 'Home snapshot for instant cold starts and offline browsing; in-app update checks and pixiv:// deep links.',
    'feat.12.t': 'Tiny footprint',
    'feat.12.d': 'R8-minified release builds, roughly 10 MB per ABI.',
    'ui.kicker': 'Screenshots',
    'ui.title': 'Phone and tablet, one Material 3 app',
    'ui.desc': 'The UI adapts to window width: bottom navigation on phones, a side rail on tablets, and a list + detail split view for rankings on wide screens.',
    'ui.tab.phone': 'Phone',
    'ui.tab.tablet': 'Tablet',
    'ui.slot.hint': 'Screenshot coming soon',
    'ui.slot.file': 'Expected size',
    'ui.an.p1': 'Bottom navigation with 5 tabs: Home / Artworks / Novels / Following / Me',
    'ui.an.p2': 'Cards keep each artwork\'s original aspect ratio — nothing is cropped',
    'ui.an.p3': 'Fullscreen viewer with pinch-to-zoom and frame-by-frame ugoira playback',
    'ui.an.t1': '≥600dp: an 84dp NavigationRail replaces the bottom bar',
    'ui.an.t2': '≥704dp: rankings expand into a list + detail / comments split view',
    'ui.an.t3': 'Content is capped at 760dp and centered — never stretched on large screens',
    'dl.kicker': 'Download',
    'dl.title': 'Get the latest APK',
    'dl.desc': 'APKs are split by CPU architecture to keep them small. If unsure, pick arm64-v8a — nearly every phone uses it.',
    'dl.ver.label': 'Latest release',
    'dl.date.label': 'Published',
    'dl.all': 'Browse all releases',
    'dl.badge.rec': 'Recommended',
    'dl.abi.arm64.desc': 'Nearly all phones & tablets since 2016',
    'dl.abi.v7a.desc': 'Older devices · 32-bit ARM',
    'dl.cta': 'Download APK',
    'dl.size': 'Size',
    'dl.which.t': 'Which one should I get?',
    'dl.which.d': 'Open Settings → About phone and check the processor: 64-bit (almost everything modern) → arm64-v8a; older devices that only mention 32-bit → armeabi-v7a. A mismatch makes Android report "App not installed".',
    'install.kicker': 'Getting started',
    'install.title': 'Up and running in four steps',
    'install.desc': 'Takes about two minutes, no extra configuration needed.',
    'install.1.t': 'Download the APK',
    'install.1.d': 'Pick the package matching your device above, or grab it from the GitHub Releases page.',
    'install.2.t': 'Allow unknown apps',
    'install.2.d': 'On Android 8+ you will be prompted once: grant your browser or file manager permission to "install unknown apps". This is a standard system prompt.',
    'install.3.t': 'Install & open',
    'install.3.d': 'Tap the downloaded APK to install. Installing a new version over an existing one keeps history, downloads and reading progress.',
    'install.4.t': 'Sign in to pixiv',
    'install.4.d': 'First launch walks you through pixiv\'s official OAuth flow — your password is only ever entered on pixiv\'s own page, never in this app.',
    'install.note1.t': 'Where is my data kept?',
    'install.note1.d': 'History, searches, downloads and reading progress live only in the on-device database; uninstalling wipes them.',
    'install.note2.t': 'How do I update?',
    'install.note2.d': 'Use Me → Check for updates in the app (reads GitHub Releases), or simply install the new APK over the old one.',
    'install.note3.t': 'Requirements',
    'install.note3.d': 'Android 8.0 (API 26) or later; an arm64-v8a or armeabi-v7a device; network access to pixiv.',
    'build.t': 'Build from source',
    'build.d': 'Requires JDK 21 and the Android SDK (compileSdk 37)',
    'faq.kicker': 'FAQ',
    'faq.title': 'Good to know',
    'faq.1.q': 'Do I need a pixiv account?',
    'faq.1.a': 'Yes. First launch walks you through pixiv\'s official OAuth authorization (PKCE). Your password is only ever entered on pixiv\'s own page — this app never sees or stores it.',
    'faq.2.q': 'Could a third-party client get my account banned?',
    'faq.2.a': 'PixivReader is an unofficial client. Accessing pixiv through third-party clients carries a theoretical account risk — please evaluate it yourself. This project is for learning purposes only and the author is not responsible for account issues.',
    'faq.3.q': 'Is my data safe? Where is it stored?',
    'faq.3.a': 'Browse history, searches, downloads and reading progress live only in the on-device Room database. The app uploads nothing, collects nothing, and ships no analytics SDK.',
    'faq.4.q': 'Why are there only ARM APKs?',
    'faq.4.a': 'Release builds are split by ABI to keep them small, covering arm64-v8a and armeabi-v7a — together that is virtually every Android phone and tablet. x86 devices and emulators are out of scope.',
    'faq.5.q': 'Android warns about "unknown apps" during install',
    'faq.5.a': 'Since Android 8.0 the system requires per-app approval: on the screen that pops up, allow the browser or file manager you downloaded the APK with to "install unknown apps", then go back and install again.',
    'faq.6.q': 'How do I update to a new version?',
    'faq.6.a': 'Use Me → Check for updates in the app — it reads GitHub Releases and shows the changelog. You can also download the new APK from this page or the Releases page and install it over the old version; all local data is kept.',
    'faq.7.q': 'Is a tablet supported?',
    'faq.7.a': 'Yes. At ≥600dp window width the bottom bar becomes a side NavigationRail; at ≥704dp rankings expand into a list + detail / comments split view; content is capped at 760dp and centered. Both orientations work.',
    'faq.8.q': 'What is the relationship with Pixiv-Shaft?',
    'faq.8.a': 'None — PixivReader is an independent project. Its API layer draws on the open-source projects Pixiv-Shaft and pixiv-api-kotlin, both GPL-2.0, credited in the footer.',
    'log.kicker': 'What\'s new',
    'log.prefix': 'v',
    'log.title': ' in the latest release',
    'log.loading': 'Loading release notes…',
    'log.all': 'View the full changelog on GitHub →',
    'log.fallback': 'Couldn\'t load release notes right now — check the Releases page instead.',
    'foot.license': 'Open source under GPL-2.0',
    'foot.built': 'Built with vanilla HTML / CSS / JS · no tracking',
    'foot.disclaimer.t': 'Disclaimer',
    'foot.disclaimer.d': 'PixivReader is an unofficial third-party client, not affiliated with pixiv or Pixiv Inc. The pixiv name and trademarks belong to their respective owners. Use at your own risk; for learning purposes only.',
    'foot.credits.t': 'Credits',
    'foot.credits.d': 'The API layer draws on these open-source projects:',
    /* Release 数据未拉到时的回退文案 */
    'misc.latest': 'Latest',
    'misc.approxSize': '≈ 10 MB',
    'misc.seeRelease': 'See release'
  };

  /* 截图替换后的替代文本（按占位槽区分） */
  const SLOT_ALT = {
    'phone-home': { zh: '应用首页截图', en: 'App home screenshot' },
    'tablet-ranking': { zh: '平板端排行榜截图', en: 'Rankings on tablet screenshot' }
  };

  let lang = 'zh';
  /** 最近一次成功拉取的 Release 数据；null 表示回退到静态文案 */
  let releaseData = null;
  /** Release 是否已确认失败（区分「加载中」与「失败」两种占位状态） */
  let releaseFailed = false;

  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

  /** HTML 转义，防止 Release 正文注入 */
  const esc = (s) => String(s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');

  /** 取当前语言文案；key 缺失时回退中文原文（元素自带） */
  const t = (key) => (lang === 'en' && I18N[key]) || null;

  /* ── 语言切换 ─────────────────────────────────────────── */

  /* 中文原文的快照：中文只写在 HTML 里、不在字典中重复，
     切回中文时据此回填，修复「来回切换后停留在英文」的问题 */
  const zhSnapshots = new Map();

  /** 记录所有 i18n 元素的中文初始值（text / html / aria 三类分开快照） */
  function snapshotZh() {
    $$('[data-i18n]').forEach((el) => zhSnapshots.set(el, el.textContent));
    $$('[data-i18n-html]').forEach((el) => zhSnapshots.set(el, el.innerHTML));
    $$('[data-i18n-aria]').forEach((el) => zhSnapshots.set(el, el.getAttribute('aria-label')));
  }

  /** 把页面切到指定语言：en 取字典、zh 回填快照，并同步 html lang / meta / 动态文案 */
  function setLang(next) {
    lang = next;
    try { localStorage.setItem('pr_lang', lang); } catch (_) { /* 隐私模式忽略 */ }

    $$('[data-i18n]').forEach((el) => {
      const value = lang === 'en' ? (I18N[el.dataset.i18n] ?? null) : (zhSnapshots.get(el) ?? null);
      if (value !== null) el.textContent = value;
    });
    $$('[data-i18n-html]').forEach((el) => {
      const value = lang === 'en' ? (I18N[el.dataset.i18nHtml] ?? null) : (zhSnapshots.get(el) ?? null);
      if (value !== null) el.innerHTML = value;
    });
    $$('[data-i18n-aria]').forEach((el) => {
      const value = lang === 'en' ? (I18N[el.dataset.i18nAria] ?? null) : (zhSnapshots.get(el) ?? null);
      if (value !== null) el.setAttribute('aria-label', value);
    });

    document.documentElement.lang = lang === 'en' ? 'en' : 'zh-Hans';

    // meta 描述是属性值而非文本，单独切换（中文原文记录在 dataset.orig）
    const desc = $('meta[name="description"]');
    if (desc) desc.setAttribute('content', lang === 'en' ? I18N['meta.desc'] : (desc.dataset.orig || ''));

    // 切换按钮展示的是「目标语言」
    const btn = $('#lang-btn');
    btn.textContent = lang === 'en' ? '中文' : 'EN';

    renderReleaseTexts();
    renderChangelog();
    applySlotAlt();
    // 主题按钮的 aria / title 标签随语言刷新
    applyTheme();
  }

  /** 初始化：先快照中文原文，再恢复上次语言选择并绑定切换按钮 */
  function initLang() {
    snapshotZh();
    const descEl = $('meta[name="description"]');
    if (descEl) descEl.dataset.orig = descEl.getAttribute('content') || '';

    let saved = null;
    try { saved = localStorage.getItem('pr_lang'); } catch (_) { /* 忽略 */ }
    if (saved === 'en') setLang('en');
    $('#lang-btn').addEventListener('click', () => setLang(lang === 'zh' ? 'en' : 'zh'));
  }

  /* ── Release 数据 ─────────────────────────────────────── */

  /** 拉取最新 Release；任何失败都静默回退到静态链接，页面不空 */
  async function loadRelease() {
    try {
      let data = null;
      try {
        const raw = sessionStorage.getItem(CACHE_KEY);
        if (raw) data = JSON.parse(raw);
      } catch (_) { /* 缓存损坏则忽略 */ }

      if (!data) {
        const res = await fetch(API_URL, { headers: { Accept: 'application/vnd.github+json' } });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        data = await res.json();
        try { sessionStorage.setItem(CACHE_KEY, JSON.stringify(data)); } catch (_) { /* 忽略 */ }
      }
      releaseData = normalizeRelease(data);
      renderReleaseTexts();
      renderChangelog();
    } catch (_) {
      // 拉取失败：置失败标记，保留静态回退文案，并把更新日志区换成提示
      releaseFailed = true;
      renderChangelog();
    }
  }

  /** 从 API 响应提取页面需要的字段（tag / 日期 / 两个 ABI 的直链与体积） */
  function normalizeRelease(data) {
    const assets = Array.isArray(data.assets) ? data.assets : [];
    const find = (re) => assets.find((a) => re.test(a.name || ''));
    const mb = (bytes) => `${(bytes / 1048576).toFixed(1)} MB`;
    const arm64 = find(/arm64-v8a\.apk$/i);
    const v7a = find(/armeabi-v7a\.apk$/i);
    return {
      tag: data.tag_name || '',
      date: data.published_at || '',
      body: typeof data.body === 'string' ? data.body : '',
      arm64Url: arm64 ? arm64.browser_download_url : `${RELEASES_URL}/latest`,
      v7aUrl: v7a ? v7a.browser_download_url : `${RELEASES_URL}/latest`,
      arm64Size: arm64 ? mb(arm64.size) : '',
      v7aSize: v7a ? mb(v7a.size) : '',
      totalSize: arm64 && v7a ? mb(Math.min(arm64.size, v7a.size)) : ''
    };
  }

  /** 把 Release 数据（或静态回退文案）填进所有 data-release 插槽 */
  function renderReleaseTexts() {
    const d = releaseData;
    const put = (name, value) => {
      $$(`[data-release="${name}"]`).forEach((el) => { el.textContent = value; });
    };

    put('version', d ? d.tag : (t('misc.latest') || '最新版'));
    put('version-num', d ? d.tag.replace(/^v/, '') : '?');
    // 体积前缀随语言变化：中文「约」/ 英文「≈」
    const approx = lang === 'en' ? '≈' : '约';
    put('size', d ? `${approx} ${d.totalSize}` : (t('misc.approxSize') || `${approx} 10 MB`));
    put('size-arm64', d ? (d.arm64Size || (t('misc.seeRelease') || '见 Release')) : (t('misc.seeRelease') || '见 Release'));
    put('size-v7a', d ? (d.v7aSize || (t('misc.seeRelease') || '见 Release')) : (t('misc.seeRelease') || '见 Release'));

    // 日期按当前语言本地化
    let dateText = '—';
    if (d && d.date) {
      try {
        dateText = new Date(d.date).toLocaleDateString(lang === 'en' ? 'en-US' : 'zh-CN', {
          year: 'numeric', month: 'long', day: 'numeric'
        });
      } catch (_) { dateText = d.date.slice(0, 10); }
    }
    put('date', dateText);

    // 直链：成功则指向具体 APK，失败保持 releases/latest
    $$('[data-release-link="arm64"]').forEach((a) => { a.href = d ? d.arm64Url : `${RELEASES_URL}/latest`; });
    $$('[data-release-link="v7a"]').forEach((a) => { a.href = d ? d.v7aUrl : `${RELEASES_URL}/latest`; });
  }

  /* ── 更新日志渲染（markdown 子集，先转义再处理标记） ──── */

  /** 行内标记：**加粗**、`代码`、[链接](url)；输入已整体转义 */
  function inlineMd(s) {
    return esc(s)
      .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
      .replace(/`([^`]+)`/g, '<code>$1</code>')
      .replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>');
  }

  /** 极简 markdown → HTML：仅支持 ### 标题、- 列表、行内标记与普通段落 */
  function renderMarkdown(src) {
    const out = [];
    let inList = false;
    const closeList = () => { if (inList) { out.push('</ul>'); inList = false; } };

    for (const raw of String(src || '').split(/\r?\n/)) {
      const line = raw.trim();
      if (!line) { closeList(); continue; }
      const heading = line.match(/^#{2,4}\s+(.*)$/);
      if (heading) { closeList(); out.push(`<h4>${inlineMd(heading[1])}</h4>`); continue; }
      const item = line.match(/^[-*]\s+(.*)$/);
      if (item) {
        if (!inList) { out.push('<ul>'); inList = true; }
        out.push(`<li>${inlineMd(item[1])}</li>`);
        continue;
      }
      closeList();
      out.push(`<p>${inlineMd(line)}</p>`);
    }
    closeList();
    return out.join('');
  }

  /** 渲染更新日志区：成功展示正文；失败/无数据展示提示 + Releases 链接 */
  function renderChangelog() {
    const box = $('[data-release="changelog"]');
    if (!box) return;

    if (releaseData && releaseData.body) {
      box.innerHTML = renderMarkdown(releaseData.body);
      return;
    }
    // 加载中（尚未失败也未成功）保持 loading；已失败则给回退提示
    const loading = $('.log-loading', box);
    if (releaseFailed) {
      box.innerHTML = '';
      const p = document.createElement('p');
      p.className = 'log-loading';
      p.textContent = lang === 'en' ? I18N['log.fallback'] : '暂时无法加载更新说明，请前往 Releases 页面查看。';
      const a = document.createElement('a');
      a.href = RELEASES_URL;
      a.target = '_blank';
      a.rel = 'noopener';
      a.textContent = 'GitHub Releases →';
      box.append(p, a);
    } else if (!loading) {
      box.innerHTML = `<p class="log-loading">${lang === 'en' ? I18N['log.loading'] : '正在加载更新说明…'}</p>`;
    }
  }

  /* ── 截图占位槽：图片存在则自动替换示意画面 ──────────── */

  /** 检查每个设备屏的截图；加载成功则隐藏占位示意并显示真实截图 */
  function activateSlots() {
    $$('.device-screen[data-slot]').forEach((screen) => {
      const img = $('.slot-img', screen);
      if (!img) return;
      const show = () => {
        $$('.ghost, .slot-hint', screen).forEach((el) => { el.style.display = 'none'; });
        img.hidden = false;
      };
      // 缓存命中等 load 事件已错过的情况，先查 complete
      if (img.complete && img.naturalWidth > 0) show();
      else img.addEventListener('load', show, { once: true });
      // 加载失败（图片尚未放入）保持占位画面不动
    });
  }

  /** 按当前语言更新截图替代文本 */
  function applySlotAlt() {
    $$('.device-screen[data-slot]').forEach((screen) => {
      const img = $('.slot-img', screen);
      const alt = SLOT_ALT[screen.dataset.slot];
      if (img && alt) img.alt = alt[lang] || alt.zh;
    });
  }

  /* ── 功能特性折叠：默认 6 项，展开其余 ────────────────── */

  /** 折叠阈值：首屏展示的功能卡数量 */
  const FEAT_VISIBLE_COUNT = 6;

  /** 初始化折叠：JS 可用时隐藏第 7 张起的卡片并绑定展开按钮（无 JS 保持全部可见） */
  function initFeatToggle() {
    const grid = $('.bento');
    const btn = $('#feat-toggle');
    if (!grid || !btn) return;
    const cards = $$('.bento > .card');
    if (cards.length <= FEAT_VISIBLE_COUNT) { btn.parentElement.hidden = true; return; }

    const extras = cards.slice(FEAT_VISIBLE_COUNT);
    const apply = (expanded) => {
      extras.forEach((el) => { el.hidden = !expanded; });
      btn.setAttribute('aria-expanded', String(expanded));
      btn.classList.toggle('is-open', expanded);
      $('.feat-more', btn).hidden = expanded;
      $('.feat-less', btn).hidden = !expanded;
    };
    btn.addEventListener('click', () => apply(extras[0].hidden));
    apply(false);
  }

  /* ── 轻交互：移动端菜单 / 界面预览 Tab ────────────────── */

  function initNav() {
    const header = $('.nav');
    const toggle = $('#nav-toggle');
    toggle.addEventListener('click', () => {
      const open = header.classList.toggle('nav-open');
      toggle.setAttribute('aria-expanded', String(open));
    });
    // 点击链接后收起菜单，避免遮挡内容
    $$('.nav-links a').forEach((a) => a.addEventListener('click', () => {
      header.classList.remove('nav-open');
      toggle.setAttribute('aria-expanded', 'false');
    }));
    // 回到桌面宽度时复位菜单状态
    window.matchMedia('(min-width: 821px)').addEventListener('change', (e) => {
      if (e.matches) {
        header.classList.remove('nav-open');
        toggle.setAttribute('aria-expanded', 'false');
      }
    });
  }

  /** 手机 / 平板预览切换（原生 tab 语义：role + aria-selected + hidden） */
  function initUiTabs() {
    const tabs = [$('#tab-phone'), $('#tab-tablet')];
    const panels = [$('#panel-phone'), $('#panel-tablet')];
    const select = (i) => {
      tabs.forEach((tab, j) => {
        const on = i === j;
        tab.classList.toggle('is-active', on);
        tab.setAttribute('aria-selected', String(on));
        tab.tabIndex = on ? 0 : -1;
        panels[j].classList.toggle('is-active', on);
        panels[j].hidden = !on;
      });
    };
    tabs.forEach((tab, i) => {
      tab.addEventListener('click', () => select(i));
      tab.addEventListener('keydown', (e) => {
        // 左右方向键在 tab 间移动焦点，符合 WAI-ARIA tabs 惯例
        if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') {
          e.preventDefault();
          const next = (i + (e.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length;
          select(next);
          tabs[next].focus();
        }
      });
    });
  }

  /* ── 主题：深色 / 亮色 / 跟随系统 ─────────────────────── */

  const THEME_KEY = 'pr_theme';
  const THEME_CYCLE = ['system', 'light', 'dark'];
  const THEME_MEDIA = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;
  let theme = 'system';

  /* 主题按钮的无障碍标签（状态 × 语言组合，故不走 data-i18n 快照） */
  const THEME_ARIA = {
    system: { zh: '主题：跟随系统，点击切换为亮色', en: 'Theme: follow system, click for light' },
    light: { zh: '主题：亮色，点击切换为深色', en: 'Theme: light, click for dark' },
    dark: { zh: '主题：深色，点击切换为跟随系统', en: 'Theme: dark, click to follow system' }
  };

  /** 当前系统是否偏好深色（无 matchMedia 时按深色兜底） */
  function systemPrefersDark() {
    return THEME_MEDIA ? THEME_MEDIA.matches : true;
  }

  /** 把偏好落到 <html data-theme>、theme-color 与按钮图标 / 标签上 */
  function applyTheme() {
    const dark = theme === 'dark' || (theme === 'system' && systemPrefersDark());
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
    const meta = $('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', dark ? '#0A0A0A' : '#F7F8FA');

    $$('#theme-btn [data-icon]').forEach((svg) => { svg.hidden = svg.dataset.icon !== theme; });
    const labels = THEME_ARIA[theme];
    const label = (labels && (labels[lang] || labels.zh)) || 'Theme';
    const btn = $('#theme-btn');
    if (btn) {
      btn.setAttribute('aria-label', label);
      btn.setAttribute('title', label);
    }
  }

  /** 初始化主题：读回偏好 → 应用 → 绑定循环切换 → 跟随系统的实时响应 */
  function initTheme() {
    try { theme = localStorage.getItem(THEME_KEY) || 'system'; } catch (_) { theme = 'system'; }
    if (!THEME_CYCLE.includes(theme)) theme = 'system';
    // ?theme= 参数仅本次会话生效（与内联首帧脚本一致），不写入持久偏好
    const q = new URLSearchParams(location.search).get('theme');
    if (THEME_CYCLE.includes(q)) theme = q;
    applyTheme();

    $('#theme-btn').addEventListener('click', () => {
      theme = THEME_CYCLE[(THEME_CYCLE.indexOf(theme) + 1) % THEME_CYCLE.length];
      try { localStorage.setItem(THEME_KEY, theme); } catch (_) { /* 隐私模式忽略 */ }
      applyTheme();
    });

    // 「跟随系统」时系统切换深浅色要即时生效
    const onSystemChange = () => { if (theme === 'system') applyTheme(); };
    if (THEME_MEDIA) {
      if (THEME_MEDIA.addEventListener) THEME_MEDIA.addEventListener('change', onSystemChange);
      else if (THEME_MEDIA.addListener) THEME_MEDIA.addListener(onSystemChange);
    }
  }

  /* ── 启动 ─────────────────────────────────────────────── */
  initTheme();
  initLang();
  initNav();
  initUiTabs();
  initFeatToggle();
  // URL 参数：?lang=en 直达英文、?tab=tablet 直达平板预览（便于分享与测试）
  const params = new URLSearchParams(location.search);
  if (params.get('lang') === 'en') setLang('en');
  const uiParam = params.get('tab');
  if (uiParam === 'tablet' || uiParam === 'phone') {
    $('#tab-' + uiParam).click();
    // 深链接语义：?tab= 直达界面预览区块
    const screens = document.getElementById('screens');
    if (screens) screens.scrollIntoView({ behavior: 'instant', block: 'start' });
  }
  activateSlots();
  applySlotAlt();
  loadRelease();
})();
