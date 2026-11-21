package mattiagiordano.sorte.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import mattiagiordano.sorte.Prefs
import mattiagiordano.sorte.R
import mattiagiordano.sorte.logic.ClosedOracle
import mattiagiordano.sorte.logic.DiceSet
import mattiagiordano.sorte.logic.Rng
import mattiagiordano.sorte.matrix.DiceArt
import mattiagiordano.sorte.matrix.Frame
import mattiagiordano.sorte.matrix.Haptic
import mattiagiordano.sorte.matrix.Scripts
import mattiagiordano.sorte.matrix.Step
import mattiagiordano.sorte.matrix.diceSettle
import mattiagiordano.sorte.toys.Icons

/**
 * Widget tondo: mostra la matrice; toccandolo parte l'oracolo (o il dado).
 * I widget non reggono animazioni fluide: passano i fotogrammi chiave (~1 ogni 120 ms).
 */
abstract class MatrixWidget : AppWidgetProvider() {
    private companion object { val running: MutableSet<Int> = java.util.Collections.synchronizedSet(mutableSetOf()) }

    protected abstract fun idle(ctx: Context): Frame
    protected abstract fun steps(ctx: Context): List<Step>
    private val action get() = "mattiagiordano.sorte.TAP_" + javaClass.simpleName
    protected val cycleAction get() = "mattiagiordano.sorte.CYCLE_" + javaClass.simpleName

    /** Per aggiungere controlli al widget (es. il cambio dado). */
    protected open fun decorate(ctx: Context, views: RemoteViews, id: Int) {}

    /** Azioni extra (es. cambio dado): restituisce true se gestita. */
    protected open fun onExtra(ctx: Context, intent: Intent, id: Int): Boolean = false

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { push(ctx, mgr, it, idle(ctx)) }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        if (intent.action == cycleAction) { onExtra(ctx, intent, id); return }
        if (intent.action != action) return
        if (!running.add(id)) return                       // questo widget sta già rispondendo
        // il tocco viene rilasciato subito: così gli altri widget restano liberi (prima andavano uno alla volta)
        Thread { try { play(ctx, id) } finally { running.remove(id) } }.start()
    }

    private fun play(ctx: Context, id: Int) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val s = steps(ctx)
        var wait = 0L
        // fotogrammi chiave: uno ogni 4, più quelli importanti; le attese lunghe (suspense del "ma") restano
        s.forEachIndexed { i, st ->
            wait += st.wait
            val key = i % 4 == 0 || i == s.lastIndex || st.haptic != Haptic.NONE
            if (key) {
                Thread.sleep(if (wait > 600) 700 else wait.coerceAtMost(160)); wait = 0
                push(ctx, mgr, id, st.frame)
            }
        }
    }

    protected fun push(ctx: Context, mgr: AppWidgetManager, id: Int, f: Frame) {
        val tap = Intent(ctx, javaClass).setAction(action).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        val pi = PendingIntent.getBroadcast(ctx, id, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val views = RemoteViews(ctx.packageName, R.layout.widget_matrix).apply {
            setImageViewBitmap(R.id.matrix, MatrixBitmap.render(f))
            setOnClickPendingIntent(R.id.matrix, pi)
            setOnClickPendingIntent(R.id.zone_left, pi)
            setOnClickPendingIntent(R.id.zone_center, pi)
            setOnClickPendingIntent(R.id.zone_right, pi)
        }
        decorate(ctx, views, id)
        mgr.updateAppWidget(id, views)
    }
}

class ClosedOracleWidget : MatrixWidget() {
    override fun idle(ctx: Context) = Scripts.closedIdle(0.6)
    override fun steps(ctx: Context) = Scripts.closedAsk(ClosedOracle.ask(), Prefs.italian(ctx), Icons.random(ctx), 0.6)
}

class OpenOracleWidget : MatrixWidget() {
    override fun idle(ctx: Context) = Scripts.openIdle(0.6)
    override fun steps(ctx: Context) = Icons.random(ctx)?.let { Scripts.openAsk(it) } ?: emptyList()
}

/**
 * Widget dei dadi: un solo cerchio grande. Zone invisibili: centro = lancia,
 * bordo destro = dado successivo, bordo sinistro = dado precedente.
 */
class DiceWidget : MatrixWidget() {
    private fun idx(ctx: Context) = Prefs.dieIndex(ctx).coerceIn(0, DiceSet.sides.lastIndex)
    private fun sides(ctx: Context) = DiceSet.sides[idx(ctx)]
    override fun idle(ctx: Context) = DiceArt.idle(sides(ctx))
    override fun steps(ctx: Context): List<Step> {
        val s = sides(ctx); val t = DiceArt.Tumbler()
        return List(12) { Step(55, DiceArt.tumble(s, t.next())) } + Scripts.diceSettle(s, Rng.die(s))
    }

    override fun decorate(ctx: Context, views: RemoteViews, id: Int) {
        fun pi(dir: Int) = PendingIntent.getBroadcast(ctx, 10_000 + id * 3 + dir + 1,
            Intent(ctx, javaClass).setAction(cycleAction).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("dir", dir),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.zone_left, pi(-1))
        views.setOnClickPendingIntent(R.id.zone_right, pi(+1))
    }

    override fun onExtra(ctx: Context, intent: Intent, id: Int): Boolean {
        val n = DiceSet.sides.size
        Prefs.setDieIndex(ctx, (idx(ctx) + intent.getIntExtra("dir", 1) + n) % n)
        val mgr = AppWidgetManager.getInstance(ctx)
        // il dado scelto è uno solo, condiviso con l'app: aggiorna tutti i widget dei dadi
        mgr.getAppWidgetIds(android.content.ComponentName(ctx, javaClass)).forEach { push(ctx, mgr, it, idle(ctx)) }
        return true
    }
}
