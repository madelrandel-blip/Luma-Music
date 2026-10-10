/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.arturo254.opentune.ui.screens.settings

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.arturo254.opentune.LocalPlayerAwareWindowInsets
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import com.arturo254.opentune.App.Companion.forgetAccount
import com.arturo254.opentune.BuildConfig
import com.arturo254.opentune.R
import com.arturo254.opentune.constants.AccountChannelHandleKey
import com.arturo254.opentune.constants.AccountEmailKey
import com.arturo254.opentune.constants.AccountNameKey
import com.arturo254.opentune.constants.DataSyncIdKey
import com.arturo254.opentune.constants.GuestAvatarUriKey
import com.arturo254.opentune.constants.GuestModeEnabledKey
import com.arturo254.opentune.constants.GuestNameKey
import com.arturo254.opentune.constants.InnerTubeCookieKey
import com.arturo254.opentune.constants.PoTokenKey
import com.arturo254.opentune.constants.SelectedYtmPlaylistsKey
import com.arturo254.opentune.constants.UseLoginForBrowse
import com.arturo254.opentune.constants.VisitorDataKey
import com.arturo254.opentune.constants.YtmSyncKey
import com.arturo254.opentune.innertube.YouTube
import com.arturo254.opentune.innertube.utils.completed
import com.arturo254.opentune.innertube.utils.parseCookieString
import com.arturo254.opentune.ui.component.InfoLabel
import com.arturo254.opentune.ui.component.TextFieldDialog
import com.arturo254.opentune.ui.screens.buildLoginRoute
import com.arturo254.opentune.utils.Updater
import com.arturo254.opentune.utils.dataStore
import com.arturo254.opentune.utils.rememberPreference
import com.arturo254.opentune.viewmodels.HomeViewModel

