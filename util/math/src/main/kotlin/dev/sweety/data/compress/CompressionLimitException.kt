package dev.sweety.data.compress

/** Thrown by [CompressUtils]'s bounded inflate/deflate when a wall-clock or output-size guard trips. */
class CompressionLimitException(message: String) : RuntimeException(message)
