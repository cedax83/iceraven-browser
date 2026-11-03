/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.topsites.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mozilla.components.feature.top.sites.TopSite
import org.mozilla.fenix.R
import org.mozilla.fenix.home.topsites.gridKeys
import org.mozilla.fenix.home.topsites.movedTo
import org.mozilla.fenix.home.topsites.reorderRange
import org.mozilla.fenix.tabstray.ui.tabpage.ReorderDirection
import org.mozilla.fenix.tabstray.ui.tabpage.TabReorderGeometry

/**
 * Tracks a shortcut being dragged around the grid.
 *
 * @param gridState State of the grid the shortcuts are displayed in.
 * @param scope [CoroutineScope] used for scrolling and for animating a dropped shortcut into place.
 * @param canAutoScroll Whether the grid scrolls when a shortcut is dragged to its edge.
 * @param sites Returns the shortcuts as currently displayed.
 * @param onSitesChange Invoked with the new arrangement each time the dragged shortcut changes slot.
 * @param onDrop Invoked with the shortcut and its new index when it is dropped somewhere else.
 */
internal class ShortcutsReorderState(
    private val gridState: LazyGridState,
    private val scope: CoroutineScope,
    private val canAutoScroll: Boolean,
    private val sites: () -> List<TopSite>,
    private val onSitesChange: (List<TopSite>) -> Unit,
    private val onDrop: (TopSite, Int) -> Unit,
) {
    /** Key of the shortcut under the user's finger. */
    var draggedKey by mutableStateOf<Any?>(null)
        private set

    /** Key of the shortcut animating into its slot after being dropped. */
    var settlingKey by mutableStateOf<Any?>(null)
        private set

    /** How far the settling shortcut still is from its slot. */
    val settleOffset = Animatable(Offset.Zero, Offset.VectorConverter)

    private var initialItemOffset by mutableStateOf(Offset.Zero)
    private var accumulatedDrag by mutableStateOf(Offset.Zero)
    private var startIndex = -1

    private val draggedItem: LazyGridItemInfo?
        get() = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggedKey }

    /** How far the dragged shortcut has to be translated from its slot to stay under the finger. */
    fun dragTranslation(): Offset =
        draggedItem?.let { initialItemOffset + accumulatedDrag - it.offset.toOffset() } ?: Offset.Zero

    /**
     * Starts dragging the shortcut at [position], in the coordinates of the grid.
     *
     * @return Whether there is a shortcut that can be dragged at [position].
     */
    fun startDrag(position: Offset): Boolean {
        val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.contains(position) } ?: return false
        val currentSites = sites()
        val index = currentSites.gridKeys().indexOf(item.key)
        if (currentSites.reorderRange(index).isEmpty()) return false

        startIndex = index
        initialItemOffset = item.offset.toOffset()
        accumulatedDrag = Offset.Zero
        draggedKey = item.key
        return true
    }

    /** Moves the dragged shortcut by [delta], swapping slots with the shortcut it is now over. */
    fun drag(delta: Offset) {
        accumulatedDrag += delta
        val dragged = draggedItem ?: return

        val topLeft = initialItemOffset + accumulatedDrag
        val center = topLeft + Offset(dragged.size.width / 2f, dragged.size.height / 2f)
        gridState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key != draggedKey && it.contains(center) }
            ?.let { moveOnto(it.key) }

        if (canAutoScroll) {
            val viewportEndOverflow = topLeft.y + dragged.size.height - gridState.layoutInfo.viewportEndOffset
            val viewportStartOverflow = topLeft.y - gridState.layoutInfo.viewportStartOffset
            val overflow =
                when {
                    delta.y > 0 -> viewportEndOverflow.coerceAtLeast(0f)
                    delta.y < 0 -> viewportStartOverflow.coerceAtMost(0f)
                    else -> 0f
                }
            if (overflow != 0f) {
                scope.launch { gridState.scrollBy(overflow) }
            }
        }
    }

    /** Ends the drag and reports the new position of the shortcut, if it changed. */
    fun endDrag() {
        val key = draggedKey ?: return
        val currentSites = sites()
        val finalIndex = currentSites.gridKeys().indexOf(key)
        val remainingTranslation = dragTranslation()

        settlingKey = key
        draggedKey = null
        accumulatedDrag = Offset.Zero
        initialItemOffset = Offset.Zero

        scope.launch {
            settleOffset.snapTo(remainingTranslation)
            settleOffset.animateTo(
                targetValue = Offset.Zero,
                animationSpec =
                    spring(
                        stiffness = Spring.StiffnessMediumLow,
                        visibilityThreshold = Offset.VisibilityThreshold,
                    ),
            )
            settlingKey = null
        }

        if (finalIndex >= 0 && finalIndex != startIndex) {
            onDrop(currentSites[finalIndex], finalIndex)
        }
    }

    private fun moveOnto(targetKey: Any) {
        // Going by key rather than by the indices in the layout info, which lag behind by up to a frame.
        val currentSites = sites()
        val keys = currentSites.gridKeys()
        val fromIndex = keys.indexOf(draggedKey)
        val toIndex = keys.indexOf(targetKey)

        if (fromIndex >= 0 && toIndex >= 0 && toIndex in currentSites.reorderRange(fromIndex)) {
            onSitesChange(currentSites.movedTo(fromIndex, toIndex))
        }
    }

    private fun LazyGridItemInfo.contains(position: Offset): Boolean =
        position.x >= offset.x &&
            position.x <= offset.x + size.width &&
            position.y >= offset.y &&
            position.y <= offset.y + size.height
}

