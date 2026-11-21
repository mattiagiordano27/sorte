package mattiagiordano.sorte.toys

import mattiagiordano.sorte.Prefs
import mattiagiordano.sorte.logic.ClosedOracle
import mattiagiordano.sorte.matrix.Scripts

/**
 * Oracolo chiuso — vapore sulle pietre calde.
 * Arrivo: uno sbuffo lascia il "?". Attesa: il "?" con fili di vapore (animazione continua).
 * Domanda (pressione lunga, o scuoti a faccia in giù se non in carica): lo sbuffo parte dall'attesa,
 * la risposta compare; il "ma…" arriva dopo una pausa; MAH lascia la parola al fuoco, che dà un simbolo.
 */
open class ClosedOracleToy : GlyphToyService() {

    private var busy = false
    private var idling = false
    private var idleT = 0.0
    private val shake by lazy { ShakeDetector(this, onStart = { if (Device.isPhone4aPro || !charging()) ask() }, onStop = {}) }

    override fun onStart() {
        busy = true
        play(Scripts.closedArrival(glyph = true)) { busy = false; idling = true; idleT = 0.0; idleTick() }
        shake.start()
    }

    override fun onStop() {
        shake.stop()
        stopSounds()
        busy = false; idling = false
    }

    override fun onLongPress() = ask()

    private fun idleTick() {
        if (!idling) return
        show(Scripts.closedIdle(idleT, glyph = true))
        idleT += Scripts.IDLE_MS / 1000.0
        later(Scripts.IDLE_MS) { idleTick() }
    }

    private fun ask() {
        if (busy) return
        busy = true; idling = false
        cancelAnimations(); stopSounds()
        val steps = Scripts.closedAsk(ClosedOracle.ask(), Prefs.italian(this), Icons.random(this), idleT, glyph = true)
        play(Scripts.hook(lastFrame, steps)) { busy = false }
    }
}
