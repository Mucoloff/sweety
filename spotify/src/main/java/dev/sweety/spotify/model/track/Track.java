package dev.sweety.spotify.model.track;

import com.google.gson.annotations.SerializedName;
import dev.sweety.spotify.model.device.Device;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class Track {

    @SerializedName("device")
    private Device device;

    @SerializedName("repeat_state")
    private String repeat;

    @SerializedName("shuffle_state")
    private boolean shuffle;

    @SerializedName("timestamp")
    private long timestamp;

    @SerializedName("progress_ms")
    private Integer progress;

    @SerializedName("is_playing")
    private boolean isPlaying;

    @SerializedName("item")
    private TrackItem item;


}
