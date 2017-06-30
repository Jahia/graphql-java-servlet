package graphql.servlet;

import java.util.Collection;

public interface GraphQLAnnotatedClassProvider {

    Collection<Class<?>> getExtensions();
}
