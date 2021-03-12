package com.xiejj.terminal.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import org.apache.commons.io.IOUtils;

import java.io.Closeable;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * @author xiejiajun
 */
public class ClientCache <KEY, CLIENT extends Closeable>{

    private final Cache<KEY, Optional<CLIENT>> CLIENT_CACHE;

    public ClientCache(int maxSize) {
        this.CLIENT_CACHE = CacheBuilder.newBuilder()
                .maximumSize(maxSize)
                .build(new CacheLoader<KEY, Optional<CLIENT>>() {
                    @Override
                    public Optional<CLIENT> load(KEY key) throws Exception {
                        return Optional.empty();
                    }
                });
    }

    public void put(KEY key, CLIENT client) {
        if (Objects.isNull(client)) {
            return;
        }
        this.CLIENT_CACHE.put(key, Optional.of(client));
    }

    public void remove(KEY key) {
        this.CLIENT_CACHE.invalidate(key);
    }

    public CLIENT get(KEY key) {
       return this.get(key, Optional::empty);
    }

    public CLIENT get(KEY key, Callable<? extends Optional<CLIENT>> cacheLoader) {
        try {
            Optional<CLIENT> opt = CLIENT_CACHE.get(key, cacheLoader);
            return opt.orElse(null);
        } catch (ExecutionException e) {
            return null;
        }
    }

    public void destroy() {
        this.CLIENT_CACHE.asMap().forEach((key, value) -> value.ifPresent(IOUtils::closeQuietly));
        this.CLIENT_CACHE.invalidateAll();
//        this.CLIENT_CACHE.cleanUp();
    }

}
