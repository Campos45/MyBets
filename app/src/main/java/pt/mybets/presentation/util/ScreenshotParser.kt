package pt.mybets.presentation.util

import pt.mybets.presentation.viewmodel.GameInputState
import java.util.Locale
import java.util.UUID

object ScreenshotParser {

    /**
     * Analisa o texto OCR extraído de screenshots de apostas (Betano, Placard, 1xBet, etc.)
     * Totalmente livre de Regex para evitar qualquer incompatibilidade com o motor ICU do Android.
     */
    fun parseTextToGameInputs(text: String): Pair<Double?, List<GameInputState>> {
        val rawLines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val splitLines = splitMergedLines(rawLines)

        // Filtrar linhas óbvias de barra de estado do sistema, navegação ou rodapé da app
        val lines = splitLines.filter { line ->
            !isClockHeader(line) &&
            !line.equals("Bet details", ignoreCase = true) &&
            !line.equals("Sobre liquidação de apostas", ignoreCase = true) &&
            !line.equals("Popular", ignoreCase = true) &&
            !line.equals("Favorites", ignoreCase = true) &&
            !line.equals("Bet slip", ignoreCase = true) &&
            !line.equals("History", ignoreCase = true) &&
            !line.equals("Menu", ignoreCase = true)
        }

        val stake = extractStake(lines)
        val extractedGames = mutableListOf<GameInputState>()

        // 1. Tentar jogos inline com placar primeiro (ex: "Dinamarca 2 - 4 Portugal" ou "Spain 4 : 1 Croatia")
        val inlineGames = parseInlineGames(lines)
        if (inlineGames.isNotEmpty()) {
            extractedGames.addAll(inlineGames)
        } else {
            // 2. Tentar Padrão Betano ancorado nos cabeçalhos de mercado (ex: Resultado Final, Hipótese Dupla, etc.)
            val anchoredGames = parseBetanoMarketAnchored(lines)
            if (anchoredGames.isNotEmpty()) {
                extractedGames.addAll(anchoredGames)
            } else {
                // 3. Fallback Padrão Betano por marcadores de data/resultado
                val betanoGames = parseBetanoStyle(lines)
                extractedGames.addAll(betanoGames)
            }
        }

        // 4. Fallback: Separadores clássicos (vs, x, -) se ainda não encontrou
        if (extractedGames.isEmpty()) {
            extractedGames.addAll(parseClassicSeparators(lines))
        }

        return Pair(stake, extractedGames)
    }

    /**
     * Padrão Betano de alta precisão ancorado nos cabeçalhos de mercado:
     * - O campo ACIMA do mercado indica o que foi apostado (Seleção) e a Odd.
     * - O mercado (Resultado Final, Hipótese Dupla, etc.) define o tipo de aposta.
     * - O campo IMEDIATAMENTE ABAIXO do mercado lista as equipas por ordem: 1º Casa, 2º Fora.
     * - A modalidade é associada como 1, X, 2 (ou 1X, X2, 12, etc.) conforme a seleção corresponda à casa, empate ou visitante.
     */
    private fun parseBetanoMarketAnchored(lines: List<String>): List<GameInputState> {
        val games = mutableListOf<GameInputState>()

        val marketIndices = mutableListOf<Int>()
        for (i in lines.indices) {
            if (isBetanoMarketHeader(lines[i])) {
                marketIndices.add(i)
            }
        }

        if (marketIndices.isEmpty()) return emptyList()

        for (idx in marketIndices.indices) {
            val m = marketIndices[idx]
            val marketLine = lines[m]

            val prevMarketIdx = if (idx > 0) marketIndices[idx - 1] else -1
            val nextMarketIdx = if (idx < marketIndices.size - 1) marketIndices[idx + 1] else lines.size

            // As equipas devem ser lidas no campo abaixo do mercado por essa ordem: Casa e Fora
            var casa: String? = null
            var fora: String? = null

            val forwardLimit = minOf(nextMarketIdx, m + 5)
            for (k in (m + 1) until forwardLimit) {
                val lineK = lines[k]
                if (isDateLine(lineK) || isScoreMarkerLine(lineK) || isBetanoMarketHeader(lineK)) {
                    continue
                }
                val candidate = cleanTeamName(lineK)
                if (isValidTeam(candidate)) {
                    if (casa == null) {
                        casa = candidate
                    } else if (fora == null && !candidate.equals(casa, ignoreCase = true)) {
                        fora = candidate
                        break
                    }
                }
            }

            if (casa != null && fora != null) {
                var odd = ""
                var selection = ""

                // Procurar odd e seleção nas linhas ANTES do cabeçalho de mercado
                val backwardLimit = maxOf(0, prevMarketIdx + 1, m - 5)
                for (k in (m - 1) downTo backwardLimit) {
                    val subLine = lines[k]
                    if (isDateLine(subLine) || isScoreMarkerLine(subLine)) continue

                    if (odd.isEmpty()) {
                        val candidateOdd = extractOdd(subLine)
                        if (candidateOdd != null) {
                            odd = candidateOdd
                            continue
                        }
                    }

                    if (selection.isEmpty() && isSelectionCandidate(subLine)) {
                        val teamClean = cleanTeamName(subLine)
                        selection = if (teamClean.isNotEmpty()) teamClean else subLine.trim()
                    }
                }

                // Fallback para odd se não encontrada antes do mercado: procurar dentro do cartão
                if (odd.isEmpty()) {
                    for (k in (m + 1) until forwardLimit) {
                        val candidateOdd = extractOdd(lines[k])
                        if (candidateOdd != null) {
                            odd = candidateOdd
                            break
                        }
                    }
                }

                val finalModality = resolveBetanoModality(marketLine, selection, casa, fora)

                games.add(
                    GameInputState(
                        id = UUID.randomUUID().toString(),
                        homeTeam = casa,
                        awayTeam = fora,
                        competition = "Desconhecida",
                        modalityName = finalModality,
                        odd = odd
                    )
                )
            }
        }

        return games
    }

