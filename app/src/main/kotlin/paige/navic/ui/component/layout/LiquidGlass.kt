/*
 * Copyright 2025 Kyant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copied from the catalog of https://github.com/Kyant0/AndroidLiquidGlass (tag 2.0.1):
 * components/LiquidBottomTabs.kt, LiquidBottomTab.kt, LiquidButton.kt and
 * utils/DampedDragAnimation.kt, InteractiveHighlight.kt, DragGestureInspector.kt.
 * Changes for Navic: theme colours instead of fixed blue/grey, CircleShape instead of the
 * kyant shapes Capsule, 64 dp bar, a tab weight and an indicator alpha for the collapsing bar,
 * a reselect callback, a nearly opaque surface below Android 12, withFrameNanos for awaitFrame.
 */

package paige.navic.ui.component.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.time.Clock

/** Tint over the refracted backdrop; below Android 12 there is no blur, so it must hide the content. */
@Composable
internal fun glassContainerColor(): Color =
	MaterialTheme.colorScheme.surface.copy(alpha = if (isRenderEffectSupported()) 0.4f else 0.94f)

// ---- components/LiquidBottomTabs.kt ----

@Composable
fun LiquidBottomTabs(
	selectedTabIndex: () -> Int,
	onTabSelected: (index: Int) -> Unit,
	onTabReselected: (index: Int) -> Unit,
	backdrop: Backdrop,
	tabsCount: Int,
	modifier: Modifier = Modifier,
	indicatorAlpha: () -> Float = { 1f },
	content: @Composable RowScope.() -> Unit
) {
	val isLightTheme = MaterialTheme.colorScheme.surface.luminance() > 0.5f
	val accentColor = MaterialTheme.colorScheme.primary
	val containerColor = glassContainerColor()

	val tabsBackdrop = rememberLayerBackdrop()

	BoxWithConstraints(
		modifier,
		contentAlignment = Alignment.CenterStart
	) {
		val density = LocalDensity.current
		val tabWidth = with(density) {
			(constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
		}

		val offsetAnimation = remember { Animatable(0f) }
		val panelOffset by remember(density) {
			derivedStateOf {
				val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
				with(density) {
					4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
				}
			}
		}

		val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
		val animationScope = rememberCoroutineScope()
		var currentIndex by remember(selectedTabIndex) {
			mutableIntStateOf(selectedTabIndex())
		}
		var dragged = 0f
		val dampedDragAnimation = remember(animationScope) {
			DampedDragAnimation(
				animationScope = animationScope,
				initialValue = selectedTabIndex().coerceAtLeast(0).toFloat(),
				valueRange = 0f..(tabsCount - 1).coerceAtLeast(0).toFloat(),
				visibilityThreshold = 0.001f,
				initialScale = 1f,
				pressedScale = 78f / 56f,
				onDragStarted = { dragged = 0f },
				onDragStopped = {
					val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
					// Navic: a plain tap on the selected tab scrolls to top / pops to the tab root
					if (targetIndex == currentIndex && dragged < with(density) { 8f.dp.toPx() }) onTabReselected(targetIndex)
					currentIndex = targetIndex
					animateToValue(targetIndex.toFloat())
					animationScope.launch {
						offsetAnimation.animateTo(
							0f,
							spring(1f, 300f, 0.5f)
						)
					}
				},
				onDrag = { _, dragAmount ->
					dragged += abs(dragAmount.x)
					updateValue(
						(targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
							.fastCoerceIn(0f, (tabsCount - 1).toFloat())
					)
					animationScope.launch {
						offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
					}
				}
			)
		}
		LaunchedEffect(selectedTabIndex) {
			snapshotFlow { selectedTabIndex() }
				.collectLatest { index ->
					currentIndex = index
				}
		}
		LaunchedEffect(dampedDragAnimation) {
			snapshotFlow { currentIndex }
				.drop(1)
				.collectLatest { index ->
					if (index >= 0) dampedDragAnimation.animateToValue(index.toFloat())
					onTabSelected(index)
				}
		}

		val interactiveHighlight = remember(animationScope) {
			InteractiveHighlight(
				animationScope = animationScope,
				position = { size, _ ->
					Offset(
						if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
						else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
						size.height / 2f
					)
				}
			)
		}

		Row(
			Modifier
				.graphicsLayer {
					translationX = panelOffset
				}
				.drawBackdrop(
					backdrop = backdrop,
					shape = { CircleShape },
					effects = {
						vibrancy()
						blur(2f.dp.toPx()) // Navic: light blur so the refraction shows, as on LiquidButton
						lens(24f.dp.toPx(), 24f.dp.toPx())
					},
					layerBlock = {
						val progress = dampedDragAnimation.pressProgress
						val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
						scaleX = scale
						scaleY = scale
					},
					onDrawSurface = { drawRect(containerColor) }
				)
				.then(interactiveHighlight.modifier)
				.height(64f.dp)
				.fillMaxWidth()
				.padding(4f.dp),
			verticalAlignment = Alignment.CenterVertically,
			content = content
		)

		CompositionLocalProvider(
			LocalLiquidBottomTabScale provides {
				lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
			}
		) {
			Row(
				Modifier
					.clearAndSetSemantics {}
					.alpha(0f)
					.layerBackdrop(tabsBackdrop)
					.graphicsLayer {
						translationX = panelOffset
					}
					.drawBackdrop(
						backdrop = backdrop,
						shape = { CircleShape },
						effects = {
							val progress = dampedDragAnimation.pressProgress
							vibrancy()
							blur(2f.dp.toPx()) // Navic: light blur so the refraction shows, as on LiquidButton
							lens(
								24f.dp.toPx() * progress,
								24f.dp.toPx() * progress
							)
						},
						highlight = {
							val progress = dampedDragAnimation.pressProgress
							Highlight.Default.copy(alpha = progress)
						},
						onDrawSurface = { drawRect(containerColor) }
					)
					.then(interactiveHighlight.modifier)
					.height(56f.dp)
					.fillMaxWidth()
					.padding(horizontal = 4f.dp)
					.graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
				verticalAlignment = Alignment.CenterVertically,
				content = content
			)
		}

		Box(
			Modifier
				.padding(horizontal = 4f.dp)
				.graphicsLayer {
					translationX =
						if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
						else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
					alpha = indicatorAlpha()
				}
				.then(interactiveHighlight.gestureModifier)
				.then(dampedDragAnimation.modifier)
				.drawBackdrop(
					backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
					shape = { CircleShape },
					effects = {
						// Navic: the indicator stays a lens at rest, it only grows when pressed
						val progress = dampedDragAnimation.pressProgress.coerceAtLeast(0.5f)
						lens(
							10f.dp.toPx() * progress,
							14f.dp.toPx() * progress,
							chromaticAberration = true
						)
					},
					highlight = {
						val progress = dampedDragAnimation.pressProgress.coerceAtLeast(0.5f)
						Highlight.Default.copy(alpha = progress)
					},
					shadow = {
						val progress = dampedDragAnimation.pressProgress
						Shadow(alpha = progress)
					},
					innerShadow = {
						val progress = dampedDragAnimation.pressProgress
						InnerShadow(
							radius = 8f.dp * progress,
							alpha = progress
						)
					},
					layerBlock = {
						scaleX = dampedDragAnimation.scaleX
						scaleY = dampedDragAnimation.scaleY
						val velocity = dampedDragAnimation.velocity / 10f
						scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
						scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
					},
					onDrawSurface = {
						val progress = dampedDragAnimation.pressProgress
						drawRect(
							if (isLightTheme) Color.Black.copy(0.1f)
							else Color.White.copy(0.1f),
							alpha = 1f - progress
						)
						drawRect(Color.Black.copy(alpha = 0.03f * progress))
					}
				)
				.height(56f.dp)
				.fillMaxWidth(1f / tabsCount)
		)
	}
}

// ---- components/LiquidBottomTab.kt ----

internal val LocalLiquidBottomTabScale =
	staticCompositionLocalOf { { 1f } }

@Composable
fun RowScope.LiquidBottomTab(
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	weight: Float = 1f,
	content: @Composable ColumnScope.() -> Unit
) {
	val scale = LocalLiquidBottomTabScale.current
	Column(
		modifier
			.clip(CircleShape)
			.clickable(
				interactionSource = null,
				indication = null,
				role = Role.Tab,
				onClick = onClick
			)
			.fillMaxHeight()
			.weight(weight)
			.graphicsLayer {
				val scale = scale()
				scaleX = scale
				scaleY = scale
			},
		verticalArrangement = Arrangement.spacedBy(2f.dp, Alignment.CenterVertically),
		horizontalAlignment = Alignment.CenterHorizontally,
		content = content
	)
}

// ---- components/LiquidButton.kt ----

@Composable
fun LiquidButton(
	onClick: () -> Unit,
	backdrop: Backdrop,
	modifier: Modifier = Modifier,
	isInteractive: Boolean = true,
	tint: Color = Color.Unspecified,
	surfaceColor: Color = glassContainerColor(),
	content: @Composable RowScope.() -> Unit
) {
	val animationScope = rememberCoroutineScope()

	val interactiveHighlight = remember(animationScope) {
		InteractiveHighlight(
			animationScope = animationScope
		)
	}

	Row(
		modifier
			.drawBackdrop(
				backdrop = backdrop,
				shape = { CircleShape },
				effects = {
					vibrancy()
					blur(2f.dp.toPx())
					lens(12f.dp.toPx(), 24f.dp.toPx())
				},
				layerBlock = if (isInteractive) {
					{
						val width = size.width
						val height = size.height

						val progress = interactiveHighlight.pressProgress
						val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)

						val maxOffset = size.minDimension
						val initialDerivative = 0.05f
						val offset = interactiveHighlight.offset
						translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
						translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

						val maxDragScale = 4f.dp.toPx() / size.height
						val offsetAngle = atan2(offset.y, offset.x)
						scaleX =
							scale +
								maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
								(width / height).fastCoerceAtMost(1f)
						scaleY =
							scale +
								maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
								(height / width).fastCoerceAtMost(1f)
					}
				} else {
					null
				},
				onDrawSurface = {
					if (tint.isSpecified) {
						drawRect(tint, blendMode = BlendMode.Hue)
						drawRect(tint.copy(alpha = 0.75f))
					}
					if (surfaceColor.isSpecified) {
						drawRect(surfaceColor)
					}
				}
			)
			.clickable(
				interactionSource = null,
				indication = if (isInteractive) null else LocalIndication.current,
				role = Role.Button,
				onClick = onClick
			)
			.then(
				if (isInteractive) {
					Modifier
						.then(interactiveHighlight.modifier)
						.then(interactiveHighlight.gestureModifier)
				} else {
					Modifier
				}
			)
			.height(48f.dp)
			.padding(horizontal = 16f.dp),
		horizontalArrangement = Arrangement.spacedBy(8f.dp, Alignment.CenterHorizontally),
		verticalAlignment = Alignment.CenterVertically,
		content = content
	)
}

// ---- utils/DampedDragAnimation.kt ----

class DampedDragAnimation(
	private val animationScope: CoroutineScope,
	val initialValue: Float,
	val valueRange: ClosedRange<Float>,
	val visibilityThreshold: Float,
	val initialScale: Float,
	val pressedScale: Float,
	val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
	val onDragStopped: DampedDragAnimation.() -> Unit,
	val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {

	private val valueAnimationSpec =
		spring(1f, 1000f, visibilityThreshold)
	private val velocityAnimationSpec =
		spring(0.5f, 300f, visibilityThreshold * 10f)
	private val pressProgressAnimationSpec =
		spring(1f, 1000f, 0.001f)
	private val scaleXAnimationSpec =
		spring(0.6f, 250f, 0.001f)
	private val scaleYAnimationSpec =
		spring(0.7f, 250f, 0.001f)

	private val valueAnimation =
		Animatable(initialValue, visibilityThreshold)
	private val velocityAnimation =
		Animatable(0f, 5f)
	private val pressProgressAnimation =
		Animatable(0f, 0.001f)
	private val scaleXAnimation =
		Animatable(initialScale, 0.001f)
	private val scaleYAnimation =
		Animatable(initialScale, 0.001f)

	private val mutatorMutex = MutatorMutex()

	private val velocityTracker = VelocityTracker()

	val value: Float get() = valueAnimation.value
	val progress: Float get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
	val targetValue: Float get() = valueAnimation.targetValue
	val pressProgress: Float get() = pressProgressAnimation.value
	val scaleX: Float get() = scaleXAnimation.value
	val scaleY: Float get() = scaleYAnimation.value
	val velocity: Float get() = velocityAnimation.value

	val modifier: Modifier = Modifier.pointerInput(Unit) {
		inspectDragGestures(
			onDragStart = { down ->
				onDragStarted(down.position)
				press()
			},
			onDragEnd = {
				onDragStopped()
				release()
			},
			onDragCancel = {
				onDragStopped()
				release()
			}
		) { _, dragAmount ->
			onDrag(size, dragAmount)
		}
	}

	fun press() {
		velocityTracker.resetTracking()
		animationScope.launch {
			launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
			launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
			launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
		}
	}

	fun release() {
		animationScope.launch {
			withFrameNanos { }
			if (value != targetValue) {
				val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
				snapshotFlow { valueAnimation.value }
					.filter { abs(it - valueAnimation.targetValue) < threshold }
					.first()
			}
			launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
			launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
			launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
		}
	}

	fun updateValue(value: Float) {
		val targetValue = value.coerceIn(valueRange)
		animationScope.launch {
			launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) { updateVelocity() } }
		}
	}

	fun animateToValue(value: Float) {
		animationScope.launch {
			mutatorMutex.mutate {
				press()
				val targetValue = value.coerceIn(valueRange)
				launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) }
				if (velocity != 0f) {
					launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
				}
				release()
			}
		}
	}

	private fun updateVelocity() {
		velocityTracker.addPosition(
			Clock.System.now().toEpochMilliseconds(),
			Offset(value, 0f)
		)
		val targetVelocity = velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
		animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
	}
}

