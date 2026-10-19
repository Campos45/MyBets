package pt.mybets

import org.junit.Test
import org.junit.Assert.*
import pt.mybets.presentation.util.ScreenshotParser
import pt.mybets.presentation.util.normalizeKey

class ExampleUnitTest {
    @Test
    fun testScreenshotParser() {
        val sampleText = """
            21:00
            Múltipla 12 seleções 1,38 €
            Perdeu
            Atlético de Madrid ou Empate
            1.18
            Hipótese Dupla
            Espanyol
            Atlético de Madrid
            Resultado 0-1
            Manchester City
            1.24
            Resultado Final
            Manchester City
            Ipswich Town
            17/10/2026, 15:00
            Paris Saint-Germain
            1.40
            Resultado Final
            Estrasburgo
            Paris Saint-Germain
            17/10/2026, 16:15
            Inter de Milão
            1.60
            Resultado Final
            Bolonha
            Inter de Milão
            17/10/2026, 17:00
            Sim
            1.36
            Ambas as Equipas Marcam
            Bétis
            Barcelona
            17/10/2026, 17:30
            Feyenoord
            1.33
            Resultado Final
            SC Telstar
            Feyenoord
            17/10/2026, 19:00
            Valor da Aposta 1,38 €
        """.trimIndent()

        val (stake, games) = ScreenshotParser.parseTextToGameInputs(sampleText)
        println("Stake: $stake")
        println("Games found: ${games.size}")
        games.forEach { g ->
            println("${g.homeTeam} vs ${g.awayTeam} | ${g.modalityName} | ${g.odd}")
        }
        assertEquals(1.38, stake ?: 0.0, 0.001)
        assertEquals(6, games.size)
        assertEquals("Espanyol", games[0].homeTeam)
        assertEquals("Atlético de Madrid", games[0].awayTeam)
        assertEquals("X2", games[0].modalityName)
        assertEquals("1.18", games[0].odd)

        assertEquals("Manchester City", games[1].homeTeam)
        assertEquals("Ipswich Town", games[1].awayTeam)
        assertEquals("1", games[1].modalityName)
        assertEquals("1.24", games[1].odd)

        assertEquals("Estrasburgo", games[2].homeTeam)
        assertEquals("Paris Saint-Germain", games[2].awayTeam)
        assertEquals("2", games[2].modalityName)
        assertEquals("1.40", games[2].odd)

        assertEquals("Bolonha", games[3].homeTeam)
        assertEquals("Inter de Milão", games[3].awayTeam)
        assertEquals("2", games[3].modalityName)
        assertEquals("1.60", games[3].odd)

        assertEquals("Bétis", games[4].homeTeam)
        assertEquals("Barcelona", games[4].awayTeam)
        assertEquals("Ambas as Equipas Marcam (Sim)", games[4].modalityName)
        assertEquals("1.36", games[4].odd)

        assertEquals("SC Telstar", games[5].homeTeam)
        assertEquals("Feyenoord", games[5].awayTeam)
        assertEquals("2", games[5].modalityName)
        assertEquals("1.33", games[5].odd)
    }

