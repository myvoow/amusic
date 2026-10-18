/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.component.layout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import org.koin.compose.koinInject
import paige.navic.di.LocalSizeClass
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.model.settings.BottomBarCollapseMode

/**
 * Floating mini player above the floating tab bar. On scroll it morphs into one line
 * (active tab, mini player, search) like iOS 26.1. [backdrop] must be recorded by the
 * screen content (`Modifier.layerBackdrop`), see [glass].
 */
@Composable
fun RootBottomBar(
	scrolled: Boolean,
	backdrop: Backdrop,
	modifier: Modifier = Modifier
) {
	val sizeClass = LocalSizeClass.current
	if (sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium) return

	val preferenceManager = koinInject<PreferenceManager>()
	val collapsed =
		scrolled && preferenceManager.bottomBarCollapseMode == BottomBarCollapseMode.OnScroll
	// one progress drives every part of the morph: 0 = two lines, 1 = one line
	val progress by animateFloatAsState(
		targetValue = if (collapsed) 1f else 0f,
		animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f)
	)

	Layout(
		modifier = modifier
			.windowInsetsPadding(
				WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
			)
			.padding(horizontal = 16.dp, vertical = 8.dp),
		content = {
			Box { MiniPlayer(backdrop = backdrop, compact = collapsed) } // a node even when hidden when idle
			BottomBar(backdrop = backdrop, collapse = { progress }) // emits the tab capsule, then the search circle
		}
	) { measurables, constraints ->
		val (mini, tabs, search) = measurables
		val p = progress // read while measuring: the morph only relayouts, it doesn't recompose
		val width = constraints.maxWidth
		val bar = 62.dp.roundToPx()
		val gap = 8.dp.roundToPx()
		val miniPlaceable = mini.measure(Constraints.fixedWidth(lerp(width, width - 2 * (bar + gap), p)))
		val tabsPlaceable = tabs.measure(Constraints.fixed(lerp(width - bar - gap, bar, p), bar))
		val searchPlaceable = search.measure(Constraints.fixed(bar, bar))
		val miniHeight = miniPlaceable.height
		// the height stays the expanded one, so the screen's padding doesn't change on every frame
		val height = if (miniHeight > 0) miniHeight + gap + bar else bar
		layout(width, height) {
			val rowY = height - bar
			miniPlaceable.place(lerp(0, bar + gap, p), lerp(0, rowY + (bar - miniHeight) / 2, p))
			tabsPlaceable.place(0, rowY)
			searchPlaceable.place(width - bar, rowY)
		}
	}
}
