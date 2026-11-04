package com.example.libraryapi.exception;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GraphQLExceptionResolver extends DataFetcherExceptionResolverAdapter {
    @Override
    protected GraphQLError resolveToSingleError(Throwable exception, DataFetchingEnvironment environment) {
        if (exception instanceof ResourceNotFoundException) {
            return error(exception, environment, ErrorType.NOT_FOUND, "NOT_FOUND");
        }
        if (exception instanceof ValidationException) {
            return error(exception, environment, ErrorType.BAD_REQUEST, "BAD_REQUEST");
        }
        if (exception instanceof ConflictException) {
            return error(exception, environment, ErrorType.BAD_REQUEST, "CONFLICT");
        }
        // Unhandled exceptions retain Spring GraphQL's generic INTERNAL_ERROR response.
        return null;
    }

    private GraphQLError error(Throwable exception, DataFetchingEnvironment environment,
                               ErrorType type, String code) {
        return GraphqlErrorBuilder.newError(environment)
                .errorType(type)
                .message(exception.getMessage())
                .extensions(Map.of("code", code))
                .build();
    }
}
