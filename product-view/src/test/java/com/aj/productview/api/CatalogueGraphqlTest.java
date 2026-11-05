package com.aj.productview.api;

import com.aj.productview.persistence.MongoStore;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogueGraphqlTest {
    @Test void resolvesTypedFieldsAndForwardsPaginationToOwner() {
        MongoStore store=mock(MongoStore.class);
        when(store.search(any(),eq(0),eq(2))).thenReturn(Map.of("items",List.of(Map.of("guid",UUID.randomUUID().toString(),"brand","Volvo","price",100)),"total",1,"hasMore",false));
        var result=CatalogueGraphql.engine(store).execute("{products(limit:2){total items{guid brand price}}}");
        assertTrue(result.getErrors().isEmpty());verify(store).search(null,0,2);
    }
    @Test void rejectsUnknownSchemaFieldsWithoutQueryingStorage() {
        MongoStore store=mock(MongoStore.class);
        assertFalse(CatalogueGraphql.engine(store).execute("{products{unknownField}}").getErrors().isEmpty());
        verifyNoInteractions(store);
    }
    @Test void rejectsExcessiveAliasComplexityBeforeStorage() {
        MongoStore store=mock(MongoStore.class);StringBuilder query=new StringBuilder("{");
        for(int i=0;i<220;i++)query.append("p").append(i).append(":products{total} ");query.append("}");
        assertFalse(CatalogueGraphql.engine(store).execute(query.toString()).getErrors().isEmpty());verifyNoInteractions(store);
    }
}
