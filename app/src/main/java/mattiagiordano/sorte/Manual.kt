package mattiagiordano.sorte

/**
 * Il manuale dell'app. Prima la parte pratica (breve), poi la poetica dove c'entra davvero:
 * nell'oracolo aperto, coi simboli di Mattia Giordano.
 * TESTO PROVVISORIO: verrà riscritto dall'autore.
 */
sealed interface Block {
    data class Chapter(val number: String, val title: String) : Block
    data class Heading(val text: String) : Block
    data class Para(val text: String) : Block
    data class Row(val answer: String, val text: String) : Block
    /** Esempio di gioco: la domanda (dialogo), la risposta dell'oracolo, l'interpretazione. */
    data class Example(val question: String, val result: String, val text: String) : Block
    data class Rule(val text: String) : Block
    /** Galleria di tutti i simboli dell'oracolo aperto. */
    data object Symbols : Block
}

object Manual {

    /**
     * Il manuale per questo telefono: la sezione "sul retro" del Phone (3) solo sul Phone (3),
     * quella del Phone (4a) Pro solo sul (4a) Pro; sugli altri Android nessuna delle due.
     */
    fun get(italian: Boolean, phone3: Boolean = false, phone4aPro: Boolean = false): List<Block> {
        var base = if (italian) IT else EN
        if (!phone3) {
            // toglie il titoletto del Phone (3) e i paragrafi che lo seguono, fino al titoletto successivo
            val h = base.indexOfFirst { it is Block.Heading && it.text.contains("Nothing Phone (3)") }
            if (h >= 0) {
                var e = h + 1
                while (e < base.size && base[e] is Block.Para) e++
                base = base.subList(0, h) + base.subList(e, base.size)
            }
        }
        if (!phone4aPro) return base
        // il Phone (4a) Pro: dentro le istruzioni, prima del capitolo II
        val at = base.indexOfFirst { it is Block.Chapter && it.number == "II" }
        val extra = if (italian) listOf(
            Block.Heading("Sul retro · Phone (4a) Pro"),
            Block.Para("Il tuo retro ha una matrice più piccola e niente Glyph Button: i tre strumenti si usano scuotendo " +
                "il telefono a faccia in giù. Scegli quale mostrare in Impostazioni › Glyph Interface › Flip to Glyph › " +
                "Always-on Glyph Toy. Il tipo di dado si sceglie nell'app."),
            Block.Para("Mentre l'app è aperta, la matrice sul retro ripete quello che vedi sullo schermo: oracoli e dadi si usano dall'app."),
        ) else listOf(
            Block.Heading("On the back · Phone (4a) Pro"),
            Block.Para("Your phone has a smaller matrix and no Glyph Button, so the three tools are used by shaking the " +
                "phone face down. Choose which one to show in Settings › Glyph Interface › Flip to Glyph › Always-on " +
                "Glyph Toy. Pick the die type in the app."),
            Block.Para("While the app is open, the matrix on the back mirrors what you see on screen: use the oracles and dice from the app."),
        )
        return base.subList(0, at) + extra + base.subList(at, base.size)
    }

    /** Nomi dei simboli con l'articolo, come scritti sulle carte. */
    val SYMBOL_IT = mapOf(
        "finestra" to "la finestra", "stella" to "la stella", "palma" to "la palma", "porta" to "la porta",
        "lucchetto" to "il lucchetto", "recinto" to "il recinto", "scatola" to "la scatola", "nuvole" to "le nuvole",
        "sedia" to "la sedia", "bicchiere" to "il bicchiere", "presa elettrica" to "la presa elettrica", "chiave" to "la chiave",
    )

    /** Nomi dei simboli in inglese (i file sono in italiano). */
    val SYMBOL_EN = mapOf(
        "finestra" to "window", "stella" to "star", "palma" to "palm", "porta" to "door",
        "lucchetto" to "padlock", "recinto" to "fence", "scatola" to "box", "nuvole" to "clouds",
        "sedia" to "chair", "bicchiere" to "glass", "presa elettrica" to "power socket", "chiave" to "key",
    )

