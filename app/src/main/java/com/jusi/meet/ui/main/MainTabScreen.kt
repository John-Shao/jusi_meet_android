package com.jusi.meet.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.jusi.meet.R
import com.jusi.meet.ui.home.HomeScreen
import com.jusi.meet.ui.profile.ProfileScreen

private data class TabItem(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val tabs = listOf(
    TabItem(R.string.tab_meeting, Icons.Filled.Videocam, Icons.Outlined.Videocam),
    TabItem(R.string.tab_profile, Icons.Filled.Person, Icons.Outlined.Person),
)

@Composable
fun MainTabScreen(
    onCreateMeeting: () -> Unit,
    onJoinMeeting: () -> Unit,
    onSignedOut: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == index) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = stringResource(tab.labelRes),
                            )
                        },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        when (selectedTab) {
            0 -> {
                androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding)) {
                    HomeScreen(
                        onCreateMeeting = onCreateMeeting,
                        onJoinMeeting = onJoinMeeting,
                    )
                }
            }
            1 -> {
                androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding)) {
                    ProfileScreen(onSignedOut = onSignedOut)
                }
            }
        }
    }
}
