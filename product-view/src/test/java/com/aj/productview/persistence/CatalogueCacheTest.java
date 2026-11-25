package com.aj.productview.persistence;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CatalogueCacheTest {
    private final AtomicLong clock=new AtomicLong();
    private CatalogueCache cache(int capacity,int bytes) {
        return new CatalogueCache(capacity,bytes,Duration.ofSeconds(20),clock::get);
    }

    @Test void hitsExpireTwentySecondsAfterLoadingWithoutSlidingExpiry() {
        // Given
        CatalogueCache cache=cache(2,4096);AtomicInteger reads=new AtomicInteger();
        Supplier<Map<String,Object>> loader=() -> Map.of("total",reads.incrementAndGet());

        // When
        var firstRead = cache.get("a",loader);
        clock.set(Duration.ofSeconds(19).toNanos());
        var cachedRead = cache.get("a",loader);
        clock.set(Duration.ofSeconds(20).toNanos());
        var expiredRead = cache.get("a",loader);

        // Then
        assertEquals(1,firstRead.get("total"));
        assertEquals(1,cachedRead.get("total"));
        assertEquals(2,expiredRead.get("total"));
    }

    @Test void evictsLeastRecentlyUsedResultWhenCapacityIsReached() {
        // Given
        CatalogueCache cache=cache(2,4096);AtomicInteger reads=new AtomicInteger();
        Supplier<Map<String,Object>> loader=() -> Map.of("total",reads.incrementAndGet());

        // When
        cache.get("a",loader);cache.get("b",loader);cache.get("a",loader);cache.get("c",loader);
        var mostRecentlyUsed = cache.get("a",loader);
        var evictedEntry = cache.get("b",loader);

        // Then
        assertEquals(1,mostRecentlyUsed.get("total"));
        assertEquals(4,evictedEntry.get("total"));
    }

    @Test void oversizedResultsAndFailuresAreNotCached() {
        // Given
        CatalogueCache cache=cache(2,30);AtomicInteger reads=new AtomicInteger();
        Supplier<Map<String,Object>> loader=() -> { reads.incrementAndGet();return Map.of("description","x".repeat(50)); };

        // When
        cache.get("large",loader);cache.get("large",loader);

        // Then
        assertEquals(2,reads.get());

        // When / Then
        assertThrows(IllegalStateException.class,() -> cache.get("failed",() -> { throw new IllegalStateException("offline"); }));
        assertEquals(Map.of("ok",true),cache.get("failed",() -> Map.of("ok",true)));
    }

    @Test void invalidationDiscardsEverySearchPage() {
        // Given
        CatalogueCache cache=cache(3,4096);AtomicInteger reads=new AtomicInteger();
        Supplier<Map<String,Object>> loader=() -> Map.of("total",reads.incrementAndGet());

        // When
        cache.get("a:0",loader);cache.get("a:24",loader);
        cache.invalidate();
        var firstPage = cache.get("a:0",loader);
        var secondPage = cache.get("a:24",loader);

        // Then
        assertEquals(3,firstPage.get("total"));
        assertEquals(4,secondPage.get("total"));
    }

    @Test void invalidatedInflightReadCannotRepopulateTheCache() throws Exception {
        // Given
        CatalogueCache cache=cache(2,4096);CountDownLatch started=new CountDownLatch(1),finish=new CountDownLatch(1);
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try {
            Future<Map<String,Object>> old=pool.submit(() -> cache.get("a",() -> {
                started.countDown();
                try { if(!finish.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Timeout"); }
                catch(InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException(e); }
                return Map.of("version",1);
            }));

            // When
            assertTrue(started.await(5,TimeUnit.SECONDS));
            cache.invalidate();
            var freshRead = cache.get("a",() -> Map.of("version",2));
            finish.countDown();
            var oldRead = old.get(5,TimeUnit.SECONDS);
            var finalRead = cache.get("a",() -> { fail("Old read evicted the fresh result");return Map.of(); });

            // Then
            assertEquals(2,freshRead.get("version"));
            assertEquals(1,oldRead.get("version"));
            assertEquals(2,finalRead.get("version"));
        } finally { finish.countDown();pool.shutdownNow(); }
    }

    @Test void cachedSnapshotsCannotBeMutatedAndKeepDecimalPrecision() {
        // Given
        CatalogueCache cache=cache(2,4096);
        List<String> equipment=new ArrayList<>(List.of("ABS"));
        Map<String,Object> car=new HashMap<>();car.put("equipment",equipment);car.put("price",new BigDecimal("123.45"));car.put("optional",null);

        // When
        Map<String,Object> result=cache.get("a",() -> Map.of("items",List.of(car)));
        equipment.add("changed");car.put("price",0);
        Map<?,?> saved=(Map<?,?>)((List<?>)result.get("items")).get(0);

        // Then
        assertEquals(List.of("ABS"),saved.get("equipment"));assertEquals(new BigDecimal("123.45"),saved.get("price"));
        assertThrows(UnsupportedOperationException.class,() -> result.put("items",List.of()));
        assertThrows(UnsupportedOperationException.class,() -> saved.clear());
        assertThrows(UnsupportedOperationException.class,() -> ((List<?>)saved.get("equipment")).clear());
    }
}
