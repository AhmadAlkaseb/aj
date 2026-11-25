package com.aj.productview.persistence;

import com.aj.common.Json;
import com.mongodb.client.*;
import com.mongodb.client.result.UpdateResult;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MongoStoreCacheTest {
    private MongoCollection<Document> products;
    private FindIterable<Document> found;
    private MongoStore store;
    private final AtomicLong clock=new AtomicLong();
    private final String guid=UUID.randomUUID().toString();

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        MongoClient client=mock(MongoClient.class);MongoDatabase db=mock(MongoDatabase.class);
        products=mock(MongoCollection.class);found=mock(FindIterable.class);
        when(client.getDatabase(anyString())).thenReturn(db);when(db.getCollection("products")).thenReturn(products);
        when(products.find(any(Bson.class))).thenReturn(found);when(found.sort(any(Bson.class))).thenReturn(found);
        when(found.skip(anyInt())).thenReturn(found);when(found.limit(anyInt())).thenReturn(found);
        when(products.countDocuments(any(Bson.class))).thenReturn(1L);
        when(found.iterator()).thenAnswer(call -> {
            MongoCursor<Document> cursor=mock(MongoCursor.class);
            when(cursor.hasNext()).thenReturn(true,false);when(cursor.next()).thenReturn(new Document("guid",guid).append("brand","Volvo"));return cursor;
        });
        store=new MongoStore(client,new CatalogueCache(256,256*1024,Duration.ofSeconds(20),clock::get));
    }

    @Test void equivalentFiltersHitCacheButPagesAndFiltersRemainSeparate() {
        // Given
        Map<String,Object> first=new LinkedHashMap<>();first.put("brand","Volvo");first.put("status","AVAILABLE");
        Map<String,Object> reordered=new LinkedHashMap<>();reordered.put("status","AVAILABLE");reordered.put("brand","Volvo");

        // When
        var cachedResult = store.search(first,null,null);
        var equivalentResult = store.search(reordered,0,24);

        // Then
        assertEquals(cachedResult,equivalentResult);
        verify(products,times(1)).countDocuments(any(Bson.class));verify(found,times(1)).iterator();

        // When
        store.search(first,24,24);store.search(Map.of("brand","BMW"),0,24);

        // Then
        verify(products,times(3)).countDocuments(any(Bson.class));

        // When / Then
        assertThrows(io.javalin.http.BadRequestResponse.class,() -> store.search(Map.of(),-1,24));
        verify(products,times(3)).countDocuments(any(Bson.class));
    }

    @Test void expiryReloadsBothCountAndItems() {
        // Given
        store.search(null,null,null);store.search(Map.of(),0,24);

        // When
        clock.set(Duration.ofSeconds(20).toNanos());store.search(null,null,null);

        // Then
        verify(products,times(2)).countDocuments(any(Bson.class));verify(found,times(2)).iterator();
    }

    @Test void everySuccessfulProjectionInvalidatesCachedSearchesIncludingTombstones() {
        // Given
        when(found.first()).thenReturn(new Document("guid",guid).append("version",0L));
        when(products.replaceOne(any(Bson.class),any(Document.class))).thenReturn(UpdateResult.acknowledged(1,1L,null));

        // When
        for(String topic:List.of("product.created","product.updated","product.reserved","product.removed")) {
            store.search(null,0,24);
            store.project(UUID.randomUUID().toString(),topic,Json.read("{\"guid\":\""+guid+"\",\"version\":1,\"price\":100}"));
            store.search(null,0,24);
        }

        // Then
        verify(products,times(5)).countDocuments(any(Bson.class));
    }

    @Test void insertInvalidatesButDuplicateOldEventsAndFailedWritesDoNot() {
        // Given
        store.search(null,0,24);when(found.first()).thenReturn(null);
        var event=Json.read("{\"guid\":\""+guid+"\",\"version\":1,\"price\":100}");

        // When
        store.project("event","product.created",event);store.search(null,0,24);
        when(found.first()).thenReturn(new Document("guid",guid).append("version",1L));
        store.project("event","product.created",event);store.search(null,0,24);
        when(found.first()).thenReturn(null);doThrow(new IllegalStateException("offline")).when(products).insertOne(any(Document.class));

        // Then
        assertThrows(IllegalStateException.class,() -> store.project("failed","product.created",event));
        store.search(null,0,24);verify(products,times(2)).countDocuments(any(Bson.class));
    }
}
