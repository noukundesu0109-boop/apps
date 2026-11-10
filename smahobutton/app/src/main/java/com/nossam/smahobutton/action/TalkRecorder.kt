package com.nossam.smahobutton.action

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.nossam.smahobutton.logic.TALK_SAMPLE_RATE
import com.nossam.smahobutton.logic.wavHeader
import java.io.File
import java.io.RandomAccessFile

/** 16 kHz mono PCM written to a temporary WAV file in the cache. */
object TalkRecorder {
    private val lock = Any()
    private var audio: AudioRecord? = null
    private var thread: Thread? = null
    private var current: File? = null

    @Volatile
    private var running = false

    fun start(context: Context): Boolean {
        synchronized(lock) {
            if (running) return true
            val dir = File(context.cacheDir, "talk").apply { mkdirs() }
            sweepOld(dir)
            val out = File(dir, "talk-${System.currentTimeMillis()}.wav")
            val min = AudioRecord.getMinBufferSize(
                TALK_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (min <= 0) return false
            val record = openRecord(min) ?: return false
            try {
                record.startRecording()
            } catch (_: Exception) {
                record.release()
                return false
            }
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                record.release()
                return false
            }
            running = true
            audio = record
            current = out
            val worker = Thread({
                write(record, out, min.coerceAtLeast(1280))
            }, "smahobutton-talk")
            thread = worker
            worker.start()
            return true
        }
    }

    fun stop(): File? {
        val worker: Thread?
        val out: File?
        synchronized(lock) {
            if (!running && thread == null) return null
            running = false
            worker = thread
            out = current
            thread = null
            current = null
            try {
                audio?.stop()
            } catch (_: Exception) {
            }
        }
        try {
            worker?.join(1500)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        synchronized(lock) {
            try {
                audio?.release()
            } catch (_: Exception) {
            }
            audio = null
        }
        if (out == null || !out.exists() || out.length() <= 44L) {
            out?.delete()
            return null
        }
        return out
    }

    @SuppressLint("MissingPermission")
    private fun openRecord(minBytes: Int): AudioRecord? {
        val sources = intArrayOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
        for (source in sources) {
            val record = try {
                AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(TALK_SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(minBytes * 2)
                    .build()
            } catch (_: Exception) {
                null
            } ?: continue
            if (record.state == AudioRecord.STATE_INITIALIZED) return record
            record.release()
        }
        return null
    }

    private fun write(record: AudioRecord, out: File, chunk: Int) {
        try {
            val buffer = ByteArray(chunk)
            var total = 0
            RandomAccessFile(out, "rw").use { file ->
                file.write(wavHeader(0))
                while (running) {
                    val read = try {
                        record.read(buffer, 0, buffer.size)
                    } catch (_: Exception) {
                        break
                    }
                    if (read <= 0) break
                    file.write(buffer, 0, read)
                    total += read
                }
                file.seek(0)
                file.write(wavHeader(total))
            }
        } catch (_: Exception) {
            running = false
        }
    }

    private fun sweepOld(dir: File) {
        val cutoff = System.currentTimeMillis() - 10 * 60 * 1000
        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
    }
}
