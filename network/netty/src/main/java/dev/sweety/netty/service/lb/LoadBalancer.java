package dev.sweety.netty.service.lb;

import io.netty.channel.Channel;

import java.util.List;

/**
 * Strategy interface for client-side load balancing across active service channels/nodes.
 */
public interface LoadBalancer {

    /**
     * Selects the best active channel from the candidate list.
     *
     * @param candidates list of active Netty channels
     * @return selected channel, or {@code null} if candidates is empty
     */
    Channel select(List<Channel> candidates);
}
