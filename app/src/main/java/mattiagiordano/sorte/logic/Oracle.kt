package mattiagiordano.sorte.logic

import java.security.SecureRandom
import java.util.Random

/** Sorgente casuale unica per tutta l'app: SecureRandom = nessuno schema prevedibile. */
object Rng {
    val random: Random = SecureRandom()
    /** Un dado vero: ogni faccia da 1 a [sides] ha la stessa probabilità. */
    fun die(sides: Int): Int = random.nextInt(sides) + 1
}

enum class Answer { NO, NO_BUT, TIE, YES_BUT, YES }

/**
 * Oracolo chiuso: un dado da cento (due d10, decine e unità), ogni numero ugualmente probabile.
 * Le quattro risposte hanno la stessa probabilità; il MAH è più raro.
 *
 *   1–22    No        22%
 *   23–44   No, ma…   22%
 *   45–56   Mah       12%   (spareggio)
 *   57–78   Sì, ma…   22%
 *   79–100  Sì        22%
 */
object ClosedOracle {
    fun fromRoll(d100: Int): Answer = when (d100) {
        in 1..22 -> Answer.NO
        in 23..44 -> Answer.NO_BUT
        in 45..56 -> Answer.TIE
        in 57..78 -> Answer.YES_BUT
        in 79..100 -> Answer.YES
        else -> throw IllegalArgumentException("d100 fuori scala: $d100")
    }

    fun ask(): Answer = fromRoll(Rng.die(100))
}

object DiceSet {
    val sides = intArrayOf(4, 6, 8, 10, 12, 20, 100)
}
