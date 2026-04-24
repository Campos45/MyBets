# 📊 MyBets - O Meu Tracker de Apostas

**MyBets** é uma aplicação Android nativa e robusta, desenvolvida para proporcionar uma gestão profissional e analítica de bancas de apostas desportivas. Focada na performance, resiliência de dados e numa experiência de utilizador fluida, a aplicação permite aos apostadores abandonarem as folhas de cálculo e passarem a ter insights financeiros detalhados diretamente no telemóvel.

## 🚀 Funcionalidades Principais

* **Gestão de Apostas Inteligente:** Registo de apostas simples e múltiplas, com suporte nativo para distinção entre **Dinheiro Real** e **FreeBets** (garantindo cálculos de lucro líquido precisos).
* **Tratamento de Jogos Anulados (Void):** Lógica matemática avançada onde jogos adiados assumem Odd `1.0` sem invalidar o resto da aposta múltipla.
* **Dashboard Financeiro:** Visão global imediata com métricas de Lucro Total, Investimento Real, Win Rate e Lucro proveniente de FreeBets.
* **Gráfico de Evolução Nativo:** Visualização da evolução da banca através de um gráfico de linha desenhado nativamente com a `Canvas API` do Compose (sem dependência de bibliotecas externas pesadas).
* **Análise de Performance Detalhada (Tabs):**
  * Cálculo de **ROI (Retorno sobre o Investimento)** segmentado por faixas de Odds (ex: 1.0-1.5, 1.5-2.0, etc.).
  * Taxa de acerto por **Competição** e por **Modalidade**.
* **Ações Rápidas (Produtividade):** Botão para marcar todos os jogos de uma aposta múltipla como "Ganhos" com um só clique, com atualização reativa em tempo real.
* **Portabilidade e Resiliência (Backup JSON):** Sistema de Exportação e Importação de base de dados usando o *Storage Access Framework (SAF)* do Android. Transações atómicas garantem que não há perda de dados durante a importação.

## 🛠️ Stack Tecnológico & Arquitetura

O projeto foi construído seguindo rigorosamente os padrões de desenvolvimento recomendados pela Google para o ecossistema Android moderno:

* **Linguagem:** [Kotlin](https://kotlinlang.org/)
* **Interface Gráfica:** [Jetpack Compose](https://developer.android.com/jetpack/compose) com Material Design 3.
* **Arquitetura:** MVVM (Model-View-ViewModel) suportada por princípios de *Clean Architecture* e separação de responsabilidades.
* **Gestão de Estado & Concorrência:** `StateFlow` e `Kotlin Coroutines` (com delegação de cálculos pesados para `Dispatchers.Default`).
* **Armazenamento Local:** [Room Database](https://developer.android.com/training/data-storage/room) (SQLite) com relações complexas de 1-para-Muitos e Índices otimizados para evitar *Full Table Scans*.
* **Serialização de Dados:** [Gson](https://github.com/google/gson) para backups.

## 📸 Screenshots

*(Nota: Adiciona aqui imagens da aplicação real)*

<div style="display: flex; flex-direction: row;">
  <img src="link_para_imagem_da_home.png" width="250" alt="Home Screen">
  <img src="link_para_imagem_das_estatisticas.png" width="250" alt="Stats Screen">
  <img src="link_para_imagem_de_adicionar.png" width="250" alt="Add Bet Screen">
</div>

## ⚙️ Como Executar o Projeto

1. Faz o clone deste repositório:
   ```bash
   git clone [https://github.com/o-teu-user/mybets.git](https://github.com/o-teu-user/mybets.git)
