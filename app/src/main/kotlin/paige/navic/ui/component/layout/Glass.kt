/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.component.layout

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * iOS 26 style "Liquid Glass" surface (Kyant's Backdrop library).
 * [backdrop] must be recorded by a `Modifier.layerBackdrop` on a sibling, never on a parent.
 */
@Composable
fun Modifier.glass(
	backdrop: Backdrop?,
	shape: CornerBasedShape = CircleShape,
	tint: Color = MaterialTheme.colorScheme.surface
): Modifier {
	// ponytail: no RenderEffect below Android 12, so the backdrop would show through un-blurred;
	// fall back to a plain translucent surface there (lens() itself only works from Android 13)
	if (backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
		return this
			.dropShadow(shape, Shadow(radius = 12.dp, alpha = .15f))
			.background(tint.copy(alpha = .94f), shape)
			.clip(shape)
	}
	return drawBackdrop(
		backdrop = backdrop,
		shape = { shape },
		effects = {
			vibrancy()
			blur(8.dp.toPx())
			// Kyant's values (24 dp on a 64 dp bar); refraction must stay below the corner radius
			lens(size.minDimension * 0.375f, 24.dp.toPx())
		},
		onDrawSurface = { drawRect(tint.copy(alpha = .4f)) }
	)
}
