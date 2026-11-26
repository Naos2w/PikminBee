package com.naos.pikminbee.data

import com.naos.pikminbee.domain.WalkMode

/**
 * Read-only preflight for decoded, user-selected backup records.
 * No storage, permissions, active route, or draft state is changed here.
 * A later restore must revalidate against the current library under its write lock.
 */
internal object SavedDataRestorePreview {
    private const val MAX_ROUTES = 100

    enum class Blocker {
        DUPLICATE_EXISTING_ID,
        DUPLICATE_BACKUP_ID,
        INVALID_PLACE,
        INVALID_ROUTE,
        LEGACY_ROUTE_REVIEW,
        ID_CONFLICT,
        ROUTE_CAPACITY,
    }

    data class Counts(val new: Int, val identical: Int, val conflicting: Int)
    data class Result(
        val places: Counts,
        val routes: Counts,
        val blockers: Set<Blocker>,
    ) {
        val canProceed: Boolean get() = blockers.isEmpty()
    }

    fun inspect(
        existingPlaces: List<SavedPlace>,
        existingRoutes: List<SavedRoute>,
        backupPlaces: List<SavedPlace>,
        backupRoutes: List<SavedRoute>,
    ): Result {
        val blockers = mutableSetOf<Blocker>()
        if (hasDuplicateIds(existingPlaces, SavedPlace::id) ||
            hasDuplicateIds(existingRoutes, SavedRoute::id)) {
            blockers.add(Blocker.DUPLICATE_EXISTING_ID)
        }
        if (hasDuplicateIds(backupPlaces, SavedPlace::id) ||
            hasDuplicateIds(backupRoutes, SavedRoute::id)) {
            blockers.add(Blocker.DUPLICATE_BACKUP_ID)
        }
        if (backupPlaces.any { it.id <= 0 || it.createdAtMillis < 0 ||
                it.name.isBlank() || it.name.length > 80 }) {
            blockers.add(Blocker.INVALID_PLACE)
        }
        if (backupRoutes.any { it.createdAtMillis < 0 }) {
            blockers.add(Blocker.INVALID_ROUTE)
        }
        // Keep historical records intact, but do not restore retired modes as active features.
        if (backupRoutes.any { it.mode != WalkMode.ROUTE }) {
            blockers.add(Blocker.LEGACY_ROUTE_REVIEW)
        }
        val places = compare(existingPlaces, backupPlaces, SavedPlace::id)
        val routes = compare(existingRoutes, backupRoutes, SavedRoute::id)
        if (places.conflicting > 0 || routes.conflicting > 0) {
            blockers.add(Blocker.ID_CONFLICT)
        }
        if (existingRoutes.size.toLong() + routes.new > MAX_ROUTES) {
            blockers.add(Blocker.ROUTE_CAPACITY)
        }
        return Result(places, routes, blockers.toSet())
    }

    private fun <T, K> hasDuplicateIds(items: List<T>, id: (T) -> K): Boolean =
        items.map(id).toSet().size != items.size

    private fun <T, K> compare(existing: List<T>, incoming: List<T>, id: (T) -> K): Counts {
        val byId = existing.associateBy(id)
        var new = 0
        var identical = 0
        var conflicting = 0
        for (item in incoming) {
            val old = byId[id(item)]
            when {
                old == null -> new++
                old == item -> identical++
                else -> conflicting++
            }
        }
        return Counts(new, identical, conflicting)
    }
}
