package space.webgui.wmc.api;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What a page is allowed to do on a channel.
 *
 * <p>Three separate limits, because they fail differently. An origin list decides who may
 * speak at all; a payload cap stops one message from being a memory problem; a rate cap
 * stops a loop in someone JavaScript from becoming a server problem. Leaving any of them to
 * each consuming mod means most will forget at least one.
 */
public final class ChannelPolicy {

    private final Set<String> origins;
    private final int maxPayloadBytes;
    private final int messagesPerSecond;
    private final Duration requestTimeout;

    private ChannelPolicy(Builder builder) {
        this.origins = Set.copyOf(builder.origins);
        this.maxPayloadBytes = builder.maxPayloadBytes;
        this.messagesPerSecond = builder.messagesPerSecond;
        this.requestTimeout = builder.requestTimeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Origins allowed to use the channel, as {@code scheme://host[:port]}.
     *
     * <p>Empty means none: a policy that forgot to say who may speak lets nobody speak,
     * rather than everybody.
     */
    public Set<String> origins() {
        return origins;
    }

    public int maxPayloadBytes() {
        return maxPayloadBytes;
    }

    public int messagesPerSecond() {
        return messagesPerSecond;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    /** Whether a page served from {@code origin} may use the channel. */
    public boolean allows(String origin) {
        return origin != null && origins.contains(origin);
    }

    public static final class Builder {

        private final Set<String> origins = new LinkedHashSet<>();
        private int maxPayloadBytes = 64 * 1024;
        private int messagesPerSecond = 20;
        private Duration requestTimeout = Duration.ofSeconds(10);

        private Builder() {
        }

        /** Adds an origin, such as {@code app://shop} or {@code https://example.com}. */
        public Builder allowOrigin(String origin) {
            origins.add(origin);
            return this;
        }

        public Builder maxPayload(int bytes) {
            if (bytes <= 0) {
                throw new IllegalArgumentException("payload cap must be positive, got " + bytes);
            }
            this.maxPayloadBytes = bytes;
            return this;
        }

        /** Messages per second from the page; further ones are dropped and reported to it. */
        public Builder perSecond(int messages) {
            if (messages <= 0) {
                throw new IllegalArgumentException("rate must be positive, got " + messages);
            }
            this.messagesPerSecond = messages;
            return this;
        }

        public Builder requestTimeout(Duration timeout) {
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("timeout must be positive, got " + timeout);
            }
            this.requestTimeout = timeout;
            return this;
        }

        public ChannelPolicy build() {
            return new ChannelPolicy(this);
        }
    }
}
