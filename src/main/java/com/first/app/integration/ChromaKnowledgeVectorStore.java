package com.first.app.integration;

import com.first.app.exception.KnowledgeBaseUnavailableException;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.chroma.vectorstore.common.ChromaApiConstants;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

/**
 * Chroma-backed {@link KnowledgeVectorStore}. The underlying {@link ChromaVectorStore} is built
 * lazily on first use, so application startup never contacts the vector store; every store or
 * transport failure surfaces as {@link KnowledgeBaseUnavailableException}.
 */
public class ChromaKnowledgeVectorStore implements KnowledgeVectorStore {

    private static final String TENANT_NAME = ChromaApiConstants.DEFAULT_TENANT_NAME;

    private static final String DATABASE_NAME = ChromaApiConstants.DEFAULT_DATABASE_NAME;

    private final ChromaApi chromaApi;

    private final EmbeddingModel embeddingModel;

    private final String collectionName;

    private volatile ChromaVectorStore store;

    public ChromaKnowledgeVectorStore(ChromaApi chromaApi, EmbeddingModel embeddingModel, String collectionName) {
        this.chromaApi = chromaApi;
        this.embeddingModel = embeddingModel;
        this.collectionName = collectionName;
    }

    @Override
    public void add(List<Chunk> chunks) {
        try {
            store().add(chunks.stream()
                    .map(chunk -> new Document(chunk.id(), chunk.text(), chunk.metadata()))
                    .toList());
        }
        catch (KnowledgeBaseUnavailableException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw unavailable("store chunks", ex);
        }
    }

    @Override
    public void deleteBySource(String sourceType, long sourceId) {
        try {
            FilterExpressionBuilder filters = new FilterExpressionBuilder();
            store().delete(filters.and(filters.eq("sourceType", sourceType), filters.eq("sourceId", sourceId))
                    .build());
        }
        catch (KnowledgeBaseUnavailableException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw unavailable("delete chunks of source " + sourceType + ":" + sourceId, ex);
        }
    }

    @Override
    public void reset() {
        store(); // liveness: throws when the store is unreachable
        try {
            chromaApi.deleteCollection(TENANT_NAME, DATABASE_NAME, collectionName);
        }
        catch (HttpClientErrorException.NotFound ex) {
            // The collection never existed — reset is already satisfied.
        }
        catch (Exception ex) {
            throw unavailable("reset the collection", ex);
        }
        this.store = null;
    }

    @Override
    public List<Hit> search(String query, int topK) {
        try {
            return store().similaritySearch(SearchRequest.builder().query(query).topK(topK).build())
                    .stream()
                    .map(document -> new Hit(document.getText(), document.getMetadata()))
                    .toList();
        }
        catch (KnowledgeBaseUnavailableException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw unavailable("search the knowledge base", ex);
        }
    }

    private ChromaVectorStore store() {
        ChromaVectorStore current = this.store;
        if (current == null) {
            synchronized (this) {
                current = this.store;
                if (current == null) {
                    current = initialize();
                    this.store = current;
                }
            }
        }
        return current;
    }

    private ChromaVectorStore initialize() {
        try {
            // initializeSchema(true): the builder's default refuses to create a missing
            // collection and fails every operation instead; creation must happen here, lazily on
            // first use — never at application startup.
            ChromaVectorStore created = ChromaVectorStore.builder(chromaApi, embeddingModel)
                    .tenantName(TENANT_NAME)
                    .databaseName(DATABASE_NAME)
                    .collectionName(collectionName)
                    .initializeSchema(true)
                    .build();
            created.afterPropertiesSet();
            return created;
        }
        catch (Exception ex) {
            throw unavailable("initialize the collection", ex);
        }
    }

    private static KnowledgeBaseUnavailableException unavailable(String action, Exception cause) {
        return new KnowledgeBaseUnavailableException(
                "Knowledge base store unavailable — failed to " + action + ": " + cause.getMessage(), cause);
    }
}
