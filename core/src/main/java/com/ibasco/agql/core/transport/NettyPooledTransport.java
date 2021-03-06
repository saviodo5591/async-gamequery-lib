/*
 * MIT License
 *
 * Copyright (c) 2016 Asynchronous Game Query Library
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.ibasco.agql.core.transport;

import com.ibasco.agql.core.AbstractMessage;
import com.ibasco.agql.core.AbstractRequest;
import com.ibasco.agql.core.enums.ChannelType;
import com.ibasco.agql.core.exceptions.ConnectException;
import com.ibasco.agql.core.transport.pool.MessageChannelPoolMap;
import io.netty.channel.Channel;
import io.netty.channel.pool.AbstractChannelPoolHandler;
import io.netty.channel.pool.ChannelPool;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * <p>A transport that use a pool implementation to create or re-use a {@link Channel}</p>
 *
 * @param <M>
 *         A type of {@link AbstractRequest} that will be used as a lookup reference for our key
 * @param <K>
 *         A type of the value for the internal {@link io.netty.channel.pool.ChannelPoolMap} implementation
 */
abstract public class NettyPooledTransport<M extends AbstractRequest, K> extends NettyTransport<M> {
    private static final Logger log = LoggerFactory.getLogger(NettyPooledTransport.class);

    private MessageChannelPoolMap<M, K> poolMap;
    protected final ChannelPoolHandler channelPoolHandler = new AbstractChannelPoolHandler() {
        @Override
        public void channelCreated(Channel ch) throws Exception {
            onChannelCreate(ch);
        }

        @Override
        public void channelAcquired(Channel ch) throws Exception {
            log.debug("Channel Acquired: {}", ch);
            onChannelAcquire(ch);
        }

        @Override
        public void channelReleased(Channel ch) throws Exception {
            log.debug("Channel Released : {}", ch);
            onChannelRelease(ch);
        }
    };

    public NettyPooledTransport(ChannelType channelType) {
        super(channelType);
        //Initialize our pool map instance
        poolMap = new MessageChannelPoolMap<>(this::createKey, this::createChannelPool);
    }

    public NettyPooledTransport(ChannelType channelType, ExecutorService executor) {
        super(channelType, executor);
    }

    /**
     * <p>Acquires a {@link Channel} from the {@link ChannelPool}</p>
     *
     * @param message
     *         An {@link AbstractRequest} that will be used as the lookup reference for the {@link
     *         io.netty.channel.pool.ChannelPoolMap} key
     *
     * @return A {@link CompletableFuture} containing the acquired {@link Channel}
     */
    @Override
    public CompletableFuture<Channel> getChannel(M message) {
        final CompletableFuture<Channel> channelFuture = new CompletableFuture<>();
        //Retrieve our channel pool based on the message
        final ChannelPool pool = poolMap.get(message);

        log.debug("Acquiring channel from pool '{}' for message : {}", pool, message);

        //Acquire a channel from the pool and listen for completion
        pool.acquire().addListener((Future<Channel> future) -> {
            if (future.isSuccess()) {
                log.debug("Successfully acquired Channel from pool");
                Channel channel = future.get();
                channel.attr(ChannelAttributes.CHANNEL_POOL).set(pool);
                channelFuture.complete(channel);
            } else {
                log.debug("Failed to acquire Channel from Pool");
                channelFuture.completeExceptionally(new ConnectException(future.cause()));
            }
        });
        return channelFuture;
    }

    /**
     * <p>A method to perform cleanup operations on a {@link Channel}. This is called after every invocation of {@link
     * #send(AbstractRequest)}.</p>
     *
     * @param c
     *         The {@link Channel} that will need to be cleaned-up/released.
     */
    @Override
    public void cleanupChannel(Channel c) {
        //Release channel from the pool
        if (c.hasAttr(ChannelAttributes.CHANNEL_POOL))
            c.attr(ChannelAttributes.CHANNEL_POOL).get().release(c);
    }

    /**
     * <p>A callback method that gets invoked once a {@link Channel} has been acquired from the {@link ChannelPool}</p>
     *
     * @param ch
     *         The acquired {@link Channel}
     */
    protected void onChannelAcquire(Channel ch) {
        //no implementation
    }

    /**
     * <p>A callback method that gets invoked once a {@link Channel} has been released from the {@link ChannelPool}</p>
     *
     * @param ch
     *         The released {@link Channel}
     */
    protected void onChannelRelease(Channel ch) {
        //no implementation
    }

    /**
     * <p>A callback method that gets invoked once a {@link Channel} has been created using the {@link ChannelPool}</p>
     *
     * @param ch
     *         The newly created {@link Channel}
     */
    protected void onChannelCreate(Channel ch) {
        getChannelInitializer().initializeChannel(ch, this);
    }

    /**
     * @return The {@link ChannelPoolHandler}
     */
    public ChannelPoolHandler getChannelPoolHandler() {
        return channelPoolHandler;
    }

    /**
     * Creates a key from the {@link AbstractMessage} provided which will be used in the pool map
     *
     * @param message
     *         An instance of {@link AbstractRequest}
     *
     * @return The resolved key from the message
     */
    abstract public K createKey(M message);

    /**
     * A factory method that creates a {@link ChannelPool} based on the key provided.
     *
     * @param key
     *         The key to be used for {@link ChannelPool} creation
     *
     * @return A {@link ChannelPool} instance
     */
    abstract public ChannelPool createChannelPool(K key);
}
