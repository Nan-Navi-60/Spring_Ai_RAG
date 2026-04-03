package com.example.spring_ai_tutorial.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring AI 표준 인터페이스를 사용하여 질의응답을 수행하는 서비스
 */
@Slf4j
@Service
public class ChatService {

    // 특정 벤더(OpenAI 등)의 클래스가 아닌 범용 인터페이스를 주입받습니다.
    private final ChatModel chatModel;

    // application.yml 설정에 따라 Google GenAI용 구현체가 자동으로 주입됩니다.
    public ChatService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 챗 API를 이용하여 응답을 생성
     */
    public ChatResponse chat(String userInput, String systemMessage, String model) {
        log.debug("챗 호출 시작 - 모델: {}", model);
        try {
            Prompt prompt = new Prompt(
                    List.of(new SystemMessage(systemMessage), new UserMessage(userInput)),
                    // 벤더에 구애받지 않는 표준 ChatOptions 사용
                    ChatOptions.builder().model(model).build()
            );

            // 매번 객체를 새로 build 할 필요 없이 주입받은 빈의 call 메서드를 사용합니다.
            return chatModel.call(prompt);
        } catch (Exception e) {
            log.error("챗 호출 중 오류 발생: {}", e.getMessage(), e);
            return null;
        }
    }
}