package net.loeu.wallybudget.domain.model

data class HistoryState(
    val monthlyHistory: List<MonthlyHistory>,
    val historySections: List<ExpenseCycleSection>,
    val bucketNameByUuid: Map<String, String> = emptyMap()
)
