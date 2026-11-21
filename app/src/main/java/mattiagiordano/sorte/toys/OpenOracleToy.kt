package mattiagiordano.sorte.toys

import mattiagiordano.sorte.matrix.Scripts

/**
 * Oracolo aperto — il fuoco che genera (e distrugge).
 * Arrivo: una scintilla accende la fiammella. Attesa: la fiammella che tremola.
 * Domanda: la fiammella tocca la carta, il fronte brucia e resta solo il simbolo.
 */
open class OpenOracleToy : GlyphToyService() {

    private var busy = false
    private var idling = false
    private var idleT = 0.0
    private val shake by lazy { ShakeDetector(this, onStart = { if (Device.isPhone4aPro || !charging()) ask() }, onStop = {}) }

    override fun onStart() {
        busy = true
        play(Scripts.openArrival()) { busy = false; idling = true; idleT = 0.0; idleTick() }
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
        show(Scripts.openIdle(idleT))
        idleT += Scripts.IDLE_MS / 1000.0
        later(Scripts.IDLE_MS) { idleTick() }
    }

    private fun ask() {
        if (busy) return
        val symbol = Icons.random(this) ?: return
        busy = true; idling = false
        cancelAnimations(); stopSounds()
        play(Scripts.hook(lastFrame, Scripts.openAsk(symbol))) { busy = false }
    }
}
