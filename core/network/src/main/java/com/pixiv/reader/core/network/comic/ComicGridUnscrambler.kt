package com.pixiv.reader.core.network.comic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import java.security.MessageDigest

/**
 * COMIC 正文图 gridshuffle 去扰：按 gridsize（实测 32px）切块，用同一 XorShift128 流
 * （seed=SHA256(STATIC_KEY+pageKey) 前 16 字节小端 4×u32，预热丢弃 100 次）逐块行做
 * Fisher–Yates 置换，将打乱图第 c 块搬回 perm[c]；不能整除的边缘条带不打乱。
 */
object ComicGridUnscrambler {

    /** 官方 web viewer 固定扰动常量，服务端更换时需重新抓包。 */
    const val STATIC_KEY = "4wXCKprMMoxnyJ3PocJFs4CYbfnbazNe"

    private const val WARM_UP = 100

    fun permutationsFor(pageKey: String, cols: Int, rows: Int): List<IntArray> {
        // 每行从同一流继续抽取（不重置状态），与服务端打乱顺序一致
        val xs = XorShift128(pageKey)
        val perms = ArrayList<IntArray>(rows.coerceAtLeast(0))
        repeat(rows) {
            val arr = IntArray(cols) { it }
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

    /** 还原打乱图；不满足打乱条件或无法分配副本时返回原 [src]（调用方负责回收）。 */
    fun unscramble(src: Bitmap, gridsize: Int, pageKey: String): Bitmap {
        if (gridsize <= 0) return src
        val g = gridsize
        val cols = src.width / g
        val rows = src.height / g
        if (cols < 2 || rows < 1) return src

        // 先复制原图以保留网格外未打乱的边缘条带
        val out = src.copy(Bitmap.Config.ARGB_8888, true) ?: return src
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val perms = permutationsFor(pageKey, cols, rows)
        for (r in 0 until rows) {
            val perm = perms[r]
            val y = r * g
            for (c in 0 until cols) {
                // 还原方向：打乱图第 c 块 → 还原图第 perm[c] 块
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

    // Kotlin Int 32 位补码天然满足 u32 wrapping 语义
    private class XorShift128(pageKey: String) {

        private val s = IntArray(4)

        init {
            // seed = SHA256(STATIC_KEY + pageKey) 前 16 字节，按小端解成 4 个 u32
            val digest = MessageDigest.getInstance("SHA-256")
                .digest((STATIC_KEY + pageKey).toByteArray())
            for (i in 0 until 4) {
                var v = 0
                for (b in 0 until 4) v = v or ((digest[i * 4 + b].toInt() and 0xFF) shl (b * 8))
                s[i] = v
            }
            repeat(WARM_UP) { next() }
        }

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
