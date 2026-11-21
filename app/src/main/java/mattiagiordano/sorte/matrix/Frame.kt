package mattiagiordano.sorte.matrix

import kotlin.math.atan2

/** Un fotogramma della Glyph Matrix del Phone (3): 25×25, solo il cerchio da 489 LED è visibile. */
class Frame {
    val px = IntArray(N * N)

    operator fun set(x: Int, y: Int, v: Int) {
        if (x in 0 until N && y in 0 until N) px[y * N + x] = v.coerceIn(0, FULL)
    }

    operator fun get(x: Int, y: Int): Int = if (x in 0 until N && y in 0 until N) px[y * N + x] else 0

    /** Anello lungo il bordo del cerchio. [dashed] = un pixel sì e uno no. */
    fun ring(brightness: Int, dashed: Boolean = false) {
        RING.forEachIndexed { i, p -> if (!dashed || i % 2 == 0) this[p.first, p.second] = brightness }
    }

    /** Arco luminoso che gira lungo il bordo (animazione di attesa). [head] = posizione 0..1. */
    fun spinner(head: Float, length: Int = 12) {
        val start = (head * RING.size).toInt()
        for (k in 0 until length) {
            val p = RING[(start - k + RING.size * 2) % RING.size]
            this[p.first, p.second] = FULL * (length - k) / length
        }
    }

    fun toArray(): IntArray = IntArray(N * N) { i -> if (MASK[i]) px[i] else 0 }

    companion object {
        const val N = 25
        const val FULL = 4095
        const val DIM = 900

        fun inCircle(x: Int, y: Int) = (x - 12) * (x - 12) + (y - 12) * (y - 12) <= 156

        private val MASK = BooleanArray(N * N) { inCircle(it % N, it / N) }

        /** Pixel del bordo esterno, ordinati in senso orario partendo dall'alto. */
        val RING: List<Pair<Int, Int>> = buildList {
            for (y in 0 until N) for (x in 0 until N) {
                val d = (x - 12) * (x - 12) + (y - 12) * (y - 12)
                if (d in 122..156) add(x to y)
            }
        }.sortedBy { (x, y) ->
            val a = atan2((x - 12).toDouble(), -(y - 12).toDouble())
            if (a < 0) a + 2 * Math.PI else a
        }
    }
}
