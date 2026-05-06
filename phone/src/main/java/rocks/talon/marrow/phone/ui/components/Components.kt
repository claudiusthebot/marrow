package rocks.talon.marrow.phone.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/* -------------------------------------------------------------------------- */
/* Marrow design system — surfaces, tiles, chips, progress, headings.          */
/*                                                                             */
/* The vocabulary closely mirrors PixelPlayer's DeviceCapabilitiesScreen so   */
/* the visual language stays consistent with the reference Dylan called out:  */
/*                                                                             */
/*   • CapabilityCard — top-level squircle surface (28dp/60, surfaceContainer)*/
/*   • InfoTile      — labelled value cell (18dp/60, surfaceContainerLow)     */
/*   • HeroMetricTile — content-centred metric (18dp/60, tinted container)    */
/*   • TonalChip     — pill chip (CircleShape, surfaceContainerHighest)       */
/*   • StatusIcon    — 44dp circle badge for card headers                     */
/*   • ProgressReadout — label/value row + 8dp progress bar                   */
/*   • SectionLabel  — semibold titleSmall heading inside a card              */
/*                                                                             */
/* Shapes: AbsoluteSmoothCornerShape(radius, 60) everywhere — 60 is the       */
/* smoothness percent PixelPlayer uses (0=standard rounded, 100=full iOS      */
/* squircle). Cards=28dp, tiles=18dp, hero=32dp.                              */
/* -------------------------------------------------------------------------- */

/* ---- Cards & surfaces --------------------------------------------------- */

/**
 * The top-level card every section uses on the home screen and in detail
 * heroes. 28dp squircle corners, `surfaceContainer` background, no tonal
 * elevation. Optional press-scale feedback when [onClick] is supplied.
 */
@Composable
fun MarrowCapabilityCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    iconContainer: Color = MaterialTheme.colorScheme.secondaryContainer,
    iconContent: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    surface: Color = MaterialTheme.colorScheme.surfaceContainer,
    verticalSpacing: Dp = 12.dp,
    enableTopSpacer: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    MarrowSurface(
        modifier = modifier.fillMaxWidth(),
        surface = surface,
        cornerRadius = 28.dp,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusIcon(icon = icon, container = iconContainer, content = iconContent)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                if (trailing != null) trailing()
            }
            if (enableTopSpacer) Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

/**
 * Press-scale wrapper used by all clickable surfaces. Adds a subtle scale +
 * haptic kick when [onClick] is set. Uses [AbsoluteSmoothCornerShape] for the
 * squircle visual language consistent with PixelPlayer.
 */
@Composable
fun MarrowSurface(
    modifier: Modifier = Modifier,
    surface: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    cornerRadius: Dp = 28.dp,
    onClick: (() -> Unit)? = null,
    interaction: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "press-scale",
    )
    val haptic = LocalHapticFeedback.current
    val mod = modifier.scale(scale)
    // Squircle shape: smoothness=60 matches PixelPlayer's AbsoluteSmoothCornerShape usage.
    // 0 = standard circular corners, 100 = full iOS-style superellipse.
    val shape = AbsoluteSmoothCornerShape(cornerRadius, 60)
    if (onClick != null) {
        Card(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
            modifier = mod,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = surface, contentColor = contentColor),
            interactionSource = interaction,
        ) { content() }
    } else {
        Surface(modifier = mod, shape = shape, color = surface, contentColor = contentColor) {
            content()
        }
    }
}

/** Backwards-compat shim for older code that called `MarrowCard`. */
@Composable
fun MarrowCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    cornerRadius: Dp = 28.dp,
    content: @Composable () -> Unit,
) = MarrowSurface(
    modifier = modifier,
    surface = containerColor,
    contentColor = contentColor,
    cornerRadius = cornerRadius,
    onClick = onClick,
    content = content,
)

/* ---- Status icon, used as the leading badge on every card header -------- */

@Composable
fun StatusIcon(
    icon: ImageVector,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = container,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/* ---- Info tile — a labelled value cell ---------------------------------- */

@Composable
fun InfoTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .defaultMinSize(minHeight = 86.dp),
        shape = AbsoluteSmoothCornerShape(18.dp, 60),
        color = container,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/* ---- Hero metric tile — used in hero rows -------------------------------- */

@Composable
fun HeroMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    minHeight: Dp = 82.dp,
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .defaultMinSize(minHeight = minHeight),
        shape = AbsoluteSmoothCornerShape(18.dp, 60),
        color = container,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.78f),
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ---- Tonal chip — pill ----------------------------------------------- */

@Composable
fun TonalChip(
    text: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    compact: Boolean = false,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable { onClick() } else Modifier,
        ),
        shape = CircleShape,
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp else 12.dp,
                vertical = if (compact) 5.dp else 8.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(if (compact) 14.dp else 16.dp),
                )
            }
            Text(
                text = text,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ---- Progress readout ---------------------------------------------------- */

@Composable
fun ProgressReadout(
    label: String,
    value: String,
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f).let { if (it > 0f && it < 0.01f) 0.01f else it } },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

/* ---- Headings ------------------------------------------------------------ */

/** Title row for the home screen. titleLarge semibold + optional subtitle. */
@Composable
fun ScreenSectionTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) trailing()
    }
}

/** Sub-heading inside a card. Bolded titleSmall. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.semantics { heading() },
    )
}

/* ---- Backwards-compat ---------------------------------------------------- */

/** Old name kept so other files compile — delegates to ScreenSectionTitle. */
@Composable
fun SectionTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) = ScreenSectionTitle(title = title, subtitle = subtitle, modifier = modifier, trailing = trailing)

/** Tonal-elevation badge that older detail screens use. Kept lean. */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Int = 44,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    cornerRadius: Int = 22,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(
                if (cornerRadius >= size / 2) CircleShape
                else AbsoluteSmoothCornerShape(cornerRadius.dp, 60)
            )
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size((size * 0.50f).dp),
        )
    }
}

/** Key/value row used by section detail bodies — sits on `surfaceContainer`
 *  so it inherits whatever card it's nested in. Tap-to-copy supported. */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    monospaceValue: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = if (monospaceValue) MaterialTheme.typography.labelSmall.copy(
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                fontWeight = FontWeight.Medium,
            ) else MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Small live-stat chip that older tabs may still reference. */
@Composable
fun LiveStatChip(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) = HeroMetricTile(
    label = label,
    value = value,
    modifier = modifier
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    container = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

/** Old SectionGridCard reference — same layout but now full-width and PixelPlayer-styled. */
@Composable
fun SectionGridCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    livePreview: String? = null,
    secondary: String? = null,
    onClick: () -> Unit,
) {
    MarrowCapabilityCard(
        title = title,
        icon = icon,
        onClick = onClick,
        modifier = modifier,
        verticalSpacing = 8.dp,
        trailing = {
            if (!livePreview.isNullOrBlank()) {
                Text(
                    text = livePreview,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    ) {
        if (!secondary.isNullOrBlank()) {
            Text(
                text = secondary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
