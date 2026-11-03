/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.topsites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.compose.base.theme.Theme
import mozilla.components.feature.top.sites.TopSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.theme.FirefoxTheme

@RunWith(AndroidJUnit4::class)
class TopSitesReorderTest {
    @get:Rule val composeTestRule = createComposeRule()

    private var fingerPosition = Offset.Zero
    private val moves = mutableListOf<Pair<TopSite, Int>>()
    private val clicks = mutableListOf<TopSite>()
    private val longClicks = mutableListOf<TopSite>()

    private val pinnedA = pinned("Alpha")
    private val pinnedB = pinned("Bravo")
    private val pinnedC = pinned("Charlie")
    private val frecentD = TopSite.Frecent(id = null, title = "Delta", url = "https://delta", createdAt = null)
    private val frecentE = TopSite.Frecent(id = null, title = "Echo", url = "https://echo", createdAt = null)
    private val provided =
        TopSite.Provided(
            id = 1L,
            title = "Sponsor",
            url = "https://sponsor",
            clickUrl = "",
            imageUrl = "",
            impressionUrl = "",
            createdAt = 1L,
        )

    @Test
    fun `WHEN a pinned shortcut is long pressed and dragged onto another THEN it moves to that index`() {
        setContent(listOf(pinnedA, pinnedB, pinnedC, frecentD))

        drag(from = "Alpha", to = "Charlie")

        assertEquals(listOf(pinnedA to 2), moves)
    }

    @Test
    fun `GIVEN a right to left layout WHEN a pinned shortcut is dragged onto another THEN it moves to that index`() {
        setContent(listOf(pinnedA, pinnedB, pinnedC, frecentD), layoutDirection = LayoutDirection.Rtl)

        drag(from = "Alpha", to = "Charlie")

        assertEquals(listOf(pinnedA to 2), moves)
    }

    @Test
    fun `WHEN a shortcut starts being dragged THEN its menu closes`() {
        setContent(listOf(pinnedA, pinnedB, pinnedC))

        longPress("Alpha")
        composeTestRule.onNodeWithTag(TopSitesTestTag.TOP_SITE_CONTEXTUAL_MENU).assertExists()

        moveFinger(to = center("Bravo"))
        composeTestRule.onNodeWithTag(TopSitesTestTag.TOP_SITE_CONTEXTUAL_MENU).assertDoesNotExist()

        lift()
    }

    @Test
    fun `WHEN a shortcut is long pressed and released without moving THEN it does not move and the menu stays`() {
        setContent(listOf(pinnedA, pinnedB))

        longPress("Alpha")
        lift()

        assertEquals(listOf(pinnedA), longClicks)
        assertTrue(moves.isEmpty())
        composeTestRule.onNodeWithTag(TopSitesTestTag.TOP_SITE_CONTEXTUAL_MENU).assertExists()
    }

    @Test
    fun `WHEN a shortcut is tapped THEN it opens and does not move`() {
        setContent(listOf(pinnedA, pinnedB))

        composeTestRule.onNodeWithText("Alpha").performClick()

        assertEquals(listOf(pinnedA), clicks)
        assertTrue(moves.isEmpty())
    }

    @Test
    fun `WHEN the finger moves before the long press THEN nothing is dragged`() {
        setContent(listOf(pinnedA, pinnedB, pinnedC))

        val from = center("Alpha")
        pressAt(from)
        moveFinger(to = center("Charlie"))
        composeTestRule.mainClock.advanceTimeBy(LONG_PRESS_MILLIS)
        lift()

        assertTrue(moves.isEmpty())
    }

    @Test
    fun `WHEN a frecent shortcut is dragged onto a pinned one THEN it moves there`() {
        setContent(listOf(pinnedA, pinnedB, frecentD, frecentE))

        drag(from = "Echo", to = "Alpha")

        assertEquals(listOf(frecentE to 0), moves)
    }

    @Test
    fun `WHEN a pinned shortcut is dragged past the pinned block THEN it stops at the end of the block`() {
        setContent(listOf(pinnedA, pinnedB, frecentD, frecentE))

        drag(from = "Alpha", to = "Echo")

        assertEquals(listOf(pinnedA to 1), moves)
    }

    @Test
    fun `WHEN a sponsored shortcut is dragged THEN nothing moves`() {
        setContent(listOf(provided, pinnedA, pinnedB))

        drag(from = "Sponsor", to = "Bravo")

        assertTrue(moves.isEmpty())
    }

