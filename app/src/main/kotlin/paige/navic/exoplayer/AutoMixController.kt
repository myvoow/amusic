/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.exoplayer

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.serialization.json.Json
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.model.DomainReplayGain
import paige.navic.domain.model.settings.ReplayGainMode
import paige.navic.exoplayer.impl.ExoAudioGainProcessor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Plays [planTransition]'s plan with a second ExoPlayer over the end of the current song.
 *
 * The main player keeps the queue, the session, scrobbles and the UI. The mix player plays the
 * next song (tempo-stretched, pitch kept) while the main one fades out; afterwards the main player
 * jumps to the next song at the mix player's position, catches up silently, and takes over.
 * Swapping the session's player instead was rejected: ExoPlayer can't hand over its shuffle
 * order, and the scrobbler, Discord and the shuffle/repeat buttons hold the main player.
 *
 * Debug log: adb shell setprop log.tag.AutoMix DEBUG
 */
@UnstableApi
class AutoMixController(
	private val stateHolder: ExoStateHolder,
	private val preferenceManager: PreferenceManager
) : Player.Listener {
	private enum class Phase { Idle, Prepared, Overlap, Handoff }

	private val main: ExoPlayer get() = stateHolder.playerInstance
	private val handler = Handler(Looper.getMainLooper())
	private val tick = Runnable { step(); schedule() }

	private var mix: ExoPlayer? = null
	private var phase = Phase.Idle
	private var plan: TransitionPlan = TransitionPlan.None
	private var planKey: String? = null
	private var doneKey: String? = null
	private var nextIndex = C.INDEX_UNSET
	private var nextId: String? = null
	private var phaseStart = 0L
	private var ticks = 0
	private var synced = 0
	private var lastReport = -1
	private var selfSeek = false
	private var ownsMainSpeed = false

	private val debug get() = Log.isLoggable(TAG, Log.DEBUG)

	override fun onIsPlayingChanged(isPlaying: Boolean) {
		if (isPlaying) schedule()
	}

	override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
		// pause / resume / sleep timer during a mix: both players follow
		if (phase == Phase.Overlap || phase == Phase.Handoff) mix?.playWhenReady = playWhenReady
	}

	override fun onPositionDiscontinuity(
		oldPosition: Player.PositionInfo,
		newPosition: Player.PositionInfo,
		reason: Int
	) {
		when {
			// manual skip or seek: drop the mix and play normally, like Apple Music
			reason == Player.DISCONTINUITY_REASON_SEEK && !selfSeek -> cancel("seek or skip")
			// the main player ran off the end of A during the overlap
			reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION && phase == Phase.Overlap -> startHandoff()
		}
	}

	fun release() {
		handler.removeCallbacks(tick)
		cancel("service stopped")
	}

	private fun schedule() {
		handler.removeCallbacks(tick)
		if (!main.isPlaying && mix?.isPlaying != true) return // paused: resumes in onIsPlayingChanged
		handler.postDelayed(tick, if (phase == Phase.Idle) IDLE_TICK_MS else TICK_MS)
	}

	private fun step() {
		ticks++
		when (phase) {
			Phase.Idle, Phase.Prepared -> watch()
			Phase.Overlap -> overlap()
			Phase.Handoff -> handoff()
		}
	}

	private fun watch() {
		val plan = currentPlan()
		if (plan == TransitionPlan.None) return cancel(null)
		val posA = main.currentPosition / 1000.0
		if (phase == Phase.Idle && posA >= plan.startOffsetInA - PREPARE_AHEAD_SECONDS) prepare(plan)
		if (phase == Phase.Prepared && posA >= plan.startOffsetInA) {
			val m = mix ?: return
			if (m.playbackState != Player.STATE_READY) {
				doneKey = planKey // network too slow: this pair plays normally
				return cancel("next song not buffered in time")
			}
			m.playWhenReady = main.playWhenReady
			phase = Phase.Overlap
			lastReport = -1
			AutoMixState.isMixing.value = plan is TransitionPlan.Mix
			if (debug) Log.d(TAG, "start $plan at A=${main.currentPosition} ms")
		}
	}

	private fun currentPlan(): TransitionPlan {
		val next = main.nextMediaItemIndex
		val a = main.currentMediaItem
		val b = if (next != C.INDEX_UNSET) main.getMediaItemAt(next) else null
		val mode = preferenceManager.trackTransition
		val key = "$mode|${AutoMixState.tracks.value.size}|${main.currentMediaItemIndex}:${a?.mediaId}>$next:${b?.mediaId}"
		if (key != planKey) {
			cancel("queue changed")
			planKey = key
			nextIndex = next
			nextId = b?.mediaId
			plan = if (a == null || b == null || main.playbackParameters.speed != 1f) TransitionPlan.None
			else planTransition(
				a.mixTrack(main.duration.takeIf { it > 0 } ?: a.mediaMetadata.durationMs),
				b.mixTrack(b.mediaMetadata.durationMs),
				MixContext(mode, repeatOne = main.repeatMode == Player.REPEAT_MODE_ONE)
			)
		}
		return if (key == doneKey) TransitionPlan.None else plan
	}

	private fun prepare(plan: TransitionPlan) {
		val item = main.getMediaItemAt(nextIndex)
		// ReplayGain on both players, or a louder song would bury the other during the overlap
		val gain = ExoAudioGainProcessor().apply {
			rgAmpValue = preferenceManager.rgAmpGain
			ampValue = preferenceManager.ampGain
			setReplayGainMetadata(item.replayGain())
			// ponytail: Dynamic ReplayGain is treated as Track for the incoming song
			applyGainMode(preferenceManager.replayGainMode.let { if (it == ReplayGainMode.Dynamic) ReplayGainMode.Track else it })
		}
		mix = stateHolder.createMixPlayer(gain).apply {
			volume = 0f
			playbackParameters = PlaybackParameters(plan.stretchRatio.toFloat(), 1f)
			setMediaItem(item, (plan.startOffsetInB * 1000).toLong())
			prepare()
		}
		phase = Phase.Prepared
		if (debug) Log.d(TAG, "prepared ${item.mediaId} for $plan")
	}

	private fun overlap() {
		val m = mix ?: return cancel("mix player gone")
		if (main.nextMediaItemIndex != nextIndex || main.getMediaItemAt(nextIndex).mediaId != nextId) {
			return cancel("next song changed during the overlap")
		}
		val posA = main.currentPosition / 1000.0
		val t = if (plan.overlapSeconds > 0) ((posA - plan.startOffsetInA) / plan.overlapSeconds).coerceIn(0.0, 1.0) else 1.0
		// equal power: the two songs keep a steady loudness, A fades out while B fades in
		main.volume = cos(t * PI / 2).toFloat()
		m.volume = sin(t * PI / 2).toFloat()
		if (plan is TransitionPlan.Mix) {
			val expectedB = plan.startOffsetInB + (posA - plan.startOffsetInA) * plan.stretchRatio
			val err = follow(m, expectedB, plan.stretchRatio.toFloat(), AUDIBLE_GAIN, AUDIBLE_MAX_NUDGE)
			val report = (t * 10).toInt()
			if (debug && report != lastReport && report in setOf(1, 5, 9)) {
				lastReport = report
				val line = "overlap ${report * 10}%: A=${main.currentPosition} ms x${main.playbackParameters.speed} " +
					"vol ${"%.2f".format(main.volume)}, B=${m.currentPosition} ms x${m.playbackParameters.speed} " +
					"vol ${"%.2f".format(m.volume)}, beat error ${err.toInt()} ms"
				if (abs(err) > BEAT_ERROR_WARN_MS) Log.w(TAG, line) else Log.d(TAG, line)
			}
		}
		if (t >= 1.0) startHandoff()
	}

	private fun startHandoff() {
		val m = mix ?: return cancel("mix player gone")
		if (nextIndex >= main.mediaItemCount || main.getMediaItemAt(nextIndex).mediaId != nextId) {
			return cancel("next song left the queue")
		}
		main.volume = 0f
		m.volume = 1f
		AutoMixState.isMixing.value = false
		// the main player takes B over silently; the lead covers its rebuffering, follow() the rest
		ownsMainSpeed = true
		main.playbackParameters = m.playbackParameters
		selfSeek = true
		main.seekTo(nextIndex, m.currentPosition + HANDOFF_LEAD_MS)
		selfSeek = false
		phase = Phase.Handoff
		phaseStart = SystemClock.elapsedRealtime()
		synced = 0
	}

	private fun handoff() {
		val m = mix ?: return cancel("mix player gone")
		val elapsed = SystemClock.elapsedRealtime() - phaseStart
		// back to B's own tempo over a few bars once A is gone, no audible tempo jump
		val r = (elapsed / (RAMP_SECONDS * 1000)).coerceIn(0.0, 1.0)
		val speed = (plan.stretchRatio + (1 - plan.stretchRatio) * r).toFloat()
		if (abs(m.playbackParameters.speed - speed) > 0.001f) m.playbackParameters = PlaybackParameters(speed, 1f)
		var err = Double.NaN
		if (main.isPlaying) {
			err = follow(main, m.currentPosition / 1000.0, speed, MUTED_GAIN, MUTED_MAX_NUDGE)
			synced = if (r >= 1 && abs(err) < SYNC_TOLERANCE_MS) synced + 1 else 0
		}
		val timedOut = elapsed > (RAMP_SECONDS * 1000 + HANDOFF_TIMEOUT_MS)
		if (synced >= 3 || timedOut) {
			if (debug) Log.d(TAG, "handoff ${if (timedOut) "TIMED OUT" else "ok"}: main=${main.currentPosition} ms, " +
				"mix=${m.currentPosition} ms, error ${err.toInt()} ms")
			if (timedOut && debug) Log.w(TAG, "handoff not synced, hard switch")
			main.volume = 1f
			cancel(null)
		}
	}

	/** Nudges [p]'s speed around [base] so its position meets [expectedSeconds]; returns the error in ms. */
	private fun follow(p: ExoPlayer, expectedSeconds: Double, base: Float, gain: Double, maxNudge: Double): Double {
		val errMs = p.currentPosition - expectedSeconds * 1000
		// ponytail: plain proportional catch-up, 5 times a second; a PLL if this ever oscillates
		if (ticks % CORRECTION_EVERY_TICKS == 0) {
			val nudge = (errMs / 1000 * gain).coerceIn(-maxNudge, maxNudge)
			val speed = (base * (1 - nudge)).toFloat()
			if (abs(p.playbackParameters.speed - speed) > 0.002f) p.playbackParameters = PlaybackParameters(speed, 1f)
		}
		return errMs
	}

	private fun cancel(reason: String?) {
		if (reason != null && phase != Phase.Idle && debug) Log.d(TAG, "cancelled: $reason")
		mix?.release()
		mix = null
		if (phase != Phase.Idle) main.volume = 1f
		if (ownsMainSpeed) main.playbackParameters = PlaybackParameters.DEFAULT
		ownsMainSpeed = false
		phase = Phase.Idle
		AutoMixState.isMixing.value = false
	}

	private fun MediaItem.mixTrack(durationMs: Long?) = MixTrack(
		analysis = AutoMixState.tracks.value[mediaId],
		durationSeconds = (durationMs ?: 0L) / 1000.0,
		albumId = mediaMetadata.extras?.getString(EXTRA_ALBUM_ID),
		albumTitle = mediaMetadata.albumTitle?.toString(),
		discNumber = mediaMetadata.discNumber,
		trackNumber = mediaMetadata.trackNumber,
		genre = mediaMetadata.genre?.toString()
	)

	private fun MediaItem.replayGain(): DomainReplayGain? = mediaMetadata.extras?.getString(EXTRA_REPLAY_GAIN)
		?.let { runCatching { Json.decodeFromString<DomainReplayGain>(it) }.getOrNull() }

	companion object {
		private const val TAG = "AutoMix"
		const val EXTRA_ALBUM_ID = "navic.albumId"
		const val EXTRA_REPLAY_GAIN = "navic.replayGain"

		// starting values, tune by ear
		private const val PREPARE_AHEAD_SECONDS = 30.0
		private const val RAMP_SECONDS = 8.0 // B's speed back to 1.0 after A is gone, about 4 bars
		private const val HANDOFF_LEAD_MS = 600L
		private const val SYNC_TOLERANCE_MS = 15.0
		private const val BEAT_ERROR_WARN_MS = 30.0
		private const val HANDOFF_TIMEOUT_MS = 12_000L
		private const val AUDIBLE_GAIN = 1.0 // 30 ms late -> 3 % faster for a moment
		private const val AUDIBLE_MAX_NUDGE = 0.03
		private const val MUTED_GAIN = 3.0
		private const val MUTED_MAX_NUDGE = 0.5
		private const val TICK_MS = 25L
		private const val IDLE_TICK_MS = 500L
		private const val CORRECTION_EVERY_TICKS = 8
	}
}
