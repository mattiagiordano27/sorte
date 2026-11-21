package mattiagiordano.sorte.matrix

/** Rumore morbido (per fumo, fiamme, carta): valori lisci 0..1, deterministici. */
object Noise {
    private fun h(x: Int, y: Int, s: Int): Double {
        var k = x * 374761393 + y * 668265263 + s * 2147483647
        k = (k xor (k ushr 13)) * 1274126177
        return ((k xor (k ushr 16)) and 0xFFFF) / 65535.0
    }

    fun vnoise(x: Double, y: Double, s: Int = 0): Double {
        val x0 = kotlin.math.floor(x).toInt(); val y0 = kotlin.math.floor(y).toInt()
        val fx = x - x0; val fy = y - y0
        val sx = fx * fx * (3 - 2 * fx); val sy = fy * fy * (3 - 2 * fy)
        val a = h(x0, y0, s); val b = h(x0 + 1, y0, s); val c = h(x0, y0 + 1, s); val d = h(x0 + 1, y0 + 1, s)
        return a + (b - a) * sx + (c - a) * sy + (a - b - c + d) * sx * sy
    }
}
