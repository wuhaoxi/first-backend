package com.first.app.repository;

import com.first.app.TestcontainersConfiguration;
import com.first.app.entity.Attraction;
import com.first.app.entity.AttractionCategory;
import com.first.app.entity.AttractionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AttractionRepositoryTest {

    private static final Sort LIST_SORT = Sort.by(
            Sort.Order.desc("isPopular"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final Sort POPULAR_SORT = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    @Autowired
    private AttractionRepository attractionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldPersistAndReadBackAllCatalogFields() {
        Attraction attraction = buildPublished("forbidden-city", "Forbidden City", "beijing",
                AttractionCategory.HISTORICAL_SITE);
        attraction.setNameZh("故宫");
        attraction.setTags(List.of("unesco", "must-see"));
        attraction.setProvince("Beijing");
        attraction.setAddress("4 Jingshan Front Street");
        attraction.setLatitude(39.9163);
        attraction.setLongitude(116.3972);
        attraction.setSummary("Imperial palace at the heart of Beijing");
        attraction.setDescription("The Forbidden City served as the home of emperors for five centuries.");
        attraction.setCoverImageUrl("https://example.com/forbidden-city.jpg");
        attraction.setOpeningHours("08:30-17:00 (closed Mondays)");
        attraction.setTicketPrice("¥60");
        attraction.setBookingRequired(true);
        attraction.setBookingNote("Book with passport 7 days ahead");
        attraction.setSuggestedDuration("2-3 hours");
        attraction.setPopular(true);

        entityManager.persist(attraction);
        entityManager.flush();
        entityManager.clear();

        Attraction found = attractionRepository
                .findBySlugAndStatus("forbidden-city", AttractionStatus.PUBLISHED)
                .orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getSlug()).isEqualTo("forbidden-city");
        assertThat(found.getName()).isEqualTo("Forbidden City");
        assertThat(found.getNameZh()).isEqualTo("故宫");
        assertThat(found.getCategory()).isEqualTo(AttractionCategory.HISTORICAL_SITE);
        assertThat(found.getTags()).containsExactly("unesco", "must-see");
        assertThat(found.getCity()).isEqualTo("beijing");
        assertThat(found.getCitySlug()).isEqualTo("beijing");
        assertThat(found.getProvince()).isEqualTo("Beijing");
        assertThat(found.getAddress()).isEqualTo("4 Jingshan Front Street");
        assertThat(found.getLatitude()).isEqualTo(39.9163);
        assertThat(found.getLongitude()).isEqualTo(116.3972);
        assertThat(found.getSummary()).isEqualTo("Imperial palace at the heart of Beijing");
        assertThat(found.getDescription()).startsWith("The Forbidden City served");
        assertThat(found.getCoverImageUrl()).isEqualTo("https://example.com/forbidden-city.jpg");
        assertThat(found.getOpeningHours()).isEqualTo("08:30-17:00 (closed Mondays)");
        assertThat(found.getTicketPrice()).isEqualTo("¥60");
        assertThat(found.isBookingRequired()).isTrue();
        assertThat(found.getBookingNote()).isEqualTo("Book with passport 7 days ahead");
        assertThat(found.getSuggestedDuration()).isEqualTo("2-3 hours");
        assertThat(found.getStatus()).isEqualTo(AttractionStatus.PUBLISHED);
        assertThat(found.isPopular()).isTrue();
    }

    @Test
    void shouldReadBackEmptyTagsWhenNoneSaved() {
        Attraction attraction = buildPublished("west-lake", "West Lake", "hangzhou",
                AttractionCategory.NATURE);
        entityManager.persist(attraction);
        entityManager.flush();
        entityManager.clear();

        Attraction found = attractionRepository
                .findBySlugAndStatus("west-lake", AttractionStatus.PUBLISHED)
                .orElseThrow();

        assertThat(found.getTags()).isEmpty();
    }

    @Test
    void shouldNotFindDraftOrUnknownSlug() {
        Attraction draft = buildPublished("secret-spot", "Secret Spot", "beijing",
                AttractionCategory.MUSEUM);
        draft.setStatus(AttractionStatus.DRAFT);
        entityManager.persist(draft);
        entityManager.flush();
        entityManager.clear();

        assertThat(attractionRepository.findBySlugAndStatus("secret-spot", AttractionStatus.PUBLISHED))
                .isEmpty();
        assertThat(attractionRepository.findBySlugAndStatus("no-such-place", AttractionStatus.PUBLISHED))
                .isEmpty();
    }

    @Test
    void shouldRejectDuplicateSlug() {
        attractionRepository.saveAndFlush(buildPublished("duplicate-slug", "First", "beijing",
                AttractionCategory.MUSEUM));

        Attraction second = buildPublished("duplicate-slug", "Second", "xian",
                AttractionCategory.MUSEUM);

        assertThatThrownBy(() -> attractionRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldSearchWithNullableFiltersAndOrderByPopularThenCreatedAtThenId() {
        Attraction a = buildPublished("a-popular-beijing-hs", "Attraction A", "beijing",
                AttractionCategory.HISTORICAL_SITE);
        a.setPopular(true);
        Attraction b = buildPublished("b-beijing-museum", "Attraction B", "beijing",
                AttractionCategory.MUSEUM);
        Attraction c = buildPublished("c-beijing-hs", "Attraction C", "beijing",
                AttractionCategory.HISTORICAL_SITE);
        Attraction d = buildPublished("d-popular-xian-hs", "Attraction D", "xian",
                AttractionCategory.HISTORICAL_SITE);
        d.setPopular(true);
        Attraction e = buildPublished("e-draft-popular", "Attraction E", "beijing",
                AttractionCategory.HISTORICAL_SITE);
        e.setPopular(true);
        e.setStatus(AttractionStatus.DRAFT);
        Attraction f = buildPublished("f-shanghai-temple", "Attraction F", "shanghai",
                AttractionCategory.TEMPLE);
        persistAndFlush(a, b, c, d, e, f);

        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        updateCreatedAt(a.getId(), base);
        updateCreatedAt(d.getId(), base);
        updateCreatedAt(f.getId(), base.minusHours(1));
        updateCreatedAt(c.getId(), base.minusHours(2));
        updateCreatedAt(b.getId(), base.minusHours(3));
        entityManager.clear();

        // No filters: drafts excluded; popular first, then createdAt DESC, id DESC tiebreak
        Page<Attraction> all = attractionRepository.search(
                AttractionStatus.PUBLISHED, null, null, PageRequest.of(0, 20, LIST_SORT));
        assertThat(all.getContent()).extracting(Attraction::getId)
                .containsExactly(d.getId(), a.getId(), f.getId(), c.getId(), b.getId());
        assertThat(all.getTotalElements()).isEqualTo(5);

        // Pagination across the filtered list
        Page<Attraction> firstPage = attractionRepository.search(
                AttractionStatus.PUBLISHED, null, null, PageRequest.of(0, 2, LIST_SORT));
        assertThat(firstPage.getContent()).extracting(Attraction::getId)
                .containsExactly(d.getId(), a.getId());
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);

        // City filter matches citySlug only
        Page<Attraction> beijing = attractionRepository.search(
                AttractionStatus.PUBLISHED, "beijing", null, PageRequest.of(0, 20, LIST_SORT));
        assertThat(beijing.getContent()).extracting(Attraction::getId)
                .containsExactly(a.getId(), c.getId(), b.getId());

        // Category filter
        Page<Attraction> historical = attractionRepository.search(
                AttractionStatus.PUBLISHED, null, AttractionCategory.HISTORICAL_SITE,
                PageRequest.of(0, 20, LIST_SORT));
        assertThat(historical.getContent()).extracting(Attraction::getId)
                .containsExactly(d.getId(), a.getId(), c.getId());

        // Combined filters
        Page<Attraction> combined = attractionRepository.search(
                AttractionStatus.PUBLISHED, "beijing", AttractionCategory.HISTORICAL_SITE,
                PageRequest.of(0, 20, LIST_SORT));
        assertThat(combined.getContent()).extracting(Attraction::getId)
                .containsExactly(a.getId(), c.getId());

        // Unknown city slug -> empty page, not an error
        Page<Attraction> none = attractionRepository.search(
                AttractionStatus.PUBLISHED, "guilin", null, PageRequest.of(0, 20, LIST_SORT));
        assertThat(none.getContent()).isEmpty();
        assertThat(none.getTotalElements()).isZero();
    }

    @Test
    void shouldFindPopularWithinLimitExcludingDrafts() {
        Attraction a = buildPublished("a-popular", "Popular A", "beijing", AttractionCategory.MUSEUM);
        a.setPopular(true);
        Attraction b = buildPublished("b-popular", "Popular B", "xian", AttractionCategory.MUSEUM);
        b.setPopular(true);
        Attraction draftPopular = buildPublished("c-popular-draft", "Draft Popular", "shanghai",
                AttractionCategory.MUSEUM);
        draftPopular.setPopular(true);
        draftPopular.setStatus(AttractionStatus.DRAFT);
        Attraction regular = buildPublished("d-regular", "Regular", "chengdu", AttractionCategory.MUSEUM);
        persistAndFlush(a, b, draftPopular, regular);

        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        updateCreatedAt(a.getId(), base.minusHours(2));
        updateCreatedAt(b.getId(), base.minusHours(1));
        entityManager.clear();

        List<Attraction> limited = attractionRepository.findByStatusAndIsPopularTrue(
                AttractionStatus.PUBLISHED, PageRequest.of(0, 1, POPULAR_SORT));
        assertThat(limited).extracting(Attraction::getId).containsExactly(b.getId());

        List<Attraction> all = attractionRepository.findByStatusAndIsPopularTrue(
                AttractionStatus.PUBLISHED, PageRequest.of(0, 10, POPULAR_SORT));
        assertThat(all).extracting(Attraction::getId)
                .containsExactly(b.getId(), a.getId());
    }

    private Attraction buildPublished(String slug, String name, String citySlug,
                                      AttractionCategory category) {
        return Attraction.builder()
                .slug(slug)
                .name(name)
                .nameZh(name + "（中文名）")
                .category(category)
                .city(citySlug)
                .citySlug(citySlug)
                .summary("Summary of " + name)
                .description("Description of " + name)
                .status(AttractionStatus.PUBLISHED)
                .build();
    }

    private void persistAndFlush(Object... entities) {
        for (Object entity : entities) {
            entityManager.persist(entity);
        }
        entityManager.flush();
    }

    private void updateCreatedAt(Long id, LocalDateTime createdAt) {
        entityManager.createNativeQuery("UPDATE attractions SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", id)
                .executeUpdate();
    }
}
