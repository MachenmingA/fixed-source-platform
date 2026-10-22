package com.fixedsource.db;

import java.util.List;

/**
 * 公开参考数据种子。
 * 来源均为政府公开页面或国家标准公开信息，种子只保存企业名称、区域、
 * 统一社会信用代码/组织机构代码、名录类别、标准号和发布日期等公开字段，
 * 不伪造联系方式、坐标或生产数据。
 */
public final class PublicDataSeed {

    public record KeyUnit(String recordKey, String title, String region, String creditCode,
                          String category, String annual, String sourceName, String sourceUrl) {}

    public record Standard(String recordKey, String title, String category, String standardCode,
                           String issuer, String publishDate, String effectiveDate, String scope,
                           String sourceName, String sourceUrl) {}

    private static final String BJ_ENV_LIST_2018 =
            "https://sthjj.beijing.gov.cn/bjhrb/index/xxgk69/sthjlyzwg/wrygl/830495/index.html";
    private static final String BJ_ENV_LIST_2019 =
            "https://sthjj.beijing.gov.cn/bjhrb/index/xxgk69/sthjlyzwg/wrygl/847451/index.html";
    private static final String CHY_ENV_LIST_2020 =
            "http://www.bjchy.gov.cn/dynamic/notice/8a24fe837128f1500171292821e4021a.html";
    private static final String PERMIT_PORTAL =
            "https://permit.mee.gov.cn/permitExt/defaults/default-index!getInformation.action";

    public static final List<KeyUnit> KEY_UNITS = List.of(
            new KeyUnit("BJ-2018-001", "北京荷美尔食品有限公司", "朝阳区", "911100006000297122",
                    "水环境重点排污单位", "2018", "北京市生态环境局公开名录", BJ_ENV_LIST_2018),
            new KeyUnit("BJ-2018-002", "北京京丰燃气发电有限责任公司", "丰台区", "911100007546789268",
                    "水环境重点排污单位", "2018", "北京市生态环境局公开名录", BJ_ENV_LIST_2018),
            new KeyUnit("BJ-2018-003", "瑞萨半导体（北京）有限公司", "海淀区", "91110108600042247X",
                    "水环境重点排污单位", "2018", "北京市生态环境局公开名录", BJ_ENV_LIST_2018),
            new KeyUnit("BJ-2018-004", "中国石油化工股份有限公司北京燕山分公司", "房山区", "91110304802763501L",
                    "水环境重点排污单位", "2018", "北京市生态环境局公开名录", BJ_ENV_LIST_2018),
            new KeyUnit("BJ-2018-005", "蒙牛乳业（北京）有限责任公司", "通州区", "91110000744735827H",
                    "水环境重点排污单位", "2018", "北京市生态环境局公开名录", BJ_ENV_LIST_2018),
            new KeyUnit("BJ-2018-006", "北京首钢冷轧薄板有限公司", "顺义区", "91110000678206539E",
                    "水环境重点排污单位", "2018", "北京市生态环境局公开名录", BJ_ENV_LIST_2018),
            new KeyUnit("BJ-2018-007", "北汽福田汽车股份有限公司北京欧辉客车分公司", "昌平区", "91110114777687681K",
                    "水环境重点排污单位", "2018", "北京市生态环境局公开名录", BJ_ENV_LIST_2018),
            new KeyUnit("BJ-2018-008", "统一石油化工有限公司", "大兴区", "91110000102888960U",
                    "水环境重点排污单位", "2018", "北京市生态环境局公开名录", BJ_ENV_LIST_2018),
            new KeyUnit("BJ-2018-009", "北京福田戴姆勒汽车有限公司", "怀柔区", "9111000071788494XU",
                    "水环境重点排污单位", "2018", "北京市生态环境局公开名录", BJ_ENV_LIST_2018),
            new KeyUnit("BJ-2018-010", "北京市老才臣食品有限公司", "平谷区", "91110117700009698W",
                    "水环境重点排污单位", "2018", "北京市生态环境局公开名录", BJ_ENV_LIST_2018),
            new KeyUnit("BJ-2018-011", "北京宝沃汽车有限公司", "密云区", "91110228MA0037BTXB",
                    "水环境重点排污单位", "2018", "北京市生态环境局公开名录", BJ_ENV_LIST_2018),
            new KeyUnit("BJ-2018-012", "北京八达岭酒业有限公司", "延庆区", "9111022972364911R",
                    "水环境重点排污单位", "2018", "北京市生态环境局公开名录", BJ_ENV_LIST_2018),
            new KeyUnit("BJ-2018-013", "中芯北方集成电路制造（北京）有限公司", "北京经济技术开发区", "91110302071737747W",
                    "水环境重点排污单位", "2018", "北京市生态环境局公开名录", BJ_ENV_LIST_2018),
            new KeyUnit("BJ-2018-014", "有研半导体材料有限公司", "西城区", "91110000600090126J",
                    "大气环境重点排污单位", "2018", "北京市生态环境局公开名录", BJ_ENV_LIST_2018),
            new KeyUnit("BJ-2018-015", "北京市和平里医院", "东城区", "40078267-0",
                    "其他重点排污单位", "2018", "北京市生态环境局公开名录", BJ_ENV_LIST_2018),
            new KeyUnit("BJ-2018-016", "北京燕山集联石油化工有限公司", "房山区", "91110304102764191Y",
                    "水环境重点排污单位", "2018", "北京市生态环境局公开名录", BJ_ENV_LIST_2018),
            new KeyUnit("CHY-2020-001", "北京城市排水集团有限责任公司高碑店污水处理厂", "朝阳区", "911101058016915345",
                    "水环境重点排污单位", "2020", "朝阳区生态环境局公开名录", CHY_ENV_LIST_2020),
            new KeyUnit("CHY-2020-002", "北京京能高安屯燃气热电有限责任公司", "朝阳区", "911100005674558745",
                    "水环境/大气环境重点排污单位", "2020", "朝阳区生态环境局公开名录", CHY_ENV_LIST_2020),
            new KeyUnit("CHY-2020-003", "华能北京热电有限责任公司", "朝阳区", "91110000X26000551M",
                    "水环境/大气环境重点排污单位", "2020", "朝阳区生态环境局公开名录", CHY_ENV_LIST_2020),
            new KeyUnit("CHY-2020-004", "北京太阳宫燃气热电有限公司", "朝阳区", "91110000781700113N",
                    "水环境/大气环境重点排污单位", "2020", "朝阳区生态环境局公开名录", CHY_ENV_LIST_2020)
    );

