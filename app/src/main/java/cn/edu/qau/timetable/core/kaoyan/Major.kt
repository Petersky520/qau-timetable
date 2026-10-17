package cn.edu.qau.timetable.core.kaoyan

/**
 * 可选的考研专业意向。
 *
 * 用枚举而不是自由文本：推荐算法要按专业匹配院校的招生专业，
 * 自由文本会让「计算机」「计算机科学与技术」「计算机技术」变成三个互不相干的键。
 *
 * ## 两套编码体系（重要）
 *
 * 这个枚举同时装了**两套**教育部目录，共 330 项：
 *
 * 1. **研究生学科目录**（4 位一级学科 / 6 位二级学科，代码 01xx–14xx 前段）
 *    —— 如 `0812 计算机科学与技术`、`0817 化学工程与技术`。
 *    这类代码和院校招生目录 `majorCode` **直接对齐**，推荐引擎按它做精确匹配。
 *
 * 2. **本科专业目录**（6 位专业代码，如 `080901 计算机科学与技术`、
 *    `081302 制药工程`）—— 用户按自己的本科专业选意向时用。
 *
 * 两套的 6 位代码长得像但含义不同（`0812` 是一级学科，`080901` 是本科专业），
 * **不能混用**。同名专业在两套里都出现时，本科那条加「（本科）」后缀，
 * 让用户能区分，而不是看到两个一模一样的 chip 猜哪个对。
 *
 * ## 为什么 `mathRequired` 单独留字段
 * 不从代码推：同一个门类下学硕/专硕的数学要求并不统一，
 * 这里只表达「通常是否要考数学」。
 *
 * ## 门类必须显式声明
 * 覆盖 14 个学科门类（[MajorCategory]），每项都必须显式写出所属门类 ——
 * 不给默认值，是为了让「新增专业忘了归类」在编译期就暴露，
 * 而不是等到 UI 上冒出一个「其他」分组才发现。
 *
 * 哲学、历史学等门类下的一级学科数量不足 4 个，为了满足「每门类至少 4 个专业」，
 * 这些门类补的是**真实的二级学科代码**（6 位，如 010102 中国哲学），
 * 仍然来自教育部学科目录，不是自造代码。
 */
