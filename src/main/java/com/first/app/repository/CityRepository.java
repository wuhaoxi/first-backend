package com.first.app.repository;

import com.first.app.entity.City;
import com.first.app.entity.CityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CityRepository extends JpaRepository<City, Long> {

    Page<City> findByStatus(CityStatus status, Pageable pageable);

    Optional<City> findBySlugAndStatus(String slug, CityStatus status);
}
