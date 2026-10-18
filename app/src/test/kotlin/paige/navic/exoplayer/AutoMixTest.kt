/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.exoplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import paige.navic.exoplayer.TransitionPlan.CleanCut
import paige.navic.exoplayer.TransitionPlan.Mix
import paige.navic.exoplayer.TransitionPlan.None
import paige.navic.exoplayer.TransitionPlan.ShortFade

class AutoMixTest {
	private fun track(
		bpm: Double = 128.0,
		key: String = "8A",
		confidence: Double = 0.6,
		steady: Boolean = true,
		album: String? = null,
		trackNumber: Int? = null,
		genre: String? = null,
		albumTitle: String? = null,
		analysed: Boolean = true
	) = MixTrack(
		analysis = if (analysed) AutoMixAnalysis(
			bpm = bpm, bpmConfidence = confidence, firstBeat = 0.5, steady = steady, key = key,
			cueIn = 0.5, cueOut = 200.0, end = 210.0, duration = 212.0
		) else null,
		durationSeconds = 212.0, albumId = album, albumTitle = albumTitle, discNumber = 1,
		trackNumber = trackNumber, genre = genre
	)

	private val auto = MixContext(TrackTransition.AutoMix)

	@Test
	fun closeTemposMix() {
		val plan = planTransition(track(128.0), track(126.0, key = "9A"), auto) as Mix
		assertEquals(128.0 / 126.0, plan.stretchRatio, 1e-9)
		assertEquals(8 * 4 * 60 / 128.0, plan.overlapSeconds, 1e-9) // compatible keys: 8 bars = 15 s
		val barA = 4 * 60 / 128.0
		val k = (plan.startOffsetInA - 0.5) / (4 * barA)
		assertEquals(Math.round(k).toDouble(), k, 1e-9) // B lands on a 4-bar boundary of A
		assertTrue(plan.startOffsetInA <= 200.0 && plan.startOffsetInA + plan.overlapSeconds <= 212.0)
		assertEquals(0.5, plan.startOffsetInB, 0.0)
	}

	@Test
	fun distantKeysUseShortOverlap() {
		val plan = planTransition(track(128.0, key = "8A"), track(128.0, key = "3B"), auto) as Mix
		assertEquals(4 * 4 * 60 / 128.0, plan.overlapSeconds, 1e-9)
	}

	@Test
	fun halfTempoIsMatched() {
		val plan = planTransition(track(140.0), track(71.0), auto) as Mix
		assertEquals(140.0 / 142.0, plan.stretchRatio, 1e-9)
	}

	@Test
	fun incompatibleTemposFadeShortly() {
		assertEquals(ShortFade(1.0, 209.0, 0.5), planTransition(track(128.0), track(95.0), auto))
	}

	@Test
	fun unreliableTempoFadesShortly() {
		assertTrue(planTransition(track(128.0), track(128.0, confidence = 0.1), auto) is ShortFade)
		assertTrue(planTransition(track(128.0, steady = false), track(128.0), auto) is ShortFade)
	}

	@Test
	fun albumInOrderIsLeftAlone() {
		assertEquals(None, planTransition(track(album = "x", trackNumber = 3), track(album = "x", trackNumber = 4), auto))
		assertTrue(planTransition(track(album = "x", trackNumber = 3), track(album = "x", trackNumber = 7), auto) is Mix)
	}

	@Test
	fun missingDataMeansNoMix() {
		assertEquals(None, planTransition(track(), track(analysed = false), auto))
	}

	@Test
	fun excludedGenresOnlyTrimSilence() {
		assertEquals(CleanCut(210.0, 0.5), planTransition(track(), track(genre = "Musique classique"), auto))
		assertEquals(CleanCut(210.0, 0.5), planTransition(track(genre = "Clássica"), track(), auto))
		assertEquals(CleanCut(210.0, 0.5), planTransition(track(), track(albumTitle = "Live au Zénith"), auto))
		assertTrue(planTransition(track(), track(albumTitle = "Oliver Twist"), auto) is Mix)
	}

	@Test
	fun settingsRepeatAndEndOfQueue() {
		assertEquals(None, planTransition(track(), track(), MixContext(TrackTransition.None)))
		assertEquals(None, planTransition(track(), track(), auto.copy(repeatOne = true)))
		assertEquals(None, planTransition(track(), track(), auto.copy(hasNext = false)))
	}

	@Test
	fun crossfadeWorksWithoutData() {
		assertEquals(
			ShortFade(6.0, 206.0, 0.0),
			planTransition(track(analysed = false), track(analysed = false), MixContext(TrackTransition.Crossfade))
		)
	}

	@Test
	fun slowTempoOverlapStaysUnder16Seconds() {
		val plan = planTransition(track(80.0), track(80.0), auto) as Mix
		assertEquals(5 * 3.0, plan.overlapSeconds, 1e-9) // 8 bars would be 24 s
	}

	@Test
	fun userTuning() {
		fun with(t: AutoMixTuning) = MixContext(TrackTransition.AutoMix, tuning = t)
		val inOrder = track(album = "x", trackNumber = 3) to track(album = "x", trackNumber = 4)
		assertTrue(planTransition(inOrder.first, inOrder.second, with(AutoMixTuning(mixAlbumsInOrder = true))) is Mix)
		assertTrue(planTransition(track(), track(albumTitle = "Live"), with(AutoMixTuning(excludeLive = false))) is Mix)
		assertTrue(planTransition(track(), track(genre = "Techno"), with(AutoMixTuning(exclusions = "techno"))) is CleanCut)
		assertTrue(planTransition(track(128.0), track(95.0), with(AutoMixTuning(maxTempoGapPercent = 30f))) is Mix)
		assertEquals(ShortFade(2.0, 208.0, 0.5), planTransition(track(128.0), track(95.0), with(AutoMixTuning(shortFadeSeconds = 2f))))
		val normal = planTransition(track(), track(), auto) as Mix
		val early = planTransition(track(), track(), with(AutoMixTuning(exitEarlyBars = 8))) as Mix
		assertEquals(8 * 4 * 60 / 128.0, normal.startOffsetInA - early.startOffsetInA, 1e-9)
		assertEquals(4 * 4 * 60 / 128.0, (planTransition(track(), track(), with(AutoMixTuning(compatibleKeyBars = 4))) as Mix).overlapSeconds, 1e-9)
	}

	@Test
	fun camelotWheel() {
		assertTrue(keysCompatible("12A", "1A"))
		assertTrue(keysCompatible("8A", "8B"))
		assertTrue(!keysCompatible("8A", "9B"))
		assertTrue(!keysCompatible("8A", "10A"))
	}
}
