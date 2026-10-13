package com.first.app.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ZhipuEmbeddingConfigTest {

    @Test
    void embeddingModel_callsTheZhipuEmbeddingsPathWithBearerKeyModelAndDimensions() throws Exception {
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/paas/v4/embeddings", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ("{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"index\":0,"
                    + "\"embedding\":[0.1,0.2,0.3]}],\"model\":\"embedding-3\","
                    + "\"usage\":{\"prompt_tokens\":2,\"total_tokens\":2}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            KnowledgeBaseProperties properties = new KnowledgeBaseProperties(
                    "test-key",
                    "http://127.0.0.1:" + port + "/api/paas/v4",
                    null, 0, 0, 0, 0, null, null);

            EmbeddingModel model = KnowledgeBaseConfig.buildEmbeddingModel(properties);
            float[] vector = model.embed("杭州西湖值得去吗");

            assertThat(capturedPath.get()).isEqualTo("/api/paas/v4/embeddings");
            assertThat(capturedAuth.get()).isEqualTo("Bearer test-key");
            assertThat(capturedBody.get())
                    .contains("\"model\":\"embedding-3\"")
                    .contains("\"dimensions\":2048");
            assertThat(vector).containsExactly(0.1f, 0.2f, 0.3f);
        }
        finally {
            server.stop(0);
        }
    }
}