    private val IT = listOf(
        Block.Para("Sorte è un oracolo tascabile. Fai una domanda: risponde con un sì o un no, con un simbolo da " +
            "interpretare o con un tiro di dado."),
        Block.Rule("Gioco di ruolo, anche in solitaria: fa da master quando non c'è."),
        Block.Rule("Scrittura creativa: quando la storia si blocca, chiedi cosa succede e lascia che un simbolo ti porti altrove."),
        Block.Rule("Divinazione e domande del giorno: una domanda, una risposta, per meditarci su."),
        Block.Rule("Decisioni e giochi da tavolo: un dado, uno spareggio, una svolta."),

        Block.Chapter("I", "Istruzioni"),
        Block.Heading("Nell'app"),
        Block.Para("Scegli in basso l'oracolo chiuso, l'oracolo aperto o i dadi, poi tocca la matrice o scuoti il telefono. " +
            "Per i dadi, scegli il tipo coi pulsanti sotto la matrice."),
        Block.Para("Sorte funziona anche nel browser, su iPhone, iPad o qualsiasi computer: mattiagiordano27.github.io/sorte/app."),
        Block.Heading("Sul retro · Nothing Phone (3)"),
        Block.Para("I tre strumenti sono Glyph Toys. Pressione breve sul Glyph Button per sceglierli, pressione lunga " +
            "per usarli. Puoi anche scuotere il telefono a faccia in giù."),
        Block.Para("Dadi: tieni premuto il pulsante sul retro per cambiare dado. Capovolgi il telefono e scuotilo come " +
            "un bicchiere di dadi: finché scuoti rotola, quando ti fermi rimbalza sul tavolo e si posa."),
        Block.Para("In carica Nothing OS usa lo scuotimento per mostrare la batteria: gli oracoli si usano con la pressione " +
            "lunga, e per i dadi basta sollevare leggermente un lato del telefono e riappoggiarlo: il dado cade verso il " +
            "lato più basso, rimbalza e si posa."),
        Block.Heading("I widget"),
        Block.Para("Sulla schermata Home puoi aggiungere i tre widget tondi: si usano toccandoli. Nel widget dei dadi " +
            "tocca il bordo destro (dado successivo) o sinistro (precedente); al centro lanci."),

        Block.Chapter("II", "L'Oracolo chiuso"),
        Block.Para("Per le domande a cui si risponde sì o no: «Il gentil gendarme mi ha visto?». Fai la domanda: " +
            "una piccola nuvola cade sulle pietre calde, si fa pioggia, poi vapore, e risale lasciando la risposta. " +
            "La nuvola ripercorre al contrario il ciclo che l'ha formata, e torna a salire, ciclicamente."),
        Block.Para("Il vapore ricorda la Pizia, la sacerdotessa di Delfi che rispondeva seduta sopra i vapori che " +
            "salivano dalla terra."),
        Block.Row("NO", "No, senza riserve."),
        Block.Row("NO, MA…", "No, ma qualcosa attenua il colpo: un'alternativa, un indizio, un piccolo vantaggio."),
        Block.Row("MAH", "La domanda era sbagliata, o c'è una svolta inaspettata. Il vapore si dirada e il " +
            "fuoco lascia un simbolo: leggilo come la svolta, oppure fai un'altra domanda."),
        Block.Row("SÌ, MA…", "Sì, ma c'è un prezzo, una complicazione, un dettaglio che non torna."),
        Block.Row("SÌ", "Sì, senza riserve."),
        Block.Para("Il «ma» arriva dopo un respiro: finché non compare, non sai se la risposta è netta. Cosa viene " +
            "dopo lo decidi tu: deve cambiare qualcosa, senza annullare la risposta."),
        Block.Example("La buca delle lettere è aperta?", "SÌ, MA…",
            "La serratura canta. Se la apri suonerà un valzer, e tutta la casa saprà che stai rubando la corrispondenza."),
        Block.Heading("Regole d'oro"),
        Block.Rule("Non ripetere la stessa domanda sperando in un'altra risposta. La storia va avanti da lì."),
        Block.Rule("Chiedi solo quello che non sai. Se la risposta è ovvia, decidila tu."),
        Block.Rule("Il «ma» e il simbolo sono inviti, non vincoli."),

        Block.Chapter("III", "L'Oracolo aperto"),
        Block.Para("Per le domande aperte, quelle a cui non basta un sì o un no: chi è, cosa trovo, cosa succede adesso. La " +
            "fiammella brucia la carta, il fuoco la consuma e resta un simbolo a suggestionare."),
        Block.Para("Il simbolo non ha un significato fisso. Leggilo alla lettera, come metafora o come stato d'animo. " +
            "Spesso il primo pensiero è quello buono."),
        Block.Example("Dove ho lasciato la chiave di casa?", "la chiave",
            "Ce l'avevi addosso. Da quanto tempo la cerchi, e cos'altro ti porti dietro senza saperlo?"),
        Block.Heading("Da dove vengono i simboli"),
        Block.Para("I simboli sono il mazzo oracolare di Mattia Giordano, artista che disegna a puntini: immagini che " +
            "da lontano scompaiono e si rivelano solo a chi si avvicina."),
        Block.Para("Nei suoi disegni tornano sempre gli stessi oggetti. Non li sceglie: riaffiorano dalla memoria, a " +
            "volte dopo mesi, a volte tre nella stessa settimana. Col tempo sono diventati archetipi personali, e poi " +
            "carte che adopera per creare le sue opere."),
        Block.Symbols,
        Block.Para("I tarocchi offrono archetipi universali, uguali per tutti. Queste carte sono invece un dispositivo " +
            "di riconoscimento: nascono dalla memoria di una persona, ma restano leggibili come simboli anche per chi " +
            "non ne conosce la storia. Sulla matrice i puntini diventano luci: lo stesso disegno, un punto alla volta."),
        Block.Rule("Leggi un simbolo come un oggetto ritrovato in un cassetto: cosa ti ricorda, adesso, dentro questa scena?"),
        Block.Example("Cosa c'è oltre il ponte di vetro?", "le nuvole",
            "Nebbia bassa, e qualcosa che ci cammina dentro senza far rumore."),

        Block.Chapter("IV", "I dadi"),
        Block.Para("Dei dadi d4, d6, d8, d10, d12, d20 e d100."),
        Block.Para("Il risultato massimo accende il contorno; l'1 lascia la sagoma vuota."),

        Block.Chapter("V", "Un esempio di gioco"),
        Block.Para("Sei una cartografa che attraversa la Prateria Lilla per tracciarne i confini, che cambiano ogni martedì."),
        Block.Example("Trovo un riparo per la notte?", "SÌ, MA…",
            "Una capanna su palafitte. Dentro, una minestra che si raffredda da sola, con educazione."),
        Block.Example("Chi vive qui?", "la sedia",
            "Una sola persona, rivolta verso la finestra. Qualcuno che aspetta, da molto tempo."),
        Block.Example("Tornerà stanotte?", "MAH + la porta",
            "Tornerà, ma non da dove te lo aspetti: la capanna ha una seconda porta che fino a ieri non c'era."),
        Block.Para("Nessuna di queste risposte era scritta da qualche parte. L'oracolo ti ha dato una spinta; la storia " +
            "l'hai fatta tu."),
    )

