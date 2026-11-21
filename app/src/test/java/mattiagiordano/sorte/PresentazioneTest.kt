package mattiagiordano.sorte

import mattiagiordano.sorte.logic.Answer
import mattiagiordano.sorte.logic.Rng
import mattiagiordano.sorte.matrix.DiceArt
import mattiagiordano.sorte.matrix.Frame
import mattiagiordano.sorte.matrix.Scripts
import mattiagiordano.sorte.matrix.Sfx
import mattiagiordano.sorte.matrix.Step
import mattiagiordano.sorte.matrix.diceSettle
import org.junit.Test
import java.io.File

/**
 * Esporta le animazioni della Glyph Matrix per il materiale di presentazione:
 * build/presentazione/<nome>.txt con righe "ms<TAB>suono<TAB>pixel" (tools/presentazione.py → PNG, GIF, WAV).
 */
class PresentazioneTest {
    private val dir = File("build/presentazione")

    private fun icon(name: String): Frame {
        val line = File(dir, "icone.txt").readLines().first { it.startsWith(name + "\t") }
        return Frame().also { f -> line.substringAfter("\t").split(",").forEachIndexed { i, v -> f.px[i] = v.toInt() } }
    }

    private fun idle(seconds: Double, ms: Long, frame: (Double) -> Frame) =
        (0 until (seconds * 1000 / ms).toInt()).map { Step(ms, frame(it * ms / 1000.0)) }

    private fun hold(f: Frame, seconds: Double) = listOf(Step((seconds * 1000).toLong(), f))

    private fun write(name: String, steps: List<Step>) {
        var t = 0L
        File(dir, "$name.txt").writeText(steps.joinToString("\n") { s ->
            t += s.wait; "$t\t${s.sound?.name ?: "-"}\t" + s.frame.toArray().joinToString(",")
        })
    }

    @Test fun esporta() {
        if (!File(dir, "icone.txt").exists()) return
        // ORACOLO CHIUSO, in italiano e in inglese (le risposte sulla matrice cambiano lingua)
        for ((it, suffix) in listOf(true to "", false to "_EN")) {
            run {
                val arrival = Scripts.closedArrival(glyph = true)
                val wait = idle(3.0, Scripts.IDLE_MS) { t -> Scripts.closedIdle(t, glyph = true) }
                val ask = Scripts.hook(wait.last().frame, Scripts.closedAsk(Answer.YES_BUT, it, null, 3.0, glyph = true))
                write("01_oracolo_chiuso$suffix", arrival + wait + ask + hold(ask.last().frame, 1.5))
            }
            run {
                val wait = idle(2.0, Scripts.IDLE_MS) { t -> Scripts.closedIdle(t, glyph = true) }
                val ask = Scripts.hook(wait.last().frame, Scripts.closedAsk(Answer.TIE, it, icon("chiave"), 2.0, glyph = true))
                write("02_oracolo_chiuso_mah$suffix", wait + ask + hold(ask.last().frame, 1.5))
            }
        }
        // ORACOLO APERTO: scintilla, fiammella, la carta brucia, resta un simbolo
        run {
            val arrival = Scripts.openArrival()
            val wait = idle(3.0, Scripts.IDLE_MS) { t -> Scripts.openIdle(t) }
            val ask = Scripts.hook(wait.last().frame, Scripts.openAsk(icon("palma")))
            write("03_oracolo_aperto", arrival + wait + ask + hold(ask.last().frame, 1.5))
        }
        // DADI: la sagoma del d20, scossa, rimbalzi sul feltro, 17
        run {
            val t = DiceArt.Tumbler()
            val still = hold(DiceArt.idle(20), 1.5)
            val shake = List(28) { i -> Step(55, DiceArt.tumble(20, t.next()), sound = if (Rng.random.nextDouble() < 0.4) Sfx.DADI_CLIC else null) }
            val settle = Scripts.diceSettle(20, 17)
            write("04_dadi_d20", still + shake + settle + hold(settle.last().frame, 1.5))
        }
        // DADI: il d6 (per alternarlo al d20 nella landing)
        run {
            val t = DiceArt.Tumbler()
            val still = hold(DiceArt.idle(6), 1.5)
            val shake = List(24) { i -> Step(55, DiceArt.tumble(6, t.next()), sound = if (Rng.random.nextDouble() < 0.4) Sfx.DADI_CLIC else null) }
            val settle = Scripts.diceSettle(6, 4)
            write("04_dadi_d6", still + shake + settle + hold(settle.last().frame, 1.5))
        }
        // GALLERIA dei simboli (per foto)
        File(dir, "05_simboli.txt").writeText(
            File(dir, "icone.txt").readLines().joinToString("\n") { l -> l.substringBefore("\t") + "\t-\t" + l.substringAfter("\t") })
    }
}
