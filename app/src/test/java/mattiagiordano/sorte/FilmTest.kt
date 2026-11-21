package mattiagiordano.sorte

import mattiagiordano.sorte.logic.Answer
import mattiagiordano.sorte.matrix.Scripts
import org.junit.Test
import java.io.File

/** Esporta la sequenza "attesa → sbuffo → SÌ → MA…" in build/anteprime/film.txt (per tools/film.py). */
class FilmTest {
    @Test fun esporta() {
        val idle = (0 until 30).map { Scripts.closedIdle(it * Scripts.IDLE_MS / 1000.0, glyph = true) }
        val steps = Scripts.hook(idle.last(), Scripts.closedAsk(Answer.YES_BUT, true, null, 30 * Scripts.IDLE_MS / 1000.0, glyph = true))
        var t = 0L
        val lines = idle.map { f -> t += Scripts.IDLE_MS; "$t\t" + f.toArray().joinToString(",") } +
            steps.map { s -> t += s.wait; "$t\t" + s.frame.toArray().joinToString(",") }
        File("build/anteprime").mkdirs()
        File("build/anteprime/film.txt").writeText(lines.joinToString("\n"))
    }
}
