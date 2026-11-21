package mattiagiordano.sorte

import mattiagiordano.sorte.logic.Answer
import mattiagiordano.sorte.logic.ClosedOracle
import mattiagiordano.sorte.matrix.Art
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OracleTest {
    /** Conta esatta su tutte le 100 facce del d100. */
    @Test fun distribuzioneD100() {
        val counts = (1..100).groupingBy { ClosedOracle.fromRoll(it) }.eachCount()
        assertEquals(22, counts[Answer.NO]); assertEquals(22, counts[Answer.NO_BUT])
        assertEquals(12, counts[Answer.TIE]); assertEquals(22, counts[Answer.YES_BUT])
        assertEquals(22, counts[Answer.YES])
    }

    /** 50.000 domande vere: frequenze vicine a quelle attese. */
    @Test fun estrazioniReali() {
        val n = 50_000
        val counts = mutableMapOf<Answer, Int>()
        repeat(n) { counts.merge(ClosedOracle.ask(), 1, Int::plus) }
        val pct = { a: Answer -> counts.getValue(a) * 100.0 / n }
        assertTrue(pct(Answer.TIE) in 11.0..13.0)
        for (a in listOf(Answer.NO, Answer.NO_BUT, Answer.YES_BUT, Answer.YES)) assertTrue("$a", pct(a) in 20.8..23.2)
        assertEquals("MA...", Art.qualifier(Answer.YES_BUT, true))
    }
}
