
package com.supermenpark.dayflowdiary

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("diary_data", MODE_PRIVATE)
    }

    private val paper = Color.rgb(247, 243, 234)
    private val ink = Color.rgb(62, 54, 45)
    private val muted = Color.rgb(137, 126, 111)
    private val brown = Color.rgb(139, 105, 76)

    private lateinit var titleInput: EditText
    private lateinit var contentInput: EditText

    private var selectedMood = "평온"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showDiary()
    }

    private fun showDiary() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(paper)
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(26), dp(22), dp(32))
        }

        scroll.addView(root)

        val top = row()
        top.addView(
            label("DAYFLOW", 15, true, brown),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        top.addView(label("D I A R Y", 12, true, muted))
        root.addView(top)

        space(root, 20)

        root.addView(label("나의 하루를 담는", 16, false, muted))
        root.addView(label("작은 기록장.", 31, true, ink))

        space(root, 8)
        root.addView(label(todayString(), 14, true, brown))

        space(root, 20)

        val quote = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(19), dp(20), dp(19))
            background = rounded(Color.rgb(232, 222, 205), 18)
        }

        quote.addView(
            label("TODAY'S NOTE · 오늘의 한마디", 11, true, brown)
        )

        space(quote, 10)

        quote.addView(
            label(
                "천천히 가도 괜찮아요.\n오늘의 나도 충분히 애썼으니까요.",
                18, true, ink
            )
        )

        space(quote, 7)
        quote.addView(
            label("하루의 작은 순간도 소중하게 기록해요.", 12, false, muted)
        )

        root.addView(quote)

        space(root, 24)

        root.addView(label("오늘의 기록", 21, true, ink))
        space(root, 12)

        titleInput = EditText(this).apply {
            hint = "오늘을 한마디로 표현한다면"
            textSize = 17f
            setTextColor(ink)
            setHintTextColor(Color.LTGRAY)
            setSingleLine(true)
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = rounded(Color.WHITE, 13)
        }

        root.addView(titleInput, matchWidth())
        space(root, 12)

        contentInput = EditText(this).apply {
            hint = "오늘 있었던 일, 떠오른 생각,\n마음속 이야기를 편하게 적어보세요.\n\n잘 쓰려고 애쓰지 않아도 괜찮아요.\n있는 그대로의 오늘이면 충분합니다."
            textSize = 16f
            setTextColor(ink)
            setHintTextColor(Color.GRAY)
            gravity = Gravity.TOP
            minLines = 9
            setPadding(dp(17), dp(18), dp(17), dp(18))
            background = rounded(Color.WHITE, 13)
        }

        root.addView(
            contentInput,
            LinearLayout.LayoutParams(-1, dp(245))
        )

        space(root, 18)

        root.addView(label("오늘의 기분은 어땠나요?", 14, true, ink))
        space(root, 10)

        val moods = row()

        listOf("평온", "행복", "감사", "피곤", "복잡").forEach { mood ->
            val chip = TextView(this).apply {
                text = mood
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(
                    if (selectedMood == mood) Color.WHITE else brown
                )
                setPadding(dp(12), dp(9), dp(12), dp(9))
                background = rounded(
                    if (selectedMood == mood) brown else Color.WHITE,
                    30
                )

                setOnClickListener {
                    selectedMood = mood
                    showDiary()
                }
            }

            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.marginEnd = dp(5)
            moods.addView(chip, lp)
        }

        root.addView(moods)

        space(root, 24)

        root.addView(primaryButton("기록 저장하기") {
            saveDiary()
        })

        space(root, 9)

        root.addView(secondaryButton("저장한 일기 모아보기") {
            showSavedEntries()
        })

        space(root, 9)

        root.addView(secondaryButton("새 일기 작성") {
            titleInput.setText("")
            contentInput.setText("")
            selectedMood = "평온"
            showDiary()
        })

        space(root, 20)

        root.addView(
            label("A LITTLE SPACE FOR YOURSELF", 10, true, muted).apply {
                gravity = Gravity.CENTER
            }
        )

        setContentView(scroll)
    }

    private fun saveDiary() {
        val title = titleInput.text.toString().trim()
        val content = contentInput.text.toString().trim()

        if (title.isEmpty() && content.isEmpty()) {
            Toast.makeText(
                this,
                "오늘의 기록을 먼저 적어주세요.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val entries = readEntries()

        entries.put(JSONObject().apply {
            put("title", title)
            put("content", content)
            put("date", System.currentTimeMillis())
            put("mood", selectedMood)
        })

        prefs.edit().putString("entries", entries.toString()).apply()

        Toast.makeText(
            this,
            "오늘의 기록을 소중히 저장했어요.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun readEntries(): JSONArray {
        return try {
            JSONArray(prefs.getString("entries", "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
    }

    private fun showSavedEntries() {
        val entries = readEntries()

        if (entries.length() == 0) {
            Toast.makeText(
                this,
                "아직 저장한 일기가 없어요.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val items = Array(entries.length()) { index ->
            val item = entries.getJSONObject(
                entries.length() - 1 - index
            )

            val date = SimpleDateFormat(
                "yyyy.MM.dd",
                Locale.KOREA
            ).format(Date(item.optLong("date")))

            "$date · ${item.optString("title", "제목 없는 기록")}"
        }

        AlertDialog.Builder(this)
            .setTitle("나의 하루 모음")
            .setItems(items) { _, which ->
                val item = entries.getJSONObject(
                    entries.length() - 1 - which
                )

                val savedTitle = item.optString("title")
                val savedContent = item.optString("content")

                selectedMood = item.optString("mood", "평온")
                showDiary()

                titleInput.setText(savedTitle)
                contentInput.setText(savedContent)
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun primaryButton(
        text: String,
        action: () -> Unit
    ) = Button(this).apply {
        this.text = text
        textSize = 16f
        isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        background = rounded(brown, 14)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setOnClickListener { action() }
    }

    private fun secondaryButton(
        text: String,
        action: () -> Unit
    ) = Button(this).apply {
        this.text = text
        textSize = 14f
        isAllCaps = false
        setTextColor(brown)
        background = rounded(Color.rgb(239, 232, 220), 14)
        setOnClickListener { action() }
    }

    private fun label(
        text: String,
        size: Int,
        bold: Boolean,
        color: Int
    ) = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(color)

        if (bold) {
            setTypeface(null, Typeface.BOLD)
        }

        setLineSpacing(dp(3).toFloat(), 1f)
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun rounded(
        color: Int,
        radius: Int
    ) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun space(parent: LinearLayout, height: Int) {
        parent.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(height))
        )
    }

    private fun todayString(): String {
        return SimpleDateFormat(
            "yyyy년 M월 d일 EEEE",
            Locale.KOREA
        ).format(Date())
    }

    private fun matchWidth() =
        LinearLayout.LayoutParams(-1, -2)

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
