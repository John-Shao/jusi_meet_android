package com.jusi.meet.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jusi.meet.JusiMeetApp
import com.jusi.meet.R
import com.jusi.meet.data.auth.SessionState
import com.jusi.meet.ui.discover.CreatorScreen
import com.jusi.meet.ui.discover.MyFavoritesScreen
import com.jusi.meet.ui.discover.MyWorksScreen
import com.jusi.meet.ui.discover.PostDetailScreen
import com.jusi.meet.ui.discover.PostEditorScreen
import com.jusi.meet.ui.login.LoginScreen
import com.jusi.meet.ui.main.MainTabScreen
import com.jusi.meet.ui.preview.PreviewMode
import com.jusi.meet.ui.preview.PreviewScreen
import com.jusi.meet.ui.room.RoomScreen
import com.jusi.meet.ui.settings.SettingsScreen
import kotlinx.coroutines.delay
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val CREATE_PREVIEW = "create_preview"
    const val JOIN_PREVIEW = "join_preview"

    private const val ROOM_BASE = "room"
    const val ROOM = "$ROOM_BASE/{roomId}/{url}/{token}/{name}/{slug}/{host}/{createdAt}/{isAdmin}/{mic}/{cam}"

    private const val HISTORY_BASE = "history_detail"
    const val HISTORY_DETAIL = "$HISTORY_BASE/{roomId}"

    // Discover feed routes
    private const val POST_DETAIL_BASE = "post_detail"
    const val POST_DETAIL = "$POST_DETAIL_BASE/{postId}"
    private const val CREATOR_BASE = "creator"
    const val CREATOR = "$CREATOR_BASE/{userId}"
    const val MY_WORKS = "my_works"
    const val MY_FAVORITES = "my_favorites"
    const val POST_EDITOR_CREATE = "post_editor_create"
    private const val POST_EDITOR_EDIT_BASE = "post_editor_edit"
    const val POST_EDITOR_EDIT = "$POST_EDITOR_EDIT_BASE/{postId}"

    fun room(
        roomId: String, url: String, token: String, name: String, slug: String,
        host: String?, createdAtMs: Long, isAdmin: Boolean, mic: Boolean, cam: Boolean,
    ): String {
        fun enc(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8.name())
        // Empty host serialises as "" which decode() round-trips cleanly; the
        // receiver treats blank as null.
        return "$ROOM_BASE/${enc(roomId)}/${enc(url)}/${enc(token)}/${enc(name)}/${enc(slug)}/${enc(host.orEmpty())}/$createdAtMs/$isAdmin/$mic/$cam"
    }

    fun historyDetail(roomId: String): String {
        val enc = URLEncoder.encode(roomId, StandardCharsets.UTF_8.name())
        return "$HISTORY_BASE/$enc"
    }

    fun postDetail(postId: String): String =
        "$POST_DETAIL_BASE/${URLEncoder.encode(postId, StandardCharsets.UTF_8.name())}"

    fun creator(userId: String): String =
        "$CREATOR_BASE/${URLEncoder.encode(userId, StandardCharsets.UTF_8.name())}"

    fun postEditorEdit(postId: String): String =
        "$POST_EDITOR_EDIT_BASE/${URLEncoder.encode(postId, StandardCharsets.UTF_8.name())}"

    fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}