    public static final List<Standard> STANDARDS = List.of(
            new Standard("GB16297-1996", "大气污染物综合排放标准", "国家标准", "GB 16297-1996",
                    "国家环境保护局、国家技术监督局", "1996-04-12", "1997-01-01",
                    "固定源大气污染物排放限值；行业标准发布后按行业标准执行",
                    "国家标准全文公开系统",
                    "https://openstd.samr.gov.cn/bzgk/gb/newGbInfo?hcno=72B7BB9338A1B19773000E6E318AEB44"),
            new Standard("GB8978-1996", "污水综合排放标准", "国家标准", "GB 8978-1996",
                    "国家环境保护局、国家技术监督局", "1996-10-04", "1998-01-01",
                    "水污染物最高允许排放浓度与部分行业排水量；行业标准优先执行",
                    "国家标准全文公开系统",
                    "https://openstd.samr.gov.cn/bzgk/std/newGbInfo?hcno=1BF9CDBD6FDE2AD5053C69A36318972C"),
            new Standard("GB37822-2019", "挥发性有机物无组织排放控制标准", "国家标准", "GB 37822-2019",
                    "生态环境部、国家市场监督管理总局", "2019-05-24", "2019-07-01",
                    "VOCs 物料储存、转移输送、工艺过程、设备泄漏和敞开液面等无组织排放控制要求",
                    "生态环境部标准公开信息", "https://www.mee.gov.cn/"),
            new Standard("HJ819-2017", "排污单位自行监测技术指南 总则", "环境保护标准", "HJ 819-2017",
                    "环境保护部", "2017-04-25", "2017-06-01",
                    "自行监测一般要求、监测方案、质量保证与质量控制、信息记录和报告",
                    "生态环境部标准公开信息", "https://www.mee.gov.cn/"),
            new Standard("PERMIT-PORTAL", "全国排污许可证管理信息平台（公开端）", "数据平台", "permit.mee.gov.cn",
                    "生态环境部", "2021-03-01", "2021-03-01",
                    "可查询排污单位排口位置、排放污染物种类、排放浓度限值、执行标准和执行报告等公开信息",
                    "全国排污许可证管理信息平台", PERMIT_PORTAL)
    );

    private PublicDataSeed() {
    }
}