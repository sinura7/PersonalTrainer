package com.sinura.personaltrainer.ui.preview

/**
 * The state vocabulary every screen preview and golden may draw from.
 *
 * These are deliberately screen-neutral. A page maps a fixture to its own
 * UiState rather than teaching production ViewModels about preview data.
 */
enum class PreviewStateKind {
    LOADING,
    EMPTY,
    POPULATED,
    ERROR,
    LONG_IDENTITY,
    LARGE_METRICS,
    ACTIVE,
    RESTING,
    PERMISSION_DENIED,
}

data class FoundationPreviewFixture(
    val kind: PreviewStateKind,
    val title: String,
    val body: String,
    val primaryMetric: String,
    val secondaryMetric: String,
    val actionLabel: String?,
)

object PreviewFixtures {
    val loading = FoundationPreviewFixture(
        kind = PreviewStateKind.LOADING,
        title = "Reading local training",
        body = "This state is delayed in production so fast Room reads never flash a spinner.",
        primaryMetric = "—",
        secondaryMetric = "—",
        actionLabel = null,
    )

    val empty = FoundationPreviewFixture(
        kind = PreviewStateKind.EMPTY,
        title = "Nothing recorded yet",
        body = "Start the first activity. The local record remains available without an account.",
        primaryMetric = "0",
        secondaryMetric = "0 min",
        actionLabel = "Start activity",
    )

    val populated = FoundationPreviewFixture(
        kind = PreviewStateKind.POPULATED,
        title = "Upper strength",
        body = "Bench press · Chest-supported row · Cable fly",
        primaryMetric = "12",
        secondaryMetric = "48 min",
        actionLabel = "Open activity",
    )

    val error = FoundationPreviewFixture(
        kind = PreviewStateKind.ERROR,
        title = "Training data unavailable",
        body = "Temper could not read the local record. Retry without treating this as an empty history.",
        primaryMetric = "—",
        secondaryMetric = "—",
        actionLabel = "Retry",
    )

    val longIdentity = FoundationPreviewFixture(
        kind = PreviewStateKind.LONG_IDENTITY,
        title = "Saturday posterior-chain strength and conditioning",
        body = "Romanian deadlift with controlled eccentric · 24 August 2026",
        primaryMetric = "18",
        secondaryMetric = "1 h 42 min",
        actionLabel = "Open activity",
    )

    val largeMetrics = FoundationPreviewFixture(
        kind = PreviewStateKind.LARGE_METRICS,
        title = "Annual training total",
        body = "Large values must not erase the identity that explains them.",
        primaryMetric = "123,456",
        secondaryMetric = "9,999 min",
        actionLabel = "Inspect year",
    )

    val active = FoundationPreviewFixture(
        kind = PreviewStateKind.ACTIVE,
        title = "Upper strength in progress",
        body = "Set 3 of 12 · Bench press",
        primaryMetric = "00:28",
        secondaryMetric = "3 sets",
        actionLabel = "Resume",
    )

    val resting = FoundationPreviewFixture(
        kind = PreviewStateKind.RESTING,
        title = "Resting",
        body = "Next: Bench press · 82.5 kg × 8",
        primaryMetric = "01:30",
        secondaryMetric = "exact",
        actionLabel = "Skip rest",
    )

    val permissionDenied = FoundationPreviewFixture(
        kind = PreviewStateKind.PERMISSION_DENIED,
        title = "Rest alerts are off",
        body = "Keep logging. A compact recovery action must not cover the lift or Log button.",
        primaryMetric = "01:30",
        secondaryMetric = "in app",
        actionLabel = "Review alerts",
    )

    val all: List<FoundationPreviewFixture> = listOf(
        loading,
        empty,
        populated,
        error,
        longIdentity,
        largeMetrics,
        active,
        resting,
        permissionDenied,
    )
}
