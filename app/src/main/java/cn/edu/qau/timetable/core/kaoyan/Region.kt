package cn.edu.qau.timetable.core.kaoyan

/**
 * 大区，用来做「地区意向」筛选。
 *
 * 存的是**省名列表**而不是省份枚举：院校目录里的 `province` 是自由文本
 * （「山东」「山东省」都见过），这里只做包含判断；同时地区意向从来不是
 * 用户最在意的维度，粒度到大区就够，再细会让推荐结果碎成一堆。
 */
enum class Region(val label: String, val provinces: List<String>) {
    EAST("华东", listOf("山东", "江苏", "浙江", "上海", "安徽", "福建", "江西")),
    NORTH("华北", listOf("北京", "天津", "河北", "山西", "内蒙古")),
    NORTHEAST("东北", listOf("辽宁", "吉林", "黑龙江")),
    CENTRAL("华中", listOf("河南", "湖北", "湖南")),
    SOUTH("华南", listOf("广东", "广西", "海南")),
    SOUTHWEST("西南", listOf("四川", "重庆", "贵州", "云南", "西藏")),
    NORTHWEST("西北", listOf("陕西", "甘肃", "青海", "宁夏", "新疆")),
    ANY("不限", emptyList()),
    ;

    companion object {
        /**
         * 从持久化值还原地区。
         *
         * 认不出的值落到 [ANY]：「地区不限」是最宽松的选择，不会误删候选院校，
         * 而错误地收紧地区会让推荐列表直接空掉。
         */
        fun fromName(name: String?): Region =
            entries.firstOrNull { it.name == name || it.label == name } ?: ANY
    }
}
