package graphql.kickstart.servlet;

import graphql.ExecutionResult;
import graphql.kickstart.execution.GraphQLObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class SingleQueryResponseWriter implements QueryResponseWriter {

  private final ExecutionResult result;
  private final GraphQLObjectMapper graphQLObjectMapper;

  @Override
  public void write(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setContentType(HttpRequestHandler.APPLICATION_JSON_UTF8);
    response.setStatus(HttpRequestHandler.STATUS_OK);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    String responseContent = graphQLObjectMapper.serializeResultAsJson(result);
    byte[] contentBytes = responseContent.getBytes(StandardCharsets.UTF_8);
    response.setContentLength(contentBytes.length);
    response.getOutputStream().write(contentBytes);
  }

}
