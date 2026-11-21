package mattiagiordano.sorte.matrix

import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * LABORATORIO DADI: alternative alla sagoma. Stessa scansione per tutte:
 * 0–1,5 s si scuote, 1,5–2,9 s si assesta, poi il numero resta.
 * Ogni filmato porta con sé gli eventi sonori (rimbalzi, tuffi…) per sincronizzare l'audio.
 */
object DiceCandidates {

    enum class Kind { MERCURIO, SASSI, COSTELLAZIONE }

    class Film(val frames: List<Frame>, val events: List<Pair<Long, String>>)

    private const val N = Frame.N
    private const val DT = 0.04
    const val SHAKE_S = 1.5
    const val SETTLE_S = 1.4
    const val HOLD_S = 1.5

    fun film(kind: Kind, sides: Int, value: Int, seed: Long = 11): Film = when (kind) {
        Kind.MERCURIO -> mercury(sides, value, Random(seed))
        Kind.SASSI -> stones(sides, value, Random(seed))
        Kind.COSTELLAZIONE -> stars(sides, value, Random(seed))
    }

    private fun number(value: Int): Frame = Frame().apply {
        val t = value.toString()
        PixelFont.BIG.draw(this, t, (N - PixelFont.BIG.width(t)) / 2, 15)
    }

    /** Versione sfocata del numero (campo morbido per far "colare" il metallo nelle cifre). */
    private fun blurred(f: Frame): DoubleArray {
        val v = DoubleArray(N * N)
        for (y in 0 until N) for (x in 0 until N) {
            var s = 0.0; var w = 0.0
            for (oy in -2..2) for (ox in -2..2) {
                val k = exp(-(ox * ox + oy * oy) / 2.0); s += k * f[x + ox, y + oy] / Frame.FULL.toDouble(); w += k
            }
            v[y * N + x] = s / w * 2.2
        }
        return v
    }

    private fun total() = SHAKE_S + SETTLE_S + HOLD_S

    // ───────────── MERCURIO: goccia di metallo liquido ─────────────

    private fun mercury(sides: Int, value: Int, r: Random): Film {
        class Drop(var x: Double, var y: Double, var vx: Double, var vy: Double, val rad: Double)
        val drops = MutableList(5) { Drop(12.0, 12.0, 0.0, 0.0, 2.2 + r.nextDouble() * 1.2) }
        val target = blurred(number(value))
        val frames = mutableListOf<Frame>(); val ev = mutableListOf<Pair<Long, String>>()
        var lastHit = -1.0
        var i = 0
        while (i * DT < total()) {
            val t = i * DT
            val shaking = t < SHAKE_S
            val settle = ((t - SHAKE_S) / SETTLE_S).coerceIn(0.0, 1.0)
            drops.forEach { d ->
                if (shaking) {
                    // scossa: spinte casuali, le gocce si separano e sciabordano
                    d.vx += r.nextGaussian() * 60 * DT; d.vy += r.nextGaussian() * 60 * DT
                } else {
                    // si richiamano al centro e si fondono
                    d.vx += (12 - d.x) * 30 * DT; d.vy += (12 - d.y) * 30 * DT
                    d.vx *= 0.82; d.vy *= 0.82
                }
                d.x += d.vx * DT; d.y += d.vy * DT
                val dist = hypot(d.x - 12, d.y - 12)
                if (dist > 8.5) {
                    val nx = (d.x - 12) / dist; val ny = (d.y - 12) / dist
                    val vn = d.vx * nx + d.vy * ny
                    if (vn > 0) { d.vx -= 1.6 * vn * nx; d.vy -= 1.6 * vn * ny; if (vn > 8 && t - lastHit > 0.08) { ev += (t * 1000).toLong() to "sciabordio"; lastHit = t } }
                    d.x = 12 + nx * 8.5; d.y = 12 + ny * 8.5
                }
            }
            if (!shaking && settle > 0.35 && ev.none { it.second == "fusione" }) ev += (t * 1000).toLong() to "fusione"
            // tremolio della goccia unica prima di diventare numero
            val wobble = if (settle in 0.35..0.7) 1 + 0.18 * sin((settle - 0.35) * 40) * exp(-(settle - 0.35) * 8) else 1.0
            val morph = ((settle - 0.6) / 0.35).coerceIn(0.0, 1.0)
            if (morph > 0 && ev.none { it.second == "numero" }) ev += (t * 1000).toLong() to "numero"
            val f = Frame()
            for (y in 0 until N) for (x in 0 until N) {
                // campo metaball: le gocce si fondono quando si avvicinano
                var field = 0.0
                drops.forEach { d -> val dd = (x - d.x) * (x - d.x) + (y - d.y) * (y - d.y); field += d.rad * d.rad * wobble / (dd + 0.5) }
                val blob = (field - 1.0).coerceIn(0.0, 1.0)
                val v = blob * (1 - morph) + target[y * N + x].coerceIn(0.0, 1.0) * morph
                // riflesso: il metallo è più chiaro in alto a sinistra
                val shine = if (blob > 0.5 && morph < 1) 0.15 * (1 - (x + y) / 30.0) else 0.0
                f[x, y] = (((if (v > 0.5) 0.75 + 0.25 * v else v * 0.9) + shine).coerceIn(0.0, 1.0) * Frame.FULL).toInt()
            }
            if (settle >= 1.0) { frames += number(value); i++; continue }
            frames += f; i++
        }
        return Film(frames, ev)
    }

    // ───────────── SASSI NEL LAGO: rimbalzello ─────────────

