package com.brainbuddy.app.reward

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.ParentAccessGuard

class RewardContractActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this)) return

        setContentView(R.layout.activity_reward_contract)

        val store = RewardContractStore(this)

        fun refresh() {
            val list = store.getContracts()
            val container = findViewById<android.widget.LinearLayout>(R.id.containerContracts)
            container.removeAllViews()
            for (c in list) {
                val row = layoutInflater.inflate(android.R.layout.simple_list_item_2, container, false)
                val t1 = row.findViewById<android.widget.TextView>(android.R.id.text1)
                val t2 = row.findViewById<android.widget.TextView>(android.R.id.text2)
                t1.text = "${c.targetXP} XP: ${c.description}"
                t2.text = if (c.completed) "Tamamlandı ✓" else "Bekliyor"
                row.setOnLongClickListener {
                    if (!c.completed) {
                        AlertDialog.Builder(this)
                            .setTitle("Ödül Teslim Edildi?")
                            .setMessage(c.description)
                            .setPositiveButton("Evet") { _, _ ->
                                store.markCompleted(c.id)
                                refresh()
                            }
                            .setNegativeButton("Hayır", null)
                            .show()
                    }
                    true
                }
                container.addView(row)
            }
        }

        refresh()

        findViewById<android.widget.Button>(R.id.btnAddContract).setOnClickListener {
            val etXp = findViewById<android.widget.EditText>(R.id.etTargetXp)
            val etDesc = findViewById<android.widget.EditText>(R.id.etDescription)
            val xp = etXp.text.toString().toIntOrNull() ?: 0
            val desc = etDesc.text.toString().trim()
            if (xp > 0 && desc.isNotEmpty()) {
                store.addContract(xp, desc)
                etXp.setText("")
                etDesc.setText("")
                refresh()
            }
        }

        findViewById<android.widget.Button>(R.id.btnRewardBack).setOnClickListener { finish() }
    }
}
