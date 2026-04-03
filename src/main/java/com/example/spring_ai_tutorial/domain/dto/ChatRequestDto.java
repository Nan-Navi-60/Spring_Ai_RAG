package com.example.spring_ai_tutorial.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 채팅 요청 데이터 모델
 */
@Schema(description = "채팅 요청 데이터 모델")
public class ChatRequestDto {

    @Schema(description = "사용자 질문", example = "안녕하세요")
    private String query;

    @Schema(description = "사용할 LLM 모델", example = "gemini-2.5-flash-lite", defaultValue = "gemini-2.5-flash-lite")
    private String model = "gemini-2.5-flash-lite";

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
