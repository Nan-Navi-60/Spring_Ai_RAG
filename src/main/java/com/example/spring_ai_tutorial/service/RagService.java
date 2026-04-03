package com.example.spring_ai_tutorial.service;

import com.example.spring_ai_tutorial.domain.dto.DocumentSearchResultDto;
import com.example.spring_ai_tutorial.exception.DocumentProcessingException;
import com.example.spring_ai_tutorial.repository.QdrantDocumentVectorStore;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * [업로드 Step 2]
 * documentId 생성 및 메타데이터 구성 후 벡터 스토어에 저장
 * * 문서 업로드, 검색, 그리고 검색 결과를 활용한 LLM 응답 생성을 담당
 */
@Slf4j
@Service
public class RagService {

    // 💡 참고: 현재는 직접 만드신 InMemoryDocumentVectorStore를 사용 중입니다.
    // 향후 Qdrant를 완벽하게 활용하시려면 이 부분을 Spring AI의 표준 인터페이스인
    // org.springframework.ai.vectorstore.VectorStore 로 교체하는 작업이 필요합니다.
    private final QdrantDocumentVectorStore vectorStore;
    private final ChatService chatService;


    public RagService(QdrantDocumentVectorStore vectorStore, ChatService chatService) {
        this.vectorStore = vectorStore;
        this.chatService = chatService;
    }

    /**
     * PDF 파일을 업로드하여 벡터 스토어에 추가하는 작업 수행
     */
    public String uploadPdfFile(File file, String originalFilename) {
        String documentId = UUID.randomUUID().toString();
        log.info("PDF 문서 업로드 시작. 파일: {}, ID: {}", originalFilename, documentId);

        Map<String, Object> docMetadata = new HashMap<>();
        docMetadata.put("originalFilename", originalFilename != null ? originalFilename : "");
        docMetadata.put("uploadTime", System.currentTimeMillis());

        try {
            vectorStore.addDocumentFile(documentId, file, docMetadata);
            log.info("PDF 문서 업로드 완료. ID: {}", documentId);
            return documentId;
        } catch (Exception e) {
            log.error("문서 처리 중 오류 발생: {}", e.getMessage(), e);
            throw new DocumentProcessingException("문서 처리 중 오류: " + e.getMessage(), e);
        }
    }

    /**
     * [질의 Step 2] 질문과 유사한 문서 청크를 벡터 스토어에서 검색함
     */
    public List<DocumentSearchResultDto> retrieve(String question, int maxResults) {
        log.debug("검색 시작: '{}', 최대 결과 수: {}", question, maxResults);
        return vectorStore.similaritySearch(question, maxResults);
    }

    /**
     * [질의 Step 4] 검색된 청크를 컨텍스트로 조합하여 systemPrompt를 구성하고 LLM을 호출
     */
    public String generateAnswerWithContexts(String question, List<DocumentSearchResultDto> relevantDocs, String model) {
        log.debug("RAG 응답 생성 시작: '{}', 모델: {}", question, model);

        if (relevantDocs.isEmpty()) {
            log.info("관련 정보를 찾을 수 없음: '{}'", question);
            return "관련 정보를 찾을 수 없습니다. 다른 질문을 시도하거나 관련 문서를 업로드해 주세요.";
        }

        String context = IntStream.range(0, relevantDocs.size())
                .mapToObj(i -> "[" + (i + 1) + "] " + relevantDocs.get(i).getContent())
                .collect(Collectors.joining("\n\n"));

        String systemPrompt = """
                당신은 지식 기반 Q&A 시스템입니다.
                사용자의 질문에 대한 답변을 다음 정보를 바탕으로 생성해주세요.
                주어진 정보에 답이 없다면 모른다고 솔직히 말해주세요.
                답변 마지막에 사용한 정보의 출처 번호 [1], [2] 등을 반드시 포함해주세요.
                
                정보:
                """ + context;

        try {
            // 🚀 핵심 수정: openAiChat -> chat 으로 메서드명 변경 완료
            var response = chatService.chat(question, systemPrompt, model);

            String aiAnswer = (response != null && response.getResult() != null)
                    ? response.getResult().getOutput().getText()
                    : "응답을 생성할 수 없습니다.";

            StringBuilder sourceInfo = new StringBuilder("\n\n참고 문서:\n");
            for (int i = 0; i < relevantDocs.size(); i++) {
                String filename = (String) relevantDocs.get(i).getMetadata().getOrDefault("originalFilename", "Unknown file");
                sourceInfo.append("[").append(i + 1).append("] ").append(filename).append("\n");
            }

            return aiAnswer + sourceInfo;
        } catch (Exception e) {
            log.error("AI 모델 호출 중 오류 발생: {}", e.getMessage(), e);
            return "AI 모델 호출 중 오류가 발생했습니다. 검색 결과만 제공합니다:\n\n" +
                    relevantDocs.stream().map(DocumentSearchResultDto::getContent).collect(Collectors.joining("\n\n"));
        }
    }
}