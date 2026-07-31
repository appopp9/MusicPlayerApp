package com.musicplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.musicplayer.model.MusicRepository
import com.musicplayer.model.PlayerState
import com.musicplayer.model.RepeatMode
import com.musicplayer.model.Song
import com.musicplayer.model.SortOrder
import com.musicplayer.service.MusicPlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _allSongs.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filteredSongs = MutableStateFlow<List<Song>>(emptyList())
    val filteredSongs: StateFlow<List<Song>> = _filteredSongs.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_ADDED)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _albums = MutableStateFlow<Map<String, List<Song>>>(emptyMap())
    val albums: StateFlow<Map<String, List<Song>>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<Map<String, List<Song>>>(emptyMap())
    val artists: StateFlow<Map<String, List<Song>>> = _artists.asStateFlow()

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    init {
        loadSongs()
        connectToService()
    }

    private fun loadSongs() {
        viewModelScope.launch {
            _isLoading.value = true
            val songs = repository.loadSongs()
            _allSongs.value = songs
            _filteredSongs.value = songs
            _albums.value = repository.getSongsByAlbum(songs)
            _artists.value = repository.getSongsByArtist(songs)
            _isLoading.value = false
        }
    }

    private fun connectToService() {
        val sessionToken = SessionToken(
            getApplication(),
            ComponentName(getApplication(), MusicPlaybackService::class.java)
        )
        mediaControllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            mediaController = mediaControllerFuture?.get()
            setupPlayerListener()
            handler.post(progressRunnable)
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    updateProgress()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateProgress()
                _playerState.update {
                    it.copy(currentIndex = mediaController?.currentMediaItemIndex ?: -1)
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                val mode = when (repeatMode) {
                    Player.REPEAT_MODE_OFF -> RepeatMode.OFF
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else -> RepeatMode.OFF
                }
                _playerState.update { it.copy(repeatMode = mode) }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _playerState.update { it.copy(shuffleEnabled = shuffleModeEnabled) }
            }
        })
    }

    private fun updateProgress() {
        val controller = mediaController ?: return
        val duration = controller.duration.takeIf { it != androidx.media3.common.C.TIME_UNSET } ?: 0L
        val position = controller.currentPosition
        val index = controller.currentMediaItemIndex

        val songs = _playerState.value.songs
        val currentSong = if (index in songs.indices) songs[index] else null

        _playerState.update {
            it.copy(
                currentPosition = position,
                totalDuration = duration,
                currentIndex = index,
                currentSong = currentSong
            )
        }
    }

    fun playSong(song: Song, songList: List<Song> = _allSongs.value) {
        val controller = mediaController ?: return
        val index = songList.indexOfFirst { it.id == song.id }
        if (index == -1) return

        _playerState.update { it.copy(songs = songList) }

        val songsToUse = if (_playerState.value.shuffleEnabled) {
            songList.shuffled()
        } else {
            songList
        }
        _playerState.update { it.copy(songs = songsToUse) }

        viewModelScope.launch {
            val service = getApplication<Application>().let { app ->
                // Use MediaController to prepare and play
                val mediaItems = songsToUse.map { s ->
                    androidx.media3.common.MediaItem.Builder()
                        .setMediaId(s.id.toString())
                        .setUri(s.uri)
                        .setMediaMetadata(
                            androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(s.title)
                                .setArtist(s.artist)
                                .setAlbumTitle(s.album)
                                .setArtworkUri(s.albumUri)
                                .build()
                        )
                        .build()
                }
                controller.setMediaItems(mediaItems, 0, 0L)
                controller.prepare()
                controller.play()
            }
        }
    }

    fun playSongFromList(songs: List<Song>, index: Int) {
        val controller = mediaController ?: return
        if (index !in songs.indices) return

        _playerState.update { it.copy(songs = songs) }

        viewModelScope.launch {
            val mediaItems = songs.map { s ->
                androidx.media3.common.MediaItem.Builder()
                    .setMediaId(s.id.toString())
                    .setUri(s.uri)
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(s.title)
                            .setArtist(s.artist)
                            .setAlbumTitle(s.album)
                            .setArtworkUri(s.albumUri)
                            .build()
                    )
                    .build()
            }
            controller.setMediaItems(mediaItems, index, 0L)
            controller.prepare()
            controller.play()
        }
    }

    fun togglePlayPause() {
        mediaController?.let { player ->
            if (player.isPlaying) player.pause() else player.play()
        }
    }

    fun skipToNext() {
        mediaController?.let { player ->
            if (player.hasNextMediaItem()) player.seekToNext()
        }
    }

    fun skipToPrevious() {
        mediaController?.let { player ->
            if (player.currentPosition > 3000) {
                player.seekTo(0)
            } else if (player.hasPreviousMediaItem()) {
                player.seekToPrevious()
            }
        }
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
    }

    fun seekToProgress(progress: Float) {
        val duration = _playerState.value.totalDuration
        if (duration > 0) {
            mediaController?.seekTo((progress * duration).toLong())
        }
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun searchSongs(query: String) {
        _searchQuery.value = query
        _filteredSongs.value = if (query.isBlank()) {
            _allSongs.value
        } else {
            repository.searchSongs(_allSongs.value, query)
        }
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        _filteredSongs.value = when (order) {
            SortOrder.TITLE -> _allSongs.value.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> _allSongs.value.sortedBy { it.artist.lowercase() }
            SortOrder.ALBUM -> _allSongs.value.sortedBy { it.album.lowercase() }
            SortOrder.DATE_ADDED -> _allSongs.value.sortedByDescending { it.id }
            SortOrder.DURATION -> _allSongs.value.sortedByDescending { it.duration }
        }
    }

    fun getAlbumSongs(albumName: String): List<Song> {
        return _allSongs.value.filter { it.album == albumName }
    }

    fun getArtistSongs(artistName: String): List<Song> {
        return _allSongs.value.filter { it.artist == artistName }
    }

    override fun onCleared() {
        super.onCleared()
        handler.removeCallbacks(progressRunnable)
        mediaController?.release()
        mediaControllerFuture?.cancel(true)
    }
}
