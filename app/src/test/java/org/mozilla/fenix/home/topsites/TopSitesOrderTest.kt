/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.topsites

import mozilla.components.feature.top.sites.TopSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TopSitesOrderTest {
    private val provided = TopSite.Provided(1, "ad", "https://ad", "", "", "", 1L)
    private val pinnedA = pinned("a")
    private val pinnedB = pinned("b")
    private val pinnedC = pinned("c")
    private val storedDefault = TopSite.Default(9, "d", "https://d", 1L)
    private val frecent = TopSite.Frecent(null, "f", "https://f", null)
    private val backfill = TopSite.Default(9, "g", "https://g", null)

    private fun pinned(name: String) = TopSite.Pinned(name.hashCode().toLong(), name, "https://$name", 1L)

    @Test
    fun `GIVEN no saved order THEN the list is untouched`() {
        val sites = listOf(frecent, pinnedA, provided)

        assertSame(sites, sites.applyUserOrder(emptyList()))
    }

    @Test
    fun `GIVEN a saved order THEN pinned shortcuts follow it between sponsored and other sites`() {
        val sites = listOf(provided, pinnedA, pinnedB, storedDefault, frecent, backfill)

        assertEquals(
            listOf(provided, storedDefault, pinnedB, pinnedA, frecent, backfill),
            sites.applyUserOrder(listOf("https://d", "https://b", "https://a")),
        )
    }

    @Test
    fun `GIVEN a saved order WHEN shortcuts were added or removed THEN new ones go last and old ones are ignored`() {
        val sites = listOf(pinnedA, pinnedB, pinnedC, frecent)

        assertEquals(
            listOf(pinnedC, pinnedA, pinnedB, frecent),
            sites.applyUserOrder(listOf("https://removed", "https://c", "https://a")),
        )
    }

    @Test
    fun `GIVEN a site saved in the order that is not pinned yet THEN it stays after the pinned shortcuts`() {
        val sites = listOf(pinnedA, pinnedB, frecent)

        assertEquals(sites, sites.applyUserOrder(listOf("https://f", "https://a", "https://b")))
    }

    @Test
    fun `GIVEN two shortcuts with the same URL THEN neither is lost`() {
        val duplicate = TopSite.Pinned(50, "copy", "https://a", 1L)
        val sites = listOf(pinnedA, pinnedB, duplicate)

        assertEquals(
            listOf(pinnedB, pinnedA, duplicate),
            sites.applyUserOrder(listOf("https://b", "https://a", "https://a")),
        )
    }

    @Test
    fun `GIVEN a pinned shortcut THEN it can only move within the pinned block`() {
        val sites = listOf(provided, pinnedA, pinnedB, pinnedC, frecent)

        assertEquals(1..3, sites.reorderRange(2))
    }

    @Test
    fun `GIVEN a site that is not pinned THEN it can also take the slot after the pinned block`() {
        val sites = listOf(provided, pinnedA, pinnedB, frecent, backfill)

        assertEquals(1..3, sites.reorderRange(3))
        assertEquals(1..3, sites.reorderRange(4))
    }

    @Test
    fun `GIVEN a sponsored site or an invalid index THEN nothing can move`() {
        val sites = listOf(provided, pinnedA)

        assertTrue(sites.reorderRange(0).isEmpty())
        assertTrue(sites.reorderRange(5).isEmpty())
    }

    @Test
    fun `WHEN moving an item THEN it lands on the requested index in both directions`() {
        val list = listOf(0, 1, 2, 3)

        assertEquals(listOf(1, 2, 0, 3), list.movedTo(0, 2))
        assertEquals(listOf(3, 0, 1, 2), list.movedTo(3, 0))
    }

    @Test
    fun `WHEN moving a site THEN the saved order has the pinned shortcuts and that site only`() {
        val sites = listOf(provided, frecent, pinnedA, backfill)

        assertEquals(listOf("https://f", "https://a"), sites.userOrderAfterMoving(frecent))
    }

    @Test
    fun `WHEN a shortcut URL is edited THEN it keeps its place in the order`() {
        assertEquals(listOf("x", "new", "z"), listOf("x", "old", "z").withUrlChanged("old", "new"))
        assertEquals(listOf("x"), listOf("x").withUrlChanged("old", "new"))
    }

    @Test
    fun `GIVEN repeated URLs THEN grid keys are still unique`() {
        val keys = listOf(pinnedA, TopSite.Pinned(50, "copy", "https://a", 1L), pinnedB).gridKeys()

        assertEquals(listOf("https://a", "https://a#1", "https://b"), keys)
    }
}
