/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.topsites.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import mozilla.components.compose.base.annotation.FlexibleWindowLightDarkPreview
import mozilla.components.feature.top.sites.TopSite
import org.mozilla.fenix.compose.MenuItem
import org.mozilla.fenix.home.fake.FakeHomepagePreview
import org.mozilla.fenix.home.topsites.TOP_SITES_ITEM_BOTTOM_SPACING
import org.mozilla.fenix.home.topsites.TOP_SITES_ITEM_TOP_SPACING
import org.mozilla.fenix.home.topsites.TOP_SITES_SPACING
import org.mozilla.fenix.home.topsites.TopSiteColors
import org.mozilla.fenix.home.topsites.TopSiteItem
import org.mozilla.fenix.home.topsites.TopSiteItemText
import org.mozilla.fenix.home.topsites.TopSitesRowLayout
import org.mozilla.fenix.home.topsites.calculateTopSitesRowLayout
import org.mozilla.fenix.home.topsites.getMenuItems
import org.mozilla.fenix.home.topsites.gridKeys
import org.mozilla.fenix.theme.FirefoxTheme

/**
 * The grid of shortcuts shared by the homepage and the shortcuts library.
 *
 * The number of columns, the cell size and the spacing all adapt to the available width, so both surfaces lay their
 * shortcuts out identically.
 *
 * On the homepage the grid cannot scroll itself: it sits inside the page's vertical scroll, where a scrollable child is
 * measured with an infinite height and fails. There it is given a bounded height instead, with scrolling disabled. The
 * library, which is a screen of its own, scrolls normally.
 *
 * @param topSites List of [TopSite] to display.
 * @param topSiteColors The color set defined by [TopSiteColors] used to style a top site.
 * @param showAddShortcut Whether to display the "Add shortcut" tile after the top sites.
 * @param scrollable Whether the grid scrolls itself. False when hosted inside another scrolling container.
 * @param menuItems The [MenuItem]s to show in a top site's dropdown menu.
 * @param onTopSiteClick Invoked when the user clicks on a top site.
 * @param onTopSiteLongClick Invoked when the user long clicks on a top site.
 * @param onTopSiteImpression Invoked when the user sees a provided top site.
 * @param onTopSitesItemBound Invoked during the composition of a top site item.
 * @param onAddShortcutClicked Invoked when the user clicks on the "Add shortcut" tile.
 * @param onTopSiteMoved Invoked when the user drags a top site to a new index.
 * @param modifier The [Modifier] to be applied to the grid.
 */
@Composable
@Suppress("LongParameterList")
internal fun Shortcuts(
    topSites: List<TopSite>,
    topSiteColors: TopSiteColors,
    showAddShortcut: Boolean,
    scrollable: Boolean,
    menuItems: @Composable (TopSite) -> List<MenuItem>,
    onTopSiteClick: (TopSite) -> Unit,
    onTopSiteLongClick: (TopSite) -> Unit,
    onTopSiteImpression: (TopSite.Provided, Int) -> Unit,
    onTopSitesItemBound: () -> Unit,
    onAddShortcutClicked: () -> Unit,
    onTopSiteMoved: (TopSite, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val itemCount = topSites.size + if (showAddShortcut) 1 else 0
    val rowSpacing = FirefoxTheme.layout.space.static150

    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val layout = remember(maxWidth) { calculateTopSitesRowLayout(maxWidth) }
        val rowWidth = layout.itemWidth * layout.columns + TOP_SITES_SPACING * (layout.columns - 1)
        val rowCount = (itemCount + layout.columns - 1) / layout.columns

        val grid: @Composable (Modifier) -> Unit = { gridModifier ->
            ShortcutsLazyGrid(
                modifier = gridModifier,
                layout = layout,
                scrollable = scrollable,
                rowSpacing = rowSpacing,
                topSites = topSites,
                topSiteColors = topSiteColors,
                showAddShortcut = showAddShortcut,
                menuItems = menuItems,
                onTopSiteClick = onTopSiteClick,
                onTopSiteLongClick = onTopSiteLongClick,
                onTopSiteImpression = onTopSiteImpression,
                onTopSitesItemBound = onTopSitesItemBound,
                onAddShortcutClicked = onAddShortcutClicked,
                onTopSiteMoved = onTopSiteMoved,
            )
        }

        if (scrollable) {
            grid(Modifier.width(rowWidth).fillMaxHeight())
        } else {
            SelfMeasuredGrid(
                rowCount = rowCount,
                rowSpacing = rowSpacing,
                layout = layout,
                topSiteColors = topSiteColors,
                modifier = Modifier.width(rowWidth),
            ) {
                grid(Modifier.width(rowWidth))
            }
        }
    }
}

