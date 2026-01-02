package dev.sweety.spotify.model.playback;

import com.google.gson.annotations.SerializedName;
import dev.sweety.spotify.model.device.Device;
import dev.sweety.spotify.model.track.TrackItem;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class Playback {

    @SerializedName("device")
    private Device device;

    @SerializedName("repeat_state")
    private String repeat;

    @SerializedName("shuffle_state")
    private boolean shuffle;

    @SerializedName("context")
    private PlaybackContext context;

    @SerializedName("timestamp")
    private long timestamp;

    @SerializedName("progress_ms")
    private Integer progress;

    @SerializedName("is_playing")
    private boolean isPlaying;

    @SerializedName("item")
    private TrackItem item;

    @SerializedName("currently_playing_type")
    private String type;

    @SerializedName("smart_shuffle")
    private boolean smartShuffle;


}
