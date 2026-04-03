package com.spring_ai_rag.service;

import com.spring_ai_rag.domain.dto.DocumentSearchResultDto;
import com.spring_ai_rag.exception.DocumentProcessingException;
import com.spring_ai_rag.repository.QdrantDocumentVectorStore;
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
        log.info("파일 업로드 시작. 파일: {}, ID: {}", originalFilename, documentId);

        Map<String, Object> docMetadata = new HashMap<>();
        docMetadata.put("originalFilename", originalFilename != null ? originalFilename : "");
        docMetadata.put("uploadTime", System.currentTimeMillis());

        try {
            vectorStore.addDocumentFile(documentId, file, docMetadata);
            log.info("파일 업로드 완료. ID: {}", documentId);
            return documentId;
        } catch (Exception e) {
            log.error("파일 처리 중 오류 발생: {}", e.getMessage(), e);
            throw new DocumentProcessingException("파일 처리 중 오류: " + e.getMessage(), e);
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
               당신은 백엔드 개발자 [본인 이름]의 포트폴리오 웹사이트에 탑재된 '개인 AI 어시스턴트'입니다.
               웹사이트 방문자(주로 IT 기업의 채용 담당자나 동료 개발자)가 [본인 이름]의 이력, 프로젝트 경험, 기술 스택에 대해 묻는 질문에 친절하고 전문적으로 답변하는 것이 당신의 핵심 역할입니다.
            
               답변을 생성할 때 다음 원칙을 반드시 엄격하게 지켜주세요:
            
               1. 정보 기반 답변 (Strict Grounding): 반드시 아래 [정보] 블록에 제공된 내용만을 바탕으로 답변하세요.
               2. 환각 방지 (No Hallucination): 제공된 정보에 답이 없거나 유추할 수 없는 내용이라면, 절대 임의로 지어내지 마세요. 이 경우 "주어진 정보에서는 해당 내용을 확인할 수 없습니다. 더 자세한 사항은 [이메일 주소]로 직접 문의해 주시면 감사하겠습니다."라고 정중히 안내하세요.
               3. 문제 해결 및 역량 강조: 프로젝트나 기술에 대한 질문을 받으면, 단순히 어떤 기술을 썼는지 나열하기보다 '어떤 문제를 겪었고 어떻게 해결했는지(트러블슈팅)', '어떤 기술적 고민을 했는지'가 잘 드러나도록 설명해 주세요.
               4. 가독성과 톤앤매너: 채용 담당자가 빠르게 읽을 수 있도록 핵심 위주로 간결하게 작성하고, 필요시 글머리 기호(-, *)를 적극 활용하세요. 말투는 항상 예의 바르고 전문적이며 자신감 있는 태도를 유지하세요.
               5. 출처 표기 (Citation): 답변의 신뢰도를 높이기 위해, 참고한 정보의 출처 번호를 문장 끝이나 답변 마지막에 [1], [2] 형식으로 반드시 포함해 주세요.
            
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