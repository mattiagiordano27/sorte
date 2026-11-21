package mattiagiordano.sorte.matrix

/**
 * Phone (4a) Pro: matrice 13×13. Riduce un fotogramma 25×25 tenendo, per ogni LED piccolo,
 * il punto più luminoso della zona che copre (così le linee sottili non spariscono).
 */
object Small {
    const val N = 13

    fun to13(f: Frame): IntArray {
        val src = f.toArray()
        return IntArray(N * N) { i ->
            val cx = i % N; val cy = i / N
            val x0 = cx * Frame.N / N; val x1 = ((cx + 1) * Frame.N / N - 1).coerceAtLeast(x0)
            val y0 = cy * Frame.N / N; val y1 = ((cy + 1) * Frame.N / N - 1).coerceAtLeast(y0)
            var m = 0
            for (y in y0..y1) for (x in x0..x1) m = maxOf(m, src[y * Frame.N + x])
            m
        }
    }
}
