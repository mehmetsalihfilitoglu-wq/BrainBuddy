package com.brainbuddy.app.avatar

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.GamificationStore
import com.brainbuddy.app.core.ProtectionPrefs

class AvatarShopScreen : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ProtectionPrefs(this).userLocked()) {
            startActivity(android.content.Intent(this, com.brainbuddy.app.LockScreenActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        setContentView(R.layout.activity_avatar_shop)

        val store = AvatarStore(this)
        val gam = GamificationStore(this)

        if (store.isShopDisabledByParent()) {
            findViewById<android.widget.TextView>(R.id.tvShopDisabled).visibility = android.view.View.VISIBLE
            findViewById<android.widget.ScrollView>(R.id.scrollShop).visibility = android.view.View.GONE
        } else {
            findViewById<android.widget.TextView>(R.id.tvShopDisabled).visibility = android.view.View.GONE
            findViewById<android.widget.TextView>(R.id.tvXpBalance).text = "XP: ${gam.xp()}"
            val container = findViewById<android.widget.LinearLayout>(R.id.containerItems)
            container.removeAllViews()
            for (item in store.getAvatarItems()) {
                val row = layoutInflater.inflate(android.R.layout.simple_list_item_2, container, false)
                val t1 = row.findViewById<android.widget.TextView>(android.R.id.text1)
                val t2 = row.findViewById<android.widget.TextView>(android.R.id.text2)
                t1.text = "${item.id} (${item.type.name})"
                t2.text = if (item.unlocked) "Açık" else "${item.priceXP} XP"
                row.setOnClickListener {
                    if (item.unlocked) {
                        store.equipItem(item.type, item.id)
                        Toast.makeText(this, "Eklendi: ${item.id}", Toast.LENGTH_SHORT).show()
                    } else if (store.unlockWithXP(item.id)) {
                        Toast.makeText(this, "Açıldı: ${item.id}", Toast.LENGTH_SHORT).show()
                        recreate()
                    } else Toast.makeText(this, "Yetersiz XP veya mağaza kapalı", Toast.LENGTH_SHORT).show()
                }
                container.addView(row)
            }
        }

        findViewById<android.widget.Button>(R.id.btnShopBack).setOnClickListener { finish() }
    }
}
