package com.aj.productview.persistence;

import com.aj.common.Json;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Per-process cache for public, bounded catalogue search results only. */
final class CatalogueCache {
    private record Entry(Map<String,Object> value,long storedAt) {}
    private final Map<String,Entry> entries=new LinkedHashMap<>(16,0.75f,true);
    private final int capacity;
    private final int maxEntryBytes;
    private final long ttlNanos;
    private final LongSupplier clock;
    private long generation;

    CatalogueCache() { this(256,256*1024,Duration.ofSeconds(20),System::nanoTime); }

    CatalogueCache(int capacity,int maxEntryBytes,Duration ttl,LongSupplier clock) {
        if(capacity<1 || maxEntryBytes<1 || ttl.isZero() || ttl.isNegative())
            throw new IllegalArgumentException("Positive cache limits required");
        this.capacity=capacity;this.maxEntryBytes=maxEntryBytes;
        this.ttlNanos=ttl.toNanos();this.clock=Objects.requireNonNull(clock);
    }

    Map<String,Object> get(String key,Supplier<Map<String,Object>> loader) {
        long observed;
        synchronized(this) {
            Entry cached=entries.get(key);
            if(cached!=null && clock.getAsLong()-cached.storedAt()<ttlNanos) return cached.value();
            entries.remove(key);observed=generation;
        }
        // Database work stays outside the lock. Exceptions never populate the cache.
        Map<String,Object> loaded=loader.get();
        if(Json.write(loaded).getBytes(StandardCharsets.UTF_8).length>maxEntryBytes) return loaded;
        Map<String,Object> snapshot=freezeMap(loaded);
        synchronized(this) {
            // A projection committed while this read was in flight: don't cache its old result.
            if(observed==generation) {
                long now=clock.getAsLong();
                entries.values().removeIf(entry -> now-entry.storedAt()>=ttlNanos);
                if(!entries.containsKey(key) && entries.size()>=capacity)
                    entries.remove(entries.keySet().iterator().next());
                entries.put(key,new Entry(snapshot,now));
            }
        }
        return snapshot;
    }

    synchronized void invalidate() { generation++;entries.clear(); }

    private static Map<String,Object> freezeMap(Map<?,?> input) {
        Map<String,Object> copy=new LinkedHashMap<>();
        input.forEach((key,value) -> copy.put((String)key,freeze(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object freeze(Object value) {
        if(value instanceof Map<?,?> map) return freezeMap(map);
        if(value instanceof List<?> list) {
            List<Object> copy=new ArrayList<>();
            list.forEach(item -> copy.add(freeze(item)));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
