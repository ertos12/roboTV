package org.xvdr.robotv.artwork.provider;

import org.xvdr.robotv.artwork.ArtworkHolder;
import org.xvdr.robotv.artwork.Event;

import java.io.IOException;

public abstract class ArtworkProvider {

    protected abstract ArtworkHolder searchMovie(Event event) throws IOException;

    protected abstract ArtworkHolder searchTv(Event event) throws IOException;

    public ArtworkHolder search(Event event) throws IOException {
        // search tv series
        if(event.getContentId() == 0x15 || event.getGenre() == 0x50 || event.getContentId() == 0x23) {
            return searchTv(event);
        }
        // search movies
        else if(event.getGenre() == 0x10 || event.getGenre() == 0x70) {
            return searchMovie(event);
        }

        return null;
    }

}
