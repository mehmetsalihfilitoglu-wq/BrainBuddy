package com.mioacademy.app.reward

import android.content.Context
import com.mioacademy.app.core.ProfileScopedPrefs
import org.json.JSONArray
import org.json.JSONObject

data class RewardContract(
    val id: String,
    val targetXP: Int,
    val description: String,
    val completed: Boolean
)

class RewardContractStore(context: Context) {
    private val prefs = ProfileScopedPrefs.rewardContracts(context)
    private val gamification = com.mioacademy.app.core.GamificationStore(context)

    fun getContracts(): List<RewardContract> {
        val arr = prefs.getString(KEY_CONTRACTS, "[]") ?: "[]"
        return try {
            val ja = JSONArray(arr)
            (0 until ja.length()).mapNotNull {
                val o = ja.getJSONObject(it)
                RewardContract(
                    o.optString("id", ""),
                    o.optInt("targetXP", 0),
                    o.optString("description", ""),
                    o.optBoolean("completed", false)
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun addContract(targetXP: Int, description: String) {
        val list = getContracts().toMutableList()
        val id = "rc_${System.currentTimeMillis()}"
        list.add(RewardContract(id, targetXP, description, false))
        saveContracts(list)
    }

    fun markCompleted(id: String) {
        val list = getContracts().map { if (it.id == id) it.copy(completed = true) else it }
        saveContracts(list)
    }

    fun getPendingReached(): List<RewardContract> {
        val xp = gamification.xp()
        return getContracts().filter { !it.completed && xp >= it.targetXP }
    }

    private fun saveContracts(list: List<RewardContract>) {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject()
                .put("id", c.id)
                .put("targetXP", c.targetXP)
                .put("description", c.description)
                .put("completed", c.completed))
        }
        prefs.edit().putString(KEY_CONTRACTS, arr.toString()).apply()
    }

    companion object {
        private const val KEY_CONTRACTS = "contracts_json"
    }
}
