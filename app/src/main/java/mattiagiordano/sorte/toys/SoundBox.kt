package mattiagiordano.sorte.toys

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import mattiagiordano.sorte.R
import mattiagiordano.sorte.logic.Rng
import mattiagiordano.sorte.matrix.Sfx

/** I suoni brevi di oracoli e dadi (SoundPool: partono subito, senza ritardo). */
class SoundBox(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()

    private val ids: Map<Sfx, Int> = mapOf(
        Sfx.CHIUSO_ARRIVO to R.raw.chiuso_arrivo, Sfx.CHIUSO_PENSA to R.raw.chiuso_pensa, Sfx.CHIUSO_MA to R.raw.chiuso_ma,
        Sfx.APERTO_ARRIVO to R.raw.aperto_arrivo, Sfx.APERTO_PENSA to R.raw.aperto_pensa,
        Sfx.DADI_CLIC to R.raw.dadi_clic, Sfx.DADI_FELTRO to R.raw.dadi_feltro, Sfx.DADI_POSA to R.raw.dadi_posa,
    ).mapValues { (_, res) -> pool.load(context, res, 1) }

    /** Volume relativo di ogni suono (i file sono normalizzati). */
    private val gain = mapOf(Sfx.DADI_CLIC to 0.45f, Sfx.CHIUSO_ARRIVO to 0.6f, Sfx.APERTO_ARRIVO to 0.7f)

    private val streams = ArrayDeque<Int>()

    /** Ferma tutto quello che sta suonando (cambio di scheda, nuova domanda, uscita dal toy). */
    fun stopAll() { while (streams.isNotEmpty()) pool.stop(streams.removeFirst()) }

    fun play(s: Sfx) {
        val id = ids[s] ?: return
        val g = gain[s] ?: 1f
        // i colpi dei dadi variano un po' di tono e volume, come colpi veri
        val rate = if (s == Sfx.DADI_CLIC || s == Sfx.DADI_FELTRO) 0.88f + Rng.random.nextFloat() * 0.3f else 1f
        val vol = if (s == Sfx.DADI_CLIC) g * (0.5f + Rng.random.nextFloat() * 0.5f) else g
        val stream = pool.play(id, vol, vol, 1, 0, rate)
        if (stream != 0) { streams.addLast(stream); while (streams.size > 12) streams.removeFirst() }
    }

    fun release() = pool.release()
}
