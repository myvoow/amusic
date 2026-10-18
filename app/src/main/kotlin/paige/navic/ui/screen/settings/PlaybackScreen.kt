/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.toImmutableList
import org.koin.compose.koinInject
import paige.navic.R
import paige.navic.exoplayer.AutoMixState
import androidx.compose.material3.MaterialTheme
import paige.navic.exoplayer.AutoMixTuning
import paige.navic.exoplayer.TrackTransition
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import paige.navic.di.LocalNavStack
import paige.navic.di.LocalSizeClass
import paige.navic.discord.ExoDiscordIntegration
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.model.settings.ExplicitContentPlayback
import paige.navic.ui.component.common.SegmentedListItemDefaults
import paige.navic.ui.component.layout.NestedTopBar
import paige.navic.ui.component.layout.NestedTopBarDefaults
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screen.settings.component.SettingsChoiceItem
import paige.navic.ui.screen.settings.component.SettingsGroup
import paige.navic.ui.screen.settings.component.SettingsGroupDefaults
import paige.navic.ui.screen.settings.component.SettingsInputItem
import paige.navic.ui.screen.settings.component.SettingsNavItem
import paige.navic.ui.screen.settings.component.SettingsSliderItem
import paige.navic.ui.screen.settings.component.SettingsToggleItem
import kotlin.math.roundToInt

