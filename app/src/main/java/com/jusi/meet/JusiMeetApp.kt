package com.jusi.meet

import android.app.Application
import com.jusi.meet.data.api.ApiClient
import com.jusi.meet.data.auth.TokenStore
import com.jusi.meet.data.history.HistoryStore
import com.jusi.meet.data.repository.AuthRepository
import com.jusi.meet.data.repository.RoomRepository
import com.jusi.meet.overlay.ScreenShareOverlay

/**
 * Application class that owns the shared singletons for the app.
 *
 * MVP intentionally avoids a DI framework — the surface is small enough that
 * a hand-rolled service locator on [JusiMeetApp] keeps the code obvious.
 * If the app grows beyond a few screens, swap this for Hilt without churning
 * the call sites: every screen reads dependencies from a single property.
 */
class JusiMeetApp : Application() {

    lateinit var tokenStore: TokenStore
        private set
    lateinit var apiClient: ApiClient
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var roomRepository: RoomRepository
        private set
    lateinit var historyStore: HistoryStore
        private set

    override fun onCreate() {
        super.onCreate()
        tokenStore = TokenStore(this)
        apiClient = ApiClient(tokenStore)
        authRepository = AuthRepository(apiClient.authApi, tokenStore, apiClient.okHttp)
        roomRepository = RoomRepository(apiClient.roomApi)
        historyStore = HistoryStore(this)
        ScreenShareOverlay.init(this)
    }
}