    private val EN = listOf(
        Block.Para("Sorte is a pocket oracle. Ask it something and it answers: a yes or a no, a symbol to read, or a roll of the dice."),
        Block.Rule("Role-playing, solo or round the table: it stands in for the games master when there isn't one."),
        Block.Rule("Creative writing: when the story stalls, ask what happens next and let a symbol take you somewhere else."),
        Block.Rule("Divination and questions of the day: one question, one answer, and something to mull over."),
        Block.Rule("Decisions and table games: a die, a tie-breaker, a twist."),

        Block.Chapter("I", "Instructions"),
        Block.Heading("In the app"),
        Block.Para("Pick the closed oracle, the open oracle or the dice at the bottom, then tap the matrix or give the " +
            "phone a shake. For the dice, choose the type with the buttons under the matrix."),
        Block.Para("Sorte also works in your web browser, on iPhone, iPad or any computer: mattiagiordano27.github.io/sorte/app."),
        Block.Heading("On the back · Nothing Phone (3)"),
        Block.Para("The three tools are Glyph Toys. A short press on the Glyph Button cycles through them, a long press " +
            "uses them. You can also shake the phone face down."),
        Block.Para("Dice: hold the button on the back to change die. Turn the phone over and shake it like a dice cup: " +
            "it tumbles while you shake, and when you stop it bounces on the table and settles."),
        Block.Para("While charging, Nothing OS keeps shaking for the battery: use the oracles with a long press, and roll " +
            "the die by lifting one edge of the phone slightly and setting it back down: the die falls towards the lower " +
            "side, bounces and settles."),
        Block.Heading("The widgets"),
        Block.Para("You can add the three round widgets to your home screen: just tap them. On the dice widget, tap " +
            "the right edge for the next die or the left edge for the previous one; tap the centre to roll."),

        Block.Chapter("II", "The Closed Oracle"),
        Block.Para("For questions with a yes or no answer: “Did the kindly gendarme see me?” Ask, and a small " +
            "cloud falls onto hot stones, turns to rain, then to steam, and rises again leaving the answer. " +
            "The cloud runs backwards through the cycle that made it, and rises again, over and over."),
        Block.Para("The steam is a nod to the Pythia, the priestess of Delphi who gave her answers seated above the " +
            "vapours rising from the earth."),
        Block.Row("NO", "No, plain and simple."),
        Block.Row("NO, BUT…", "No, but something softens the blow: another way, a clue, a small advantage."),
        Block.Row("MEH", "Either the question was wrong, or there's an unexpected twist. The steam clears and " +
            "the fire leaves a symbol: read it as the twist, or ask something else."),
        Block.Row("YES, BUT…", "Yes, but at a price: a complication, a detail that doesn't quite add up."),
        Block.Row("YES", "Yes, plain and simple."),
        Block.Para("The “but” arrives after a breath: until it does, you can't tell whether the answer is clean. " +
            "What follows it is yours to decide: it should change something without undoing the answer."),
        Block.Example("Is the letterbox open?", "YES, BUT…",
            "The lock sings. Open it and it'll play a waltz, and the whole house will know you're stealing the post."),
        Block.Heading("Golden rules"),
        Block.Rule("Don't ask the same question twice hoping for a different answer. The story carries on from there."),
        Block.Rule("Only ask what you don't know. If the answer's obvious, decide it yourself."),
        Block.Rule("The “but” and the symbol are invitations, not orders."),

        Block.Chapter("III", "The Open Oracle"),
        Block.Para("For open questions, the ones a yes or no can't answer: who is it, what do I find, what happens now. The little " +
            "flame catches the paper, the fire eats it away and a symbol is left behind to stir your imagination."),
        Block.Para("A symbol has no fixed meaning. Read it literally, as a metaphor or as a mood. Your first thought " +
            "is usually the right one."),
        Block.Example("Where did I leave the house key?", "the key",
            "You had it on you all along. How long have you been looking for it, and what else are you carrying around without knowing?"),
        Block.Heading("Where the symbols come from"),
        Block.Para("The symbols are Mattia Giordano's oracle deck. He is an artist who draws in dots: images that " +
            "vanish from a distance and only reveal themselves as you draw closer."),
        Block.Para("The same objects keep coming back in his drawings. He doesn't choose them: they surface from " +
            "memory, sometimes after months, sometimes three in a single week. Over time they became personal " +
            "archetypes, and then cards he works with to make his art."),
        Block.Symbols,
        Block.Para("Tarot offers universal archetypes, the same for everyone. These cards are a means of recognition " +
            "instead: born from one person's memory, yet readable as symbols even if you don't know their story. " +
            "On the matrix the dots become points of light: the same drawing, one dot at a time."),
        Block.Rule("Read a symbol as you would an object found at the back of a drawer: what does it bring to mind, here, in this scene?"),
        Block.Example("What lies beyond the glass bridge?", "the clouds",
            "Low mist, and something moving through it without a sound."),

        Block.Chapter("IV", "The dice"),
        Block.Para("Dice: d4, d6, d8, d10, d12, d20 and d100."),
        Block.Para("The highest roll lights up its edge; a 1 leaves the shape hollow."),

        Block.Chapter("V", "An example of play"),
        Block.Para("You're a cartographer crossing the Lilac Prairie to map its borders, which shift every Tuesday."),
        Block.Example("Do I find shelter for the night?", "YES, BUT…",
            "A hut on stilts. Inside, a soup that cools itself down, very politely."),
        Block.Example("Who lives here?", "the chair",
            "A single person, facing the window. Someone who has been waiting a very long time."),
        Block.Example("Will he come back tonight?", "MEH + the door",
            "He'll be back, just not the way you expect: the hut has a second door that wasn't there yesterday."),
        Block.Para("None of these answers were written down anywhere. The oracle gave you a nudge; the story is all yours."),
    )
}
