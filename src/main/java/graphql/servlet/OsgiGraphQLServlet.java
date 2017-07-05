/**
 * Copyright 2016 Yurii Rashkovskii
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 */
package graphql.servlet;

import graphql.annotations.GraphQLAnnotations;
import graphql.annotations.GraphQLAnnotationsProcessor;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

import static graphql.schema.GraphQLSchema.newSchema;

@Component(
        service={javax.servlet.http.HttpServlet.class,javax.servlet.Servlet.class},
        property = {"alias=/graphql", "jmx.objectname=graphql.servlet:type=graphql"}
)
public class OsgiGraphQLServlet extends GraphQLServlet {

    private final List<GraphQLQueryProvider> queryProviders = new ArrayList<>();
    private final List<GraphQLMutationProvider> mutationProviders = new ArrayList<>();
    private final List<GraphQLTypesProvider> typesProviders = new ArrayList<>();
    private final List<GraphQLAnnotatedClassProvider> extensionsProviders = new ArrayList<>();

    private GraphQLContextBuilder contextBuilder = new DefaultGraphQLContextBuilder();
    private ExecutionStrategyProvider executionStrategyProvider = new EnhancedExecutionStrategyProvider();
    private InstrumentationProvider instrumentationProvider = new NoOpInstrumentationProvider();

    private GraphQLAnnotationsProcessor graphQLAnnotationsProcessor = GraphQLAnnotations.getInstance();

    private GraphQLSchemaProvider schemaProvider;

    protected void updateSchema() {
        graphQLAnnotationsProcessor.removeAllTypes();

        for (GraphQLAnnotatedClassProvider extensionsProvider : extensionsProviders) {
            for (Class<?> aClass : extensionsProvider.getExtensions()) {
                graphQLAnnotationsProcessor.registerTypeExtension(aClass);
            }
        }

        final Set<GraphQLType> types = new HashSet<>();
        for (GraphQLTypesProvider typesProvider : typesProviders) {
            types.addAll(typesProvider.getTypes());
        }

        final GraphQLObjectType.Builder queryTypeBuilder = graphQLAnnotationsProcessor.getObjectBuilder(GraphQLQuery.class);

        for (GraphQLQueryProvider provider : queryProviders) {
            if (provider.getQueries() != null && !provider.getQueries().isEmpty()) {
                provider.getQueries().forEach(queryTypeBuilder::field);
            }
        }

        GraphQLObjectType mutationType = null;

        if (!mutationProviders.isEmpty()) {
            final GraphQLObjectType.Builder mutationTypeBuilder = graphQLAnnotationsProcessor.getObjectBuilder(GraphQLMutation.class);

            for (GraphQLMutationProvider provider : mutationProviders) {
                provider.getMutations().forEach(mutationTypeBuilder::field);
            }

            if (!mutationTypeBuilder.build().getFieldDefinitions().isEmpty()) {
                mutationType = mutationTypeBuilder.build();
            }
        }

        this.schemaProvider = new DefaultGraphQLSchemaProvider(newSchema().query(queryTypeBuilder.build()).mutation(mutationType).build(types));
    }

