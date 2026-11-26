package cn.edu.qau.timetable

import cn.edu.qau.timetable.data.qz.QzEndpoints.Target
import cn.edu.qau.timetable.ui.KaoyanFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LiteTabContractTest {
    @Test
    fun liteNavigation() {
        assertEquals(listOf("课表", "今日", "成绩"), Tab.available(hasKaoyan = false).map { it.label })
    }

    @Test
    fun fullNavigation() {
        assertEquals(listOf("课表", "今日", "成绩", "考研"), Tab.available(hasKaoyan = true).map { it.label })
    }

    @Test
    fun currentBuildAndSyncTargets() {
        assertEquals(KaoyanFeature.hasKaoyan, Tab.KAOYAN in Tab.available())
        assertFalse(Tab.EXAMS in Tab.available())
        assertEquals(listOf("课表", "成绩"), Target.entries.map { it.label })
    }
}
