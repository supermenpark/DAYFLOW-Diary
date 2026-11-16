package com.supermenpark.dayflowdiary

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("diary_data", MODE_PRIVATE)
    }

    private val paper = Color.rgb(250, 247, 239)
    private val ink = Color.rgb(65, 58, 49)
    private val brown = Color.rgb(145, 112, 82)

    private lateinit var titleInput: EditText
    private lateinit var contentInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showDiary()
    }

    private fun showDiary() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(paper)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(30), dp(22), dp(30))
        }

        scroll.addView(root)

        root.addView(label("DAYFLOW DIARY", 25, true))
        root.addView(label("오늘의 나를 기록하는 공간", 14, false))

        addSpace(root, 28)

        root.addView(label("오늘의 기록", 21, true))

        addSpace(root, 12)

        titleInput = EditText(this).apply {
            hint = "오늘의 제목을 적어보세요"
            textSize = 18f
            setTextColor(ink)
            setHintTextColor(Color.LTGRAY)
            setSingleLine(true)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.WHITE)
        }

        root.addView(titleInput, matchWidth())

        addSpace(root, 14)

        contentInput = EditText(this).apply {
            hint = "오늘 있었던 일, 생각, 마음을 자유롭게 적어보세요.\n\n이곳은 당신만의 작은 기록 공간입니다."
            textSize = 16f
            setTextColor(ink)
            setHintTextColor(Color.GRAY)
            gravity = Gravity.TOP
            minLines = 12
            setPadding(dp(16), dp(18), dp(16), dp(18))
            setBackgroundColor(Color.WHITE)
        }

        root.addView(
            contentInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(300)
            )
        )

        addSpace(root, 22)

        root.addView(actionButton("일기 저장") {
            saveDiary()
        })

        addSpace(root, 10)

        root.addView(actionButton("저장한 기록 보기") {
            showSavedEntries()
        })

        addSpace(root, 10)

        root.addView(actionButton("새 일기 쓰기") {
            titleInput.setText("")
            contentInput.setText("")
        })

        setContentView(scroll)
    }

    private fun saveDiary() {
        val title = titleInput.text.toString().trim()
        val content = contentInput.text.toString().trim()

        if (title.isEmpty() && content.isEmpty()) {
            Toast.makeText(this, "기록할 내용을 입력해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val entries = readEntries()
        val entry = JSONObject().apply {
            put("title", title)
            put("content", content)
            put("date", System.currentTimeMillis())
        }

        entries.put(entry)
        prefs.edit().putString("entries", entries.toString()).apply()

        Toast.makeText(this, "일기를 저장했어요.", Toast.LENGTH_SHORT).show()
    }

    private fun readEntries(): JSONArray {
        val saved = prefs.getString("entries", "[]") ?: "[]"
        return try {
            JSONArray(saved)
        } catch (e: Exception) {
            JSONArray()
        }
    }

    private fun showSavedEntries() {
        val entries = readEntries()

        if (entries.length() == 0) {
            Toast.makeText(this, "저장된 기록이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val items = Array(entries.length()) { index ->
            val item = entries.getJSONObject(entries.length() - 1 - index)
            val title = item.optString("title", "제목 없음")
            val date = java.text.SimpleDateFormat(
                "yyyy.MM.dd HH:mm",
                java.util.Locale.KOREA
            ).format(java.util.Date(item.optLong("date")))
            "$date\n$title"
        }

        AlertDialog.Builder(this)
            .setTitle("나의 기록")
            .setItems(items) { _, which ->
                val item = entries.getJSONObject(entries.length() - 1 - which)
                titleInput.setText(item.optString("title"))
                contentInput.setText(item.optString("content"))
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun actionButton(
        text: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(brown)
            setOnClickListener { action() }
        }
    }

    private fun label(
        text: String,
        size: Int,
        bold: Boolean
    ): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size.toFloat()
            setTextColor(ink)
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(5), 0, dp(5))
        }
    }

    private fun addSpace(root: LinearLayout, height: Int) {
        root.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun matchWidth() =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
