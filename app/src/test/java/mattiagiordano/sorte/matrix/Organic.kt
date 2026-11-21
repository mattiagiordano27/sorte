package mattiagiordano.sorte.matrix

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Animazioni "materiche": fumo/inchiostro di luce che si muove sulla matrice
 * e risposte che si condensano granello per granello.
 */
object Organic {

    /** Hash deterministico 0..1 per pixel (la "grana"). */
    private fun hash(x: Int, y: Int, s: Int): Double {
        var h = x * 374761393 + y * 668265263 + s * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFF) / 65535.0
    }

    private fun smooth(a: Double, b: Double, v: Double): Double {
        val t = ((v - a) / (b - a)).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    /**
     * Fumo: quattro masse morbide che girano lente e si fondono (metaball),
     * con una grana che sfarfalla. [t] in secondi, [energy] 0..1.
     */
    fun swirl(t: Double, energy: Double = 1.0, seed: Int = 0): Frame {
        val f = Frame()
        if (energy <= 0.0) return f
        val blobs = List(4) { i ->
            val ph = i * 1.7 + seed * 0.37
            Triple(
                12 + 6.5 * sin(t * (0.9 + i * 0.23) + ph),
                12 + 6.5 * cos(t * (0.7 + i * 0.31) + ph * 1.3),
                3.2 + 1.3 * sin(t * (1.3 + i * 0.4) + ph),
            )
        }
        val frameNo = floor(t * 22).toInt()
        for (y in 0 until Frame.N) for (x in 0 until Frame.N) {
            var s = 0.0
            for ((cx, cy, r) in blobs) {
                val dx = x - cx; val dy = y - cy
                s += r * r / (dx * dx + dy * dy + r * r)
            }
            var v = smooth(0.38, 1.15, s)
            v *= 0.7 + 0.3 * hash(x, y, frameNo + seed * 1000)
            f[x, y] = (v * energy * Frame.FULL * 0.85).toInt()
        }
        return f
    }

    /**
     * La risposta [target] si condensa dal fumo: ogni pixel ha la sua soglia (la grana),
     * il fumo si spegne man mano. [p] 0..1.
     */
    fun condense(target: Frame, p: Double, t: Double, seed: Int = 0, from: Frame? = null): Frame {
        val base = from ?: swirl(t, (1 - p) * (1 - p), seed)
        val f = Frame()
        for (y in 0 until Frame.N) for (x in 0 until Frame.N) {
            val a = ((p - hash(x, y, seed + 7) * 0.7) / 0.3).coerceIn(0.0, 1.0)
            val b = if (from != null) base[x, y] * (1 - a) else base[x, y].toDouble()
            f[x, y] = maxOf(target[x, y] * a, b.toDouble()).toInt()
        }
        return f
    }

    /** Il contrario: [src] si sfalda in fumo. [p] 0..1. */
    fun dissolve(src: Frame, p: Double, t: Double, seed: Int = 0): Frame {
        val smoke = swirl(t, p * p, seed)
        val f = Frame()
        for (y in 0 until Frame.N) for (x in 0 until Frame.N) {
            val a = (((1 - p) - hash(x, y, seed + 7) * 0.7) / 0.3).coerceIn(0.0, 1.0)
            f[x, y] = maxOf(src[x, y] * a, smoke[x, y].toDouble()).toInt()
        }
        return f
    }

    /** Solo i pixel nuovi di [to] rispetto a [from] compaiono a grana (il resto resta fermo). */
    fun grow(from: Frame, to: Frame, p: Double, seed: Int = 0): Frame {
        val f = Frame()
        for (y in 0 until Frame.N) for (x in 0 until Frame.N) {
            val a = ((p - hash(x, y, seed + 11) * 0.7) / 0.3).coerceIn(0.0, 1.0)
            f[x, y] = (from[x, y] + (to[x, y] - from[x, y]) * a).toInt()
        }
        return f
    }
}
