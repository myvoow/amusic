/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.nowPlaying.component.rows

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import org.koin.compose.koinInject
import paige.navic.R
import paige.navic.di.LocalNavStack
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.icons.Icons
import paige.navic.ui.icons.filled.RepeatOn
import paige.navic.ui.icons.filled.RepeatOneOn
import paige.navic.ui.icons.filled.ShuffleOn
import paige.navic.ui.icons.filled.SkipNext
import paige.navic.ui.icons.filled.SkipPrevious
import paige.navic.ui.icons.outlined.List
import paige.navic.ui.icons.outlined.Lyrics
import paige.navic.ui.icons.outlined.Repeat
import paige.navic.ui.icons.outlined.Shuffle
import paige.navic.ui.navigation.Screen
import paige.navic.ui.util.playPauseIconPainter

/** iOS 26: big previous / play-pause / next glyphs, no button backgrounds. */
@Composable
fun NowPlayingButtonsRow() {
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()
	val interactionSource = remember { MutableInteractionSource() }
	val isPressed by interactionSource.collectIsPressedAsState()
	val scale = remember { Animatable(1f) }
	val enabled = playerState.currentSong != null
	val haptic = LocalHapticFeedback.current

	val hapticType = if (playerState.isPaused) {
		HapticFeedbackType.ToggleOn
	} else {
		HapticFeedbackType.ToggleOff
	}

	LaunchedEffect(isPressed) {
		if (!isPressed) {
			if (scale.value != 1f) {
				scale.animateTo(
					targetValue = 1.2f,
					animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing)
				)
				scale.animateTo(
					targetValue = 1f,
					animationSpec = spring(
						dampingRatio = Spring.DampingRatioMediumBouncy,
						stiffness = Spring.StiffnessLow
					)
				)
			}
		} else {
			scale.animateTo(0.85f)
		}
	}

	Row(
		modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceEvenly,
		verticalAlignment = Alignment.CenterVertically
	) {
		IconButton(
			modifier = Modifier.size(72.dp),
			onClick = {
				player.previous()
			},
			enabled = enabled
		) {
			Icon(
				imageVector = Icons.Filled.SkipPrevious,
				contentDescription = stringResource(R.string.action_previous_song),
				modifier = Modifier.size(44.dp)
			)
		}
		IconButton(
			modifier = Modifier
				.size(88.dp)
				.scale(scale.value),
			onClick = {
				haptic.performHapticFeedback(hapticType)
				player.togglePlay()
			},
			enabled = enabled,
			interactionSource = interactionSource
		) {
			val painter = playPauseIconPainter(playerState.isPaused)
			val description = stringResource(
				if (playerState.isPaused)
					R.string.action_play
				else R.string.action_pause
			)
			AnimatedContent(playerState.isLoading) { isBuffering ->
				if (!isBuffering) {
					Icon(
						painter = painter,
						contentDescription = description,
						modifier = Modifier.size(64.dp)
					)
				} else {
					CircularProgressIndicator(
						Modifier.size(48.dp),
						color = MaterialTheme.colorScheme.onSurface,
						trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .3f),
					)
				}
			}
		}
		IconButton(
			modifier = Modifier.size(72.dp),
			onClick = {
				player.next()
			},
			enabled = enabled,
		) {
			Icon(
				imageVector = Icons.Filled.SkipNext,
				contentDescription = stringResource(R.string.action_next_song),
				modifier = Modifier.size(44.dp)
			)
		}
	}
}

/** iOS 26 bottom row: lyrics, shuffle, repeat, queue (no AirPlay on Android). */
@Composable
fun NowPlayingActionsRow() {
	val backStack = LocalNavStack.current
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()
	val enabled = playerState.currentSong != null
	val tint = MaterialTheme.colorScheme.onSurfaceVariant

	val repeatStateDescription = stringResource(when (playerState.repeatMode) {
		1 -> R.string.info_repeat_one
		2 -> R.string.info_repeat_all
		else -> R.string.info_repeat_off
	})

	Row(
		modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceEvenly,
		verticalAlignment = Alignment.CenterVertically
	) {
		IconButton(onClick = dropUnlessResumed { backStack.add(Screen.Lyrics) }) {
			Icon(Icons.Outlined.Lyrics, stringResource(R.string.action_lyrics), tint = tint)
		}
		IconButton(
			modifier = Modifier.semantics {
				toggleableState = ToggleableState(playerState.isShuffleEnabled)
			},
			onClick = {
				player.toggleShuffle()
			},
			enabled = enabled,
		) {
			Icon(
				imageVector = if (playerState.isShuffleEnabled)
					Icons.Filled.ShuffleOn
				else Icons.Outlined.Shuffle,
				contentDescription = stringResource(R.string.action_shuffle),
				tint = tint
			)
		}
		IconButton(
			modifier = Modifier.semantics {
				stateDescription = repeatStateDescription
			},
			onClick = {
				player.toggleRepeat()
			},
			enabled = enabled,
		) {
			Icon(
				imageVector = when (playerState.repeatMode) {
					1 -> Icons.Filled.RepeatOneOn
					2 -> Icons.Filled.RepeatOn
					else -> Icons.Outlined.Repeat
				},
				contentDescription = null,
				tint = tint
			)
		}
		IconButton(onClick = dropUnlessResumed { backStack.add(Screen.Queue) }) {
			Icon(Icons.Outlined.List, stringResource(R.string.action_queue), tint = tint)
		}
	}
}
