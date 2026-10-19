package pt.mybets.presentation

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import pt.mybets.presentation.util.ScreenshotParser
import pt.mybets.presentation.util.normalizeKey
import pt.mybets.presentation.viewmodel.BetViewModel
import pt.mybets.presentation.viewmodel.GameInputState
import kotlin.coroutines.resume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBetScreen(
    viewModel: BetViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var stake by remember { mutableStateOf("") }
    var isFreeBet by remember { mutableStateOf(false) }
    var games by remember { mutableStateOf(listOf(GameInputState())) }
    var isLoadingImage by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }

    // REQUISITO 6: Carregar rascunho de aposta copiada
    val copiedBetDraft by viewModel.copiedBetDraft.collectAsState()
    LaunchedEffect(copiedBetDraft) {
        copiedBetDraft?.let { draft ->
            stake = draft.stake
            isFreeBet = draft.isFreeBet
            if (draft.games.isNotEmpty()) {
                games = draft.games
            }
            viewModel.clearCopiedBetDraft()
        }
    }

    // REQUISITO 7: Processamento de screenshots (suporte para 1 ou múltiplos)
    val processSelectedUris: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) {
            isLoadingImage = true
            coroutineScope.launch {
                try {
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    val newGames = mutableListOf<GameInputState>()
                    var detectedStake: Double? = null

                    for (index in uris.indices) {
                        val uri = uris[index]
                        progressMessage = "A processar screenshot ${index + 1} de ${uris.size}..."
                        val text = recognizeTextFromUri(context, recognizer, uri)
                        if (text.isNotBlank()) {
                            val (parsedStake, parsedGames) = ScreenshotParser.parseTextToGameInputs(text)
                            if (parsedStake != null && detectedStake == null) {
                                detectedStake = parsedStake
                            }
                            for (pg in parsedGames) {
                                val key = "${pg.homeTeam.normalizeKey()} vs ${pg.awayTeam.normalizeKey()}"
                                if (newGames.none { "${it.homeTeam.normalizeKey()} vs ${it.awayTeam.normalizeKey()}" == key }) {
                                    newGames.add(pg)
                                }
                            }
                        }
                    }

                    if (detectedStake != null && stake.isBlank()) {
                        stake = detectedStake.toString()
                    }

                    if (newGames.isNotEmpty()) {
                        val isInitialEmpty = games.size == 1 && games[0].homeTeam.isBlank() && games[0].awayTeam.isBlank()
                        if (isInitialEmpty) {
                            games = newGames
                        } else {
                            val existingList = games.toMutableList()
                            for (ng in newGames) {
                                val key = "${ng.homeTeam.normalizeKey()} vs ${ng.awayTeam.normalizeKey()}"
                                if (existingList.none { "${it.homeTeam.normalizeKey()} vs ${it.awayTeam.normalizeKey()}" == key }) {
                                    existingList.add(ng)
                                }
                            }
                            games = existingList
                        }
                        Toast.makeText(context, "${newGames.size} jogos importados com sucesso!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Não foi possível detetar jogos nos screenshots.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AddBetScreen", "Erro ao processar imagem", e)
                    val errorDetail = "${e.javaClass.simpleName}: ${e.localizedMessage ?: "desconhecido"}"
                    Toast.makeText(context, "Erro ao processar imagem: $errorDetail", Toast.LENGTH_LONG).show()
                } finally {
                    isLoadingImage = false
                    progressMessage = ""
                }
            }
        }
    }

    // Seletor de Fotos Nativo do Android (Galeria/Screenshots)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> processSelectedUris(uris) }

    // Seletor de Ficheiros Múltiplos
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris -> processSelectedUris(uris) }

    // Seletor de Ficheiro Único (Compatibilidade universal)
    val singleFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { processSelectedUris(listOf(it)) } }

    val modalities by viewModel.modalities.collectAsState()
    val teamSuggestions by viewModel.teamSuggestions.collectAsState()
    val competitionSuggestions by viewModel.competitionSuggestions.collectAsState()

    val modalityNames = remember(modalities) { modalities.map { it.name } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nova Aposta") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val safeStakeStr = stake.replace(",", ".")
                    val stakeValue = safeStakeStr.toDoubleOrNull() ?: 0.0

                    val validGames = games.filter {
                        val safeOddStr = it.odd.replace(",", ".")
                        val safeOdd = safeOddStr.toDoubleOrNull()
                        it.homeTeam.isNotBlank() && it.awayTeam.isNotBlank() &&
                                safeOdd != null && safeOdd > 1.0 && it.modalityName.isNotBlank()
                    }

                    if (stakeValue > 0.0 && validGames.isNotEmpty()) {
                        val sanitizedGames = validGames.map { it.copy(odd = it.odd.replace(",", ".")) }
                        viewModel.saveBet(stakeValue, isFreeBet, sanitizedGames)
                        onNavigateBack()
                    } else {
                        Toast.makeText(context, "Preenche a stake e pelo menos um jogo completo com odd válida.", Toast.LENGTH_SHORT).show()
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        if (progressMessage.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(progressMessage, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                } catch (e: Exception) {
                                    try {
                                        filePickerLauncher.launch("image/*")
                                    } catch (e2: Exception) {
                                        singleFilePickerLauncher.launch("image/*")
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Galeria")
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    filePickerLauncher.launch("image/*")
                                } catch (e: Exception) {
                                    singleFilePickerLauncher.launch("image/*")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Ficheiros")
                        }
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
                    viewModel = viewModel,
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
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

private suspend fun recognizeTextFromUri(
    context: Context,
    recognizer: com.google.mlkit.vision.text.TextRecognizer,
    uri: Uri
): String = suspendCancellableCoroutine { continuation ->
    try {
        val image = try {
            InputImage.fromFilePath(context, uri)
        } catch (e: Exception) {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap != null) {
                InputImage.fromBitmap(bitmap, 0)
            } else {
                throw e
            }
        }
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                if (continuation.isActive) {
                    val orderedText = orderVisionTextSpatially(visionText)
                    android.util.Log.d("AddBetScreen", "Reordered OCR text:\n$orderedText")
                    continuation.resume(orderedText)
                }
            }
            .addOnFailureListener {
                if (continuation.isActive) continuation.resume("")
            }
    } catch (e: Exception) {
        if (continuation.isActive) continuation.resume("")
    }
}

/**
 * Reconstrói a ordem de leitura visual (de cima para baixo, da esquerda para a direita)
 * inspecionando os bounding boxes 2D de todas as linhas de texto detetadas pelo ML Kit.
 * Isso evita que o ML Kit separe colunas verticais (equipas à esquerda, odds/datas à direita)
 * em blocos desconexos.
 */
private fun orderVisionTextSpatially(visionText: com.google.mlkit.vision.text.Text): String {
    data class LineItem(
        val text: String,
        val top: Int,
        val bottom: Int,
        val left: Int,
        val right: Int,
        val centerY: Int = (top + bottom) / 2,
        val height: Int = maxOf(1, bottom - top)
    )

    val items = mutableListOf<LineItem>()
    for (block in visionText.textBlocks) {
        for (line in block.lines) {
            val box = line.boundingBox
            val text = line.text.trim()
            if (text.isNotEmpty()) {
                if (box != null) {
                    items.add(LineItem(text, box.top, box.bottom, box.left, box.right))
                } else {
                    items.add(LineItem(text, 0, 0, 0, 0))
                }
            }
        }
    }

    if (items.isEmpty()) {
        return visionText.text
    }

    // Calcular altura mediana das linhas para tolerância vertical dinâmica
    val heights = items.map { it.height }.sorted()
    val medianHeight = heights[heights.size / 2]
    val verticalTolerance = maxOf(8, (medianHeight * 0.55).toInt())

    // 1. Ordenar inicialmente por topo (Y)
    val sortedByTop = items.sortedBy { it.top }

    // 2. Agrupar em faixas horizontais (linhas visuais da imagem)
    val rows = mutableListOf<MutableList<LineItem>>()
    for (item in sortedByTop) {
        val matchingRow = rows.find { row ->
            val rowCenterY = row.map { it.centerY }.average()
            kotlin.math.abs(item.centerY - rowCenterY) <= verticalTolerance
        }

        if (matchingRow != null) {
            matchingRow.add(item)
        } else {
            rows.add(mutableListOf(item))
        }
    }

    // 3. Ordenar as linhas visuais de cima para baixo
    rows.sortBy { row -> row.map { it.centerY }.average() }

    // 4. Dentro de cada linha visual, ordenar da esquerda para a direita
    for (row in rows) {
        row.sortBy { it.left }
    }

    // 5. Concatenar o texto preservando a ordem de leitura humana
    val sb = StringBuilder()
    for (row in rows) {
        for (item in row) {
            sb.append(item.text).append("\n")
        }
    }

    return sb.toString()
}

@Composable
fun GameInputCard(
    game: GameInputState,
    modalities: List<String>,
    teamSuggestions: List<String>,
    competitionSuggestions: List<String>,
    viewModel: BetViewModel,
    onGameChange: (GameInputState) -> Unit,
    onDelete: () -> Unit
) {
    // REQUISITO 2: Listar primeiro as competições já associadas às equipas inseridas
    val dynamicCompetitions = remember(game.homeTeam, game.awayTeam, competitionSuggestions) {
        viewModel.getCompetitionsForTeams(game.homeTeam, game.awayTeam)
    }

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

            // EQUIPA VISITANTE (Linha inteira)
            AutocompleteTextField(
                value = game.awayTeam,
                onValueChange = { onGameChange(game.copy(awayTeam = it)) },
                label = "Equipa Visitante (ex: Benfica)",
                suggestions = teamSuggestions,
                modifier = Modifier.fillMaxWidth()
            )

            // COMPETIÇÃO (Linha inteira com priorização automática para as equipas acima)
            AutocompleteTextField(
                value = game.competition,
                onValueChange = { onGameChange(game.copy(competition = it)) },
                label = "Competição (ex: Primeira Liga)",
                suggestions = dynamicCompetitions,
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

// COMPONENTE GENÉRICO DE AUTOCOMPLETE COM FILTRAGEM SEM DIFERENÇA DE ACENTOS OU MAIÚSCULAS
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
        val normValue = value.normalizeKey()
        if (normValue.isEmpty()) {
            suggestions.take(15)
        } else {
            suggestions.filter {
                it.normalizeKey().contains(normValue) && it.normalizeKey() != normValue
            }.take(15)
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

        DropdownMenu(
            expanded = expanded && filteredSuggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
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