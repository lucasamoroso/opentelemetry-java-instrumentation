/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javalin.v5_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.javalin.Javalin;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.ClientAttributes;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.UserAgentAttributes;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JavalinInstrumentationTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static Javalin app;
  static int port;
  static WebClient client;

  @BeforeAll
  static void setup() {
    port = PortUtils.findOpenPort();

    app = TestJavalinJavaApplication.initJavalin(port);

    client = WebClient.of("http://localhost:" + port);
  }

  @AfterAll
  static void cleanup() {
    app.stop();
  }

  @Test
  void testSpanNameAndHttpRouteSpanWithPathParamResponseSuccessful() {
    String id = "123";
    AggregatedHttpResponse response = client.get("/param/" + id).aggregate().join();
    String content = response.contentUtf8();

    assertNotEquals(port, 0);
    assertEquals(content, id);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /param/{id}")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(UrlAttributes.URL_SCHEME, "http"),
                            equalTo(UrlAttributes.URL_PATH, "/param/" + id),
                            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                            satisfies(
                                UserAgentAttributes.USER_AGENT_ORIGINAL,
                                val -> val.isInstanceOf(String.class)),
                            equalTo(HTTP_ROUTE, "/param/{id}"),
                            equalTo(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                            equalTo(ServerAttributes.SERVER_PORT, port),
                            equalTo(ClientAttributes.CLIENT_ADDRESS, "127.0.0.1"),
                            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT,
                                val -> val.isInstanceOf(Long.class)))));
  }

  @Test
  void testSpanNameAndHttpRouteSpanResponseError() {
    client.get("/error").aggregate().join();

    assertNotEquals(port, 0);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /error")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(UrlAttributes.URL_SCHEME, "http"),
                            equalTo(UrlAttributes.URL_PATH, "/error"),
                            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 500),
                            satisfies(
                                UserAgentAttributes.USER_AGENT_ORIGINAL,
                                val -> val.isInstanceOf(String.class)),
                            equalTo(HTTP_ROUTE, "/error"),
                            equalTo(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                            equalTo(ServerAttributes.SERVER_PORT, port),
                            equalTo(ErrorAttributes.ERROR_TYPE, "500"),
                            equalTo(ClientAttributes.CLIENT_ADDRESS, "127.0.0.1"),
                            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT,
                                val -> val.isInstanceOf(Long.class)))));
  }

  @Test
  public void testSpanNameAndHttpRouteSpanAsyncRouteResponseSuccessful() {
    client.get("/async").aggregate().join();

    assertNotEquals(port, 0);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /async")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(UrlAttributes.URL_SCHEME, "http"),
                            equalTo(UrlAttributes.URL_PATH, "/async"),
                            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                            satisfies(
                                UserAgentAttributes.USER_AGENT_ORIGINAL,
                                val -> val.isInstanceOf(String.class)),
                            equalTo(HTTP_ROUTE, "/async"),
                            equalTo(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                            equalTo(ServerAttributes.SERVER_PORT, port),
                            equalTo(ClientAttributes.CLIENT_ADDRESS, "127.0.0.1"),
                            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT,
                                val -> val.isInstanceOf(Long.class)))));
  }

  @Test
  void testHttpRouteMetricWithPathParamResponseSuccessful() {
    String id = "123";
    AggregatedHttpResponse response = client.get("/param/" + id).aggregate().join();
    String content = response.contentUtf8();
    String instrumentation = "io.opentelemetry.jetty-11.0";

    assertNotEquals(port, 0);
    assertEquals(content, id);

    testing.waitAndAssertMetrics(
        instrumentation,
        "http.server.request.duration",
        metrics ->
            metrics.anySatisfy(
                metric -> {
                  String metricHttpRoute =
                      metric.getData().getPoints().stream()
                          .findFirst()
                          .get()
                          .getAttributes()
                          .get(HTTP_ROUTE);
                  assertEquals("/param/{id}", metricHttpRoute);
                }));
  }
}
