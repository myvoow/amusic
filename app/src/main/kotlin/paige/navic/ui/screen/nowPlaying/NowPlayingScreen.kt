/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.nowPlaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.R
import paige.navic.di.LocalNavStack
import paige.navic.di.LocalSheetState
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.model.settings.NowPlayingBackgroundStyle
import paige.navic.domain.model.settings.ToolbarPosition
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.component.common.BlendBackground
import paige.navic.ui.component.layout.SheetScaffold
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screen.nowPlaying.component.controls.NowPlayingArtworkPager
import paige.navic.ui.screen.nowPlaying.component.rows.NowPlayingControlsRow
import paige.navic.ui.screen.nowPlaying.viewmodel.NowPlayingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen() {
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val backStack = LocalNavStack.current

	val currentScreen = backStack.lastOrNull()
	val isPlayerCurrent = currentScreen is Screen.NowPlaying
		|| currentScreen is Screen.Queue
		|| currentScreen is Screen.PlaybackSpeed
		|| currentScreen is Screen.SongDetailSheet

	val playerState by player.uiState.collectAsStateWithLifecycle()
	val song = playerState.currentSong

	val viewModel = koinViewModel<NowPlayingViewModel> { parametersOf(player) }
	val songIsStarred by viewModel.songIsStarred.collectAsStateWithLifecycle()
	val songRating by viewModel.songRating.collectAsStateWithLifecycle()

	val sheetState = LocalSheetState.current
	val closeScope = rememberCoroutineScope()
	val animateToDismiss = dropUnlessResumed {
		closeScope.launch {
			sheetState.hide()
		}.invokeOnCompletion {
			if (!sheetState.isVisible) {
				backStack.remove(Screen.NowPlaying)
			}
		}
	}

	SheetScaffold(
		toolbar = { windowInsets ->
			NowPlayingGrabber(
				windowInsets = windowInsets,
				onClick = animateToDismiss,
				modifier = Modifier.alpha(if (isPlayerCurrent) 1f else 0f)
			)
		},
		toolbarPosition = ToolbarPosition.Top
	) { contentPadding ->
		Box(Modifier.fillMaxSize()) {
			if (preferenceManager.nowPlayingBackgroundStyle
				== NowPlayingBackgroundStyle.Dynamic
			) {
				BlendBackground(
					coverArtId = song?.coverArtId,
					isPaused = playerState.isPaused
				)
			}
			if (!isPlayerCurrent) return@Box
			BoxWithConstraints(Modifier.fillMaxSize()) {
				val isLandscape = maxWidth > maxHeight
				// TODO: figure out why artwork pager unnecessarily resizes when you drag the sheet
				if (isLandscape) {
					Row(
						modifier = Modifier
							.fillMaxSize()
							.padding(contentPadding)
							.padding(horizontal = 8.dp),
						horizontalArrangement = Arrangement.SpaceEvenly,
						verticalAlignment = Alignment.CenterVertically
					) {
						NowPlayingArtworkPager(
							modifier = Modifier.weight(1f).fillMaxHeight(),
							isLandscape = true
						)
						NowPlayingControlsRow(
							modifier = Modifier.weight(1f).fillMaxHeight(),
							songIsStarred = songIsStarred,
							onSetSongIsStarred = { viewModel.starSong(it) },
							songRating = songRating,
							onSetSongRating = { viewModel.rateSong(it) }
						)
					}
				} else {
					// iOS 26: edge-to-edge artwork at the top, fading into the cover colours
					Column(
						modifier = Modifier
							.fillMaxSize()
							.padding(bottom = contentPadding.calculateBottomPadding()),
						horizontalAlignment = Alignment.CenterHorizontally
					) {
						NowPlayingArtworkPager(
							modifier = Modifier.fillMaxWidth().aspectRatio(1f),
							isLandscape = false
						)
						NowPlayingControlsRow(
							modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
							songIsStarred = songIsStarred,
							onSetSongIsStarred = { viewModel.starSong(it) },
							songRating = songRating,
							onSetSongRating = { viewModel.rateSong(it) }
						)
					}
				}
			}
		}
	}
}

/** iOS sheet grabber; tapping it closes the player (keeps the old back button's accessibility). */
@Composable
private fun NowPlayingGrabber(
	windowInsets: WindowInsets,
	onClick: () -> Unit,
	modifier: Modifier = Modifier
) {
	val label = stringResource(R.string.action_navigate_back)
	Box(
		modifier = modifier
			.fillMaxWidth()
			.windowInsetsPadding(windowInsets),
		contentAlignment = Alignment.Center
	) {
		Box(
			modifier = Modifier
				.size(width = 72.dp, height = 48.dp)
				.clickable(role = Role.Button, onClick = onClick)
				.semantics { contentDescription = label },
			contentAlignment = Alignment.Center
		) {
			Box(
				Modifier
					.size(width = 36.dp, height = 5.dp)
					.background(MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), CircleShape)
			)
		}
	}
}
