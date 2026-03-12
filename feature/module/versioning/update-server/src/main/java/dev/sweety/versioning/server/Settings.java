package dev.sweety.versioning.server;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Settings {

    public String rollbackToken = "token";
    public String webhookSecret = "secret";
    public String tokenGeneratorSalt = "very-secret-key";

}
