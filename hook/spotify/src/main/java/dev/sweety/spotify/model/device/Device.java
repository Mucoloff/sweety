package dev.sweety.spotify.model.device;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class Device {

    @SerializedName("id")
    private String id;

    @SerializedName("is_active")
    private boolean active;

    @SerializedName("is_private_session")
    private boolean privateSession;

    @SerializedName("is_restricted")
    private boolean restricted;

    @SerializedName("name")
    private String name;

    @SerializedName("type")
    private String type;

    @SerializedName("volume_percent")
    private Integer volume;

    @SerializedName("supports_volume")
    private boolean supportsVolume;

}