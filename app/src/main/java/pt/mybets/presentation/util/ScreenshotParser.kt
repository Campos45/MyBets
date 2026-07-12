package pt.mybets.presentation.util

import pt.mybets.presentation.viewmodel.GameInputState

object ScreenshotParser {
    fun parseTextToGameInputs(text: String): Pair<Double?, List<GameInputState>> {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        var stake: Double? = null
        val games = mutableListOf<GameInputState>()

        // 1. Procurar por Stake/Aposta
        // Padrões de dinheiro: "10.00€", "10 €", "Stake: 10.00", "Total: 10€"
        val stakeRegex = Regex("""(?:aposta|montante|valor|total|stake|valor aposta)[\s:]*([0-9]+(?:[.,][0-9]{2})?)""", RegexOption.IGNORE_CASE)
        for (line in lines) {
            val match = stakeRegex.find(line)
            if (match != null) {
                val valueStr = match.groupValues[1].replace(',', '.')
                stake = valueStr.toDoubleOrNull()
                if (stake != null) break
            }
        }
        if (stake == null) {
            // Alternativa: procurar por qualquer número imediatamente seguido de € ou EUR
            val fallbackStakeRegex = Regex("""([0-9]+(?:[.,][0-9]{2})?)\s*(?:€|eur|euros|usd)""", RegexOption.IGNORE_CASE)
            for (line in lines) {
                val match = fallbackStakeRegex.find(line)
                if (match != null) {
                    val valueStr = match.groupValues[1].replace(',', '.')
                    stake = valueStr.toDoubleOrNull()
                    if (stake != null) break
                }
            }
        }

        // 2. Extração dos jogos
        val teamSeparators = listOf(" vs ", " - ", " x ")
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            var separatorFound: String? = null
            for (sep in teamSeparators) {
                if (line.contains(sep, ignoreCase = true)) {
                    separatorFound = sep
                    break
                }
            }

            if (separatorFound != null) {
                val parts = line.split(separatorFound)
                var home = parts.getOrNull(0)?.trim() ?: ""
                var away = parts.getOrNull(1)?.trim() ?: ""

                // Limpar possíveis lixos nas equipas
                home = cleanTeamName(home)
                away = cleanTeamName(away)

                if (home.isNotEmpty() && away.isNotEmpty()) {
                    var competition = ""
                    var modalityName = ""
                    var odd = ""

                    // Procurar cotações/odds e competições nas 4 linhas subsequentes
                    var k = i + 1
                    val limit = minOf(lines.size, i + 5)
                    while (k < limit) {
                        val subLine = lines[k]
                        
                        // Reconhecer odd (decimais como "1.85" ou "1,85")
                        val oddRegex = Regex("""\b([1-9]\.[0-9]{2}|[1-9],[0-9]{2})\b""")
                        val matchOdd = oddRegex.find(subLine)
                        
                        if (matchOdd != null && odd.isEmpty()) {
                            odd = matchOdd.groupValues[1].replace(',', '.')
                        } else if (subLine.contains("Liga", ignoreCase = true) || 
                                   subLine.contains("League", ignoreCase = true) || 
                                   subLine.contains("Serie", ignoreCase = true) || 
                                   subLine.contains("NBA", ignoreCase = true) ||
                                   subLine.contains("Taça", ignoreCase = true) ||
                                   subLine.contains("Cup", ignoreCase = true)) {
                            competition = subLine
                        } else if (modalityName.isEmpty() && 
                                   !subLine.contains(" vs ", ignoreCase = true) && 
                                   !subLine.contains(" - ", ignoreCase = true)) {
                            modalityName = subLine
                        }
                        k++
                    }

                    games.add(
                        GameInputState(
                            homeTeam = home,
                            awayTeam = away,
                            competition = if (competition.isNotEmpty()) competition else "Desconhecida",
                            modalityName = if (modalityName.isNotEmpty()) modalityName else "1X2",
                            odd = odd
                        )
                    )
                }
            }
            i++
        }

        return Pair(stake, games)
    }

    private fun cleanTeamName(name: String): String {
        // Remove lixo comum que possa aparecer no início ou fim
        return name.replace(Regex("""^[^\p{L}\p{N}]+"""), "") // remove símbolos do início
                   .replace(Regex("""[^\p{L}\p{N}]+$"""), "") // remove símbolos do fim
                   .trim()
    }
}
