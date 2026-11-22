package mattiagiordano.sorte

/** Testi dell'app: seguono la lingua scelta nell'app (non quella del telefono). */
data class UiText(
    val title: String, val tagline: String, val closed: String, val open: String, val dice: String,
    val hintClosed: String, val hintOpen: String, val hintDice: String,
    val manual: String, val manualTitle: String, val settings: String,
    val language: String, val phone: String,
    val glyphTitle: String, val glyphBody: String, val addToys: String, val about: String,
    val gestureTitle: String, val gestureAuto: String, val gestureShake: String, val gestureTilt: String,
    val gestureHelp: String,
    val proTitle: String, val proBody: String,
    val soundsTitle: String, val soundsOn: String, val soundsOff: String, val soundsHelp: String,
) {
    companion object {
        fun of(italian: Boolean) = if (italian) IT else EN

        private val IT = UiText(
            title = "Sorte", tagline = "Oracolo per scrittura creativa, divinazione e gioco di ruolo", closed = "Chiuso", open = "Aperto", dice = "Dadi",
            hintClosed = "Fai una domanda sì/no, poi tocca o scuoti",
            hintOpen = "Chiedi chi, cosa, come, poi tocca o scuoti",
            hintDice = "Tocca o scuoti per lanciare",
            manual = "Manuale", manualTitle = "Come si gioca", settings = "Impostazioni",
            language = "Lingua", phone = "Telefono",
            glyphTitle = "Glyph Toys",
            glyphBody = "Su Nothing Phone (3) i tre strumenti vivono anche sul retro, sulla Glyph Matrix: " +
                "pressione lunga sul Glyph Button, o scuoti a faccia in giù.",
            addToys = "Aggiungi ai Glyph Toys",
            about = "Gratis e open source. Tabelle originali.",
            gestureTitle = "Lancio dei dadi sul retro",
            gestureAuto = "Automatico", gestureShake = "Scuoti", gestureTilt = "Inclina",
            gestureHelp = "Scuoti: telefono a faccia in giù, scuoti e fermati. Inclina: a faccia in giù, solleva " +
                "leggermente un lato e riappoggialo, e il dado cade verso il lato più basso. In carica Nothing OS usa lo " +
                "scuotimento per mostrare la batteria: Automatico passa da solo a Inclina quando in carica.",
            proTitle = "Phone (4a) Pro",
            proBody = "Sul retro i tre strumenti si usano scuotendo il telefono a faccia in giù. Scegli quale mostrare in " +
                "Impostazioni › Glyph Interface › Flip to Glyph › Always-on Glyph Toy. Il dado si sceglie qui nell'app. " +
                "Con l'app aperta, il retro ripete lo schermo.",
            soundsTitle = "Suoni dei Glyph Toys", soundsOn = "Attivi", soundsOff = "Spenti",
            soundsHelp = "Vapore, fuoco e dadi sul retro. Nell'app i suoni seguono il volume del telefono.",
        )
        private val EN = UiText(
            title = "Sorte", tagline = "An oracle for creative writing, divination and role-playing", closed = "Closed", open = "Open", dice = "Dice",
            hintClosed = "Ask a yes/no question, then tap or shake",
            hintOpen = "Ask who, what, how, then tap or shake",
            hintDice = "Tap or shake to roll",
            manual = "Manual", manualTitle = "How to play", settings = "Settings",
            language = "Language", phone = "Phone",
            glyphTitle = "Glyph Toys",
            glyphBody = "On Nothing Phone (3) the three tools also live on the back, on the Glyph Matrix: " +
                "long press the Glyph Button, or shake face down.",
            addToys = "Add to Glyph Toys",
            about = "Free and open source. Original tables.",
            gestureTitle = "Rolling dice on the back",
            gestureAuto = "Auto", gestureShake = "Shake", gestureTilt = "Tilt",
            gestureHelp = "Shake: phone face down, shake and stop. Tilt: face down, lift one edge slightly " +
                "and set it back down, and the die falls towards the lower side. While charging Nothing OS uses shaking to show the battery: Auto switches " +
                "to Tilt by itself.",
            proTitle = "Phone (4a) Pro",
            proBody = "On the back, the three tools are used by shaking the phone face down. Choose which one to show in " +
                "Settings › Glyph Interface › Flip to Glyph › Always-on Glyph Toy. Pick the die here in the app. " +
                "With the app open, the back mirrors the screen.",
            soundsTitle = "Sounds of the Glyph Toys", soundsOn = "On", soundsOff = "Off",
            soundsHelp = "Steam, fire and dice on the back. In the app, sounds follow the phone volume.",
        )
    }
}
