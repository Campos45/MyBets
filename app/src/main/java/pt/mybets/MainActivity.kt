package pt.mybets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import pt.mybets.data.local.BetDatabase
import pt.mybets.data.repository.BetRepositoryImpl

import pt.mybets.presentation.navigation.AppNavigation
import pt.mybets.presentation.viewmodel.BetViewModel
import pt.mybets.presentation.theme.MyBetsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN isLive INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `name_aliases` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `category` TEXT NOT NULL,
                        `alias` TEXT NOT NULL,
                        `canonicalName` TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_name_aliases_category_alias` ON `name_aliases` (`category`, `alias`)")
            }
        }

        // 1. Instanciar a Base de Dados
        val database = Room.databaseBuilder(
            applicationContext,
            BetDatabase::class.java,
            "mybets_db"
        )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()

        // 2. Instanciar o Repositório
        val repository = BetRepositoryImpl(database.dao)

        // 3. Criar a Factory do ViewModel
        val viewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BetViewModel(repository) as T
            }
        }

        setContent {
            // Aplicar o tema escuro/verde neon (Betano Green Style)
            MyBetsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Obtemos a instância do ViewModel usando a nossa Factory
                    val viewModel: BetViewModel = viewModel(factory = viewModelFactory)

                    // Aqui vamos chamar o nosso sistema de navegação (Passo 2)
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
    }
}