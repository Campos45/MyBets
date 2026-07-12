package pt.mybets.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pt.mybets.presentation.viewmodel.BetViewModel
import pt.mybets.presentation.viewmodel.GameInputState
import androidx.compose.ui.window.PopupProperties
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import pt.mybets.presentation.util.ScreenshotParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBetScreen(
    viewModel: BetViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var stake by remember { mutableStateOf("") }
    var isFreeBet by remember { mutableStateOf(false) }
    var games by remember { mutableStateOf(listOf(GameInputState())) }
    var isLoadingImage by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isLoadingImage = true
            try {
                val image = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        isLoadingImage = false
                        val (parsedStake, parsedGames) = ScreenshotParser.parseTextToGameInputs(visionText.text)
                        if (parsedStake != null) {
                            stake = parsedStake.toString()
                        }
                        if (parsedGames.isNotEmpty()) {
                            games = parsedGames
                        }
                    }
                    .addOnFailureListener {
                        isLoadingImage = false
                    }
            } catch (e: Exception) {
                isLoadingImage = false
            }
        }
    }

    val modalities by viewModel.modalities.collectAsState()
    val teamSuggestions by viewModel.teamSuggestions.collectAsState()
    val competitionSuggestions by viewModel.competitionSuggestions.collectAsState()

    val modalityNames = remember(modalities) { modalities.map { it.name } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nova Aposta") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Voltar") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    // CORREÇÃO: Substitui vírgula por ponto para evitar nulls na Europa
                    val safeStakeStr = stake.replace(",", ".")
                    val stakeValue = safeStakeStr.toDoubleOrNull() ?: 0.0

                    val validGames = games.filter {
                        val safeOddStr = it.odd.replace(",", ".")
                        val safeOdd = safeOddStr.toDoubleOrNull()

                        // VALIDAÇÃO: Tem de ter dados e a odd tem de ser realista (> 1.0)
                        it.homeTeam.isNotBlank() && it.awayTeam.isNotBlank() &&
                                safeOdd != null && safeOdd > 1.0 && it.modalityName.isNotBlank()
                    }

                    // VALIDAÇÃO: Stake > 0 e tem de haver jogos válidos
                    if (stakeValue > 0.0 && validGames.isNotEmpty()) {
                        // Garante que a odd guardada vai higienizada (com ponto)
                        val sanitizedGames = validGames.map { it.copy(odd = it.odd.replace(",", ".")) }
                        viewModel.saveBet(stakeValue, isFreeBet, sanitizedGames)
                        onNavigateBack()
                    }
                },
                icon = { Icon(Icons.Default.Add, "Guardar") },
                text = { Text("Guardar Aposta") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                if (isLoadingImage) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text("Importar do Screenshot")
                    }
                }

                OutlinedTextField(
                    value = stake,
                    onValueChange = { stake = it },
                    label = { Text("Stake (Valor)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Aposta grátis (Free Bet)?", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = isFreeBet, onCheckedChange = { isFreeBet = it })
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            itemsIndexed(games, key = { _, game -> game.id }) { index, game ->
                GameInputCard(
                    game = game,
                    modalities = modalityNames,
                    teamSuggestions = teamSuggestions,
                    competitionSuggestions = competitionSuggestions,
                    onGameChange = { updatedGame ->
                        val newList = games.toMutableList()
                        newList[index] = updatedGame
                        games = newList
                    },
                    onDelete = {
                        if (games.size > 1) {
                            val newList = games.toMutableList()
                            newList.removeAt(index)
                            games = newList
                        }
                    }
                )
            }

            item {
                OutlinedButton(
                    onClick = { games = games + GameInputState() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Adicionar Jogo")
                }
                Spacer(Modifier.height(80.dp)) // Espaço para não ficar por baixo do botão flutuante
            }
        }
    }
}

@Composable
fun GameInputCard(
    game: GameInputState,
    modalities: List<String>,
    teamSuggestions: List<String>,
    competitionSuggestions: List<String>,
    onGameChange: (GameInputState) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Detalhes do Jogo", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Remover", tint = MaterialTheme.colorScheme.error)
                }
            }

            // EQUIPA DA CASA (Linha inteira)
            AutocompleteTextField(
                value = game.homeTeam,
                onValueChange = { onGameChange(game.copy(homeTeam = it)) },
                label = "Equipa da Casa (ex: Sporting)",
                suggestions = teamSuggestions,
                modifier = Modifier.fillMaxWidth()
            )

            // EQUIPA VISITANTE (Linha inteira, debaixo da casa)
            AutocompleteTextField(
                value = game.awayTeam,
                onValueChange = { onGameChange(game.copy(awayTeam = it)) },
                label = "Equipa Visitante (ex: Portugal)",
                suggestions = teamSuggestions,
                modifier = Modifier.fillMaxWidth()
            )

            // COMPETIÇÃO (Linha inteira)
            AutocompleteTextField(
                value = game.competition,
                onValueChange = { onGameChange(game.copy(competition = it)) },
                label = "Competição (ex: Primeira Liga)",
                suggestions = competitionSuggestions,
                modifier = Modifier.fillMaxWidth()
            )

            // MODALIDADE (Linha inteira)
            AutocompleteTextField(
                value = game.modalityName,
                onValueChange = { onGameChange(game.copy(modalityName = it)) },
                label = "Modalidade (ex: Over 2.5)",
                suggestions = modalities,
                modifier = Modifier.fillMaxWidth()
            )

            // ODD (Linha inteira)
            OutlinedTextField(
                value = game.odd,
                onValueChange = { onGameChange(game.copy(odd = it)) },
                label = { Text("Odd do Jogo (ex: 1.85)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Aposta em Live?", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = game.isLive,
                    onCheckedChange = { onGameChange(game.copy(isLive = it)) }
                )
            }
        }
    }
}



// COMPONENTE GENÉRICO DE AUTOCOMPLETE (À prova de bala, sem roubo de foco)
@Composable
fun AutocompleteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val filteredSuggestions = remember(value, suggestions) {
        suggestions.filter {
            it.contains(value, ignoreCase = true) && !it.equals(value, ignoreCase = true)
        }
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Usamos um DropdownMenu clássico e seguro com altura limitada e scroll vertical
        DropdownMenu(
            expanded = expanded && filteredSuggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
            // A MAGIA ESTÁ AQUI: Impede que a lista roube o foco do teclado!
            properties = PopupProperties(focusable = false),
            modifier = Modifier.heightIn(max = 200.dp)
        ) {
            filteredSuggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    }
                )
            }
        }
    }
}