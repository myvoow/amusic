/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.component.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 * iOS 26 tab bar: a glass capsule of tabs and a separate glass search circle, emitted as two
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
	val kept = tabs.firstOrNull { it.destination == root } ?: tabs.firstOrNull()
	val p = collapse()
	val fade = 1f - p

	Row(Modifier.glass(backdrop).padding(4.dp)) {
		tabs.forEach { tab ->
			if (tab != kept && p >= 1f) return@forEach
			val selected = root == tab.destination
			val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
			val label = stringResource(tab.label)
			Column(
				modifier = Modifier
					.weight(if (tab == kept) 1f else fade.coerceAtLeast(0.001f))
					.fillMaxHeight()
					.graphicsLayer { alpha = if (tab == kept) 1f else fade }
					.clip(CircleShape)
					.background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = .08f) else Color.Transparent)
					.selectable(
						selected = selected,
						role = Role.Tab,
						onClick = dropUnlessResumed { onTabSelected(tab.destination) }
					),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.Center
			) {
				Icon(
					imageVector = if (selected) tab.iconFilled else tab.iconOutlined,
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
	IconButton(
		onClick = dropUnlessResumed { onTabSelected(NavigationTab.SEARCH.destination) },
		modifier = Modifier.glass(backdrop)
	) {
		Icon(
			imageVector = NavigationTab.SEARCH.iconOutlined,
			contentDescription = stringResource(NavigationTab.SEARCH.label),
			tint = if (root is Screen.Search) MaterialTheme.colorScheme.primary
			else MaterialTheme.colorScheme.onSurface
		)
	}
}
