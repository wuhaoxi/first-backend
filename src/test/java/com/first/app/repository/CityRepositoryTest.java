package com.first.app.repository;

import com.first.app.TestcontainersConfiguration;
import com.first.app.entity.City;
import com.first.app.entity.CityStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CityRepositoryTest {

    private static final Sort NAME_SORT = Sort.by(
            Sort.Order.asc("name"), Sort.Order.asc("id"));

    @Autowired
    private CityRepository cityRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldPersistAndReadBackAllCatalogFields() {
        City city = buildPublished("beijing", "Beijing", "北京");
        city.setCoverImageUrl("https://example.com/beijing.jpg");
        city.setDescription("Capital of China with imperial landmarks.");
        city.setBestSeason("September–October & April–May");

        entityManager.persist(city);
        entityManager.flush();
        entityManager.clear();

        City found = cityRepository
                .findBySlugAndStatus("beijing", CityStatus.PUBLISHED)
                .orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getSlug()).isEqualTo("beijing");
        assertThat(found.getName()).isEqualTo("Beijing");
        assertThat(found.getNameZh()).isEqualTo("北京");
        assertThat(found.getCoverImageUrl()).isEqualTo("https://example.com/beijing.jpg");
        assertThat(found.getDescription()).isEqualTo("Capital of China with imperial landmarks.");
        assertThat(found.getBestSeason()).isEqualTo("September–October & April–May");
        assertThat(found.getStatus()).isEqualTo(CityStatus.PUBLISHED);
    }

    @Test
    void shouldRejectDuplicateSlug() {
        cityRepository.saveAndFlush(buildPublished("duplicate-slug", "First", "第一"));

        City second = buildPublished("duplicate-slug", "Second", "第二");

        assertThatThrownBy(() -> cityRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldFindPublishedOnlyOrderedByNameThenId() {
        City beijing = buildPublished("beijing", "Beijing", "北京");
        City xian = buildPublished("xian", "Xi'an", "西安");
        City shanghai = buildPublished("shanghai", "Shanghai", "上海");
        City chengdu = buildPublished("chengdu", "Chengdu", "成都");
        City draft = buildPublished("draft-city", "Aaa Draft City", "草稿");
        draft.setStatus(CityStatus.DRAFT);
        persistAndFlush(beijing, xian, shanghai, chengdu, draft);

        // Drafts excluded; ordering by name ASC
        Page<City> all = cityRepository.findByStatus(CityStatus.PUBLISHED,
                PageRequest.of(0, 20, NAME_SORT));
        assertThat(all.getContent()).extracting(City::getSlug)
                .containsExactly("beijing", "chengdu", "shanghai", "xian");
        assertThat(all.getTotalElements()).isEqualTo(4);

        // Pagination across the filtered list
        Page<City> firstPage = cityRepository.findByStatus(CityStatus.PUBLISHED,
                PageRequest.of(0, 2, NAME_SORT));
        assertThat(firstPage.getContent()).extracting(City::getSlug)
                .containsExactly("beijing", "chengdu");
        assertThat(firstPage.getTotalElements()).isEqualTo(4);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);

        Page<City> secondPage = cityRepository.findByStatus(CityStatus.PUBLISHED,
                PageRequest.of(1, 2, NAME_SORT));
        assertThat(secondPage.getContent()).extracting(City::getSlug)
                .containsExactly("shanghai", "xian");
    }

    @Test
    void shouldTiebreakEqualNamesByIdAscending() {
        City first = buildPublished("same-a", "Same Name", "同名一");
        City second = buildPublished("same-b", "Same Name", "同名二");
        persistAndFlush(first, second);

        Page<City> page = cityRepository.findByStatus(CityStatus.PUBLISHED,
                PageRequest.of(0, 20, NAME_SORT));

        assertThat(page.getContent()).extracting(City::getId)
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void shouldReturnEmptyPageWhenNoPublishedCityExists() {
        Page<City> page = cityRepository.findByStatus(CityStatus.PUBLISHED,
                PageRequest.of(0, 20, NAME_SORT));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void shouldNotFindDraftOrUnknownSlug() {
        City draft = buildPublished("secret-city", "Secret City", "秘密");
        draft.setStatus(CityStatus.DRAFT);
        entityManager.persist(draft);
        entityManager.flush();
        entityManager.clear();

        assertThat(cityRepository.findBySlugAndStatus("secret-city", CityStatus.PUBLISHED))
                .isEmpty();
        assertThat(cityRepository.findBySlugAndStatus("no-such-city", CityStatus.PUBLISHED))
                .isEmpty();
    }

    private City buildPublished(String slug, String name, String nameZh) {
        return City.builder()
                .slug(slug)
                .name(name)
                .nameZh(nameZh)
                .status(CityStatus.PUBLISHED)
                .build();
    }

    private void persistAndFlush(Object... entities) {
        for (Object entity : entities) {
            entityManager.persist(entity);
        }
        entityManager.flush();
    }
}
