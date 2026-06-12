package com.mimo.remote.data.remote

import android.content.Context
import android.util.Log
import com.mimo.remote.data.remote.WebSocketClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.webrtc.*

/**
 * WebRTC peer connection manager for voice/video calls.
 * Handles creating offers, answers, ICE candidates, and media tracks.
 */
class WebRtcManager(
    private val context: Context,
    private val wsClient: WebSocketClient
) {
    companion object {
        private const val TAG = "WebRtcManager"
        private val ICE_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
    }

    private var peerConnection: PeerConnection? = null
    private var factory: PeerConnectionFactory? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _callActive = MutableStateFlow(false)
    val callActive: StateFlow<Boolean> = _callActive.asStateFlow()

    private val _remoteAudioTrack = MutableStateFlow<AudioTrack?>(null)
    val remoteAudioTrack: StateFlow<AudioTrack?> = _remoteAudioTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    init {
        // Listen for media signaling messages from WebSocket
        scope.launch {
            wsClient.mediaMessages.collect { msg ->
                handleMediaMessage(msg)
            }
        }
    }

    /**
     * Initialize the WebRTC factory. Call once at app startup.
     */
    fun initialize() {
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val options = PeerConnectionFactory.Options()
        factory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()

        Log.i(TAG, "WebRTC factory initialized")
    }

    /**
     * Start an audio call
     */
    fun startAudioCall() {
        val pc = createPeerConnection() ?: return

        // Create audio track
        audioSource = factory?.createAudioMediaConstraints()
            ?.let { factory?.createAudioSource(it) }
        audioTrack = audioSource?.let { factory?.createAudioTrack("audio_track", it) }
        audioTrack?.let { pc.addTrack(it) }

        // Create and send offer
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.offerToReceiveAudio("true"))
            mandatory.add(MediaConstraints.offerToReceiveVideo("false"))
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        wsClient.sendMediaOffer(sdp.description, "audio")
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {
                        Log.e(TAG, "Failed to set local description: $p0")
                    }
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create offer: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)

        _callActive.value = true
    }

    /**
     * Start a video call (audio + video)
     */
    fun startVideoCall() {
        val pc = createPeerConnection() ?: return

        // Audio
        audioSource = factory?.createAudioMediaConstraints()
            ?.let { factory?.createAudioSource(it) }
        audioTrack = audioSource?.let { factory?.createAudioTrack("audio_track", it) }
        audioTrack?.let { pc.addTrack(it) }

        // Video - use front camera
        startVideoCapture(pc)

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.offerToReceiveAudio("true"))
            mandatory.add(MediaConstraints.offerToReceiveVideo("true"))
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        wsClient.sendMediaOffer(sdp.description, "video")
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {
                        Log.e(TAG, "Failed to set local description: $p0")
                    }
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create offer: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)

        _callActive.value = true
    }

    /**
     * End the current call
     */
    fun endCall() {
        peerConnection?.close()
        peerConnection = null
        audioTrack?.dispose()
        audioTrack = null
        audioSource?.dispose()
        audioSource = null
        videoTrack?.dispose()
        videoTrack = null
        videoSource?.dispose()
        videoSource = null
        videoCapturer?.dispose()
        videoCapturer = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        _callActive.value = false
        _remoteAudioTrack.value = null
        _remoteVideoTrack.value = null
        Log.i(TAG, "Call ended")
    }

    /**
     * Toggle audio mute
     */
    fun setAudioMuted(muted: Boolean) {
        audioTrack?.setEnabled(!muted)
    }

    /**
     * Toggle video mute
     */
    fun setVideoMuted(muted: Boolean) {
        videoTrack?.setEnabled(!muted)
    }

    fun destroy() {
        endCall()
        factory?.dispose()
        factory = null
        scope.cancel()
    }

    // ─── Internal ────────────────────────────────────────────

    private fun createPeerConnection(): PeerConnection? {
        val f = factory ?: run {
            Log.e(TAG, "Factory not initialized")
            return null
        }

        val rtcConfig = PeerConnection.RTCConfiguration(ICE_SERVERS).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        val pc = f.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE connection state: $p0")
                if (p0 == PeerConnection.IceConnectionState.DISCONNECTED ||
                    p0 == PeerConnection.IceConnectionState.FAILED) {
                    endCall()
                }
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    wsClient.sendMediaCandidate(
                        it.sdp,
                        it.sdpMid,
                        it.sdpMLineIndex
                    )
                }
            }
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {
                stream?.audioTracks?.firstOrNull()?.let { track ->
                    _remoteAudioTrack.value = track
                    track.setEnabled(true)
                    Log.i(TAG, "Remote audio track added")
                }
                stream?.videoTracks?.firstOrNull()?.let { track ->
                    _remoteVideoTrack.value = track
                    track.setEnabled(true)
                    Log.i(TAG, "Remote video track added")
                }
            }
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                mediaStreams?.forEach { stream ->
                    stream.audioTracks.forEach { track ->
                        _remoteAudioTrack.value = track as? AudioTrack
                        Log.i(TAG, "Remote audio track added (unified plan)")
                    }
                    stream.videoTracks.forEach { track ->
                        _remoteVideoTrack.value = track as? VideoTrack
                        Log.i(TAG, "Remote video track added (unified plan)")
                    }
                }
            }
        })

        peerConnection = pc
        return pc
    }

    private fun handleMediaMessage(msg: JSONObject) {
        when (msg.optString("type")) {
            "media:answer" -> {
                val sdp = msg.optString("sdp")
                val pc = peerConnection ?: return
                pc.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.d(TAG, "Remote description set (answer)")
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {
                        Log.e(TAG, "Failed to set remote description: $p0")
                    }
                }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
            }
            "media:candidate" -> {
                val candidate = IceCandidate(
                    msg.optString("sdpMid"),
                    msg.optInt("sdpMLineIndex"),
                    msg.optString("candidate")
                )
                peerConnection?.addIceCandidate(candidate)
            }
            "media:control" -> {
                when (msg.optString("action")) {
                    "end_call" -> endCall()
                    "mute_audio" -> setAudioMuted(true)
                    "unmute_audio" -> setAudioMuted(false)
                    "mute_video" -> setVideoMuted(true)
                    "unmute_video" -> setVideoMuted(false)
                }
            }
        }
    }

    private fun startVideoCapture(peerConnection: PeerConnection) {
        // For now, create a placeholder video track
        // Real implementation needs Camera2Capturer or ScreenCapturer
        videoSource = factory?.createVideoSource(false)
        videoTrack = videoSource?.let { factory?.createVideoTrack("video_track", it) }
        videoTrack?.let { peerConnection.addTrack(it) }
        Log.i(TAG, "Video capture started (placeholder)")
    }
}
