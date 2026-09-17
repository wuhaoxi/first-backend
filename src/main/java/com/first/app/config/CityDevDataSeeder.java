package com.first.app.config;

import com.first.app.entity.City;
import com.first.app.entity.CityStatus;
import com.first.app.repository.CityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Seeds the city catalog for local development (dev profile only).
 *
 * <p>Idempotent: runs only when the cities table is empty. To force a re-seed,
 * clear the table first ({@code DELETE FROM cities;}) and restart the app.
 *
 * <p>The slug set MUST stay in sync with {@code AttractionDevDataSeeder} — the soft
 * attribution contract matches {@code City.slug} to {@code Attraction.citySlug}.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class CityDevDataSeeder implements CommandLineRunner {

    private final CityRepository cityRepository;

    @Override
    public void run(String... args) {
        if (cityRepository.count() > 0) {
            return;
        }
        cityRepository.saveAll(catalog());
    }

    private List<City> catalog() {
        List<City> cities = new ArrayList<>();

        cities.add(city("beijing", "Beijing", "北京",
                "China's imperial capital, home to the Forbidden City, the Great Wall and centuries of "
                        + "dynastic history. Grand monuments, hutong alleys and a fast, modern city around them.",
                "September–October & April–May"));
        cities.add(city("xian", "Xi'an", "西安",
                "The eastern end of the Silk Road and home of the Terracotta Army. A walled old town packed "
                        + "with street food, ancient pagodas and some of China's most important archaeological sites.",
                "April–May & September–October"));
        cities.add(city("shanghai", "Shanghai", "上海",
                "China's largest city and its most cosmopolitan: a futuristic skyline across the Huangpu River "
                        + "faces the colonial-era Bund, with world-class museums, shopping and nightlife in between.",
                "March–May & September–November"));
        cities.add(city("chengdu", "Chengdu", "成都",
                "The laid-back capital of Sichuan, famous for pandas, teahouses and spicy cuisine. A city where "
                        + "life moves slowly — perfect for a relaxed few days between bigger sights.",
                "March–June & September–November"));
        cities.add(city("hangzhou", "Hangzhou", "杭州",
                "A poetic lakeside city celebrated for West Lake, whose causeways, pagodas and tea hills have "
                        + "inspired artists for a thousand years. Ideal for slow walks and boat rides.",
                "March–May & September–November"));
        cities.add(city("guilin", "Guilin", "桂林",
                "Gateway to China's most surreal karst scenery. Cruise the Li River past towering limestone "
                        + "peaks, explore rice terraces and cycle through Yangshuo's countryside.",
                "April–October"));

        return cities;
    }

    private City city(String slug, String name, String nameZh, String description, String bestSeason) {
        return City.builder()
                .slug(slug)
                .name(name)
                .nameZh(nameZh)
                .description(description)
                .bestSeason(bestSeason)
                .status(CityStatus.PUBLISHED)
                .build();
    }
}
