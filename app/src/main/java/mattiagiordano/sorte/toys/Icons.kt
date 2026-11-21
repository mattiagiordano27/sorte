package mattiagiordano.sorte.toys

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import mattiagiordano.sorte.logic.Rng
import mattiagiordano.sorte.matrix.Frame

/**
 * I simboli dell'Oracolo aperto: PNG in assets/icone/ (bianco su nero, 25×25,
 * i grigi diventano luminosità intermedie). Condivisi con l'Oracolo chiuso per lo spareggio.
 */
object Icons {
    @Volatile private var cache: List<Pair<String, Frame>>? = null

    /** Tutti i simboli con il loro nome (dal nome del file: "presa_elettrica" → "presa elettrica"). */
    fun named(ctx: Context): List<Pair<String, Frame>> = cache ?: load(ctx).also { cache = it }

    fun all(ctx: Context): List<Frame> = named(ctx).map { it.second }

    fun random(ctx: Context): Frame? = all(ctx).takeIf { it.isNotEmpty() }?.let { it[Rng.random.nextInt(it.size)] }

    /** Versione attenuata, per l'effetto "carte mescolate". */
    fun dimmed(src: Frame) = Frame().also { f -> for (i in f.px.indices) f.px[i] = src.px[i] / 4 }

    private fun load(ctx: Context): List<Pair<String, Frame>> {
        val names = ctx.assets.list("icone")?.filter { it.endsWith(".png", ignoreCase = true) }.orEmpty().sorted()
        return names.mapNotNull { name ->
            runCatching { name.removeSuffix(".png").replace('_', ' ') to ctx.assets.open("icone/$name").use { BitmapFactory.decodeStream(it) }.toFrame() }
                .onFailure { Log.w("Icons", "icona illeggibile: $name", it) }
                .getOrNull()
        }
    }

    private fun Bitmap.toFrame(): Frame {
        val b = if (width == Frame.N && height == Frame.N) this
                else Bitmap.createScaledBitmap(this, Frame.N, Frame.N, false)
        val f = Frame()
        for (y in 0 until Frame.N) for (x in 0 until Frame.N) {
            val c = b.getPixel(x, y)
            val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
            f[x, y] = lum * Color.alpha(c) / 255 * Frame.FULL / 255
        }
        return f
    }
}
