package com.musicplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.musicplayer.model.Song
import com.musicplayer.ui.components.MiniPlayer
import com.musicplayer.ui.screens.AlbumDetailScreen
import com.musicplayer.ui.screens.ArtistDetailScreen
import com.musicplayer.ui.screens.LibraryScreen
import com.musicplayer.ui.screens.NowPlayingScreen
import com.musicplayer.ui.screens.SearchScreen
import com.musicplayer.ui.theme.MusicPlayerTheme
import com.musicplayer.viewmodel.MusicViewModel
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // Permissions granted, songs will be loaded automatically
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()
        setContent {
            MusicPlayerTheme {
                MusicPlayerApp(viewModel = viewModel)
            }
        }
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions)
    }
}

sealed class Screen(val route: String) {
    data object Library : Screen("library")
    data object Search : Screen("search")
    data object NowPlaying : Screen("now_playing")
    data object AlbumDetail : Screen("album/{albumName}") {
        fun createRoute(albumName: String) = "album/$albumName"
    }
    data object ArtistDetail : Screen("artist/{artistName}") {
        fun createRoute(artistName: String) = "artist/$artistName"
    }
}

data class BottomNavItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: String
)

@Composable
fun MusicPlayerApp(viewModel: MusicViewModel) {
    val playerState by viewModel.playerState.collectAsState()

    var currentScreen by rememberSaveable { mutableStateOf(Screen.Library.route) }
    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedAlbum by rememberSaveable { mutableStateOf("") }
    var selectedArtist by rememberSaveable { mutableStateOf("") }
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }

    val bottomNavItems = listOf(
        BottomNavItem("Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic, Screen.Library.route),
        BottomNavItem("Search", Icons.Filled.Search, Icons.Outlined.Search, Screen.Search.route),
    )

    Scaffold(
        bottomBar = {
            Column {
                // Mini Player
                AnimatedVisibility(
                    visible = playerState.currentSong != null && !showNowPlaying,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    playerState.currentSong?.let { song ->
                        MiniPlayer(
                            song = song,
                            isPlaying = playerState.isPlaying,
                            progress = playerState.progress,
                            onPlayPauseClick = { viewModel.togglePlayPause() },
                            onNextClick = { viewModel.skipToNext() },
                            onMiniPlayerClick = { showNowPlaying = true }
                        )
                    }
                }

                // Bottom Navigation
                if (!showNowPlaying) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        bottomNavItems.forEachIndexed { index, item ->
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        imageVector = if (currentTab == index)
                                            item.selectedIcon
                                        else
                                            item.unselectedIcon,
                                        contentDescription = item.title
                                    )
                                },
                                label = { Text(item.title) },
                                selected = currentTab == index,
                                onClick = {
                                    currentTab = index
                                    currentScreen = item.route
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    bottom = if (showNowPlaying || playerState.currentSong == null)
                        paddingValues.calculateBottomPadding()
                    else
                        paddingValues.calculateBottomPadding() + 72.dp
                )
        ) {
            when {
                showNowPlaying -> {
                    NowPlayingScreen(
                        viewModel = viewModel,
                        onBackClick = { showNowPlaying = false }
                    )
                }
                currentScreen.startsWith("album/") -> {
                    AlbumDetailScreen(
                        viewModel = viewModel,
                        albumName = selectedAlbum,
                        onBackClick = {
                            currentScreen = Screen.Library.route
                            currentTab = 0
                        },
                        onSongClick = { song, songs ->
                            viewModel.playSongFromList(songs, songs.indexOf(song))
                        }
                    )
                }
                currentScreen.startsWith("artist/") -> {
                    ArtistDetailScreen(
                        viewModel = viewModel,
                        artistName = selectedArtist,
                        onBackClick = {
                            currentScreen = Screen.Library.route
                            currentTab = 0
                        },
                        onSongClick = { song, songs ->
                            viewModel.playSongFromList(songs, songs.indexOf(song))
                        }
                    )
                }
                currentScreen == Screen.Library.route -> {
                    LibraryScreen(
                        viewModel = viewModel,
                        onSongClick = { song, songs ->
                            viewModel.playSongFromList(songs, songs.indexOf(song))
                        },
                        onAlbumClick = { albumName ->
                            selectedAlbum = albumName
                            currentScreen = Screen.AlbumDetail.createRoute(albumName)
                        },
                        onArtistClick = { artistName ->
                            selectedArtist = artistName
                            currentScreen = Screen.ArtistDetail.createRoute(artistName)
                        }
                    )
                }
                currentScreen == Screen.Search.route -> {
                    SearchScreen(
                        viewModel = viewModel,
                        onSongClick = { song, songs ->
                            viewModel.playSongFromList(songs, songs.indexOf(song))
                        }
                    )
                }
            }
        }
    }
}
