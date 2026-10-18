/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.nowPlaying.component.rows

import androidx.compose.foundation.layout.Row
import paige.navic.exoplayer.AutoMixState
import paige.navic.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.util.toHoursMinutesSeconds
import kotlin.time.Duration.Companion.seconds

@Composable
fun NowPlayingDurationsRow() {
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()
	val duration = playerState.currentSong?.duration
	val style = MaterialTheme.typography.bodyMedium
		.copy(
			shadow = Shadow(
				color = MaterialTheme.colorScheme.inverseOnSurface,
				offset = Offset(0f, 4f),
				blurRadius = 10f
			)
		)
	val color = MaterialTheme.colorScheme.onSurfaceVariant
	Row(Modifier.padding(horizontal = 16.dp)) {
		when {
			duration == kotlin.time.Duration.ZERO -> {
				Text(text = "LIVE", color = color, style = style)
				MixingLabel(style)
				Text(text = "∞", color = color, style = style)
			}

			duration != null -> {
				val elapsed = (duration.inWholeSeconds * playerState.progress).toDouble().seconds
				Text(
					text = elapsed.toHoursMinutesSeconds(),
					color = color, style = style
				)
				MixingLabel(style)
				Text(
					"-" + (duration - elapsed).coerceAtLeast(kotlin.time.Duration.ZERO).toHoursMinutesSeconds(),
					color = color, style = style
				)
			}

			else -> {
				Text("--:--", color = color, style = style)
				MixingLabel(style)
				Text("--:--", color = color, style = style)
			}
		}
	}
}

/** Apple Music style "Mixing" between the times while two songs overlap. */
@Composable
private fun RowScope.MixingLabel(style: TextStyle) {
	val mixing by AutoMixState.isMixing.collectAsState()
	Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
		if (mixing) Text(stringResource(R.string.info_mixing), style = style, color = MaterialTheme.colorScheme.onSurface)
	}
}
