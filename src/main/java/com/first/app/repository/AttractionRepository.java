package com.first.app.repository;

import com.first.app.entity.Attraction;
import com.first.app.entity.AttractionCategory;
import com.first.app.entity.AttractionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttractionRepository extends JpaRepository<Attraction, Long> {

    @Query("SELECT a FROM Attraction a WHERE a.status = :status "
            + "AND (:citySlug IS NULL OR a.citySlug = :citySlug) "
            + "AND (:category IS NULL OR a.category = :category) "
            + "AND (:q IS NULL OR LOWER(a.name) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '!' "
            + "OR LOWER(a.nameZh) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '!')")
    Page<Attraction> search(@Param("status") AttractionStatus status,
                            @Param("citySlug") String citySlug,
                            @Param("category") AttractionCategory category,
                            @Param("q") String q,
                            Pageable pageable);

    List<Attraction> findByStatusAndIsPopularTrue(AttractionStatus status, Pageable pageable);

    Optional<Attraction> findBySlugAndStatus(String slug, AttractionStatus status);

    long countByCitySlugAndStatus(String citySlug, AttractionStatus status);

    List<Attraction> findByIdInAndStatus(Collection<Long> ids, AttractionStatus status);

    List<Attraction> findByStatus(AttractionStatus status);
}
