/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.component.layout

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.R
import paige.navic.di.LocalNavStack
import paige.navic.di.LocalSizeClass
import paige.navic.domain.model.settings.NavbarConfig
import paige.navic.domain.model.settings.NavbarTab
import paige.navic.ui.icons.Icons
import paige.navic.ui.icons.filled.Settings
import paige.navic.ui.icons.outlined.AccountCircle
import paige.navic.ui.icons.outlined.Search
import paige.navic.ui.component.common.TooltipBox
import paige.navic.ui.component.sheet.AccountSheet
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screen.settings.viewmodel.NavtabsViewModel

@Composable
fun RootTopBar(
	title: @Composable () -> Unit,
	scrollBehavior: TopAppBarScrollBehavior,
	actions: @Composable RowScope.() -> Unit = {},
) {
	val navViewModel = koinViewModel<NavtabsViewModel>()
	val navState by navViewModel.state.collectAsState()
	val navConfig = (navState as? UiState.Success)?.data

	MediumFlexibleTopAppBar(
		title = {
			CompositionLocalProvider(
				LocalTextStyle provides when (LocalTextStyle.current) {
					MaterialTheme.typography.headlineMedium -> MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
					else -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
				}
			) {
				title()
			}
		},
		actions = {
			// iOS: the header buttons sit together in one round pill
			Row(
				modifier = Modifier.padding(end = 12.dp).glass(null),
				verticalAlignment = Alignment.CenterVertically
			) {
				actions()
				Actions(navConfig = navConfig)
			}
		},
		scrollBehavior = scrollBehavior,
		colors = TopAppBarDefaults.topAppBarColors(
			scrolledContainerColor = MaterialTheme.colorScheme.surface
		),
	)
}

@Composable
private fun Actions(
	navConfig: NavbarConfig?,
) {
	val backStack = LocalNavStack.current

	val isSearchEnabled = navConfig?.tabs?.any {
		it.id == NavbarTab.Id.SEARCH && it.visible
	} == true

	var accountSheetOpen by rememberSaveable { mutableStateOf(false) }

	// phones get the search circle next to the tab bar
	val sizeClass = LocalSizeClass.current
	if (!isSearchEnabled && sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium) {
		TooltipBox(stringResource(R.string.title_search)) {
			IconButton(
				onClick = dropUnlessResumed {
					backStack.add(Screen.Search(nested = true))
				}
			) {
				Icon(
					imageVector = Icons.Outlined.Search,
					contentDescription = stringResource(R.string.title_search)
				)
			}
		}
	}

	TooltipBox(stringResource(R.string.title_settings)) {
		IconButton(onClick = dropUnlessResumed {
			backStack.add(Screen.Settings.Root)
		}) {
			Icon(
				imageVector = Icons.Filled.Settings,
				contentDescription = stringResource(R.string.title_settings)
			)
		}
	}

	TooltipBox(stringResource(R.string.title_account)) {
		IconButton(onClick = {
			accountSheetOpen = true
		}) {
			Icon(
				imageVector = Icons.Outlined.AccountCircle,
				contentDescription = stringResource(R.string.title_account)
			)
		}
	}

	if (accountSheetOpen) {
		AccountSheet(onDismissRequest = { accountSheetOpen = false })
	}
}
