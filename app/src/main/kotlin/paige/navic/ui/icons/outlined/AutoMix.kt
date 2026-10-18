/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.icons.outlined

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import paige.navic.ui.icons.Icons

/** Two overlapping circles: song transitions (AutoMix). */
val Icons.Outlined.AutoMix: ImageVector
	get() {
		if (_AutoMix != null) {
			return _AutoMix!!
		}
		_AutoMix = ImageVector.Builder(
			name = "Outlined.AutoMix",
			defaultWidth = 24.dp,
			defaultHeight = 24.dp,
			viewportWidth = 24f,
			viewportHeight = 24f
		).apply {
			for (cx in listOf(8.5f, 15.5f)) {
				path(stroke = SolidColor(Color(0xFFE3E3E3)), strokeLineWidth = 2f) {
					moveTo(cx - 6f, 12f)
					arcToRelative(6f, 6f, 0f, true, true, 12f, 0f)
					arcToRelative(6f, 6f, 0f, true, true, -12f, 0f)
					close()
				}
			}
		}.build()

		return _AutoMix!!
	}

@Suppress("ObjectPropertyName")
private var _AutoMix: ImageVector? = null