    @Test
    fun `WHEN a shortcut is dragged onto the add shortcut tile THEN it does not go past it`() {
        setContent(listOf(pinnedA, pinnedB))

        val target =
            composeTestRule.onNodeWithTag(TopSitesTestTag.ADD_SHORTCUT_ROOT).fetchSemanticsNode().boundsInRoot.center
        longPress("Alpha")
        moveFinger(to = target)
        lift()

        assertEquals(listOf(pinnedA to 1), moves)
    }

    @Test
    fun `GIVEN a pinned shortcut THEN accessibility actions move it inside the pinned block`() {
        setContent(listOf(pinnedA, pinnedB, pinnedC, frecentD))

        assertEquals(
            listOf("Move shortcut left", "Move shortcut right"),
            reorderActionLabels("Bravo"),
        )

        performReorderAction(title = "Bravo", label = "Move shortcut right")

        assertEquals(listOf(pinnedB to 2), moves)
    }

    @Test
    fun `GIVEN a right to left layout THEN the accessibility action to move left goes to the next index`() {
        setContent(listOf(pinnedA, pinnedB, pinnedC), layoutDirection = LayoutDirection.Rtl)

        performReorderAction(title = "Bravo", label = "Move shortcut left")

        assertEquals(listOf(pinnedB to 2), moves)
    }

    @Test
    fun `GIVEN a sponsored shortcut THEN it has no accessibility actions to move it`() {
        setContent(listOf(provided, pinnedA))

        assertTrue(reorderActionLabels("Sponsor").isEmpty())
    }

    private fun pinned(title: String) =
        TopSite.Pinned(id = title.hashCode().toLong(), title = title, url = "https://$title", createdAt = 1L)

    private fun setContent(
        topSites: List<TopSite>,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                FirefoxTheme(theme = Theme.Light) {
                    Surface {
                        Box(modifier = Modifier.requiredWidth(360.dp)) {
                            TopSites(
                                topSites = topSites,
                                onTopSiteClick = { clicks.add(it) },
                                onTopSiteLongClick = { longClicks.add(it) },
                                onTopSiteImpression = { _, _ -> },
                                onOpenInPrivateTabClicked = {},
                                onEditTopSiteClicked = {},
                                onRemoveTopSiteClicked = {},
                                onSettingsClicked = {},
                                onSponsorPrivacyClicked = {},
                                onTopSitesItemBound = {},
                                onAddShortcutClicked = {},
                                onTopSiteMoved = { topSite, toIndex -> moves.add(topSite to toIndex) },
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private val container: SemanticsNodeInteraction
        get() = composeTestRule.onNodeWithTag(TopSitesTestTag.TOP_SITES)

    private fun center(title: String): Offset =
        composeTestRule.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.center

    private fun inContainer(position: Offset): Offset = position - container.fetchSemanticsNode().boundsInRoot.topLeft

    private fun pressAt(position: Offset) {
        val inContainer = inContainer(position)
        fingerPosition = inContainer
        container.performTouchInput { down(inContainer) }
    }

    private fun longPress(title: String) {
        pressAt(center(title))
        composeTestRule.mainClock.advanceTimeBy(LONG_PRESS_MILLIS)
        composeTestRule.waitForIdle()
    }

    /** Moves the pressed finger to [to] in small steps, letting the grid lay itself out after each one. */
    private fun moveFinger(to: Offset) {
        val start = fingerPosition
        val target = inContainer(to)
        for (step in 1..MOVE_STEPS) {
            val next = start + (target - start) * (step.toFloat() / MOVE_STEPS)
            fingerPosition = next
            container.performTouchInput { moveTo(next) }
            composeTestRule.mainClock.advanceTimeBy(FRAME_MILLIS)
            composeTestRule.waitForIdle()
        }
    }

    private fun lift() {
        container.performTouchInput { up() }
        composeTestRule.waitForIdle()
    }

    private fun drag(from: String, to: String) {
        val target = center(to)
        longPress(from)
        moveFinger(to = target)
        lift()
    }

    private fun reorderActions(title: String) =
        composeTestRule
            .onNode(hasTitle(title))
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsActions.CustomActions)
            .orEmpty()

    private fun hasTitle(title: String) =
        SemanticsMatcher("has title $title") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == title } == true
        }

    private fun reorderActionLabels(title: String) = reorderActions(title).map { it.label }.sorted()

    private fun performReorderAction(title: String, label: String) {
        composeTestRule.runOnUiThread {
            assertTrue(reorderActions(title).first { it.label == label }.action())
        }
        composeTestRule.waitForIdle()
    }

    private companion object {
        const val LONG_PRESS_MILLIS = 1_000L
        const val FRAME_MILLIS = 32L
        const val MOVE_STEPS = 12
    }
}
