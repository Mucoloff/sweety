package dev.sweety.spotify.model.device;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class Devices {
    @SerializedName("devices")
    private Device[] devices;

}
