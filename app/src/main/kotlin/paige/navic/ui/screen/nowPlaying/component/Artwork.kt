/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.nowPlaying.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import paige.navic.domain.model.DomainSong
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.component.common.CoverArt
import paige.navic.ui.icons.Icons
import paige.navic.ui.icons.filled.Note
import paige.navic.ui.util.easedVerticalGradient

@Composable
fun NowPlayingArtwork(
	modifier: Modifier = Modifier,
	onClick: (() -> Unit)?,
	isLandscape: Boolean,
	song: DomainSong
) {
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()

	val padding by animateDpAsState(
		targetValue = if (playerState.isPaused || playerState.currentSong?.id != song.id)
			48.dp
		else 16.dp
	)
	Box(
		modifier = modifier,
		contentAlignment = Alignment.Center
	) {
		if (isLandscape) {
			CoverArt(
				coverArtId = song.coverArtId,
				modifier = Modifier
					.aspectRatio(1f)
					.fillMaxHeight()
					.padding(padding),
				shadowElevation = 8.dp,
				onClick = onClick
			)
		} else {
			// iOS 26: edge-to-edge cover whose bottom melts into the background
			CoverArt(
				coverArtId = song.coverArtId,
				modifier = Modifier
					.fillMaxSize()
					.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
					.drawWithContent {
						drawContent()
						drawRect(
							Brush.easedVerticalGradient(startY = 0f, endY = size.height),
							blendMode = BlendMode.DstIn
						)
					},
				contentScale = ContentScale.Crop,
				shape = RectangleShape,
				onClick = onClick
			)
		}
		if (song.coverArtId == null) {
			Icon(
				imageVector = Icons.Filled.Note,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
				modifier = Modifier.size(96.dp)
			)
		}
	}
}
