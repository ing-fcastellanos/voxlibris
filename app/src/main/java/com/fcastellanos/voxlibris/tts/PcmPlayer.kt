package com.fcastellanos.voxlibris.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.SystemClock

/** Reproduce un buffer corto de audio de forma bloqueante (muestras de voz en el catálogo). */
object PcmPlayer {

    fun playBlocking(samples: FloatArray, sampleRate: Int, rate: Float = 1f) {
        if (samples.isEmpty()) return
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(samples.size * Float.SIZE_BYTES)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            if (rate != 1f) {
                runCatching { track.playbackParams = PlaybackParams().setSpeed(rate).setPitch(1f) }
            }
            track.play()
            SystemClock.sleep((samples.size * 1000L / sampleRate / rate).toLong() + 100)
            track.stop()
        } finally {
            track.release()
        }
    }
}