    private fun stones(sides: Int, value: Int, r: Random): Film {
        val frames = mutableListOf<Frame>(); val ev = mutableListOf<Pair<Long, String>>()
        val rings = mutableListOf<Triple<Double, Double, Double>>()        // x, y, nascita
        // rimbalzi: da sinistra verso il centro, sempre più vicini
        val skips = listOf(2.5 to 0.0, 6.5 to 0.35, 9.5 to 0.62, 11.5 to 0.85, 12.0 to 1.02)
        val number = number(value)
        var i = 0
        while (i * DT < total()) {
            val t = i * DT
            val glow = DoubleArray(N * N)
            if (t < SHAKE_S) {
                // il sasso in mano: acqua appena increspata, qualche cerchio lieve
                if (r.nextDouble() < 0.16) rings += Triple(4 + r.nextDouble() * 17, 4 + r.nextDouble() * 17, t)
            } else {
                val ts = t - SHAKE_S
                skips.forEachIndexed { k, (sx, st) ->
                    if (ts >= st && rings.none { it.third == SHAKE_S + st }) {
                        rings += Triple(sx, 12.0 + (if (k % 2 == 0) 0.6 else -0.6), SHAKE_S + st)
                        ev += (t * 1000).toLong() to (if (k == skips.lastIndex) "tuffo" else "rimbalzo")
                    }
                }
                // il sasso in volo tra un rimbalzo e l'altro (arco)
                for (k in 0 until skips.lastIndex) {
                    val (x0, t0) = skips[k]; val (x1, t1) = skips[k + 1]
                    if (ts in t0..t1) {
                        val u = (ts - t0) / (t1 - t0)
                        val x = x0 + (x1 - x0) * u; val y = 12 - sin(u * PI) * (5.0 - k)
                        for (oy in 0..1) for (ox in 0..1) { val xi = x.toInt() + ox; val yi = y.toInt() + oy; if (xi in 0 until N && yi in 0 until N) glow[yi * N + xi] = 1.0 }
                    }
                }
            }
            rings.forEach { (cx, cy, born) ->
                val a = t - born; if (a < 0) return@forEach
                val big = born >= SHAKE_S
                val rad = a * (if (big) 8.0 else 5.0); val fade = exp(-a * (if (big) 1.4 else 2.2)) * (if (big) 0.8 else 0.42)
                for (y in 0 until N) for (x in 0 until N) {
                    val d = hypot(x - cx, y - cy)
                    glow[y * N + x] += exp(-((d - rad) * (d - rad)) / 0.8) * fade
                }
            }
            // il numero affiora dopo il tuffo, dove è passata l'onda
            val tSink = SHAKE_S + skips.last().second
            val f = Frame()
            for (idx in 0 until N * N) {
                var v = glow[idx].coerceAtMost(1.0)
                if (t > tSink && number.px[idx] > 0) {
                    val d = hypot((idx % N) - 12.0, (idx / N) - 12.0)
                    v = maxOf(v, (((t - tSink) * 8 - d) / 3).coerceIn(0.0, 1.0))
                }
                f.px[idx] = (v * Frame.FULL).toInt()
            }
            frames += f; i++
        }
        return Film(frames, ev)
    }

    // ───────────── COSTELLAZIONE ─────────────

    private fun stars(sides: Int, value: Int, r: Random): Film {
        val frames = mutableListOf<Frame>(); val ev = mutableListOf<Pair<Long, String>>()
        val target = (0 until N * N).filter { number(value).px[it] > 0 }
        class Star(var a: Double, var d: Double, val ph: Double, val tw: Double)
        val stars = MutableList(maxOf(target.size, 40)) { Star(r.nextDouble() * 2 * PI, 2 + r.nextDouble() * 9.5, r.nextDouble() * 6.28, 3 + r.nextDouble() * 6) }
        val trail = DoubleArray(N * N)
        var i = 0
        var nextChime = 0.0
        while (i * DT < total()) {
            val t = i * DT
            val settle = ((t - SHAKE_S) / SETTLE_S).coerceIn(0.0, 1.0)
            for (k in trail.indices) trail[k] *= 0.8
            stars.forEachIndexed { k, s ->
                // galassia: le stelle interne girano più veloci; scuotendo vorticano
                val spin = if (t < SHAKE_S) 2.2 / sqrt(s.d) else 2.2 / sqrt(s.d) * (1 - settle)
                s.a += spin * DT
                var x = 12 + cos(s.a) * s.d; var y = 12 + sin(s.a) * s.d * 0.85
                if (settle > 0 && k < target.size) {
                    // si raccolgono nella costellazione del numero
                    val u = ((settle - k.toDouble() / target.size * 0.4) / 0.6).coerceIn(0.0, 1.0)
                    val e = u * u * (3 - 2 * u)
                    x += ((target[k] % N) - x) * e; y += ((target[k] / N) - y) * e
                }
                val fade = if (settle > 0 && k >= target.size) 1 - settle else 1.0
                val tw = 0.55 + 0.45 * sin(t * s.tw + s.ph)
                val xi = Math.round(x).toInt(); val yi = Math.round(y).toInt()
                if (xi in 0 until N && yi in 0 until N) trail[yi * N + xi] = maxOf(trail[yi * N + xi], tw * fade * (if (settle > 0.95 && k < target.size) 1.0 else 0.8))
            }
            if (t < SHAKE_S && t >= nextChime) { ev += (t * 1000).toLong() to "stella"; nextChime = t + 0.12 + r.nextDouble() * 0.25 }
            if (settle >= 0.95 && ev.none { it.second == "costellazione" }) ev += (t * 1000).toLong() to "costellazione"
            val f = Frame()
            for (k in trail.indices) f.px[k] = (trail[k].coerceAtMost(1.0) * Frame.FULL).toInt()
            frames += f; i++
        }
        return Film(frames, ev)
    }
}
