package org.xvdr.robotv.artwork.provider;

import android.util.Log;

import org.xvdr.msgexchange.Packet;
import org.xvdr.robotv.artwork.ArtworkHolder;
import org.xvdr.robotv.artwork.Event;
import org.xvdr.robotv.client.Connection;

public class RoboTvProvider extends SimpleArtworkProvider {

    static final String TAG = "RoboTvProvider";

    private Connection mConnection;

    public RoboTvProvider(Connection connection) {
        mConnection = connection;
    }

    @Override
    public ArtworkHolder search(Event event) {

        // request artwork from server cache

        Packet req = mConnection.CreatePacket(Connection.XVDR_ARTWORK_GET);
        req.putString(event.getTitle());
        req.putU32(event.getContentId());

        Packet resp = mConnection.transmitMessage(req);

        if(resp == null) {
            return null;
        }

        String posterUrl = resp.getString();
        String backgroundUrl = resp.getString();

        if(posterUrl.equals("x") || backgroundUrl.equals("x")) {
            return null;
        }

        ArtworkHolder artwork = new ArtworkHolder(posterUrl, backgroundUrl);

        if(artwork.hasBackground()) {
            Log.d(TAG, "found artwork for '" + event.getTitle() + "' (0x" + Integer.toHexString(event.getContentId()) + ") in cache");
        }

        return artwork;
    }
}