/**
 * Lets the user drag a shortcut after long pressing it.
 *
 * The events are read on the initial pass, before the shortcuts see them. Shortcuts handle their own long press to open
 * their menu and then consume every event until the finger is lifted, which would otherwise cancel a drag detected by
 * a parent such as with `detectDragGesturesAfterLongPress`. Nothing is consumed here until the drag starts, so tapping,
 * opening the menu and scrolling the page keep working unchanged.
 */
internal fun Modifier.shortcutsReorderGesture(state: ShortcutsReorderState): Modifier =
    pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val touchSlop = viewConfiguration.touchSlop

            // Times out when the finger stays put, which is the long press.
            var isLongPressAbandoned = false
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (!isLongPressAbandoned) {
                    val change =
                        awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    isLongPressAbandoned =
                        change == null || !change.pressed || (change.position - down.position).getDistance() > touchSlop
                }
            }
            if (isLongPressAbandoned) return@awaitEachGesture

            var isDragging = false
            try {
                while (true) {
                    val change =
                        awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break

                    if (isDragging) {
                        state.drag(change.position - change.previousPosition)
                        change.consume()
                    } else if ((change.position - down.position).getDistance() > touchSlop) {
                        isDragging = state.startDrag(down.position)
                        if (!isDragging) break
                        state.drag(change.position - down.position)
                        change.consume()
                    }
                }
            } finally {
                if (isDragging) state.endDrag()
            }
        }
    }

/**
 * Builds the accessibility actions that let [topSite] be moved without dragging it, for the directions where the move
 * would land inside [reorderRange].
 *
 * @param topSite The top site the actions are attached to.
 * @param sites The top sites currently displayed.
 * @param index The index of [topSite] in [sites].
 * @param columns Number of columns of the grid.
 * @param onTopSiteMoved Invoked with [topSite] and its new index when an action is performed.
 */
@Composable
internal fun rememberShortcutReorderActions(
    topSite: TopSite,
    sites: List<TopSite>,
    index: Int,
    columns: Int,
    onTopSiteMoved: (TopSite, Int) -> Unit,
): List<CustomAccessibilityAction> {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val up = stringResource(R.string.custom_accessibility_action_reorder_shortcut_up)
    val down = stringResource(R.string.custom_accessibility_action_reorder_shortcut_down)
    val left = stringResource(R.string.custom_accessibility_action_reorder_shortcut_left)
    val right = stringResource(R.string.custom_accessibility_action_reorder_shortcut_right)
    val currentOnTopSiteMoved by rememberUpdatedState(onTopSiteMoved)

    return remember(topSite, sites, index, columns, isRtl, up, down, left, right) {
        val geometry = TabReorderGeometry(tabCount = sites.size, columns = columns)
        val range = sites.reorderRange(index)

        geometry.reorderDirections(tabIndex = index, isRtl = isRtl).mapNotNull { direction ->
            val toIndex = index + geometry.reorderOffset(direction = direction, isRtl = isRtl)
            val label =
                when (direction) {
                    ReorderDirection.UP -> up
                    ReorderDirection.DOWN -> down
                    ReorderDirection.LEFT -> left
                    ReorderDirection.RIGHT -> right
                }

            if (toIndex in range) {
                CustomAccessibilityAction(label = label) {
                    currentOnTopSiteMoved(topSite, toIndex)
                    true
                }
            } else {
                null
            }
        }
    }
}
