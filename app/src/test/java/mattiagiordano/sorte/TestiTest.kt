package mattiagiordano.sorte

import mattiagiordano.sorte.logic.Answer
import mattiagiordano.sorte.matrix.Art
import org.junit.Test
import java.io.File

/** Esporta TUTTI i testi dell'app in build/testi/testi.json (tools/testi.py ne fa un documento per Pages). */
class TestiTest {
    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    private fun str(s: String) = "\"" + esc(s) + "\""

    private fun manual(it: Boolean) = Manual.get(it, phone3 = true).joinToString(",\n") { b ->
        when (b) {
            is Block.Chapter -> "[\"capitolo\",${str(b.number)},${str(b.title)}]"
            is Block.Heading -> "[\"titoletto\",${str(b.text)}]"
            is Block.Para -> "[\"paragrafo\",${str(b.text)}]"
            is Block.Row -> "[\"riga\",${str(b.answer)},${str(b.text)}]"
            is Block.Example -> "[\"esempio\",${str("— " + b.question + "\n→ " + b.result.uppercase() + "\n" + b.text)}]"
            is Block.Rule -> "[\"punto\",${str(b.text)}]"
            Block.Symbols -> "[\"galleria\"]"
        }
    }

    private fun ui(t: UiText) = listOf(
        "Titolo" to t.title, "Sottotitolo" to t.tagline, "Scheda oracolo chiuso" to t.closed, "Scheda oracolo aperto" to t.open,
        "Scheda dadi" to t.dice, "Suggerimento chiuso" to t.hintClosed, "Suggerimento aperto" to t.hintOpen, "Suggerimento dadi" to t.hintDice,
        "Pagina manuale" to t.manual, "Titolo manuale" to t.manualTitle, "Impostazioni" to t.settings, "Lingua" to t.language,
        "Lingua: telefono" to t.phone, "Glyph Toy: titolo" to t.glyphTitle, "Glyph Toy: testo" to t.glyphBody, "Pulsante Glyph Toy" to t.addToys,
        "Gesto dadi: titolo" to t.gestureTitle, "Automatico" to t.gestureAuto, "Scuoti" to t.gestureShake, "Inclina" to t.gestureTilt,
        "Gesto dadi: spiegazione" to t.gestureHelp, "Suoni: titolo" to t.soundsTitle, "Suoni attivi" to t.soundsOn, "Suoni spenti" to t.soundsOff,
        "Suoni: spiegazione" to t.soundsHelp, "Nota finale" to t.about,
    ).joinToString(",\n") { (k, v) -> "[${str(k)},${str(v)}]" }

    private fun matrix(it: Boolean) = Answer.entries.joinToString(",") { a ->
        "[${str(a.name)},${str(Art.answer(a, it, subChars = 0).let { "" }.ifEmpty { matrixWord(a, it) })}]"
    }

    private fun matrixWord(a: Answer, it: Boolean): String {
        val main = when (a) { Answer.NO, Answer.NO_BUT -> "NO"; Answer.TIE -> if (it) "MAH" else "MEH"; else -> if (it) "SÌ" else "YES" }
        return main + (Art.qualifier(a, it)?.let { " / $it" } ?: "")
    }

    @Test fun esporta() {
        val json = """{
"manuale_it": [${manual(true)}],
"manuale_en": [${manual(false)}],
"interfaccia_it": [${ui(UiText.of(true))}],
"interfaccia_en": [${ui(UiText.of(false))}],
"matrice_it": [${matrix(true)}],
"matrice_en": [${matrix(false)}],
"crediti_it": [${Credits.lines(true).joinToString(",") { str(it) }}],
"crediti_en": [${Credits.lines(false).joinToString(",") { str(it) }}]
}"""
        File("build/testi").mkdirs()
        File("build/testi/testi.json").writeText(json)
    }
}
