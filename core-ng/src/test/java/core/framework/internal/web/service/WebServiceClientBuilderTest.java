package core.framework.internal.web.service;

import core.framework.http.HTTPMethod;
import core.framework.http.HTTPRequest;
import core.framework.internal.asm.CodeBuilder;
import core.framework.util.ClasspathResources;
import core.framework.util.Types;
import core.framework.web.service.WebServiceClientInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author neo
 */
class WebServiceClientBuilderTest {
    private TestWebService client;
    private WebServiceClientBuilder<TestWebService> builder;
    private WebServiceClient webServiceClient;

    @BeforeEach
    void createTestWebServiceClient() {
        webServiceClient = mock(WebServiceClient.class);
        builder = new WebServiceClientBuilder<>(TestWebService.class, webServiceClient);
        client = builder.build();
    }

    @Test
    void sourceCode() {
        String sourceCode = builder.builder.sourceCode();
        assertThat(sourceCode).isEqualTo(ClasspathResources.text("webservice-test/test-webservice-client.java"));
    }

    @Test
    void intercept() {
        assertThat(client).isInstanceOf(WebServiceClientProxy.class);
        var interceptor = new WebServiceClientInterceptor() {
            @Override
            public void onRequest(HTTPRequest request) {
            }
        };
        ((WebServiceClientProxy) client).intercept(interceptor);
        verify(webServiceClient).intercept(interceptor);
    }

    @Test
    void get() {
        var expectedResponse = new TestWebService.TestResponse();

        when(webServiceClient.execute(HTTPMethod.GET, "/test/1", null, null, Types.optional(TestWebService.TestResponse.class)))
            .thenReturn(Optional.of(expectedResponse));

        TestWebService.TestResponse response = client.get(1).orElseThrow();
        assertThat(response).isSameAs(expectedResponse);
    }

    @Test
    void create() {
        var request = new TestWebService.TestRequest();
        client.create(1, request);

        verify(webServiceClient).execute(HTTPMethod.PUT, "/test/1", TestWebService.TestRequest.class, request, void.class);
    }

    @Test
    void patch() {
        var request = new TestWebService.TestRequest();
        client.patch(1, request);

        verify(webServiceClient).execute(HTTPMethod.PATCH, "/test/1", TestWebService.TestRequest.class, request, void.class);
    }

    @Test
    void buildPath() {
        var builder = new CodeBuilder();
        this.builder.buildPath(builder, "/test", Map.of());
        assertThat(builder.build()).isEqualToIgnoringWhitespace("String path = \"/test\";");

        builder = new CodeBuilder();
        this.builder.buildPath(builder, "/test/:id", Map.of("id", 0));
        assertThat(builder.build()).contains("builder.append(\"/test/\").append(core.framework.internal.web.service.PathParamHelper.toString(param0));");

        builder = new CodeBuilder();
        this.builder.buildPath(builder, "/:id/status", Map.of("id", 0));
        assertThat(builder.build())
            .contains("builder.append(\"/\").append(core.framework.internal.web.service.PathParamHelper.toString(param0));")
            .contains("builder.append(\"/status\");");

        builder = new CodeBuilder();
        this.builder.buildPath(builder, "/test/:key1/:key2", Map.of("key1", 0, "key2", 1));
        assertThat(builder.build())
            .contains("builder.append(\"/test/\").append(core.framework.internal.web.service.PathParamHelper.toString(param0));")
            .contains("builder.append(\"/\").append(core.framework.internal.web.service.PathParamHelper.toString(param1));");

        builder = new CodeBuilder();
        this.builder.buildPath(builder, "/test/:key1/:key2/", Map.of("key1", 0, "key2", 1));
        assertThat(builder.build())
            .contains("builder.append(\"/test/\").append(core.framework.internal.web.service.PathParamHelper.toString(param0));")
            .contains("builder.append(\"/\").append(core.framework.internal.web.service.PathParamHelper.toString(param1));")
            .contains("builder.append(\"/\");");
    }
}
