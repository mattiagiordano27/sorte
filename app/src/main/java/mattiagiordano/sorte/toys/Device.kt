package mattiagiordano.sorte.toys

import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import mattiagiordano.sorte.matrix.Frame
import mattiagiordano.sorte.matrix.Small

/** Quale Glyph Matrix c'è sul telefono: Phone (3) 25×25 (interattiva) o Phone (4a) Pro 13×13 (solo always-on). */
object Device {
    val isPhone4aPro: Boolean by lazy { runCatching { Common.is25111p() }.getOrDefault(false) }
    val isPhone3: Boolean by lazy { runCatching { Common.is23112() }.getOrDefault(false) }
    val target: String get() = if (isPhone4aPro) Glyph.DEVICE_25111p else Glyph.DEVICE_23112
    fun pixels(f: Frame): IntArray = if (isPhone4aPro) Small.to13(f) else f.toArray()
}
