package dev.sweety.netty.service;

/** Identify-handshake phase between a {@link ServiceClient} and the {@link HubServer}. */
public enum ServiceState {
    /** Client → hub: "register me as service id N". */
    REQUEST,
    /** Hub → client: identify accepted, the client is now routable. */
    ACCEPT,
    /** Hub → client: identify rejected (bad id / secret) — the client should close. */
    REJECT
}
