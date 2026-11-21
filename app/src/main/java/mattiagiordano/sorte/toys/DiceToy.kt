package mattiagiordano.sorte.toys

import mattiagiordano.sorte.Prefs
import mattiagiordano.sorte.logic.DiceSet
import mattiagiordano.sorte.logic.Rng
import mattiagiordano.sorte.matrix.DiceArt
import mattiagiordano.sorte.matrix.Scripts
import mattiagiordano.sorte.matrix.Sfx
import mattiagiordano.sorte.matrix.Step
import mattiagiordano.sorte.matrix.Haptic
import mattiagiordano.sorte.matrix.diceSettle
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Dadi. Pressione lunga = cambia dado (d4 → d6 → … → d100). Due gesti (Impostazioni → Lancio dei dadi):
 *  - SCUOTI: a faccia in giù, scuoti; quando smetti il dado rimbalza e si posa.
 *  - INCLINA: a faccia in giù il telefono è un vassoio: il dado scivola verso il lato basso e rimbalza
 *    sul bordo; rimesso in piano si ferma e mostra il numero.
 *  - AUTOMATICO: scuoti, ma in carica inclina (in carica Nothing usa lo scuotimento per la batteria).
 */
open class DiceToy : GlyphToyService() {

    private enum class State { IDLE, ROLLING, LANDING, SHOWN }

    private var dieIdx = 5
    private var state = State.IDLE
    private val tray by lazy { TiltTray(this) }
    private val shake by lazy { ShakeDetector(this, onStart = ::startShake, onStop = ::endShake) }
    private var tumbler = DiceArt.Tumbler()
    private var rollingByShake = false

    // fisica in pixel e secondi
    private var x = 0.0; private var y = 0.0
    private var vx = 0.0; private var vy = 0.0
    private var angle = 0.0
    private var travelled = 0.0
    private var stillMs = 0L
    private var baseX = 0.0; private var baseY = 0.0       // inclinazione "di riposo"
    private var warmup = 0

    private val sides get() = DiceSet.sides[dieIdx]

    override fun onStart() {
        dieIdx = Prefs.dieIndex(this).coerceIn(0, DiceSet.sides.lastIndex)
        tray.start()
        shake.start()
        warmup = 0
        state = State.IDLE
        showIdle()
        loop()
    }

    override fun onStop() {
        tray.stop()
        shake.stop()
    }

    /** Inclina o scuoti? Deciso a ogni lancio. */
    private fun useTilt(): Boolean = when (Prefs.diceGesture(this)) {
        Prefs.DiceGesture.TILT -> true
        Prefs.DiceGesture.SHAKE -> false
        Prefs.DiceGesture.AUTO -> !Device.isPhone4aPro && charging()   // sul (4a) Pro c'è solo lo scuotimento
    }

    // ── SCUOTI ──

    private fun startShake() {
        if (useTilt() || state == State.ROLLING || state == State.LANDING) return
        state = State.ROLLING; rollingByShake = true
        tumbler = DiceArt.Tumbler()
        shakeTick()
    }

    private fun shakeTick() {
        if (!rollingByShake) return
        show(DiceArt.tumble(sides, tumbler.next()))
        if (mattiagiordano.sorte.logic.Rng.random.nextDouble() < 0.4) sfx(Sfx.DADI_CLIC)    // i dadi tra le mani
        later(55) { shakeTick() }
    }

    private fun endShake() {
        if (!rollingByShake) return
        rollingByShake = false
        state = State.LANDING
        play(Scripts.diceSettle(sides, Rng.die(sides))) { state = State.SHOWN }
    }

    override fun onLongPress() {
        if (state == State.ROLLING || state == State.LANDING) return
        dieIdx = (dieIdx + 1) % DiceSet.sides.size
        Prefs.setDieIndex(this, dieIdx)
        state = State.IDLE
        showIdle()
        thud()
    }

