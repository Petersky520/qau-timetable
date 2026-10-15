package cn.edu.qau.timetable.core

/**
 * 计算学分绩点所需的一门课的最小信息。
 *
 * 刻意不复用 `domain.GradeItem` / Room 实体 —— `core/` 是纯 Kotlin 层，
 * 不依赖 Android、也不依赖数据层，这样这套算法才能直接单测。
 */
data class GradeRecord(
    val credit: Double = 0.0,
    val score: Double = 0.0,
    val gpa: Double = 0.0,
)

/** 一批成绩的统计结果。 */
data class GradeStats(
    /** 总学分：所有课程之和（含未通过的）。 */
    val totalCredit: Double = 0.0,
    /** 已获学分：成绩及格的那部分。 */
    val earnedCredit: Double = 0.0,
    /** 平均学分绩点 Σ(绩点×学分) / Σ学分。 */
    val gpa: Double = 0.0,
    /** 加权平均分 Σ(分数×学分) / Σ学分 —— 与绩点是两个不同指标。 */
    val weightedScore: Double = 0.0,
    val courseCount: Int = 0,
    /** 不及格门数（分数缺失的不算）。 */
    val failedCount: Int = 0,
) {
    val hasGpa: Boolean get() = gpa > 0.0
    val hasScore: Boolean get() = weightedScore > 0.0
}

/**
 * 学分绩点统计。
 *
 * ## 两个指标不是一回事
 * 强智的成绩表里同时有「成绩」和「绩点」两列：
 *   · **平均学分绩点** = Σ(绩点 × 学分) / Σ学分 —— 学校评奖评优、保研看的是这个
 *   · **加权平均分**   = Σ(分数 × 学分) / Σ学分 —— 用百分制分数算
 * 早期版本只算了后者、却把它当成"成绩概况"，容易和学分绩点混为一谈，
 * 所以这里两个都给出来、分别标注。
 *
 * ## 为什么分母要单独算
 * 成绩表里常有非数字成绩（「优秀」「良好」「通过」）或压根没有绩点列的课，
 * 解析时这类值会落到 0。如果不把它们排除，平均值会被凭空拉低。
 * 所以参与绩点/分数计算的课程各自单独累计分母，而不是直接用总学分。
 *
 * ## 但挂科的 0 绩点必须留在分母里
 * 这里有个容易搞反的地方：**挂科课程的成绩是 60 以下、绩点通常是 0**，
 * 这个 0 是真实值，不能当成"缺失"排除掉 —— 否则挂一门课反而会让平均学分绩点
 * 变高。判据是「有没有有效的成绩」：
 *   · 有有效分数（>0）→ 这门课确实被打分过，绩点即便是 0 也计入
 *   · 没分数但有绩点   → 也计入（有些页面只有绩点列）
 *   · 两者都没有       → 如「优秀/通过」，整门课不参与计算
 */
object GradeSummary {

    /** 及格线，用于「已获学分」和「不及格门数」。 */
    const val PASS_SCORE = 60.0

    fun of(records: List<GradeRecord>): GradeStats {
        if (records.isEmpty()) return GradeStats()

        // 有分数或绩点之一，就说明这门课被真正评价过
        val graded = records.filter { it.credit > 0 && (it.score > 0 || it.gpa > 0) }
        val scoreBase = records.filter { it.credit > 0 && it.score > 0 }

        val gpaCredit = graded.sumOf { it.credit }
        val scoreCredit = scoreBase.sumOf { it.credit }

        return GradeStats(
            totalCredit = records.sumOf { it.credit },
            earnedCredit = records.filter { it.score >= PASS_SCORE }.sumOf { it.credit },
            gpa = if (gpaCredit > 0) graded.sumOf { it.gpa * it.credit } / gpaCredit else 0.0,
            weightedScore =
                if (scoreCredit > 0) scoreBase.sumOf { it.score * it.credit } / scoreCredit else 0.0,
            courseCount = records.size,
            // 分数缺失（解析成 0）的不算挂科 —— 否则「优秀/通过」会变成不及格
            failedCount = records.count { it.score > 0 && it.score < PASS_SCORE },
        )
    }

    /**
     * 学期名排序：新的在前，空名/读不出格式的排最后。
     *
     * 青农大的学期名形如 `2026-2027-1`，这种格式的字符串比较顺序与时间顺序一致，
     * 所以直接按名字排就够，不需要额外解析。
     */
    fun orderTerms(names: Collection<String>): List<String> =
        names.sortedWith(
            compareByDescending<String> { it.isNotBlank() }.thenByDescending { it }
        )
}
