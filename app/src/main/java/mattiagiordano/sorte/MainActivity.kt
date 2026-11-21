package mattiagiordano.sorte

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mattiagiordano.sorte.logic.ClosedOracle
import mattiagiordano.sorte.logic.DiceSet
import mattiagiordano.sorte.logic.Rng
import mattiagiordano.sorte.matrix.Art
import mattiagiordano.sorte.matrix.DiceArt
import mattiagiordano.sorte.matrix.Frame
import mattiagiordano.sorte.matrix.Haptic
import mattiagiordano.sorte.matrix.Scripts
import mattiagiordano.sorte.matrix.Sfx
import mattiagiordano.sorte.matrix.Step
import mattiagiordano.sorte.matrix.diceSettle
import mattiagiordano.sorte.toys.Icons
import mattiagiordano.sorte.toys.ShakeDetector
import mattiagiordano.sorte.toys.SoundBox
import mattiagiordano.sorte.ui.LocalPalette
import mattiagiordano.sorte.ui.OracleTheme
import mattiagiordano.sorte.ui.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Screen { PLAY, MANUAL, SETTINGS }

class MainActivity : ComponentActivity() {

    private val toysManager = Intent().setComponent(
        ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity")
    )

    /** Solo sui Nothing con la Glyph Matrix esiste la gestione dei Glyph Toy. */
    private val hasGlyphToys by lazy { packageManager.resolveActivity(toysManager, 0) != null }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        configureToys()
        setContent { OracleTheme { App() } }
    }

    /**
     * Phone (4a) Pro: solo toy always-on (niente Glyph Button) → accende le versioni always-on (a scuotimento) e nasconde i tre
     * toy interattivi. Su tutti gli altri telefoni resta tutto com'è.
     */
    private fun configureToys() {
        val pm = packageManager
        fun set(cls: Class<*>, on: Boolean) = runCatching {
            pm.setComponentEnabledSetting(ComponentName(this, cls),
                if (on) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                android.content.pm.PackageManager.DONT_KILL_APP)
        }
        val pro = mattiagiordano.sorte.toys.Device.isPhone4aPro
        listOf(mattiagiordano.sorte.toys.ProClosedOracleToy::class.java, mattiagiordano.sorte.toys.ProOpenOracleToy::class.java,
               mattiagiordano.sorte.toys.ProDiceToy::class.java).forEach { set(it, pro) }
        listOf(mattiagiordano.sorte.toys.ClosedOracleToy::class.java, mattiagiordano.sorte.toys.OpenOracleToy::class.java,
               mattiagiordano.sorte.toys.DiceToy::class.java).forEach { cls ->
            runCatching {
                pm.setComponentEnabledSetting(ComponentName(this, cls),
                    if (pro) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    android.content.pm.PackageManager.DONT_KILL_APP)
            }
        }
    }

    @Composable
    private fun App() {
        val c = LocalPalette.current
        var screen by remember { mutableStateOf(Screen.PLAY) }
        var lang by remember { mutableStateOf(Prefs.lang(this)) }
        val italian = remember(lang) { Prefs.italian(this) }
        val tx = UiText.of(italian)

        BackHandler(enabled = screen != Screen.PLAY) { screen = Screen.PLAY }

        Box(Modifier.fillMaxSize().background(c.bg).systemBarsPadding()) {
            when (screen) {
                Screen.PLAY -> Play(tx, italian, onManual = { screen = Screen.MANUAL }, onSettings = { screen = Screen.SETTINGS })
                Screen.MANUAL -> Page(tx.manual, tx.manualTitle, onBack = { screen = Screen.PLAY }) {
                    ManualView(Manual.get(italian, mattiagiordano.sorte.toys.Device.isPhone3, mattiagiordano.sorte.toys.Device.isPhone4aPro))
                }
                Screen.SETTINGS -> Page(tx.settings, tx.settings, onBack = { screen = Screen.PLAY }) {
                    Settings(tx, lang) { Prefs.setLang(this@MainActivity, it); lang = it }
                }
            }
        }
    }

    // ───────────────────────── GIOCA: solo matrice, oracoli e dadi ─────────────────────────

    @Composable
    private fun Play(tx: UiText, italian: Boolean, onManual: () -> Unit, onSettings: () -> Unit) {
        val c = LocalPalette.current
        val scope = rememberCoroutineScope()
        val view = LocalView.current
        var mode by remember { mutableIntStateOf(0) }
        var dieIdx by remember { mutableIntStateOf(Prefs.dieIndex(this).coerceIn(0, DiceSet.sides.lastIndex)) }
        var frame by remember { mutableStateOf(Art.closedIdle()) }
        var busy by remember { mutableStateOf(false) }
        var holding by remember { mutableStateOf(false) }
        var idle by remember { mutableStateOf(false) }
        var arrival by remember { mutableIntStateOf(0) }
        var idleClock by remember { mutableStateOf(0.0) }      // tempo dell'attesa: la nuvola cade da dove si trova       // cresce a ogni cambio di scheda: riparte l'arrivo
        val sides = DiceSet.sides[dieIdx]
        val sounds = remember { SoundBox(this@MainActivity) }
        DisposableEffect(Unit) { onDispose { sounds.release() } }
        // app in secondo piano o schermo bloccato: silenzio subito
        val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle) {
            val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
                if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) { sounds.stopAll(); holding = false }
            }
            lifecycle.addObserver(obs)
            onDispose { lifecycle.removeObserver(obs) }
        }
        fun foreground() = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)

        // Phone (4a) Pro: con l'app aperta la matrice sul retro ripete lo schermo (ridotta a 13×13)
        val mirror = remember { if (mattiagiordano.sorte.toys.Device.isPhone4aPro) MatrixMirror(this@MainActivity) else null }
        if (mirror != null) {
            DisposableEffect(lifecycle) {
                val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
                    if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) mirror.start()
                    if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) mirror.stop()
                }
                lifecycle.addObserver(obs)
                onDispose { lifecycle.removeObserver(obs); mirror.stop() }
            }
            LaunchedEffect(frame) { mirror.show(frame) }
        }

        suspend fun play(steps: List<Step>) = steps.forEach { s ->
            delay(s.wait); frame = s.frame
            if (foreground()) { haptic(view, s.haptic); s.sound?.let { sounds.play(it) } }
        }

        // ARRIVO + ATTESA continua (oracoli); per i dadi la sagoma ferma
        LaunchedEffect(arrival, mode) {
            if (mode == 2) { frame = DiceArt.idle(sides); idle = false; return@LaunchedEffect }
            busy = true
            play(if (mode == 0) Scripts.closedArrival() else Scripts.openArrival())
            busy = false; idle = true
        }
        LaunchedEffect(idle, mode) {
            if (!idle || mode == 2) return@LaunchedEffect
            var t = 0.0
            while (true) {
                frame = if (mode == 0) Scripts.closedIdle(t) else Scripts.openIdle(t)
                idleClock = t
                delay(Scripts.IDLE_MS); t += Scripts.IDLE_MS / 1000.0
            }
        }

        /** Oracoli: la domanda parte dall'attesa e ci si aggancia. */
        fun ask() {
            if (busy || mode == 2) return
            busy = true; idle = false
            sounds.stopAll()
            val m = mode; val from = frame
            scope.launch {
                val steps = if (m == 0) Scripts.closedAsk(ClosedOracle.ask(), italian, Icons.random(this@MainActivity), idleClock)
                            else Icons.random(this@MainActivity)?.let { Scripts.openAsk(it) } ?: emptyList()
                play(Scripts.hook(from, steps))
                busy = false
            }
        }

        /** Dadi: rotola finché [holding] (tocco = un attimo, scuotere = quanto scuoti), poi si posa. */
        fun roll() {
            if (busy) return
            busy = true; holding = true
            sounds.stopAll()
            val s = sides
            scope.launch {
                val tumbler = DiceArt.Tumbler(); var i = 0
                while (holding || i < 10) {
                    frame = DiceArt.tumble(s, tumbler.next())
                    if (foreground() && Rng.random.nextDouble() < 0.4) sounds.play(Sfx.DADI_CLIC)
                    delay(55); i++
                }
                play(Scripts.diceSettle(s, Rng.die(s)))
                busy = false
            }
        }

        fun tap() {
            if (mode != 2) { ask(); return }
            if (busy) return
            roll()
            scope.launch { delay(900); holding = false }
        }

        // scuotere il telefono (schermo in su): oracoli = domanda; dadi = rotolano finché scuoti
        DisposableEffect(sides, italian) {
            val shake = ShakeDetector(this@MainActivity,
                onStart = { if (foreground()) { if (mode == 2) roll() else ask() } },
                onStop = { holding = false }, requireFaceDown = false)
            val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
                when (e) {
                    androidx.lifecycle.Lifecycle.Event.ON_RESUME -> shake.start()
                    androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> shake.stop()
                    else -> {}
                }
            }
            lifecycle.addObserver(obs)
            onDispose { lifecycle.removeObserver(obs); shake.stop() }
        }

        @Composable fun Header() = Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tx.title, style = T.display, color = c.text)
                Text(tx.tagline.uppercase(), style = T.label, fontSize = 9.sp, color = c.muted)
            }
            RoundButton("?", onManual)
            Spacer(Modifier.width(10.dp))
            RoundButton("···", onSettings)
        }

        @Composable fun TheMatrix(m: Modifier) = Matrix(
            frame,
            m.aspectRatio(1f).clip(CircleShape)
                .clickable(remember { MutableInteractionSource() }, indication = null) { tap() }
        )

        @Composable fun Hint() = Text(
            when (mode) { 0 -> tx.hintClosed; 1 -> tx.hintOpen; else -> tx.hintDice }.uppercase(),
            style = T.label, color = c.muted, textAlign = TextAlign.Center,
        )

        @Composable fun DiceChips() = Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
            if (mode == 2) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DiceSet.sides.forEachIndexed { i, s ->
                    DieButton(s, selected = dieIdx == i) {
                        if (!busy && dieIdx != i) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            dieIdx = i; Prefs.setDieIndex(this@MainActivity, i); frame = DiceArt.idle(s)
                        }
                    }
                }
            }
        }

        @Composable fun Tabs() = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(tx.closed, tx.open, tx.dice).forEachIndexed { i, label ->
                Tab(label, selected = mode == i, modifier = Modifier.weight(1f)) {
                    if (!busy && mode != i) { sounds.stopAll(); mode = i; idle = false; arrival++ }
                }
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            if (maxWidth > maxHeight) {
                // ORIZZONTALE: matrice a sinistra alta quanto lo schermo, comandi a destra
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    TheMatrix(Modifier.fillMaxHeight())
                    Spacer(Modifier.width(28.dp))
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Header()
                        Column(
                            Modifier.weight(1f).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Hint()
                            Spacer(Modifier.height(14.dp))
                            DiceChips()
                        }
                        Tabs()
                    }
                }
            } else {
                // VERTICALE
                Column(Modifier.fillMaxSize()) {
                    Header()
                    Column(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        TheMatrix(Modifier.fillMaxWidth(0.92f))
                        Spacer(Modifier.height(22.dp))
                        Hint()
                        Spacer(Modifier.height(16.dp))
                        DiceChips()
                    }
                    Tabs()
                }
            }
        }
    }

    private fun haptic(view: View, h: Haptic) {
        when (h) {
            Haptic.TICK -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            Haptic.THUD -> view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
            )
            Haptic.NONE -> {}
        }
    }

    @Composable
    private fun Matrix(frame: Frame, modifier: Modifier) {
        val c = LocalPalette.current
        val px = frame.toArray()
        Canvas(modifier.background(Color.Black)) {
            val pitch = size.minDimension / (Frame.N + 1)
            val r = pitch * 0.36f
            for (y in 0 until Frame.N) for (x in 0 until Frame.N) {
                if (!Frame.inCircle(x, y)) continue
                val v = px[y * Frame.N + x] / Frame.FULL.toFloat()
                drawCircle(lerp(c.ledOff, c.ledOn, v), r, Offset(pitch * (x + 1), pitch * (y + 1)))
            }
        }
    }

    // ───────────────────────── pagine secondarie ─────────────────────────

    @Composable
    private fun Page(label: String, title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
        val c = LocalPalette.current
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 40.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton("←", onBack)
                Spacer(Modifier.width(14.dp))
                Text(label.uppercase(), style = T.label, color = c.muted)
            }
            Spacer(Modifier.height(24.dp))
            Text(title, style = T.chapter, color = c.text)
            Spacer(Modifier.height(18.dp))
            content()
        }
    }

    @Composable
    private fun Settings(tx: UiText, lang: Prefs.Lang, onLang: (Prefs.Lang) -> Unit) {
        val c = LocalPalette.current
        Label(tx.language)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Prefs.Lang.AUTO to tx.phone, Prefs.Lang.IT to "Italiano", Prefs.Lang.EN to "English").forEach { (l, name) ->
                Chip(name, selected = lang == l) { onLang(l) }
            }
        }
        if (mattiagiordano.sorte.toys.Device.isPhone4aPro) {
            Spacer(Modifier.height(36.dp))
            Label(tx.proTitle)
            Spacer(Modifier.height(10.dp))
            Text(tx.proBody, style = T.body, color = c.text)
        } else if (hasGlyphToys && mattiagiordano.sorte.toys.Device.isPhone3) {
            Spacer(Modifier.height(36.dp))
            Label(tx.glyphTitle)
            Spacer(Modifier.height(10.dp))
            Text(tx.glyphBody, style = T.body, color = c.text)
            Spacer(Modifier.height(16.dp))
            Pill(tx.addToys) { runCatching { startActivity(toysManager) } }

            Spacer(Modifier.height(28.dp))
            Label(tx.gestureTitle)
            Spacer(Modifier.height(10.dp))
            var gesture by remember { mutableStateOf(Prefs.diceGesture(this@MainActivity)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Prefs.DiceGesture.AUTO to tx.gestureAuto, Prefs.DiceGesture.SHAKE to tx.gestureShake,
                    Prefs.DiceGesture.TILT to tx.gestureTilt).forEach { (g, name) ->
                    Chip(name, selected = gesture == g) { Prefs.setDiceGesture(this@MainActivity, g); gesture = g }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(tx.gestureHelp, style = T.body, color = c.muted)

            Spacer(Modifier.height(28.dp))
            Label(tx.soundsTitle)
            Spacer(Modifier.height(10.dp))
            var toySounds by remember { mutableStateOf(Prefs.toySounds(this@MainActivity)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(true to tx.soundsOn, false to tx.soundsOff).forEach { (on, name) ->
                    Chip(name, selected = toySounds == on) { Prefs.setToySounds(this@MainActivity, on); toySounds = on }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(tx.soundsHelp, style = T.body, color = c.muted)
        }
        Spacer(Modifier.height(36.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Spacer(Modifier.height(16.dp))
        Label(if (Prefs.italian(this@MainActivity)) "Crediti" else "Credits")
        Spacer(Modifier.height(10.dp))
        Credits.lines(Prefs.italian(this@MainActivity)).forEach {
            Text(it, style = T.body, color = c.muted, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text("v" + Credits.VERSION, style = T.label, color = c.muted)
    }

    @Composable
    private fun ManualView(blocks: List<Block>) {
        val c = LocalPalette.current
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            blocks.forEach { b ->
                when (b) {
                    is Block.Chapter -> Column(Modifier.padding(top = 28.dp)) {
                        Text(b.number, style = T.numeral, color = c.muted)
                        Text(b.title, style = T.chapter, color = c.text)
                    }
                    is Block.Heading -> Text(b.text.uppercase(), style = T.label, color = c.text,
                        modifier = Modifier.padding(top = 14.dp))
                    is Block.Para -> Text(b.text, style = T.body, color = c.text)
                    is Block.Row -> Row(
                        Modifier.fillMaxWidth().border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(14.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(b.answer, style = T.answer, color = c.text)
                            Spacer(Modifier.height(4.dp))
                            Text(b.text, style = T.body, color = c.muted)
                        }
                    }
                    is Block.Example -> Row(Modifier.height(IntrinsicSize.Min).padding(vertical = 4.dp)) {
                        // esempio di gioco: domanda (dialogo) → RISPOSTA → interpretazione
                        Box(Modifier.width(2.dp).fillMaxHeight().background(c.line))
                        Column(Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val italian = Prefs.italian(this@MainActivity)
                            Text(if (italian) "— " + b.question else "“" + b.question + "”", style = T.example, color = c.muted)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("→", style = T.answer, color = c.text)
                                Spacer(Modifier.width(8.dp))
                                Text(b.result.uppercase(), style = T.answer, color = c.text)
                            }
                            Text(b.text, style = T.example, color = c.text)
                        }
                    }
                    is Block.Symbols -> {
                        // galleria: tutti i simboli, tre per riga, come appaiono sulla matrice
                        val all = remember { Icons.named(this@MainActivity) }
                        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            all.chunked(3).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    row.forEach { (name, f) ->
                                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Matrix(f, Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape))
                                            Spacer(Modifier.height(6.dp))
                                            // con l'articolo, come sulle carte: "la chiave", "the key"
                                            val label = if (Prefs.italian(this@MainActivity)) Manual.SYMBOL_IT[name] ?: name
                                                        else "the " + (Manual.SYMBOL_EN[name] ?: name)
                                            Text(label, style = T.label, fontSize = 10.sp, color = c.muted, textAlign = TextAlign.Center)
                                        }
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    is Block.Rule -> Row {
                        Box(Modifier.padding(top = 8.dp).size(6.dp).background(c.muted, CircleShape))
                        Text(b.text, style = T.body, color = c.text, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
    }

    // ───────────────────────── componenti ─────────────────────────

    @Composable
    private fun Label(t: String, color: Color = LocalPalette.current.muted) =
        Text(t.uppercase(), style = T.label, color = color)

    @Composable
    private fun RoundButton(t: String, onClick: () -> Unit) {
        val c = LocalPalette.current
        Box(
            Modifier.size(44.dp).clip(CircleShape).border(1.dp, c.line, CircleShape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Text(t, color = c.text, fontSize = 16.sp) }
    }

    /** Pulsante tondo di un dado: grande per il pollice, pieno bianco quando è scelto. */
    @Composable
    private fun DieButton(sides: Int, selected: Boolean, onClick: () -> Unit) {
        val c = LocalPalette.current
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .border(1.dp, if (selected) c.text else c.line, CircleShape)
                .background(if (selected) c.text else Color.Transparent)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("D", style = T.label, fontSize = 8.sp, color = if (selected) c.bg else c.muted)
                Text("$sides", style = T.answer, fontSize = if (sides == 100) 12.sp else 15.sp, color = if (selected) c.bg else c.text)
            }
        }
    }

    @Composable
    private fun Tab(t: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
        val c = LocalPalette.current
        Box(
            modifier.clip(RoundedCornerShape(50))
                .border(1.dp, if (selected) c.text else c.line, RoundedCornerShape(50))
                .background(if (selected) c.text else Color.Transparent)
                .clickable(onClick = onClick).padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text(t.uppercase(), style = T.label, color = if (selected) c.bg else c.text) }
    }

    @Composable
    private fun Chip(t: String, selected: Boolean, compact: Boolean = false, onClick: () -> Unit) {
        val c = LocalPalette.current
        Box(
            Modifier.clip(RoundedCornerShape(50))
                .border(1.dp, if (selected) c.text else c.line, RoundedCornerShape(50))
                .background(if (selected) c.text else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = if (compact) 9.dp else 14.dp, vertical = if (compact) 7.dp else 9.dp)
        ) { Text(t.uppercase(), style = T.label, color = if (selected) c.bg else c.text) }
    }

    @Composable
    private fun Pill(t: String, onClick: () -> Unit) {
        val c = LocalPalette.current
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(c.text)
                .clickable(onClick = onClick).padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) { Text(t.uppercase(), style = T.label, color = c.bg) }
    }
}
