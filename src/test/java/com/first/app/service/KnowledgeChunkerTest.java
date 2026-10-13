package com.first.app.service;

import com.first.app.config.KnowledgeBaseProperties;
import com.first.app.entity.Attraction;
import com.first.app.entity.AttractionCategory;
import com.first.app.entity.AttractionStatus;
import com.first.app.entity.City;
import com.first.app.entity.CityStatus;
import com.first.app.entity.Post;
import com.first.app.integration.KnowledgeVectorStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeChunkerTest {

    private static final KnowledgeBaseProperties DEFAULT_PROPERTIES =
            new KnowledgeBaseProperties(null, null, null, 0, 0, 0, 0, null, null);

    private final KnowledgeChunker chunker = new KnowledgeChunker(DEFAULT_PROPERTIES);

    @Test
    void shortPost_isASingleChunkWithPrefixMetadataAndDeterministicId() {
        Post post = Post.builder()
                .id(42L)
                .title("西湖游记")
                .content("西湖的春天最美。")
                .build();

        List<KnowledgeVectorStore.Chunk> chunks = chunker.chunkPost(post);

        assertThat(chunks).hasSize(1);
        KnowledgeVectorStore.Chunk chunk = chunks.get(0);
        assertThat(chunk.id()).isEqualTo("posts:42:0");
        assertThat(chunk.text()).isEqualTo("《西湖游记》 (posts)\n西湖的春天最美。");
        assertThat(chunk.metadata())
                .containsEntry("sourceType", "posts")
                .containsEntry("sourceId", 42L)
                .containsEntry("title", "西湖游记")
                .containsEntry("chunkIndex", 0);
    }

    @Test
    void longBody_splitsIntoOverlappingChunksWithSequentialIds() {
        String body = "0123456789".repeat(200); // 2000 chars
        Post post = Post.builder()
                .id(43L)
                .title("长文")
                .content(body)
                .build();

        List<KnowledgeVectorStore.Chunk> chunks = chunker.chunkPost(post);

        String prefix = "《长文》 (posts)\n";
        assertThat(chunks).extracting(KnowledgeVectorStore.Chunk::id)
                .containsExactly("posts:43:0", "posts:43:1", "posts:43:2");
        assertThat(chunks).extracting(KnowledgeVectorStore.Chunk::metadata)
                .extracting(metadata -> metadata.get("chunkIndex"))
                .containsExactly(0, 1, 2);

        String first = chunks.get(0).text().substring(prefix.length());
        String second = chunks.get(1).text().substring(prefix.length());
        String third = chunks.get(2).text().substring(prefix.length());
        assertThat(first).hasSize(800);
        assertThat(second).hasSize(800);
        assertThat(third).hasSize(600);
        assertThat(second).startsWith(first.substring(700));
        assertThat(first + second.substring(100) + third.substring(100)).isEqualTo(body);
    }

    @Test
    void bodyOfExactlyOneChunkSize_isASingleChunk() {
        String body = "x".repeat(800);
        Post post = Post.builder()
                .id(44L)
                .title("恰好")
                .content(body)
                .build();

        List<KnowledgeVectorStore.Chunk> chunks = chunker.chunkPost(post);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text().substring("《恰好》 (posts)\n".length())).isEqualTo(body);
    }

    @Test
    void customChunkSizeAndOverlap_driveTheSplit() {
        KnowledgeChunker custom = new KnowledgeChunker(
                new KnowledgeBaseProperties(null, null, null, 0, 0, 100, 20, null, null));
        Post post = Post.builder()
                .id(45L)
                .title("短文")
                .content("y".repeat(250))
                .build();

        List<KnowledgeVectorStore.Chunk> chunks = custom.chunkPost(post);

        String prefix = "《短文》 (posts)\n";
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).text().length() - prefix.length()).isEqualTo(100);
        assertThat(chunks.get(1).text().length() - prefix.length()).isEqualTo(100);
        assertThat(chunks.get(2).text().length() - prefix.length()).isEqualTo(90);
    }

    @Test
    void attraction_composesFieldsUnderTheChineseNameTitle() {
        Attraction attraction = Attraction.builder()
                .id(7L)
                .slug("west-lake")
                .name("West Lake")
                .nameZh("西湖")
                .category(AttractionCategory.NATURE)
                .tags(List.of("nature", "lake"))
                .city("杭州")
                .citySlug("hangzhou")
                .summary("著名景点")
                .description("详细的风光介绍")
                .openingHours("08:00-18:00")
                .ticketPrice("免费")
                .status(AttractionStatus.PUBLISHED)
                .build();

        List<KnowledgeVectorStore.Chunk> chunks = chunker.chunkAttraction(attraction);

        assertThat(chunks).hasSize(1);
        KnowledgeVectorStore.Chunk chunk = chunks.get(0);
        assertThat(chunk.id()).isEqualTo("attractions:7:0");
        assertThat(chunk.text()).startsWith("《西湖》 (attractions)\n");
        assertThat(chunk.text())
                .contains("West Lake")
                .contains("NATURE")
                .contains("杭州")
                .contains("nature")
                .contains("lake")
                .contains("著名景点")
                .contains("详细的风光介绍")
                .contains("08:00-18:00")
                .contains("免费");
        assertThat(chunk.metadata())
                .containsEntry("sourceType", "attractions")
                .containsEntry("sourceId", 7L)
                .containsEntry("title", "西湖");
    }

    @Test
    void attractionWithoutOptionalFields_skipsEmptySegments() {
        Attraction attraction = Attraction.builder()
                .id(8L)
                .slug("museum")
                .name("Museum")
                .nameZh("博物馆")
                .category(AttractionCategory.MUSEUM)
                .city("北京")
                .citySlug("beijing")
                .summary("简介")
                .description("描述")
                .status(AttractionStatus.PUBLISHED)
                .build();

        List<KnowledgeVectorStore.Chunk> chunks = chunker.chunkAttraction(attraction);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text())
                .contains("Museum")
                .contains("描述")
                .doesNotContain("null")
                .doesNotContain("\n\n");
    }

    @Test
    void city_composesFieldsUnderTheChineseNameTitle() {
        City city = City.builder()
                .id(3L)
                .slug("hangzhou")
                .name("Hangzhou")
                .nameZh("杭州")
                .bestSeason("春季")
                .description("人间天堂")
                .status(CityStatus.PUBLISHED)
                .build();

        List<KnowledgeVectorStore.Chunk> chunks = chunker.chunkCity(city);

        assertThat(chunks).hasSize(1);
        KnowledgeVectorStore.Chunk chunk = chunks.get(0);
        assertThat(chunk.id()).isEqualTo("cities:3:0");
        assertThat(chunk.text()).startsWith("《杭州》 (cities)\n");
        assertThat(chunk.text())
                .contains("Hangzhou")
                .contains("春季")
                .contains("人间天堂");
        assertThat(chunk.metadata())
                .containsEntry("sourceType", "cities")
                .containsEntry("sourceId", 3L)
                .containsEntry("title", "杭州");
    }
}
