package dev.sweety.event.api

interface IEvent {

    fun cancel() {
        this.isCancelled = true
    }

    fun <T : IEvent?> post(): T

    fun isPost(): Boolean
    fun isPre(): Boolean

    var isCancelled: Boolean

    val isChanged: Boolean
}