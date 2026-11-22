package mattiagiordano.sorte

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Avviso di aggiornamento: al massimo una volta al giorno chiede a GitHub qual è l'ultima release.
 * Se è più nuova di questa versione, l'app mostra il puntino sopra «···» e il riquadro nelle impostazioni.
 * Nessun dato viene inviato: è una semplice lettura della pagina pubblica delle release.
 */
object UpdateCheck {
    data class Info(val version: String, val url: String)

    private const val API = "https://api.github.com/repos/mattiagiordano27/sorte/releases/latest"
    private const val PAGE = "https://github.com/mattiagiordano27/sorte/releases/latest"
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val FILE = "aggiornamenti"

    /** Da chiamare fuori dal thread principale. Restituisce l'aggiornamento disponibile, o null. */
    fun check(ctx: Context): Info? {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - sp.getLong("controllo", 0) > DAY_MS) {
            runCatching { fetch() }.getOrNull()?.let { info ->
                sp.edit().putLong("controllo", now).putString("versione", info.version).putString("link", info.url).apply()
            }
        }
        val v = sp.getString("versione", null) ?: return null
        return if (newer(v, Credits.VERSION)) Info(v, sp.getString("link", PAGE) ?: PAGE) else null
    }

    private fun fetch(): Info {
        val c = URL(API).openConnection() as HttpURLConnection
        c.connectTimeout = 5000; c.readTimeout = 5000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "Sorte")
        try {
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val version = json.getString("tag_name").removePrefix("v")
            // il link scarica direttamente l'APK, se c'è; altrimenti apre la pagina della release
            val assets = json.optJSONArray("assets")
            var url = json.optString("html_url", PAGE)
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) { url = a.optString("browser_download_url", url); break }
            }
            return Info(version, url)
        } finally { c.disconnect() }
    }

    /** "0.28" è più nuova di "0.27.1"? Confronta i numeri uno a uno. */
    fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }; val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }; val q = y.getOrElse(i) { 0 }
            if (p != q) return p > q
        }
        return false
    }
}