    public OsgiGraphQLServlet() {
        updateSchema();
    }

//    @Reference(cardinality = ReferenceCardinality.MANDATORY, policyOption = ReferencePolicyOption.GREEDY)
//    public void setGraphQLAnnotationsProcessor(GraphQLAnnotationsProcessor graphQLAnnotationsProcessor) {
//        this.graphQLAnnotationsProcessor = graphQLAnnotationsProcessor;
//    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policyOption = ReferencePolicyOption.GREEDY)
    public void bindProvider(GraphQLProvider provider) {
        if (provider instanceof GraphQLQueryProvider) {
            queryProviders.add((GraphQLQueryProvider) provider);
        }
        if (provider instanceof GraphQLMutationProvider) {
            mutationProviders.add((GraphQLMutationProvider) provider);
        }
        if (provider instanceof GraphQLTypesProvider) {
            typesProviders.add((GraphQLTypesProvider) provider);
        }
        if (provider instanceof GraphQLAnnotatedClassProvider) {
            extensionsProviders.add((GraphQLAnnotatedClassProvider) provider);
        }
        updateSchema();
    }
    public void unbindProvider(GraphQLProvider provider) {
        if (provider instanceof GraphQLQueryProvider) {
            queryProviders.remove((GraphQLQueryProvider) provider);
        }
        if (provider instanceof GraphQLMutationProvider) {
            mutationProviders.remove((GraphQLMutationProvider) provider);
        }
        if (provider instanceof GraphQLTypesProvider) {
            typesProviders.remove((GraphQLTypesProvider) provider);
        }
        if (provider instanceof GraphQLAnnotatedClassProvider) {
            extensionsProviders.remove((GraphQLAnnotatedClassProvider) provider);
        }
        updateSchema();
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policyOption = ReferencePolicyOption.GREEDY)
    public void bindQueryProvider(GraphQLQueryProvider queryProvider) {
        queryProviders.add(queryProvider);
        updateSchema();
    }
    public void unbindQueryProvider(GraphQLQueryProvider queryProvider) {
        queryProviders.remove(queryProvider);
        updateSchema();
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policyOption = ReferencePolicyOption.GREEDY)
    public void bindMutationProvider(GraphQLMutationProvider mutationProvider) {
        mutationProviders.add(mutationProvider);
        updateSchema();
    }
    public void unbindMutationProvider(GraphQLMutationProvider mutationProvider) {
        mutationProviders.remove(mutationProvider);
        updateSchema();
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policyOption = ReferencePolicyOption.GREEDY)
    public void typesProviders(GraphQLTypesProvider typesProvider) {
        typesProviders.add(typesProvider);
        updateSchema();
    }
    public void unbindTypesProvider(GraphQLTypesProvider typesProvider) {
        typesProviders.remove(typesProvider);
        updateSchema();
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policyOption = ReferencePolicyOption.GREEDY)
    public void bindExtensionsProvider(GraphQLAnnotatedClassProvider extensionProvider) {
        extensionsProviders.add(extensionProvider);
        updateSchema();
    }
    public void unbindExtensionsProvider(GraphQLAnnotatedClassProvider extensionProvider) {
        extensionsProviders.remove(extensionProvider);
        updateSchema();
    }


    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
    public void setContextProvider(GraphQLContextBuilder contextBuilder) {
        this.contextBuilder = contextBuilder;
    }
    public void unsetContextProvider(GraphQLContextBuilder contextBuilder) {
        this.contextBuilder = new DefaultGraphQLContextBuilder();
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL)
    public void setExecutionStrategyProvider(ExecutionStrategyProvider provider) {
        executionStrategyProvider = provider;
    }
    public void unsetExecutionStrategyProvider(ExecutionStrategyProvider provider) {
        executionStrategyProvider = new EnhancedExecutionStrategyProvider();
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL)
    public void setInstrumentationProvider(InstrumentationProvider provider) {
        instrumentationProvider = provider;
    }
    public void unsetInstrumentationProvider(InstrumentationProvider provider) {
        instrumentationProvider = new NoOpInstrumentationProvider();
    }

    @Override
    protected GraphQLSchemaProvider getSchemaProvider() {
        return schemaProvider;
    }

    protected GraphQLContext createContext(Optional<HttpServletRequest> req, Optional<HttpServletResponse> resp) {
        return contextBuilder.build(req, resp);
    }

    @Override
    protected ExecutionStrategyProvider getExecutionStrategyProvider() {
        return executionStrategyProvider;
    }

    @Override
    protected Instrumentation getInstrumentation() {
        return instrumentationProvider.getInstrumentation();
    }

    @Override
    protected Map<String, Object> transformVariables(GraphQLSchema schema, String query, Map<String, Object> variables) {
        return new GraphQLVariables(schema, query, variables);
    }
}
