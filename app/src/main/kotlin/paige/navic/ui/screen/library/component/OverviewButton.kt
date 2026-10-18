/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.library.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.NavKey
import paige.navic.di.LocalNavStack
import paige.navic.ui.icons.Icons
import paige.navic.ui.icons.outlined.ChevronForward
import paige.navic.ui.navigation.Screen

/** iOS Library list row: accent icon, label, chevron, inset divider. */
fun LazyGridScope.libraryScreenOverviewButton(
	icon: ImageVector,
	label: Int,
	destination: NavKey
) {
	item(span = { GridItemSpan(maxLineSpan) }) {
		val backStack = LocalNavStack.current
		Column(
			Modifier.clickable(onClick = dropUnlessResumed {
				if (backStack.lastOrNull() !is Screen.AlbumList) {
					backStack.add(destination)
				}
			})
		) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.height(52.dp)
					.padding(horizontal = 16.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Icon(
					icon,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.primary
				)
				Spacer(Modifier.width(16.dp))
				Text(
					stringResource(label),
					style = MaterialTheme.typography.bodyLarge,
					maxLines = 1,
					modifier = Modifier.weight(1f)
				)
				Icon(
					Icons.Outlined.ChevronForward,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
			HorizontalDivider(Modifier.padding(start = 56.dp, end = 16.dp))
		}
	}
}
