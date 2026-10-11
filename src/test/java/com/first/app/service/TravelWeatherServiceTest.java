package com.first.app.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.type.TypeReference;
import com.first.app.dto.WeatherForecastResponse;
import com.first.app.dto.WeatherHistoryResponse;
import com.first.app.dto.WeatherResponse;
import com.first.app.entity.Attraction;
import com.first.app.entity.AttractionStatus;
import com.first.app.entity.City;
import com.first.app.entity.CityStatus;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.exception.UpstreamUnavailableException;
import com.first.app.integration.OpenMeteoClient;
import com.first.app.integration.TravelProviderException;
import com.first.app.repository.AttractionRepository;
import com.first.app.repository.CityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TravelWeatherServiceTest {

    private static final String BEIJING_KEY_F7_P0 = "travel:weather:39.9042:116.4074:f7:p0";
    private static final String BEIJING_KEY_F1_P7 = "travel:weather:39.9042:116.4074:f1:p7";
    private static final String CHENGDU_KEY_F1_P0 = "travel:weather:30.5728:104.0668:f1:p0";
    private static final Duration WEATHER_TTL = Duration.ofMinutes(60);

    @Mock
    private CityRepository cityRepository;

    @Mock
    private AttractionRepository attractionRepository;

    @Mock
    private OpenMeteoClient openMeteoClient;

    @Mock
    private RankingCacheService cache;

    @InjectMocks
    private TravelWeatherService travelWeatherService;

    // ---- location resolution ------------------------------------------------------------------

    @Test
    void getCurrent_invalidSelectorCombination_throwsInvalidRequest_withoutAnyCollaboration() {
        assertThatThrownBy(() -> travelWeatherService.getCurrent(null, null))
                .isInstanceOf(InvalidRequestException.class)
                .satisfies(ex -> assertThat(((InvalidRequestException) ex).getErrors())
                        .extracting(error -> error.get("field"))
                        .containsExactly("citySlug", "attractionSlug"));
        assertThatThrownBy(() -> travelWeatherService.getCurrent("beijing", "forbidden-city"))
                .isInstanceOf(InvalidRequestException.class)
                .satisfies(ex -> assertThat(((InvalidRequestException) ex).getErrors())
                        .extracting(error -> error.get("field"))
                        .containsExactly("citySlug", "attractionSlug"));

        verifyNoInteractions(cityRepository, attractionRepository, openMeteoClient, cache);
    }

    @Test
    void getCurrent_unknownOrDraftCitySlug_throwsNotFound() {
        when(cityRepository.findBySlugAndStatus("atlantis", CityStatus.PUBLISHED))
                .thenReturn(Optional.empty());
        when(cityRepository.findBySlugAndStatus("secret-city", CityStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> travelWeatherService.getCurrent("atlantis", null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("atlantis");
        assertThatThrownBy(() -> travelWeatherService.getCurrent("secret-city", null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("secret-city");

        verifyNoInteractions(openMeteoClient, cache);
    }

    @Test
    void getCurrent_publishedCityWithoutCoordinates_throwsInvalidRequest_namingTheCity() {
        when(cityRepository.findBySlugAndStatus("beijing", CityStatus.PUBLISHED))
                .thenReturn(Optional.of(city("beijing", "Beijing", null, null)));

        assertThatThrownBy(() -> travelWeatherService.getCurrent("beijing", null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("city beijing has no coordinates configured");

        verifyNoInteractions(openMeteoClient, cache);
    }

    @Test
    void getCurrent_attractionWithoutCoordinates_throwsInvalidRequest_namingTheAttraction() {
        when(attractionRepository.findBySlugAndStatus("coords-missing", AttractionStatus.PUBLISHED))
                .thenReturn(Optional.of(attraction("coords-missing", "Coords Missing", null, null)));

        assertThatThrownBy(() -> travelWeatherService.getCurrent(null, "coords-missing"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("attraction coords-missing has no coordinates configured");

        verifyNoInteractions(openMeteoClient, cache);
    }

    @Test
    void getCurrent_attractionSelector_usesStoredAttractionCoordinates() {
        when(attractionRepository.findBySlugAndStatus("forbidden-city", AttractionStatus.PUBLISHED))
                .thenReturn(Optional.of(attraction("forbidden-city", "Forbidden City", 39.9163, 116.3972)));
        when(openMeteoClient.fetchWeather(39.9163, 116.3972, 1, 0)).thenReturn(samplePayload());
        cacheMissRunsLoader();

        WeatherResponse response = travelWeatherService.getCurrent(null, "forbidden-city");

        verify(openMeteoClient).fetchWeather(39.9163, 116.3972, 1, 0);
        assertThat(response.attractionSlug()).isEqualTo("forbidden-city");
        assertThat(response.citySlug()).isNull();
        assertThat(response.locationName()).isEqualTo("Forbidden City");
        assertThat(response.latitude()).isEqualTo(39.9163);
        assertThat(response.longitude()).isEqualTo(116.3972);
        verifyNoInteractions(cityRepository);
    }

    // ---- cache-aside keys, TTL, and surface neutrality -----------------------------------------

    @Test
    void getCurrent_cacheHit_skipsTheProvider_andOverlaysLocationAfterTheRead() {
        when(cityRepository.findBySlugAndStatus("chengdu", CityStatus.PUBLISHED))
                .thenReturn(Optional.of(city("chengdu", "Chengdu", 30.5728, 104.0668)));
        WeatherForecastResponse cached =
                WeatherForecastResponse.from(samplePayload(), 30.5728, 104.0668, 1);
        when(cache.getOrLoad(eq(CHENGDU_KEY_F1_P0), any(Duration.class),
                ArgumentMatchers.<TypeReference<WeatherForecastResponse>>any(),
                ArgumentMatchers.<Supplier<WeatherForecastResponse>>any()))
                .thenReturn(cached);

        WeatherResponse response = travelWeatherService.getCurrent("chengdu", null);

        assertThat(response.locationName()).isEqualTo("Chengdu");
        assertThat(response.citySlug()).isEqualTo("chengdu");
        assertThat(response.attractionSlug()).isNull();
        assertThat(response.current().temperatureC()).isEqualTo(21.4);
        verify(openMeteoClient, org.mockito.Mockito.never())
                .fetchWeather(anyDouble(), anyDouble(), anyInt(), anyInt());
    }

    @Test
    void getCurrent_usesF1P0Window_withExactKeyAndSixtyMinuteTtl() {
        when(cityRepository.findBySlugAndStatus("chengdu", CityStatus.PUBLISHED))
                .thenReturn(Optional.of(city("chengdu", "Chengdu", 30.5728, 104.0668)));
        when(openMeteoClient.fetchWeather(30.5728, 104.0668, 1, 0)).thenReturn(samplePayload());
        cacheMissRunsLoader();

        travelWeatherService.getCurrent("chengdu", null);

        verify(openMeteoClient).fetchWeather(30.5728, 104.0668, 1, 0);
        verify(cache).getOrLoad(eq(CHENGDU_KEY_F1_P0), eq(WEATHER_TTL),
                ArgumentMatchers.<TypeReference<WeatherForecastResponse>>any(),
                ArgumentMatchers.<Supplier<WeatherForecastResponse>>any());
    }

    @Test
    void getForecast_defaultsToSevenDays_usesF7P0Window_andOverlaysLocation() {
        when(cityRepository.findBySlugAndStatus("beijing", CityStatus.PUBLISHED))
                .thenReturn(Optional.of(city("beijing", "Beijing", 39.9042, 116.4074)));
        when(openMeteoClient.fetchWeather(39.9042, 116.4074, 7, 0)).thenReturn(samplePayload());
        cacheMissRunsLoader();

        WeatherForecastResponse response = travelWeatherService.getForecast("beijing", null, null);

        verify(openMeteoClient).fetchWeather(39.9042, 116.4074, 7, 0);
        verify(cache).getOrLoad(eq(BEIJING_KEY_F7_P0), eq(WEATHER_TTL),
                ArgumentMatchers.<TypeReference<WeatherForecastResponse>>any(),
                ArgumentMatchers.<Supplier<WeatherForecastResponse>>any());
        assertThat(response.locationName()).isEqualTo("Beijing");
        assertThat(response.citySlug()).isEqualTo("beijing");
        assertThat(response.days()).isEqualTo(7);
        assertThat(response.daily()).hasSize(3);
    }

    @Test
    void getHistory_defaultsToSevenDays_usesF1P7Window() {
        when(cityRepository.findBySlugAndStatus("beijing", CityStatus.PUBLISHED))
                .thenReturn(Optional.of(city("beijing", "Beijing", 39.9042, 116.4074)));
        when(openMeteoClient.fetchWeather(39.9042, 116.4074, 1, 7)).thenReturn(samplePayload());
        cacheMissRunsLoader();

        WeatherHistoryResponse response = travelWeatherService.getHistory("beijing", null, null);

        verify(openMeteoClient).fetchWeather(39.9042, 116.4074, 1, 7);
        verify(cache).getOrLoad(eq(BEIJING_KEY_F1_P7), eq(WEATHER_TTL),
                ArgumentMatchers.<TypeReference<WeatherForecastResponse>>any(),
                ArgumentMatchers.<Supplier<WeatherForecastResponse>>any());
        assertThat(response.days()).isEqualTo(7);
        assertThat(response.daily()).hasSize(3);
        assertThat(response.daily().get(0).date()).isEqualTo("2026-10-04");
        assertThat(response.locationName()).isEqualTo("Beijing");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getForecast_cacheMiss_buildsSurfaceNeutralPayload_forStorage() {
        when(cityRepository.findBySlugAndStatus("beijing", CityStatus.PUBLISHED))
                .thenReturn(Optional.of(city("beijing", "Beijing", 39.9042, 116.4074)));
        when(openMeteoClient.fetchWeather(39.9042, 116.4074, 7, 0)).thenReturn(samplePayload());
        cacheMissRunsLoader();

        travelWeatherService.getForecast("beijing", null, 7);

        ArgumentCaptor<Supplier<WeatherForecastResponse>> loaderCaptor =
                ArgumentCaptor.forClass((Class) Supplier.class);
        verify(cache).getOrLoad(eq(BEIJING_KEY_F7_P0), eq(WEATHER_TTL),
                ArgumentMatchers.<TypeReference<WeatherForecastResponse>>any(), loaderCaptor.capture());
        // The value handed to the cache helper is what a real cache would store: no slug or name echo.
        WeatherForecastResponse stored = loaderCaptor.getValue().get();
        assertThat(stored.citySlug()).isNull();
        assertThat(stored.attractionSlug()).isNull();
        assertThat(stored.locationName()).isNull();
        assertThat(stored.latitude()).isEqualTo(39.9042);
        assertThat(stored.timezone()).isEqualTo("Asia/Shanghai");
        assertThat(stored.attribution()).isEqualTo("Weather data by Open-Meteo (CC BY 4.0)");
    }

    @Test
    void getWeather_mcpWindow_sharesTheExactRestCacheKey_entryStaysSurfaceNeutral() {
        when(openMeteoClient.fetchWeather(39.9042, 116.4074, 7, 0)).thenReturn(samplePayload());
        cacheMissRunsLoader();

        WeatherForecastResponse payload = travelWeatherService.getWeather(39.9042, 116.4074, 7, 0);

        // Same entry the REST forecast for Beijing's f7 p0 window reads and writes.
        verify(cache).getOrLoad(eq(BEIJING_KEY_F7_P0), eq(WEATHER_TTL),
                ArgumentMatchers.<TypeReference<WeatherForecastResponse>>any(),
                ArgumentMatchers.<Supplier<WeatherForecastResponse>>any());
        assertThat(payload.citySlug()).isNull();
        assertThat(payload.locationName()).isNull();
        assertThat(payload.timezone()).isEqualTo("Asia/Shanghai");
        assertThat(payload.daily()).hasSize(3);
    }

    // ---- provider failure translation and cache degradation -------------------------------------

    @Test
    void getCurrent_providerAvailabilityFailure_throwsUpstreamUnavailable() {
        when(cityRepository.findBySlugAndStatus("chengdu", CityStatus.PUBLISHED))
                .thenReturn(Optional.of(city("chengdu", "Chengdu", 30.5728, 104.0668)));
        when(openMeteoClient.fetchWeather(30.5728, 104.0668, 1, 0)).thenThrow(
                new TravelProviderException(TravelProviderException.Kind.UPSTREAM_UNAVAILABLE,
                        "Open-Meteo request failed with status 500"));
        cacheMissRunsLoader();

        assertThatThrownBy(() -> travelWeatherService.getCurrent("chengdu", null))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining("500");
    }

    @Test
    void getCurrent_providerInputRejection_throwsInvalidRequest() {
        when(cityRepository.findBySlugAndStatus("chengdu", CityStatus.PUBLISHED))
                .thenReturn(Optional.of(city("chengdu", "Chengdu", 30.5728, 104.0668)));
        when(openMeteoClient.fetchWeather(30.5728, 104.0668, 1, 0)).thenThrow(
                new TravelProviderException(TravelProviderException.Kind.INPUT_REJECTION,
                        "Latitude must be in range of -90 to 90"));
        cacheMissRunsLoader();

        assertThatThrownBy(() -> travelWeatherService.getCurrent("chengdu", null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Latitude");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getCurrent_redisFailure_degradesToTheProvider_withASingleWarn_fromTheCacheLayer() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CHENGDU_KEY_F1_P0)).thenThrow(new RuntimeException("redis down"));
        RankingCacheService realCache =
                new RankingCacheService(redisTemplate, Jackson2ObjectMapperBuilder.json().build());
        TravelWeatherService service = new TravelWeatherService(
                cityRepository, attractionRepository, openMeteoClient, realCache);

        when(cityRepository.findBySlugAndStatus("chengdu", CityStatus.PUBLISHED))
                .thenReturn(Optional.of(city("chengdu", "Chengdu", 30.5728, 104.0668)));
        when(openMeteoClient.fetchWeather(30.5728, 104.0668, 1, 0)).thenReturn(samplePayload());

        Logger cacheLogger = (Logger) LoggerFactory.getLogger(RankingCacheService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        cacheLogger.addAppender(appender);
        WeatherResponse response;
        try {
            response = service.getCurrent("chengdu", null);
        } finally {
            cacheLogger.detachAppender(appender);
        }

        assertThat(response.locationName()).isEqualTo("Chengdu");
        assertThat(response.current().temperatureC()).isEqualTo(21.4);
        verify(openMeteoClient).fetchWeather(30.5728, 104.0668, 1, 0);
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
    }

    // ---- fixtures -------------------------------------------------------------------------------

    private void cacheMissRunsLoader() {
        when(cache.getOrLoad(anyString(), any(Duration.class),
                ArgumentMatchers.<TypeReference<WeatherForecastResponse>>any(),
                ArgumentMatchers.<Supplier<WeatherForecastResponse>>any()))
                .thenAnswer(invocation ->
                        ((Supplier<WeatherForecastResponse>) invocation.getArgument(3)).get());
    }

    private static City city(String slug, String name, Double latitude, Double longitude) {
        return City.builder()
                .slug(slug)
                .name(name)
                .nameZh(name + "·zh")
                .latitude(latitude)
                .longitude(longitude)
                .status(CityStatus.PUBLISHED)
                .build();
    }

    private static Attraction attraction(String slug, String name, Double latitude, Double longitude) {
        return Attraction.builder()
                .slug(slug)
                .name(name)
                .nameZh(name + "·zh")
                .latitude(latitude)
                .longitude(longitude)
                .status(AttractionStatus.PUBLISHED)
                .build();
    }

    private static OpenMeteoClient.WeatherPayload samplePayload() {
        return new OpenMeteoClient.WeatherPayload("Asia/Shanghai",
                new OpenMeteoClient.CurrentSnapshot(
                        "2026-10-05T10:00", 21.4, 20.1, 65.0, 0.0, 8.5, 180.0, 2),
                new OpenMeteoClient.DailySeries(
                        List.of("2026-10-04", "2026-10-05", "2026-10-06"),
                        List.of(22.0, 21.5, 20.8),
                        List.of(12.0, 11.4, 10.9),
                        List.of(0.0, 0.6, 1.2),
                        List.of(10.0, 55.0, 70.0),
                        List.of(14.0, 16.5, 12.0),
                        List.of(1, 61, 63)));
    }
}
