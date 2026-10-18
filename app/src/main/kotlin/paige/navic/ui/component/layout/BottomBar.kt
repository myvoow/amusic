/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.component.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import com.kyant.backdrop.Backdrop
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.di.LocalNavStack
import paige.navic.domain.model.settings.NavbarConfig
import paige.navic.domain.model.settings.NavbarTab
import paige.navic.ui.navigation.NavigationTab
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screen.settings.viewmodel.NavtabsViewModel
import paige.navic.ui.viewmodel.RootViewModel

/**
 * iOS 26 tab bar: a glass capsule of tabs plus a separate glass search circle.
 * When [collapsed], the capsule shrinks to the active tab and the mini player sits in between.
 */
@Composable
fun BottomBar(
	backdrop: Backdrop,
	collapsed: Boolean,
	modifier: Modifier = Modifier
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
	val shown = if (collapsed) tabs.filter { it.destination == root }.ifEmpty { tabs.take(1) } else tabs

	Row(
		modifier = modifier,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Row(
			Modifier
				.then(if (collapsed) Modifier else Modifier.weight(1f))
				.height(62.dp)
				.glass(backdrop)
				.padding(4.dp)
		) {
			shown.forEach { tab ->
				val selected = root == tab.destination
				val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
				val label = stringResource(tab.label)
				Column(
					modifier = Modifier
						.then(if (collapsed) Modifier.aspectRatio(1f) else Modifier.weight(1f))
						.fillMaxHeight()
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
						contentDescription = if (collapsed) label else null,
						tint = color
					)
					if (!collapsed) {
						Text(
							text = label,
							color = color,
							style = MaterialTheme.typography.labelSmall,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis
						)
					}
				}
			}
		}
		if (collapsed) {
			Box(Modifier.weight(1f)) {
				MiniPlayer(backdrop = backdrop, compact = true)
			}
		}
		IconButton(
			onClick = dropUnlessResumed { onTabSelected(NavigationTab.SEARCH.destination) },
			modifier = Modifier.size(62.dp).glass(backdrop)
		) {
			Icon(
				imageVector = NavigationTab.SEARCH.iconOutlined,
				contentDescription = stringResource(NavigationTab.SEARCH.label),
				tint = if (root is Screen.Search) MaterialTheme.colorScheme.primary
				else MaterialTheme.colorScheme.onSurface
			)
		}
	}
}
