package dev.sweety.integration.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AccountType {
    //MOJANG("Mojang"),
    MICROSOFT("Microsoft"),
    CRACKED("Cracked");

    private final String name;

}