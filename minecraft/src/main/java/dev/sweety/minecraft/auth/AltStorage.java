package dev.sweety.minecraft.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class AltStorage {
    public String username;
    public UUID uuid;

    public String accessToken;
    public String refreshToken;
    public AccountType type;

    public boolean isCracked() {
        return type == AccountType.CRACKED;
    }

}