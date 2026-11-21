package mattiagiordano.sorte

import mattiagiordano.sorte.logic.Answer
import mattiagiordano.sorte.matrix.Art
import mattiagiordano.sorte.matrix.Candidates
import mattiagiordano.sorte.matrix.DiceArt
import mattiagiordano.sorte.matrix.DiceCandidates
import mattiagiordano.sorte.matrix.Elements
import mattiagiordano.sorte.matrix.Frame
import org.junit.Test
import java.io.File

/** Esporta i filmati del laboratorio (tools/laboratorio.py li monta in una pagina con i suoni). */
class LaboratorioTest {
    private val dir = File("build/laboratorio").apply { mkdirs() }

    @Test fun esporta() {
        val target = Art.answer(Answer.YES, true)
        Candidates.Kind.entries.forEach { k -> write(k.name.lowercase(), Candidates.film(k, target), emptyList()) }

        // dadi a sagoma: stessa scansione degli altri (1,5 s di scossa)
        val frames = mutableListOf<Frame>(); val ev = mutableListOf<Pair<Long, String>>()
        val tumbler = DiceArt.Tumbler()
        repeat((DiceCandidates.SHAKE_S / 0.04).toInt()) { frames += DiceArt.tumble(20, tumbler.next()) }
        var ms = (DiceCandidates.SHAKE_S * 1000).toLong()
        DiceArt.settlePoses().zip(DiceArt.SETTLE_DELAYS.toList()).forEach { (pose, d) ->
            ms += d; ev += ms to "rimbalzo"
            repeat((d / 40).toInt()) { frames += frames.last() }
            frames[frames.lastIndex] = DiceArt.tumble(20, pose)
        }
        ms += DiceArt.LAND_DELAY; ev += ms to "posa"
        repeat((DiceArt.LAND_DELAY / 40).toInt()) { frames += frames.last() }
        frames[frames.lastIndex] = DiceArt.face(20, 17)
        repeat(38) { frames += DiceArt.face(20, 17) }
        write("dadi_sagome", frames, listOf(0L to "scossa") + ev)

        DiceCandidates.Kind.entries.forEach { k ->
            val f = DiceCandidates.film(k, 20, 17)
            write("dadi_" + k.name.lowercase(), f.frames, listOf(0L to "scossa") + f.events)
        }
    }

    @Test fun elementi() {
        if (!File(dir, "simbolo.txt").exists()) return     // solo per il laboratorio (lo crea tools/laboratorio)
        val symbol = Frame().also { f ->
            File(dir, "simbolo.txt").readText().trim().split(",").forEachIndexed { i, v -> f.px[i] = v.toInt() }
        }
        Elements.Fire.entries.forEach { k -> write("fuoco_" + k.name.lowercase(), Elements.fire(k, symbol), emptyList()) }
        // scelti: carta (aperto) e sbuffo (chiuso), con l'animazione d'arrivo davanti
        val openArr = Elements.openArrival(); val closedArr = Elements.closedArrival()
        write("scelto_aperto", openArr + Elements.fire(Elements.Fire.CARTA, symbol), emptyList())
        listOf("si" to Answer.YES, "noma" to Answer.NO_BUT, "mah" to Answer.TIE).forEach { (n, a) ->
            write("scelto_chiuso_$n", closedArr + Elements.steam(Elements.Steam.SBUFFO,
                Art.answer(a, true, subChars = 0), Art.answer(a, true, subChars = 0, mainBottom = 12), Art.answer(a, true)), emptyList())
        }
        // carosello: come scorrere i toy col Glyph Button (2 s ciascuno, due giri)
        val closedIdle = Elements.steam(Elements.Steam.SBUFFO, Art.answer(Answer.YES, true), Art.answer(Answer.YES, true), Art.answer(Answer.YES, true)).take(45)
        val openIdle = Elements.fire(Elements.Fire.CARTA, symbol).take(45)
        write("carosello", (closedArr + closedIdle + openArr + openIdle).let { it + it }, emptyList())
        val results = listOf("si" to Answer.YES, "noma" to Answer.NO_BUT, "mah" to Answer.TIE)
        Elements.Steam.entries.forEach { k ->
            results.forEach { (n, a) ->
                val first = Art.answer(a, true, subChars = 0)
                val raised = Art.answer(a, true, subChars = 0, mainBottom = 12)
                val full = Art.answer(a, true)
                write("chiuso_${k.name.lowercase()}_$n", Elements.steam(k, first, raised, full), emptyList())
            }
        }
    }

    private fun write(name: String, frames: List<Frame>, events: List<Pair<Long, String>>) {
        val lines = events.map { (ms, n) -> "#ev\t$ms\t$n" } +
            frames.mapIndexed { i, fr -> "${i * 40L}\t" + fr.toArray().joinToString(",") }
        File(dir, "$name.txt").writeText(lines.joinToString("\n"))
    }
}
