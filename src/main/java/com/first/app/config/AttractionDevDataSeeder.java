package com.first.app.config;

import com.first.app.entity.Attraction;
import com.first.app.entity.AttractionCategory;
import com.first.app.entity.AttractionStatus;
import com.first.app.repository.AttractionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Seeds the attractions catalog for local development (dev profile only).
 *
 * <p>Idempotent: runs only when the attractions table is empty. To force a re-seed,
 * clear the table first ({@code DELETE FROM attractions;}) and restart the app.
 *
 * <p>Insertion order matters: ids follow this order and the popular feed picks the top 6
 * by id descending, so the six homepage showcases MUST stay the final catalog entries.
 *
 * <p>Curated rankings ({@link #RANKINGS}) and imagery ({@link #IMAGES}) are keyed by slug and
 * applied to every entry before {@code saveAll}; the six showcases own the six highest heat scores.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class AttractionDevDataSeeder implements CommandLineRunner {

    private final AttractionRepository attractionRepository;

    private static final Map<String, Ranking> RANKINGS = Map.ofEntries(
            // --- City catalog ---
            Map.entry("temple-of-heaven", new Ranking(4.6, 900, 88)),
            Map.entry("summer-palace", new Ranking(4.7, 1100, 87)),
            Map.entry("city-wall-xian", new Ranking(4.7, 1200, 86)),
            Map.entry("big-wild-goose-pagoda", new Ranking(4.5, 640, 78)),
            Map.entry("muslim-quarter", new Ranking(4.4, 950, 84)),
            Map.entry("yu-garden", new Ranking(4.5, 880, 83)),
            Map.entry("shanghai-museum", new Ranking(4.8, 1050, 85)),
            Map.entry("tianzifang", new Ranking(4.3, 520, 74)),
            Map.entry("wuhou-shrine", new Ranking(4.5, 610, 77)),
            Map.entry("jinli-ancient-street", new Ranking(4.4, 730, 80)),
            Map.entry("dujiangyan-irrigation", new Ranking(4.7, 590, 76)),
            Map.entry("lingyin-temple", new Ranking(4.6, 810, 82)),
            Map.entry("leifeng-pagoda", new Ranking(4.5, 700, 79)),
            Map.entry("longjing-tea-village", new Ranking(4.6, 560, 75)),
            Map.entry("li-river", new Ranking(4.9, 2400, 89)),
            Map.entry("reed-flute-cave", new Ranking(4.4, 430, 71)),
            Map.entry("elephant-trunk-hill", new Ranking(4.3, 380, 69)),
            Map.entry("longji-rice-terraces", new Ranking(4.8, 890, 81)),
            // --- Homepage showcases: MUST own the six highest heat scores ---
            Map.entry("mutianyu-great-wall", new Ranking(4.9, 4200, 99)),
            Map.entry("forbidden-city", new Ranking(4.9, 5100, 98)),
            Map.entry("terracotta-army", new Ranking(4.8, 3900, 97)),
            Map.entry("the-bund", new Ranking(4.6, 2800, 96)),
            Map.entry("chengdu-panda-base", new Ranking(4.8, 3600, 95)),
            Map.entry("west-lake", new Ranking(4.7, 3100, 94)));

    private record Ranking(double ratingScore, int favoriteCount, int heatScore) {
    }

    /**
     * Curated gallery images (slug → 3-4 stable Wikimedia Commons 960px thumbnails of the actual
     * attraction; 960px is an allowed thumbnail bucket). The first entry of each gallery doubles as
     * {@code coverImageUrl}.
     */
    private static final Map<String, List<String>> IMAGES = Map.ofEntries(
            // --- City catalog ---
            Map.entry("temple-of-heaven", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/0/0a/Temple_of_Heaven%2C_Beijing%2C_China_-_010_edit.jpg/960px-Temple_of_Heaven%2C_Beijing%2C_China_-_010_edit.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9a/Temple_of_Heaven%2C_Beijing_-_February_2024.jpg/960px-Temple_of_Heaven%2C_Beijing_-_February_2024.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/0/07/Temple_of_Heaven_-_Beijing_-_June_2012.jpg/960px-Temple_of_Heaven_-_Beijing_-_June_2012.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/0/0d/Beijing%2C_Tiantan%2C_Imperial_Vault_of_Heaven_WLF_2023.jpg/960px-Beijing%2C_Tiantan%2C_Imperial_Vault_of_Heaven_WLF_2023.jpg")),
            Map.entry("summer-palace", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/d/db/Longevity_Hill_of_the_Summer_Palace.jpg/960px-Longevity_Hill_of_the_Summer_Palace.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fb/20090530_Beijing_Summer_Palace_8467.jpg/960px-20090530_Beijing_Summer_Palace_8467.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/e/eb/Summer_Palace_-_Wenchang_Pavilion.jpg/960px-Summer_Palace_-_Wenchang_Pavilion.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/9/97/The_Summer_Palace_%E9%A0%A4%E5%92%8C%E5%9C%92%2C_Beijing%2C_China_%2838171942106%29.jpg/960px-The_Summer_Palace_%E9%A0%A4%E5%92%8C%E5%9C%92%2C_Beijing%2C_China_%2838171942106%29.jpg")),
            Map.entry("city-wall-xian", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d7/Xi%27an_city_walls_%2889479%29.jpg/960px-Xi%27an_city_walls_%2889479%29.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/3/32/City_wall_of_Xi%27an_51550-Xian_%2827959363326%29.jpg/960px-City_wall_of_Xi%27an_51550-Xian_%2827959363326%29.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/9/92/Xi%27an_city_walls.jpg/960px-Xi%27an_city_walls.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b7/Xi%27an_city_walls_%2859626%29.jpg/960px-Xi%27an_city_walls_%2859626%29.jpg")),
            Map.entry("big-wild-goose-pagoda", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/9/91/Giant_Wild_Goose_Pagoda%2C_Xi%27an%2C_May%2C_2018-1.jpg/960px-Giant_Wild_Goose_Pagoda%2C_Xi%27an%2C_May%2C_2018-1.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/1/13/Giant_Wild_Goose_Pagoda.jpg/960px-Giant_Wild_Goose_Pagoda.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c2/Giant_Wild_Goose_Pagoda_20240806_02.jpg/960px-Giant_Wild_Goose_Pagoda_20240806_02.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e4/Giant_Wild_Goose_Pagoda_20240806_04.jpg/960px-Giant_Wild_Goose_Pagoda_20240806_04.jpg")),
            Map.entry("muslim-quarter", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/7/76/Xi%27an_Muslim_Quarter.jpg/960px-Xi%27an_Muslim_Quarter.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c4/Muslim_Quarter_Xi%27an_China.jpg/960px-Muslim_Quarter_Xi%27an_China.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/Muslim_Quarter_in_Xi%27an_%2848785759581%29.jpg/960px-Muslim_Quarter_in_Xi%27an_%2848785759581%29.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/0/09/1_xian_muslim_quarter_china_2011.JPG/960px-1_xian_muslim_quarter_china_2011.JPG")),
            Map.entry("yu-garden", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/4/45/Shanghai_-_Yu_Garden_-_0035.jpg/960px-Shanghai_-_Yu_Garden_-_0035.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/7/72/Shanghai_-_Yu_Garden_-_0034.jpg/960px-Shanghai_-_Yu_Garden_-_0034.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4b/Yu_Garden_Shanghai_November_2017_003.jpg/960px-Yu_Garden_Shanghai_November_2017_003.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/1/14/Yu_Garden_Shanghai_November_2017_002.jpg/960px-Yu_Garden_Shanghai_November_2017_002.jpg")),
            Map.entry("shanghai-museum", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2d/The_shanghai_museum.jpg/960px-The_shanghai_museum.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d8/Shanghai_East_Museum_-_54128313357.jpg/960px-Shanghai_East_Museum_-_54128313357.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/7/74/Shanghai_East_Museum_-_54133541419.jpg/960px-Shanghai_East_Museum_-_54133541419.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c2/Shanghai_East_Museum_-_54129151186.jpg/960px-Shanghai_East_Museum_-_54129151186.jpg")),
            Map.entry("tianzifang", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/7/73/Tianzifang%2C_Shanghai.jpg/960px-Tianzifang%2C_Shanghai.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a6/Tianzifangbyday.jpg/960px-Tianzifangbyday.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a8/Tianzifang_21669-Shanghai_%2833070816285%29.jpg/960px-Tianzifang_21669-Shanghai_%2833070816285%29.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2a/Tianzifang_21641-Shanghai_%2833029166756%29.jpg/960px-Tianzifang_21641-Shanghai_%2833029166756%29.jpg")),
            Map.entry("wuhou-shrine", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ef/Wuhou_Shrine_20260513.jpg/960px-Wuhou_Shrine_20260513.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/5/59/Wuhou_Shrine_-_Chengdu%2C_China_-_DSC05476.jpg/960px-Wuhou_Shrine_-_Chengdu%2C_China_-_DSC05476.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9c/Penjing_garden_-_Wuhou_Shrine_-_Chengdu%2C_China_-_DSC05432.jpg/960px-Penjing_garden_-_Wuhou_Shrine_-_Chengdu%2C_China_-_DSC05432.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1d/Pavilion_-_Wuhou_Shrine_-_Chengdu%2C_China_-_DSC05444.jpg/960px-Pavilion_-_Wuhou_Shrine_-_Chengdu%2C_China_-_DSC05444.jpg")),
            Map.entry("jinli-ancient-street", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/5/51/Chengdu_Jinli-Stra%C3%9Fe_11.jpg/960px-Chengdu_Jinli-Stra%C3%9Fe_11.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ad/Chengdu_Jinli-Stra%C3%9Fe_09.jpg/960px-Chengdu_Jinli-Stra%C3%9Fe_09.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fa/Jinli_Street_-_Chengdu%2C_China_-_DSC05404.jpg/960px-Jinli_Street_-_Chengdu%2C_China_-_DSC05404.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/0/04/Chengdu_Jinli-Stra%C3%9Fe_bei_Nacht_19.jpg/960px-Chengdu_Jinli-Stra%C3%9Fe_bei_Nacht_19.jpg")),
            Map.entry("dujiangyan-irrigation", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a7/Dujiang_Weir.jpg/960px-Dujiang_Weir.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ec/Dujiangyan_Irrigation_System_%2850620354352%29.jpg/960px-Dujiangyan_Irrigation_System_%2850620354352%29.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ea/Dujiangyan_Irrigation_System_%2850619502518%29.jpg/960px-Dujiangyan_Irrigation_System_%2850619502518%29.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/f/ff/Dujiangyan_Irrigation_System_%2850619502968%29.jpg/960px-Dujiangyan_Irrigation_System_%2850619502968%29.jpg")),
            Map.entry("lingyin-temple", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/6/66/Lingyin_Temple%2C_Hangzhou_20161003.jpg/960px-Lingyin_Temple%2C_Hangzhou_20161003.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/6/64/Lingyin_Temple_in_Hangzhou.jpg/960px-Lingyin_Temple_in_Hangzhou.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c2/Hangzhou_Lingyin-Temple_20161003.jpg/960px-Hangzhou_Lingyin-Temple_20161003.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/6/65/20260423_Stone_Pagodas_of_Lingyin_Temple_01.jpg/960px-20260423_Stone_Pagodas_of_Lingyin_Temple_01.jpg")),
            Map.entry("leifeng-pagoda", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c9/Leifeng_Pagoda_%E9%9B%B7%E5%B3%B0%E5%A1%94_-_panoramio.jpg/960px-Leifeng_Pagoda_%E9%9B%B7%E5%B3%B0%E5%A1%94_-_panoramio.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e5/Leifeng_Pagoda_20191102.jpg/960px-Leifeng_Pagoda_20191102.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c4/Leifeng_Pagoda_20240729_103559.jpg/960px-Leifeng_Pagoda_20240729_103559.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/5/50/Leifeng_Pagoda_at_Dusk.jpg/960px-Leifeng_Pagoda_at_Dusk.jpg")),
            Map.entry("longjing-tea-village", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fd/Longjing_villiage.jpg/960px-Longjing_villiage.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6f/Longjing_Tea_field%2C_Dragon_Well_area%2C_Meijiawu_China.jpg/960px-Longjing_Tea_field%2C_Dragon_Well_area%2C_Meijiawu_China.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/d/db/Tea_field_%26_tea_farm_houses_in_Hangzhou.JPG/960px-Tea_field_%26_tea_farm_houses_in_Hangzhou.JPG",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1e/Tea-grower-hangzhou.jpg/960px-Tea-grower-hangzhou.jpg")),
            Map.entry("li-river", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/9/92/1_li_jiang_guilin_yangshuo_2011.jpg/960px-1_li_jiang_guilin_yangshuo_2011.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8c/Li_River_cruise_from_Guilin_to_Yangshuo.JPG/960px-Li_River_cruise_from_Guilin_to_Yangshuo.JPG",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c6/Guilin_li_river.jpg/960px-Guilin_li_river.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7b/Yangshuo-Li-River-2019-Luka-Peternel.jpg/960px-Yangshuo-Li-River-2019-Luka-Peternel.jpg")),
            Map.entry("reed-flute-cave", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fa/Reed_Flute_Cave_89145-Guilin_%2830047619307%29.jpg/960px-Reed_Flute_Cave_89145-Guilin_%2830047619307%29.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4a/Reed_Flute_Cave_89009-Guilin_%2843171728970%29.jpg/960px-Reed_Flute_Cave_89009-Guilin_%2843171728970%29.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/3/39/Reed_Flute_Cave_89017-Guilin_%2844934927312%29.jpg/960px-Reed_Flute_Cave_89017-Guilin_%2844934927312%29.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b9/Reed_Flute_Cave_89020-Guilin_%2843171737050%29.jpg/960px-Reed_Flute_Cave_89020-Guilin_%2843171737050%29.jpg")),
            Map.entry("elephant-trunk-hill", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/9/98/Elephant_Trunk_Hill%2C_Guilin.jpg/960px-Elephant_Trunk_Hill%2C_Guilin.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2f/Guilin_Elephant_Hill_at_night_%2820240217201207%29.jpg/960px-Guilin_Elephant_Hill_at_night_%2820240217201207%29.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/3/33/View_of_Guilin_from_Elephant_Trunk_Hill_%28cropped%29.jpg/960px-View_of_Guilin_from_Elephant_Trunk_Hill_%28cropped%29.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fb/View_of_Guilin_from_Elephant_Trunk_Hill.jpg/960px-View_of_Guilin_from_Elephant_Trunk_Hill.jpg")),
            Map.entry("longji-rice-terraces", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5e/Longji_Rice_Terraces_004.jpg/960px-Longji_Rice_Terraces_004.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4a/Longji_Rice_Terraces_002.jpg/960px-Longji_Rice_Terraces_002.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/0/0c/Longji_Rice_Terraces_005.jpg/960px-Longji_Rice_Terraces_005.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/3/33/Longji_rice_terraces_-_2023_10_11_Kaur_Virunurm.jpg/960px-Longji_rice_terraces_-_2023_10_11_Kaur_Virunurm.jpg")),
            // --- Homepage showcases: MUST own the six highest heat scores ---
            Map.entry("mutianyu-great-wall", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/6/64/The_Mutianyu_section_of_the_Great_Wall_of_China.jpg/960px-The_Mutianyu_section_of_the_Great_Wall_of_China.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/e/eb/Mutianyu_Great_Wall_%286222519140%29.jpg/960px-Mutianyu_Great_Wall_%286222519140%29.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e9/Great_wall_of_china-mutianyu_4.JPG/960px-Great_wall_of_china-mutianyu_4.JPG",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ad/66954-The-Great-Wall%2C_Mutianyu.jpg/960px-66954-The-Great-Wall%2C_Mutianyu.jpg")),
            Map.entry("forbidden-city", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ef/The_Forbidden_City_-_View_from_Coal_Hill.jpg/960px-The_Forbidden_City_-_View_from_Coal_Hill.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c8/Beijing_Forbidden_City_Hall_of_Central_Harmony_terraces-20071018-RM-143736.jpg/960px-Beijing_Forbidden_City_Hall_of_Central_Harmony_terraces-20071018-RM-143736.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/d/da/Beijing_China_Forbidden-City-03.jpg/960px-Beijing_China_Forbidden-City-03.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8f/Beijing_China_Forbidden-City-04.jpg/960px-Beijing_China_Forbidden-City-04.jpg")),
            Map.entry("terracotta-army", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/8/88/51714-Terracota-Army.jpg/960px-51714-Terracota-Army.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/4/43/Qin_Shihuang_Terracotta_Army%2C_Pit_1.jpg/960px-Qin_Shihuang_Terracotta_Army%2C_Pit_1.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/4/49/Terracotta_Army%2C_View_of_Pit_1.jpg/960px-Terracotta_Army%2C_View_of_Pit_1.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/4/41/Terracotta_army.jpg/960px-Terracotta_army.jpg")),
            Map.entry("the-bund", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/8/86/Blue_hour_view_of_the_Bund_from_the_Shanghai_World_Financial_Center_dllu.jpg/960px-Blue_hour_view_of_the_Bund_from_the_Shanghai_World_Financial_Center_dllu.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/d/df/Pudong_Shanghai_November_2017_panorama.jpg/960px-Pudong_Shanghai_November_2017_panorama.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d4/The_Bund%2C_Shanghai_at_night.jpg/960px-The_Bund%2C_Shanghai_at_night.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f5/Sunset_at_the_Bund%2C_Shanghai_2019.jpg/960px-Sunset_at_the_Bund%2C_Shanghai_2019.jpg")),
            Map.entry("chengdu-panda-base", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/5/54/Chengdu-pandas-d10.jpg/960px-Chengdu-pandas-d10.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e4/Chengdu_Sichuan_China_Panda-breeding-and-research-center-01.jpg/960px-Chengdu_Sichuan_China_Panda-breeding-and-research-center-01.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c1/Chengdu_Research_Base_of_Giant_Panda_Breeding%2C_201907%2C_01.jpg/960px-Chengdu_Research_Base_of_Giant_Panda_Breeding%2C_201907%2C_01.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fe/Panda_in_Chengdu_Research_Base_of_Giant_Panda_Breeding_-_7708872342.jpg/960px-Panda_in_Chengdu_Research_Base_of_Giant_Panda_Breeding_-_7708872342.jpg")),
            Map.entry("west-lake", List.of(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ed/20260424_West_Lake_and_Hangzhou_Skyline.jpg/960px-20260424_West_Lake_and_Hangzhou_Skyline.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/2/22/West_Lake%2C_Hangzhou_%28Wanzi_Pavilion%29.jpg/960px-West_Lake%2C_Hangzhou_%28Wanzi_Pavilion%29.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d8/West_Lake%2C_Hangzhou_%28Nine-turn_bridge%29.jpg/960px-West_Lake%2C_Hangzhou_%28Nine-turn_bridge%29.jpg",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/0/07/20090524_Hangzhou_West_Lake_7531.jpg/960px-20090524_Hangzhou_West_Lake_7531.jpg")));

    @Override
    public void run(String... args) {
        if (attractionRepository.count() > 0) {
            return;
        }
        List<Attraction> attractions = catalog();
        applyRankings(attractions);
        applyImages(attractions);
        attractionRepository.saveAll(attractions);
    }

    private void applyRankings(List<Attraction> attractions) {
        for (Attraction attraction : attractions) {
            Ranking ranking = RANKINGS.get(attraction.getSlug());
            if (ranking == null) {
                throw new IllegalStateException("Missing curated ranking for slug: " + attraction.getSlug());
            }
            attraction.setRatingScore(ranking.ratingScore());
            attraction.setFavoriteCount(ranking.favoriteCount());
            attraction.setHeatScore(ranking.heatScore());
        }
    }

    private void applyImages(List<Attraction> attractions) {
        for (Attraction attraction : attractions) {
            List<String> images = IMAGES.get(attraction.getSlug());
            if (images == null) {
                throw new IllegalStateException("Missing curated imagery for slug: " + attraction.getSlug());
            }
            attraction.setGallery(images);
            attraction.setCoverImageUrl(images.get(0));
        }
    }

    private List<Attraction> catalog() {
        List<Attraction> attractions = new ArrayList<>();

        // --- Beijing ---
        attractions.add(attraction("temple-of-heaven", "Temple of Heaven", "天坛", AttractionCategory.HISTORICAL_SITE,
                List.of("unesco", "park", "local-life"), "Beijing", "beijing", "Beijing",
                "Ming-dynasty altar where emperors prayed for good harvests.",
                "A vast park centered on the triple-tiered Hall of Prayer for Good Harvests. Come early to watch "
                        + "retirees practice tai chi, play chess and sing opera among ancient cypress trees.",
                "06:00-22:00 (park); 08:00-17:30 (sights)", "¥34 combined ticket", false, null, "2-3 hours",
                39.8822, 116.4066, false));
        attractions.add(attraction("summer-palace", "Summer Palace", "颐和园", AttractionCategory.NATURE,
                List.of("unesco", "garden", "lake"), "Beijing", "beijing", "Beijing",
                "Imperial garden of lakes, temples and painted corridors.",
                "The Qing court's summer retreat, built around Kunming Lake. Walk the Long Corridor, climb Longevity "
                        + "Hill for panoramic views, or rent a boat on the lake.",
                "06:30-18:00 (park); 08:30-17:00 (sights)", "¥30 (park); ¥60 combined", false, null, "Half day",
                39.9999, 116.2755, true));

        // --- Xi'an ---
        attractions.add(attraction("city-wall-xian", "Xi'an City Wall", "西安城墙", AttractionCategory.HISTORICAL_SITE,
                List.of("cycling", "sunset", "history"), "Xi'an", "xian", "Shaanxi",
                "Best-preserved ancient city wall in China, 14 km around the old town.",
                "Walk or rent a bicycle on the wide ramparts of the Ming-era wall. Late afternoon is best: golden "
                        + "light over the rooftops, then lanterns at dusk.",
                "08:00-22:00", "¥54", false, null, "2-3 hours",
                34.2570, 108.9530, false));
        attractions.add(attraction("big-wild-goose-pagoda", "Big Wild Goose Pagoda", "大雁塔", AttractionCategory.TEMPLE,
                List.of("buddhism", "landmark"), "Xi'an", "xian", "Shaanxi",
                "7th-century pagoda built to house sutras brought from India.",
                "The pagoda anchors a lively square with musical fountains in the evening. Climb the seven levels "
                        + "for a view over Xi'an and the surrounding temple complex.",
                "08:30-17:00", "¥40 (temple); ¥30 (pagoda climb)", false, null, "1-2 hours",
                34.2186, 108.9643, false));
        attractions.add(attraction("muslim-quarter", "Muslim Quarter", "回民街", AttractionCategory.STREET_DISTRICT,
                List.of("food", "night-market", "street-food"), "Xi'an", "xian", "Shaanxi",
                "Buzzing food street behind the Drum Tower.",
                "Grazing ground for roujiamo (Chinese 'burger'), hand-pulled biangbiang noodles, persimmon cakes and "
                        + "lamb skewers. Go hungry, bring cash or a phone with a payment app set up.",
                "All day (busiest 17:00-22:00)", "Free", false, null, "2-3 hours",
                34.2658, 108.9400, false));

        // --- Shanghai ---
        attractions.add(attraction("yu-garden", "Yu Garden", "豫园", AttractionCategory.HISTORICAL_SITE,
                List.of("garden", "old-town"), "Shanghai", "shanghai", "Shanghai",
                "Classical Ming garden in the heart of the old city.",
                "Rockeries, koi ponds and dragon walls squeezed into a compact classical garden. Pair it with the "
                        + "market streets around it and the City God Temple snacks.",
                "09:00-16:30", "¥40", false, null, "1-2 hours",
                31.2272, 121.4921, false));
        attractions.add(attraction("shanghai-museum", "Shanghai Museum", "上海博物馆", AttractionCategory.MUSEUM,
                List.of("art", "bronzes", "ceramics"), "Shanghai", "shanghai", "Shanghai",
                "World-class collection of Chinese bronze, ceramics and painting.",
                "The People's Square flagship covers five millennia in a manageable few hours. Free entry with "
                        + "advance passport reservation; the ancient bronzes and ceramics galleries are unmissable.",
                "09:00-17:00 (closed Mondays)", "Free (reserve with passport)", true,
                "Free entry - reserve online with passport up to 7 days ahead", "2-3 hours",
                31.2286, 121.4751, false));
        attractions.add(attraction("tianzifang", "Tianzifang", "田子坊", AttractionCategory.STREET_DISTRICT,
                List.of("art", "shopping", "cafes"), "Shanghai", "shanghai", "Shanghai",
                "Labyrinth of shikumen lanes turned into shops and studios.",
                "Wander narrow stone-gate alleys packed with design boutiques, galleries and cafes. Compact and "
                        + "touristy, but photogenic and easy to combine with the French Concession.",
                "10:00-22:00", "Free", false, null, "1-2 hours",
                31.2080, 121.4667, false));

        // --- Chengdu ---
        attractions.add(attraction("wuhou-shrine", "Wuhou Shrine", "武侯祠", AttractionCategory.TEMPLE,
                List.of("three-kingdoms", "history"), "Chengdu", "chengdu", "Sichuan",
                "China's most famous Three Kingdoms memorial temple.",
                "Dedicated to strategist Zhuge Liang and the Shu Han court. The red-wall bamboo corridor next door "
                        + "connects to Jinli Street - visit both in one trip.",
                "09:00-18:00", "¥50", false, null, "1-2 hours",
                30.6465, 104.0474, false));
        attractions.add(attraction("jinli-ancient-street", "Jinli Ancient Street", "锦里", AttractionCategory.STREET_DISTRICT,
                List.of("food", "lanterns", "night"), "Chengdu", "chengdu", "Sichuan",
                "Recreated Qing-era street of snacks, teahouses and lanterns.",
                "Evening is the time to come: red lanterns light up, snack stalls fire up (try three-cannon "
                        + "rice balls) and teahouses pour bottomless jasmine tea.",
                "All day (busiest 18:00-22:00)", "Free", false, null, "1-2 hours",
                30.6455, 104.0450, false));
        attractions.add(attraction("dujiangyan-irrigation", "Dujiangyan Irrigation System", "都江堰", AttractionCategory.HISTORICAL_SITE,
                List.of("unesco", "engineering", "river"), "Chengdu", "chengdu", "Sichuan",
                "2,200-year-old irrigation works still functioning today.",
                "The world's oldest surviving non-dam irrigation system, engineered in 256 BC and still feeding the "
                        + "Chengdu plain. Walk the river gorge and suspension bridge.",
                "08:00-18:00", "¥80", false, null, "Half day",
                31.0063, 103.6093, false));

        // --- Hangzhou ---
        attractions.add(attraction("lingyin-temple", "Lingyin Temple", "灵隐寺", AttractionCategory.TEMPLE,
                List.of("buddhism", "sculpture", "forest"), "Hangzhou", "hangzhou", "Zhejiang",
                "One of China's oldest and wealthiest Buddhist temples.",
                "Enter through the Feilai Feng grottoes - hundreds of Buddhist carvings cut into a limestone cliff - "
                        + "before reaching the temple halls in their forested valley.",
                "07:00-18:15", "¥45 temple + ¥30 Feilai Peak", false, null, "2-3 hours",
                30.2408, 120.1010, false));
        attractions.add(attraction("leifeng-pagoda", "Leifeng Pagoda", "雷峰塔", AttractionCategory.HISTORICAL_SITE,
                List.of("sunset", "pagoda", "legend"), "Hangzhou", "hangzhou", "Zhejiang",
                "Rebuilt pagoda famous for the White Snake legend.",
                "The best elevated view of West Lake, especially at sunset. Escalators up spare you the stairs; "
                        + "each level has viewing platforms over the lake and city.",
                "08:00-20:00 (May-Oct); 08:00-17:30 (Nov-Apr)", "¥40", false, null, "1-2 hours",
                30.2333, 120.1494, false));
        attractions.add(attraction("longjing-tea-village", "Longjing Tea Village", "龙井村", AttractionCategory.NATURE,
                List.of("tea", "countryside", "hiking"), "Hangzhou", "hangzhou", "Zhejiang",
                "Home of China's most celebrated green tea.",
                "Terraced tea fields climb the hills behind the village. Walk the paths, then sip freshly roasted "
                        + "Longjing in a farmhouse teahouse overlooking the rows.",
                "09:00-17:00", "Free (teahouse tastings vary)", false, null, "2-3 hours",
                30.2190, 120.1160, false));

        // --- Guilin ---
        attractions.add(attraction("li-river", "Li River Cruise", "漓江", AttractionCategory.NATURE,
                List.of("cruise", "karst", "must-see"), "Guilin", "guilin", "Guangxi",
                "China's most iconic karst river scenery.",
                "The 4-hour cruise from Guilin to Yangshuo glides past water buffalo, bamboo groves and the "
                        + "sugar-loaf peaks that appear on the ¥20 note. Book a top-deck seat.",
                "Cruises depart 09:00-11:00", "¥215-360 (cruise)", false, null, "4-5 hours",
                25.1630, 110.4300, true));
        attractions.add(attraction("reed-flute-cave", "Reed Flute Cave", "芦笛岩", AttractionCategory.NATURE,
                List.of("cave", "stalactites", "colorful"), "Guilin", "guilin", "Guangxi",
                "Spectacular limestone cave lit in rainbow colors.",
                "A 240 m walkway through cathedral-sized chambers of stalactites and stalagmites, theatrically lit "
                        + "with colored lights. Slightly kitsch, entirely memorable.",
                "07:30-18:00", "¥90", false, null, "2 hours",
                25.3030, 110.2560, false));
        attractions.add(attraction("elephant-trunk-hill", "Elephant Trunk Hill", "象鼻山", AttractionCategory.NATURE,
                List.of("icon", "river-view"), "Guilin", "guilin", "Guangxi",
                "Guilin's emblem - a hill shaped like a drinking elephant.",
                "The rock arch resembling an elephant's trunk dipping into the Li River is the city's symbol. Best "
                        + "photographed from the opposite bank or by boat in the evening.",
                "07:00-18:30", "¥55 (¥70 with boat)", false, null, "1-2 hours",
                25.2610, 110.2930, false));
        attractions.add(attraction("longji-rice-terraces", "Longji Rice Terraces", "龙脊梯田", AttractionCategory.NATURE,
                List.of("hiking", "minority-villages", "scenery"), "Guilin", "guilin", "Guangxi",
                "Dragon's-backbone terraces carved into mountainsides.",
                "Terraces cascade down entire valleys, farmed by Zhuang and Yao villages for 600 years. Day-trip "
                        + "from Guilin; overnighting in Ping'an village rewards you with sunrise over the water mirrors.",
                "All day (day trip from Guilin, ~2h each way)", "¥80", false, null, "Full day",
                25.7660, 110.1190, false));

        // --- Homepage showcases: MUST stay the final six entries (highest ids -> top of the popular feed) ---
        attractions.add(attraction("mutianyu-great-wall", "Mutianyu Great Wall", "慕田峪长城", AttractionCategory.HISTORICAL_SITE,
                List.of("unesco", "must-see", "hiking"), "Beijing", "beijing", "Beijing",
                "The most scenic restored stretch of the Great Wall near Beijing.",
                "Less crowded than Badaling with dramatic ridges and 23 watchtowers. Take the cable car up and "
                        + "coast down on the toboggan. Solid 2-3 hours of walking.",
                "07:30-18:00 (Apr-Oct); 08:00-17:00 (Nov-Mar)", "¥45 entry + ¥15 shuttle", true,
                "Passport required for online tickets - book 1-3 days ahead in high season", "Half day",
                40.4319, 116.5704, true));
        attractions.add(attraction("forbidden-city", "Forbidden City", "故宫", AttractionCategory.HISTORICAL_SITE,
                List.of("unesco", "must-see", "imperial"), "Beijing", "beijing", "Beijing",
                "The world's largest imperial palace complex - 980 buildings.",
                "Twenty-four Ming and Qing emperors ruled from this walled city within the city. Walk the central "
                        + "axis from Tiananmen, then explore the quieter eastern palaces and imperial garden.",
                "08:30-17:00 (Apr-Oct); 08:30-16:30 (Nov-Mar); closed Mondays", "¥60 (Apr-Oct); ¥40 (Nov-Mar)", true,
                "Passport required - book online at least 7 days ahead; tickets sell out in peak season", "3-4 hours",
                39.9163, 116.3972, true));
        attractions.add(attraction("terracotta-army", "Terracotta Army", "秦始皇兵马俑", AttractionCategory.HISTORICAL_SITE,
                List.of("unesco", "must-see", "archaeology"), "Xi'an", "xian", "Shaanxi",
                "Thousands of life-sized clay soldiers guarding China's first emperor.",
                "Three excavated pits hold an army of individually sculpted warriors, horses and chariots buried "
                        + "in 210 BC. Pit 1 is the postcard view; go early to beat the tour groups.",
                "08:30-18:00 (Mar-Nov); 08:30-17:30 (Dec-Feb)", "¥120", true,
                "Passport required - book online 3-7 days ahead; no same-day tickets in peak season", "3-4 hours",
                34.3841, 109.2785, true));
        attractions.add(attraction("the-bund", "The Bund", "外滩", AttractionCategory.MODERN_LANDMARK,
                List.of("skyline", "night-view", "must-see"), "Shanghai", "shanghai", "Shanghai",
                "Shanghai's iconic waterfront - colonial facades against the Pudong skyline.",
                "Walk the promenade at dusk as the Lujiazui towers light up across the Huangpu. Behind you, "
                        + "1920s banking palaces in beaux-arts and art-deco styles line the street.",
                "All day (skyline lights 19:00-22:00)", "Free", false, null, "1-2 hours",
                31.2400, 121.4900, true));
        attractions.add(attraction("chengdu-panda-base", "Chengdu Panda Base", "成都大熊猫繁育研究基地", AttractionCategory.NATURE,
                List.of("pandas", "must-see", "family"), "Chengdu", "chengdu", "Sichuan",
                "The best place on earth to see giant pandas up close.",
                "A bamboo-park research base with dozens of giant pandas, plus red pandas and nursery cubs. Pandas "
                        + "are most active at feeding time - arrive right at opening.",
                "07:30-18:00 (last entry 17:00)", "¥55", true,
                "Passport required - book 1-3 days ahead for the morning slot (pandas most active 07:30-10:00)", "Half day",
                30.7327, 104.1450, true));
        attractions.add(attraction("west-lake", "West Lake", "西湖", AttractionCategory.NATURE,
                List.of("unesco", "must-see", "boating"), "Hangzhou", "hangzhou", "Zhejiang",
                "The lake that inspired a thousand Chinese poems and paintings.",
                "A UNESCO cultural landscape of willow-lined causeways, pagodas and islands. Stroll the Su Causeway, "
                        + "rent a boat at sunset, or cycle the 15 km loop.",
                "All day", "Free (cruises ¥55-70)", false, null, "Half day",
                30.2470, 120.1490, true));

        return attractions;
    }

    private Attraction attraction(String slug, String name, String nameZh, AttractionCategory category,
                                  List<String> tags, String city, String citySlug, String province,
                                  String summary, String description, String openingHours, String ticketPrice,
                                  boolean bookingRequired, String bookingNote, String suggestedDuration,
                                  Double latitude, Double longitude, boolean popular) {
        return Attraction.builder()
                .slug(slug)
                .name(name)
                .nameZh(nameZh)
                .category(category)
                .tags(tags)
                .city(city)
                .citySlug(citySlug)
                .province(province)
                .summary(summary)
                .description(description)
                .openingHours(openingHours)
                .ticketPrice(ticketPrice)
                .bookingRequired(bookingRequired)
                .bookingNote(bookingNote)
                .suggestedDuration(suggestedDuration)
                .latitude(latitude)
                .longitude(longitude)
                .status(AttractionStatus.PUBLISHED)
                .isPopular(popular)
                .build();
    }
}