enum class Major(
    val label: String,
    /** 教育部学科门类代码，用于和院校目录对齐。 */
    val code: String,
    /** 该专业是否通常要求考数学（一/二/三）。影响难度评估。 */
    val mathRequired: Boolean,
    /** 所属学科门类，UI 按这个分组展示，避免 90 个 chip 平铺成一堵墙。 */
    val category: MajorCategory,
) {
    // ---- 哲学 ----
    PHILOSOPHY("哲学", "0101", false, MajorCategory.PHILOSOPHY),
    MARXIST_PHILOSOPHY("马克思主义哲学", "010101", false, MajorCategory.PHILOSOPHY),
    CHINESE_PHILOSOPHY("中国哲学", "010102", false, MajorCategory.PHILOSOPHY),
    FOREIGN_PHILOSOPHY("外国哲学", "010103", false, MajorCategory.PHILOSOPHY),
    LOGIC("逻辑学", "010104", false, MajorCategory.PHILOSOPHY),
    ETHICS("伦理学", "010105", false, MajorCategory.PHILOSOPHY),
    AESTHETICS("美学", "010106", false, MajorCategory.PHILOSOPHY),

    // ---- 经济学 ----
    ECONOMICS("应用经济学", "0202", true, MajorCategory.ECONOMICS),
    THEORETICAL_ECONOMICS("理论经济学", "0201", true, MajorCategory.ECONOMICS),
    FINANCE("金融", "0251", true, MajorCategory.ECONOMICS),
    APPLIED_STATISTICS("应用统计", "0252", true, MajorCategory.ECONOMICS),
    TAXATION("税务", "0253", true, MajorCategory.ECONOMICS),
    INTERNATIONAL_BUSINESS("国际商务", "0254", true, MajorCategory.ECONOMICS),
    INSURANCE("保险", "0255", true, MajorCategory.ECONOMICS),
    ASSET_VALUATION("资产评估", "0256", true, MajorCategory.ECONOMICS),
    AUDIT("审计", "0257", true, MajorCategory.ECONOMICS),

    // ---- 法学 ----
    LAW("法律（非法学）", "0351", false, MajorCategory.LAW),
    JURISPRUDENCE("法学", "0301", false, MajorCategory.LAW),
    POLITICAL_SCIENCE("政治学", "0302", false, MajorCategory.LAW),
    SOCIOLOGY("社会学", "0303", false, MajorCategory.LAW),
    MARXISM("马克思主义理论", "0305", false, MajorCategory.LAW),
    PUBLIC_SECURITY("公安学", "0306", false, MajorCategory.LAW),
    LAW_LLM("法律（法学）", "0352", false, MajorCategory.LAW),
    POLICING("警务", "0353", false, MajorCategory.LAW),

    // ---- 教育学 ----
    EDUCATION("教育硕士", "0451", false, MajorCategory.EDUCATION),
    PEDAGOGY("教育学", "0401", false, MajorCategory.EDUCATION),
    PSYCHOLOGY("心理学", "0402", false, MajorCategory.EDUCATION),
    SPORTS_SCIENCE("体育学", "0403", false, MajorCategory.EDUCATION),
    PHYSICAL_EDUCATION("体育", "0452", false, MajorCategory.EDUCATION),
    CHINESE_TEACHING("汉语国际教育", "0453", false, MajorCategory.EDUCATION),
    APPLIED_PSYCHOLOGY("应用心理", "0454", false, MajorCategory.EDUCATION),
    EDUCATIONAL_TECHNOLOGY("教育技术学", "040110", false, MajorCategory.EDUCATION),

    // ---- 文学 ----
    FOREIGN_LANG("外国语言文学", "0502", false, MajorCategory.LITERATURE),
    JOURNALISM("新闻与传播", "0552", false, MajorCategory.LITERATURE),
    CHINESE_LITERATURE("中国语言文学", "0501", false, MajorCategory.LITERATURE),
    JOURNALISM_STUDIES("新闻传播学", "0503", false, MajorCategory.LITERATURE),
    TRANSLATION("翻译", "0551", false, MajorCategory.LITERATURE),
    PUBLISHING("出版", "0553", false, MajorCategory.LITERATURE),
    CHINESE_PHILOLOGY("汉语言文字学", "050103", false, MajorCategory.LITERATURE),
    COMPARATIVE_LITERATURE("比较文学与世界文学", "050108", false, MajorCategory.LITERATURE),

    // ---- 历史学 ----
    ARCHAEOLOGY("考古学", "0601", false, MajorCategory.HISTORY),
    CHINESE_HISTORY("中国史", "0602", false, MajorCategory.HISTORY),
    WORLD_HISTORY("世界史", "0603", false, MajorCategory.HISTORY),
    MUSEOLOGY("文物与博物馆", "0651", false, MajorCategory.HISTORY),
    HISTORIOGRAPHY("史学理论及史学史", "060201", false, MajorCategory.HISTORY),

    // ---- 理学 ----
    MATHEMATICS("数学", "0701", true, MajorCategory.SCIENCE),
    PHYSICS("物理学", "0702", true, MajorCategory.SCIENCE),
    CHEMISTRY_SCIENCE("化学", "0703", true, MajorCategory.SCIENCE),
    ASTRONOMY("天文学", "0704", true, MajorCategory.SCIENCE),
    GEOGRAPHY("地理学", "0705", false, MajorCategory.SCIENCE),
    ATMOSPHERIC_SCIENCE("大气科学", "0706", true, MajorCategory.SCIENCE),
    MARINE_SCIENCE("海洋科学", "0707", false, MajorCategory.SCIENCE),
    GEOPHYSICS("地球物理学", "0708", true, MajorCategory.SCIENCE),
    GEOLOGY("地质学", "0709", false, MajorCategory.SCIENCE),
    BIOLOGY("生物学", "0710", false, MajorCategory.SCIENCE),
    ECOLOGY("生态学", "0713", false, MajorCategory.SCIENCE),
    STATISTICS("统计学", "0714", true, MajorCategory.SCIENCE),

    // ---- 工学 ----
    COMPUTER("计算机科学与技术", "0812", true, MajorCategory.ENGINEERING),
    SOFTWARE("软件工程", "0835", true, MajorCategory.ENGINEERING),
    ELECTRONIC("电子信息", "0854", true, MajorCategory.ENGINEERING),
    AUTOMATION("控制科学与工程", "0811", true, MajorCategory.ENGINEERING),
    MECHANICAL("机械工程", "0802", true, MajorCategory.ENGINEERING),
    CIVIL("土木工程", "0814", true, MajorCategory.ENGINEERING),
    CHEMISTRY("化学工程与技术", "0817", true, MajorCategory.ENGINEERING),
    FOOD("食品科学与工程", "0832", true, MajorCategory.ENGINEERING),
    OPTICAL_ENGINEERING("光学工程", "0803", true, MajorCategory.ENGINEERING),
    INSTRUMENTATION("仪器科学与技术", "0804", true, MajorCategory.ENGINEERING),
    MATERIALS("材料科学与工程", "0805", true, MajorCategory.ENGINEERING),
    METALLURGY("冶金工程", "0806", true, MajorCategory.ENGINEERING),
    POWER_ENGINEERING("动力工程及工程热物理", "0807", true, MajorCategory.ENGINEERING),
    ELECTRICAL("电气工程", "0808", true, MajorCategory.ENGINEERING),
    INFORMATION_COMM("信息与通信工程", "0810", true, MajorCategory.ENGINEERING),
    ARCHITECTURE("建筑学", "0813", true, MajorCategory.ENGINEERING),
    WATER_CONSERVANCY("水利工程", "0815", true, MajorCategory.ENGINEERING),
    SURVEYING("测绘科学与技术", "0816", true, MajorCategory.ENGINEERING),
    MINING("矿业工程", "0819", true, MajorCategory.ENGINEERING),
    PETROLEUM("石油与天然气工程", "0820", true, MajorCategory.ENGINEERING),
    TEXTILE("纺织科学与工程", "0821", true, MajorCategory.ENGINEERING),
    LIGHT_INDUSTRY("轻工技术与工程", "0822", true, MajorCategory.ENGINEERING),
    TRANSPORTATION("交通运输工程", "0823", true, MajorCategory.ENGINEERING),
    SHIPBUILDING("船舶与海洋工程", "0824", true, MajorCategory.ENGINEERING),
    AEROSPACE("航空宇航科学与技术", "0825", true, MajorCategory.ENGINEERING),
    NUCLEAR("核科学与技术", "0827", true, MajorCategory.ENGINEERING),
    AGRICULTURAL_ENGINEERING("农业工程", "0828", true, MajorCategory.ENGINEERING),
    ENVIRONMENT("环境科学与工程", "0830", true, MajorCategory.ENGINEERING),
    BIOMEDICAL_ENGINEERING("生物医学工程", "0831", true, MajorCategory.ENGINEERING),
    URBAN_PLANNING("城乡规划学", "0833", false, MajorCategory.ENGINEERING),
    SAFETY_ENGINEERING("安全科学与工程", "0837", true, MajorCategory.ENGINEERING),
    MECHANICAL_PROF("机械", "0855", true, MajorCategory.ENGINEERING),
    ENERGY_POWER("能源动力", "0858", true, MajorCategory.ENGINEERING),
    CIVIL_HYDRAULIC("土木水利", "0859", true, MajorCategory.ENGINEERING),

    // ---- 农学 ----
    AGRONOMY("作物学", "0901", false, MajorCategory.AGRICULTURE),
    PLANT_PROTECTION("植物保护", "0904", false, MajorCategory.AGRICULTURE),
    ANIMAL_SCIENCE("畜牧学", "0905", false, MajorCategory.AGRICULTURE),
    VETERINARY("兽医学", "0906", false, MajorCategory.AGRICULTURE),
    HORTICULTURE("园艺学", "0902", false, MajorCategory.AGRICULTURE),
    LANDSCAPE("风景园林", "0953", false, MajorCategory.AGRICULTURE),
    AGRICULTURAL_RESOURCES("农业资源与环境", "0903", false, MajorCategory.AGRICULTURE),
    FORESTRY("林学", "0907", false, MajorCategory.AGRICULTURE),
    AQUACULTURE("水产", "0908", false, MajorCategory.AGRICULTURE),
    GRASS_SCIENCE("草学", "0909", false, MajorCategory.AGRICULTURE),
    AGRICULTURE_MASTER("农业硕士", "0951", false, MajorCategory.AGRICULTURE),
    FORESTRY_MASTER("林业硕士", "0954", false, MajorCategory.AGRICULTURE),

    // ---- 医学 ----
    BASIC_MEDICINE("基础医学", "1001", false, MajorCategory.MEDICINE),
    CLINICAL_MEDICINE("临床医学", "1002", false, MajorCategory.MEDICINE),
    STOMATOLOGY("口腔医学", "1003", false, MajorCategory.MEDICINE),
    PUBLIC_HEALTH("公共卫生与预防医学", "1004", false, MajorCategory.MEDICINE),
    TRADITIONAL_CHINESE_MEDICINE("中医学", "1005", false, MajorCategory.MEDICINE),
    INTEGRATIVE_MEDICINE("中西医结合", "1006", false, MajorCategory.MEDICINE),
    PHARMACY("药学", "1007", false, MajorCategory.MEDICINE),
    CHINESE_PHARMACY("中药学", "1008", false, MajorCategory.MEDICINE),
    SPECIAL_MEDICINE("特种医学", "1009", false, MajorCategory.MEDICINE),
    MEDICAL_TECHNOLOGY("医学技术", "1010", false, MajorCategory.MEDICINE),
    NURSING("护理学", "1011", false, MajorCategory.MEDICINE),
    CLINICAL_MEDICINE_MASTER("临床医学硕士", "1051", false, MajorCategory.MEDICINE),
    PUBLIC_HEALTH_MASTER("公共卫生硕士", "1053", false, MajorCategory.MEDICINE),
    NURSING_MASTER("护理硕士", "1054", false, MajorCategory.MEDICINE),
    PHARMACY_MASTER("药学硕士", "1055", false, MajorCategory.MEDICINE),
    TRADITIONAL_MEDICINE_MASTER("中医硕士", "1057", false, MajorCategory.MEDICINE),

    // ---- 管理学 ----
    MANAGEMENT("农林经济管理", "1203", true, MajorCategory.MANAGEMENT),
    MANAGEMENT_SCIENCE("管理科学与工程", "1201", true, MajorCategory.MANAGEMENT),
    BUSINESS_ADMIN("工商管理", "1202", true, MajorCategory.MANAGEMENT),
    PUBLIC_ADMIN("公共管理", "1204", false, MajorCategory.MANAGEMENT),
    LIBRARY_SCIENCE("图书情报与档案管理", "1205", false, MajorCategory.MANAGEMENT),
    MBA("工商管理硕士", "1251", true, MajorCategory.MANAGEMENT),
    MPA("公共管理硕士", "1252", false, MajorCategory.MANAGEMENT),
    MPACC("会计硕士", "1253", true, MajorCategory.MANAGEMENT),
    TOURISM_MANAGEMENT("旅游管理硕士", "1254", false, MajorCategory.MANAGEMENT),
    ENGINEERING_MANAGEMENT("工程管理硕士", "1256", true, MajorCategory.MANAGEMENT),

    // ---- 艺术学 ----
    ART_THEORY("艺术学理论", "1301", false, MajorCategory.ART),
    MUSIC_DANCE("音乐与舞蹈学", "1302", false, MajorCategory.ART),
    DRAMA_FILM("戏剧与影视学", "1303", false, MajorCategory.ART),
    FINE_ARTS("美术学", "1304", false, MajorCategory.ART),
    DESIGN("设计学", "1305", false, MajorCategory.ART),
    ART_MASTER("艺术硕士", "1351", false, MajorCategory.ART),
    MUSIC_MASTER("音乐", "1352", false, MajorCategory.ART),
    BROADCAST_ANNOUNCING("广播电视", "1353", false, MajorCategory.ART),
    FILM_MASTER("电影", "1354", false, MajorCategory.ART),

    // ---- 军事学 ----
    MILITARY_THOUGHT("军事思想及军事历史", "1101", false, MajorCategory.MILITARY),
    STRATEGY("战略学", "1102", false, MajorCategory.MILITARY),
    CAMPAIGN("战役学", "1103", false, MajorCategory.MILITARY),
    TACTICS("战术学", "1104", false, MajorCategory.MILITARY),
    MILITARY_COMMAND("军队指挥学", "1105", false, MajorCategory.MILITARY),
    MILITARY_MANAGEMENT("军事管理学", "1106", false, MajorCategory.MILITARY),
    MILITARY_LOGISTICS("军事后勤学", "1108", false, MajorCategory.MILITARY),
    MILITARY_EQUIPMENT("军事装备学", "1109", false, MajorCategory.MILITARY),

    // ---- 交叉学科 ----
    INTEGRATED_CIRCUIT("集成电路科学与工程", "1401", true, MajorCategory.CROSS),
    NATIONAL_SECURITY("国家安全学", "1402", false, MajorCategory.CROSS),
    DESIGN_CROSS("设计学（交叉学科）", "1403", false, MajorCategory.CROSS),
    REMOTE_SENSING("遥感科学与技术", "1404", true, MajorCategory.CROSS),
    INTELLIGENT_SCIENCE("智能科学与技术", "1405", true, MajorCategory.CROSS),
    NANO_SCIENCE("纳米科学与工程", "1406", true, MajorCategory.CROSS),

    // ================================================================
    // 以下为**本科专业目录**（教育部《普通高等学校本科专业目录》2024 版，
    // 6 位专业代码）。上面那 152 项是研究生学科目录（4 位一级学科 /
    // 部分 6 位二级学科），两者编码体系不同、用途也不同：
    //
    // - 研究生代码（0812）用于和院校招生目录的 `majorCode` 对齐；
    // - 本科代码（080901）用于用户按自己的**本科专业**筛选意向。
    //
    // 同名专业加「（本科）」后缀是为了让搜索结果能区分两条：
    // 搜「计算机」会同时出「计算机科学与技术」(0812, 研) 和
    // 「计算机科学与技术（本科）」(080901)，用户能自己判断要哪一个，
    // 而不是看到两个一模一样的 chip 猜哪个是对的。
    // ================================================================

    // ---- 工学 · 机械类 ----
    MECHANICAL_DESIGN("机械设计制造及其自动化", "080202", true, MajorCategory.ENGINEERING),
    MATERIAL_FORMING("材料成型及控制工程", "080203", true, MajorCategory.ENGINEERING),
    MECHANICAL_ELECTRONIC("机械电子工程", "080204", true, MajorCategory.ENGINEERING),
    INDUSTRIAL_DESIGN("工业设计", "080205", false, MajorCategory.ENGINEERING),
    PROCESS_EQUIPMENT("过程装备与控制工程", "080206", true, MajorCategory.ENGINEERING),
    VEHICLE_ENGINEERING("车辆工程", "080207", true, MajorCategory.ENGINEERING),
    ROBOT_ENGINEERING("机器人工程", "080803", true, MajorCategory.ENGINEERING),
    MEASUREMENT_CONTROL("测控技术与仪器", "080301", true, MajorCategory.ENGINEERING),

    // ---- 工学 · 材料类 ----
    METAL_MATERIAL("金属材料工程", "080405", true, MajorCategory.ENGINEERING),
    INORGANIC_NONMETAL("无机非金属材料工程", "080406", true, MajorCategory.ENGINEERING),
    HIGH_MOLECULE("高分子材料与工程", "080407", true, MajorCategory.ENGINEERING),
    COMPOSITE_MATERIAL("复合材料与工程", "080408", true, MajorCategory.ENGINEERING),
    NEW_ENERGY_MATERIAL("新能源材料与器件", "080414", true, MajorCategory.ENGINEERING),

    // ---- 工学 · 能源动力类 ----
    NEW_ENERGY_SCIENCE("新能源科学与工程", "080503", true, MajorCategory.ENGINEERING),
    ENERGY_STORAGE("储能科学与工程", "080504", true, MajorCategory.ENGINEERING),

    // ---- 工学 · 电气 / 电子信息类 ----
    ELECTRICAL_AUTOMATION("电气工程及其自动化", "080601", true, MajorCategory.ENGINEERING),
    SMART_GRID("智能电网信息工程", "080602", true, MajorCategory.ENGINEERING),
    ELECTRONIC_INFO("电子信息工程", "080701", true, MajorCategory.ENGINEERING),
    ELECTRONIC_SCIENCE_B("电子科学与技术（本科）", "080702", true, MajorCategory.ENGINEERING),
    COMMUNICATION_ENGINEERING("通信工程", "080703", true, MajorCategory.ENGINEERING),
    MICROELECTRONICS("微电子科学与工程", "080704", true, MajorCategory.ENGINEERING),
    OPTOELECTRONIC("光电信息科学与工程", "080705", true, MajorCategory.ENGINEERING),
    IC_DESIGN("集成电路设计与集成系统", "080710", true, MajorCategory.ENGINEERING),

    // ---- 工学 · 自动化 / 计算机类 ----
    AUTOMATION_B("自动化", "080801", true, MajorCategory.ENGINEERING),
    RAIL_SIGNAL("轨道交通信号与控制", "080802", true, MajorCategory.ENGINEERING),
    COMPUTER_SCIENCE_B("计算机科学与技术（本科）", "080901", true, MajorCategory.ENGINEERING),
    SOFTWARE_ENGINEERING_B("软件工程（本科）", "080902", true, MajorCategory.ENGINEERING),
    NETWORK_ENGINEERING("网络工程", "080903", true, MajorCategory.ENGINEERING),
    INFORMATION_SECURITY("信息安全", "080904", true, MajorCategory.ENGINEERING),
    INTERNET_OF_THINGS("物联网工程", "080905", true, MajorCategory.ENGINEERING),
    DIGITAL_MEDIA_TECH("数字媒体技术", "080906", true, MajorCategory.ENGINEERING),
    DATA_SCIENCE("数据科学与大数据技术", "080910", true, MajorCategory.ENGINEERING),
    AI_ENGINEERING("人工智能", "080917", true, MajorCategory.ENGINEERING),
    BLOCKCHAIN("区块链工程", "080919", true, MajorCategory.ENGINEERING),

    // ---- 工学 · 土木 / 水利类 ----
    CIVIL_ENGINEERING_B("土木工程（本科）", "081001", true, MajorCategory.ENGINEERING),
    BUILDING_ENVIRONMENT("建筑环境与能源应用工程", "081002", true, MajorCategory.ENGINEERING),
    WATER_SUPPLY("给排水科学与工程", "081003", true, MajorCategory.ENGINEERING),
    ROAD_BRIDGE("道路桥梁与渡河工程", "081006", true, MajorCategory.ENGINEERING),
    WATER_RESOURCES("水利水电工程", "081101", true, MajorCategory.ENGINEERING),
    PORT_CHANNEL("港口航道与海岸工程", "081103", true, MajorCategory.ENGINEERING),

    // ---- 工学 · 测绘类 ----
    SURVEYING_B("测绘工程", "081201", true, MajorCategory.ENGINEERING),
    REMOTE_SENSING_B("遥感科学与技术（本科）", "081202", true, MajorCategory.ENGINEERING),

    // ---- 工学 · 化工与制药类 ----
    CHEMICAL_ENGINEERING_B("化学工程与工艺", "081301", true, MajorCategory.ENGINEERING),
    PHARMACEUTICAL_ENGINEERING("制药工程", "081302", true, MajorCategory.ENGINEERING),
    RESOURCE_RECYCLING("资源循环科学与工程", "081303", true, MajorCategory.ENGINEERING),
    ENERGY_CHEMICAL("能源化学工程", "081304", true, MajorCategory.ENGINEERING),
    BIOPHARMACEUTICAL("生物制药", "083002", true, MajorCategory.ENGINEERING),
    PHARMACEUTICAL_PREP("药物制剂", "100702", true, MajorCategory.MEDICINE),

    // ---- 工学 · 地质 / 矿业 / 石油类 ----
    GEOLOGICAL_ENGINEERING("地质工程", "081401", true, MajorCategory.ENGINEERING),
    EXPLORATION_TECH("勘查技术与工程", "081402", true, MajorCategory.ENGINEERING),
    MINING_ENGINEERING("采矿工程", "081501", true, MajorCategory.ENGINEERING),
    PETROLEUM_ENGINEERING("石油工程", "081502", true, MajorCategory.ENGINEERING),
    MINERAL_PROCESSING("矿物加工工程", "081503", true, MajorCategory.ENGINEERING),
    NATURAL_GAS("油气储运工程", "081504", true, MajorCategory.ENGINEERING),

    // ---- 工学 · 纺织 / 轻工类 ----
    TEXTILE_ENGINEERING("纺织工程", "081601", true, MajorCategory.ENGINEERING),
    GARMENT_DESIGN("服装设计与工程", "081602", false, MajorCategory.ENGINEERING),
    LIGHT_INDUSTRY_B("轻化工程", "081701", true, MajorCategory.ENGINEERING),
    PACKAGING_ENGINEERING("包装工程", "081702", true, MajorCategory.ENGINEERING),
    PRINTING_ENGINEERING("印刷工程", "081703", true, MajorCategory.ENGINEERING),

    // ---- 工学 · 交通运输类 ----
    TRANSPORT_ENGINEERING("交通运输", "081801", true, MajorCategory.ENGINEERING),
    TRAFFIC_ENGINEERING("交通工程", "081802", true, MajorCategory.ENGINEERING),
    NAVIGATION_TECH("航海技术", "081803", false, MajorCategory.ENGINEERING),
    FLIGHT_TECHNOLOGY("飞行技术", "081805", false, MajorCategory.ENGINEERING),

    // ---- 工学 · 海洋 / 航空航天 / 兵器 / 核工程类 ----
    SHIP_BUILDING("船舶与海洋工程（本科）", "081901", true, MajorCategory.ENGINEERING),
    MARINE_ENGINEERING("海洋工程与技术", "081902", true, MajorCategory.ENGINEERING),
    AEROSPACE_ENGINEERING("航空航天工程", "082001", true, MajorCategory.ENGINEERING),
    AIRCRAFT_DESIGN("飞行器设计与工程", "082002", true, MajorCategory.ENGINEERING),
    AIRCRAFT_POWER("飞行器动力工程", "082004", true, MajorCategory.ENGINEERING),
    WEAPON_SYSTEM("武器系统与工程", "082101", true, MajorCategory.ENGINEERING),
    NUCLEAR_ENGINEERING("核工程与核技术", "082201", true, MajorCategory.ENGINEERING),
    RADIATION_PROTECTION("辐射防护与核安全", "082202", true, MajorCategory.ENGINEERING),

    // ---- 工学 · 农业工程 / 食品类 ----
    AGRICULTURAL_MACHINERY("农业机械化及其自动化", "082302", true, MajorCategory.ENGINEERING),
    AGRICULTURAL_WATER("农业水利工程", "082304", true, MajorCategory.ENGINEERING),
    FOOD_SCIENCE_B("食品科学与工程（本科）", "082701", true, MajorCategory.ENGINEERING),
    FOOD_QUALITY("食品质量与安全", "082702", true, MajorCategory.ENGINEERING),
    GRAIN_ENGINEERING("粮食工程", "082703", true, MajorCategory.ENGINEERING),
    BREWING_ENGINEERING("酿酒工程", "082705", true, MajorCategory.ENGINEERING),

    // ---- 工学 · 建筑类 ----
    ARCHITECTURE_B("建筑学（本科）", "082801", false, MajorCategory.ENGINEERING),
    URBAN_PLANNING_B("城乡规划", "082802", false, MajorCategory.ENGINEERING),
    LANDSCAPE_ARCHITECTURE_B("风景园林（本科）", "082803", false, MajorCategory.ENGINEERING),

    // ---- 工学 · 环境 / 安全 / 生物 / 公安技术类 ----
    ENVIRONMENTAL_ENGINEERING("环境工程", "082502", true, MajorCategory.ENGINEERING),
    ENVIRONMENTAL_SCIENCE_B("环境科学", "082503", true, MajorCategory.ENGINEERING),
    SAFETY_ENGINEERING_B("安全工程", "082901", true, MajorCategory.ENGINEERING),
    FIRE_PROTECTION("消防工程", "082902", true, MajorCategory.ENGINEERING),
    BIOMEDICAL_ENGINEERING_B("生物医学工程（本科）", "082601", true, MajorCategory.ENGINEERING),
    BIOLOGICAL_ENGINEERING("生物工程", "083001", true, MajorCategory.ENGINEERING),
    CRIMINAL_SCIENCE("刑事科学技术", "083101", false, MajorCategory.ENGINEERING),
    NETWORK_SECURITY_LAW("网络安全与执法", "083108", false, MajorCategory.ENGINEERING),

    // ---- 理学 ----
    MATHEMATICS_B("数学与应用数学", "070101", true, MajorCategory.SCIENCE),
    INFO_COMPUTING("信息与计算科学", "070102", true, MajorCategory.SCIENCE),
    APPLIED_STATISTICS_B("应用统计学", "071202", true, MajorCategory.SCIENCE),
    PHYSICS_B("物理学（本科）", "070201", true, MajorCategory.SCIENCE),
    APPLIED_PHYSICS("应用物理学", "070202", true, MajorCategory.SCIENCE),
    CHEMISTRY_B("化学（本科）", "070301", false, MajorCategory.SCIENCE),
    APPLIED_CHEMISTRY("应用化学", "070302", false, MajorCategory.SCIENCE),
    BIOLOGICAL_SCIENCE("生物科学", "071001", false, MajorCategory.SCIENCE),
    BIOTECHNOLOGY("生物技术", "071002", false, MajorCategory.SCIENCE),
    BIOLOGICAL_INFO("生物信息学", "071003", true, MajorCategory.SCIENCE),
    GEOGRAPHY_B("地理科学", "070501", false, MajorCategory.SCIENCE),
    GIS("地理信息科学", "070504", true, MajorCategory.SCIENCE),
    ATMOSPHERIC_B("大气科学（本科）", "070601", true, MajorCategory.SCIENCE),
    MARINE_SCIENCE_B("海洋科学（本科）", "070701", false, MajorCategory.SCIENCE),
    GEOLOGY_B("地质学（本科）", "070901", false, MajorCategory.SCIENCE),
    PSYCHOLOGY_B("心理学（本科）", "071101", false, MajorCategory.SCIENCE),

    // ---- 农学 ----
    AGRONOMY_B("农学", "090101", false, MajorCategory.AGRICULTURE),
    HORTICULTURE_B("园艺", "090102", false, MajorCategory.AGRICULTURE),
    PLANT_PROTECTION_B("植物保护（本科）", "090103", false, MajorCategory.AGRICULTURE),
    SEED_SCIENCE("种子科学与工程", "090105", false, MajorCategory.AGRICULTURE),
    ANIMAL_SCIENCE_B("动物科学", "090301", false, MajorCategory.AGRICULTURE),
    ANIMAL_MEDICINE("动物医学", "090401", false, MajorCategory.AGRICULTURE),
    QUARANTINE("动植物检疫", "090403", false, MajorCategory.AGRICULTURE),
    FORESTRY_B("林学（本科）", "090501", false, MajorCategory.AGRICULTURE),
    LANDSCAPE_GARDEN("园林", "090502", false, MajorCategory.AGRICULTURE),
    SOIL_WATER("水土保持与荒漠化防治", "090203", false, MajorCategory.AGRICULTURE),

    // ---- 医学 ----
    CLINICAL_MEDICINE_B("临床医学（本科）", "100201", false, MajorCategory.MEDICINE),
    ANESTHESIOLOGY("麻醉学", "100202", false, MajorCategory.MEDICINE),
    MEDICAL_IMAGING("医学影像学", "100203", false, MajorCategory.MEDICINE),
    STOMATOLOGY_B("口腔医学（本科）", "100301", false, MajorCategory.MEDICINE),
    PREVENTIVE_MEDICINE("预防医学", "100401", false, MajorCategory.MEDICINE),
    TCM_B("中医学（本科）", "100501", false, MajorCategory.MEDICINE),
    PHARMACY_B("药学（本科）", "100701", false, MajorCategory.MEDICINE),
    TCM_PHARMACY_B("中药学（本科）", "100801", false, MajorCategory.MEDICINE),
    NURSING_B("护理学（本科）", "101101", false, MajorCategory.MEDICINE),
    REHABILITATION("康复治疗学", "101005", false, MajorCategory.MEDICINE),
    MEDICAL_LAB("医学检验技术", "101001", false, MajorCategory.MEDICINE),

    // ---- 经济学 ----
    ECONOMICS_B("经济学", "020101", true, MajorCategory.ECONOMICS),
    INTL_ECONOMY("国际经济与贸易", "020401", false, MajorCategory.ECONOMICS),
    FINANCE_B("金融学", "020301", true, MajorCategory.ECONOMICS),
    FINANCIAL_ENGINEERING("金融工程", "020302", true, MajorCategory.ECONOMICS),
    INSURANCE_B("保险学", "020303", true, MajorCategory.ECONOMICS),
    INVESTMENT("投资学", "020304", true, MajorCategory.ECONOMICS),
    ECONOMIC_STATISTICS("经济统计学", "020102", true, MajorCategory.ECONOMICS),

    // ---- 管理学 ----
    MANAGEMENT_SCIENCE_B("管理科学", "120101", true, MajorCategory.MANAGEMENT),
    INFO_MANAGEMENT("信息管理与信息系统", "120102", true, MajorCategory.MANAGEMENT),
    ENGINEERING_COST("工程造价", "120105", false, MajorCategory.MANAGEMENT),
    BUSINESS_ADMIN_B("工商管理（本科）", "120201", false, MajorCategory.MANAGEMENT),
    MARKETING("市场营销", "120202", false, MajorCategory.MANAGEMENT),
    ACCOUNTING_B("会计学", "120203", false, MajorCategory.MANAGEMENT),
    FINANCIAL_MANAGEMENT("财务管理", "120204", false, MajorCategory.MANAGEMENT),
    HR_MANAGEMENT("人力资源管理", "120206", false, MajorCategory.MANAGEMENT),
    AUDITING_B("审计学", "120207", false, MajorCategory.MANAGEMENT),
    LOGISTICS_MANAGEMENT("物流管理", "120601", false, MajorCategory.MANAGEMENT),
    E_COMMERCE("电子商务", "120801", false, MajorCategory.MANAGEMENT),
    TOURISM_MANAGEMENT_B("旅游管理", "120901", false, MajorCategory.MANAGEMENT),
    LAND_RESOURCE("土地资源管理", "120404", false, MajorCategory.MANAGEMENT),
    ADMINISTRATION("行政管理", "120402", false, MajorCategory.MANAGEMENT),
    PUBLIC_AFFAIRS("公共事业管理", "120401", false, MajorCategory.MANAGEMENT),

    // ---- 法学 ----
    LAW_B("法学（本科）", "030101", false, MajorCategory.LAW),
    INTELLECTUAL_PROPERTY("知识产权", "030102", false, MajorCategory.LAW),
    POLITICAL_SCIENCE_B("政治学与行政学", "030201", false, MajorCategory.LAW),
    INTL_POLITICS("国际政治", "030202", false, MajorCategory.LAW),
    SOCIOLOGY_B("社会学（本科）", "030301", false, MajorCategory.LAW),
    SOCIAL_WORK("社会工作", "030302", false, MajorCategory.LAW),
    IDEOLOGICAL_EDU("思想政治教育", "030503", false, MajorCategory.LAW),

    // ---- 文学 ----
    CHINESE_LANGUAGE("汉语言文学", "050101", false, MajorCategory.LITERATURE),
    ENGLISH_B("英语", "050201", false, MajorCategory.LITERATURE),
    JAPANESE("日语", "050207", false, MajorCategory.LITERATURE),
    TRANSLATION_B("翻译（本科）", "050261", false, MajorCategory.LITERATURE),
    JOURNALISM_B("新闻学", "050301", false, MajorCategory.LITERATURE),
    BROADCASTING("广播电视学", "050302", false, MajorCategory.LITERATURE),
    ADVERTISING("广告学", "050303", false, MajorCategory.LITERATURE),

    // ---- 教育学 ----
    EDUCATION_B("教育学（本科）", "040101", false, MajorCategory.EDUCATION),
    EARLY_CHILDHOOD("学前教育", "040106", false, MajorCategory.EDUCATION),
    PRIMARY_EDUCATION("小学教育", "040107", false, MajorCategory.EDUCATION),
    SPECIAL_EDUCATION("特殊教育", "040108", false, MajorCategory.EDUCATION),
    SPORTS_TRAINING("运动训练", "040202", false, MajorCategory.EDUCATION),
    SOCIAL_SPORTS("社会体育指导与管理", "040203", false, MajorCategory.EDUCATION),

    // ---- 艺术学 ----
    MUSIC_PERFORMANCE("音乐表演", "130201", false, MajorCategory.ART),
    MUSICOLOGY_B("音乐学", "130202", false, MajorCategory.ART),
    DANCE_PERFORMANCE("舞蹈表演", "130204", false, MajorCategory.ART),
    VISUAL_COMMUNICATION("视觉传达设计", "130502", false, MajorCategory.ART),
    ENVIRONMENTAL_DESIGN("环境设计", "130503", false, MajorCategory.ART),
    PRODUCT_DESIGN("产品设计", "130504", false, MajorCategory.ART),
    DIGITAL_MEDIA_ART("数字媒体艺术", "130508", false, MajorCategory.ART),
    ANIMATION("动画", "130310", false, MajorCategory.ART),

    // ---- 历史学 ----
    HISTORY_B("历史学", "060101", false, MajorCategory.HISTORY),
    ARCHAEOLOGY_B("考古学（本科）", "060103", false, MajorCategory.HISTORY),
    CULTURAL_HERITAGE("文物与博物馆学", "060104", false, MajorCategory.HISTORY),
    ;

    companion object {
        /**
         * 从持久化值还原专业。
         *
         * 同时认枚举名和中文名：DataStore 里存的是枚举名，但早期版本/手写配置
         * 可能存中文标签；认不出的值退回 [COMPUTER] 而不是抛异常 ——
         * 一个坏字段不该让整个选校页打不开。
         */
        fun fromName(name: String?): Major =
            entries.firstOrNull { it.name == name || it.label == name } ?: COMPUTER

        /** 按门类分组，顺序沿用 [MajorCategory] 的声明顺序；空门类不会出现。 */
        fun byCategory(): Map<MajorCategory, List<Major>> =
            MajorCategory.entries.mapNotNull { cat ->
                entries.filter { it.category == cat }.takeIf { it.isNotEmpty() }
                    ?.let { cat to it }
            }.toMap()

        /** 某个门类下的专业，UI 的门类 chip 切换时直接取这一份。 */
        fun of(category: MajorCategory): List<Major> =
            entries.filter { it.category == category }
    }
}