/**
 * Caps [grid] at the height [rowCount] rows take in the worst case, measured from a shortcut's text.
 *
 * The homepage grid sits inside the page's vertical scroll, where it is measured with an infinite height and cannot
 * measure itself, so it has to be given a bound. That bound is a maximum rather than an exact height: a row whose
 * titles all fit on one line is shorter than the worst case, and pinning the grid to the worst case instead leaves the
 * surplus as dead space between the last row and whatever follows the grid.
 *
 * Estimating the bound from the caption line height under-counts, because the text style preserves the padding around
 * each line, and the shortfall grows with the user's font scale until a `LazyVerticalGrid` clips the last row.
 * Measuring the real [TopSiteItemText] avoids the estimate entirely.
 *
 * @param rowCount Number of rows the grid lays out.
 * @param rowSpacing Vertical gap between rows.
 * @param layout The resolved row layout, providing the cell and favicon sizes.
 * @param topSiteColors The color set defined by [TopSiteColors] used to style a top site.
 * @param modifier The [Modifier] to be applied to the layout.
 * @param grid The grid to bound.
 */
@Composable
private fun SelfMeasuredGrid(
    rowCount: Int,
    rowSpacing: Dp,
    layout: TopSitesRowLayout,
    topSiteColors: TopSiteColors,
    modifier: Modifier = Modifier,
    grid: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val cellWidth = layout.itemWidth.roundToPx()

        // A shortcut with a title long enough to wrap is the tallest a cell gets, so it bounds every row.
        val textHeight =
            subcompose(SlotId.Probe) {
                    TopSiteItemText(
                        title = TITLE_HEIGHT_PROBE,
                        maxTitleLines = MAX_TITLE_LINES,
                        isSponsored = true,
                        topSiteColors = topSiteColors,
                        itemWidth = layout.itemWidth,
                    )
                }
                .sumOf { it.measure(Constraints(minWidth = cellWidth, maxWidth = cellWidth)).height }

        val itemHeight =
            layout.faviconCardSize.roundToPx() +
                TOP_SITES_ITEM_TOP_SPACING.roundToPx() +
                TOP_SITES_ITEM_BOTTOM_SPACING.roundToPx() +
                textHeight
        val gridHeight = itemHeight * rowCount + rowSpacing.roundToPx() * (rowCount - 1).coerceAtLeast(0)

        val placeable =
            subcompose(SlotId.Grid, grid).first().measure(constraints.copy(minHeight = 0, maxHeight = gridHeight))

        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

private enum class SlotId {
    Probe,
    Grid,
}

/** Long enough to wrap onto [MAX_TITLE_LINES] at any cell width, so the probe measures the tallest cell. */
private const val TITLE_HEIGHT_PROBE = "Measuring a shortcut title that wraps"

private const val MAX_TITLE_LINES = 2

/** Grid key of the "Add shortcut" tile. Not a URL, so it can't clash with the key of a shortcut. */
private const val ADD_SHORTCUT_KEY = "add_shortcut_tile"

private const val DRAGGED_SHORTCUT_SCALE = 1.1f

@Composable
@Suppress("LongParameterList")
private fun ShortcutsLazyGrid(
    modifier: Modifier,
    layout: TopSitesRowLayout,
    scrollable: Boolean,
    rowSpacing: Dp,
    topSites: List<TopSite>,
    topSiteColors: TopSiteColors,
    showAddShortcut: Boolean,
    menuItems: @Composable (TopSite) -> List<MenuItem>,
    onTopSiteClick: (TopSite) -> Unit,
    onTopSiteLongClick: (TopSite) -> Unit,
    onTopSiteImpression: (TopSite.Provided, Int) -> Unit,
    onTopSitesItemBound: () -> Unit,
    onAddShortcutClicked: () -> Unit,
    onTopSiteMoved: (TopSite, Int) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    // The arrangement on screen, which is ahead of topSites while a shortcut is being dragged.
    val displayedSites = remember(topSites) { mutableStateOf(topSites) }
    val latestDisplayedSites by rememberUpdatedState(displayedSites)
    val latestOnTopSiteMoved by rememberUpdatedState(onTopSiteMoved)

    val reorderState =
        remember(gridState, scrollable) {
            ShortcutsReorderState(
                gridState = gridState,
                scope = scope,
                canAutoScroll = scrollable,
                sites = { latestDisplayedSites.value },
                onSitesChange = { latestDisplayedSites.value = it },
                onDrop = { topSite, toIndex -> latestOnTopSiteMoved(topSite, toIndex) },
            )
        }

    val sites = displayedSites.value
    val keys = remember(sites) { sites.gridKeys() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(layout.columns),
        state = gridState,
        modifier = modifier.shortcutsReorderGesture(reorderState),
        userScrollEnabled = scrollable,
        verticalArrangement = Arrangement.spacedBy(rowSpacing),
        horizontalArrangement = Arrangement.spacedBy(TOP_SITES_SPACING),
    ) {
        itemsIndexed(items = sites, key = { index, _ -> keys[index] }) { index, topSite ->
            val key = keys[index]
            val isDragged = key == reorderState.draggedKey
            val isSettling = key == reorderState.settlingKey

            Box(
                modifier =
                    Modifier.zIndex(if (isDragged || isSettling) 1f else 0f)
                        .then(
                            when {
                                isDragged ->
                                    Modifier.graphicsLayer {
                                        val translation = reorderState.dragTranslation()
                                        translationX = translation.x
                                        translationY = translation.y
                                        scaleX = DRAGGED_SHORTCUT_SCALE
                                        scaleY = DRAGGED_SHORTCUT_SCALE
                                    }

                                isSettling ->
                                    Modifier.graphicsLayer {
                                        translationX = reorderState.settleOffset.value.x
                                        translationY = reorderState.settleOffset.value.y
                                    }

                                else -> Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                            }
                        )
            ) {
                TopSiteItem(
                    topSite = topSite,
                    menuItems = menuItems(topSite),
                    position = index,
                    topSiteColors = topSiteColors,
                    onTopSiteClick = onTopSiteClick,
                    onTopSiteLongClick = onTopSiteLongClick,
                    onTopSiteImpression = onTopSiteImpression,
                    onTopSitesItemBound = onTopSitesItemBound,
                    itemLayout = layout.itemLayout,
                    isDragged = isDragged,
                )
            }
        }

        if (showAddShortcut) {
            item(key = ADD_SHORTCUT_KEY) {
                AddShortcutItem(
                    topSiteColors = topSiteColors,
                    onClick = onAddShortcutClicked,
                    itemLayout = layout.itemLayout,
                )
            }
        }
    }
}

@Composable
@FlexibleWindowLightDarkPreview
private fun ShortcutsPreview(@PreviewParameter(ShortcutsPreviewParameterProvider::class) showAddShortcut: Boolean) {
    val interactor = FakeHomepagePreview.topSitesInteractor

    FirefoxTheme {
        Surface {
            Shortcuts(
                topSites = FakeHomepagePreview.topSites(),
                topSiteColors = TopSiteColors.colors(),
                showAddShortcut = showAddShortcut,
                scrollable = true,
                menuItems = { topSite ->
                    getMenuItems(
                        topSite = topSite,
                        onOpenInPrivateTabClicked = interactor::onOpenInPrivateTabClicked,
                        onEditTopSiteClicked = interactor::onEditTopSiteClicked,
                        onRemoveTopSiteClicked = interactor::onRemoveTopSiteClicked,
                        onSettingsClicked = interactor::onSettingsClicked,
                        onSponsorPrivacyClicked = interactor::onSponsorPrivacyClicked,
                    )
                },
                onTopSiteClick = {},
                onTopSiteLongClick = {},
                onTopSiteImpression = { _, _ -> },
                onTopSitesItemBound = {},
                onAddShortcutClicked = {},
                modifier = Modifier.fillMaxSize().padding(FirefoxTheme.layout.space.static200),
            )
        }
    }
}

private class ShortcutsPreviewParameterProvider : PreviewParameterProvider<Boolean> {
    override val values: Sequence<Boolean> = sequenceOf(false, true)
}
