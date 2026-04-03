package com.example.spring_ai_tutorial.service;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * Spring AI 표준 인터페이스를 사용하여 텍스트를 벡터로 변환합니다.
 */
@Service
public class EmbeddingService {

    // 특정 벤더(OpenAI, Google 등)에 종속되지 않는 표준 인터페이스
    private final EmbeddingModel embeddingModel;

    // application.yml의 spring.ai.google.genai 설정을 바탕으로
    // Spring Boot가 Google GenAI용 객체를 알아서 생성하고 주입해 줍니다.
    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    /**
     * (추가 제안) 텍스트를 바로 벡터 배열로 변환하는 편의 메서드
     */
    public float[] embedText(String text) {
        return embeddingModel.embed(text);
    }
}