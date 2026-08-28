package dev.sweety.time

enum class TimeMode(private val clock: () -> Long) {
    MILLIS(System::currentTimeMillis),
    NANO(System::nanoTime),
    NONE({ 0L });

    fun now(): Long = clock()
}
