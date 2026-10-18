/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.component.layout

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import org.koin.compose.koinInject
import paige.navic.di.LocalSizeClass
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.model.settings.BottomBarCollapseMode

/**
 * Floating mini player above the floating tab bar. [backdrop] must be recorded
 * by the screen content (`Modifier.layerBackdrop`), see [glass].
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

	Column(
		modifier = modifier
			.windowInsetsPadding(
				WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
			)
			.padding(horizontal = 16.dp, vertical = 8.dp)
			.animateContentSize(),
		verticalArrangement = Arrangement.spacedBy(8.dp)
	) {
		if (!collapsed) {
			MiniPlayer(backdrop = backdrop)
		}
		BottomBar(backdrop = backdrop, collapsed = collapsed)
	}
}
