package org.xvdr.extractor;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.media.PlaybackParams;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.MediaCodecTrackRenderer;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.audio.AudioTrack;
import com.google.android.exoplayer.util.PriorityHandlerThread;

import org.xvdr.msgexchange.Packet;
import org.xvdr.msgexchange.Session;
import org.xvdr.robotv.client.Connection;
import org.xvdr.robotv.client.StreamBundle;

public class Player implements ExoPlayer.Listener, Session.Callback, RoboTvSampleSource.Listener, MediaCodecAudioTrackRenderer.EventListener, MediaCodecVideoTrackRenderer.EventListener {

    private static final String TAG = "Player";

    public final static int CHANNELS_DEFAULT = 0;
    public final static int CHANNELS_STEREO = 2;
    public final static int CHANNELS_SURROUND = 4;
    public final static int CHANNELS_DIGITAL51 = 6;

    public interface Listener  {

        void onPlayerStateChanged(boolean playWhenReady, int playbackState);

        void onPlayerError(Exception e);

        void onNotification(Packet notification);

        void onDisconnect();

        void onReconnect();

        void onDrawnToSurface(Surface surface);

        void onTracksChanged(StreamBundle bundle);

        void onAudioTrackChanged(StreamBundle.Stream stream);

        void onVideoTrackChanged(StreamBundle.Stream stream);

