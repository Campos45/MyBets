package pt.mybets.presentation.util

import java.text.Normalizer
import java.util.Locale

/**
 * Remove acentos e converte para minúsculas para comparações insensíveis a maiúsculas/minúsculas e acentos.
 * Exemplo: "Atlético" -> "atletico", "Cíty" -> "city", "SPORTING" -> "sporting"
 */
fun String.normalizeKey(): String {
    val normalized = Normalizer.normalize(this.trim(), Normalizer.Form.NFD)
    val sb = StringBuilder()
    for (i in 0 until normalized.length) {
        val ch = normalized[i]
        // Faixa de marcas diacríticas Unicode: U+0300 a U+036F
        if (ch.code !in 0x0300..0x036F) {
            sb.append(ch)
        }
    }
    return sb.toString().lowercase(Locale.ROOT)
}
