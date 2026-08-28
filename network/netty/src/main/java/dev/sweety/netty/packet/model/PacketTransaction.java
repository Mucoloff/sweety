package dev.sweety.netty.packet.model;

import dev.sweety.data.Container;
import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.data.ChecksumUtils;
import dev.sweety.math.RandomUtils;
import dev.sweety.netty.packet.buffer.io.Codec;

import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.zip.CRC32C;

public abstract class PacketTransaction<R extends PacketTransaction.Transaction, S extends PacketTransaction.Transaction> extends Packet {

    private long requestId;

    // Container<T> = mutable Optional — lets request/response be reassigned in place (toResponse,
    // read()) without losing the null-safety Optional gives every other accessor here.
    private final Container<R> reqContainer = Container.empty();
    private final Container<S> respContainer = Container.empty();

    protected PacketTransaction() {
    }

    public PacketTransaction(final R request) {
        this(generateId(), request, true);
    }

    public PacketTransaction(final long id, final S response) {
        this(id, response, false);
    }

    protected <T extends Transaction> PacketTransaction(final long id, final T request, final boolean isRequest) {
        this.requestId = id;
        if (isRequest) {
            //noinspection unchecked
            this.reqContainer.set((R) request);
            this.respContainer.clear();
            return;
        }
        this.reqContainer.clear();
        //noinspection unchecked
        this.respContainer.set((S) request);
    }

    @Override
    public void write(final BufferWriter buffer) {
        buffer.writeVarLong(this.requestId);
        buffer.writeObject(this.reqContainer.getUnsafe());
        buffer.writeObject(this.respContainer.getUnsafe());
    }

    @Override
    public void read(final BufferReader buffer) {
        this.requestId = buffer.readVarLong();
        this.reqContainer.setNullable(buffer.readObject(this::request));
        this.respContainer.setNullable(buffer.readObject(this::response));
    }

    private static long generateId() {
        CRC32C crc = ChecksumUtils.crc32(true);
        byte[] randomBytes = new byte[RandomUtils.range(8, 32)];
        RandomUtils.RANDOM.nextBytes(randomBytes);
        crc.update(randomBytes);
        return crc.getValue();
    }

    public String requestCode() {
        return "#" + Long.toHexString(requestId);
    }

    public boolean hasRequest() {
        return this.reqContainer.isPresent();
    }

    public boolean hasResponse() {
        return this.respContainer.isPresent();
    }

    public Optional<R> requestOptional() {
        return this.reqContainer.optional();
    }

    public Optional<S> responseOptional() {
        return this.respContainer.optional();
    }

    public void toResponse(S response) {
        this.reqContainer.clear();
        this.respContainer.set(response);
    }

    /**
     * Runs {@code action} only if a request is present — skips the {@code hasRequest()} + null-check dance.
     */
    public void ifRequestPresent(Consumer<? super R> action) {
        this.reqContainer.ifPresent(action);
    }

    /**
     * Runs {@code action} only if a response is present — skips the {@code hasResponse()} + null-check dance.
     */
    public void ifResponsePresent(Consumer<? super S> action) {
        this.respContainer.ifPresent(action);
    }

    /**
     * Request, asserted present — for handler code already behind a {@code hasRequest()} gate that wants
     * a non-null value without re-deriving it from the nullable {@link #getRequest()}.
     */
    public R requireRequest() {
        return this.reqContainer.orElseThrow(() -> new NoSuchElementException(
                "No request present on " + getClass().getSimpleName()));
    }

    /**
     * Response, asserted present — see {@link #requireRequest()}.
     */
    public S requireResponse() {
        return this.respContainer.orElseThrow(() -> new NoSuchElementException(
                "No response present on " + getClass().getSimpleName()));
    }

    // Per-concrete-class no-arg constructor, resolved once from R/S's reified type argument (works as
    // long as the subclass directly extends PacketTransaction<ConcreteR, ConcreteS> — true for every
    // transaction today) then cached, so request()/response() no longer need a manual "return new
    // Request()" override in every subclass. A subclass that needs different construction (e.g.
    // HeartbeatTransaction pulling from an object pool instead of `new`) still overrides either method.
    private static final ConcurrentMap<Class<?>, Constructor<?>> REQUEST_CTORS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Constructor<?>> RESPONSE_CTORS = new ConcurrentHashMap<>();

    protected R request() {
        return newInstance(REQUEST_CTORS, 0);
    }

    protected S response() {
        return newInstance(RESPONSE_CTORS, 1);
    }

    @SuppressWarnings("unchecked")
    private <T> T newInstance(ConcurrentMap<Class<?>, Constructor<?>> cache, int typeArgIndex) {
        Constructor<?> ctor = cache.computeIfAbsent(getClass(), cls -> resolveNoArgCtor(cls, typeArgIndex));
        try {
            return (T) ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to instantiate " + ctor.getDeclaringClass()
                    + " for " + getClass(), e);
        }
    }

    private static Constructor<?> resolveNoArgCtor(Class<?> cls, int typeArgIndex) {
        Type generic = cls.getGenericSuperclass();
        if (!(generic instanceof ParameterizedType pt) || pt.getRawType() != PacketTransaction.class) {
            throw new IllegalStateException(cls.getName() + " must directly extend PacketTransaction<R, S> "
                    + "with concrete type arguments, or override request()/response() itself.");
        }
        Type arg = pt.getActualTypeArguments()[typeArgIndex];
        if (!(arg instanceof Class<?> argClass)) {
            throw new IllegalStateException(cls.getName() + " type argument " + arg
                    + " is not a concrete class — override request()/response() itself.");
        }
        try {
            Constructor<?> ctor = argClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(argClass.getName() + " needs a no-arg constructor, "
                    + "or override request()/response() in " + cls.getName(), e);
        }
    }


    public abstract static class Transaction implements Codec {

    }

    /**
     * Zero-content response body — the RPC still round-trips (caller still gets/awaits a response), there's
     * just nothing worth encoding in it (e.g. {@code PresenceUpdateTransaction}'s ack). Not the same thing
     * as a fire-and-forget send ({@code ServiceMessage.isFireAndForget()}/{@code ServiceClient.sendFireAndForget}),
     * which skips the round-trip entirely — no response object is ever built or sent back.
     */
    public static final class Ack extends Transaction {
        @Override
        public void write(final BufferWriter buffer) {

        }

        @Override
        public void read(final BufferReader buffer) {

        }
    }

    public long getRequestId() {
        return requestId;
    }

    public R getRequest() {
        return reqContainer.getUnsafe();
    }

    public S getResponse() {
        return respContainer.getUnsafe();
    }
}