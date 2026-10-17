package finance.rival;

import finance.company.CompanyManager;
import finance.company.CompanyType;

import java.util.List;
import java.util.Map;

/** 竞争公司名称池：按行业挑一个没被占用的名字；都占满时加编号。 */
public final class RivalNames {
    private static final Map<CompanyType, List<String>> POOL = Map.of(
            CompanyType.RAW_MATERIALS, List.of("北山铁业", "黑石冶炼", "红炉钢铁", "铁脊矿业"),
            CompanyType.BUILDING_BLOCKS, List.of("青岩石料", "白崖采石", "磐石建材", "灰谷石业"),
            CompanyType.FOOD, List.of("金穗农场", "丰年粮行", "南坡农庄", "麦浪农业"),
            CompanyType.FOOD_PROCESSING, List.of("香炉面包坊", "晨光食品", "老灶烘焙", "暖麦食坊"),
            CompanyType.FORESTRY, List.of("松涛林场", "橡木伐业", "深林木材", "绿荫林业"),
            CompanyType.COAL_MINING, List.of("乌金煤矿", "黑脉矿业", "炭谷煤业", "深井矿务"),
            CompanyType.TEXTILE, List.of("云纱纺织", "织锦坊", "羊绒织造", "彩线纺业"));

    private RivalNames() { }

    public static String pick(CompanyType type, long salt) {
        List<String> names = POOL.getOrDefault(type, List.of("新兴实业"));
        int start = (int) Math.floorMod(salt, (long) names.size());
        for (int i = 0; i < names.size(); i++) {
            String name = names.get((start + i) % names.size());
            if (!CompanyManager.hasCompanyNamed(name)) return name;
        }
        for (int n = 2; n < 1_000; n++) {
            String name = names.get(start) + n;
            if (!CompanyManager.hasCompanyNamed(name)) return name;
        }
        return names.get(start) + salt;
    }
}
