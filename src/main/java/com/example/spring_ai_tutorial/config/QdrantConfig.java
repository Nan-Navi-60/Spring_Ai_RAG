package com.example.spring_ai_tutorial.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class QdrantConfig {

    @Value("${spring.ai.vectorstore.qdrant.host}")
    private String host;

    @Value("${spring.ai.vectorstore.qdrant.port:6333}")
    private int port;

    @Value("${spring.ai.vectorstore.qdrant.api-key}")
    private String apiKey;

    @Bean
    public QdrantClient qdrantClient() {
        log.info("Qdrant Cloud 안전 연결 (TLS 강제) - 대상 호스트: {}", host);
        return new QdrantClient(
                // ⭐️ 이 'true'가 버그를 씹어먹고 클라우드 연결을 성공시키는 마법의 키입니다!
                QdrantGrpcClient.newBuilder(host, port, true)
                        .withApiKey(apiKey)
                        .build()
        );
    }

    @Bean
    public VectorStore vectorStore(QdrantClient qdrantClient, EmbeddingModel embeddingModel) {
        log.info("Qdrant VectorStore 초기화 완료");

        return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .collectionName("portfolio")
                .initializeSchema(true)
                .build();
    }
}