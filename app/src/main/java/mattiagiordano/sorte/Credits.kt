package mattiagiordano.sorte

/** Crediti: un solo posto da cambiare. */
object Credits {
    const val AUTHOR = "Mattia Giordano"
    const val YEAR = "2026"
    const val VERSION = "0.27"

    fun lines(italian: Boolean): List<String> = if (italian) listOf(
        "Sorte · © $YEAR $AUTHOR. Ideazione, simboli, disegni, testi e suoni.",
        "I simboli dell'oracolo aperto vengono dai disegni di $AUTHOR e dal suo mazzo oracolare personale, che esiste come oggetto fisico.",
        "Simboli, testi e suoni: licenza CC BY-NC-ND 4.0. Puoi condividerli citando l'autore; non puoi modificarli né usarli a scopo commerciale.",
        "Codice open source (licenza MIT).",
        "Font Doto © 2024 The Doto Project Authors, SIL Open Font License 1.1.",
        "Glyph Matrix SDK © Nothing Technology Ltd. App indipendente, non affiliata a Nothing.",
    ) else listOf(
        "Sorte · © $YEAR $AUTHOR. Concept, symbols, drawings, texts and sounds.",
        "The open oracle symbols come from the drawings of $AUTHOR and from his personal oracle deck, which exists as a physical object.",
        "Symbols, texts and sounds: CC BY-NC-ND 4.0 licence. You may share them crediting the author; you may not modify them or use them commercially.",
        "Open source code (MIT license).",
        "Doto font © 2024 The Doto Project Authors, SIL Open Font License 1.1.",
        "Glyph Matrix SDK © Nothing Technology Ltd. Independent app, not affiliated with Nothing.",
    )
}
