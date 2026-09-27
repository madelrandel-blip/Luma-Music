/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.arturo254.opentune.ui.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.arturo254.opentune.R
import com.arturo254.opentune.constants.AppBarHeight
import com.arturo254.opentune.ui.utils.YtimgResizePolicy
import com.arturo254.opentune.ui.utils.resize

@Composable
public fun MediaHero(
    title: String,
    thumbnailUrl: String?,
    @DrawableRes fallbackIcon: Int,
    systemBarsTopPadding: Dp,
    isAdded: Boolean,
    @StringRes addContentDescription: Int,
    @StringRes removeContentDescription: Int,
    onShuffle: (() -> Unit)?,
    onPlay: (() -> Unit)?,
    onToggleAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: AnnotatedString? = null,
    metadata: String? = null,
    description: String? = null,
    additionalPrimaryActions: @Composable (RowScope.(Color) -> Unit)? = null,
    customActions: @Composable (RowScope.(Color) -> Unit)? = null,
    heroContent: (@Composable () -> Unit)? = null, // Nuevo: contenido personalizado del hero
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val menuState = LocalMenuState.current
    val heroContentColor =
        if (surfaceColor.luminance() > 0.5f) {
            MaterialTheme.colorScheme.onSurface
        } else {
            Color.White
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = MediaDetailHeroMinHeight)
                .background(surfaceColor),
    ) {
        if (thumbnailUrl != null) {
            AsyncImage(
                model =
                    thumbnailUrl.resize(
                        width = MediaDetailHeroArtworkSizePx,
                        height = MediaDetailHeroArtworkSizePx,
                        ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
                    ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(fallbackIcon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(96.dp),
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.42f),
                            0.18f to Color.Transparent,
                            0.42f to Color.Transparent,
                            0.72f to surfaceColor.copy(alpha = 0.78f),
                            1f to surfaceColor,
                        ),
                    ),
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .widthIn(max = MediaDetailContentMaxWidth)
                    .padding(
                        start = MediaDetailHorizontalPadding,
                        top = systemBarsTopPadding + AppBarHeight + 96.dp,
                        end = MediaDetailHorizontalPadding,
                        bottom = 24.dp,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Si hay contenido personalizado, mostrarlo
            if (heroContent != null) {
                heroContent()
            } else {
                // Contenido predeterminado
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = heroContentColor,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        color = heroContentColor.copy(alpha = 0.82f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                description?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = heroContentColor.copy(alpha = 0.76f),
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                metadata?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = heroContentColor.copy(alpha = 0.62f),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                    )
                }
            }

            // Acciones
            if (customActions != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    customActions(this, heroContentColor)
                }
            } else {
                // Acciones predeterminadas
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    onShuffle?.let { shuffle ->
                        FilledTonalIconButton(
                            onClick = shuffle,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = heroContentColor.copy(alpha = 0.16f),
                                contentColor = heroContentColor,
                                disabledContainerColor = heroContentColor.copy(alpha = 0.08f),
                                disabledContentColor = heroContentColor.copy(alpha = 0.38f),
                            ),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.shuffle),
                                contentDescription = stringResource(R.string.shuffle),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    onPlay?.let { play ->
                        val playButtonHeight = ButtonDefaults.MediumContainerHeight
                        Button(
                            onClick = play,
                            shape = RoundedCornerShape(percent = 50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = heroContentColor,
                                contentColor = surfaceColor,
                            ),
                            contentPadding = ButtonDefaults.contentPaddingFor(playButtonHeight, hasStartIcon = true),
                            modifier = Modifier.heightIn(min = playButtonHeight),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.play),
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.iconSizeFor(playButtonHeight)),
                            )
                            Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(playButtonHeight)))
                            Text(
                                text = stringResource(R.string.play),
                                style = ButtonDefaults.textStyleFor(playButtonHeight),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    onToggleAdd?.let { toggleAdd ->
                        FilledTonalIconButton(
                            onClick = toggleAdd,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = heroContentColor.copy(alpha = 0.16f),
                                contentColor = heroContentColor,
                                disabledContainerColor = heroContentColor.copy(alpha = 0.08f),
                                disabledContentColor = heroContentColor.copy(alpha = 0.38f),
                            ),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                painter = painterResource(if (isAdded) R.drawable.done else R.drawable.add),
                                contentDescription = stringResource(
                                    if (isAdded) removeContentDescription else addContentDescription
                                ),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    additionalPrimaryActions?.invoke(this, heroContentColor)
                }
            }
        }
    }
}

private const val MediaDetailHeroArtworkSizePx = 1200
private val MediaDetailHeroArtworkSizeBuckets = listOf(MediaDetailHeroArtworkSizePx)
private val MediaDetailHeroMinHeight = 560.dp
private val MediaDetailHorizontalPadding = 24.dp
private val MediaDetailContentMaxWidth = 720.dp