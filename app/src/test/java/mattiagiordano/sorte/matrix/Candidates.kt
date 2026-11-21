package mattiagiordano.sorte.matrix

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * LABORATORIO: quattro "materie oracolari" candidate, da confrontare prima di sceglierne una.
 * Ognuna produce un filmato: attesa → pensiero → la risposta prende forma.
 * Tutte usano la persistenza dei fosfori (le luci si spengono lente, come su un vecchio schermo).
 */
object Candidates {

    const val DT = 0.04
    enum class Kind { VAPORE, BRACI, INCHIOSTRO, PENDOLO, NUVOLE, NEBBIA, FIATO, INCENSO }

    /** Fasi del filmato, in secondi. */
    const val IDLE_S = 3.0
    const val THINK_S = 1.8
    const val REVEAL_S = 1.4
    const val HOLD_S = 1.0

    fun film(kind: Kind, target: Frame, seed: Long = 7): List<Frame> = when (kind) {
        Kind.VAPORE -> Particles(seed, target, vapor = true).run()
        Kind.BRACI -> Particles(seed, target, vapor = false).run()
        Kind.INCHIOSTRO -> Ink(seed, target).run()
        Kind.PENDOLO -> Pendulum(target).run()
        Kind.NUVOLE -> Clouds(seed, target).run()
        Kind.NEBBIA -> Fog(seed, target).run()
        Kind.FIATO -> Breath(seed, target).run()
        Kind.INCENSO -> Incense(seed, target).run()
    }

    // ───────────── strumenti comuni ─────────────

    private const val N = Frame.N

    /** Buffer di luce 0..1 con persistenza dei fosfori. */
    private class Glow(val decay: Double) {
        val v = DoubleArray(N * N)
        private val fresh = DoubleArray(N * N)
        fun splat(x: Double, y: Double, a: Double) {
            // distribuisce la luce sui 4 pixel vicini (movimento morbido, non a scatti)
            val x0 = kotlin.math.floor(x).toInt(); val y0 = kotlin.math.floor(y).toInt()
            val fx = x - x0; val fy = y - y0
            add(x0, y0, a * (1 - fx) * (1 - fy)); add(x0 + 1, y0, a * fx * (1 - fy))
            add(x0, y0 + 1, a * (1 - fx) * fy); add(x0 + 1, y0 + 1, a * fx * fy)
        }
        fun add(x: Int, y: Int, a: Double) { if (x in 0 until N && y in 0 until N) fresh[y * N + x] += a }
        fun set(i: Int, a: Double) { fresh[i] = maxOf(fresh[i], a) }
        /** Chiude il fotogramma: nuova luce + scia che decade. */
        fun commit(): Frame {
            val f = Frame()
            for (i in v.indices) {
                v[i] = maxOf(fresh[i].coerceAtMost(1.0), v[i] * decay)
                fresh[i] = 0.0
                f.px[i] = (v[i] * Frame.FULL).toInt()
            }
            return f
        }
    }

    private fun targets(t: Frame) = (0 until N * N).filter { t.px[it] > 0 }

