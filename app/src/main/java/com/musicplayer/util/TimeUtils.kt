package com.musicplayer.util

object TimeUtils {
    fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    fun formatDuration(duration: Long): String {
        val totalMinutes = duration / 60000
        return when {
            totalMinutes < 1 -> "<1 min"
            totalMinutes == 1L -> "1 min"
            else -> "$totalMinutes mins"
        }
    }
}
