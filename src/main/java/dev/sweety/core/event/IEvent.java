package dev.sweety.core.event;

public interface IEvent {

    default void cancel() {
        setCancelled(true);
    }

    <T extends Event> T post();

    boolean isPost();

    boolean isCancelled();

    boolean isChanged();

    boolean isPre();

    void setCancelled(boolean cancelled);
}