    private fun ease(u: Double) = u.coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }

    // ───────────── VAPORE e BRACI: motore a particelle ─────────────

    private class P(
        var x: Double, var y: Double, var vx: Double, var vy: Double,
        var age: Double, val life: Double, val ph: Double, var target: Int = -1, var arrived: Boolean = false,
    )

    private class Particles(seed: Long, val target: Frame, val vapor: Boolean) {
        val r = Random(seed)
        val glow = Glow(if (vapor) 0.78 else 0.86)
        val ps = mutableListOf<P>()
        var t = 0.0

        fun spawn(energy: Double) {
            if (vapor) {
                // dal "braciere" in basso al centro
                ps += P(12 + r.nextGaussian() * 2.2, 22.5 + r.nextDouble(), r.nextGaussian() * 0.4,
                    -(2.5 + 3 * energy) - r.nextDouble(), 0.0, 2.6 + r.nextDouble() * 1.6, r.nextDouble() * 6.28)
            } else {
                val a = r.nextDouble() * 2 * PI; val d = 3 + r.nextDouble() * 8
                ps += P(12 + cos(a) * d, 12 + sin(a) * d, r.nextGaussian(), r.nextGaussian(),
                    0.0, 3.0 + r.nextDouble() * 3, r.nextDouble() * 6.28)
            }
        }

        fun move(p: P, energy: Double) {
            if (vapor) {
                // sale, ondeggia sempre di più man mano che si alza, si arriccia quando "pensa"
                val h = (23 - p.y) / 20
                p.vx += (sin(p.age * (1.6 + energy) + p.ph) * (0.8 + 2.5 * h) * (0.6 + energy) - p.vx) * 0.12
                p.vx += (12 - p.x) * 0.02 * energy
                p.vy += (-(2.2 + 4 * energy) - p.vy) * 0.05
            } else {
                // braci: vagano, e pensando girano intorno al centro
                val dx = p.x - 12; val dy = p.y - 12
                p.vx += r.nextGaussian() * 1.6 - dy * 0.35 * energy - dx * 0.05
                p.vy += r.nextGaussian() * 1.6 + dx * 0.35 * energy - dy * 0.05
                p.vx *= 0.9; p.vy *= 0.9
            }
            p.x += p.vx * DT; p.y += p.vy * DT; p.age += DT
        }

        fun brightness(p: P, energy: Double): Double {
            val u = p.age / p.life
            val env = if (vapor) sin(PI * u.coerceIn(0.0, 1.0)) else (1 - u).coerceAtLeast(0.0)
            val flicker = if (vapor) 1.0 else 0.65 + 0.35 * sin(p.age * 17 + p.ph)
            return env * flicker * (0.35 + 0.45 * energy)
        }

        /**
         * VAPORE → PAROLE: il pennacchio continua a salire e condensa nelle lettere dal basso verso l'alto,
         * come su un vetro freddo. Formata la parola, sottili fili continuano a salire dalle lettere.
         */
        fun condense(): List<Frame> {
            val out = mutableListOf<Frame>()
            val tg = targets(target).sortedByDescending { it / N }           // dal basso verso l'alto
            val filled = DoubleArray(N * N)
            class C(var x: Double, var y: Double, val tx: Int, val ty: Int, var age: Double, val ph: Double)
            val cs = mutableListOf<C>()
            var next = 0
            val tops = (0 until N).mapNotNull { x -> tg.filter { it % N == x }.minByOrNull { it / N } }   // cima di ogni colonna
            val steps = ((REVEAL_S + HOLD_S) / DT).toInt()
            for (i in 0 until steps) {
                val t = i * DT
                // il vapore residuo dell'attesa/pensiero continua a salire e si dirada
                ps.forEach { move(it, 0.5); glow.splat(it.x, it.y, brightness(it, 0.5) * (1 - t / REVEAL_S).coerceAtLeast(0.0)) }
                ps.removeAll { it.age > it.life || it.y < -2 }
                // nuovi sbuffi dal basso, ognuno destinato a un pixel della parola
                if (t < REVEAL_S * 0.8) repeat(4) {
                    if (next < tg.size * 2) {
                        val idx = tg[next % tg.size]; next++
                        cs += C((idx % N) + r.nextGaussian() * 2.5, 23.5 + r.nextDouble(), idx % N, idx / N, 0.0, r.nextDouble() * 6.28)
                    }
                }
                cs.forEach { c ->
                    c.age += DT
                    // sale ondeggiando, e l'ondeggiare si calma vicino alla sua lettera
                    val near = ((c.y - c.ty) / 10).coerceIn(0.0, 1.0)
                    c.x += ((c.tx - c.x) * 2.2 + sin(c.age * 6 + c.ph) * 3 * near) * DT
                    c.y -= (5.5 + 4 * near) * DT
                    if (c.y <= c.ty + 0.3) { filled[c.ty * N + c.tx] = minOf(1.0, filled[c.ty * N + c.tx] + 0.5); c.age = 99.0 }
                    else glow.splat(c.x, c.y, 0.32)
                }
                cs.removeAll { it.age > 50 }
                for (idx in filled.indices) if (filled[idx] > 0) glow.set(idx, filled[idx] * target.px[idx] / Frame.FULL)
                // a parola formata: fili sottili salgono dalle cime delle lettere
                if (t > REVEAL_S * 0.7 && r.nextDouble() < 0.5) {
                    val top = tops[r.nextInt(tops.size)]
                    ps += P((top % N).toDouble(), (top / N) - 1.0, 0.0, -2.0, 0.0, 1.2 + r.nextDouble(), r.nextDouble() * 6.28)
                }
                out += glow.commit()
            }
            return out
        }

        fun run(): List<Frame> {
            val out = mutableListOf<Frame>()
            // ATTESA e PENSIERO
            val steps = ((IDLE_S + THINK_S) / DT).toInt()
            for (i in 0 until steps) {
                t = i * DT
                val energy = if (t < IDLE_S) 0.0 else ease((t - IDLE_S) / 0.6)
                val rate = (if (vapor) 5.0 else 1.5) + energy * (if (vapor) 22 else 6)
                if (r.nextDouble() < rate * DT) spawn(energy)
                if (!vapor && ps.size < 4) spawn(energy)
                ps.forEach { move(it, energy); glow.splat(it.x, it.y, brightness(it, energy)) }
                ps.removeAll { it.age > it.life || it.y < -2 }
                out += glow.commit()
            }
            if (vapor) { out += condense(); return out }
            // RISPOSTA: ogni pixel della risposta richiama una particella
            val tg = targets(target)
            while (ps.size < tg.size) spawn(1.0)
            ps.shuffle(r)
            tg.forEachIndexed { k, idx -> ps[k].target = idx }
            val settled = DoubleArray(N * N)
            val rsteps = ((REVEAL_S + HOLD_S) / DT).toInt()
            for (i in 0 until rsteps) {
                val u = i * DT / REVEAL_S
                ps.forEach { p ->
                    if (p.target >= 0) {
                        val tx = (p.target % N).toDouble(); val ty = (p.target / N).toDouble()
                        // molla smorzata verso il proprio pixel: arriva con un piccolo assestamento
                        val k = 14 + 30 * u
                        p.vx += ((tx - p.x) * k - p.vx * 7) * DT
                        p.vy += ((ty - p.y) * k - p.vy * 7) * DT
                        p.x += p.vx * DT; p.y += p.vy * DT
                        if (hypot(tx - p.x, ty - p.y) < 0.35) p.arrived = true
                        if (p.arrived) settled[p.target] = minOf(1.0, settled[p.target] + 0.18)
                        glow.splat(p.x, p.y, if (p.arrived) 0.0 else 0.8)
                    } else {
                        move(p, 0.3); glow.splat(p.x, p.y, brightness(p, 0.2) * (1 - u).coerceAtLeast(0.0))
                    }
                }
                for (idx in settled.indices) if (settled[idx] > 0) glow.set(idx, settled[idx] * target.px[idx] / Frame.FULL)
                out += glow.commit()
            }
            return out
        }
    }

    // ───────────── INCHIOSTRO: goccia e cerchi nell'acqua ─────────────

    private class Ink(seed: Long, val target: Frame) {
        val r = Random(seed)
        val glow = Glow(0.8)

        /** Un'onda: anello di raggio crescente, sottile, che si affievolisce. */
        fun ring(cx: Double, cy: Double, age: Double, strength: Double, speed: Double = 7.0) {
            if (age < 0) return
            val rad = age * speed
            val fade = exp(-age * 1.3) * strength
            for (y in 0 until N) for (x in 0 until N) {
                val d = hypot(x - cx, y - cy)
                val w = exp(-((d - rad) * (d - rad)) / 0.9)
                // un po' di irregolarità, come acqua vera
                val wob = 0.85 + 0.15 * sin(x * 1.7 + y * 2.3 + age * 9)
                glow.add(x, y, w * fade * wob)
            }
        }

        fun drop(t: Double, t0: Double, cx: Double, cy: Double, strength: Double) {
            val fall = 0.35
            if (t < t0) return
            if (t < t0 + fall) {
                val u = (t - t0) / fall
                glow.splat(cx, cy - 10 * (1 - u * u), 0.9 * strength)        // la goccia che cade, accelerando
            } else {
                val a = t - t0 - fall
                ring(cx, cy, a, strength)
                ring(cx, cy, a - 0.25, strength * 0.6)
                ring(cx, cy, a - 0.5, strength * 0.35)
                if (a < 0.15) glow.splat(cx, cy, (0.15 - a) / 0.15)       // il tocco sulla superficie
            }
        }

        fun run(): List<Frame> {
            val out = mutableListOf<Frame>()
            val drops = mutableListOf(0.2 to 0.35, 1.7 to 0.3)            // attesa: gocce rare e lievi
            var tt = IDLE_S
            while (tt < IDLE_S + THINK_S) { drops += tt to 0.8; tt += 0.32 + r.nextDouble() * 0.2 }
            val pos = drops.map { (12 + r.nextGaussian() * (if (it.second > 0.5) 3.0 else 1.5)) to (12 + r.nextGaussian() * 2) }
            val total = IDLE_S + THINK_S + REVEAL_S + HOLD_S
            val reveal0 = IDLE_S + THINK_S
            var i = 0
            while (i * DT < total) {
                val t = i * DT
                drops.forEachIndexed { k, (t0, s) -> drop(t, t0, pos[k].first, pos[k].second, s) }
                if (t >= reveal0) {
                    // goccia finale al centro: la risposta affiora dove passa l'onda
                    drop(t, reveal0, 12.0, 12.0, 1.0)
                    val rad = (t - reveal0 - 0.35) * 7.0
                    for (idx in 0 until N * N) if (target.px[idx] > 0) {
                        val d = hypot((idx % N) - 12.0, (idx / N) - 12.0)
                        val a = ((rad - d) / 2.5).coerceIn(0.0, 1.0)
                        glow.set(idx, a * target.px[idx] / Frame.FULL)
                    }
                }
                out += glow.commit()
                i++
            }
            return out
        }
    }

    // ───────────── PENDOLO del rabdomante ─────────────

    private class Pendulum(val target: Frame) {
        val glow = Glow(0.84)
        val px = 12.0; val py = 0.5; val len = 15.0

        fun run(): List<Frame> {
            val out = mutableListOf<Frame>()
            var th = 0.12; var w = 0.0
            val total = IDLE_S + THINK_S + REVEAL_S + HOLD_S
            var i = 0
            var stopAt = -1.0
            while (i * DT < total) {
                val t = i * DT
                val thinking = t >= IDLE_S && t < IDLE_S + THINK_S
                val revealing = t >= IDLE_S + THINK_S
                // fisica del pendolo: gravità, attrito; pensando riceve spinte (la mano trema)
                val g = 9.8 * 8 / len
                var acc = -g * sin(th) - w * (if (revealing) 2.6 else 0.15)
                if (thinking && abs(th) < 0.15) acc += 9.0 * (if (w >= 0) 1 else -1)
                if (!thinking && !revealing) acc += 0.25 * sin(t * 2.1)            // attesa: oscillazione minima
                w += acc * DT; th += w * DT
                val bx = px + sin(th) * len; val by = py + cos(th) * len
                // filo tenue + peso luminoso
                for (k in 2..13 step 3) glow.splat(px + sin(th) * k, py + cos(th) * k, 0.10)
                glow.splat(bx, by, 1.0); glow.splat(bx + 0.6, by, 0.6); glow.splat(bx, by + 0.6, 0.6)
                if (revealing) {
                    if (stopAt < 0 && abs(w) < 0.25 && abs(th) < 0.06) stopAt = t
                    if (stopAt < 0 && t > IDLE_S + THINK_S + 1.0) stopAt = t   // non aspettare all'infinito
                    if (stopAt > 0) {
                        // fermo: la risposta si irradia dal punto in cui si è fermato
                        val rad = (t - stopAt) * 22
                        for (idx in 0 until N * N) if (target.px[idx] > 0) {
                            val d = hypot((idx % N) - bx, (idx / N) - by)
                            glow.set(idx, ((rad - d) / 4).coerceIn(0.0, 1.0) * target.px[idx] / Frame.FULL)
                        }
                    }
                }
                out += glow.commit()
                i++
            }
            return out
        }
    }

    // ───────────── rumore morbido (per nuvole, nebbia, condensa) ─────────────

    private fun h(x: Int, y: Int, s: Int): Double {
        var k = x * 374761393 + y * 668265263 + s * 2147483647
        k = (k xor (k ushr 13)) * 1274126177
        return ((k xor (k ushr 16)) and 0xFFFF) / 65535.0
    }

    /** Rumore a valori, liscio, 0..1. */
    fun vnoise(x: Double, y: Double, s: Int = 0): Double {
        val x0 = kotlin.math.floor(x).toInt(); val y0 = kotlin.math.floor(y).toInt()
        val fx = x - x0; val fy = y - y0
        val sx = fx * fx * (3 - 2 * fx); val sy = fy * fy * (3 - 2 * fy)
        val a = h(x0, y0, s); val b = h(x0 + 1, y0, s); val c = h(x0, y0 + 1, s); val d = h(x0 + 1, y0 + 1, s)
        return a + (b - a) * sx + (c - a) * sy + (a - b - c + d) * sx * sy
    }

    /** Rumore frattale: più ottave, aspetto "nuvoloso". */
    fun fbm(x: Double, y: Double, s: Int = 0, oct: Int = 3): Double {
        var v = 0.0; var a = 0.5; var f = 1.0
        repeat(oct) { v += a * vnoise(x * f, y * f, s + it * 31); a *= 0.5; f *= 2.1 }
        return v / (1 - Math.pow(0.5, oct.toDouble()))   // somma delle ampiezze: risultato di nuovo 0..1
    }

    private fun total() = IDLE_S + THINK_S + REVEAL_S + HOLD_S

    /** Rivela [target] dove [mask](idx) ≥ soglia; restituisce il livello 0..1 per pixel. */
    private inline fun reveal(glow: Glow, target: Frame, level: (Int) -> Double) {
        for (idx in 0 until N * N) if (target.px[idx] > 0) glow.set(idx, level(idx).coerceIn(0.0, 1.0) * target.px[idx] / Frame.FULL)
    }

    // ───────────── NUVOLE ─────────────

    private class Clouds(seed: Long, val target: Frame) {
        val glow = Glow(0.6); val s = seed.toInt()
        fun run(): List<Frame> {
            val out = mutableListOf<Frame>()
            val r0 = IDLE_S + THINK_S
            var i = 0
            while (i * DT < total()) {
                val t = i * DT
                val think = ease((t - IDLE_S) / 0.8) * (if (t < r0) 1.0 else 0.0)
                val part = if (t >= r0) ease((t - r0) / REVEAL_S) else 0.0
                for (y in 0 until N) for (x in 0 until N) {
                    // il cielo scorre; pensando le nuvole vengono risucchiate verso il centro e ribollono
                    val dx = x - 12.0; val dy = y - 12.0
                    val pull = 1 - 0.45 * think
                    val sx = 12 + dx * pull + t * 1.4; val sy = 12 + dy * pull
                    var d = fbm(sx / 6.5 + t * 0.05 * (1 + 3 * think), sy / 5.5 - t * 0.12 * think, s, 4)
                    val cover = 0.52 - 0.12 * think + 0.35 * part                 // soglia: più alta = cielo più sereno
                    // le nuvole si aprono dal centro verso fuori
                    val open = part * (1 - hypot(dx, dy) / 14)
                    var v = ((d - cover - open * 0.3) / 0.16).coerceIn(0.0, 1.0)
                    v = v * (0.22 + 0.26 * think) * (0.85 + 0.15 * vnoise(x * 0.9, y * 0.9 + t * 3, s + 9))
                    glow.add(x, y, v)
                }
                if (t >= r0) reveal(glow, target) { ((t - r0) / (REVEAL_S * 0.8)) * 1.2 - hypot((it % N) - 12.0, (it / N) - 12.0) / 20 }
                out += glow.commit(); i++
            }
            return out
        }
    }

    // ───────────── NEBBIA ─────────────

    private class Fog(seed: Long, val target: Frame) {
        val glow = Glow(0.55); val s = seed.toInt()
        fun run(): List<Frame> {
            val out = mutableListOf<Frame>()
            val r0 = IDLE_S + THINK_S
            var i = 0
            while (i * DT < total()) {
                val t = i * DT
                val think = ease((t - IDLE_S) / 1.0) * (if (t < r0) 1.0 else 0.0)
                val lift = if (t >= r0) ease((t - r0) / REVEAL_S) else 0.0
                // livello della nebbia: bassa in attesa, sale pensando, si solleva e svanisce alla risposta
                val level = 19 - 13 * think - (if (t >= r0) 13.0 else 0.0) - 18 * lift
                for (y in 0 until N) for (x in 0 until N) {
                    // veli orizzontali a velocità diverse (parallasse)
                    var d = 0.0
                    for (b in 0..2) d += vnoise(x / (7.0 - b) + t * (0.35 + b * 0.45), y / 2.2 + b * 7.0, s + b) / 3
                    val height = ((y - level) / 6.0).coerceIn(0.0, 1.0)
                    val v = (d * 1.4 - 0.25).coerceIn(0.0, 1.0) * height * (0.35 + 0.25 * think) * (1 - lift)
                    glow.add(x, y, v)
                }
                if (t >= r0) reveal(glow, target) { ((t - r0) / REVEAL_S) * 1.8 - (it / N) / 30.0 }
                out += glow.commit(); i++
            }
            return out
        }
    }

    // ───────────── FIATO SUL VETRO ─────────────

    private class Breath(seed: Long, val target: Frame) {
        val glow = Glow(0.5); val s = seed.toInt()
        fun run(): List<Frame> {
            val out = mutableListOf<Frame>()
            val r0 = IDLE_S + THINK_S
            // ordine di scrittura col dito: colonna per colonna, da sinistra
            val strokes = (0 until N * N).filter { target.px[it] > 0 }.sortedBy { (it % N) * 100 + (it / N) }
            val writeS = 0.9
            var i = 0
            while (i * DT < total()) {
                val t = i * DT
                // attesa: piccoli fiati regolari; pensando: un fiato lungo che appanna tutto
                val breath = if (t < IDLE_S) 0.25 + 0.2 * kotlin.math.max(0.0, sin(t * 2 * PI / 2.6)) else 0.0
                val big = if (t >= IDLE_S && t < r0 + writeS) ease((t - IDLE_S) / 0.9) else 0.0
                val evap = if (t >= r0 + writeS) ease((t - r0 - writeS) / 0.8) else 0.0
                val radius = if (t < IDLE_S) 5 + 4 * breath else 6 + 9 * big
                val written = if (t >= r0) ((t - r0) / writeS * strokes.size).toInt().coerceAtMost(strokes.size) else 0
                val cleared = strokes.take(written).toHashSet()
                for (y in 0 until N) for (x in 0 until N) {
                    val d = hypot(x - 12.0, y - 12.0)
                    val edge = ((radius - d) / 3.0).coerceIn(0.0, 1.0)
                    // condensa a grana: goccioline
                    val grain = 0.7 + 0.3 * h(x, y, s + 3) + 0.1 * vnoise(x * 0.6, y * 0.6, s)
                    var v = edge * grain * (if (t < IDLE_S) breath else 0.45 * (0.5 * big + 0.5 * (1 - evap))) * (1 - evap)
                    val idx = y * N + x
                    if (idx in cleared) v = 0.0                                  // il dito toglie la condensa
                    glow.add(x, y, v)
                }
                if (evap > 0) reveal(glow, target) { evap * 1.3 }                  // evaporando, le lettere restano accese
                out += glow.commit(); i++
            }
            return out
        }
    }

    // ───────────── INCENSO: un filo solo che si arriccia ─────────────

    private class Incense(seed: Long, val target: Frame) {
        val glow = Glow(0.9)
        val r = Random(seed)
        class S(var x: Double, var y: Double, var age: Double)
        fun run(): List<Frame> {
            val out = mutableListOf<Frame>()
            val ps = mutableListOf<S>()
            val r0 = IDLE_S + THINK_S
            val order = (0 until N * N).filter { target.px[it] > 0 }.sortedBy { (it % N) * 100 + (it / N) }
            var i = 0
            while (i * DT < total()) {
                val t = i * DT
                val think = ease((t - IDLE_S) / 0.7) * (if (t < r0) 1.0 else 0.0)
                if (t < r0) repeat(3) { ps += S(12.0 + r.nextGaussian() * 0.15, 22.0, 0.0) }
                ps.forEach { p ->
                    // campo di flusso: il filo sale e si arriccia; pensando le spire si stringono
                    val a = sin(p.y * 0.45 + t * 1.3) * (0.6 + 1.3 * think) + cos(p.age * (2 + 3 * think)) * think * 1.2
                    p.x += a * DT * 3; p.y -= (3.2 + 2 * think) * DT; p.age += DT
                    glow.splat(p.x, p.y, 0.09 * (1 - p.age / 4.5).coerceAtLeast(0.0) * (1 + 0.6 * think))
                }
                ps.removeAll { it.age > 4.5 || it.y < -1 }
                if (t >= r0) {
                    // la punta che scrive: parte dalla cima del filo e percorre le lettere
                    val u = ((t - r0) / (REVEAL_S * 0.9)).coerceIn(0.0, 1.0)
                    val kDone = (u * order.size).toInt().coerceAtMost(order.size)
                    order.take(kDone).forEachIndexed { n, idx ->
                        // brace: appena scritta brucia forte, poi si assesta (con un tremolio leggero)
                        val ageW = (t - r0) - n.toDouble() / order.size * REVEAL_S * 0.9
                        val ember = 1.0 - 0.25 * (1 - exp(-ageW * 3)) + 0.06 * sin(t * 13 + idx)
                        glow.set(idx, (ember * target.px[idx] / Frame.FULL).coerceIn(0.0, 1.0))
                    }
                    if (kDone < order.size) {
                        val tip = order[maxOf(kDone, 0)]
                        // il filo si piega verso la punta: una scia di fumo dalla base del bastoncino
                        val tx = (tip % N).toDouble(); val ty = (tip / N).toDouble()
                        for (q in 0..8) { val f = q / 8.0; glow.splat(12 + (tx - 12) * f, 22 - (22 - ty) * f, 0.10) }
                        glow.splat(tx, ty, 1.0)
                    }
                    // dalle lettere salgono uno o due fili di fumo
                    if (u > 0.6 && r.nextDouble() < 0.6) {
                        val src = order[r.nextInt(order.size)]
                        if (target.px[src - N.coerceAtMost(src)] == 0) ps += S((src % N).toDouble(), (src / N) - 1.0, 2.5)
                    }
                }
                out += glow.commit(); i++
            }
            return out
        }
    }
}
