package dev.sweety.spotify.model.playback;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class PlaybackContext {

    @SerializedName("type")
    private String type;

    @SerializedName("href")
    private String href;

    @SerializedName("external_urls")
    private ExternalUrls externalUrls;

    @SerializedName("uri")
    private String uri;
}