@Composable
fun AppNav() {
    val context = LocalContext.current
    val app = context.applicationContext as JusiMeetApp
    val navController = rememberNavController()

    val startDestination = if (app.tokenStore.isLoggedIn()) Routes.HOME else Routes.LOGIN

    // Set by RoomScreen when the server disconnected us because the host
    // ended the meeting. Rendered as a bottom sheet overlay on top of
    // whatever NavHost is showing (typically Home after the auto pop).
    var hostEndedSheetVisible by remember { mutableStateOf(false) }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            MainTabScreen(
                onCreateMeeting = { navController.navigate(Routes.CREATE_PREVIEW) },
                onJoinMeeting = { navController.navigate(Routes.JOIN_PREVIEW) },
                onHistoryClick = { roomId ->
                    navController.navigate(Routes.historyDetail(roomId))
                },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onSignedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onPostClick = { postId ->
                    navController.navigate(Routes.postDetail(postId))
                },
                onCreatorClick = { userId ->
                    navController.navigate(Routes.creator(userId))
                },
                onPublishClick = { navController.navigate(Routes.POST_EDITOR_CREATE) },
                onMyWorksClick = { navController.navigate(Routes.MY_WORKS) },
                onMyFavoritesClick = { navController.navigate(Routes.MY_FAVORITES) },
            )
        }

        composable(
            route = Routes.POST_DETAIL,
            arguments = listOf(navArgument("postId") { type = NavType.StringType }),
        ) { entry ->
            val postId = Routes.decode(entry.arguments!!.getString("postId").orEmpty())
            PostDetailScreen(
                postId = postId,
                onBack = { navController.popBackStack() },
                onCreatorClick = { uid -> navController.navigate(Routes.creator(uid)) },
                onEditClick = { navController.navigate(Routes.postEditorEdit(postId)) },
                onDeleted = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.CREATOR,
            arguments = listOf(navArgument("userId") { type = NavType.StringType }),
        ) { entry ->
            val userId = Routes.decode(entry.arguments!!.getString("userId").orEmpty())
            CreatorScreen(
                userId = userId,
                onBack = { navController.popBackStack() },
                onPostClick = { pid -> navController.navigate(Routes.postDetail(pid)) },
            )
        }

        composable(Routes.MY_WORKS) {
            MyWorksScreen(
                onBack = { navController.popBackStack() },
                onPostClick = { pid -> navController.navigate(Routes.postDetail(pid)) },
                onPublishClick = { navController.navigate(Routes.POST_EDITOR_CREATE) },
            )
        }

        composable(Routes.MY_FAVORITES) {
            MyFavoritesScreen(
                onBack = { navController.popBackStack() },
                onPostClick = { pid -> navController.navigate(Routes.postDetail(pid)) },
            )
        }

        composable(Routes.POST_EDITOR_CREATE) {
            PostEditorScreen(
                postId = null,
                onBack = { navController.popBackStack() },
                onPublished = { newPostId ->
                    // Replace editor with the just-published post detail.
                    navController.popBackStack()
                    navController.navigate(Routes.postDetail(newPostId))
                },
            )
        }

        composable(
            route = Routes.POST_EDITOR_EDIT,
            arguments = listOf(navArgument("postId") { type = NavType.StringType }),
        ) { entry ->
            val postId = Routes.decode(entry.arguments!!.getString("postId").orEmpty())
            PostEditorScreen(
                postId = postId,
                onBack = { navController.popBackStack() },
                onPublished = { _ -> navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.CREATE_PREVIEW) {
            PreviewScreen(
                mode = PreviewMode.Create,
                onEnterRoom = { roomId, url, token, name, slug, host, createdAtMs, isAdmin, mic, cam ->
                    navController.navigate(Routes.room(roomId, url, token, name, slug, host, createdAtMs, isAdmin, mic, cam)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onClose = { navController.popBackStack() },
            )
        }

        composable(Routes.JOIN_PREVIEW) {
            PreviewScreen(
                mode = PreviewMode.Join,
                onEnterRoom = { roomId, url, token, name, slug, host, createdAtMs, isAdmin, mic, cam ->
                    navController.navigate(Routes.room(roomId, url, token, name, slug, host, createdAtMs, isAdmin, mic, cam)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onClose = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.ROOM,
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType },
                navArgument("url") { type = NavType.StringType },
                navArgument("token") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType },
                navArgument("slug") { type = NavType.StringType },
                navArgument("host") { type = NavType.StringType },
                navArgument("createdAt") { type = NavType.LongType },
                navArgument("isAdmin") { type = NavType.BoolType },
                navArgument("mic") { type = NavType.BoolType },
                navArgument("cam") { type = NavType.BoolType },
            ),
        ) { entry ->
            val args = entry.arguments!!
            val decodedHost = Routes.decode(args.getString("host").orEmpty())
            RoomScreen(
                roomId = Routes.decode(args.getString("roomId").orEmpty()),
                livekitUrl = Routes.decode(args.getString("url").orEmpty()),
                livekitToken = Routes.decode(args.getString("token").orEmpty()),
                roomName = Routes.decode(args.getString("name").orEmpty()),
                roomSlug = Routes.decode(args.getString("slug").orEmpty()),
                host = decodedHost.takeIf { it.isNotBlank() },
                createdAtMs = args.getLong("createdAt"),
                isAdmin = args.getBoolean("isAdmin", false),
                initialMicEnabled = args.getBoolean("mic", true),
                initialCameraEnabled = args.getBoolean("cam", true),
                onLeave = { hostEnded ->
                    if (hostEnded) hostEndedSheetVisible = true
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }

        composable(
            route = Routes.HISTORY_DETAIL,
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType },
            ),
        ) { entry ->
            val args = entry.arguments!!
            com.jusi.meet.ui.history.HistoryDetailScreen(
                roomId = Routes.decode(args.getString("roomId").orEmpty()),
                onBack = { navController.popBackStack() },
            )
        }
    }

    if (hostEndedSheetVisible) {
        HostEndedSheet(onDismiss = { hostEndedSheetVisible = false })
    }

    // Global session-expired handler. Any authed 401 caught by
    // SessionExpiredInterceptor flips this flag; we overlay a modal dialog
    // on top of whatever screen is visible and, on confirm, wipe the back
    // stack and navigate to Login.
    val sessionExpired by SessionState.expired.collectAsStateWithLifecycle()
    if (sessionExpired) {
        SessionExpiredDialog(
            onConfirm = {
                SessionState.reset()
                navController.navigate(Routes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
    }
}

@Composable
private fun SessionExpiredDialog(onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* non-dismissable — user must tap re-login */ },
        title = { Text(stringResource(R.string.session_expired_title)) },
        text = { Text(stringResource(R.string.session_expired_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.session_expired_action))
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    )
}

/**
 * Bottom sheet shown on Home after the server disconnected us because the
 * host ended the meeting. Auto-dismisses after 5 s; the button label shows
 * a live countdown until it does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HostEndedSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    var remaining by remember { mutableIntStateOf(5) }

    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000L)
            remaining--
        }
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.room_host_ended_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            )
            HorizontalDivider()
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    text = if (remaining > 0)
                        stringResource(R.string.room_host_ended_ack_countdown, remaining)
                    else stringResource(R.string.room_host_ended_ack),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
