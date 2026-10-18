/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.exoplayer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

// AutoMix: our own take on Apple Music's DJ-style transitions, built from its documented
// behaviour only (Apple published no algorithm). Pure Kotlin, no Android: see AutoMixTest.

/** One entry of automix.json, produced by automix-server/analyze.py. Times in seconds. */
@Serializable
data class AutoMixAnalysis(
	val bpm: Double,
	val bpmConfidence: Double,
	val bpmFactor: Double = 1.0,
	val firstBeat: Double,
	val steady: Boolean,
	val key: String,
	val keyConfidence: Double = 0.0,
	val cueIn: Double,
	val cueOut: Double,
	val end: Double,
	val duration: Double
)

@Serializable
data class AutoMixFile(val version: Int, val tracks: Map<String, AutoMixAnalysis>)

enum class TrackTransition { AutoMix, Crossfade, None }

object AutoMixState {
	/** automix.json entries by Navidrome song id. */
	val tracks = MutableStateFlow<Map<String, AutoMixAnalysis>>(emptyMap())

	/** True while two songs are mixed (the now playing screen shows "Mixing"). */
	val isMixing = MutableStateFlow(false)

	private val json = Json { ignoreUnknownKeys = true }

	/**
	 * One request for the whole library, ETag-aware; falls back to the copy cached in [dir] when
	 * the server can't be reached (it is LAN only). Empty [url] turns AutoMix off.
	 */
	fun load(dir: File, url: String) {
		val cache = File(dir, "automix.json")
		val etag = File(dir, "automix.etag")
		if (url.isBlank()) {
			tracks.value = emptyMap()
			return
		}
		runCatching {
			val c = URI(url).toURL().openConnection() as HttpURLConnection
			c.connectTimeout = 5_000
			c.readTimeout = 30_000
			if (cache.exists() && etag.exists()) c.setRequestProperty("If-None-Match", etag.readText())
			if (c.responseCode == HttpURLConnection.HTTP_OK) {
				val tmp = File(dir, "automix.json.tmp")
				c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
				tmp.renameTo(cache)
				c.getHeaderField("ETag")?.let { etag.writeText(it) } ?: etag.delete()
			}
			c.disconnect()
		}
		tracks.value = runCatching { json.decodeFromString<AutoMixFile>(cache.readText()).tracks }.getOrDefault(emptyMap())
	}
}

data class MixTrack(
	val analysis: AutoMixAnalysis?,
	val durationSeconds: Double,
	val albumId: String? = null,
	val albumTitle: String? = null,
	val discNumber: Int? = null,
	val trackNumber: Int? = null,
	val genre: String? = null
)

data class MixContext(
	val mode: TrackTransition,
	val hasNext: Boolean = true,
	val repeatOne: Boolean = false
)

/** Where B starts in A (A's own time), where in B it starts, for how long both play, B's speed. */
sealed interface TransitionPlan {
	val overlapSeconds: Double
	val stretchRatio: Double
	val startOffsetInA: Double
	val startOffsetInB: Double

	data class Mix(
		override val overlapSeconds: Double,
		override val stretchRatio: Double,
		override val startOffsetInA: Double,
		override val startOffsetInB: Double
	) : TransitionPlan

	/** Back to back with the silences trimmed. */
	data class CleanCut(override val startOffsetInA: Double, override val startOffsetInB: Double) : TransitionPlan {
		override val overlapSeconds get() = 0.0
		override val stretchRatio get() = 1.0
	}

	data class ShortFade(
		val seconds: Double,
		override val startOffsetInA: Double,
		override val startOffsetInB: Double
	) : TransitionPlan {
		override val overlapSeconds get() = seconds
		override val stretchRatio get() = 1.0
	}

	data object None : TransitionPlan {
		override val overlapSeconds get() = 0.0
		override val stretchRatio get() = 1.0
		override val startOffsetInA get() = 0.0
		override val startOffsetInB get() = 0.0
	}
}

// Starting values, to be tuned by ear.
const val MIN_BPM_CONFIDENCE = 0.2 // ≈ 1.06 on Essentia's 0..5.32 multifeature scale
const val MAX_TEMPO_GAP = 0.08
const val MIN_OVERLAP_SECONDS = 6.0
const val MAX_OVERLAP_SECONDS = 16.0
const val COMPATIBLE_KEY_BARS = 8
const val DISTANT_KEY_BARS = 4
const val SHORT_FADE_SECONDS = 1.0 // personal choice, not an Apple value
const val CROSSFADE_SECONDS = 6.0