    @Test
    fun testScreenshotParserSecondImage() {
        val sampleText2 = """
            21:00
            Feyenoord
            17/10/2026, 19:00
            Lille ou Empate
            1.23
            Hipótese Dupla
            Lille
            Brest
            17/10/2026, 19:45
            AS Roma
            1.50
            Resultado Final
            AS Roma
            Génova
            17/10/2026, 19:45
            Mais de 2.5
            1.57
            Total de Golos Mais/Menos
            Everton
            Chelsea
            Resultado 0-2
            Nápoles ou Empate
            1.19
            Hipótese Dupla
            Venezia
            Nápoles
            Resultado 1-0
            Borussia Dortmund
            1.53
            +2 Golos Vantagem
            1. FC Union Berlin
            Borussia Dortmund
            17/10/2026, 14:30
            PSV Eindhoven
            1.29
            Resultado Final
            Den Haag
            PSV Eindhoven
            17/10/2026, 15:30
            Sobre liquidação de apostas
            Valor da Aposta 1,38 €
        """.trimIndent()

        val (stake, games) = ScreenshotParser.parseTextToGameInputs(sampleText2)
        println("Sample 2 - Stake: $stake")
        println("Sample 2 - Games found: ${games.size}")
        games.forEach { g ->
            println("${g.homeTeam} vs ${g.awayTeam} | ${g.modalityName} | ${g.odd}")
        }
        assertEquals(1.38, stake ?: 0.0, 0.001)
        assertEquals(6, games.size)

        assertEquals("Lille", games[0].homeTeam)
        assertEquals("Brest", games[0].awayTeam)
        assertEquals("1X", games[0].modalityName)
        assertEquals("1.23", games[0].odd)

        assertEquals("AS Roma", games[1].homeTeam)
        assertEquals("Génova", games[1].awayTeam)
        assertEquals("1", games[1].modalityName)
        assertEquals("1.50", games[1].odd)

        assertEquals("Everton", games[2].homeTeam)
        assertEquals("Chelsea", games[2].awayTeam)
        assertEquals("Mais de 2.5", games[2].modalityName)
        assertEquals("1.57", games[2].odd)

        assertEquals("Venezia", games[3].homeTeam)
        assertEquals("Nápoles", games[3].awayTeam)
        assertEquals("X2", games[3].modalityName)
        assertEquals("1.19", games[3].odd)

        assertEquals("FC Union Berlin", games[4].homeTeam)
        assertEquals("Borussia Dortmund", games[4].awayTeam)
        assertEquals("+2 Golos Vantagem (2)", games[4].modalityName)
        assertEquals("1.53", games[4].odd)

        assertEquals("Den Haag", games[5].homeTeam)
        assertEquals("PSV Eindhoven", games[5].awayTeam)
        assertEquals("2", games[5].modalityName)
        assertEquals("1.29", games[5].odd)
    }

    @Test
    fun testBetanoMergedLines() {
        // Simular o caso em que o ML Kit agrupa elementos na mesma linha horizontal
        val mergedText = """
            21:00
            Múltipla 12 seleções 1,38 €
            Perdeu
            Atlético de Madrid ou Empate 1.18
            Hipótese Dupla
            Espanyol
            Atlético de Madrid Resultado 0-1
            Manchester City 1.24
            Resultado Final
            Manchester City
            Ipswich Town 17/10/2026, 15:00
            Paris Saint-Germain 1.40
            Resultado Final
            Estrasburgo
            Paris Saint-Germain 17/10/2026, 16:15
            Valor da Aposta 1,38 €
        """.trimIndent()

        val (stake, games) = ScreenshotParser.parseTextToGameInputs(mergedText)
        assertEquals(1.38, stake ?: 0.0, 0.001)
        assertEquals(3, games.size)

        assertEquals("Espanyol", games[0].homeTeam)
        assertEquals("Atlético de Madrid", games[0].awayTeam)
        assertEquals("X2", games[0].modalityName)
        assertEquals("1.18", games[0].odd)

        assertEquals("Manchester City", games[1].homeTeam)
        assertEquals("Ipswich Town", games[1].awayTeam)
        assertEquals("1", games[1].modalityName)
        assertEquals("1.24", games[1].odd)

        assertEquals("Estrasburgo", games[2].homeTeam)
        assertEquals("Paris Saint-Germain", games[2].awayTeam)
        assertEquals("2", games[2].modalityName)
        assertEquals("1.40", games[2].odd)
    }

    @Test
    fun testNormalizeKey() {
        assertEquals("atletico de madrid", "Atlético de Madrid".normalizeKey())
        assertEquals("city", "Cíty".normalizeKey())
        assertEquals("sporting", "SPORTING".normalizeKey())
        assertEquals("sao paulo", "São Paulo".normalizeKey())
        assertEquals("munchen", "München".normalizeKey())
    }

