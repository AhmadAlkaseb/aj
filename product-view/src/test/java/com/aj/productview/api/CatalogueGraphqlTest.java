package com.aj.productview.api;

import com.aj.productview.persistence.MongoStore;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogueGraphqlTest {
    @Test void resolvesTypedFieldsAndForwardsPaginationToOwner() {
        // Given
        MongoStore store=mock(MongoStore.class);
        when(store.search(any(),eq(0),eq(2))).thenReturn(Map.of("items",List.of(Map.of("guid",UUID.randomUUID().toString(),"brand","Volvo","price",100)),"total",1,"hasMore",false));

        // When
        var result=CatalogueGraphql.engine(store).execute("{products(limit:2){total items{guid brand price}}}");

        // Then
        assertTrue(result.getErrors().isEmpty());verify(store).search(null,0,2);
    }

    @Test void rejectsUnknownSchemaFieldsWithoutQueryingStorage() {
        // Given
        MongoStore store=mock(MongoStore.class);

        // When
        var result=CatalogueGraphql.engine(store).execute("{products{unknownField}}");

        // Then
        assertFalse(result.getErrors().isEmpty());
        verifyNoInteractions(store);
    }

    @Test void rejectsExcessiveAliasComplexityBeforeStorage() {
        // Given
        MongoStore store=mock(MongoStore.class);StringBuilder query=new StringBuilder("{");
        for(int i=0;i<220;i++)query.append("p").append(i).append(":products{total} ");query.append("}");

        // When
        var result=CatalogueGraphql.engine(store).execute(query.toString());

        // Then
        assertFalse(result.getErrors().isEmpty());verifyNoInteractions(store);
    }
}
