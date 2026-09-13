package com.first.app.repository;

import com.first.app.entity.Attraction;
import com.first.app.entity.AttractionCategory;
import com.first.app.entity.AttractionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AttractionRepository extends JpaRepository<Attraction, Long> {

    @Query("SELECT a FROM Attraction a WHERE a.status = :status "
            + "AND (:citySlug IS NULL OR a.citySlug = :citySlug) "
            + "AND (:category IS NULL OR a.category = :category)")
    Page<Attraction> search(@Param("status") AttractionStatus status,
                            @Param("citySlug") String citySlug,
                            @Param("category") AttractionCategory category,
                            Pageable pageable);

    List<Attraction> findByStatusAndIsPopularTrue(AttractionStatus status, Pageable pageable);

    Optional<Attraction> findBySlugAndStatus(String slug, AttractionStatus status);
}
