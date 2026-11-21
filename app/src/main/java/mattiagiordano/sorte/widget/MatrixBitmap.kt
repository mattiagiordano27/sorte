package mattiagiordano.sorte.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import mattiagiordano.sorte.matrix.Frame

/** Disegna la matrice in un'immagine tonda (puntini tondi, come nell'app). */
object MatrixBitmap {
    private const val OFF = 0x1C
    fun render(f: Frame, size: Int = 360): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFF000000.toInt()
        c.drawCircle(size / 2f, size / 2f, size / 2f, p)
        val px = f.toArray()
        val pitch = size / (Frame.N + 1f); val r = pitch * 0.36f
        for (y in 0 until Frame.N) for (x in 0 until Frame.N) {
            if (!Frame.inCircle(x, y)) continue
            val v = px[y * Frame.N + x] / Frame.FULL.toFloat()
            val l = (OFF + (255 - OFF) * v).toInt().coerceIn(0, 255)
            p.color = (0xFF shl 24) or (l shl 16) or (l shl 8) or l
            c.drawCircle(pitch * (x + 1), pitch * (y + 1), r, p)
        }
        return bmp
    }
}