    fun isBetanoMarketHeader(line: String): Boolean {
        val lower = line.lowercase().trim()
        if (lower.isEmpty()) return false
        if (isClockHeader(line) || isDateLine(line)) return false
        // Não pode ser uma linha de placar/resultado (ex: "Resultado 0-1")
        if (lower.startsWith("resultado") && (lower.contains("-") || lower.contains(":") || lower.contains("–"))) {
            return false
        }
        return lower.contains("resultado final") ||
                lower.contains("hipótese dupla") ||
                lower.contains("hipotese dupla") ||
                lower.contains("total de golos") ||
                lower.contains("golos mais/menos") ||
                lower.contains("ambas as equipas") ||
                lower.contains("ambas equipas") ||
                lower.contains("vantagem") ||
                lower.contains("empate anula") ||
                lower.contains("intervalo / final") ||
                lower.contains("intervalo/final") ||
                lower.contains("handicap") ||
                lower.contains("total de cantos")
    }

    fun resolveBetanoModality(market: String, selection: String, casa: String, fora: String): String {
        val normSel = selection.normalizeKey()
        val normCasa = casa.normalizeKey()
        val normFora = fora.normalizeKey()
        val normMarket = market.normalizeKey()

        // 1. Hipótese Dupla (1X, X2, 12)
        if (normMarket.contains("hipotese") || normMarket.contains("dupla")) {
            val hasCasa = normSel.contains(normCasa) || (normCasa.isNotEmpty() && normCasa.split(" ").any { it.length >= 4 && normSel.contains(it) })
            val hasFora = normSel.contains(normFora) || (normFora.isNotEmpty() && normFora.split(" ").any { it.length >= 4 && normSel.contains(it) })
            val hasEmpate = normSel.contains("empate") || normSel.contains(" x ") || normSel.endsWith(" x")

            return when {
                hasCasa && hasEmpate -> "1X"
                hasFora && hasEmpate -> "X2"
                hasCasa && hasFora -> "12"
                hasCasa -> "1X"
                hasFora -> "X2"
                else -> "Hipótese Dupla"
            }
        }

        // 2. Resultado Final (1, X, 2)
        if (normMarket.contains("resultado final") || normMarket.contains("1x2") || normMarket.contains("vencedor")) {
            val isCasa = normSel == normCasa || normSel.contains(normCasa) || (normCasa.isNotEmpty() && normCasa.split(" ").any { it.length >= 4 && normSel == it })
            val isFora = normSel == normFora || normSel.contains(normFora) || (normFora.isNotEmpty() && normFora.split(" ").any { it.length >= 4 && normSel == it })
            val isEmpate = normSel == "empate" || normSel == "x"

            return when {
                isCasa -> "1"
                isFora -> "2"
                isEmpate -> "X"
                else -> "Resultado Final"
            }
        }

        // 3. +2 Golos Vantagem
        if (normMarket.contains("vantagem")) {
            val isCasa = normSel == normCasa || normSel.contains(normCasa) || (normCasa.isNotEmpty() && normCasa.split(" ").any { it.length >= 4 && normSel.contains(it) })
            val isFora = normSel == normFora || normSel.contains(normFora) || (normFora.isNotEmpty() && normFora.split(" ").any { it.length >= 4 && normSel.contains(it) })
            val prefix = cleanModalityName(market)
            return when {
                isCasa -> if (prefix.isNotBlank()) "$prefix (1)" else "1"
                isFora -> if (prefix.isNotBlank()) "$prefix (2)" else "2"
                else -> if (prefix.isNotBlank()) prefix else market
            }
        }

        // 4. Over / Under (Total de Golos Mais/Menos)
        if (normSel.startsWith("mais de") || normSel.startsWith("menos de") ||
            normSel.startsWith("over") || normSel.startsWith("under")) {
            return selection.trim()
        }

        // 5. Ambas as Equipas Marcam
        if (normMarket.contains("ambas")) {
            return if (normSel == "sim") "Ambas as Equipas Marcam (Sim)" else if (normSel == "nao" || normSel == "não") "Ambas as Equipas Marcam (Não)" else "Ambas as Equipas Marcam"
        }

        // 6. Caso geral
        val isCasa = normSel == normCasa || normSel.contains(normCasa)
        val isFora = normSel == normFora || normSel.contains(normFora)
        if (isCasa) return "1"
        if (isFora) return "2"
        if (normSel == "empate" || normSel == "x") return "X"

        return if (selection.isNotBlank()) selection else cleanModalityName(market)
    }

