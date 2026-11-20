package com.twa.flights.catalog.client;

import java.util.Set;

import com.google.common.collect.Sets;

/**
 * The paths of the catalog (local, remote or replica) that answered the calls of the current thread, so the metrics of
 * the consumers (search.catalog.outcome, provider.itineraries.*) can be compared by path (M2 C5). The flag decides the
 * path of each call, so one search can use more than one.
 *
 * The consumer calls {@link #begin()} before its calls to the catalog and {@link #end()} after them. Each call that
 * reaches a path is registered by {@link MeteredCatalogApi}; the hits of the cache do not reach any path.
 */
public final class CatalogLookupPath {

    public static final String CACHE = "cache";
    public static final String MIXED = "mixed";

    private static final ThreadLocal<Set<String>> PATHS = ThreadLocal.withInitial(Sets::newLinkedHashSet);

    private CatalogLookupPath() {
        // just to avoid create instances
    }

    public static void begin() {
        PATHS.get().clear();
    }

    static void register(String path) {
        PATHS.get().add(path);
    }

    /**
     * @return the path of the calls since {@link #begin()}: cache if none reached a path, mixed if more than one
     */
    public static String end() {
        Set<String> paths = PATHS.get();
        String path = paths.isEmpty() ? CACHE : paths.size() > 1 ? MIXED : paths.iterator().next();
        paths.clear();
        return path;
    }
}
