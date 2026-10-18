/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.component.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import com.kyant.backdrop.Backdrop
import kotlin.math.roundToInt
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.di.LocalNavStack
import paige.navic.domain.model.settings.NavbarConfig
import paige.navic.domain.model.settings.NavbarTab
import paige.navic.ui.navigation.NavigationTab
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screen.settings.viewmodel.NavtabsViewModel
import paige.navic.ui.viewmodel.RootViewModel

/**
 * iOS 26 tab bar: Kyant's liquid glass tabs (draggable glass indicator) and a liquid glass
 * search button, emitted as two
 * layout children for [RootBottomBar]. As [collapse] goes 0 -> 1 the other tabs and the labels
 * fade and shrink away until only the active tab is left, as a circle.
 */
@Composable
fun BottomBar(
	backdrop: Backdrop,
	collapse: () -> Float
) {
	val viewModel = koinViewModel<NavtabsViewModel>()
	val rootViewModel = koinViewModel<RootViewModel>()
	val backStack = LocalNavStack.current
	val state by viewModel.state.collectAsStateWithLifecycle()
	val tabs = (state.data ?: NavbarConfig.default).tabs.filter { tab -> tab.visible }.map { tab ->
		when (tab.id) {
			NavbarTab.Id.LIBRARY -> NavigationTab.LIBRARY
			NavbarTab.Id.ALBUMS -> NavigationTab.ALBUMS
			NavbarTab.Id.PLAYLISTS -> NavigationTab.PLAYLISTS
			NavbarTab.Id.ARTISTS -> NavigationTab.ARTISTS
			NavbarTab.Id.SEARCH -> NavigationTab.SEARCH
			NavbarTab.Id.GENRES -> NavigationTab.GENRES
			NavbarTab.Id.SONGS -> NavigationTab.SONGS
			NavbarTab.Id.RADIOS -> NavigationTab.RADIOS
			NavbarTab.Id.STATISTICS -> NavigationTab.STATISTICS
		}
	}.filter { it != NavigationTab.SEARCH } // search always gets its own circle

	val onTabSelected = { destination: Screen ->
		if (backStack.lastOrNull() == destination) {
			rootViewModel.requestScrollToTop()
		} else {
			backStack.apply {
				clear()
				add(destination)
			}
		}
	}

	// the tab root stays highlighted while browsing deeper into it, like iOS
	val root = backStack.firstOrNull()
	val selected = tabs.indexOfFirst { it.destination == root }
	val kept = selected.takeIf { it >= 0 } ?: 0
	val selectedState = rememberUpdatedState(selected) // a stable lambda for LiquidBottomTabs
	val p = collapse()
	val fade = 1f - p

	if (tabs.isEmpty()) Box(Modifier) else LiquidBottomTabs(
		selectedTabIndex = { selectedState.value },
		onTabSelected = { i -> tabs.getOrNull(i)?.let { if (it.destination != root) onTabSelected(it.destination) } },
		onTabReselected = { i -> tabs.getOrNull(i)?.let { onTabSelected(it.destination) } },
		backdrop = backdrop,
		tabsCount = tabs.size,
		// the glass indicator fades out while the bar collapses, the kept tab turns accent instead
		indicatorAlpha = { if (selectedState.value < 0) 0f else 1f - collapse() }
	) {
		tabs.forEachIndexed { index, tab ->
			if (index != kept && p >= 1f) return@forEachIndexed
			val label = stringResource(tab.label)
			val color = if (index == selected) lerp(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.primary, p)
			else MaterialTheme.colorScheme.onSurface
			LiquidBottomTab(
				onClick = dropUnlessResumed { onTabSelected(tab.destination) },
				weight = if (index == kept) 1f else fade.coerceAtLeast(0.001f),
				modifier = Modifier
					.graphicsLayer { alpha = if (index == kept) 1f else fade }
					.semantics { this.selected = index == selected }
			) {
				Icon(
					imageVector = if (index == selected) tab.iconFilled else tab.iconOutlined,
					contentDescription = if (p > .5f) label else null,
					tint = color
				)
				Text(
					text = label,
					color = color,
					style = MaterialTheme.typography.labelSmall,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					// the label folds away so the icon glides to the centre of the circle
					modifier = Modifier
						.layout { measurable, constraints ->
							val placeable = measurable.measure(constraints)
							layout(placeable.width, (placeable.height * fade).roundToInt()) { placeable.place(0, 0) }
						}
						.graphicsLayer { alpha = fade }
				)
			}
		}
	}
	LiquidButton(
		onClick = dropUnlessResumed { onTabSelected(NavigationTab.SEARCH.destination) },
		backdrop = backdrop
	) {
		Icon(
			imageVector = NavigationTab.SEARCH.iconOutlined,
			contentDescription = stringResource(NavigationTab.SEARCH.label),
			tint = if (root is Screen.Search) MaterialTheme.colorScheme.primary
			else MaterialTheme.colorScheme.onSurface
		)
	}
}
