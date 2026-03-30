package dev.sweeyu.microsoft.auth;

public enum AccountType {
    //MOJANG("Mojang"),
    MICROSOFT("Microsoft"),
    CRACKED("Cracked");

    private final String name;

    private AccountType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}