package com.sinura.personaltrainer.domain

sealed interface PlanCreditResult {
    data object None : PlanCreditResult

    data class Credited(
        val occurrenceId: String,
        val dayTitle: String,
    ) : PlanCreditResult

    data class NeedsChoice(
        val occurrenceId: String,
        val dayTitle: String,
    ) : PlanCreditResult
}

object PlanCreditCopy {
    fun countPrompt(dayTitle: String): String = "Count this for today's $dayTitle?"
    const val COUNT = "Count"
    const val KEEP_SEPARATE = "Keep separate"
    const val CREDITED = "Counted for today's plan"
    const val UNDO = "Undo"
}

sealed interface PlanCreditUiState {
    data class Credited(
        val occurrenceId: String,
        val dayTitle: String,
    ) : PlanCreditUiState

    data class NeedsChoice(
        val occurrenceId: String,
        val dayTitle: String,
    ) : PlanCreditUiState
}
