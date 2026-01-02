package dev.sweety.spotify.model.track;

import com.google.gson.annotations.SerializedName;
import dev.sweety.spotify.model.Album;
import dev.sweety.spotify.model.Artist;
import dev.sweety.spotify.model.Image;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class TrackItem {

    @SerializedName("name")
    private String trackName;

    @SerializedName("artists")
    private Artist[] artists;

    @SerializedName("album")
    private Album album;

    @SerializedName("duration_ms")
    private int durationMs;

    @SerializedName("uri")
    private String uri;


    public Image[] getImages() {
        if (album != null) {
            return album.getImages();
        }

        return null;
    }

}
