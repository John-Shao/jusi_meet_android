package com.jusi.meet.ui.nav

import android.app.Application
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
import com.jusi.meet.ui.room.RoomScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Top-level navigation graph.
 *
 *   login → home → room
 *
 * The starting destination depends on whether a token is already in the
 * encrypted store.  We compute it once at composition time; if the user
 * signs out from Home we pop back to Login.
 */
object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    private const val ROOM_BASE = "room"
    const val ROOM_ARG_URL = "url"
    const val ROOM_ARG_TOKEN = "token"
    const val ROOM_ARG_NAME = "name"
    const val ROOM_ARG_SLUG = "slug"
    const val ROOM = "$ROOM_BASE/{$ROOM_ARG_URL}/{$ROOM_ARG_TOKEN}/{$ROOM_ARG_NAME}/{$ROOM_ARG_SLUG}"

    fun room(url: String, token: String, name: String, slug: String): String {
        fun enc(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8.name())
        return "$ROOM_BASE/${enc(url)}/${enc(token)}/${enc(name)}/${enc(slug)}"
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
                onJoinRoom = { url, token, name, slug ->
                    navController.navigate(Routes.room(url, token, name, slug))
                },
                onSignedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.ROOM,
            arguments = listOf(
                navArgument(Routes.ROOM_ARG_URL) { type = NavType.StringType },
                navArgument(Routes.ROOM_ARG_TOKEN) { type = NavType.StringType },
                navArgument(Routes.ROOM_ARG_NAME) { type = NavType.StringType },
                navArgument(Routes.ROOM_ARG_SLUG) { type = NavType.StringType },
            ),
        ) { entry ->
            val url = Routes.decode(entry.arguments?.getString(Routes.ROOM_ARG_URL).orEmpty())
            val token = Routes.decode(entry.arguments?.getString(Routes.ROOM_ARG_TOKEN).orEmpty())
            val name = Routes.decode(entry.arguments?.getString(Routes.ROOM_ARG_NAME).orEmpty())
            val slug = Routes.decode(entry.arguments?.getString(Routes.ROOM_ARG_SLUG).orEmpty())
            RoomScreen(
                livekitUrl = url,
                livekitToken = token,
                roomName = name,
                roomSlug = slug,
                onLeave = { navController.popBackStack() },
            )
        }
    }
}
