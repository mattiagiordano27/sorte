package mattiagiordano.sorte

import android.content.Context
import java.util.Locale

/** Impostazioni condivise tra l'app e i Glyph Toy. */
object Prefs {
    private const val FILE = "oracolo"
    private const val KEY_LANG = "lingua"
    private const val KEY_DIE = "dado"

    enum class Lang { AUTO, IT, EN }

    fun lang(ctx: Context): Lang =
        runCatching { Lang.valueOf(sp(ctx).getString(KEY_LANG, Lang.AUTO.name)!!) }.getOrDefault(Lang.AUTO)

    fun setLang(ctx: Context, l: Lang) = sp(ctx).edit().putString(KEY_LANG, l.name).apply()

    /** Lingua effettiva sulla matrice: AUTO segue la lingua del telefono. */
    fun italian(ctx: Context): Boolean = when (lang(ctx)) {
        Lang.IT -> true
        Lang.EN -> false
        Lang.AUTO -> Locale.getDefault().language == "it"
    }

    fun dieIndex(ctx: Context): Int = sp(ctx).getInt(KEY_DIE, 5) // d20
    fun setDieIndex(ctx: Context, i: Int) = sp(ctx).edit().putInt(KEY_DIE, i).apply()

    /** Come si lanciano i dadi sul retro. AUTO = scuoti, ma in carica inclina (scuotere mostra la batteria). */
    enum class DiceGesture { AUTO, SHAKE, TILT }

    fun diceGesture(ctx: Context): DiceGesture =
        runCatching { DiceGesture.valueOf(sp(ctx).getString(KEY_GESTURE, DiceGesture.AUTO.name)!!) }.getOrDefault(DiceGesture.AUTO)

    fun setDiceGesture(ctx: Context, g: DiceGesture) = sp(ctx).edit().putString(KEY_GESTURE, g.name).apply()

    private const val KEY_GESTURE = "gesto_dadi"
    private const val KEY_TOY_SOUNDS = "suoni_toy"

    /** Suoni dei Glyph Toy (nell'app vale il volume del telefono). */
    fun toySounds(ctx: Context) = sp(ctx).getBoolean(KEY_TOY_SOUNDS, true)
    fun setToySounds(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean(KEY_TOY_SOUNDS, on).apply()

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
