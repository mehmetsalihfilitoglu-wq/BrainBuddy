package com.brainbuddy.app.classroom

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TeacherClass(
    val classId: String,
    val teacherName: String,
    val classCode: String,
    val studentUserIds: List<String>
)

interface TeacherClassRepository {
    fun createClass(teacherName: String): TeacherClass
    fun getClassByCode(code: String): TeacherClass?
    fun joinClass(userId: String, code: String): Boolean
    fun leaveClass(userId: String, classId: String): Boolean
    fun getClassForStudent(userId: String): TeacherClass?
    fun getWeeklyLeaderboard(classId: String): List<Pair<String, Int>>
}

/** Local store for classroom. Scaffold for future server sync. */
class TeacherClassStore(private val context: Context) : TeacherClassRepository {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val analytics = com.brainbuddy.app.core.AnalyticsStore(context)
    private val gamification = com.brainbuddy.app.core.GamificationStore(context)
    private val profileStore = com.brainbuddy.app.core.ProfileStore(context)

    override fun createClass(teacherName: String): TeacherClass {
        val code = generateJoinCode()
        val classId = "class_${System.currentTimeMillis()}"
        val c = TeacherClass(classId, teacherName, code, emptyList())
        saveClass(c)
        return c
    }

    override fun getClassByCode(code: String): TeacherClass? =
        getAllClasses().find { it.classCode.equals(code, ignoreCase = true) }

    override fun joinClass(userId: String, code: String): Boolean {
        val c = getClassByCode(code) ?: return false
        if (userId in c.studentUserIds) return true
        val updated = c.copy(studentUserIds = c.studentUserIds + userId)
        saveClass(updated)
        setStudentClass(userId, c.classId)
        return true
    }

    override fun leaveClass(userId: String, classId: String): Boolean {
        val c = getClass(classId) ?: return false
        val updated = c.copy(studentUserIds = c.studentUserIds - userId)
        if (updated.studentUserIds.isEmpty()) deleteClass(classId)
        else saveClass(updated)
        if (getStudentClassId(userId) == classId) clearStudentClass(userId)
        return true
    }

    override fun getClassForStudent(userId: String): TeacherClass? {
        val id = getStudentClassId(userId) ?: return null
        return getClass(id)
    }

    override fun getWeeklyLeaderboard(classId: String): List<Pair<String, Int>> {
        val c = getClass(classId) ?: return emptyList()
        val weekStart = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis()) / 7 * 7
        val weekMs = weekStart * java.util.concurrent.TimeUnit.DAYS.toMillis(1)
        val sessions = analytics.getSessions().filter { it.tsMs >= weekMs }
        val weeklyXp = sessions.sumOf { it.pointsEarned }
        val profileMap = profileStore.getProfiles().associateBy { it.id }
        return c.studentUserIds.map { id ->
            (profileMap[id]?.name ?: id) to if (id == profileStore.getCurrentProfileId()) weeklyXp else 0
        }.sortedByDescending { it.second }
    }

    fun getTeacherReport(classId: String): List<TeacherReportRow> {
        val c = getClass(classId) ?: return emptyList()
        val stats = analytics.getUserStats()
        val weakTopics = analytics.getWeakestTopicsWithCounts(5).map { it.first }
        val profileMap = profileStore.getProfiles().associateBy { it.id }
        val userId = profileStore.getCurrentProfileId()
        if (userId !in c.studentUserIds) return emptyList()
        return listOf(
            TeacherReportRow(
                profileMap[userId]?.name ?: userId,
                stats.overallCorrect,
                stats.overallTotal,
                weakTopics,
                analytics.getTestPerformances().size
            )
        )
    }

    data class TeacherReportRow(
        val studentName: String,
        val correctCount: Int,
        val totalQuizzes: Int,
        val weakTopics: List<String>,
        val quizCount: Int
    )

    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    private fun saveClass(c: TeacherClass) {
        val existing = getAllClasses().filter { it.classId != c.classId }
        val arr = JSONArray()
        (existing + c).forEach { cls ->
            arr.put(JSONObject()
                .put("classId", cls.classId)
                .put("teacherName", cls.teacherName)
                .put("classCode", cls.classCode)
                .put("studentUserIds", JSONArray(cls.studentUserIds)))
        }
        prefs.edit().putString(KEY_CLASSES, arr.toString()).apply()
    }

    private fun getClass(classId: String): TeacherClass? =
        getAllClasses().find { it.classId == classId }

    private fun getAllClasses(): List<TeacherClass> {
        val arr = JSONArray(prefs.getString(KEY_CLASSES, "[]"))
        return (0 until arr.length()).mapNotNull {
            try {
                val o = arr.getJSONObject(it)
                val ids = o.optJSONArray("studentUserIds") ?: JSONArray()
                TeacherClass(
                    o.optString("classId", ""),
                    o.optString("teacherName", ""),
                    o.optString("classCode", ""),
                    (0 until ids.length()).map { i -> ids.getString(i) }
                )
            } catch (_: Exception) { null }
        }
    }

    private fun deleteClass(classId: String) {
        val list = getAllClasses().filter { it.classId != classId }
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject()
                .put("classId", c.classId)
                .put("teacherName", c.teacherName)
                .put("classCode", c.classCode)
                .put("studentUserIds", JSONArray(c.studentUserIds)))
        }
        prefs.edit().putString(KEY_CLASSES, arr.toString()).apply()
    }

    private fun setStudentClass(userId: String, classId: String) {
        prefs.edit().putString("${KEY_STUDENT_CLASS}_$userId", classId).apply()
    }

    private fun getStudentClassId(userId: String): String? =
        prefs.getString("${KEY_STUDENT_CLASS}_$userId", null)?.takeIf { it.isNotEmpty() }

    private fun clearStudentClass(userId: String) {
        prefs.edit().remove("${KEY_STUDENT_CLASS}_$userId").apply()
    }

    companion object {
        private const val PREFS = "bb_teacher_class"
        private const val KEY_CLASSES = "classes_json"
        private const val KEY_STUDENT_CLASS = "student_class"
    }
}
