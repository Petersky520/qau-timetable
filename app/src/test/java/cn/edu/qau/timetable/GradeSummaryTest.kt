package cn.edu.qau.timetable

import cn.edu.qau.timetable.core.GradeRecord
import cn.edu.qau.timetable.core.GradeSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 学分绩点统计。
 *
 * 这里是「算错了也不会报错、只会给学生一个错数字」的那类逻辑，
 * 所以每个分母的取舍都单独钉一条用例 —— 尤其是挂科的 0 绩点该不该进分母。
 */
class GradeSummaryTest {

    private fun rec(credit: Double, score: Double, gpa: Double) =
        GradeRecord(credit = credit, score = score, gpa = gpa)

    // ---------------------------------------------------------- 基本公式

    /** 平均学分绩点 = Σ(绩点×学分)/Σ学分。 */
    @Test
    fun `平均学分绩点按学分加权`() {
        val s = GradeSummary.of(listOf(rec(5.0, 85.0, 3.5), rec(3.0, 92.0, 4.0)))
        // (3.5*5 + 4.0*3) / 8 = 29.5 / 8
        assertEquals(3.6875, s.gpa, 0.0001)
    }

    /** 加权平均分用的是分数，和绩点是两个指标，不能混。 */
    @Test
    fun `加权平均分与绩点分开计算`() {
        val s = GradeSummary.of(listOf(rec(5.0, 85.0, 3.5), rec(3.0, 92.0, 4.0)))
        // (85*5 + 92*3) / 8 = 701 / 8
        assertEquals(87.625, s.weightedScore, 0.0001)
        assertEquals(8.0, s.totalCredit, 0.0001)
        assertEquals(0, s.failedCount)
    }

    // ---------------------------------------------------------- 分母的取舍

    /**
     * 回归：挂科课程的绩点是 **0**，这个 0 是真实值、必须留在分母里。
     *
     * 早期把「绩点 > 0」当作参与条件，结果挂一门课反而让平均学分绩点变高 ——
     * 分母少了一门课的学分，分子却没少。
     */
    @Test
    fun `挂科的零绩点必须计入分母`() {
        val s = GradeSummary.of(
            listOf(
                rec(5.0, 85.0, 3.5),
                rec(3.0, 92.0, 4.0),
                rec(1.0, 55.0, 0.0), // 体育挂科，绩点 0
            )
        )
        // 若错误地排除挂科：(17.5+12)/8 = 3.6875（偏高）
        // 正确：(17.5+12+0)/9 = 3.2778
        assertEquals(3.2778, s.gpa, 0.0001)
        assertEquals(1, s.failedCount)
        assertEquals(9.0, s.totalCredit, 0.0001)
    }

    /** 「优秀/通过」这类没有数字成绩的课完全不参与计算，也不会被当成挂科。 */
    @Test
    fun `非数字成绩不参与计算`() {
        val s = GradeSummary.of(
            listOf(
                rec(5.0, 85.0, 3.5),
                rec(2.0, 0.0, 0.0), // 军训，成绩是「优秀」
            )
        )
        assertEquals(3.5, s.gpa, 0.0001)       // 军训不进分母
        assertEquals(7.0, s.totalCredit, 0.0001)
        assertEquals(5.0, s.earnedCredit, 0.0001)
        assertEquals(0, s.failedCount)
        assertEquals(2, s.courseCount)
    }

    /** 有些页面只有绩点列、没有分数列，这时仍应按绩点统计。 */
    @Test
    fun `只有绩点没有分数也能算`() {
        val s = GradeSummary.of(listOf(rec(2.0, 0.0, 3.0)))
        assertEquals(3.0, s.gpa, 0.0001)
        assertEquals(0.0, s.weightedScore, 0.0001) // 没有分数就算不出加权平均分
    }

    // ---------------------------------------------------------- 学分

    /** 已获学分只算及格的：挂科的学分计入总学分、不计入已获。 */
    @Test
    fun `已获学分排除挂科`() {
        val s = GradeSummary.of(
            listOf(rec(5.0, 85.0, 3.5), rec(3.0, 55.0, 0.0), rec(2.0, 90.0, 4.0))
        )
        assertEquals(10.0, s.totalCredit, 0.0001)
        assertEquals(7.0, s.earnedCredit, 0.0001)
    }

    /** 60 分是及格线，含 60。 */
    @Test
    fun `六十分算及格`() {
        val s = GradeSummary.of(listOf(rec(4.0, 60.0, 1.0)))
        assertEquals(4.0, s.earnedCredit, 0.0001)
        assertEquals(0, s.failedCount)
    }

    // ---------------------------------------------------------- 边界

    @Test
    fun `空输入返回全零`() {
        val s = GradeSummary.of(emptyList())
        assertEquals(0.0, s.gpa, 0.0001)
        assertEquals(0.0, s.totalCredit, 0.0001)
        assertEquals(0, s.courseCount)
    }

    /** 学分为 0（如某些实践环节）时不能除零。 */
    @Test
    fun `学分为零不会除零`() {
        val s = GradeSummary.of(listOf(rec(0.0, 90.0, 4.0)))
        assertEquals(0.0, s.gpa, 0.0001)
        assertEquals(0.0, s.weightedScore, 0.0001)
    }

    // ---------------------------------------------------------- 学期排序

    /** 学期名 `2026-2027-1` 这种格式，字符串顺序与时间顺序一致，新的在前。 */
    @Test
    fun `学期按新的在前排序空格在最后`() {
        val ordered = GradeSummary.orderTerms(
            listOf("2025-2026-2", "", "2026-2027-1", "2025-2026-1")
        )
        assertEquals(
            listOf("2026-2027-1", "2025-2026-2", "2025-2026-1", ""),
            ordered,
        )
    }
}
