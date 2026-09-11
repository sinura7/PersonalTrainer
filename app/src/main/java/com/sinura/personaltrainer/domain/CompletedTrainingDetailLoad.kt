package com.sinura.personaltrainer.domain

/**
 * Load, missing, failed — the R18 step-three shape both completed-training
 * detail screens share. Two composables, two view models; one answer when
 * a read throws. Missing is a successful query with no row. Failed is a
 * query that threw. Those two must not be the same sentence.
 *
 * Retry lives on each view model: the activity path re-runs a one-shot
 * get, the strength path re-subscribes to the session flow.
 */
data class CompletedTrainingDetailLoad<T>(
    val isLoading: Boolean = true,
    val missing: Boolean = false,
    val failed: Boolean = false,
    val value: T? = null,
) {
    companion object {
        fun <T> loading(): CompletedTrainingDetailLoad<T> = CompletedTrainingDetailLoad()

        fun <T> missing(): CompletedTrainingDetailLoad<T> =
            CompletedTrainingDetailLoad(isLoading = false, missing = true)

        fun <T> failed(value: T? = null): CompletedTrainingDetailLoad<T> =
            CompletedTrainingDetailLoad(isLoading = false, failed = true, value = value)

        fun <T> ready(value: T): CompletedTrainingDetailLoad<T> =
            CompletedTrainingDetailLoad(isLoading = false, value = value)

        /**
         * [DataHealth] for a nullable row. A successful null is missing. A
         * throw before any value is failed. A later throw keeps the last
         * row and still marks failed, so Retry is offered.
         */
        fun <T> from(health: DataHealth<T?>): CompletedTrainingDetailLoad<T> {
            if (health is DataHealth.Unavailable) return failed()
            if (health is DataHealth.Degraded) return failed(value = health.lastValue)
            val value = (health as DataHealth.Available).value
            return if (value == null) missing() else ready(value)
        }

        fun <T> fromResult(result: Result<T?>): CompletedTrainingDetailLoad<T> =
            result.fold(
                onSuccess = { value -> if (value == null) missing() else ready(value) },
                onFailure = { failed() },
            )
    }
}
