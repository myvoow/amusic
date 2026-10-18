/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.component.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.kyant.backdrop.Backdrop
import com.kyant.capsule.ContinuousRoundedRectangle
import org.koin.compose.koinInject
import paige.navic.R
import paige.navic.di.LocalNavStack
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.model.settings.MiniPlayerProgressStyle
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.component.common.MarqueeText
import paige.navic.ui.icons.Icons
import paige.navic.ui.icons.filled.Note
import paige.navic.ui.icons.filled.SkipNext
import paige.navic.ui.navigation.Screen
import paige.navic.ui.util.playPauseIconPainter
import coil3.compose.LocalPlatformContext as LocalCoilPlatformContext

/** iOS 26 mini player: a glass capsule. [compact] drops the skip button (collapsed tab bar). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
	backdrop: Backdrop,
	modifier: Modifier = Modifier,
	compact: Boolean = false
) {
	val player = koinInject<MediaPlayerViewModel>()
	val preferenceManager = koinInject<PreferenceManager>()
	val backStack = LocalNavStack.current
	val haptics = LocalHapticFeedback.current

	val playerState by player.uiState.collectAsState()
	val song = playerState.currentSong

	val coilPlatformContext = LocalCoilPlatformContext.current
	val imageLoader = koinInject<ImageLoader>()
	val sessionManager = koinInject<SessionManager>()
	val model = remember(song?.coverArtId) {
		ImageRequest.Builder(coilPlatformContext)
			.data(song?.coverArtId?.let { sessionManager.getCoverArtUrl(it) })
			.memoryCacheKey(song?.coverArtId)
			.diskCacheKey(song?.coverArtId)
			.diskCachePolicy(CachePolicy.ENABLED)
			.memoryCachePolicy(CachePolicy.ENABLED)
			.build()
	}

	val coverRounding by animateDpAsState(
		if (playerState.isLoading)
			46.dp
		else 8.dp
	)

	val onClick = dropUnlessResumed {
		if (!backStack.contains(Screen.NowPlaying)) {
			backStack.add(Screen.NowPlaying)
		}
	}

	val hasSong = song != null

	AnimatedVisibility(
		visible = hasSong || !preferenceManager.hideIfIdle,
		modifier = modifier
	) {
		Swiper(
			onSwipeLeft = {
				if (hasSong) player.next()
			},
			onSwipeRight = {
				if (hasSong) player.previous()
			},
			swipeLeftAccessibilityLabel = stringResource(R.string.action_previous_song),
			swipeRightAccessibilityLabel = stringResource(R.string.action_next_song),
			enabled = hasSong
		) {
			Box(Modifier.glass(backdrop)) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.height(56.dp)
						.pointerInput(hasSong) {
							if (!hasSong) return@pointerInput
							var totalDrag = 0f
							detectVerticalDragGestures(
								onVerticalDrag = { _, dragAmount ->
									totalDrag += dragAmount
								},
								onDragEnd = {
									if (totalDrag < -150f) {
										onClick()
									}
									totalDrag = 0f
								}
							)
						}
						.combinedClickable(
							onClick = onClick,
							onLongClick = {
								haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
								onClick()
							}
						)
						.padding(start = 8.dp, end = 4.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp)
				) {
					Box(contentAlignment = Alignment.Center) {
						AsyncImage(
							model = model,
							imageLoader = imageLoader,
							contentDescription = null,
							contentScale = ContentScale.Fit,
							modifier = Modifier
								.size(40.dp)
								.padding(if (playerState.isLoading) 6.dp else 0.dp)
								.clip(ContinuousRoundedRectangle(coverRounding))
								.background(MaterialTheme.colorScheme.surfaceVariant)
						)
						if (song?.coverArtId == null) {
							Icon(
								imageVector = Icons.Filled.Note,
								contentDescription = null,
								tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
							)
						}
						if (playerState.isLoading) {
							CircularProgressIndicator(
								Modifier.matchParentSize(),
								trackColor = MaterialTheme.colorScheme.primaryContainer
							)
						}
					}
					Column(Modifier.weight(1f)) {
						MarqueeText(
							text = song?.title ?: stringResource(R.string.info_not_playing),
							style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
						)
						song?.let {
							MarqueeText(
								text = it.artistName ?: "[unknown artist]",
								style = MaterialTheme.typography.bodySmall.copy(
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
							)
						}
					}
					IconButton(
						onClick = {
							if (playerState.isPaused) {
								player.resume()
							} else {
								player.pause()
							}
						},
						enabled = hasSong
					) {
						val painter = playPauseIconPainter(playerState.isPaused)
						val description = stringResource(
							if (playerState.isPaused)
								R.string.action_play
							else R.string.action_pause
						)
						Icon(
							painter = painter,
							contentDescription = description,
							modifier = Modifier.size(28.dp)
						)
					}
					if (!compact) {
						IconButton(
							onClick = {
								player.next()
							},
							enabled = hasSong
						) {
							Icon(
								imageVector = Icons.Filled.SkipNext,
								contentDescription = stringResource(R.string.action_next_song),
								modifier = Modifier.size(28.dp)
							)
						}
					}
				}
				if (preferenceManager.miniPlayerProgressStyle != MiniPlayerProgressStyle.Hidden) {
					var dragging by remember { mutableStateOf(false) }
					val alpha by animateFloatAsState(
						if (dragging) 1f else .7f
					)
					val progress by animateFloatAsState(
						playerState.progress.coerceIn(0f, 1f)
					)
					Box(
						modifier = Modifier
							.matchParentSize()
							.padding(horizontal = 24.dp),
						contentAlignment = Alignment.BottomStart
					) {
						Box(
							Modifier
								.clip(CircleShape)
								.background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
								.fillMaxWidth(if (song != null) progress else 0f)
								.height(2.dp)
						)
						Box(
							Modifier
								.fillMaxWidth()
								.height(14.dp)
								.then(
									if (song != null
										&& preferenceManager.miniPlayerProgressStyle == MiniPlayerProgressStyle.Seekable
									)
										Modifier.pointerInput(Unit) {
											detectDragGestures(
												onDragStart = {
													dragging = true
													haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
												},
												onDragEnd = {
													dragging = false
													haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
												}
											) { change, _ ->
												player.seek(
													(change.position.x / size.width.toFloat()).coerceIn(
														0f,
														1f
													)
												)
												change.consume()
											}
										}
									else Modifier
								)
						)
					}
				}
			}
		}
	}
}
