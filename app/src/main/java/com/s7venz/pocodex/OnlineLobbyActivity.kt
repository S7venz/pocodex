package com.s7venz.pocodex

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.s7venz.pocodex.data.AppDatabase
import kotlinx.coroutines.launch

class OnlineLobbyActivity : AppCompatActivity() {

    private val equipeDao by lazy { AppDatabase.get(this).equipeDao() }
    private var code: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_online_lobby)

        code = genererCode()
        findViewById<TextView>(R.id.txtCode).text = code

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<LinearLayout>(R.id.btnCreer).setOnClickListener {
            lancer(OnlineCombatActivity.ROLE_HOST, code)
        }
        findViewById<TextView>(R.id.btnRejoindre).setOnClickListener {
            val saisi = findViewById<EditText>(R.id.editCode).text.toString().trim().uppercase()
            if (saisi.length < 4) {
                Toast.makeText(this, "Entre un code valide", Toast.LENGTH_SHORT).show()
            } else {
                lancer(OnlineCombatActivity.ROLE_GUEST, saisi)
            }
        }
    }

    private fun lancer(role: String, code: String) {
        lifecycleScope.launch {
            val monId = equipeDao.tous().firstOrNull()?.id ?: 25 // défaut : Pikachu
            startActivity(
                Intent(this@OnlineLobbyActivity, OnlineCombatActivity::class.java)
                    .putExtra(OnlineCombatActivity.EXTRA_ROLE, role)
                    .putExtra(OnlineCombatActivity.EXTRA_CODE, code)
                    .putExtra(OnlineCombatActivity.EXTRA_MONID, monId),
            )
        }
    }

    private fun genererCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..5).map { chars.random() }.joinToString("")
    }
}