    @Test
    fun testPlacardScreenshot() {
        val placardText = """
            19:32 10/10/26 12:40
            Múltipla (3)
            Perdida
            Portugal
            Equipa tem 2 golos de vantagem ou vence
            (tempo reg.)
            1,90
            01/10/2026 19:45
            Dinamarca 2 - 4 Portugal
            M. Damsgaard 23'
            J. Cancelo 13'
            R. Hojlund 53'
            G. Ramos 25'
            Vitinha F. 67'
            J. Félix 87'
            Noruega
            Equipa tem 2 golos de vantagem ou vence
            (tempo reg.)
            1,34
            01/10/2026 19:45
            País de Gales 2 - 1 Noruega
            D. James 38'
            O. Bobb 11'
            N. Williams 48'
            Países Baixos
            Equipa tem 2 golos de vantagem ou vence
            (tempo reg.)
            2,05
            01/10/2026 19:45
            Grécia 2 - 2 Países Baixos
            F. Ioannidis 23'
            V. van Dijk 59'
            G. Masouras 45 +2'
            T. Reijnders 89'
            Cota 5,22
            Montante 1 €
            Ganhos 0 €
        """.trimIndent()

        val (stake, games) = ScreenshotParser.parseTextToGameInputs(placardText)
        println("Placard - Stake: $stake")
        println("Placard - Games found: ${games.size}")
        games.forEach { g ->
            println("${g.homeTeam} vs ${g.awayTeam} | ${g.modalityName} | ${g.odd}")
        }
        assertEquals(1.0, stake ?: 0.0, 0.001)
        assertEquals(3, games.size)
        assertTrue(games.any { it.homeTeam.contains("Dinamarca") && it.awayTeam.contains("Portugal") && it.odd == "1.90" })
        assertTrue(games.any { it.homeTeam.contains("Gales") && it.awayTeam.contains("Noruega") && it.odd == "1.34" })
        assertTrue(games.any { it.homeTeam.contains("Grécia") && it.awayTeam.contains("Baixos") && it.odd == "2.05" })
    }

    @Test
    fun testPlacardThreeLineScoreFormat() {
        val placardThreeLines = """
            Portugal
            Equipa tem 2 golos de vantagem ou vence
            1,90
            01/10/2026 19:45
            Dinamarca
            2 - 4
            Portugal
            Montante 1 €
        """.trimIndent()

        val (stake, games) = ScreenshotParser.parseTextToGameInputs(placardThreeLines)
        assertEquals(1.0, stake ?: 0.0, 0.001)
        assertEquals(1, games.size)
        assertEquals("Dinamarca", games[0].homeTeam)
        assertEquals("Portugal", games[0].awayTeam)
        assertEquals("1.90", games[0].odd)
    }

    @Test
    fun test1xBetScreenshot() {
        val oneXBetText = """
            19:32
            Bet details
            29.09.2026 (18:42)
            Accumulator № 88046228935
            Number of events: 2 2 of 2 completed
            Odds: 1.561
            Stake: 1.88 €
            Winnings: 2.94 €
            Status: Paid out
            Football. UEFA Nations League
            29.09.2026 (19:45)
            Spain 4 : 1 Croatia
            4:1 (2:1,2:0)
            1X2. W1 1.225
            Status: Win
            Football. UEFA Nations League
            29.09.2026 (19:45)
            Czech Republic 0 : 2 England
            0:2 (0:0,0:2)
            1X2. W2 1.275
            Status: Win
            Popular Favorites Bet slip History Menu
        """.trimIndent()

        val (stake, games) = ScreenshotParser.parseTextToGameInputs(oneXBetText)
        println("1xBet - Stake: $stake")
        println("1xBet - Games found: ${games.size}")
        games.forEach { g ->
            println("${g.homeTeam} vs ${g.awayTeam} | ${g.modalityName} | ${g.odd}")
        }
        assertEquals(1.88, stake ?: 0.0, 0.001)
        assertEquals(2, games.size)
        assertTrue(games.any { it.homeTeam.contains("Spain") && it.awayTeam.contains("Croatia") })
        assertTrue(games.any { it.homeTeam.contains("Czech") && it.awayTeam.contains("England") })
    }
}