/**
 * Full-screen "Account" page (route `settings/account`). Styled like the
 * other settings sub-screens: centred top bar, a profile header with a
 * gradient, then flat sections with bold headers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    navController: NavController,
    latestVersionName: String,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.account),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        AccountSettings(
            navController = navController,
            latestVersionName = latestVersionName,
            topPadding = innerPadding.calculateTopPadding(),
        )
    }
}

@Composable
fun AccountSettings(
    navController: NavController,
    latestVersionName: String,
    topPadding: Dp = 0.dp,
) {
    val context = LocalContext.current

    val (accountNamePref, onAccountNameChange) = rememberPreference(AccountNameKey, "")
    val (accountEmail, onAccountEmailChange) = rememberPreference(AccountEmailKey, "")
    val (accountChannelHandle, onAccountChannelHandleChange) = rememberPreference(AccountChannelHandleKey, "")
    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")
    val (poToken, onPoTokenChange) = rememberPreference(PoTokenKey, "")
    val (visitorData, onVisitorDataChange) = rememberPreference(VisitorDataKey, "")
    val (dataSyncId, onDataSyncIdChange) = rememberPreference(DataSyncIdKey, "")

    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val (useLoginForBrowse, onUseLoginForBrowseChange) = rememberPreference(UseLoginForBrowse, true)
    val (ytmSync, onYtmSyncChange) = rememberPreference(YtmSyncKey, true)

    // Guest profile - local-only name + photo, no real account
    val (guestModeEnabled, onGuestModeEnabledChange) = rememberPreference(GuestModeEnabledKey, false)
    val (guestName, onGuestNameChange) = rememberPreference(GuestNameKey, "")
    val (guestAvatarUri, onGuestAvatarUriChange) = rememberPreference(GuestAvatarUriKey, "")
    val isGuest = guestModeEnabled && !isLoggedIn

    val viewModel: HomeViewModel = hiltViewModel()
    val accountName by viewModel.accountName.collectAsState()
    val accountImageUrl by viewModel.accountImageUrl.collectAsState()

    var showToken by remember { mutableStateOf(false) }
    var showTokenEditor by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showLoginChoiceDialog by remember { mutableStateOf(false) }
    var showGuestProfileDialog by remember { mutableStateOf(false) }

    val onLogout: () -> Unit = {
        if (isLoggedIn) {
            onInnerTubeCookieChange("")
            forgetAccount(context)
        } else if (isGuest) {
            onGuestModeEnabledChange(false)
            onGuestNameChange("")
            onGuestAvatarUriChange("")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
            )
    ) {
        AccountProfileHeader(
            topPadding = topPadding,
            isLoggedIn = isLoggedIn,
            isGuest = isGuest,
            name = when {
                isLoggedIn -> accountName
                isGuest -> guestName.ifBlank { stringResource(R.string.guest_default_name) }
                else -> stringResource(R.string.not_logged_in)
            },
            subtitle = when {
                isLoggedIn -> accountEmail.ifBlank { accountChannelHandle }
                isGuest -> stringResource(R.string.continue_as_guest)
                else -> ""
            },
            avatarUrl = when {
                isLoggedIn -> accountImageUrl
                isGuest -> guestAvatarUri.ifBlank { null }
                else -> null
            },
            onPrimaryAction = {
                when {
                    isLoggedIn -> navController.navigate("account")
                    isGuest -> showGuestProfileDialog = true
                    else -> showLoginChoiceDialog = true
                }
            },
            onSettingsClick = {
                if (!navController.popBackStack("settings", inclusive = false)) {
                    navController.navigate("settings")
                }
            },
        )

        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Choose between a real YouTube Premium login or a local guest profile
            if (showLoginChoiceDialog) {
                LoginChoiceDialog(
                    onDismiss = { showLoginChoiceDialog = false },
                    onChooseYoutube = {
                        showLoginChoiceDialog = false
                        navController.navigate(buildLoginRoute())
                    },
                    onChooseGuest = {
                        showLoginChoiceDialog = false
                        showGuestProfileDialog = true
                    }
                )
            }

            // Guest profile editor (username + photo)
            if (showGuestProfileDialog) {
                GuestProfileDialog(
                    initialName = guestName,
                    initialAvatarUri = guestAvatarUri,
                    onDismiss = { showGuestProfileDialog = false },
                    onSave = { name, avatarUri ->
                        onGuestNameChange(name)
                        onGuestAvatarUriChange(avatarUri.orEmpty())
                        onGuestModeEnabledChange(true)
                        showGuestProfileDialog = false
                    }
                )
            }

            // Token Editor Dialog
            if (showTokenEditor) {
                TokenEditorDialog(
                    innerTubeCookie = innerTubeCookie,
                    visitorData = visitorData,
                    dataSyncId = dataSyncId,
                    accountNamePref = accountNamePref,
                    accountEmail = accountEmail,
                    accountChannelHandle = accountChannelHandle,
                    onInnerTubeCookieChange = onInnerTubeCookieChange,
                    onPoTokenChange = onPoTokenChange,
                    onVisitorDataChange = onVisitorDataChange,
                    onDataSyncIdChange = onDataSyncIdChange,
                    onAccountNameChange = onAccountNameChange,
                    onAccountEmailChange = onAccountEmailChange,
                    onAccountChannelHandleChange = onAccountChannelHandleChange,
                    onDismiss = { showTokenEditor = false }
                )
            }

            // Account details
            SettingsSection(title = stringResource(R.string.account_details)) {
                SettingsClickableItem(
                    title = stringResource(R.string.username),
                    subtitle = when {
                        isLoggedIn -> accountName.ifBlank { accountNamePref }
                        isGuest -> guestName.ifBlank { stringResource(R.string.guest_default_name) }
                        else -> stringResource(R.string.not_logged_in)
                    },
                    onClick = {
                        when {
                            isLoggedIn -> navController.navigate("account")
                            isGuest -> showGuestProfileDialog = true
                            else -> showLoginChoiceDialog = true
                        }
                    }
                )
                if (isLoggedIn && accountEmail.isNotBlank()) {
                    SettingsClickableItem(
                        title = stringResource(R.string.account_email),
                        subtitle = accountEmail,
                        showChevron = false,
                        onClick = {}
                    )
                }
                if (isLoggedIn && accountChannelHandle.isNotBlank()) {
                    SettingsClickableItem(
                        title = stringResource(R.string.account_channel_handle),
                        subtitle = accountChannelHandle,
                        showChevron = false,
                        onClick = {}
                    )
                }
                SettingsClickableItem(
                    title = when {
                        isLoggedIn -> stringResource(R.string.action_logout)
                        isGuest -> stringResource(R.string.exit_guest_mode)
                        else -> stringResource(R.string.action_login)
                    },
                    subtitle = if (!isLoggedIn && !isGuest) {
                        stringResource(R.string.continue_as_guest)
                    } else {
                        null
                    },
                    onClick = {
                        if (isLoggedIn || isGuest) onLogout() else showLoginChoiceDialog = true
                    }
                )
            }

            // Account Options Section
            AnimatedVisibility(
                visible = isLoggedIn,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                SettingsSection(title = stringResource(R.string.content)) {
                    SettingsToggleItem(
                        title = stringResource(R.string.more_content),
                        subtitle = stringResource(R.string.use_login_for_browse_desc),
                        checked = useLoginForBrowse,
                        onCheckedChange = {
                            YouTube.useLoginForBrowse = it
                            onUseLoginForBrowseChange(it)
                        }
                    )
                    SettingsToggleItem(
                        title = stringResource(R.string.yt_sync),
                        checked = ytmSync,
                        onCheckedChange = onYtmSyncChange
                    )
                }
            }

            // Sync & Integration Section
            SettingsSection(title = stringResource(R.string.integration)) {
                SettingsClickableItem(
                    title = stringResource(R.string.select_playlist_to_sync),
                    onClick = { showPlaylistDialog = true }
                )
                SettingsClickableItem(
                    title = stringResource(R.string.integration),
                    subtitle = "Discord, Last.fm, ListenBrainz",
                    onClick = { navController.navigate("settings/integration") }
                )
                SettingsClickableItem(
                    title = stringResource(R.string.music_together),
                    onClick = { navController.navigate("settings/music_together") }
                )
            }

            // Advanced Section
            SettingsSection(title = stringResource(R.string.misc)) {
                SettingsClickableItem(
                    title = when {
                        !isLoggedIn -> stringResource(R.string.advanced_login)
                        showToken -> stringResource(R.string.token_shown)
                        else -> stringResource(R.string.token_hidden)
                    },
                    onClick = {
                        if (!isLoggedIn) showTokenEditor = true
                        else if (!showToken) showToken = true
                        else showTokenEditor = true
                    }
                )
            }

            // App Version Footer
            AppVersionFooter()

            Spacer(Modifier.height(24.dp))
        }
    }

    // Playlist Selection Dialog
    if (showPlaylistDialog) {
        PlaylistSelectionDialog(
            onDismiss = { showPlaylistDialog = false }
        )
    }
}

/**
 * Profile header: theme-primary to background gradient, large round avatar,
 * bold name, then an outlined pill (edit / log in) and a gear to Settings.
 */
