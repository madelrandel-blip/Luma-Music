/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.arturo254.opentune.App.Companion.forgetAccount
import com.arturo254.opentune.R
import com.arturo254.opentune.constants.GuestAvatarUriKey
import com.arturo254.opentune.constants.GuestModeEnabledKey
import com.arturo254.opentune.constants.GuestNameKey
import com.arturo254.opentune.constants.InnerTubeCookieKey
import com.arturo254.opentune.innertube.utils.parseCookieString
import com.arturo254.opentune.ui.component.IconButton
import com.arturo254.opentune.ui.component.TopSearch
import com.arturo254.opentune.ui.utils.backToMain
import com.arturo254.opentune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val listState = rememberLazyListState()

    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }

    var showLogoutDialog by remember { mutableStateOf(false) }

    // Play the staggered entrance only on the first visit, not when coming back from a sub-screen.
    var entrancePlayed by rememberSaveable { mutableStateOf(false) }
    val animateEntrance = remember { !entrancePlayed }
    LaunchedEffect(Unit) { entrancePlayed = true }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")
    val (guestModeEnabled, onGuestModeEnabledChange) = rememberPreference(GuestModeEnabledKey, false)
    val (_, onGuestNameChange) = rememberPreference(GuestNameKey, "")
    val (_, onGuestAvatarUriChange) = rememberPreference(GuestAvatarUriKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val isGuest = guestModeEnabled && !isLoggedIn

    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.POST_NOTIFICATIONS
    } else {
        null
    }

    var isStorageGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isNotificationGranted by remember {
        mutableStateOf(
            notificationPermission == null ||
                ContextCompat.checkSelfPermission(context, notificationPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        isStorageGranted = result[storagePermission] == true || isStorageGranted
        if (notificationPermission != null) {
            isNotificationGranted = result[notificationPermission] == true || isNotificationGranted
        }
    }

    val shouldShowPermissionHint = !isStorageGranted || !isNotificationGranted
    val hasUpdate = false

    val resetSearch: () -> Unit = {
        isSearching = false
        query = TextFieldValue()
        focusManager.clearFocus()
    }

    val quickToggles = buildQuickToggles()
    val settingsRows = buildSettingsRows(
        navController = navController,
        isAndroid12OrLater = isAndroid12OrLater,
        hasUpdate = hasUpdate,
        isLoggedIn = isLoggedIn,
        context = context,
        onOpenAccount = { navController.navigate("settings/account") },
        resetSearch = resetSearch,
    )
    val internalItems = buildInternalItems(navController, resetSearch)

    val queryText = query.text.trim()
    val showSearchBar = isSearching || queryText.isNotBlank()

    val contentState = SettingsContentState(
        quickToggles = quickToggles,
        rows = settingsRows,
        showPermissionBanner = shouldShowPermissionHint,
        showUpdateBanner = hasUpdate,
        latestVersion = latestVersionName,
        showLogout = isLoggedIn || isGuest,
        isSearchActive = false,
        onRequestPermission = {
            val toRequest = buildList {
                if (!isStorageGranted) add(storagePermission)
                if (!isNotificationGranted && notificationPermission != null) {
                    add(notificationPermission)
                }
            }
            if (toRequest.isNotEmpty()) {
                permissionLauncher.launch(toRequest.toTypedArray())
            }
        },
        onUpdateClick = { navController.navigate("settings/update") },
        onLogoutClick = { showLogoutDialog = true },
    )

    Scaffold(
        topBar = {
            if (!showSearchBar) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.settings),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = navController::navigateUp,
                            onLongClick = navController::backToMain,
                        ) {
                            Icon(
                                painterResource(R.drawable.arrow_back),
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { isSearching = true },
                            onLongClick = {},
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.search),
                                contentDescription = stringResource(R.string.search),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (!showSearchBar) {
                SettingsList(
                    state = contentState,
                    listState = listState,
                    topPadding = innerPadding.calculateTopPadding(),
                    animateEntrance = animateEntrance,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            AnimatedVisibility(
                visible = showSearchBar,
                enter = fadeIn(tween(durationMillis = 220)),
                exit = fadeOut(tween(durationMillis = 160)),
            ) {
                TopSearch(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = { focusManager.clearFocus() },
                    active = showSearchBar,
                    onActiveChange = { active ->
                        if (active) {
                            isSearching = true
                        } else {
                            resetSearch()
                        }
                    },
                    placeholder = { Text(text = stringResource(R.string.search)) },
                    leadingIcon = {
                        IconButton(
                            onClick = { resetSearch() },
                            onLongClick = {
                                if (queryText.isBlank()) {
                                    navController.backToMain()
                                }
                            },
                        ) {
                            Icon(
                                painterResource(R.drawable.arrow_back),
                                contentDescription = null,
                            )
                        }
                    },
                    trailingIcon = {
                        Row {
                            if (query.text.isNotBlank()) {
                                IconButton(
                                    onClick = { query = TextFieldValue() },
                                    onLongClick = {},
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        contentDescription = null,
                                    )
                                }
                            }
                        }
                    },
                    focusRequester = focusRequester,
                ) {
                    val searchState = if (queryText.isBlank()) {
                        contentState.copy(isSearchActive = true)
                    } else {
                        contentState.copy(
                            quickToggles = filterQuickToggles(quickToggles, queryText),
                            rows = filterSettingsItems(settingsRows + internalItems, queryText),
                            isSearchActive = true,
                        )
                    }
                    SettingsList(
                        state = searchState,
                        animateEntrance = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(text = stringResource(R.string.action_logout)) },
            text = { Text(text = stringResource(R.string.settings_logout_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        if (isLoggedIn) {
                            onInnerTubeCookieChange("")
                            forgetAccount(context)
                        } else if (isGuest) {
                            onGuestModeEnabledChange(false)
                            onGuestNameChange("")
                            onGuestAvatarUriChange("")
                        }
                    },
                ) {
                    Text(text = stringResource(R.string.action_logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