        void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs);
    }

    private static final int RENDERER_COUNT = 2;
    protected static final int MIN_BUFFER_MS = 2000;
    protected static final int MIN_REBUFFER_MS = 3000;

    private static final int RENDERER_VIDEO = 0;
    private static final int RENDERER_AUDIO = 1;

    protected ExoPlayer mExoPlayer;
    private RoboTvSampleSource mSampleSource;
    protected Connection mConnection = null;

    private MediaCodecVideoTrackRenderer mVideoRenderer = null;
    private MediaCodecAudioTrackRenderer mAudioRenderer = null;

    private Listener mListener;
    private Handler mHandler;
    private PriorityHandlerThread mHandlerThread;
    private Surface mSurface;
    private String mServer;
    private Context mContext;
    private AudioCapabilities mAudioCapabilities;
    private boolean mAudioPassthrough;
    private int mChannelConfiguration;

    public Player(Context context, String server, String language, Listener listener) {
        this(context, server, language, listener, false, CHANNELS_SURROUND);
    }

    public Player(Context context, String server, String language, Listener listener, boolean audioPassthrough) {
        this(context, server, language, listener, audioPassthrough, CHANNELS_SURROUND);
    }

    public Player(Context context, String server, String language, Listener listener, boolean audioPassthrough, int wantedChannelConfiguration) {
        mServer = server;
        mContext = context;
        mListener = listener;
        mAudioPassthrough = audioPassthrough;
        mAudioCapabilities = AudioCapabilities.getCapabilities(mContext);

        if(wantedChannelConfiguration == CHANNELS_DIGITAL51 && mAudioCapabilities.getMaxChannelCount() < 6) {
            mChannelConfiguration = CHANNELS_SURROUND;
        }
        else {
            mChannelConfiguration = wantedChannelConfiguration;
        }

        mExoPlayer = ExoPlayer.Factory.newInstance(RENDERER_COUNT, MIN_BUFFER_MS, MIN_REBUFFER_MS);
        mExoPlayer.addListener(this);

        // create connection
        mConnection = new Connection("roboTV Player", language, true);
        mConnection.addCallback(this);

        mHandlerThread = new PriorityHandlerThread("roboTV:player", android.os.Process.THREAD_PRIORITY_DEFAULT);
        mHandlerThread.start();

        mHandler = new Handler(mHandlerThread.getLooper());
    }

    public void release() {
        stop();

        mHandler = null;
        mHandlerThread.interrupt();

        mExoPlayer.removeListener(this);
        mExoPlayer.release();
        mExoPlayer = null;

        if(mConnection != null) {
            mConnection.closeStream();
            mConnection.close();
            mConnection.removeAllCallbacks();
            mConnection = null;
        }

        mVideoRenderer = null;
        mAudioRenderer = null;
        mSampleSource = null;
    }

    public void setSurface(Surface surface) {
        mSurface = surface;

        if(mExoPlayer == null || mVideoRenderer == null) {
            return;
        }

        mExoPlayer.sendMessage(mVideoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, mSurface);
    }

    public void setStreamVolume(float volume) {
        if(mAudioRenderer != null) {
            mExoPlayer.sendMessage(mAudioRenderer, MediaCodecAudioTrackRenderer.MSG_SET_VOLUME, volume);
        }
    }

    protected void prepare() {
        // prepare player
        mExoPlayer.prepare(mVideoRenderer, mAudioRenderer);

        mExoPlayer.setSelectedTrack(RENDERER_AUDIO, ExoPlayer.TRACK_DEFAULT);
        mExoPlayer.setSelectedTrack(RENDERER_VIDEO, ExoPlayer.TRACK_DEFAULT);
    }

    public void play() {
        mExoPlayer.setPlayWhenReady(true);
    }

    public void pause(boolean on) {
        Packet req = mConnection.CreatePacket(Connection.XVDR_CHANNELSTREAM_PAUSE, Connection.XVDR_CHANNEL_REQUEST_RESPONSE);
        req.putU32(on ? 1L : 0L);

        mConnection.transmitMessage(req);

        setPlaybackSpeed(1);
        mExoPlayer.setPlayWhenReady(!on);
    }

    public void stop() {
        mExoPlayer.stop();
    }

    protected boolean open() {

        // open server connection
        if(!mConnection.isOpen() && !mConnection.open(mServer)) {
            return false;
        }

        // create samplesource
        mSampleSource = new RoboTvSampleSource(mConnection, mHandler, mAudioCapabilities, mAudioPassthrough, mChannelConfiguration);
        mSampleSource.setListener(this);

        mVideoRenderer = new MediaCodecVideoTrackRenderer(
            mContext,
            mSampleSource,
            MediaCodecSelector.DEFAULT,
            MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT,
            5000, // joining time
            null,
            true,
            mHandler,
            this,
            50);

        mAudioRenderer = new MediaCodecAudioTrackRenderer(
            mSampleSource,
            MediaCodecSelector.DEFAULT,
            null,
            true,
            mHandler,
            this,
            mAudioCapabilities,
            AudioManager.STREAM_MUSIC);

        if(mSurface != null) {
            mExoPlayer.sendMessage(mVideoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, mSurface);
        }


        return true;
    }

    public void close() {
        stop();
        mConnection.close();
    }

    public boolean selectAudioTrack(int trackId) {
        return mSampleSource.selectAudioTrack(trackId);
    }

    public long getStartPositionWallclock() {
        return mSampleSource.getStartPositionWallclock();
    }

    public long getCurrentPositionWallclock() {
        return mSampleSource.getCurrentPositionWallclock();
    }

    public void seekTo(long wallclockTimeMs) {
        mExoPlayer.seekTo(wallclockTimeMs / 1000);
    }

    public void setPlaybackSpeed(int speed) {
        if(speed == mSampleSource.getPlaybackSpeed()) {
            return;
        }

        Log.d(TAG, "playback speed: " + speed);

        // reverse playback ?
        if(speed < 0) {
            return;
        }

        if(speed == 1) {
            mExoPlayer.setSelectedTrack(RoboTvSampleSource.TRACK_AUDIO, ExoPlayer.TRACK_DEFAULT);
        }
        else {
            mExoPlayer.setSelectedTrack(RoboTvSampleSource.TRACK_AUDIO, ExoPlayer.TRACK_DISABLED);
        }

        mSampleSource.setPlaybackSpeed((int)speed);
    }

    static public String nameOfChannelConfiguration(int channelConfiguration) {
        switch(channelConfiguration) {
            case CHANNELS_DEFAULT:
                return "default (unknown)";

            case CHANNELS_STEREO:
                return "stereo";

            case CHANNELS_SURROUND:
                return "surround";

            case CHANNELS_DIGITAL51:
                return "digital51";
        }

        return "invalid configuration";
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        mListener.onPlayerStateChanged(playWhenReady, playbackState);
    }

    @Override
    public void onPlayWhenReadyCommitted() {
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        mListener.onPlayerError(error);
    }

    @Override
    public void onNotification(final Packet notification) {
        mListener.onNotification(notification);
    }

    @Override
    public void onDisconnect() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onDisconnect();
            }
        });
    }

    @Override
    public void onReconnect() {
        if(mConnection == null) {
            return;
        }

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mConnection.login();
                mListener.onReconnect();
            }
        }, 3000);
    }

    @Override
    public void onTracksChanged(StreamBundle streamBundle) {
        mListener.onTracksChanged(streamBundle);
    }

    @Override
    public void onAudioTrackChanged(StreamBundle.Stream stream) {
        mListener.onAudioTrackChanged(stream);
    }

    @Override
    public void onVideoTrackChanged(StreamBundle.Stream stream) {
        mListener.onVideoTrackChanged(stream);
    }

    @Override
    public void onAudioTrackInitializationError(AudioTrack.InitializationException e) {
    }

    @Override
    public void onAudioTrackWriteError(AudioTrack.WriteException e) {

    }

    @Override
    public void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
        mListener.onAudioTrackUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
    }

    @Override
    public void onDecoderInitializationError(MediaCodecTrackRenderer.DecoderInitializationException e) {
    }

    @Override
    public void onCryptoError(MediaCodec.CryptoException e) {
    }

    @Override
    public void onDecoderInitialized(String decoderName, long elapsedRealtimeMs, long initializationDurationMs) {
    }

    @Override
    public void onDroppedFrames(int count, long elapsed) {
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
    }

    @Override
    public void onDrawnToSurface(Surface surface) {
        mListener.onDrawnToSurface(surface);
    }

}
