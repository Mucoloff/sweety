package dev.sweety.spotify.model;

import com.google.gson.annotations.SerializedName;
import dev.sweety.spotify.model.track.Track;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
public class Queue {

    @SerializedName("queue")
    private Track[] queue;

}