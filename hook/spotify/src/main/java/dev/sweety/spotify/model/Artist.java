package dev.sweety.spotify.model;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

@Getter
public class Artist {

    @SerializedName("name")
    private String name;

    @Override
    public String toString() {
        return name;
    }
}