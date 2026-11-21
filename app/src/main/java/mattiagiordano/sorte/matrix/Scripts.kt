package mattiagiordano.sorte.matrix

import mattiagiordano.sorte.logic.Answer
import kotlin.math.max

/** Un passo di animazione: attendi [wait] ms, mostra [frame], eventualmente vibra e suona. */
data class Step(val wait: Long, val frame: Frame, val haptic: Haptic = Haptic.NONE, val sound: Sfx? = null)

enum class Haptic { NONE, TICK, THUD }

/** I suoni (file in res/raw, generati da tools/suoni.py). */
enum class Sfx {
    CHIUSO_ARRIVO, CHIUSO_PENSA, CHIUSO_MA,
    APERTO_ARRIVO, APERTO_PENSA,
    DADI_CLIC, DADI_FELTRO, DADI_POSA,
}

/**
 * La regia, uguale per Glyph Toy e app:
 *  - ORACOLO CHIUSO = vapore sulle pietre: "?" col vapore in attesa, sbuffo rapido, il "ma…" in ritardo.
 *    MAH: il vapore si dirada e il fuoco brucia la carta lasciando un simbolo.
 *  - ORACOLO APERTO = fuoco: fiammella in attesa; la carta brucia e resta il simbolo.
 * L'attesa è un'animazione continua; la domanda parte dall'ultimo fotogramma dell'attesa e ci si
 * aggancia sfumando (vedi [hook]).
 */
object Scripts {
    const val FRAME_MS = 40L
    const val IDLE_MS = 60L
    private const val DT = FRAME_MS / 1000.0
    private const val HOOK_FRAMES = 6

    private fun steps(frames: List<Frame>, sound: Sfx? = null): List<Step> =
        frames.mapIndexed { i, f -> Step(FRAME_MS, f, sound = if (i == 0) sound else null) }

    /** Aggancia l'animazione all'attesa: i primi fotogrammi sfumano dall'ultimo fotogramma visto. */
    fun hook(from: Frame?, s: List<Step>): List<Step> {
        if (from == null) return s
        return s.mapIndexed { i, st ->
            if (i < HOOK_FRAMES) st.copy(frame = blend(from, st.frame, (i + 1).toDouble() / (HOOK_FRAMES + 1))) else st
        }
    }

    // ── ORACOLO CHIUSO ──

    fun closedArrival(glyph: Boolean = false): List<Step> =
        steps(Elements.closedArrival(scale = if (glyph) Elements.CLOUD_GLYPH else Elements.CLOUD_APP), Sfx.CHIUSO_ARRIVO)

    fun closedIdle(t: Double, glyph: Boolean = false): Frame =
        Elements.closedIdle(t, if (glyph) Elements.CLOUD_GLYPH else Elements.CLOUD_APP)

    fun closedAsk(a: Answer, it: Boolean, symbol: Frame?, idleT: Double = 0.0, glyph: Boolean = false): List<Step> {
        val first = Art.answer(a, it, subChars = 0)
        val raised = Art.answer(a, it, subChars = 0, mainBottom = 12)
        val full = Art.answer(a, it)
        val frames = Elements.steam(Elements.Steam.SBUFFO, first, raised, full, seed = System.nanoTime() % 10_000, idleS = 0.0)
        // prima la nuvola cade sulle pietre, poi (con lo sfrigolio) risale come vapore
        val fall = if (Elements.QUESTION_MARK_IDLE) emptyList()
                   else steps(Elements.cloudFall(idleT, if (glyph) Elements.CLOUD_GLYPH else Elements.CLOUD_APP))
        val revealShift = fall.size
        val out = (fall + steps(frames, Sfx.CHIUSO_PENSA)).toMutableList()
        // vibrazioni: risposta che compare, e il "ma…"
        val revealAt = revealShift + ((Elements.S_THINK + Elements.S_REVEAL * 0.6) / DT).toInt()
        if (revealAt in out.indices) out[revealAt] = out[revealAt].copy(haptic = Haptic.THUD)
        if (Art.qualifier(a, it) != null) {
            val maAt = revealShift + ((Elements.S_THINK + Elements.S_REVEAL + Elements.S_PAUSE) / DT).toInt()
            if (maAt in out.indices) out[maAt] = out[maAt].copy(haptic = Haptic.TICK, sound = Sfx.CHIUSO_MA)
        }
        if (a == Answer.TIE && symbol != null) {
            // MAH: dopo una pausa il fuoco brucia la carta e lascia il simbolo
            val burn = Elements.paper(symbol, idleS = 0.0, thinkS = 1.4)
            out += steps(burn, Sfx.APERTO_PENSA)
            out[out.lastIndex] = out.last().copy(haptic = Haptic.THUD)
        }
        return out
    }

    // ── ORACOLO APERTO ──

    fun openArrival(): List<Step> = steps(Elements.openArrival(), Sfx.APERTO_ARRIVO)

    fun openIdle(t: Double): Frame = Elements.openIdle(t)

    fun openAsk(symbol: Frame): List<Step> {
        val out = steps(Elements.paper(symbol, idleS = 0.0, thinkS = 1.8), Sfx.APERTO_PENSA).toMutableList()
        val at = ((1.8 + Elements.F_REVEAL * 0.6) / DT).toInt().coerceAtMost(out.lastIndex)
        out[at] = out[at].copy(haptic = Haptic.THUD)
        return out
    }
}

/** Assestamento del dado dopo il lancio: rimbalzi sul feltro che si smorzano, poi il numero. */
fun Scripts.diceSettle(sides: Int, value: Int): List<Step> {
    val steps = mutableListOf<Step>()
    DiceArt.settlePoses().zip(DiceArt.SETTLE_DELAYS.toList()).forEach { (pose, d) ->
        steps += Step(d, DiceArt.tumble(sides, pose), Haptic.TICK, Sfx.DADI_FELTRO)
    }
    steps += Step(DiceArt.LAND_DELAY, DiceArt.face(sides, value), Haptic.THUD, Sfx.DADI_POSA)
    return steps
}

/** Fa sfumare un fotogramma nell'altro (per passare dall'attesa all'animazione senza scatti). */
fun blend(a: Frame, b: Frame, k: Double) = Frame().also { f ->
    for (i in f.px.indices) f.px[i] = max(0, (a.px[i] * (1 - k) + b.px[i] * k).toInt())
}
