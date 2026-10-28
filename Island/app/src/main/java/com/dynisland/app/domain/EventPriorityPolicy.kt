package com.dynisland.app.domain

/**
 * Higher number = more important. Swappable later (e.g. user-configurable).
 * [interrupting] types may replace an island the user has expanded.
 */
class EventPriorityPolicy(
    private val priorities: Map<IslandEventType, Int>,
    private val interrupting: Set<IslandEventType>,
) {
    fun priorityOf(type: IslandEventType): Int = priorities[type] ?: 0
    fun interruptsExpanded(type: IslandEventType): Boolean = type in interrupting

    companion object {
        val Default = EventPriorityPolicy(
            priorities = mapOf(
                IslandEventType.Call to 100,
                IslandEventType.Timer to 90,
                IslandEventType.Music to 60,
                IslandEventType.Test to 50,
                IslandEventType.Charging to 40,
                IslandEventType.Bluetooth to 30,
                IslandEventType.Notification to 20,
            ),
            interrupting = setOf(IslandEventType.Call, IslandEventType.Timer),
        )
    }
}
