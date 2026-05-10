package dev.sweety.event.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IEvent {

    default void cancel() {
        setCancelled(true);
    }

    @NotNull
    <T extends IEvent> T post();

    boolean isPost();

    boolean isPre();

    boolean isCancelled();

    void setCancelled(boolean cancelled);

    boolean isChanged();
}
