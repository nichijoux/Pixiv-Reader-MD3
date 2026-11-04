package com.pixiv.reader.core.ui.component.detail

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.pixiv.reader.core.ui.component.image.PixivImage

/**
 * hero 过渡种子：卡片点击时快照封面信息，详情页加载期渲染占位图衔接 morph
 * （避免数据未到、终点无共享元素导致的「飞入空窗」）。
 *
 * @param coverUrl 卡片封面 URL（详情加载期占位图数据源；空串表示无封面，占位块留纯色底）
 * @param aspectRatio 卡片封面宽高比（`width / height`；null 表示无宽高数据，占位块按 1:1 兜底）
 */
data class HeroSeed(
    val coverUrl: String,
    val aspectRatio: Float?,
)

/**
 * hero 种子暂存（进程级单例，参照 `LocalReaderStore` 的一次性传递模式）。
 *
 * 键为 [illustCoverKey]/[novelCoverKey] 生成的共享元素 key（含类型前缀），
 * 避免插画与小说同 id 互相串用。卡片点击写入，详情加载期 `consume` 取出即删，
 * 详情路由销毁时 `clear` 兜底清残留（详情命中缓存、占位分支从未组合的情况）。
 * 仅在主线程（点击 / 组合）使用，无需加锁。
 */
object HeroSeedStore {

    private val seeds = mutableMapOf<String, HeroSeed>()

    /**
     * 暂存 hero 种子（同 key 覆盖）。
     *
     * @param key 共享元素 key（[illustCoverKey]/[novelCoverKey]）
     * @param value 封面快照
     * @return 无返回值
     */
    fun seed(key: String, value: HeroSeed) {
        seeds[key] = value
    }

    /**
     * 取出并删除 hero 种子（仅一次有效，防过期残留被后续无关打开复用）。
     *
     * @param key 共享元素 key（[illustCoverKey]/[novelCoverKey]）
     * @return 封面快照；未暂存过或已被取走时为 null
     */
    fun consume(key: String): HeroSeed? = seeds.remove(key)

    /**
     * 丢弃 hero 种子（详情路由销毁时兜底清理未消费的残留）。
     *
     * @param key 共享元素 key（[illustCoverKey]/[novelCoverKey]）
     * @return 无返回值
     */
    fun clear(key: String) {
        seeds.remove(key)
    }
}

/**
 * 根级 SharedTransitionScope 下发通道（app 根部 `SharedTransitionLayout` 提供）。
 *
 * core:ui 不依赖 navigation-compose，故用 CompositionLocal 下发；无提供者（预览 / 测试 /
 * 未包根布局）时为 null，[heroBoundsModifier] 退化为普通 Modifier，组件调用方零改动。
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * 当前导航目的地（composable 内容）的 [AnimatedVisibilityScope] 下发通道。
 *
 * 由顶层 NavHost 在详情路由 `composable {}` 内容内提供（receiver 即 AnimatedContentScope），
 * 详情侧 hero 元素据此判断「进入 / 退出」，以便跨 NavHost 层级与内层 Tab 卡片匹配。
 */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * 插画封面共享元素 key（卡片与详情大图用同一 key 匹配）。
 *
 * @param id 作品 id
 * @return 带类型前缀的 key
 */
fun illustCoverKey(id: Long): String = "illust_cover_$id"

/**
 * 小说封面共享元素 key（卡片与详情 banner 用同一 key 匹配）。
 *
 * @param id 小说 id
 * @return 带类型前缀的 key
 */
fun novelCoverKey(id: Long): String = "novel_cover_$id"

/**
 * hero 共享边界修饰：两个 CompositionLocal 均可用时挂 `sharedBounds`，否则原样返回普通 Modifier。
 *
 * 卡片（[com.pixiv.reader.core.ui.component.card.IllustCard]/`NovelCard`）与详情容器内部
 * 直接调用，页面层无需透传 scope；bounds 动画采用主题 `MotionScheme` 的默认 spatial 弹簧
 * （与项目 `Motion.kt` 导航过渡同一 token 体系）。`boundsTransform` 非组合上下文，
 * 规格在组合期先取好再闭包捕获。
 *
 * @param key 共享元素 key（[illustCoverKey]/[novelCoverKey]/搜索框 key 等）
 * @param animatedVisibilityScope hero 元素所属的可见性作用域；null（默认）取
 *   [LocalNavAnimatedVisibilityScope]（详情路由由顶层 NavHost 提供），Tab 内搜索框等
 *   场景显式传入所在 `composable` 的 scope
 * @return 已挂 sharedBounds 的 Modifier；无共享过渡环境时返回无操作 Modifier
 */
@Composable
fun Modifier.heroBoundsModifier(
    key: String,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = animatedVisibilityScope ?: LocalNavAnimatedVisibilityScope.current
        ?: return this
    // 组合期取好动效规格：boundsTransform lambda 非组合上下文，不能读 MaterialTheme
    val boundsSpec: FiniteAnimationSpec<Rect> = MaterialTheme.motionScheme.defaultSpatialSpec()
    return with(sharedScope) {
        this@heroBoundsModifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = visibilityScope,
            boundsTransform = { _, _ -> boundsSpec },
        )
    }
}

/**
 * 详情加载期 hero 占位封面：一次性取出卡片写入的 [HeroSeed]，按卡片比例 / 指定高度渲染封面，
 * 与卡片侧同 key 衔接 morph；数据到达后随加载分支整体销毁，真图以同 key 接棒。
 *
 * 无种子（未从卡片进入，如深链 / 平板上直接切换）时不渲染任何内容，纯骨架照常展示。
 *
 * @param key 共享元素 key（[illustCoverKey]/[novelCoverKey]）
 * @param modifier 外部 Modifier（通常用于顶部对齐）
 * @param height 固定高度（小说 banner 用）；null 时按种子宽高比自适应（插画用）
 * @param contentScale 封面填充方式（默认等比裁剪，与卡片封面一致）
 * @return 无返回值（有种子才渲染占位块）
 */
@Composable
fun HeroSeedCover(
    key: String,
    modifier: Modifier = Modifier,
    height: Dp? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    // 仅消费一次：remember 缓存结果，避免每次重组重复 consume（取出即删，二次取必为 null）
    val seed = remember(key) { HeroSeedStore.consume(key) } ?: return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (height != null) Modifier.height(height)
                else Modifier.aspectRatio(seed.aspectRatio ?: 1f),
            )
            .heroBoundsModifier(key),
    ) {
        PixivImage(
            url = seed.coverUrl.ifBlank { null },
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
        )
    }
}
