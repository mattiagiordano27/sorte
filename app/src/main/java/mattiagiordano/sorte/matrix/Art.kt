package mattiagiordano.sorte.matrix

import mattiagiordano.sorte.logic.Answer

/** Tutti i fotogrammi "fissi" dei toy. Kotlin puro: si può provare anche sul Mac (vedi AnteprimeTest). */
object Art {

    /** Attesa (fotogramma fermo; animata con Scripts.idle). */
    fun closedIdle() = Frame().apply { PixelFont.BIG.drawCentered(this, "?", 15, IDLE_MARK) }

    const val IDLE_MARK = 1700

    /** Il "ma" della risposta, o null se la risposta è secca. */
    fun qualifier(a: Answer, it: Boolean): String? = when (a) {
        Answer.NO_BUT, Answer.YES_BUT -> if (it) "MA..." else "BUT"
        else -> null
    }

    /**
     * [subChars] = quante lettere del "ma" mostrare. 0 = solo la parola principale, centrata
     * esattamente come una risposta secca (così non si capisce se la svolta arriverà).
     */
    fun answer(a: Answer, it: Boolean, subChars: Int = Int.MAX_VALUE, mainBottom: Int? = null): Frame {
        val f = Frame()
        val main = when (a) {
            Answer.NO, Answer.NO_BUT -> "NO"
            Answer.TIE -> if (it) "MAH" else "MEH"
            Answer.YES, Answer.YES_BUT -> if (it) "SÌ" else "YES"
        }
        val sub = qualifier(a, it)
        if (sub == null || subChars <= 0) {
            PixelFont.BIG.drawCentered(f, main, mainBottom ?: 15)
        } else {
            PixelFont.BIG.drawCentered(f, main, 12)
            // posizione finale fissa: le lettere compaiono una a una senza far scorrere il testo
            val left = (Frame.N - PixelFont.SMALL.width(sub)) / 2
            PixelFont.SMALL.draw(f, sub.take(subChars), left, 19, 2600)
        }
        return f
    }

    fun openIdle() = Frame().apply { for (x in intArrayOf(8, 12, 16)) this[x, 12] = IDLE_MARK }
}
