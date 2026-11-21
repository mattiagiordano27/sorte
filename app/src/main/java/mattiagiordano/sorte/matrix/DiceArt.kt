package mattiagiordano.sorte.matrix

import mattiagiordano.sorte.logic.Rng
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dadi come sagome: una per tipo, piena e minimale.
 * Mentre rotola si vede solo la sagoma che gira e si sposta (nessun numero, come un dado in aria);
 * quando si ferma si raddrizza e il numero compare inciso dentro.
 *
 *   d4 triangolo, d6 quadrato, d8 rombo, d10 aquilone, d12 pentagono, d20 esagono, d100 cerchio
 */
object DiceArt {

    /** Posa del dado: rotazione (gradi), scala, spostamento in pixel. */
    data class Pose(val angle: Double = 0.0, val scale: Double = 1.0, val dx: Double = 0.0, val dy: Double = 0.0)

    private const val FILL = 1100          // sagoma: luce tenue
    private const val R = 11.0             // raggio delle sagome in pixel
    private val SAMPLES = listOf(-0.25 to -0.25, 0.25 to -0.25, -0.25 to 0.25, 0.25 to 0.25)

    // ── forme (coordinate con centro 0,0; y verso il basso) ──

    private fun regular(n: Int, startDeg: Double, r: Double = R) = List(n) { i ->
        val a = (startDeg + i * 360.0 / n) * PI / 180
        r * cos(a) to r * sin(a)
    }

    /*
     * Sagome con pendenze "giuste" per la griglia di LED (45°, 2:1, 1:2): i gradini dei lati
     * obliqui vengono tutti uguali, senza pixel storti.
     */
    private val shapes: Map<Int, List<Pair<Double, Double>>?> = mapOf(
        4 to listOf(0.0 to -11.0, 9.0 to 7.0, -9.0 to 7.0),                     // triangolo, lati 2:1, centrato a vista (la base larga sta più in alto)
        6 to regular(4, 45.0, 10.5),                                          // quadrato
        8 to listOf(0.0 to -11.0, 11.0 to 0.0, 0.0 to 11.0, -11.0 to 0.0),     // rombo, lati 45°
        10 to listOf(0.0 to -12.0, 8.0 to 4.0, 0.0 to 12.0, -8.0 to 4.0),      // aquilone: sopra 2:1, sotto 45°
        12 to regular(5, -90.0, 10.5),                                        // pentagono
        20 to listOf(0.0 to -10.5, 9.0 to -6.0, 9.0 to 6.0, 0.0 to 10.5,       // esagono, lati 1:2
            -9.0 to 6.0, -9.0 to -6.0),
        100 to null,                                                          // cerchio
    )

    /** Dove va il numero dentro ogni sagoma (spostamento verticale della riga di base). */
    private fun numberBottom(sides: Int) = when (sides) { 4 -> 17; 10 -> 17; 12 -> 16; else -> 15 }