@Composable
fun SettingsPlaybackScreen() {
	val backStack = LocalNavStack.current
	val sizeClass = LocalSizeClass.current
	val hideBack = sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
	val preferenceManager = koinInject<PreferenceManager>()

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(R.string.title_playback)) },
				navigationAction = {
					if (!hideBack) {
						NestedTopBarDefaults.NavigationAction()
					}
				}
			)
		}
	) { innerPadding ->
		CompositionLocalProvider(
			LocalMinimumInteractiveComponentSize provides 0.dp
		) {
			Column(
				modifier = Modifier
					.padding(innerPadding)
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 16.dp),
				verticalArrangement = Arrangement.spacedBy(SettingsGroupDefaults.GapBetweenGroups)
			) {
				SettingsGroup {
					SettingsNavItem(
						onClick = dropUnlessResumed { backStack.add(Screen.Settings.StreamingQuality) },
						content = { Text(stringResource(R.string.title_streaming_quality)) },
						supportingContent = { Text(stringResource(R.string.subtitle_streaming_quality)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 6)
					)
					SettingsChoiceItem(
						choices = ExplicitContentPlayback.entries.toImmutableList(),
						selectedChoice = preferenceManager.explicitContentPlayback,
						onChoiceSelected = { preferenceManager.explicitContentPlayback = it },
						content = { Text(stringResource(R.string.option_explicit_playback)) },
						label = { stringResource(it.displayName) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 6)
					)
					SettingsNavItem(
						onClick = dropUnlessResumed { backStack.add(Screen.Settings.Effects) },
						content = { Text(stringResource(R.string.title_audio_effects)) },
						supportingContent = { Text(stringResource(R.string.subtitle_audio_effects)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(
							index = 2,
							count = 6
						)
					)
					SettingsToggleItem(
						checked = preferenceManager.autoFillQueue,
						onCheckedChange = { preferenceManager.autoFillQueue = it },
						content = { Text(stringResource(R.string.option_auto_fill_queue)) },
						supportingContent = { Text(stringResource(R.string.subtitle_auto_fill_queue)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(
							index = 3,
							count = 6
						)
					)
					SettingsChoiceItem(
						choices = TrackTransition.entries.toImmutableList(),
						selectedChoice = preferenceManager.trackTransition,
						onChoiceSelected = { preferenceManager.trackTransition = it },
						content = { Text(stringResource(R.string.option_track_transition)) },
						label = { stringResource(it.label) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 4, count = 6)
					)
					val context = LocalContext.current
					val scope = rememberCoroutineScope()
					SettingsInputItem(
						value = preferenceManager.autoMixUrl,
						onValueChanged = { url ->
							preferenceManager.autoMixUrl = url.trim()
							scope.launch(Dispatchers.IO) { AutoMixState.load(context.filesDir, url.trim()) }
						},
						defaultValue = "",
						content = { Text(stringResource(R.string.option_automix_url)) },
						supportingContent = { Text(stringResource(R.string.subtitle_automix_url)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 5, count = 6)
					)
				}

				AutoMixSettings(preferenceManager)

				SettingsGroup(title = { Text(stringResource(R.string.title_behaviour)) }) {
					val enableDiscordIntegration = preferenceManager.enableDiscordIntegration
					val enableScrobbling = preferenceManager.enableScrobbling
					var count = 2
					if (enableDiscordIntegration) count += 2
					if (enableScrobbling) count += 2

					SettingsToggleItem(
						checked = enableDiscordIntegration,
						onCheckedChange = { preferenceManager.enableDiscordIntegration = it },
						content = { Text(stringResource(R.string.option_enable_discord_integration)) },
						supportingContent = { Text(stringResource(R.string.subtitle_enable_discord_integration)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = count)
					)

					AnimatedVisibility(visible = enableDiscordIntegration) {
						SettingsInputItem(
							value = preferenceManager.discordAppId,
							onValueChanged = { preferenceManager.discordAppId = it.trim() },
							defaultValue = ExoDiscordIntegration.DISCORD_APPLICATION_ID,
							onValidate = { it.isNotBlank() && it.toLongOrNull() != null },
							content = { Text(stringResource(R.string.action_edit_discord_app_id)) },
							shapes = SegmentedListItemDefaults.segmentedShapes(
								index = 1,
								count = count
							)
						)
					}

					AnimatedVisibility(visible = enableDiscordIntegration) {
						SettingsToggleItem(
							checked = preferenceManager.disableRpcWhenPowerSaving,
							onCheckedChange = { preferenceManager.disableRpcWhenPowerSaving = it },
							content = { Text(stringResource(R.string.option_disable_rpc_when_power_saving)) },
							shapes = SegmentedListItemDefaults.segmentedShapes(
								index = 2,
								count = count
							)
						)
					}

					SettingsToggleItem(
						checked = enableScrobbling,
						onCheckedChange = { preferenceManager.enableScrobbling = it },
						content = { Text(stringResource(R.string.option_enable_scrobbling)) },
						supportingContent = { Text(stringResource(R.string.subtitle_enable_scrobbling)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(
							index = if (enableDiscordIntegration) 3 else 1,
							count = count
						)
					)

					AnimatedVisibility(visible = enableScrobbling) {
						SettingsSliderItem(
							value = preferenceManager.scrobblePercentage,
							valueRange = 0f..1f,
							onValueChange = { preferenceManager.scrobblePercentage = it },
							trailingContent = { Text("${(preferenceManager.scrobblePercentage * 100).roundToInt()}%") },
							content = { Text(stringResource(R.string.option_scrobble_percentage)) },
							shapes = SegmentedListItemDefaults.segmentedShapes(
								index = if (enableDiscordIntegration) 4 else 2,
								count = count
							)
						)
					}

					AnimatedVisibility(visible = enableScrobbling) {

						SettingsSliderItem(
							value = preferenceManager.minDurationToScrobble,
							valueRange = 0f..60f,
							onValueChange = { preferenceManager.minDurationToScrobble = it },
							trailingContent = { Text("${preferenceManager.minDurationToScrobble.toInt()}s") },
							content = { Text(stringResource(R.string.option_min_duration_to_scrobble)) },
							shapes = SegmentedListItemDefaults.segmentedShapes(
								index = if (enableDiscordIntegration) 5 else 3,
								count = count
							)
						)
					}
				}
			}
		}
	}
}

val TrackTransition.label
	get() = when (this) {
		TrackTransition.AutoMix -> R.string.option_track_transition_automix
		TrackTransition.Crossfade -> R.string.option_track_transition_crossfade
		TrackTransition.None -> R.string.option_track_transition_none
	}

/** AutoMix and crossfade tuning, shown for the chosen transition mode only. */
@Composable
private fun AutoMixSettings(preferenceManager: PreferenceManager) {
	val mode = preferenceManager.trackTransition
	val t = preferenceManager.autoMixTuning
	val set = { new: AutoMixTuning -> preferenceManager.autoMixTuning = new }
	val seconds = { v: Float -> "%.1f s".format(v) }
	val whole = { v: Float -> v.roundToInt().toString() }
	when (mode) {
		TrackTransition.None -> Unit
		TrackTransition.Crossfade -> SettingsGroup {
			TuningSlider(R.string.option_crossfade_seconds, t.crossfadeSeconds, 1f..12f, 10, seconds, 0, 1) {
				set(t.copy(crossfadeSeconds = it))
			}
		}
		TrackTransition.AutoMix -> {
			SettingsGroup(title = { Text(stringResource(R.string.title_automix_tempo)) }) {
				TuningSlider(R.string.option_automix_max_tempo_gap, t.maxTempoGapPercent, 2f..20f, 17, { "${it.roundToInt()} %" }, 0, 6, R.string.subtitle_automix_max_tempo_gap) {
					set(t.copy(maxTempoGapPercent = it))
				}
				TuningSlider(R.string.option_automix_min_confidence, t.minBpmConfidence, 0f..1f, 19, { "${(it * 100).roundToInt()} %" }, 1, 6) {
					set(t.copy(minBpmConfidence = it))
				}
				TuningToggle(R.string.option_automix_require_steady, t.requireSteadyTempo, 2, 6) { set(t.copy(requireSteadyTempo = it)) }
				TuningToggle(R.string.option_automix_use_keys, t.useKeys, 3, 6, R.string.subtitle_automix_use_keys) { set(t.copy(useKeys = it)) }
				TuningSlider(R.string.option_automix_compatible_bars, t.compatibleKeyBars.toFloat(), 1f..16f, 14, whole, 4, 6) {
					set(t.copy(compatibleKeyBars = it.roundToInt()))
				}
				TuningSlider(R.string.option_automix_distant_bars, t.distantKeyBars.toFloat(), 1f..16f, 14, whole, 5, 6) {
					set(t.copy(distantKeyBars = it.roundToInt()))
				}
			}
			SettingsGroup(title = { Text(stringResource(R.string.title_automix_overlap)) }) {
				TuningSlider(R.string.option_automix_min_overlap, t.minOverlapSeconds, 2f..16f, 13, seconds, 0, 7) {
					set(t.copy(minOverlapSeconds = it))
				}
				TuningSlider(R.string.option_automix_max_overlap, t.maxOverlapSeconds, 6f..32f, 25, seconds, 1, 7) {
					set(t.copy(maxOverlapSeconds = it))
				}
				TuningSlider(R.string.option_automix_exit_early, t.exitEarlyBars.toFloat(), 0f..32f, 7, whole, 2, 7, R.string.subtitle_automix_exit_early) {
					set(t.copy(exitEarlyBars = it.roundToInt()))
				}
				TuningSlider(R.string.option_automix_ramp, t.rampSeconds, 0f..16f, 15, seconds, 3, 7) {
					set(t.copy(rampSeconds = it))
				}
				TuningSlider(R.string.option_automix_short_fade, t.shortFadeSeconds, 0f..4f, 7, seconds, 4, 7) {
					set(t.copy(shortFadeSeconds = it))
				}
				TuningToggle(R.string.option_automix_equal_power, t.equalPowerFade, 5, 7, R.string.subtitle_automix_equal_power) {
					set(t.copy(equalPowerFade = it))
				}
				TuningSlider(R.string.option_automix_prepare, t.prepareAheadSeconds, 10f..60f, 9, seconds, 6, 7) {
					set(t.copy(prepareAheadSeconds = it))
				}
			}
			SettingsGroup(title = { Text(stringResource(R.string.title_automix_other)) }) {
				TuningToggle(R.string.option_automix_albums, t.mixAlbumsInOrder, 0, 5) { set(t.copy(mixAlbumsInOrder = it)) }
				TuningToggle(R.string.option_automix_exclude_live, t.excludeLive, 1, 5) { set(t.copy(excludeLive = it)) }
				SettingsInputItem(
					value = t.exclusions,
					onValueChanged = { set(t.copy(exclusions = it)) },
					defaultValue = AutoMixTuning().exclusions,
					content = { Text(stringResource(R.string.option_automix_exclusions)) },
					supportingContent = { Text(stringResource(R.string.subtitle_automix_exclusions)) },
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = 5)
				)
				TuningToggle(R.string.option_automix_show_mixing, t.showMixingLabel, 3, 5) { set(t.copy(showMixingLabel = it)) }
				SettingsNavItem(
					onClick = { set(AutoMixTuning()) },
					content = { Text(stringResource(R.string.action_automix_reset)) },
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 4, count = 5)
				)
			}
		}
	}
}

@Composable
private fun TuningSlider(
	label: Int,
	value: Float,
	range: ClosedFloatingPointRange<Float>,
	steps: Int,
	format: (Float) -> String,
	index: Int,
	count: Int,
	subtitle: Int? = null,
	onChange: (Float) -> Unit
) = SettingsSliderItem(
	value = value,
	valueRange = range,
	steps = steps,
	onValueChange = onChange,
	trailingContent = { Text(format(value)) },
	content = {
		Column {
			Text(stringResource(label))
			subtitle?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
		}
	},
	shapes = SegmentedListItemDefaults.segmentedShapes(index = index, count = count)
)

@Composable
private fun TuningToggle(
	label: Int,
	checked: Boolean,
	index: Int,
	count: Int,
	subtitle: Int? = null,
	onChange: (Boolean) -> Unit
) = SettingsToggleItem(
	checked = checked,
	onCheckedChange = onChange,
	content = { Text(stringResource(label)) },
	supportingContent = subtitle?.let { { Text(stringResource(it)) } },
	shapes = SegmentedListItemDefaults.segmentedShapes(index = index, count = count)
)
