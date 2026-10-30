package com.todorpg.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.todorpg.app.R

enum class Music(val res: Int) {
    CAMP(R.raw.music_camp),
    BATTLE(R.raw.music_battle),
}

enum class Sfx(val res: Int) {
    HIT(R.raw.sfx_hit),
    CRIT(R.raw.sfx_crit),
    MAGIC(R.raw.sfx_magic),
    HEAL(R.raw.sfx_heal),
    COMPLETE(R.raw.sfx_complete),
    CURSOR(R.raw.sfx_cursor),
    LEVEL_UP(R.raw.sfx_levelup),
    DEFEAT(R.raw.sfx_defeat),
    VICTORY(R.raw.sfx_victory),
    COIN(R.raw.sfx_coin),
}

/**
 * Looping background music with short cross-fades between tracks, plus a
 * SoundPool for effects. All calls are expected on the main thread.
 */
class AudioController(context: Context) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val pool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attributes).build()
    private val sfxIds = Sfx.entries.associateWith { pool.load(appContext, it.res, 1) }

    private var player: MediaPlayer? = null
    private var current: Music? = null
    private var wanted: Music = Music.CAMP
    private var foreground = false

    var musicEnabled = true
        set(value) {
            field = value
            refresh()
        }
    var sfxEnabled = true

    private val musicVolume = 0.55f

    fun setMusic(music: Music) {
        wanted = music
        refresh()
    }

    fun onForeground() {
        foreground = true
        refresh()
    }

    fun onBackground() {
        foreground = false
        player?.pause()
    }

    fun play(sfx: Sfx, volume: Float = 0.8f) {
        if (!sfxEnabled) return
        sfxIds[sfx]?.let { pool.play(it, volume, volume, 1, 0, 1f) }
    }

    private fun refresh() {
        if (!foreground) return
        if (!musicEnabled) {
            fadeOutAndRelease(player)
            player = null
            current = null
            return
        }
        val p = player
        if (current == wanted && p != null) {
            if (!p.isPlaying) p.start()
            return
        }
        fadeOutAndRelease(p)
        val next = MediaPlayer.create(appContext, wanted.res) ?: return
        next.isLooping = true
        next.setVolume(0f, 0f)
        next.start()
        fade(next, 0f, musicVolume, 600)
        player = next
        current = wanted
    }

    private fun fadeOutAndRelease(p: MediaPlayer?) {
        p ?: return
        fade(p, musicVolume, 0f, 350) {
            try {
                p.stop()
            } catch (_: IllegalStateException) {
            }
            p.release()
        }
    }

    private fun fade(p: MediaPlayer, from: Float, to: Float, durationMs: Long, done: (() -> Unit)? = null) {
        val steps = 12
        for (i in 1..steps) {
            handler.postDelayed({
                val v = from + (to - from) * i / steps
                try {
                    p.setVolume(v, v)
                } catch (_: IllegalStateException) {
                }
                if (i == steps) done?.invoke()
            }, durationMs * i / steps)
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        pool.release()
    }
}