/** Genres or album titles that are never beat-matched (accents and case ignored). */
val AUTOMIX_EXCLUSIONS = listOf(
	"classique", "classical", "classica", "klassiek", "opera", "musica de concerto", // the library tags classical these ways
	"spoken word", "podcast", "livre audio", "audiobook"
)
private val LIVE = Regex("\\blive\\b")

fun planTransition(a: MixTrack, b: MixTrack, context: MixContext): TransitionPlan {
	if (context.mode == TrackTransition.None || context.repeatOne || !context.hasNext) return TransitionPlan.None
	val inAlbumOrder = a.albumId != null && a.albumId == b.albumId && (a.discNumber ?: 1) == (b.discNumber ?: 1)
		&& a.trackNumber != null && b.trackNumber == a.trackNumber + 1
	if (inAlbumOrder) return TransitionPlan.None

	val endA = a.analysis?.end ?: a.durationSeconds
	val startB = b.analysis?.cueIn ?: 0.0
	if (context.mode == TrackTransition.Crossfade) {
		return TransitionPlan.ShortFade(CROSSFADE_SECONDS, (endA - CROSSFADE_SECONDS).coerceAtLeast(0.0), startB)
	}

	val ta = a.analysis ?: return TransitionPlan.None
	val tb = b.analysis ?: return TransitionPlan.None
	if (excluded(a) || excluded(b)) return TransitionPlan.CleanCut(endA, startB)

	val factor = listOf(0.5, 1.0, 2.0).minBy { abs(ta.bpm - tb.bpm * it) }
	val gap = abs(ta.bpm - tb.bpm * factor) / ta.bpm
	val reliable = listOf(ta, tb).all { it.bpmConfidence >= MIN_BPM_CONFIDENCE && it.steady }
	if (!reliable || gap >= MAX_TEMPO_GAP) {
		return TransitionPlan.ShortFade(SHORT_FADE_SECONDS, (endA - SHORT_FADE_SECONDS).coerceAtLeast(0.0), startB)
	}

	// ponytail: constant tempo assumed, A's grid is firstBeat + k * 60/bpm (no variable beat grid)
	val bar = 4 * 60.0 / ta.bpm
	val wanted = if (keysCompatible(ta.key, tb.key)) COMPATIBLE_KEY_BARS else DISTANT_KEY_BARS
	val bars = wanted.coerceAtMost(floor(MAX_OVERLAP_SECONDS / bar).toInt())
		.coerceAtLeast(ceil(MIN_OVERLAP_SECONDS / bar).toInt())
	val overlap = bars * bar

	// B's first downbeat lands on a 4-bar boundary of A at or before cueOut, with A still there to fade out
	var start = ta.firstBeat + floor((ta.cueOut - ta.firstBeat) / (4 * bar)) * 4 * bar
	while (start + overlap > ta.duration && start - 4 * bar > ta.firstBeat) start -= 4 * bar
	if (start + overlap > ta.duration) {
		return TransitionPlan.ShortFade(SHORT_FADE_SECONDS, (endA - SHORT_FADE_SECONDS).coerceAtLeast(0.0), startB)
	}
	return TransitionPlan.Mix(overlap, ta.bpm / (tb.bpm * factor), start, tb.cueIn)
}

private fun excluded(t: MixTrack): Boolean = listOfNotNull(t.genre, t.albumTitle).any { raw ->
	val s = Normalizer.normalize(raw, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()
	LIVE.containsMatchIn(s) || AUTOMIX_EXCLUSIONS.any { it in s }
}

/** Same number, ±1 on the wheel with the same letter, or the relative key (A/B swap). */
fun keysCompatible(k1: String, k2: String): Boolean {
	val n1 = k1.dropLast(1).toIntOrNull() ?: return false
	val n2 = k2.dropLast(1).toIntOrNull() ?: return false
	val d = abs(n1 - n2).let { minOf(it, 12 - it) }
	return d == 0 || (d == 1 && k1.last() == k2.last())
}
