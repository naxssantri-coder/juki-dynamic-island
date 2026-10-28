package com.dynisland.app.domain

enum class IslandMode { Hidden, Compact, Expanded }

/** [event] is kept while Hidden so the content does not vanish during the exit animation. */
data class IslandUiState(
    val mode: IslandMode = IslandMode.Hidden,
    val event: IslandEvent? = null,
)
