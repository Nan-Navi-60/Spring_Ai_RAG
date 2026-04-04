package com.spring_ai_rag.repository;

import com.spring_ai_rag.domain.dto.DocumentSearchResultDto;
import com.spring_ai_rag.exception.DocumentProcessingException;
import com.spring_ai_rag.service.DocumentProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * [업로드 Step 4 ~ 5] 텍스트 청킹 후 임베딩하여 Qdrant 벡터 스토어에 저장
 */
@Slf4j
@Repository
public class QdrantDocumentVectorStore {

    private final DocumentProcessingService documentProcessingService;
    private final VectorStore vectorStore; // 스프링이 자동 설정한 Qdrant 구현체가 주입됩니다.

    // 🚀 핵심 수정: EmbeddingService를 직접 받아 객체를 생성하지 않고,
    // Spring이 만들어둔 VectorStore를 바로 주입받습니다.
    public QdrantDocumentVectorStore(VectorStore vectorStore,
                                     DocumentProcessingService documentProcessingService) {
        this.vectorStore = vectorStore;
        this.documentProcessingService = documentProcessingService;
    }

    /**
     * [업로드 Step 4] 텍스트를 청킹한 뒤 [Step 5] 임베딩하여 Qdrant 벡터 스토어에 저장
     */
    public void addDocument(String id, String fileText, Map<String, Object> metadata) {
        log.debug("문서 추가 시작 - ID: {}, 내용 길이: {}", id, fileText.length());
        try {
            Map<String, Object> combinedMetadata = new HashMap<>(metadata);
            combinedMetadata.put("id", id);

            Document document = new Document(fileText, combinedMetadata);

            // [Step 4] 청킹: 임베딩 모델의 토큰 한도를 넘지 않도록 분할
            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(800)
                    .withMinChunkSizeChars(100)
                    .withMinChunkLengthToEmbed(5)
                    .withMaxNumChunks(10000)
                    .withKeepSeparator(true)
                    .build();

            List<Document> chunks = splitter.split(document);
            log.debug("문서 청킹 완료 - ID: {}, 총 청크 수: {}", id, chunks.size());

            // [Step 5] 임베딩 + 저장: Qdrant DB에 실제 저장됨
            vectorStore.add(chunks);
            log.info("Qdrant 문서 추가 완료 - ID: {}", id);
        } catch (Exception e) {
            log.error("문서 추가 실패 - ID: {}", id, e);
            throw new DocumentProcessingException("문서 임베딩 및 저장 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 파일을 처리하여 벡터 스토어에 추가
     */
    public void addDocumentFile(String id, File file, Map<String, Object> metadata) {
        log.debug("파일 문서 추가 시작 - ID: {}, 파일: {}", id, file.getName());
        try {
            String fileText;
            String fileName = file.getName().toLowerCase();

            // 🚀 수정: 확장자에 따라 안전하게 분기 처리
            if (fileName.endsWith(".pdf")) {
                fileText = documentProcessingService.extractTextFromPdf(file);
            } else if (fileName.endsWith(".md") || fileName.endsWith(".txt")) {
                fileText = documentProcessingService.extractTextFromMarkdown(file);
            } else {
                throw new IllegalArgumentException("지원하지 않는 파일 형식입니다. (PDF, MD, TXT만 지원)");
            }
            log.debug("파일 텍스트 추출 완료 - 길이: {}", fileText.length());
            addDocument(id, fileText, metadata);
        } catch (Exception e) {
            log.error("파일 처리 실패 - ID: {}, 파일: {}", id, file.getName(), e);
            throw new DocumentProcessingException("파일 처리 실패: " + e.getMessage(), e);
        }
    }

    /**
     * [질의 Step 3] 질문 벡터와 Qdrant에 저장된 청크 벡터들의 코사인 유사도를 계산하여 반환
     */
    public List<DocumentSearchResultDto> similaritySearch(String query, int maxResults) {
        log.debug("Qdrant 유사도 검색 시작 - 질의: '{}', 최대 결과: {}", query, maxResults);
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(maxResults)
                    .similarityThreshold(0.6)
                    .build();

            // Qdrant DB를 조회하여 유사한 문서를 가져옵니다.
            List<Document> results = vectorStore.similaritySearch(request);
            if (results == null) results = Collections.emptyList();

            log.debug("Qdrant 유사도 검색 완료 - 결과 수: {}", results.size());

            return results.stream().map(result -> {
                String docId = result.getMetadata().getOrDefault("id", "unknown").toString();
                Map<String, Object> filteredMeta = result.getMetadata().entrySet().stream()
                        .filter(e -> !e.getKey().equals("id"))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                double score = result.getScore() != null ? result.getScore() : 0.0;
                return new DocumentSearchResultDto(docId, result.getText(), filteredMeta, score);
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Qdrant 유사도 검색 실패 - 질의: '{}'", query, e);
            throw new DocumentProcessingException("유사도 검색 중 오류 발생: " + e.getMessage(), e);
        }
    }
}