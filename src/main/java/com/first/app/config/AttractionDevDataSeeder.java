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

/**
 * Seeds the attractions catalog for local development (dev profile only).
 *
 * <p>Idempotent: runs only when the attractions table is empty. To force a re-seed,
 * clear the table first ({@code DELETE FROM attractions;}) and restart the app.
 *
 * <p>Insertion order matters: ids follow this order and the popular feed picks the top 6
 * by id descending, so the six homepage showcases MUST stay the final catalog entries.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class AttractionDevDataSeeder implements CommandLineRunner {

    private final AttractionRepository attractionRepository;

    @Override
    public void run(String... args) {
        if (attractionRepository.count() > 0) {
            return;
        }
        attractionRepository.saveAll(catalog());
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