    private fun isSelectionCandidate(line: String): Boolean {
        val lower = line.lowercase().trim()
        if (lower.isEmpty()) return false
        if (isClockHeader(line) || isDateLine(line) || isScoreMarkerLine(line)) return false
        if (lower.contains("€") || lower.contains("eur") || lower.contains("cota") ||
            lower.contains("odds:") || lower.contains("winnings") || lower.contains("ganho") ||
            lower.contains("stake") || lower.contains("montante") || lower.contains("saldo") ||
            lower.contains("deposito") || lower.contains("depósito") || lower.contains("perdeu") ||
            lower.contains("ganhou") || lower.contains("múltipla") || lower.contains("multipla") ||
            lower.contains("seleções") || lower.contains("selecoes") || lower.contains("sobre liquidação")
        ) return false

        // Não pode ser apenas um número isolado (ex: "2.5" ou "1.57"), a menos que seja 1 ou 2
        val isPureNumber = lower.all { it.isDigit() || it == '.' || it == ',' }
        if (isPureNumber && lower != "1" && lower != "2") return false

        return true
    }

    fun splitMergedLines(lines: List<String>): List<String> {
        val result = mutableListOf<String>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            // 1. Data e Hora no final da linha precedida de nome de equipa (ex: "Ipswich Town 17/10/2026, 15:00")
            val dateSplit = splitDateFromLine(line)
            if (dateSplit != null) {
                result.add(dateSplit.first)
                result.add(dateSplit.second)
                continue
            }

            // 2. Marcador "Resultado X-Y" no final da linha (ex: "Chelsea Resultado 0-2" ou "Chelsea | Resultado 0-2")
            val scoreSplit = splitScoreMarkerFromLine(line)
            if (scoreSplit != null) {
                result.add(scoreSplit.first)
                result.add(scoreSplit.second)
                continue
            }

            // 3. Linha com seleção e odd no final (ex: "Manchester City 1.24" ou "Lille ou Empate 1.23")
            val oddSplit = splitOddFromLine(line)
            if (oddSplit != null) {
                result.add(oddSplit.first)
                result.add(oddSplit.second)
                continue
            }

            result.add(line)
        }
        return result
    }

    private fun splitDateFromLine(line: String): Pair<String, String>? {
        val tokens = line.split(" ").filter { it.isNotBlank() }
        if (tokens.size < 2) return null

        val dateTokenIdx = tokens.indexOfFirst { token ->
            val cleanT = token.trim(',', ';', '(', ')')
            (cleanT.count { it == '/' || it == '.' || it == '-' } == 2) && cleanT.any { it.isDigit() }
        }

        if (dateTokenIdx > 0) {
            val prefix = tokens.take(dateTokenIdx).joinToString(" ").trim()
            val datePart = tokens.drop(dateTokenIdx).joinToString(" ").trim()
            if (isDateLine(datePart) && prefix.isNotEmpty()) {
                return Pair(prefix, datePart)
            }
        }
        return null
    }

    private fun splitScoreMarkerFromLine(line: String): Pair<String, String>? {
        val lower = line.lowercase()
        val resIdx = lower.indexOf("resultado")
        if (resIdx > 0) {
            val prefix = line.substring(0, resIdx).trim().trimEnd('|', '-', ':', '.', ' ')
            val scorePart = line.substring(resIdx).trim()
            if (isScoreMarkerLine(scorePart) && prefix.isNotEmpty()) {
                return Pair(prefix, scorePart)
            }
        }
        return null
    }

    private fun splitOddFromLine(line: String): Pair<String, String>? {
        val tokens = line.split(" ").filter { it.isNotBlank() }
        if (tokens.size < 2) return null

        val lastToken = tokens.last().trim('(', ')', '[', ']')
        if (lastToken.contains(".") || lastToken.contains(",")) {
            val num = lastToken.replace(',', '.').toDoubleOrNull()
            if (num != null && num in 1.01..100.0) {
                val prefix = tokens.dropLast(1).joinToString(" ").trim()
                val hasLetters = prefix.any { Character.isLetter(it) }
                val isScore = prefix.contains(" - ") || prefix.contains(" : ")
                val lowerPrefix = prefix.lowercase()
                val isMonetary = lowerPrefix.contains("valor") || lowerPrefix.contains("stake") ||
                        lowerPrefix.contains("cota") || lowerPrefix.contains("ganho") ||
                        lowerPrefix.contains("montante") || lowerPrefix.contains("deposito") ||
                        lowerPrefix.contains("depósito") || lowerPrefix.contains("saldo")

                val isGoalLine = lowerPrefix.endsWith("mais de") || lowerPrefix.endsWith("menos de") ||
                        lowerPrefix == "mais" || lowerPrefix == "menos" ||
                        lowerPrefix.endsWith("over") || lowerPrefix.endsWith("under")

                if (hasLetters && !isScore && !isMonetary && !isGoalLine) {
                    return Pair(prefix, lastToken)
                }
            }
        }
        return null
    }

    private fun isClockHeader(line: String): Boolean {
        // Ex: "21:00", "19:32"
        if (line.length in 4..5 && line.contains(":")) {
            val parts = line.split(":")
            if (parts.size == 2 && parts[0].all { it.isDigit() } && parts[1].all { it.isDigit() }) {
                return true
            }
        }
        return false
    }

    private fun extractStake(lines: List<String>): Double? {
        val keywords = listOf("stake", "montante", "valor da aposta", "valor", "aposta", "seleções", "selecoes")

        // Prioridade 1: Linhas que contêm palavras-chave explícitas de aposta/stake/montante
        for (line in lines) {
            val lower = line.lowercase()
            if (lower.contains("ganho") ||
                lower.contains("winning") ||
                lower.contains("cota") ||
                lower.contains("odds:") ||
                lower.contains("depósito") ||
                lower.contains("deposito") ||
                lower.contains("saldo")
            ) {
                continue
            }

            if (keywords.any { lower.contains(it) }) {
                val cleaned = removeSelectionWord(lower)
                val value = extractMonetaryNumber(cleaned)
                if (value != null && value > 0.0) return value
            }
        }

        // Prioridade 2: Linhas com € ou eur que não sejam ganhos/cota
        for (line in lines) {
            val lower = line.lowercase()
            if (lower.contains("ganho") ||
                lower.contains("winning") ||
                lower.contains("cota") ||
                lower.contains("odds:") ||
                lower.contains("depósito") ||
                lower.contains("deposito") ||
                lower.contains("saldo")
            ) {
                continue
            }
            if (lower.contains("€") || lower.contains("eur")) {
                val cleaned = removeSelectionWord(lower)
                val value = extractMonetaryNumber(cleaned)
                if (value != null && value > 0.0) return value
            }
        }

        return null
    }

    private fun removeSelectionWord(text: String): String {
        val words = text.split(" ")
        val sb = StringBuilder()
        var skipNext = false
        for (i in words.indices) {
            if (skipNext) {
                skipNext = false
                continue
            }
            val w = words[i]
            if (i < words.size - 1 && w.all { it.isDigit() } && words[i + 1].startsWith("selec", ignoreCase = true)) {
                skipNext = true
                continue
            }
            if (w.startsWith("selec", ignoreCase = true)) continue
            sb.append(w).append(" ")
        }
        return sb.toString().trim()
    }

    private fun extractMonetaryNumber(text: String): Double? {
        val tokens = text.replace("€", " € ").split(" ").filter { it.isNotBlank() }

        // 1. Número imediatamente anterior ou posterior a €
        val euroIdx = tokens.indexOfFirst { it == "€" || it.equals("eur", ignoreCase = true) }
        if (euroIdx > 0) {
            val prev = tokens[euroIdx - 1].replace(',', '.').toDoubleOrNull()
            if (prev != null && prev > 0.0) return prev
        }
        if (euroIdx in 0 until tokens.size - 1) {
            val next = tokens[euroIdx + 1].replace(',', '.').toDoubleOrNull()
            if (next != null && next > 0.0) return next
        }

        // 2. Qualquer número decimal
        for (t in tokens) {
            if (t.contains(",") || t.contains(".")) {
                val num = t.replace(',', '.').toDoubleOrNull()
                if (num != null && num > 0.0) return num
            }
        }

        // 3. Qualquer número inteiro
        for (t in tokens) {
            val num = t.toDoubleOrNull()
            if (num != null && num > 0.0) return num
        }

        return null
    }

    private fun isDateLine(line: String): Boolean {
        // Ex: "17/10/2026, 15:00", "01/10/2026 19:45", "29.09.2026 (19:45)"
        val clean = line.replace(",", " ").replace("(", " ").replace(")", " ")
        val parts = clean.split(" ").filter { it.isNotBlank() }
        val hasDate = parts.any { p ->
            (p.count { it == '/' || it == '.' || it == '-' } == 2) && p.any { it.isDigit() }
        }
        val hasTime = parts.any { p ->
            p.contains(":") && p.any { it.isDigit() }
        }
        return hasDate && hasTime
    }

    private fun isScoreMarkerLine(line: String): Boolean {
        val lower = line.lowercase()
        val idx = lower.indexOf("resultado")
        if (idx != -1) {
            val after = lower.substring(idx + "resultado".length).trim()
            if (after.any { it.isDigit() } && (after.contains("-") || after.contains(":") || after.contains("–"))) {
                return true
            }
        }
        return false
    }

    private fun isScoreLine(line: String): Boolean {
        // Ex: "2 - 4", "4 : 1", "0 : 2", "2-1"
        val sepList = listOf(" - ", " : ", " – ", "-", ":")
        for (sep in sepList) {
            if (line.contains(sep)) {
                val parts = line.split(sep)
                if (parts.size == 2) {
                    val p1 = parts[0].trim()
                    val p2 = parts[1].trim()
                    if (p1.isNotEmpty() && p2.isNotEmpty() && p1.all { it.isDigit() } && p2.all { it.isDigit() }) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun extractScoreInlineTeams(line: String): Pair<String, String>? {
        // Ex: "Dinamarca 2 - 4 Portugal" ou "Spain 4 : 1 Croatia" ou "Czech Republic 0 : 2 England"
        val sepList = listOf(" - ", " : ", " – ")
        for (sep in sepList) {
            if (line.contains(sep)) {
                val parts = line.split(sep)
                if (parts.size == 2) {
                    val left = parts[0].trim()
                    val right = parts[1].trim()

                    val leftTokens = left.split(" ")
                    val rightTokens = right.split(" ")

                    if (leftTokens.isNotEmpty() && rightTokens.isNotEmpty()) {
                        val score1Str = leftTokens.last()
                        val score2Str = rightTokens.first()
                        if (score1Str.all { it.isDigit() } && score2Str.all { it.isDigit() }) {
                            val home = leftTokens.dropLast(1).joinToString(" ")
                            val away = rightTokens.drop(1).joinToString(" ")
                            val cleanHome = cleanTeamName(home)
                            val cleanAway = cleanTeamName(away)
                            if (cleanHome.isNotBlank() && cleanAway.isNotBlank()) {
                                return Pair(cleanHome, cleanAway)
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    private fun parseInlineGames(lines: List<String>): List<GameInputState> {
        val games = mutableListOf<GameInputState>()

        for (i in lines.indices) {
            val line = lines[i]

            // Opção 1: Linha única com placar (ex: "Dinamarca 2 - 4 Portugal")
            var parsedTeams = extractScoreInlineTeams(line)
            var matchLineIdx = i

            // Opção 2: 3 linhas consecutivas (ex: "Dinamarca", "2 - 4", "Portugal")
            if (parsedTeams == null && i <= lines.size - 3) {
                if (isScoreLine(lines[i + 1])) {
                    val home = cleanTeamName(lines[i])
                    val away = cleanTeamName(lines[i + 2])
                    if (isValidTeam(home) && isValidTeam(away)) {
                        parsedTeams = Pair(home, away)
                        matchLineIdx = i + 1
                    }
                }
            }

            if (parsedTeams != null) {
                val (home, away) = parsedTeams
                if (isValidTeam(home) && isValidTeam(away)) {
                    var odd = ""
                    var modality = ""
                    var competition = ""

                    // 1. Procurar Odd e Modalidade nas linhas SEGUINTES
                    for (k in (matchLineIdx + 1)..minOf(lines.size - 1, matchLineIdx + 3)) {
                        val subLine = lines[k]
                        if (extractScoreInlineTeams(subLine) != null) break

                        if (odd.isEmpty() && isEligibleOddLine(subLine)) {
                            val candidateOdd = extractOdd(subLine)
                            if (candidateOdd != null) {
                                odd = candidateOdd
                            }
                        }
                        if (modality.isEmpty() && isModalityCandidate(subLine)) {
                            modality = cleanModalityName(subLine)
                        }
                    }

                    // 2. Procurar Competição e fallback para Odd/Modalidade nas linhas ANTERIORES
                    for (k in (matchLineIdx - 1) downTo maxOf(0, matchLineIdx - 4)) {
                        val subLine = lines[k]
                        if (extractScoreInlineTeams(subLine) != null) break

                        if (competition.isEmpty()) {
                            if (subLine.startsWith("Football. ", ignoreCase = true) ||
                                subLine.startsWith("Basketball. ", ignoreCase = true) ||
                                subLine.contains("League", ignoreCase = true) ||
                                subLine.contains("Liga", ignoreCase = true) ||
                                subLine.contains("Nations", ignoreCase = true) ||
                                subLine.contains("Taça", ignoreCase = true) ||
                                subLine.contains("Cup", ignoreCase = true) ||
                                subLine.contains("Serie", ignoreCase = true)
                            ) {
                                competition = subLine
                                    .replace("Football. ", "", ignoreCase = true)
                                    .replace("Basketball. ", "", ignoreCase = true)
                                    .replace("Tennis. ", "", ignoreCase = true)
                                    .trim()
                            }
                        }

                        if (odd.isEmpty() && isEligibleOddLine(subLine)) {
                            val candidateOdd = extractOdd(subLine)
                            if (candidateOdd != null) {
                                odd = candidateOdd
                            }
                        }
                        if (modality.isEmpty() && isModalityCandidate(subLine)) {
                            modality = cleanModalityName(subLine)
                        }
                    }

                    val key = "${home.normalizeKey()} vs ${away.normalizeKey()}"
                    if (games.none { "${it.homeTeam.normalizeKey()} vs ${it.awayTeam.normalizeKey()}" == key }) {
                        games.add(
                            GameInputState(
                                id = UUID.randomUUID().toString(),
                                homeTeam = home,
                                awayTeam = away,
                                competition = if (competition.isNotBlank()) competition else "Desconhecida",
                                modalityName = if (modality.isNotBlank()) modality else "1X2",
                                odd = odd
                            )
                        )
                    }
                }
            }
        }
        return games
    }

    private fun isEligibleOddLine(line: String): Boolean {
        val lower = line.lowercase().trim()
        if (lower.startsWith("mais de") || lower.startsWith("menos de") ||
            lower.startsWith("over ") || lower.startsWith("under ") ||
            lower.contains("golos") || lower.contains("vantagem")
        ) {
            return false
        }
        return !line.contains("€") &&
                !line.contains("eur", ignoreCase = true) &&
                !line.contains("cota", ignoreCase = true) &&
                !line.contains("odds:", ignoreCase = true) &&
                !line.contains("winnings", ignoreCase = true) &&
                !line.contains("ganho", ignoreCase = true) &&
                !line.contains("stake", ignoreCase = true) &&
                !line.contains("montante", ignoreCase = true)
    }

    private fun parseBetanoStyle(lines: List<String>): List<GameInputState> {
        val games = mutableListOf<GameInputState>()
        var prevEndMarkerIdx = -1

        for (i in lines.indices) {
            val line = lines[i]
            val isEndMarker = isDateLine(line) || isScoreMarkerLine(line)
            if (isEndMarker) {
                val windowStart = maxOf(0, prevEndMarkerIdx + 1)
                
                var awayCandidate = ""
                var homeCandidate = ""
                var homeIdx = -1

                // Procurar os 2 nomes de equipa válidos de baixo para cima dentro da janela do cartão
                for (k in (i - 1) downTo windowStart) {
                    val candidate = cleanTeamName(lines[k])
                    if (isValidTeam(candidate)) {
                        if (awayCandidate.isEmpty()) {
                            awayCandidate = candidate
                        } else if (homeCandidate.isEmpty() && !candidate.equals(awayCandidate, ignoreCase = true)) {
                            homeCandidate = candidate
                            homeIdx = k
                            break
                        }
                    }
                }

                if (homeCandidate.isNotEmpty() && awayCandidate.isNotEmpty()) {
                    var modality = "Resultado Final"
                    var odd = ""

                    // Procurar Odd e Modalidade nas linhas anteriores à equipa da casa dentro da janela
                    val modalityOddLimit = if (homeIdx != -1) homeIdx else i
                    for (k in (modalityOddLimit - 1) downTo windowStart) {
                        val subLine = lines[k]
                        val candidateOdd = extractOdd(subLine)
                        if (candidateOdd != null && odd.isEmpty()) {
                            odd = candidateOdd
                        } else if (isModalityCandidate(subLine)) {
                            val cleanedMod = cleanModalityName(subLine)
                            if (cleanedMod.isNotBlank()) {
                                if (modality == "Resultado Final" || modality.isEmpty()) {
                                    modality = cleanedMod
                                }
                            }
                        }
                    }

                    // Se a odd ainda não foi encontrada, procurar em qualquer linha da janela
                    if (odd.isEmpty()) {
                        for (k in windowStart until i) {
                            val candidateOdd = extractOdd(lines[k])
                            if (candidateOdd != null) {
                                odd = candidateOdd
                                break
                            }
                        }
                    }

                    // Se a modalidade é "Resultado Final", verificar se alguma linha da janela é candidata a modalidade
                    if (modality == "Resultado Final") {
                        for (k in windowStart until i) {
                            if (k != homeIdx && isModalityCandidate(lines[k])) {
                                val cleanedMod = cleanModalityName(lines[k])
                                if (cleanedMod.isNotBlank()) {
                                    modality = cleanedMod
                                    break
                                }
                            }
                        }
                    }

                    games.add(
                        GameInputState(
                            id = UUID.randomUUID().toString(),
                            homeTeam = homeCandidate,
                            awayTeam = awayCandidate,
                            competition = "Desconhecida",
                            modalityName = modality,
                            odd = odd
                        )
                    )
                }

                prevEndMarkerIdx = i
            }
        }
        return games
    }

    private fun parseClassicSeparators(lines: List<String>): List<GameInputState> {
        val games = mutableListOf<GameInputState>()
        val separators = listOf(" vs ", " v ", " - ", " x ")

        for (i in lines.indices) {
            val line = lines[i]
            var sepFound: String? = null
            for (sep in separators) {
                if (line.contains(sep, ignoreCase = true)) {
                    sepFound = sep
                    break
                }
            }
            if (sepFound != null) {
                val parts = line.split(sepFound)
                if (parts.size >= 2) {
                    val home = cleanTeamName(parts[0])
                    val away = cleanTeamName(parts[1])
                    if (isValidTeam(home) && isValidTeam(away)) {
                        var odd = ""
                        var modality = ""
                        var competition = ""

                        val start = maxOf(0, i - 3)
                        val end = minOf(lines.size - 1, i + 4)
                        for (k in start..end) {
                            if (k == i) continue
                            val sub = lines[k]
                            val candidateOdd = extractOdd(sub)
                            if (candidateOdd != null && odd.isEmpty()) {
                                odd = candidateOdd
                            } else if (competition.isEmpty() && (sub.contains("Liga", true) || sub.contains("League", true))) {
                                competition = sub.trim()
                            } else if (modality.isEmpty() && isModalityCandidate(sub)) {
                                modality = cleanModalityName(sub)
                            }
                        }

                        games.add(
                            GameInputState(
                                id = UUID.randomUUID().toString(),
                                homeTeam = home,
                                awayTeam = away,
                                competition = if (competition.isNotBlank()) competition else "Desconhecida",
                                modalityName = if (modality.isNotBlank()) modality else "1X2",
                                odd = odd
                            )
                        )
                    }
                }
            }
        }
        return games
    }

    private fun extractOdd(line: String): String? {
        val lower = line.lowercase().trim()
        if (lower.contains("€") || lower.contains("eur") || lower.contains("cota") ||
            lower.contains("odds:") || lower.contains("winnings") || lower.contains("ganho") ||
            lower.contains("stake") || lower.contains("montante") || lower.contains("saldo") ||
            lower.contains("deposito") || lower.contains("depósito")
        ) {
            return null
        }

        val isPureOverUnder = (lower.startsWith("mais de") || lower.startsWith("menos de") ||
                lower.startsWith("over") || lower.startsWith("under")) &&
                line.split(" ").filter { it.isNotBlank() }.size <= 3

        if (isPureOverUnder) return null

        val tokens = line.replace("€", " ").split(" ", "\t", "/", "|")
        for (i in (tokens.size - 1) downTo 0) {
            val t = tokens[i]
            val clean = t.trim().trim('(', ')', '[', ']', ',')
            if (clean.contains(".") || clean.contains(",")) {
                val num = clean.replace(',', '.').toDoubleOrNull()
                if (num != null && num in 1.01..100.0) {
                    return String.format(Locale.US, "%.2f", num)
                }
            }
        }
        return null
    }

    private fun cleanModalityName(line: String): String {
        var trimmed = line.trim()
        val tokens = trimmed.split(" ").filter { it.isNotBlank() }
        if (tokens.size >= 2) {
            val last = tokens.last().trim()
            val num = last.replace(',', '.').toDoubleOrNull()
            val isOverUnder = tokens.any { it.equals("mais", true) || it.equals("menos", true) || it.equals("over", true) || it.equals("under", true) }
            if (num != null && num in 1.01..100.0 && !isOverUnder && !last.startsWith("+") && !last.startsWith("-")) {
                trimmed = tokens.dropLast(1).joinToString(" ").trim()
            }
        }
        return trimmed
    }

    private fun isValidTeam(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.length < 2 || trimmed.length > 35) return false

        // Apenas números e pontuação
        if (trimmed.all { it.isDigit() || it.isWhitespace() || it in ".,-:'+’()/\\#" }) return false

        // Se tem mais dígitos que letras (ex: "4:1 (2:1,2:0)")
        val digitCount = trimmed.count { it.isDigit() }
        val letterCount = trimmed.count { Character.isLetter(it) }
        if (digitCount >= letterCount) return false

        // Minutos de golo (ex: "M. Damsgaard 23'")
        if (trimmed.endsWith("'") || trimmed.endsWith("’")) return false

        val lower = trimmed.lowercase()
        if (lower == "sim" || lower == "não" || lower == "nao") return false

        val badKeywords = listOf(
            "status", "paid out", "ganhos", "retorno", "cota", "aposta", "odds", "montante",
            "múltipla", "multipla", "resultado", "final", "total", "live", "directo", "direto",
            "golos", "vantagem", "tempo", "reg.", "vence", "empate", "ambas", "marcam", "dupla",
            "hipótese", "hipotese", "mais de", "menos de", "over", "under", "acumulador", "accumulator",
            "w1", "w2", "1x2", "winnings", "uefa", "league", "liga", "nations", "taça", "cup",
            "football", "basketball", "tennis", "championship", "tournament", "serie",
            "saldo", "depósito", "deposito", "seleção", "selecao", "seleções", "selecoes",
            "perdeu", "ganhou", "perdida", "ganha", "events", "completed", "number of"
        )
        if (badKeywords.any { lower.contains(it) }) return false
        return true
    }

    private fun isModalityCandidate(line: String): Boolean {
        val lower = line.lowercase()
        if (lower.startsWith("resultado") && (lower.contains("-") || lower.contains(":"))) return false
        return lower.contains("resultado final") ||
                lower.contains("golos") ||
                lower.contains("vantagem") ||
                lower.contains("ambas") ||
                lower.contains("hipótese") ||
                lower.contains("hipotese") ||
                lower.contains("1x2") ||
                lower.contains("over") ||
                lower.contains("under") ||
                lower.contains("mais de") ||
                lower.contains("menos de") ||
                lower.contains("vence") ||
                lower.startsWith("w1") ||
                lower.startsWith("w2")
    }

    private fun cleanTeamName(name: String): String {
        var cleaned = name.trim()

        // 1. Remover número inicial (ex: "1. FC Union Berlin" -> "FC Union Berlin")
        val parts = cleaned.split(" ")
        if (parts.size >= 2 && parts[0].trimEnd('.').all { it.isDigit() }) {
            cleaned = parts.drop(1).joinToString(" ").trim()
        }

        // 2. Filtrar caracteres válidos (letras de qualquer idioma, dígitos, espaços e pontuações aceitáveis)
        val sb = StringBuilder()
        for (ch in cleaned) {
            if (Character.isLetterOrDigit(ch) || ch.isWhitespace() || ch in "-.'/()&") {
                sb.append(ch)
            }
        }
        cleaned = sb.toString().trim()

        // 3. Remover eventuais pontuações das pontas
        return cleaned.trim { !Character.isLetterOrDigit(it) }
    }
}