    /** Cambio dado: la nuova sagoma "arriva" crescendo. */
    private fun showIdle() {
        show(DiceArt.idle(sides, 0.55))
        later(60) { show(DiceArt.idle(sides, 0.8)) }
        later(120) { show(DiceArt.idle(sides)) }
    }

    /** Un passo di fisica ogni 40 ms, sempre attivo mentre il toy è selezionato. */
    private fun loop() {
        step(DT)
        later((DT * 1000).toLong()) { loop() }
    }

    private fun step(dt: Double) {
        if (!tray.faceDown) { if (state == State.ROLLING) stillMs = 0; return }
        val tx = tray.tiltX.toDouble(); val ty = tray.tiltY.toDouble()
        val tilt = hypot(tx, ty)
        // primi istanti: impara come è appoggiato il telefono
        if (warmup < 8) { warmup++; baseX = tx; baseY = ty; return }

        when (state) {
            State.IDLE, State.SHOWN -> {
                // parte con un colpetto d'inclinazione rispetto a come è appoggiato (anche se sul caricatore sta storto)
                val delta = hypot(tx - baseX, ty - baseY)
                if (delta > START_DELTA && useTilt()) tiltThrow(tx - baseX, ty - baseY)
                else { baseX = baseX * 0.995 + tx * 0.005; baseY = baseY * 0.995 + ty * 0.005 }   // segue la posa molto lentamente (8 s)
            }
            State.ROLLING -> if (!rollingByShake) roll(dt, tx, ty, tilt)
            State.LANDING -> {}
        }
    }

    /**
     * Lancio inclinando: appena inclini il dado cade subito verso il lato abbassato, accelerando e ruotando,
     * sbatte sul bordo, rimbalza e si posa al centro mostrando il numero (~1 s, tutto scritto in anticipo:
     * la risposta è immediata e leggibile, senza aspettare la fisica).
     */
    private fun tiltThrow(dx: Double, dy: Double) {
        state = State.LANDING
        val d = hypot(dx, dy).coerceAtLeast(0.01); val ux = dx / d; val uy = dy / d
        val spin = (if (Rng.random.nextBoolean()) 1 else -1) * (300.0 + Rng.random.nextDouble() * 200)
        val a0 = Rng.random.nextDouble() * 360
        val steps = mutableListOf<Step>()
        // 1) cade: dal centro al bordo in 9 fotogrammi, accelerando (gravità), si rimpicciolisce e ruota
        val n = 9
        for (i in 1..n) {
            val u = i.toDouble() / n; val e = u * u
            steps += Step(32, DiceArt.tumble(sides, DiceArt.Pose(a0 + spin * e, 1 - 0.4 * u, ux * LIMIT * e, uy * LIMIT * e)),
                sound = if (i == 1) Sfx.DADI_CLIC else null)
        }
        // 2) colpo sul bordo
        steps += Step(40, DiceArt.tumble(sides, DiceArt.Pose(a0 + spin, 0.58, ux * (LIMIT + 0.6), uy * (LIMIT + 0.6))),
            Haptic.TICK, Sfx.DADI_FELTRO)
        // 3) torna indietro rimbalzando, sempre più piano, verso il centro
        val back = listOf(0.55 to 0.35, 0.3 to 0.18, 0.12 to 0.08)
        back.forEachIndexed { k, (pos, turn) ->
            steps += Step(70L + k * 40, DiceArt.tumble(sides, DiceArt.Pose(a0 + spin * (1 + turn), 0.62 + k * 0.12, ux * LIMIT * pos, uy * LIMIT * pos)),
                Haptic.TICK, if (k == 0) Sfx.DADI_FELTRO else null)
        }
        // 4) si posa: numero
        steps += Step(DiceArt.LAND_DELAY, DiceArt.face(sides, Rng.die(sides)), Haptic.THUD, Sfx.DADI_POSA)
        play(steps) {
            state = State.SHOWN
            baseX = tray.tiltX.toDouble(); baseY = tray.tiltY.toDouble()     // per rilanciare serve un'altra inclinazione
        }
    }

