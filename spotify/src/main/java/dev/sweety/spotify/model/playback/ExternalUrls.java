package dev.sweety.spotify.model.playback;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class ExternalUrls {

    @SerializedName("spotify")
    private String spotify;

}
