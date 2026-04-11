package com.jusi.meet.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jusi.meet.JusiMeetApp
import com.jusi.meet.ui.home.HomeScreen
import com.jusi.meet.ui.login.LoginScreen
import com.jusi.meet.ui.preview.PreviewMode
import com.jusi.meet.ui.preview.PreviewScreen
import com.jusi.meet.ui.room.RoomScreen
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
            HomeScreen(
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
                onLeave = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }
    }
}
