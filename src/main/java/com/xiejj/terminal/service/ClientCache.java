package com.xiejj.terminal.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.io.IOUtils;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * @author xiejiajun
 */
public class ClientCache {

    private final Cache<String, Optional<KubernetesClient>> CLIENT_CACHE;

    public ClientCache(int maxSize) {
        this.CLIENT_CACHE = CacheBuilder.newBuilder()
                .maximumSize(maxSize)
                .build(new CacheLoader<String, Optional<KubernetesClient>>() {
                    @Override
                    public Optional<KubernetesClient> load(String key) throws Exception {
                        return Optional.empty();
                    }
                });
    }

    public void put(String key, KubernetesClient client) {
        if (Objects.isNull(client)) {
            return;
        }
        this.CLIENT_CACHE.put(key, Optional.of(client));
    }

    public void remove(String key) {
        this.CLIENT_CACHE.invalidate(key);
    }

    public KubernetesClient get(String key) {
       return this.get(key, Optional::empty);
    }

    public KubernetesClient get(String key, Callable<? extends Optional<KubernetesClient>> cacheLoader) {
        try {
            Optional<KubernetesClient> opt = CLIENT_CACHE.get(key, cacheLoader);
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
