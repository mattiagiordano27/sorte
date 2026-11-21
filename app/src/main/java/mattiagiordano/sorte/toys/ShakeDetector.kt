package mattiagiordano.sorte.toys

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * Riconosce lo scuotimento a faccia in giù (matrice verso l'alto), come il gesto
 * Nothing che mostra la batteria. Il lancio inizia solo se il telefono è a faccia in giù;
 * finisce quando si smette di scuotere: [onStop] = i dadi si fermano.
 */
class ShakeDetector(
    context: Context,
    private val onStart: () -> Unit,
    private val onStop: () -> Unit,
    /** false = nell'app: si scuote con lo schermo verso di sé, in qualsiasi posizione. */
    private val requireFaceDown: Boolean = true,
) : SensorEventListener {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gravity = FloatArray(3)
    private var shaking = false
    private var lastShake = 0L
    private var samples = 0
    private val peaks = ArrayDeque<Long>()

    fun start() {
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sm.unregisterListener(this)
        shaking = false
        samples = 0
        peaks.clear()
    }

    override fun onSensorChanged(e: SensorEvent) {
        // la gravità parte dalla prima lettura (non da zero, che sembrerebbe una scossa fortissima)
        if (samples == 0) for (i in 0..2) gravity[i] = e.values[i]
        else for (i in 0..2) gravity[i] = ALPHA * gravity[i] + (1 - ALPHA) * e.values[i]
        if (++samples < WARMUP) return
        val lx = e.values[0] - gravity[0]
        val ly = e.values[1] - gravity[1]
        val lz = e.values[2] - gravity[2]
        val force = sqrt(lx * lx + ly * ly + lz * lz)
        val now = SystemClock.uptimeMillis()
        val faceDown = !requireFaceDown || gravity[2] < FACE_DOWN_Z

        if (force > SHAKE_FORCE && (shaking || faceDown)) {
            lastShake = now
            if (!shaking) {
                // per partire servono più colpi ravvicinati: un movimento singolo non è uno scuotimento
                if (peaks.isEmpty() || now - peaks.last() > PEAK_GAP_MS) peaks.addLast(now)
                while (peaks.isNotEmpty() && now - peaks.first() > PEAK_WINDOW_MS) peaks.removeFirst()
                if (peaks.size >= PEAKS_TO_START) { shaking = true; peaks.clear(); onStart() }
            }
        } else if (shaking && now - lastShake > STILL_MS) {
            shaking = false
            onStop()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private companion object {
        const val ALPHA = 0.85f
        const val SHAKE_FORCE = 5f      // m/s² oltre la gravità
        const val FACE_DOWN_Z = -6.5f   // asse Z verso il basso = schermo sul tavolo
        const val STILL_MS = 450L
        const val WARMUP = 15            // letture ignorate all'avvio, mentre la gravità si assesta
        const val PEAKS_TO_START = 2     // colpi necessari per iniziare (2: reattivo; l'avvio è protetto dal WARMUP)
        const val PEAK_GAP_MS = 120L     // due colpi più vicini di così contano come uno
        const val PEAK_WINDOW_MS = 800L  // i colpi devono stare in questa finestra
    }
}
