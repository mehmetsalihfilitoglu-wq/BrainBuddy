package com.brainbuddy.app.classroom

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.AppModeManager
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.ProfileStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.ui.PinLockActivity

class ClassroomActivity : AppCompatActivity() {

    private lateinit var store: TeacherClassStore
    private lateinit var profileStore: ProfileStore

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized && AppModeManager.isParentMode()) {
            when (PendingClassroomAction.action) {
                "join" -> {
                    val code = PendingClassroomAction.joinCode
                    PendingClassroomAction.action = null
                    PendingClassroomAction.joinCode = null
                    if (code != null && store.joinClass(profileStore.getCurrentProfileId(), code)) {
                        Toast.makeText(this, "Sınıfa katıldınız", Toast.LENGTH_SHORT).show()
                        recreate()
                    }
                }
                "leave" -> {
                    val cid = PendingClassroomAction.classId
                    PendingClassroomAction.action = null
                    PendingClassroomAction.classId = null
                    if (cid != null) {
                        store.leaveClass(profileStore.getCurrentProfileId(), cid)
                        Toast.makeText(this, "Sınıftan ayrıldınız", Toast.LENGTH_SHORT).show()
                        recreate()
                    }
                }
                else -> {}
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ProtectionPrefs(this).userLocked()) {
            startActivity(Intent(this, com.brainbuddy.app.LockScreenActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        setContentView(R.layout.activity_classroom)

        store = TeacherClassStore(this)
        profileStore = ProfileStore(this)
        val userId = profileStore.getCurrentProfileId()

        val tvStatus = findViewById<android.widget.TextView>(R.id.tvClassStatus)
        val etJoinCode = findViewById<android.widget.EditText>(R.id.etJoinCode)
        val btnJoin = findViewById<android.widget.Button>(R.id.btnJoinClass)
        val btnLeave = findViewById<android.widget.Button>(R.id.btnLeaveClass)
        val btnCreate = findViewById<android.widget.Button>(R.id.btnCreateClass)
        val tvLeaderboard = findViewById<android.widget.TextView>(R.id.tvLeaderboard)

        fun refresh() {
            val c = store.getClassForStudent(userId)
            if (c != null) {
                tvStatus.text = "Sınıf: ${c.teacherName} (Kod: ${c.classCode})"
                btnJoin.visibility = android.view.View.GONE
                etJoinCode.visibility = android.view.View.GONE
                btnLeave.visibility = android.view.View.VISIBLE
                val leaderboard = store.getWeeklyLeaderboard(c.classId)
                tvLeaderboard.text = leaderboard.joinToString("\n") { "${it.first}: ${it.second} XP" }
                    .ifEmpty { "Henüz veri yok." }
            } else {
                tvStatus.text = "Henüz sınıfa katılmadınız."
                btnJoin.visibility = android.view.View.VISIBLE
                etJoinCode.visibility = android.view.View.VISIBLE
                btnLeave.visibility = android.view.View.GONE
                tvLeaderboard.text = ""
            }
        }

        refresh()

        btnJoin.setOnClickListener {
            val code = etJoinCode.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, "Kodu girin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!AppModeManager.isParentMode()) {
                PendingClassroomAction.action = "join"
                PendingClassroomAction.joinCode = code
                val intent = Intent(this, PinLockActivity::class.java).apply {
                    putExtra(PinLockActivity.EXTRA_MODE, "verify")
                    putExtra(PinLockActivity.EXTRA_TARGET, "ClassroomJoin")
                }
                startActivity(intent)
                finish()
                return@setOnClickListener
            }
            if (store.joinClass(userId, code)) {
                Toast.makeText(this, "Sınıfa katıldınız", Toast.LENGTH_SHORT).show()
                refresh()
            } else Toast.makeText(this, "Geçersiz kod", Toast.LENGTH_SHORT).show()
        }

        btnLeave.setOnClickListener {
            val c = store.getClassForStudent(userId) ?: return@setOnClickListener
            if (!AppModeManager.isParentMode()) {
                PendingClassroomAction.action = "leave"
                PendingClassroomAction.classId = c.classId
                val intent = Intent(this, PinLockActivity::class.java).apply {
                    putExtra(PinLockActivity.EXTRA_MODE, "verify")
                    putExtra(PinLockActivity.EXTRA_TARGET, "ClassroomLeave")
                }
                startActivity(intent)
                finish()
                return@setOnClickListener
            }
            store.leaveClass(userId, c.classId)
            Toast.makeText(this, "Sınıftan ayrıldınız", Toast.LENGTH_SHORT).show()
            refresh()
        }

        btnCreate.visibility = if (AppModeManager.isParentMode()) android.view.View.VISIBLE else android.view.View.GONE
        btnCreate.setOnClickListener {
            if (!ParentAccessGuard.checkAndRedirect(this, javaClass)) return@setOnClickListener
            val c = store.createClass("Öğretmen")
            Toast.makeText(this, "Sınıf oluşturuldu. Kod: ${c.classCode}", Toast.LENGTH_LONG).show()
        }

        findViewById<android.widget.Button>(R.id.btnClassroomBack).setOnClickListener { finish() }
    }
}