@Composable
private fun AccountProfileHeader(
    topPadding: Dp,
    isLoggedIn: Boolean,
    isGuest: Boolean,
    name: String,
    subtitle: String,
    avatarUrl: String?,
    onPrimaryAction: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.surface,
                    )
                )
            )
            .padding(top = topPadding + 8.dp, bottom = 16.dp)
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(112.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        painter = painterResource(
                            when {
                                isLoggedIn -> R.drawable.account
                                isGuest -> R.drawable.person
                                else -> R.drawable.login
                            }
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onPrimaryAction,
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(
                    text = when {
                        isLoggedIn -> stringResource(R.string.view_profile)
                        isGuest -> stringResource(R.string.edit)
                        else -> stringResource(R.string.action_login)
                    },
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.width(12.dp))

            IconButton(onClick = onSettingsClick) {
                Icon(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = stringResource(R.string.settings),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/**
 * Shown when the user taps "Login" with no account and no guest profile
 * active yet: let them pick between a real YouTube Premium sign-in and a
 * local-only guest profile (just a name + photo, no account required).
 */
@Composable
private fun LoginChoiceDialog(
    onDismiss: () -> Unit,
    onChooseYoutube: () -> Unit,
    onChooseGuest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.login_choice_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LoginChoiceOption(
                    icon = R.drawable.login,
                    title = stringResource(R.string.login_with_youtube),
                    subtitle = stringResource(R.string.login_with_youtube_desc),
                    onClick = onChooseYoutube
                )
                LoginChoiceOption(
                    icon = R.drawable.person,
                    title = stringResource(R.string.continue_as_guest),
                    subtitle = stringResource(R.string.continue_as_guest_desc),
                    onClick = onChooseGuest
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun LoginChoiceOption(
    icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            painter = painterResource(R.drawable.arrow_forward),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Lets a guest pick a display name and a photo from their device - stored
 * locally only, no account is created anywhere.
 */
@Composable
private fun GuestProfileDialog(
    initialName: String,
    initialAvatarUri: String,
    onDismiss: () -> Unit,
    onSave: (name: String, avatarUri: String?) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialName) }
    var avatarUri by remember { mutableStateOf(initialAvatarUri.ifBlank { null }) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Some providers don't support persistable permissions; the
                // uri may still work for this session.
            }
            avatarUri = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.guest_profile_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .clickable { photoPickerLauncher.launch(arrayOf("image/*")) },
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUri != null) {
                        AsyncImage(
                            model = avatarUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.person),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                TextButton(onClick = { photoPickerLauncher.launch(arrayOf("image/*")) }) {
                    Text(stringResource(R.string.choose_photo))
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.guest_username_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), avatarUri) }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun SettingsSection(
    title: String? = null,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        content()
    }
}

@Composable
private fun SettingsClickableItem(
    title: String,
    subtitle: String? = null,
    showChevron: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (showChevron) {
            Spacer(Modifier.width(12.dp))
            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun AppVersionFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun TokenEditorDialog(
    innerTubeCookie: String,
    visitorData: String,
    dataSyncId: String,
    accountNamePref: String,
    accountEmail: String,
    accountChannelHandle: String,
    onInnerTubeCookieChange: (String) -> Unit,
    onPoTokenChange: (String) -> Unit,
    onVisitorDataChange: (String) -> Unit,
    onDataSyncIdChange: (String) -> Unit,
    onAccountNameChange: (String) -> Unit,
    onAccountEmailChange: (String) -> Unit,
    onAccountChannelHandleChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val text = """
        ***INNERTUBE COOKIE*** =$innerTubeCookie
        ***VISITOR DATA*** =$visitorData
        ***DATASYNC ID*** =$dataSyncId
        ***PO TOKEN*** =${YouTube.poToken.orEmpty()}
        ***ACCOUNT NAME*** =$accountNamePref
        ***ACCOUNT EMAIL*** =$accountEmail
        ***ACCOUNT CHANNEL HANDLE*** =$accountChannelHandle
    """.trimIndent()

    TextFieldDialog(
        initialTextFieldValue = TextFieldValue(text),
        onDone = { data ->
            data.split("\n").forEach {
                when {
                    it.startsWith("***INNERTUBE COOKIE*** =") -> onInnerTubeCookieChange(it.substringAfter("="))
                    it.startsWith("***VISITOR DATA*** =") -> onVisitorDataChange(it.substringAfter("="))
                    it.startsWith("***DATASYNC ID*** =") -> onDataSyncIdChange(it.substringAfter("="))
                    it.startsWith("***PO TOKEN*** =") -> onPoTokenChange(it.substringAfter("="))
                    it.startsWith("***ACCOUNT NAME*** =") -> onAccountNameChange(it.substringAfter("="))
                    it.startsWith("***ACCOUNT EMAIL*** =") -> onAccountEmailChange(it.substringAfter("="))
                    it.startsWith("***ACCOUNT CHANNEL HANDLE*** =") -> onAccountChannelHandleChange(it.substringAfter("="))
                }
            }
        },
        onDismiss = onDismiss,
        singleLine = false,
        maxLines = 20,
        isInputValid = {
            it.isNotEmpty() && "SAPISID" in parseCookieString(it)
        },
        extraContent = {
            InfoLabel(text = stringResource(R.string.token_adv_login_description))
        }
    )
}

@Composable
private fun PlaylistSelectionDialog(onDismiss: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val (initialSelected, _) = rememberPreference(SelectedYtmPlaylistsKey, "")
    val selectedList = remember { mutableStateListOf<String>() }

    LaunchedEffect(initialSelected) {
        selectedList.clear()
        if (initialSelected.isNotEmpty()) {
            selectedList.addAll(
                initialSelected.split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            )
        }
    }

    var loading by remember { mutableStateOf(true) }
    val playlists = remember { mutableStateListOf<com.arturo254.opentune.innertube.models.PlaylistItem>() }

    LaunchedEffect(Unit) {
        loading = true
        com.arturo254.opentune.innertube.YouTube
            .library("FEmusic_liked_playlists")
            .completed()
            .onSuccess { page ->
                playlists.clear()
                playlists.addAll(
                    page.items
                        .filterIsInstance<com.arturo254.opentune.innertube.models.PlaylistItem>()
                        .filterNot { it.id == "LM" || it.id == "SE" }
                        .reversed()
                )
            }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        confirmButton = {
            TextButton(
                onClick = {
                    com.arturo254.opentune.utils.PreferenceStore.launchEdit(context.dataStore) {
                        this[SelectedYtmPlaylistsKey] = selectedList.joinToString(",")
                    }
                    onDismiss()
                }
            ) {
                Text(
                    text = stringResource(R.string.save),
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel_button))
            }
        },
        title = {
            Text(
                text = stringResource(R.string.select_playlist_to_sync),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {

                    val density = LocalDensity.current
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        stroke = Stroke(
                            width = with(density) { 2.dp.toPx() }
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(playlists) { pl ->
                        val isSelected = selectedList.contains(pl.id)
                        val backgroundColor by animateColorAsState(
                            targetValue = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else
                                Color.Transparent,
                            label = "playlistItemColor"
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(backgroundColor)
                                .clickable {
                                    if (isSelected) selectedList.remove(pl.id)
                                    else selectedList.add(pl.id)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) selectedList.add(pl.id)
                                    else selectedList.remove(pl.id)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            Spacer(Modifier.width(8.dp))

                            AsyncImage(
                                model = pl.thumbnail,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )

                            Spacer(Modifier.width(12.dp))

                            Text(
                                text = pl.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    )
}
