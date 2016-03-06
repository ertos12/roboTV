package org.xvdr.extractor;

import android.content.Context;

import org.xvdr.msgexchange.Packet;
import org.xvdr.robotv.client.Connection;

public class LiveTvPlayer extends Player {

    public LiveTvPlayer(Context context, String server, String language, Listener listener, boolean audioPassthrough, int wantedChannelConfiguration) {
        super(context, server, language, listener, audioPassthrough, wantedChannelConfiguration);
    }

    /**
     * Start streaming of a LiveTV channel with a default priority, without waiting for the first keyframe
     * @param channelUid the unique id of the channel
     * @return returns the status of the operation
     */
    public int openStream(int channelUid, String language) {
        return openStream(channelUid, language, false);
    }

    /**
     * Start streaming of a LiveTV channel with a default priority
     * @param channelUid the unique id of the channel
     * @param waitForKeyFrame start streaming after the first IFRAME has been received
     * @return returns the status of the operation
     */
    public int openStream(int channelUid, String language, boolean waitForKeyFrame) {
        return openStream(channelUid, language, waitForKeyFrame, 50);
    }

    /**
     * Start streaming of a LiveTV channel
     * @param channelUid the unique id of the channel
     * @param waitForKeyFrame start streaming after the first IFRAME has been received
     * @param priority priority of the received device on the server
     * @return returns the status of the operation
     */
    public int openStream(int channelUid, String language, boolean waitForKeyFrame, int priority) {
        // stop playback
        stop();

        // open server connection
        if(!open()) {
            return Connection.NORESPONSE;
        }

        int status = mConnection.openStream(channelUid, language, waitForKeyFrame, priority);

        if(status != Connection.SUCCESS) {
            return status;
        }

        prepare();
        play();

        return Connection.SUCCESS;
    }

    public void pauseStream(boolean pause) {
        Packet req = mConnection.CreatePacket(Connection.XVDR_CHANNELSTREAM_PAUSE, Connection.XVDR_CHANNEL_REQUEST_RESPONSE);
        req.putU32(pause ? 1L : 0L);

        mConnection.transmitMessage(req);
    }

}
