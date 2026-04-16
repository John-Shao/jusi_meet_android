package com.jusi.meet.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jusi.meet.JusiMeetApp
import com.jusi.meet.R
import com.jusi.meet.ui.login.LoginScreen
import com.jusi.meet.ui.main.MainTabScreen
import com.jusi.meet.ui.preview.PreviewMode
import com.jusi.meet.ui.preview.PreviewScreen
import com.jusi.meet.ui.room.RoomScreen
import kotlinx.coroutines.delay
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val CREATE_PREVIEW = "create_preview"
    const val JOIN_PREVIEW = "join_preview"

    private const val ROOM_BASE = "room"
    const val ROOM = "$ROOM_BASE/{roomId}/{url}/{token}/{name}/{slug}/{isAdmin}/{mic}/{cam}"

    fun room(
        roomId: String, url: String, token: String, name: String, slug: String,
        isAdmin: Boolean, mic: Boolean, cam: Boolean,
    ): String {
        fun enc(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8.name())
        return "$ROOM_BASE/${enc(roomId)}/${enc(url)}/${enc(token)}/${enc(name)}/${enc(slug)}/$isAdmin/$mic/$cam"
    }

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
                onSignedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.CREATE_PREVIEW) {
            PreviewScreen(
                mode = PreviewMode.Create,
                onEnterRoom = { roomId, url, token, name, slug, isAdmin, mic, cam ->
                    navController.navigate(Routes.room(roomId, url, token, name, slug, isAdmin, mic, cam)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onClose = { navController.popBackStack() },
            )
        }

        composable(Routes.JOIN_PREVIEW) {
            PreviewScreen(
                mode = PreviewMode.Join,
                onEnterRoom = { roomId, url, token, name, slug, isAdmin, mic, cam ->
                    navController.navigate(Routes.room(roomId, url, token, name, slug, isAdmin, mic, cam)) {
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
                navArgument("isAdmin") { type = NavType.BoolType },
                navArgument("mic") { type = NavType.BoolType },
                navArgument("cam") { type = NavType.BoolType },
            ),
        ) { entry ->
            val args = entry.arguments!!
            RoomScreen(
                roomId = Routes.decode(args.getString("roomId").orEmpty()),
                livekitUrl = Routes.decode(args.getString("url").orEmpty()),
                livekitToken = Routes.decode(args.getString("token").orEmpty()),
                roomName = Routes.decode(args.getString("name").orEmpty()),
                roomSlug = Routes.decode(args.getString("slug").orEmpty()),
                isAdmin = args.getBoolean("isAdmin", false),
                initialMicEnabled = args.getBoolean("mic", true),
                initialCameraEnabled = args.getBoolean("cam", true),
                onLeave = { hostEnded ->
                    if (hostEnded) hostEndedSheetVisible = true
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }
    }

    if (hostEndedSheetVisible) {
        HostEndedSheet(onDismiss = { hostEndedSheetVisible = false })
    }
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
