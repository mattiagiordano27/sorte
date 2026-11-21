package mattiagiordano.sorte.matrix

import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * LABORATORIO 2: gli elementi.
 *  - FUOCO per l'oracolo aperto: il calore che genera (e distrugge) il simbolo.
 *  - VAPORE SULLE PIETRE per l'oracolo chiuso: rapido, il "?" resta visibile in attesa.
 */
object Elements {

    const val DT = 0.04
    private const val N = Frame.N

    enum class Fire { FALO, CARTA, FUCINA }
    enum class Steam { SBUFFO, PIETRE, CONDENSA }

    // tempi del fuoco (oracolo aperto)
    const val F_IDLE = 3.0; const val F_THINK = 2.0; const val F_REVEAL = 1.4; const val F_HOLD = 1.2
    // tempi del vapore (oracolo chiuso): più svelto
    const val S_IDLE = 2.0; const val S_THINK = 1.2; const val S_REVEAL = 0.5; const val S_PAUSE = 1.0; const val S_MA = 0.5; const val S_HOLD = 1.0

    private fun ease(u: Double) = u.coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }

    private fun toFrame(v: DoubleArray) = Frame().also { f -> for (i in v.indices) f.px[i] = (v[i].coerceIn(0.0, 1.0) * Frame.FULL).toInt() }

    private fun lvl(t: Frame, i: Int) = t.px[i].toDouble() / Frame.FULL

    // ═════════════ FUOCO ═════════════

    fun fire(kind: Fire, symbol: Frame, seed: Long = 5): List<Frame> = when (kind) {
        Fire.FALO -> bonfire(symbol, Random(seed))
        Fire.CARTA -> paper(symbol, Random(seed))
        Fire.FUCINA -> forge(symbol, Random(seed))
    }

    /** Fiamme: il calore sale riga per riga, si sposta di lato e si raffredda (algoritmo "a propagazione"). */
    private class Flames(val r: Random) {
        val heat = DoubleArray(N * N)
        var time = 0.0
        fun step(source: Double, cooling: Double) {
            time += DT
            // la base non brucia uniforme: zone calde e fredde che si spostano → lingue di fuoco separate
            for (x in 0 until N) {
                val tongue = Noise.vnoise(x / 2.6, time * 1.8, 8)
                heat[(N - 1) * N + x] = source * (0.35 + 0.85 * tongue) * (0.85 + 0.15 * r.nextDouble())
            }
            for (y in 0 until N - 1) for (x in 0 until N) {
                val sx = (x + r.nextInt(3) - 1).coerceIn(0, N - 1)
                heat[y * N + x] = max(0.0, heat[(y + 1) * N + sx] - cooling * r.nextDouble())
            }
        }
        fun light(i: Int) = Math.pow(heat[i], 1.4)          // il cuore è più luminoso dei bordi
    }

    private fun bonfire(symbol: Frame, r: Random): List<Frame> {
        val out = mutableListOf<Frame>(); val fl = Flames(r); val glow = DoubleArray(N * N)
        val total = F_IDLE + F_THINK + F_REVEAL + F_HOLD
        var i = 0
        while (i * DT < total) {
            val t = i * DT
            val think = ease((t - F_IDLE) / 0.8)
            val rev = ((t - F_IDLE - F_THINK) / F_REVEAL).coerceIn(0.0, 1.0)
            // attesa: fiammelle basse; pensando divampa; poi si spegne
            val source = if (rev > 0) (1 - rev * 1.6).coerceAtLeast(0.0) else 0.8 + 0.2 * think
            val cooling = 0.12 - 0.05 * think + 0.12 * rev     // più raffreddamento = lingue di fuoco, non un muro
            fl.step(source, cooling)
            for (k in glow.indices) {
                var v = fl.light(k) * (0.75 + 0.25 * think)
                // il simbolo emerge come brace mentre le fiamme si abbassano
                if (symbol.px[k] > 0 && rev > 0) {
                    val ember = ease((rev - 0.15) / 0.6) * (0.92 + 0.08 * sin(t * 11 + k)) * (if (rev >= 1) 1.0 else 1.0)
                    v = max(v, lvl(symbol, k) * ember)
                }
                glow[k] = max(v, glow[k] * 0.4)
            }
            out += toFrame(glow); i++
        }
        // ultimi fotogrammi: brace ferma (il toy lascia il simbolo pulito)
        out[out.lastIndex] = symbol
        return out
    }

    /**
     * La fiammella d'attesa dell'oracolo aperto: una goccia di fuoco che tremola.
     * [h] = altezza (0..1, per farla nascere), [t] = tempo per il tremolio.
     */
    fun flame(t: Double, h: Double = 1.0, cx: Double = 12.0, base: Double = 17.0): DoubleArray {
        val v = DoubleArray(N * N)
        val height = (7.5 + 1.2 * Noise.vnoise(t * 3.0, 0.0, 21)) * h
        val sway = (Noise.vnoise(t * 2.2, 5.0, 22) - 0.5) * 2.2
        for (k in v.indices) {
            val x = k % N; val y = k / N
            val up = (base - y) / height                          // 0 alla base, 1 in punta
            if (up < -0.25 || up > 1.0) continue
            // profilo a goccia: largo in basso, appuntito in alto; la punta ondeggia
            val half = 3.6 * sin(PI * (up + 0.3).coerceIn(0.0, 1.0) / 1.3) * (1 - up * 0.45)
            val dx = x - (cx + sway * up * up)
            val inside = half - kotlin.math.abs(dx)
            if (inside <= -0.4) continue
            val core = (1 - kotlin.math.abs(dx) / (half + 0.01)).coerceIn(0.0, 1.0) * (1 - up * 0.6)
            // bordi tenui, cuore chiaro: il nucleo caldo in basso al centro arriva alla luce piena
            val edge = ((inside + 0.4) / 1.2).coerceIn(0.0, 1.0)
            val hot = Math.pow(core, 1.6) * (1 - up * 0.5)
            val flick = 0.9 + 0.1 * Noise.vnoise(x * 0.9, y * 0.9 - t * 6, 23)
            v[k] = (edge * (0.22 + 0.78 * hot) * flick + (if (hot > 0.55 && up < 0.6) 0.35 else 0.0)).coerceAtMost(1.0)
        }
        return v
    }

    /** Arrivo dell'oracolo aperto: una scintilla, un piccolo lampo, e la fiamma che si accende. */
    fun openArrival(): List<Frame> {
        val out = mutableListOf<Frame>()
        for (i in 0 until 16) {
            val t = i * DT
            val v = DoubleArray(N * N)
            if (i < 3) {
                // lo sfregamento: scintilla che corre
                val x = 6.0 + i * 3; v[16 * N + x.toInt()] = 1.0; v[16 * N + x.toInt() - 1] = 0.5
            } else {
                val a = (i - 3) * DT
                val flash = exp(-a * 10)
                for (k in v.indices) v[k] = flash * 0.7 * exp(-hypot((k % N) - 12.0, (k / N) - 15.0) / 3.0)
                val f = flame(t, ease(a / 0.35)); for (k in v.indices) v[k] = max(v[k], f[k])
            }
            out += toFrame(v)
        }
        return out
    }

    fun paper(symbol: Frame, r: Random = Random(5), idleS: Double = F_IDLE, thinkS: Double = F_THINK): List<Frame> {
        val out = mutableListOf<Frame>()
        val total = idleS + thinkS + F_REVEAL + F_HOLD
        // si accende dalla fiammella (al centro, in basso) e da due punti dove "salta" il fuoco
        val ign = listOf(12.0 to 15.0, 5.0 to 8.0, 19.0 to 9.0)
        val delay = listOf(0.0, 4.0, 6.0)                            // i salti arrivano dopo
        val fiber = DoubleArray(N * N) { Noise.vnoise((it % N) / 3.0, (it / N) / 3.0, 4) * 4 }
        val dist = DoubleArray(N * N) { k -> ign.indices.minOf { n -> hypot((k % N) - ign[n].first, (k / N) - ign[n].second) + delay[n] } + fiber[k] }
        var i = 0
        while (i * DT < total) {
            val t = i * DT
            val thinkT = t - idleS
            val v = DoubleArray(N * N)
            if (thinkT < 0) {
                // ATTESA: solo la fiammella, niente carta
                val f = flame(t); for (k in v.indices) v[k] = f[k]
            } else {
                // la carta compare in un attimo, la fiamma la tocca e il fronte avanza
                val paperLvl = 0.18 * ease(thinkT / 0.25)
                val front = (thinkT - 0.25).coerceAtLeast(0.0) / (thinkS + F_REVEAL * 0.6) * 26
                for (k in v.indices) {
                    val d = dist[k]
                    v[k] = when {
                        d < front - 1.6 -> if (symbol.px[k] > 0) lvl(symbol, k) * (0.9 + 0.1 * sin(t * 9 + k)) else 0.0
                        d < front -> 0.75 + 0.25 * r.nextDouble()
                        else -> paperLvl
                    }
                }
                if (thinkT < 0.4) { val f = flame(t, 1 - thinkT / 0.4); for (k in v.indices) v[k] = max(v[k], f[k]) }
            }
            out += toFrame(v); i++
        }
        out[out.lastIndex] = symbol
        return out
    }

    /** true = attesa col "?" (versione precedente); false = nuvola di vapore. */
    const val QUESTION_MARK_IDLE = false

    /**
     * Attesa dell'oracolo chiuso: una nuvola di vapore larga e morbida che respira e ruota piano.
     * (Forma opposta alla fiammella dell'aperto: orizzontale, lenta, senza punte.)
     */
    /** Scala della nuvola: piccola nell'app, più grande sulla Glyph Matrix. */
    const val CLOUD_APP = 0.55
    const val CLOUD_GLYPH = 0.78

    fun closedIdle(t: Double, scale: Double = CLOUD_APP): Frame = if (QUESTION_MARK_IDLE) questionIdle(t) else cloud(t, scale = scale)

    /**
     * La nuvola. [dy] la sposta in basso, [squash] la schiaccia (0 = normale, 1 = piatta e larga),
     * [gain] ne alza la luce: servono alla caduta sulle pietre (vedi [cloudFall]).
     */
    fun cloud(t: Double, dy: Double = 0.0, squash: Double = 0.0, gain: Double = 1.0, scale: Double = CLOUD_APP): Frame = toFrame(cloudLevels(t, dy, squash, gain, scale))

    /** La sagoma della nuvola, presa dalla carta "le nuvole" di Mattia Giordano (la nuvola centrale). */
    private val CLOUD_SHAPE = listOf(
        "...####....",
        "...#####...",
        "..########.",
        "###########",
        "###########",
        ".#########.",
    )
    private val CW = CLOUD_SHAPE[0].length; private val CH = CLOUD_SHAPE.size

    /** Sagoma campionata in modo morbido (bordi sfumati): 0..1. */
    private fun shapeAt(u: Double, v: Double): Double {
        var s = 0.0
        for (oy in -1..1) for (ox in -1..1) {
            val x = Math.floor(u + ox * 0.45).toInt(); val y = Math.floor(v + oy * 0.45).toInt()
            if (y in 0 until CH && x in 0 until CW && CLOUD_SHAPE[y][x] == '#') s += 1.0
        }
        return s / 9
    }

    private fun cloudLevels(t: Double, dy: Double, squash: Double, gain: Double, scale: Double): DoubleArray {
        val v = DoubleArray(N * N)
        val sc = scale / CLOUD_APP * 0.95                           // un pixel della carta = sc LED
        val sx = (1 + 0.6 * squash) * sc * (1 + 0.06 * sin(t * 1.15))   // respira
        val sy = (1 - 0.55 * squash) * sc * (1 + 0.06 * sin(t * 1.15 + 0.8))
        val drift = sin(t * 0.5) * 1.1
        val bob = sin(t * 0.8 + 1.0) * 0.5
        val cx = 12 + drift; val cy = 12.4 + bob + dy
        for (k in v.indices) {
            val x = k % N; val y = k / N
            // il contorno ondeggia piano, come vapore, senza perdere la forma del disegno
            val wob = 0.35 * (Noise.vnoise(x * 0.45 + t * 0.6, y * 0.45, 33) - 0.5)
            val u = (x - cx) / sx + CW / 2.0 + wob
            val w = (y - cy) / sy + CH / 2.0 + wob * 0.6
            val f = shapeAt(u, w)
            if (f <= 0.0) continue
            val body = ((f - 0.2) / 0.6).coerceIn(0.0, 1.0)
            val swirl = Noise.vnoise((x - 12) * 0.5 + t * 0.55, (y - 12) * 0.5 - t * 0.7, 32)   // volute interne
            v[k] = (body * (0.26 + 0.38 * swirl) * gain).coerceAtMost(1.0)
        }
        return v
    }

    /**
     * Domanda all'oracolo chiuso: la nuvola scende sempre più veloce come una goccia, si schiaccia
     * toccando il fondo (le pietre calde) e si fa più chiara. Da lì risale lo sbuffo di vapore.
     */
    fun cloudFall(t0: Double, scale: Double = CLOUD_APP): List<Frame> {
        val n = 14
        // filamenti (virga): partono dalla pancia della nuvola, scendono e si biforcano
        val roots = listOf(-0.55, -0.2, 0.15, 0.5)
        return List(n) { i ->
            val t = t0 + i * DT
            val u = (i + 1).toDouble() / n
            val fall = u * u                                   // accelera, come per gravità
            val squash = ((u - 0.75) / 0.25).coerceIn(0.0, 1.0)  // si schiaccia solo all'impatto
            val dy = 7.5 * fall
            val v = cloudLevels(t, dy, squash, 1 + 0.9 * squash, scale)
            val grow = (u / 0.7).coerceIn(0.0, 1.0) * (1 - squash)  // crescono scendendo, all'impatto si riassorbono
            val bottom = 12.4 + dy + 3.0 * (scale / CLOUD_APP * 0.95)
            roots.forEachIndexed { k, rx ->
                val x0 = 12 + rx * 9 * scale + sin(t * 1.3 + k) * 0.5
                val len = (3.0 + k % 2) * grow * (scale / CLOUD_APP)
                fun strand(x: Double, y: Double, ang: Double, l: Double, a0: Double) {
                    var s2 = 0.0
                    while (s2 < l) {
                        val px = x + sin(ang) * s2 + 0.4 * sin(s2 * 1.7 + t * 4 + k)
                        val py = y + cos(ang) * s2
                        val xi = Math.round(px).toInt(); val yi = Math.round(py).toInt()
                        if (xi in 0 until N && yi in 0 until N) v[yi * N + xi] = max(v[yi * N + xi], a0 * (1 - s2 / (l + 0.01)))
                        s2 += 0.5
                    }
                }
                val a0 = 0.32 + 0.1 * sin(t * 3 + k)
                strand(x0, bottom, 0.0, len, a0)                                   // il filamento
                if (len > 2) {                                                     // e la sua biforcazione
                    val bx = x0 + 0.4 * sin(len * 1.7 + t * 4 + k); val by = bottom + len * 0.55
                    strand(bx, by, if (k % 2 == 0) 0.6 else -0.6, len * 0.6, a0 * 0.8)
                }
            }
            toFrame(v)
        }
    }

    /** La vecchia attesa: il "?" con fili di vapore. */
    private fun questionIdle(t: Double): Frame {
        val v = DoubleArray(N * N)
        val q = Art.closedIdle()
        for (k in v.indices) v[k] = lvl(q, k)
        for (w in 0..2) {
            val ph = (t * 0.42 + w / 3.0) % 1.0
            val y = 23.0 - ph * 23
            val x = 12 + (w - 1) * 3.5 + sin(t * 1.3 + w * 2.1) * 2.5 * ph
            val a = sin(PI * ph) * 0.3
            for (tr in 0..3) {
                val xi = Math.round(x - sin(t * 1.3 + w * 2.1 - tr * 0.3) * 0.8 * tr * ph).toInt(); val yi = Math.round(y + tr * 1.2).toInt()
                if (xi in 0 until N && yi in 0 until N) v[yi * N + xi] = max(v[yi * N + xi], a * (1 - tr * 0.22))
            }
        }
        return toFrame(v)
    }

    /** Attesa dell'oracolo aperto: la fiammella. */
    fun openIdle(t: Double): Frame = toFrame(flame(t))

    /** Arrivo dell'oracolo chiuso: uno sbuffo sale e, diradandosi, lascia il "?". */
    fun closedArrival(seed: Long = 3, scale: Double = CLOUD_APP): List<Frame> {
        val r = Random(seed); val out = mutableListOf<Frame>()
        val q = closedIdle(0.0, scale)
        val puffs = List(40) { doubleArrayOf(12 + r.nextGaussian() * 3, 24.0 + r.nextDouble() * 4, r.nextGaussian() * 2, -14 - r.nextDouble() * 8) }
        for (i in 0 until 18) {
            val t = i * DT
            val v = DoubleArray(N * N)
            puffs.forEach { p ->
                val x = p[0] + p[2] * t; val y = p[1] + p[3] * t
                for (oy in -1..1) for (ox in -1..1) {
                    val xx = Math.round(x).toInt() + ox; val yy = Math.round(y).toInt() + oy
                    if (xx in 0 until N && yy in 0 until N) v[yy * N + xx] += 0.12 * (1 - t / 0.72).coerceAtLeast(0.0)
                }
            }
            val reveal = ease((t - 0.25) / 0.4)
            for (k in v.indices) v[k] = max(v[k].coerceAtMost(0.8), lvl(q, k) * reveal)
            out += toFrame(v)
        }
        return out
    }

    private fun forge(symbol: Frame, r: Random): List<Frame> {
        val out = mutableListOf<Frame>()
        class Spark(var x: Double, var y: Double, var vx: Double, var vy: Double, var life: Double)
        val sparks = mutableListOf<Spark>(); val glow = DoubleArray(N * N)
        val coals = List(6) { (5 + it * 3.0 + r.nextDouble()) to (19.0 + r.nextDouble() * 2.5) }
        val total = F_IDLE + F_THINK + F_REVEAL + F_HOLD
        val strike = F_IDLE + F_THINK
        var i = 0
        while (i * DT < total) {
            val t = i * DT
            val think = ease((t - F_IDLE) / 0.6) * (if (t < strike) 1.0 else 0.0)
            // il mantice: respiro lento in attesa, pompate rapide pensando
            val bellows = if (t < F_IDLE) 0.5 + 0.5 * sin(t * 1.7) else 0.5 + 0.5 * sin(t * 7.0)
            val v = DoubleArray(N * N)
            val coalHeat = (0.3 + 0.25 * bellows + 0.35 * think) * (if (t > strike) (1 - (t - strike) / 0.6).coerceAtLeast(0.0) else 1.0)
            for (k in v.indices) {
                val x = k % N; val y = k / N
                var c = 0.0
                coals.forEach { (cx, cy) -> c += exp(-((x - cx) * (x - cx) + (y - cy) * (y - cy)) / 3.0) }
                v[k] = c.coerceAtMost(1.0) * coalHeat * (0.85 + 0.15 * Noise.vnoise(x * 0.8, y * 0.8 + t * 2, 3))
            }
            if (t < strike && r.nextDouble() < 0.1 + 0.6 * think) {
                val (cx, cy) = coals[r.nextInt(coals.size)]
                sparks += Spark(cx, cy, r.nextGaussian() * 2, -6 - r.nextDouble() * 8, 0.5 + r.nextDouble() * 0.5)
            }
            if (t >= strike && t < strike + DT) {
                // il colpo di martello: scintille in tutte le direzioni
                repeat(30) { val a = r.nextDouble() * 2 * PI; val s = 8 + r.nextDouble() * 14
                    sparks += Spark(12.0, 12.0, cos(a) * s, sin(a) * s, 0.4 + r.nextDouble() * 0.5) }
            }
            sparks.forEach { s ->
                s.vy += 14 * DT; s.x += s.vx * DT; s.y += s.vy * DT; s.life -= DT
                val xi = Math.round(s.x).toInt(); val yi = Math.round(s.y).toInt()
                if (xi in 0 until N && yi in 0 until N) v[yi * N + xi] = max(v[yi * N + xi], s.life.coerceIn(0.0, 1.0) + 0.3)
            }
            sparks.removeAll { it.life <= 0 }
            if (t >= strike) {
                val a = t - strike
                val flash = exp(-a * 9) * 0.6                                      // il lampo del colpo
                val hot = 1.0 - 0.2 * ease(a / 1.2)                                // incandescente, poi si raffredda un po'
                for (k in v.indices) {
                    v[k] = max(v[k], flash * (1 - hypot((k % N) - 12.0, (k / N) - 12.0) / 14).coerceAtLeast(0.0))
                    if (symbol.px[k] > 0) v[k] = max(v[k], lvl(symbol, k) * hot)
                }
            }
            for (k in glow.indices) glow[k] = max(v[k], glow[k] * 0.6)
            out += toFrame(glow); i++
        }
        out[out.lastIndex] = symbol
        return out
    }

    // ═════════════ VAPORE SULLE PIETRE (oracolo chiuso) ═════════════

    /**
     * [first] = risposta senza "ma" (centrata), [full] = risposta completa; se uguali, niente "ma".
     * [raised] = parola principale già alzata (per far salire la parola prima del "ma").
     */
    fun steam(kind: Steam, first: Frame, raised: Frame, full: Frame, seed: Long = 9, idleS: Double = S_IDLE): List<Frame> {
        val r = Random(seed)
        val out = mutableListOf<Frame>()
        val question = if (idleS == 0.0) Frame() else closedIdle(0.0)
        class Puff(var x: Double, var y: Double, var vx: Double, var vy: Double, var age: Double, val life: Double)
        val ps = mutableListOf<Puff>()
        val glow = DoubleArray(N * N)
        val stones = listOf(7.0 to 21.0, 12.0 to 22.0, 17.0 to 21.0)
        val hasMa = !first.px.contentEquals(full.px)
        val r0 = idleS + S_THINK
        val total = r0 + S_REVEAL + (if (hasMa) S_PAUSE + S_MA else 0.0) + S_HOLD
        var i = 0
        fun splat(x: Double, y: Double, a: Double, v: DoubleArray) {
            val xi = Math.round(x).toInt(); val yi = Math.round(y).toInt()
            for (oy in -1..1) for (ox in -1..1) {
                val xx = xi + ox; val yy = yi + oy
                if (xx in 0 until N && yy in 0 until N) v[yy * N + xx] += a * (if (ox == 0 && oy == 0) 1.0 else 0.35)
            }
        }
        while (i * DT < total) {
            val t = i * DT
            val v = DoubleArray(N * N)
            val think = t >= idleS && t < r0
            val revU = ((t - r0) / S_REVEAL).coerceIn(0.0, 1.0)
            // ATTESA: il "?" resta sempre visibile, con un filo di vapore (e le pietre, se ci sono)
            if (t < r0) for (k in v.indices) v[k] = lvl(question, k) * (if (think) 0.5 else 1.0)
            if (kind == Steam.PIETRE) {
                val fade = 1 - revU
                stones.forEach { (cx, cy) ->
                    for (k in v.indices) {
                        val d = hypot((k % N) - cx, ((k / N) - cy) * 1.4)
                        v[k] += exp(-d * d / 4.0) * (0.25 + 0.12 * sin(t * 2 + cx) + (if (think) 0.2 else 0.0)) * fade
                    }
                }
            }
            // sorgenti di vapore
            val rate = when {
                t < idleS -> 2.0
                think -> 45.0
                else -> 0.0
            }
            // all'impatto della nuvola: il primo sbuffo parte subito, nel punto dove è caduta
            if (i == 0 && idleS == 0.0 && kind == Steam.SBUFFO) repeat(18) {
                ps += Puff(12 + r.nextGaussian() * 2.5, 20.5 + r.nextDouble(), r.nextGaussian() * 3, -5 - r.nextDouble() * 5, 0.35, 1.1 + r.nextDouble() * 0.5)   // nasce già acceso
            }
            repeat((rate * DT + r.nextDouble()).toInt()) {
                when (kind) {
                    Steam.SBUFFO -> ps += Puff(12 + r.nextGaussian() * 2, 22.0, r.nextGaussian() * 4, -10 - r.nextDouble() * 6, 0.0, 1.0 + r.nextDouble() * 0.6)
                    Steam.PIETRE -> { val (sx, sy) = stones[r.nextInt(3)]; ps += Puff(sx + r.nextGaussian(), sy - 1, r.nextGaussian() * 1.5, -9 - r.nextDouble() * 5, 0.0, 1.2 + r.nextDouble() * 0.5) }
                    Steam.CONDENSA -> ps += Puff(r.nextDouble() * 24, 6 + r.nextDouble() * 14, r.nextGaussian(), -1 - r.nextDouble(), 0.0, 1.4)
                }
            }
            ps.forEach { p ->
                // alla risposta il vapore vola via verso l'alto, veloce
                val blow = if (t >= r0) 1 + revU * 4 else 1.0
                p.vx *= 0.94; p.vy = p.vy * 0.97 - (if (t >= r0) 25 * DT else 0.0)
                p.x += p.vx * DT * blow; p.y += p.vy * DT; p.age += DT
                val env = sin(PI * (p.age / p.life).coerceIn(0.0, 1.0))
                splat(p.x, p.y, env * (if (kind == Steam.CONDENSA) 0.22 else 0.3), v)
            }
            ps.removeAll { it.age > it.life || it.y < -3 }
            // RISPOSTA
            if (t >= r0) {
                val target = if (hasMa && t >= r0 + S_REVEAL + S_PAUSE) full else first
                for (k in v.indices) if (first.px[k] > 0 || target.px[k] > 0) {
                    val x = k % N; val y = k / N
                    val a = when (kind) {
                        Steam.SBUFFO -> ease(revU * 1.6 - hypot(x - 12.0, y - 12.0) / 30)                  // dal centro, mentre il vapore vola via
                        Steam.PIETRE -> ease(revU * 2.2 - (N - y) / 22.0)                                  // dal basso, al passaggio della colonna
                        Steam.CONDENSA -> ease(revU * 2.0 - y / 25.0 - 0.15 * Noise.vnoise(x * 0.9, 0.0, 2)) // dall'alto, come gocce che scivolano
                    }
                    v[k] = max(v[k], lvl(first, k) * a)
                }
                if (hasMa && t >= r0 + S_REVEAL + S_PAUSE) {
                    // la parola sale e il "ma…" arriva con un piccolo sbuffo
                    val m = (t - r0 - S_REVEAL - S_PAUSE)
                    val base = if (m < 0.12) first else raised
                    for (k in v.indices) v[k] = if (first.px[k] > 0 && m >= 0.12) 0.0.coerceAtLeast(v[k] * 0.0) else v[k]
                    for (k in v.indices) {
                        if (raised.px[k] > 0 && m >= 0.12) v[k] = lvl(raised, k)
                        if (m < 0.12 && first.px[k] > 0) v[k] = lvl(first, k)
                        val isMa = full.px[k] > 0 && raised.px[k] == 0
                        if (isMa) v[k] = max(v[k], lvl(full, k) * ease((m - 0.12) / (S_MA * 0.7) - ((k % N) - 4) / 40.0))
                    }
                    if (m in 0.1..0.4) repeat(3) { ps += Puff(6 + r.nextDouble() * 13, 21.0, r.nextGaussian(), -6.0, 0.0, 0.5) }
                }
            }
            for (k in glow.indices) glow[k] = max(v[k].coerceAtMost(1.0), glow[k] * 0.55)
            out += toFrame(glow); i++
        }
        out[out.lastIndex] = full
        return out
    }
}
