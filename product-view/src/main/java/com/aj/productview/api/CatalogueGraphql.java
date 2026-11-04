package com.aj.productview.api;

import com.aj.common.*;
import com.aj.productview.persistence.MongoStore;
import graphql.GraphQL;
import graphql.ExecutionInput;
import graphql.analysis.*;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.schema.idl.*;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class CatalogueGraphql {
    private CatalogueGraphql() {}
    public static GraphQL engine(MongoStore store) {
        try(var resource=CatalogueGraphql.class.getResourceAsStream("/catalogue.graphqls")) {
            if(resource==null) throw new IllegalStateException("Missing schema");
            var registry=new SchemaParser().parse(new String(resource.readAllBytes(),StandardCharsets.UTF_8));
            var wiring=RuntimeWiring.newRuntimeWiring().type("Query", type -> type
                .dataFetcher("products",e -> store.search(e.getArgument("filter"),e.getArgument("offset"),e.getArgument("limit")))
                .dataFetcher("product",e -> store.get(Api.guid((String)e.getArgument("guid"))))).build();
            return GraphQL.newGraphQL(new SchemaGenerator().makeExecutableSchema(registry,wiring))
                .instrumentation(new ChainedInstrumentation(List.of(new MaxQueryDepthInstrumentation(6),new MaxQueryComplexityInstrumentation(200)))).build();
        } catch(java.io.IOException ex) { throw new IllegalStateException(ex); }
    }
    @SuppressWarnings("unchecked")
    public static void register(Javalin app,MongoStore store) {
        GraphQL engine=engine(store);
        app.post("/api/v1/graphql",ctx -> {
            var n=Json.read(ctx.body()); String query=Api.required(n,"query");
            if(query.length()>10000 || n.hasNonNull("variables") && !n.get("variables").isObject()) throw new BadRequestResponse("Ugyldig GraphQL-forespørgsel");
            Map<String,Object> variables=n.hasNonNull("variables") ? Json.MAPPER.convertValue(n.get("variables"),Map.class) : Map.of();
            var input=ExecutionInput.newExecutionInput().query(query).variables(variables);
            if(n.hasNonNull("operationName")) input.operationName(Api.required(n,"operationName"));
            ctx.json(engine.execute(input.build()).toSpecification());
        });
    }
}
