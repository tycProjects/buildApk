package com.ryan.vietsubai.editor

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer

class EditorController(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply { setHandleAudioBecomingNoisy(true) }
    fun load(uri: Uri) { player.stop(); player.clearMediaItems(); player.setMediaItem(MediaItem.fromUri(uri)); player.prepare() }
    fun playPause() { if (player.isPlaying) player.pause() else player.play() }
    fun seek(ms: Long) { player.seekTo(ms) }
    fun speed(value: Float) { player.playbackParameters = PlaybackParameters(value) }
    fun volume(value: Float) { player.volume = value.coerceIn(0f, 1f) }
    fun release() { player.release() }
}