    private fun startRolling() {
        state = State.ROLLING
        x = 0.0; y = 0.0; vx = 0.0; vy = 0.0
        angle = Rng.random.nextDouble() * 360
        travelled = 0.0; stillMs = 0
        thud()
    }

    private fun roll(dt: Double, tx: Double, ty: Double, tilt: Double) {
        // accelerazione verso il lato basso + attrito del panno
        vx = (vx + tx * ACCEL * dt) * FRICTION
        vy = (vy + ty * ACCEL * dt) * FRICTION
        x += vx * dt; y += vy * dt
        val speed = hypot(vx, vy)
        travelled += speed * dt
        angle += speed * dt * SPIN * (if (vx >= 0) 1 else -1)

        // bordo del vassoio: rimbalzo con perdita di energia
        val d = hypot(x, y)
        if (d > LIMIT) {
            val nx = x / d; val ny = y / d
            val vn = vx * nx + vy * ny
            if (vn > 0) {
                vx -= (1 + BOUNCE) * vn * nx
                vy -= (1 + BOUNCE) * vn * ny
                if (vn > 6) { thud(); sfx(Sfx.DADI_FELTRO) }
            }
            x = nx * LIMIT; y = ny * LIMIT
            vx *= 0.8; vy *= 0.8                                // il bordo frena: il dado si appoggia e si ferma
        }

        show(DiceArt.tumble(sides, DiceArt.Pose(angle, ROLL_SCALE, x, y)))
        if (speed > 3 && Rng.random.nextDouble() < 0.12) sfx(Sfx.DADI_CLIC)          // rotola sul feltro

        // fermo: telefono in piano e dado quasi immobile
        // si ferma da solo quando è quasi immobile (anche appoggiato al bordo, come in un vassoio vero)
        stillMs = if (speed < STOP_SPEED) stillMs + (dt * 1000).toLong() else 0
        if (stillMs > STILL_MS && travelled > MIN_TRAVEL) land()
        else if (stillMs > STILL_MS * 3) { state = State.IDLE; showIdle() } // mosso troppo poco: nessun lancio
    }

    /** Il dado si posa: scivola al centro, si raddrizza, cresce, mostra il numero. */
    private fun land() {
        state = State.LANDING
        val value = Rng.die(sides)
        val a0 = ((angle % 90) + 90) % 90
        val toZero = if (a0 > 45) 90 - a0 else -a0
        val n = 6
        for (i in 1..n) {
            val k = i.toDouble() / n
            later(i * 45L) {
                show(DiceArt.tumble(sides, DiceArt.Pose(
                    angle + toZero * k, ROLL_SCALE + (1 - ROLL_SCALE) * k, x * (1 - k), y * (1 - k))))
            }
        }
        later(n * 45L + 120) {
            show(DiceArt.face(sides, value))
            thud(strong = true); sfx(Sfx.DADI_POSA)
            state = State.SHOWN
            baseX = tray.tiltX.toDouble(); baseY = tray.tiltY.toDouble()
        }
    }

    private companion object {
        const val DT = 0.04
        const val ROLL_SCALE = 0.55      // dado più piccolo mentre rotola, per avere spazio
        const val LIMIT = 6.5            // raggio entro cui si muove il centro del dado (pixel)
        const val ACCEL = 2.6            // pixel/s² per m/s² di inclinazione
        const val FRICTION = 0.93       // panno: più attrito = si ferma prima
        const val BOUNCE = 0.45
        const val SPIN = 9.0             // gradi di rotazione per pixel percorso
        const val START_DELTA = 1.7      // m/s² di variazione d'inclinazione (~10°): basta un colpetto
        const val STOP_SPEED = 2.0
        const val STILL_MS = 350L
        const val MIN_TRAVEL = 6.0       // pixel minimi percorsi perché sia un lancio vero
    }
}
