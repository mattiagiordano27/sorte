package mattiagiordano.sorte.toys

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import mattiagiordano.sorte.matrix.Frame
import mattiagiordano.sorte.matrix.Haptic
import mattiagiordano.sorte.matrix.Sfx
import mattiagiordano.sorte.matrix.Step

/**
 * Base comune dei tre Glyph Toy: ciclo di vita, eventi del Glyph Button,
 * invio dei fotogrammi alla matrice, animazioni sul main thread, vibrazione.
 */
abstract class GlyphToyService : Service() {

    protected val main = Handler(Looper.getMainLooper())
    private var gmm: GlyphMatrixManager? = null
    private var sounds: SoundBox? = null

    /** Ultimo fotogramma mostrato (per agganciare un'animazione a quella in corso). */
    protected var lastFrame: Frame? = null
    private var active = false

    private val buttonHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return super.handleMessage(msg)
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_CHANGE -> onLongPress()
                GlyphToy.EVENT_ACTION_DOWN -> onButtonDown()
                GlyphToy.EVENT_ACTION_UP -> onButtonUp()
                GlyphToy.EVENT_AOD -> onAod()
            }
        }
    }
    private val messenger = Messenger(buttonHandler)

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName?) {
            gmm?.register(Device.target)
            active = true
            if (sounds == null) sounds = SoundBox(applicationContext)
            onStart()
        }
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    final override fun onBind(intent: Intent?): IBinder {
        gmm = GlyphMatrixManager.getInstance(applicationContext).also { it.init(callback) }
        return messenger.binder
    }

    final override fun onUnbind(intent: Intent?): Boolean {
        active = false
        main.removeCallbacksAndMessages(null)
        onStop()
        gmm?.turnOff()
        gmm?.unInit()
        gmm = null
        sounds?.stopAll(); sounds?.release(); sounds = null
        return false
    }

    protected fun show(frame: Frame) {
        if (!active) return
        lastFrame = frame
        try {
            gmm?.setMatrixFrame(Device.pixels(frame))
        } catch (e: Exception) {
            Log.w(javaClass.simpleName, "setMatrixFrame fallito", e)
        }
    }

    /** Programma [block] dopo [ms] millisecondi, solo se il toy è ancora attivo. */
    protected fun later(ms: Long, block: () -> Unit) {
        main.postDelayed({ if (active) block() }, ms)
    }

    protected fun cancelAnimations() = main.removeCallbacksAndMessages(null)

    /** Suono, se attivo nelle impostazioni ("Suoni dei Glyph Toy"). */
    protected fun stopSounds() { sounds?.stopAll() }

    protected fun sfx(s: Sfx) { if (mattiagiordano.sorte.Prefs.toySounds(this)) sounds?.play(s) }

    /** In carica Nothing usa lo scuotimento a faccia in giù per mostrare la batteria. */
    /**
     * Collegato al caricatore? (Non basta "sta caricando": a batteria piena o con la ricarica ottimizzata in pausa
     * il telefono è attaccato ma non carica, e Nothing usa comunque lo scuotimento per mostrare la batteria.)
     */
    protected fun charging(): Boolean {
        val b = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        return (b?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    /** Riproduce una sequenza di fotogrammi con i suoi tempi e vibrazioni, poi chiama [done]. */
    protected fun play(steps: List<Step>, done: () -> Unit = {}) {
        var t = 0L
        steps.forEach { s ->
            t += s.wait
            later(t) {
                show(s.frame)
                when (s.haptic) { Haptic.TICK -> thud(); Haptic.THUD -> thud(strong = true); Haptic.NONE -> {} }
                s.sound?.let { sfx(it) }
            }
        }
        later(t) { done() }
    }

    /** Piccolo "toc" tattile, come un dado che si ferma sul tavolo. */
    protected fun thud(strong: Boolean = false) {
        if (android.os.Build.VERSION.SDK_INT < 31) return
        val vib = (getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator ?: return
        val effect = if (strong)
            VibrationEffect.createWaveform(longArrayOf(0, 25, 60, 15), intArrayOf(0, 255, 0, 120), -1)
        else
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        vib.vibrate(effect)
    }

    protected abstract fun onStart()
    protected open fun onStop() {}
    protected open fun onLongPress() {}
    protected open fun onButtonDown() {}
    protected open fun onButtonUp() {}
    /** Toy always-on: il sistema lo chiama ogni minuto. */
    protected open fun onAod() {}
}