    private fun inside(sides: Int, x: Double, y: Double): Boolean {
        val poly = shapes[sides] ?: return x * x + y * y <= R * R
        var c = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val (xi, yi) = poly[i]; val (xj, yj) = poly[j]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c
            j = i
        }
        return c
    }

    /** Maschera della sagoma nella posa data: per ogni pixel, è dentro il dado? */
    private fun mask(sides: Int, p: Pose): BooleanArray {
        val a = -p.angle * PI / 180
        val ca = cos(a); val sa = sin(a)
        val m = BooleanArray(Frame.N * Frame.N) { i ->
            val px = i % Frame.N - 12 - p.dx
            val py = i / Frame.N - 12 - p.dy
            // quattro campioni simmetrici per pixel: i lati vengono uguali a destra e a sinistra
            var hits = 0
            for (o in SAMPLES) {
                val sx = px + o.first; val sy = py + o.second
                if (inside(sides, (sx * ca - sy * sa) / p.scale, (sx * sa + sy * ca) / p.scale)) hits++
            }
            hits >= 2
        }
        return prune(m)
    }

    /**
     * Toglie i pixel appesi alla sagoma da un lato solo (le punte di un pixel su rombo,
     * esagono, aquilone…): sui LED quadrati sembrano un errore, non uno spigolo.
     */
    private fun prune(m: BooleanArray): BooleanArray {
        val n = Frame.N
        fun on(x: Int, y: Int) = x in 0 until n && y in 0 until n && m[y * n + x]
        return BooleanArray(m.size) { i ->
            val x = i % n; val y = i / n
            m[i] && (on(x - 1, y).toInt() + on(x + 1, y).toInt() + on(x, y - 1).toInt() + on(x, y + 1).toInt()) >= 2
        }
    }

    private fun Boolean.toInt() = if (this) 1 else 0

    private fun Frame.fill(m: BooleanArray, v: Int) { for (i in m.indices) if (m[i]) px[i] = v }

    private fun Frame.outline(m: BooleanArray, v: Int) {
        val n = Frame.N
        for (i in m.indices) if (m[i]) {
            val x = i % n; val y = i / n
            val edge = x == 0 || y == 0 || x == n - 1 || y == n - 1 ||
                !m[i - 1] || !m[i + 1] || !m[i - n] || !m[i + n]
            if (edge) px[i] = v
        }
    }

    // ── fotogrammi ──

    /** In attesa: la sagoma con il numero di facce inciso, per riconoscere il dado. */
    fun idle(sides: Int, scale: Double = 1.0) = Frame().apply {
        val m = mask(sides, Pose(scale = scale))
        fill(m, FILL)
        if (scale >= 1.0) number(this, sides.toString(), sides, Frame.FULL / 2)
    }

    /** In aria: solo la sagoma, in una posa qualsiasi. */
    fun tumble(sides: Int, p: Pose) = Frame().apply { fill(mask(sides, p), FILL) }

    /**
     * Movimento continuo mentre si scuote: il dado gira sempre nello stesso verso
     * (come quando rotola) e sbanda un po' a destra e sinistra.
     */
    class Tumbler {
        private var angle = Rng.random.nextDouble() * 360
        private val dir = if (Rng.random.nextBoolean()) 1 else -1
        private var dx = 0.0
        private var dy = 0.0
        fun next(): Pose {
            angle += dir * (35 + Rng.random.nextDouble() * 30)
            dx = (dx + Rng.random.nextInt(3) - 1).coerceIn(-2.0, 2.0)
            dy = (dy + Rng.random.nextInt(3) - 1).coerceIn(-2.0, 2.0)
            return Pose(angle, 0.8 + Rng.random.nextDouble() * 0.2, dx, dy)
        }
    }

    /** Ritmo dei rimbalzi di assestamento (ms prima di ciascuno) e pausa prima di mostrare il numero. */
    val SETTLE_DELAYS = longArrayOf(80, 110, 150, 200)
    const val LAND_DELAY = 260L

    /** Posa casuale mentre si scuote. */
    fun randomPose() = Pose(
        angle = Rng.random.nextDouble() * 360,
        scale = 0.78 + Rng.random.nextDouble() * 0.22,
        dx = Rng.random.nextInt(5) - 2.0,
        dy = Rng.random.nextInt(5) - 2.0,
    )

    /**
     * Rimbalzi di assestamento: a partire da una posa storta, ogni rimbalzo
     * oscilla dall'altra parte e si smorza, finché il dado è dritto.
     */
    fun settlePoses(): List<Pose> {
        val start = randomPose()
        val tilt = (if (Rng.random.nextBoolean()) 1 else -1) * (25.0 + Rng.random.nextDouble() * 20)
        return listOf(0.7, -0.4, 0.2, -0.08).map { k ->
            Pose(angle = tilt * k, scale = 1.0 - abs(k) * 0.12, dx = start.dx * abs(k), dy = start.dy * abs(k))
        }
    }

    /**
     * Fermo: sagoma dritta, numero inciso in piena luce.
     * Risultato massimo = contorno in piena luce. 1 = solo il contorno, la sagoma si svuota.
     */
    fun face(sides: Int, value: Int) = Frame().apply {
        val m = mask(sides, Pose())
        when (value) {
            sides -> { fill(m, FILL); outline(m, Frame.FULL); number(this, value.toString(), sides, Frame.FULL) }
            1 -> { outline(m, FILL); number(this, value.toString(), sides, Frame.FULL) }
            else -> { fill(m, FILL); number(this, value.toString(), sides, Frame.FULL) }
        }
    }

    private fun number(f: Frame, text: String, sides: Int, v: Int) {
        val left = (Frame.N - PixelFont.BIG.width(text)) / 2
        val bottom = numberBottom(sides)
        // margine di un pixel spento intorno alle cifre, così si leggono sulla sagoma accesa
        if (v > 0) {
            val halo = Frame()
            PixelFont.BIG.draw(halo, text, left, bottom, 1)
            for (y in 0 until Frame.N) for (x in 0 until Frame.N) if (halo[x, y] > 0) {
                for (oy in -1..1) for (ox in -1..1) if (halo[x + ox, y + oy] == 0) f[x + ox, y + oy] = 0
            }
        }
        PixelFont.BIG.draw(f, text, left, bottom, v)
    }
}
