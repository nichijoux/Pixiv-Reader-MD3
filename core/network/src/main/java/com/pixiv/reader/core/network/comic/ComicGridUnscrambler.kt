package com.pixiv.reader.core.network.comic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import java.security.MessageDigest

/**
 * COMIC 正文图 gridshuffle 去扰。
 *
 * 服务端下发的正文图是「打乱图」：把图按 [gridsize]（实测 32px）切方块后，
 * 逐块行做 Fisher–Yates 洗牌（每行各自的置换，从同一条随机流顺序抽取），
 * 宽高不能整除 gridsize 的边缘条带不打乱。还原即在客户端复现同一条随机流、
 * 生成同样的置换，把打乱图第 c 块搬回第 `perm[c]` 块。
 *
 * 随机流派生与运算语义来自开源参考实现（Lumingtianze/pixiv_comic_dl，Rust），
 * 已于 2026-09-15 用真实章节图端到端验证（打乱图 → 还原 → 画面完整正确）：
 * 1. seed = SHA256(STATIC_KEY + page.key) 前 16 字节，按小端解成 4 个 u32；
 * 2. XorShift128 状态机，先丢弃 100 次输出；
 * 3. 每行：`arr = [0..cols-1]`，i 从 cols-1 降到 1，`j = next() mod (i+1)`，
 *    交换 arr[i] 与 arr[j]。
 *
 * 运算全程用 Kotlin Int（JVM 32 位补码，乘法 / 移位 / 异或天然按 u32 wrapping 语义）。
 */
object ComicGridUnscrambler {

    /** 官方 web viewer 内置的固定扰动常量（混淆强度的来源，更换需重新抓包）。 */
    const val STATIC_KEY = "4wXCKprMMoxnyJ3PocJFs4CYbfnbazNe"

    /** 预热丢弃的输出次数。 */
    private const val WARM_UP = 100

    /**
     * 生成每块行的列置换（纯函数，可单测锁定算法）。
     *
     * @param pageKey 页签名 key（read_v4 返回的 `ComicPage.key`）
     * @param cols 整除部分的块列数（width / gridsize 向下取整）
     * @param rows 整除部分的块行数（height / gridsize 向下取整）
     * @return 长度 rows 的置换表，每项为 0..cols-1 的一个排列
     */
    fun permutationsFor(pageKey: String, cols: Int, rows: Int): List<IntArray> {
        // 每行从同一条流继续抽取（不重置状态），与服务端打乱顺序一致
        val xs = XorShift128(pageKey)
        val perms = ArrayList<IntArray>(rows.coerceAtLeast(0))
        repeat(rows) {
            val arr = IntArray(cols) { it }
            // Fisher–Yates：i 从末位降到 1，j 取余限制在未洗牌前缀内
            for (i in cols - 1 downTo 1) {
                val j = Integer.remainderUnsigned(xs.next(), i + 1)
                val t = arr[i]
                arr[i] = arr[j]
                arr[j] = t
            }
            perms.add(arr)
        }
        return perms
    }

    /**
     * 还原一张打乱图。不构成打乱条件（尺寸不足两个块 / gridsize 非法）时原样返回。
     *
     * @param src 打乱原图（调用方负责回收）
     * @param gridsize 方块边长（read_v4 返回的 `ComicPage.gridsize`）
     * @param pageKey 页签名 key（参与种子派生）
     * @return 还原后的新 Bitmap（ARGB_8888）
     */
    fun unscramble(src: Bitmap, gridsize: Int, pageKey: String): Bitmap {
        val g = gridsize.coerceAtLeast(1)
        val cols = src.width / g
        val rows = src.height / g
        // 少于两个可换位的块时洗牌无意义（未打乱变换的图 / 异常数据），原样返回
        if (cols < 2 || rows < 1) return src

        val out = src.copy(Bitmap.Config.ARGB_8888, false) ?: return src
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val perms = permutationsFor(pageKey, cols, rows)
        for (r in 0 until rows) {
            val perm = perms[r]
            val y = r * g
            for (c in 0 until cols) {
                // 还原方向（已实测验证）：打乱图第 c 块 → 还原图第 perm[c] 块
                val sx = c * g
                val dx = perm[c] * g
                canvas.drawBitmap(
                    src,
                    Rect(sx, y, sx + g, y + g),
                    Rect(dx, y, dx + g, y + g),
                    paint,
                )
            }
        }
        return out
    }

    /**
     * XorShift128 伪随机流（u32 语义）。next() 一步的变换：
     * ```
     * s1 = s[1]; r = rotl(s1 * 5, 7) * 9; t = s1 << 9
     * s[2] ^= s[0]; s[3] ^= s[1]; s[1] ^= s[2]; s[0] ^= s[3]
     * s[2] ^= t; s[3] = rotl(s[3], 11)
     * ```
     */
    private class XorShift128(pageKey: String) {

        /** 4 个 u32 状态字（小端读入）。 */
        private val s = IntArray(4)

        init {
            // 种子：SHA256(STATIC_KEY + pageKey) 前 16 字节，小端 4 × u32
            val digest = MessageDigest.getInstance("SHA-256")
                .digest((STATIC_KEY + pageKey).toByteArray())
            for (i in 0 until 4) {
                var v = 0
                for (b in 0 until 4) v = v or ((digest[i * 4 + b].toInt() and 0xFF) shl (b * 8))
                s[i] = v
            }
            // 预热：与服务端打乱时同样丢弃前 100 次输出
            repeat(WARM_UP) { next() }
        }

        /** 推进状态并返回一个 u32 输出（Kotlin Int，按 32 位 wrapping）。 */
        fun next(): Int {
            val s1 = s[1]
            val r = Integer.rotateLeft(s1 * 5, 7) * 9
            val t = s1 shl 9
            s[2] = s[2] xor s[0]
            s[3] = s[3] xor s[1]
            s[1] = s[1] xor s[2]
            s[0] = s[0] xor s[3]
            s[2] = s[2] xor t
            s[3] = Integer.rotateLeft(s[3], 11)
            return r
        }
    }
}
