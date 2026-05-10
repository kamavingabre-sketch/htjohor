package id.go.medanjohor.walkietalkie.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object AudioConfig {
    const val SAMPLE_RATE = 16000
    const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
}

class AudioRecorder(private val onAudioData: (ByteArray) -> Unit) {

    private var recorder: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isRecording = false

    fun start() {
        if (isRecording) return
        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            AudioConfig.SAMPLE_RATE,
            AudioConfig.CHANNEL_IN,
            AudioConfig.ENCODING,
            AudioConfig.BUFFER_SIZE
        )
        recorder?.startRecording()
        isRecording = true
        recordingJob = scope.launch {
            val buffer = ByteArray(AudioConfig.BUFFER_SIZE)
            while (isActive && isRecording) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    onAudioData(buffer.copyOf(read))
                }
            }
        }
    }

    fun stop() {
        isRecording = false
        recordingJob?.cancel()
        recorder?.stop()
        recorder?.release()
        recorder = null
    }
}

class AudioPlayer {
    private var track: AudioTrack? = null

    init {
        val bufferSize = AudioTrack.getMinBufferSize(
            AudioConfig.SAMPLE_RATE,
            AudioConfig.CHANNEL_OUT,
            AudioConfig.ENCODING
        )
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioConfig.ENCODING)
                    .setSampleRate(AudioConfig.SAMPLE_RATE)
                    .setChannelMask(AudioConfig.CHANNEL_OUT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track?.play()
    }

    fun play(data: ByteArray) {
        track?.write(data, 0, data.size)
    }

    fun release() {
        track?.stop()
        track?.release()
        track = null
    }
}
