package dev.sweety.microsoft.auth

enum class AccountType(private val display: String) {
    MICROSOFT("Microsoft"),
    CRACKED("Cracked");

    fun display() = display
}