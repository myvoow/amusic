/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.nowPlaying.component.rows

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.koin.compose.koinInject
import paige.navic.domain.manager.PreferenceManager
import paige.navic.ui.screen.nowPlaying.component.controls.NowPlayingProgressBar

@Composable
fun NowPlayingControlsRow(
	modifier: Modifier = Modifier,
	songIsStarred: Boolean,
	onSetSongIsStarred: (Boolean) -> Unit,
	songRating: Int,
	onSetSongRating: (Int) -> Unit
) {
	val preferenceManager = koinInject<PreferenceManager>()

	Column(
		modifier = modifier,
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.SpaceEvenly
	) {
		Column {
			NowPlayingInfoRow(
				songIsStarred = songIsStarred,
				onSetSongIsStarred = onSetSongIsStarred,
				songRating = songRating,
				onSetSongRating = onSetSongRating
			)
			NowPlayingProgressBar()
			NowPlayingDurationsRow()
			if (preferenceManager.nowPlayingSongInfo) {
				NowPlayingTechnicalInfoRow()
			}
		}
		NowPlayingButtonsRow()
		NowPlayingActionsRow()
	}
}
