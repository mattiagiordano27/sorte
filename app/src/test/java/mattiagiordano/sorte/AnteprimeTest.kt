package mattiagiordano.sorte

import mattiagiordano.sorte.logic.Answer
import mattiagiordano.sorte.matrix.Art
import mattiagiordano.sorte.matrix.DiceArt
import mattiagiordano.sorte.matrix.Frame
import org.junit.Test
import java.io.File

/**
 * Esporta tutte le schermate fisse in app/build/anteprime/frames.txt.
 * Poi `python3 tools/anteprime.py` le disegna (foglio.png) e rigenera le anteprime dei toy.
 */
class AnteprimeTest {
    @Test fun esporta() {
        val tiles = mutableListOf<Pair<String, Frame>>()
        tiles += "closed_idle" to Art.closedIdle()
        Answer.entries.forEach { tiles += "IT ${it.name}" to Art.answer(it, true) }
        Answer.entries.forEach { tiles += "EN ${it.name}" to Art.answer(it, false) }
        for (d in intArrayOf(4, 6, 8, 10, 12, 20, 100)) tiles += "idle_d$d" to DiceArt.idle(d)
        tiles += "aria 1" to DiceArt.tumble(20, DiceArt.Pose(35.0, 0.85, 2.0, -1.0))
        tiles += "aria 2" to DiceArt.tumble(6, DiceArt.Pose(20.0, 0.9, -2.0, 1.0))
        tiles += "aria 3" to DiceArt.tumble(4, DiceArt.Pose(70.0, 0.8, 1.0, 2.0))
        tiles += "d4 -> 3" to DiceArt.face(4, 3)
        tiles += "d6 -> 5" to DiceArt.face(6, 5)
        tiles += "d8 -> 7" to DiceArt.face(8, 7)
        tiles += "d10 -> 7" to DiceArt.face(10, 7)
        tiles += "d12 -> 11" to DiceArt.face(12, 11)
        tiles += "d20 -> 13" to DiceArt.face(20, 13)
        tiles += "d20 -> 20 max" to DiceArt.face(20, 20)
        tiles += "d20 -> 1" to DiceArt.face(20, 1)
        tiles += "d100 -> 47" to DiceArt.face(100, 47)
        tiles += "d100 -> 100 max" to DiceArt.face(100, 100)
        tiles += "d10 -> 10 max" to DiceArt.face(10, 10)
        tiles += "open_idle" to Art.openIdle()
        tiles += "closed_wait" to mattiagiordano.sorte.matrix.Elements.closedIdle(0.9)
        tiles += "open_flame" to mattiagiordano.sorte.matrix.Elements.openIdle(0.7)
        val out = File("build/anteprime").apply { mkdirs() }
        File(out, "frames.txt").writeText(tiles.joinToString("\n") { (n, f) -> n + "\t" + f.toArray().joinToString(",") })
    }
}