// ---- utils/InteractiveHighlight.kt ----

class InteractiveHighlight(
	val animationScope: CoroutineScope,
	val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset }
) {

	private val pressProgressAnimationSpec =
		spring(0.5f, 300f, 0.001f)
	private val positionAnimationSpec =
		spring(0.5f, 300f, Offset.VisibilityThreshold)

	private val pressProgressAnimation =
		Animatable(0f, 0.001f)
	private val positionAnimation =
		Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

	private var startPosition = Offset.Zero
	val pressProgress: Float get() = pressProgressAnimation.value
	val offset: Offset get() = positionAnimation.value - startPosition

	private val shader =
		if (isRuntimeShaderSupported()) {
			RuntimeShader(
				"""
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
			)
		} else {
			null
		}

	val modifier: Modifier =
		Modifier.drawWithContent {
			val progress = pressProgressAnimation.value
			if (progress > 0f) {
				if (shader != null) {
					drawRect(
						Color.White.copy(0.08f * progress),
						blendMode = BlendMode.Plus
					)
					shader.apply {
						val position = position(size, positionAnimation.value)
						setFloatUniform("size", size.width, size.height)
						setColorUniform("color", Color.White.copy(0.15f * progress))
						setFloatUniform("radius", size.minDimension * 1.5f)
						setFloatUniform(
							"position",
							position.x.fastCoerceIn(0f, size.width),
							position.y.fastCoerceIn(0f, size.height)
						)
					}
					drawRect(
						ShaderBrush(shader.asComposeShader()),
						blendMode = BlendMode.Plus
					)
				} else {
					drawRect(
						Color.White.copy(0.25f * progress),
						blendMode = BlendMode.Plus
					)
				}
			}

			drawContent()
		}

	val gestureModifier: Modifier =
		Modifier.pointerInput(animationScope) {
			inspectDragGestures(
				onDragStart = { down ->
					startPosition = down.position
					animationScope.launch {
						launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
						launch { positionAnimation.snapTo(startPosition) }
					}
				},
				onDragEnd = {
					animationScope.launch {
						launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
						launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
					}
				},
				onDragCancel = {
					animationScope.launch {
						launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
						launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
					}
				}
			) { change, _ ->
				animationScope.launch { positionAnimation.snapTo(change.position) }
			}
		}
}

// ---- utils/DragGestureInspector.kt ----

suspend fun PointerInputScope.inspectDragGestures(
	onDragStart: (down: PointerInputChange) -> Unit = {},
	onDragEnd: (change: PointerInputChange) -> Unit = {},
	onDragCancel: () -> Unit = {},
	onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
	awaitEachGesture {
		val initialDown = awaitFirstDown(false, PointerEventPass.Initial)

		val down = awaitFirstDown(false)
		val drag = initialDown

		onDragStart(down)
		onDrag(drag, Offset.Zero)
		val upEvent =
			drag(
				pointerId = drag.id,
				onDrag = { onDrag(it, it.positionChange()) }
			)
		if (upEvent == null) {
			onDragCancel()
		} else {
			onDragEnd(upEvent)
		}
	}
}

private suspend inline fun AwaitPointerEventScope.drag(
	pointerId: PointerId,
	onDrag: (PointerInputChange) -> Unit
): PointerInputChange? {
	val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
	if (isPointerUp) {
		return null
	}
	var pointer = pointerId
	while (true) {
		val change = awaitDragOrUp(pointer) ?: return null
		if (change.isConsumed) {
			return null
		}
		if (change.changedToUpIgnoreConsumed()) {
			return change
		}
		onDrag(change)
		pointer = change.id
	}
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
	pointerId: PointerId
): PointerInputChange? {
	var pointer = pointerId
	while (true) {
		val event = awaitPointerEvent()
		val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
		if (dragEvent.changedToUpIgnoreConsumed()) {
			val otherDown = event.changes.fastFirstOrNull { it.pressed }
			if (otherDown == null) {
				return dragEvent
			} else {
				pointer = otherDown.id
			}
		} else {
			val hasDragged = dragEvent.previousPosition != dragEvent.position
			if (hasDragged) {
				return dragEvent
			}
		}
	}
}
