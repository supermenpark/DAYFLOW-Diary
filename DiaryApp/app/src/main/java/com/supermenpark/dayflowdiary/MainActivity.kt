
package com.supermenpark.dayflowdiary
import android.graphics.BitmapFactory
import android.view.View
import android.widget.FrameLayout
import java.net.URL
import java.util.Random
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("my_diary", MODE_PRIVATE)
    }

    private val entries = mutableListOf<DiaryEntry>()

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var titleInput: EditText
    private lateinit var bodyInput: EditText
    private lateinit var dateText: TextView

    private var selectedId: Long? = null
    private var currentMode = 0

    private val paper = Color.rgb(255, 253, 246)
    private val pageBackground = Color.rgb(244, 240, 229)
    private val ink = Color.rgb(65, 61, 53)
    private val accent = Color.rgb(117, 125, 91)

    data class DiaryEntry(
        val id: Long,
        val date: String,
        val title: String,
        val body: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadEntries()
        showScreen()
    }

    private fun showScreen() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
          setBackgroundColor(this@MainActivity.pageBackground)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(15))
        }

        val logo = TextView(this).apply {
            text = "나의 하루"
            textSize = 25f
            setTextColor(ink)
            typeface = Typeface.create("serif", Typeface.BOLD)
        }

        header.addView(
            logo,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val history = TextView(this).apply {
            text = "기록 보기"
            textSize = 14f
            setTextColor(accent)
            setPadding(dp(12), dp(10), dp(4), dp(10))
            setOnClickListener { showHistory() }
        }

        header.addView(history)
        root.addView(header)

        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(12))
        }

        val menuNames = listOf(
            "일기 쓰기",
            "세로 메모지",
            "포스트잇 모아보기"
        )

        menuNames.forEachIndexed { index, name ->
            val button = TextView(this).apply {
                text = name
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(12), dp(10), dp(12))
                setTextColor(
                    if (currentMode == index) Color.WHITE
                    else accent
                )
                background = rounded(
                    if (currentMode == index) accent
                    else paper,
                    12
                )
                setOnClickListener {
                    currentMode = index
                    showScreen()
                }
            }

            val params = LinearLayout.LayoutParams(
                0, dp(43), 1f
            )
            params.setMargins(dp(3), 0, dp(3), 0)
            menu.addView(button, params)
        }

        root.addView(menu)

        val scroll = ScrollView(this)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(8), dp(17), dp(30))
        }

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        setContentView(root)

        when (currentMode) {
            0 -> buildDiary(content)
            1 -> buildMemoCollection(content, false)
            2 -> buildMemoCollection(content, true)
        }
    }

    private fun buildDiary(
        target: LinearLayout,
        entry: DiaryEntry? = null
    ) {
        target.removeAllViews()
        selectedId = entry?.id

        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(23), dp(25), dp(23), dp(25))
            setBackground(rounded(paper, 8))
            elevation = dp(3).toFloat()
        }

        target.addView(
            sheet,
            LinearLayout.LayoutParams(-1, -2)
        )

        val smallTitle = TextView(this).apply {
            text = "TODAY'S DIARY"
            textSize = 11f
            letterSpacing = 0.15f
            setTextColor(accent)
        }

        sheet.addView(smallTitle)

        dateText = TextView(this).apply {
            text = entry?.date ?: today()
            textSize = 14f
            setTextColor(Color.GRAY)
            setPadding(0, dp(15), 0, dp(20))
        }

        sheet.addView(dateText)

        titleInput = EditText(this).apply {
            hint = "오늘의 제목을 적어보세요"
            setText(entry?.title ?: "")
            textSize = 23f
            setTextColor(ink)
            setHintTextColor(Color.LTGRAY)
            typeface = Typeface.create("serif", Typeface.BOLD)
            background = null
            setSingleLine(true)
            setPadding(0, dp(8), 0, dp(15))
        }

        sheet.addView(titleInput)

        val divider = android.view.View(this).apply {
            setBackgroundColor(Color.rgb(225, 220, 207))
        }

        sheet.addView(
            divider,
            LinearLayout.LayoutParams(-1, dp(1))
        )

        bodyInput = EditText(this).apply {
            hint = "오늘 있었던 일들을 자유롭게 적어보세요.\n\n기억하고 싶은 순간도 좋고,\n마음속에 담아둔 이야기도 좋습니다.\n\n이곳은 나만의 작은 기록 공간입니다."
            setText(entry?.body ?: "")
            textSize = 17f
            setTextColor(ink)
            setHintTextColor(Color.rgb(174, 168, 153))
            gravity = Gravity.TOP
            minLines = 15
            background = null
            setPadding(0, dp(20), 0, dp(15))
            setLineSpacing(dp(5).toFloat(), 1f)
        }

        sheet.addView(bodyInput)

        val save = Button(this).apply {
            text = "기록 저장하기"
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.WHITE)
            background = rounded(accent, 10)
            setOnClickListener { saveDiary() }
        }

        sheet.addView(
            save,
            LinearLayout.LayoutParams(-1, dp(52))
        )

        val newDiary = TextView(this).apply {
            text = "+ 새 일기 쓰기"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(accent)
            setPadding(dp(10), dp(22), dp(10), dp(15))
            setOnClickListener {
                buildDiary(target)
            }
        }

        target.addView(newDiary)
    }

    private fun saveDiary() {
        val title = titleInput.text.toString().trim()
        val body = bodyInput.text.toString().trim()

        if (title.isEmpty() && body.isEmpty()) {
            Toast.makeText(
                this,
                "내용을 입력해 주세요.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val id = selectedId ?: System.currentTimeMillis()

        val entry = DiaryEntry(
            id,
            dateText.text.toString(),
            title,
            body
        )

        val index = entries.indexOfFirst { it.id == id }

        if (index >= 0) {
            entries[index] = entry
        } else {
            entries.add(0, entry)
        }

        persistEntries()
        selectedId = id

        Toast.makeText(
            this,
            "일기가 저장되었습니다.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun buildMemoCollection(
        target: LinearLayout,
        sticky: Boolean
    ) {
        target.removeAllViews()

        val heading = TextView(this).apply {
            text = if (sticky) "나의 작은 기록들"
            else "소중한 하루의 기록"
            textSize = 21f
            typeface = Typeface.create("serif", Typeface.BOLD)
            setTextColor(ink)
            setPadding(dp(5), dp(10), dp(5), dp(8))
        }

        target.addView(heading)

        val description = TextView(this).apply {
            text = if (sticky)
                "메모지를 좌우로 넘겨 기록을 살펴보세요."
            else
                "아이보리 종이 위에 쌓인 나의 일기입니다."
            textSize = 13f
            setTextColor(Color.GRAY)
            setPadding(dp(5), 0, dp(5), dp(18))
        }

        target.addView(description)

        val add = TextView(this).apply {
            text = "+ 새 일기 작성하기"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(accent)
            setPadding(dp(12), dp(13), dp(12), dp(13))
            background = rounded(paper, 10)
            setOnClickListener {
                currentMode = 0
                showScreen()
            }
        }

        target.addView(
            add,
            LinearLayout.LayoutParams(-1, -2)
        )

        if (entries.isEmpty()) {
            val empty = TextView(this).apply {
                text = "\n\n아직 기록이 없습니다.\n첫 번째 하루를 기록해 보세요."
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(Color.GRAY)
                setPadding(dp(10), dp(35), dp(10), dp(35))
            }
            target.addView(empty)
            return
        }

        val horizontal = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(15), 0, dp(15))
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }

        entries.forEachIndexed { index, entry ->

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.TOP
                setPadding(
                    dp(17), dp(20), dp(17), dp(17)
                )

                val cardColor = if (sticky) {
                    when (index % 4) {
                        0 -> Color.rgb(255, 242, 190)
                        1 -> Color.rgb(230, 239, 215)
                        2 -> Color.rgb(246, 226, 218)
                        else -> Color.rgb(232, 231, 245)
                    }
                } else {
                    paper
                }

                setBackground(
                    rounded(cardColor, if (sticky) 5 else 8)
                )

                elevation = dp(3).toFloat()

                setOnClickListener {
                    currentMode = 0
                    showScreen()
                    buildDiary(content, entry)
                }
            }

            val date = TextView(this).apply {
                text = entry.date
                textSize = 11f
                setTextColor(accent)
            }

            card.addView(date)

            val title = TextView(this).apply {
                text = entry.title.ifBlank { "제목 없는 일기" }
                textSize = if (sticky) 16f else 19f
                typeface = Typeface.create(
                    "serif", Typeface.BOLD
                )
                setTextColor(ink)
                setPadding(0, dp(13), 0, dp(12))
                maxLines = 2
            }

            card.addView(title)

            val body = TextView(this).apply {
                text = entry.body.ifBlank { "내용 없음" }
                textSize = 13f
                setTextColor(ink)
                maxLines = if (sticky) 7 else 12
                setLineSpacing(dp(3).toFloat(), 1f)
            }

            card.addView(body)

            val open = TextView(this).apply {
                text = "기록 열기  →"
                textSize = 12f
                setTextColor(accent)
                setPadding(0, dp(15), 0, 0)
            }

            card.addView(open)

            val width = if (sticky) 155 else 245
            val height = if (sticky) 230 else 340

            val params = LinearLayout.LayoutParams(
                dp(width), dp(height)
            )

            params.setMargins(
                dp(5), dp(5), dp(12), dp(10)
            )

            row.addView(card, params)
        }

        horizontal.addView(row)

        target.addView(
            horizontal,
            LinearLayout.LayoutParams(-1, -2)
        )
    }

    private fun showHistory() {
        if (entries.isEmpty()) {
            AlertDialog.Builder(this)
                .setMessage("아직 작성한 일기가 없습니다.")
                .setPositiveButton("확인", null)
                .show()
            return
        }

        val labels = entries.map {
            val name = if (it.title.isBlank()) {
                "제목 없는 일기"
            } else {
                it.title
            }
            "${it.date}  ·  $name"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("나의 일기")
            .setItems(labels) { _, which ->
                currentMode = 0
                showScreen()
                buildDiary(content, entries[which])
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun loadEntries() {
        entries.clear()

        try {
            val data = JSONArray(
                prefs.getString("entries", "[]")
            )

            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)

                entries.add(
                    DiaryEntry(
                        item.getLong("id"),
                        item.getString("date"),
                        item.getString("title"),
                        item.getString("body")
                    )
                )
            }
        } catch (_: Exception) {
            entries.clear()
        }
    }

    private fun persistEntries() {
        val array = JSONArray()

        entries.forEach {
            array.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("date", it.date)
                    put("title", it.title)
                    put("body", it.body)
                }
            )
        }

        prefs.edit()
            .putString("entries", array.toString())
            .apply()
    }

    private fun today(): String {
        return SimpleDateFormat(
            "yyyy년 M월 d일 E요일",
            Locale.KOREAN
        ).format(Date())
    }

    private fun rounded(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }
}
