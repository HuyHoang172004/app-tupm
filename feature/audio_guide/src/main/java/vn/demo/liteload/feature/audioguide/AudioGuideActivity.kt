package vn.demo.liteload.feature.audioguide

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import vn.demo.liteload.navigation.SplitFeatureActivity

class AudioGuideActivity : SplitFeatureActivity() {
    private data class Track(val title: Int, val transcript: Int, val audio: Int)
    private val tracks = listOf(
        Track(R.string.audio_guide_town_title, R.string.audio_guide_town_body, R.raw.audio_guide_town),
        Track(R.string.audio_guide_river_title, R.string.audio_guide_river_body, R.raw.audio_guide_river),
        Track(R.string.audio_guide_food_title, R.string.audio_guide_food_body, R.raw.audio_guide_food)
    )
    private var player: MediaPlayer? = null
    private var position = 0
    private var loadedTrack = -1
    private var focusRequest: AudioFocusRequest? = null
    private var ownsAudioFocus = false
    private val audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change != AudioManager.AUDIOFOCUS_GAIN) pauseAudio()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        position = savedInstanceState?.getInt("audio_position", 0) ?: 0
        loadedTrack = selectedItem
        setContentView(R.layout.audio_guide_screen)
        findViewById<Button>(R.id.audio_guide_back).setOnClickListener { navigateBack() }
        findViewById<ListView>(R.id.audio_guide_list).apply {
            adapter = ArrayAdapter(this@AudioGuideActivity, android.R.layout.simple_list_item_1,
                tracks.map { getString(it.title) })
            setOnItemClickListener { _, _, index, _ -> selectItem(index) }
        }
        findViewById<Button>(R.id.audio_guide_play).setOnClickListener { playAudio() }
        findViewById<Button>(R.id.audio_guide_pause).setOnClickListener { pauseAudio() }
        findViewById<Button>(R.id.audio_guide_stop).setOnClickListener {
            releaseAudio(keepPosition = false)
            status(R.string.audio_guide_ready)
        }
        renderSelection()
    }

    override fun renderSelection() {
        if (selectedItem >= tracks.size) { selectItem(-1); return }
        if (loadedTrack != selectedItem) {
            releaseAudio(keepPosition = false)
            loadedTrack = selectedItem
        }
        findViewById<View>(R.id.audio_guide_list).visibility = if (selectedItem < 0) View.VISIBLE else View.GONE
        findViewById<View>(R.id.audio_guide_detail).visibility = if (selectedItem < 0) View.GONE else View.VISIBLE
        if (selectedItem >= 0) tracks[selectedItem].let {
            findViewById<TextView>(R.id.audio_guide_detail_title).setText(it.title)
            findViewById<TextView>(R.id.audio_guide_detail_body).setText(it.transcript)
            status(if (position > 0) R.string.audio_guide_paused else R.string.audio_guide_ready)
        }
    }

    private fun playAudio() {
        if (selectedItem !in tracks.indices || player?.isPlaying == true) return
        try {
            if (!requestAudioFocus()) { status(R.string.audio_guide_error); return }
            val current = player ?: MediaPlayer().also { created ->
                player = created // release even if setDataSource/prepare throws
                created.setAudioAttributes(audioAttributes)
                resources.openRawResourceFd(tracks[selectedItem].audio).use { fd ->
                    created.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                }
                created.prepare() // Small bundled PCM demo clips; no network I/O.
                created.setOnCompletionListener {
                    releaseAudio(keepPosition = false)
                    status(R.string.audio_guide_finished)
                }
                created.setOnErrorListener { _, _, _ ->
                    releaseAudio(keepPosition = false)
                    status(R.string.audio_guide_error)
                    true
                }
                if (position > 0) created.seekTo(position.coerceAtMost(created.duration))
            }
            current.start()
            status(R.string.audio_guide_playing)
        } catch (_: Exception) {
            releaseAudio(keepPosition = false)
            status(R.string.audio_guide_error)
        }
    }

    private fun pauseAudio() {
        player?.let { current ->
            if (current.isPlaying) current.pause()
            position = current.currentPosition
        }
        abandonAudioFocus()
        status(R.string.audio_guide_paused)
    }
    private fun requestAudioFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes).setOnAudioFocusChangeListener(focusListener).build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        ownsAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return ownsAudioFocus
    }
    private fun abandonAudioFocus() {
        if (!ownsAudioFocus) return
        ownsAudioFocus = false
        if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
        focusRequest = null
    }
    private fun releaseAudio(keepPosition: Boolean) {
        player?.let {
            if (keepPosition) position = it.currentPosition
            it.setOnCompletionListener(null)
            it.setOnErrorListener(null)
            it.release()
        }
        player = null
        if (!keepPosition) position = 0
        abandonAudioFocus()
    }
    private fun status(text: Int) { findViewById<TextView>(R.id.audio_guide_status)?.setText(text) }
    override fun onStop() {
        releaseAudio(keepPosition = true)
        status(R.string.audio_guide_paused)
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("audio_position", player?.currentPosition ?: position)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        releaseAudio(keepPosition = true)
        super.onDestroy()
    }
}
