/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.topsites

import mozilla.components.feature.top.sites.TopSite

/**
 * Whether this top site is stored as a pinned shortcut, and therefore keeps the position the user gives it.
 *
 * A [TopSite.Default] with no creation date is a backfill from the default top sites provider rather than a stored
 * shortcut, so like a [TopSite.Frecent] it can disappear or change position on its own.
 */
internal fun TopSite.isPinnedShortcut(): Boolean =
    this is TopSite.Pinned || (this is TopSite.Default && createdAt != null)

/**
 * Sorts the pinned shortcuts in the [order] chosen by the user, given as a list of URLs.
 *
 * Sponsored sites stay first and sites that are not pinned stay last. Pinned shortcuts missing from [order], such as
 * newly added ones, follow the ordered ones. When the user has not chosen an order the list is returned untouched.
 */
internal fun List<TopSite>.applyUserOrder(order: List<String>): List<TopSite> {
    if (order.isEmpty()) return this

    val unorderedPinned = filter { it.isPinnedShortcut() }.toMutableList()
    val orderedPinned = order.mapNotNull { url ->
        val index = unorderedPinned.indexOfFirst { it.url == url }
        if (index >= 0) unorderedPinned.removeAt(index) else null
    }

    return filter { it is TopSite.Provided } +
        orderedPinned +
        unorderedPinned +
        filter { it !is TopSite.Provided && !it.isPinnedShortcut() }
}

/**
 * The indices the top site at [draggedIndex] can be dropped on.
 *
 * Shortcuts can only be moved within the pinned block, because anything after it has no stable position. A site that is
 * not pinned yet gets pinned when it is moved, so it can also take the slot right after the block.
 */
internal fun List<TopSite>.reorderRange(draggedIndex: Int): IntRange {
    val dragged = getOrNull(draggedIndex)
    if (dragged == null || dragged is TopSite.Provided) return IntRange.EMPTY

    val first = count { it is TopSite.Provided }
    val otherPinned = withIndex().count { (index, site) -> index != draggedIndex && site.isPinnedShortcut() }
    return first..first + otherPinned
}

/** Returns a copy of this list with the item at [fromIndex] moved to [toIndex]. */
internal fun <T> List<T>.movedTo(fromIndex: Int, toIndex: Int): List<T> =
    toMutableList().apply { add(toIndex, removeAt(fromIndex)) }

/**
 * The URLs to save as the user's order after [moved] was moved: every pinned shortcut, plus [moved] itself as it is
 * about to be pinned if it was not already.
 */
internal fun List<TopSite>.userOrderAfterMoving(moved: TopSite): List<String> = filter {
    it.isPinnedShortcut() || it == moved
}
    .map { it.url }

/** Returns a copy of this order that follows a shortcut whose URL was edited from [oldUrl] to [newUrl]. */
internal fun List<String>.withUrlChanged(oldUrl: String, newUrl: String): List<String> {
    val index = indexOf(oldUrl)
    return if (index >= 0) toMutableList().apply { set(index, newUrl) } else this
}

/**
 * Unique and stable keys for showing this list in a lazy grid, which crashes on duplicate keys. Two pinned shortcuts
 * can share a URL, so repeated URLs are told apart by how many times they occurred.
 */
internal fun List<TopSite>.gridKeys(): List<String> {
    val occurrences = mutableMapOf<String, Int>()
    return map { site ->
        val occurrence = occurrences.getOrElse(site.url) { 0 }
        occurrences[site.url] = occurrence + 1
        if (occurrence == 0) site.url else "${site.url}#$occurrence"
    }
}
