package com.nossam.smahobutton.action

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.nossam.smahobutton.logic.TALK_LOG_TAG
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
    private var sessionId = 0

    fun start(context: Context): Boolean {
        stop()?.delete()
        synchronized(lock) {
            val dir = File(context.cacheDir, "talk").apply { mkdirs() }
            sweepOld(dir)
            val out = File(dir, "talk-${System.currentTimeMillis()}.wav")
            val min = AudioRecord.getMinBufferSize(
                TALK_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (min <= 0) {
                Log.i(TALK_LOG_TAG, "recording failed buffer")
                return false
            }
            val chunk = min.coerceAtLeast(1280)
            repeat(2) { round ->
                val record = openRecord(min)
                if (record != null && startRecording(record)) {
                    val session = ++sessionId
                    running = true
                    audio = record
                    current = out
                    val worker = Thread({
                        write(record, out, chunk, session)
                    }, "smahobutton-talk")
                    thread = worker
                    worker.start()
                    Log.i(TALK_LOG_TAG, "recording started")
                    return true
                }
                record?.release()
                if (round == 0) {
                    try {
                        Thread.sleep(60)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
            }
            Log.i(TALK_LOG_TAG, "recording failed after retry")
            return false
        }
    }

    fun stop(): File? {
        val worker: Thread?
        val out: File?
        val record: AudioRecord?
        synchronized(lock) {
            running = false
            sessionId += 1
            worker = thread
            out = current
            record = audio
            thread = null
            current = null
            audio = null
            try {
                record?.stop()
            } catch (_: Exception) {
            }
        }
        try {
            worker?.join(800)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        try {
            record?.release()
        } catch (_: Exception) {
        }
        if (out == null || !out.exists() || out.length() <= 44L) {
            out?.delete()
            return null
        }
        return out
    }

    private fun startRecording(record: AudioRecord): Boolean {
        repeat(3) { attempt ->
            try {
                record.startRecording()
            } catch (_: Exception) {
            }
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) return true
            if (attempt < 2) {
                try {
                    Thread.sleep(40)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
        }
        return false
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

    private fun write(record: AudioRecord, out: File, chunk: Int, session: Int) {
        try {
            val buffer = ByteArray(chunk)
            var total = 0
            RandomAccessFile(out, "rw").use { file ->
                file.write(wavHeader(0))
                while (running && sessionId == session) {
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
            if (sessionId == session) running = false
        }
    }

    private fun sweepOld(dir: File) {
        val cutoff = System.currentTimeMillis() - 10 * 60 * 1000
        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
    }
}
