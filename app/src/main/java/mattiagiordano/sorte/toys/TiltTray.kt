package mattiagiordano.sorte.toys

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Il telefono come vassoio: legge la gravità filtrata. Con il telefono a faccia in giù
 * (matrice verso l'alto) le componenti x/y dicono da che parte "pende".
 */
class TiltTray(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val g = FloatArray(3)
    private var first = true

    /** Inclinazione in m/s² nelle coordinate della matrice (x verso destra, y verso il basso, guardando il retro). */
    val tiltX get() = SIGN_X * g[0]
    val tiltY get() = SIGN_Y * g[1]
    val faceDown get() = g[2] < FACE_DOWN_Z

    fun start() {
        first = true
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() = sm.unregisterListener(this)

    override fun onSensorChanged(e: SensorEvent) {
        if (first) { for (i in 0..2) g[i] = e.values[i]; first = false; return }
        for (i in 0..2) g[i] = ALPHA * g[i] + (1 - ALPHA) * e.values[i]
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val ALPHA = 0.55f      // filtro più leggero: risponde subito all'inclinazione
        private const val FACE_DOWN_Z = -6.0f
        // guardando il retro, destra/sinistra sono invertite rispetto allo schermo: da verificare sul telefono
        var SIGN_X = 1f
        var SIGN_Y = 1f
    }
}
