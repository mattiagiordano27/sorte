package mattiagiordano.sorte

import android.content.ComponentName
import android.content.Context
import com.nothing.ketchum.GlyphMatrixManager
import mattiagiordano.sorte.matrix.Frame
import mattiagiordano.sorte.toys.Device

/** Phone (4a) Pro: con l'app aperta, la matrice sul retro ripete quello che c'è sullo schermo. */
class MatrixMirror(private val ctx: Context) {
    private var gmm: GlyphMatrixManager? = null
    private var ready = false

    fun start() {
        if (gmm != null) return
        gmm = GlyphMatrixManager.getInstance(ctx.applicationContext).also { g ->
            g.init(object : GlyphMatrixManager.Callback {
                override fun onServiceConnected(name: ComponentName?) { g.register(Device.target); ready = true }
                override fun onServiceDisconnected(name: ComponentName?) { ready = false }
            })
        }
    }

    fun show(f: Frame) {
        if (!ready) return
        runCatching { gmm?.setAppMatrixFrame(Device.pixels(f)) }
    }

    fun stop() {
        runCatching { gmm?.closeAppMatrix() }
        runCatching { gmm?.unInit() }
        gmm = null; ready = false
    }
}
