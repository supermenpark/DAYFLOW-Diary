package com.supermenpark.dayflowdiary

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import android.widget.FrameLayout
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class MainActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("my_diary", MODE_PRIVATE)
    }

    private val entries = mutableListOf<DiaryEntry>()

    private lateinit var root: FrameLayout
    private lateinit var appContainer: LinearLayout
    private lateinit var drawerPanel: LinearLayout
    private lateinit var scrim: View

    private lateinit var content: LinearLayout
    private lateinit var titleInput: EditText
    private lateinit var bodyInput: EditText
    private lateinit var dateText: TextView

    private var selectedId: Long? = null
    private var currentMode = 0
    private var drawerOpen = false

    private var touchStartX = 0f
    private var touchStartY = 0f

    private val paper = Color.rgb(255, 253, 246)
    private val pageBackground = Color.rgb(244, 240, 229)
    private val ink = Color.rgb(65, 61, 53)
    private val accent = Color.rgb(117, 125, 91)
    private val drawerColor = Color.rgb(49, 55, 49)

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

    // 화면 가장자리 밀기로 메뉴 열고 닫기
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {

        when (event.action) {

            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.rawX
                touchStartY = event.rawY
            }

            MotionEvent.ACTION_UP -> {

                val dx = event.rawX - touchStartX
                val dy = event.rawY - touchStartY

                if (
                    kotlin.math.abs(dx) > dp(65) &&
                    kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.3f
                ) {

                    if (
                        !drawerOpen &&
                        touchStartX < dp(30) &&
                        dx > 0
                    ) {
                        openDrawer()
                    }

                    else if (
                        drawerOpen &&
                        dx < 0 &&
                        touchStartX < dp(290)
                    ) {
                        closeDrawer()
                    }
                }
            }
        }

        return super.dispatchTouchEvent(event)
    }

    private fun showScreen() {

        root = FrameLayout(this).apply {
            setBackgroundColor(pageBackground)
        }

        appContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBackground)
        }

        root.addView(
            appContainer,
            FrameLayout.LayoutParams(-1, -1)
        )

        val header = FrameLayout(this).apply {
            background = rounded(Color.rgb(115, 125, 104), 14)
            clipToOutline = true
        }

        val bannerImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(115, 125, 104))
        }

        header.addView(
            bannerImage,
            FrameLayout.LayoutParams(-1, -1)
        )

        val shade = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(
                    0x99000000.toInt(),
                    0x22000000,
                    0x44000000
                )
            )
        }

        header.addView(
            shade,
            FrameLayout.LayoutParams(-1, -1)
        )

        val logo = TextView(this).apply {
            text = "나의 하루"
            textSize = 27f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("serif", Typeface.BOLD)
            setShadowLayer(5f, 0f, 2f, Color.BLACK)
        }

        val logoParams = FrameLayout.LayoutParams(
            -2, -2, Gravity.BOTTOM or Gravity.START
        )
        logoParams.setMargins(dp(20), 0, dp(12), dp(19))
        header.addView(logo, logoParams)

        val history = TextView(this).apply {
            text = "기록 보기  ›"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(9), dp(5), dp(9))
            setOnClickListener { showHistory() }
        }

        val historyParams = FrameLayout.LayoutParams(
            -2, -2, Gravity.TOP or Gravity.END
        )
        historyParams.setMargins(0, dp(10), dp(13), 0)
        header.addView(history, historyParams)

        val headerParams = LinearLayout.LayoutParams(-1, dp(165))
        headerParams.setMargins(dp(14), dp(12), dp(14), dp(12))
        appContainer.addView(header, headerParams)

        loadDailyBanner(bannerImage)

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
                    if (currentMode == index) Color.WHITE else accent
                )

                background = rounded(
                    if (currentMode == index) accent else paper,
                    12
                )

                setOnClickListener {
                    currentMode = index
                    showScreen()
                }
            }

            val params = LinearLayout.LayoutParams(0, dp(43), 1f)
            params.setMargins(dp(3), 0, dp(3), 0)
            menu.addView(button, params)
        }

        appContainer.addView(menu)

        val scroll = ScrollView(this)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(8), dp(17), dp(30))
        }

        scroll.addView(content)

        appContainer.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        setContentView(root)

        when (currentMode) {
            0 -> buildDiary(content)
            1 -> buildMemoCollection(content, false)
            2 -> buildMemoCollection(content, true)
        }

        buildDrawer()
    }

    // 슬라이드 메뉴 만들기
    private fun buildDrawer() {

        scrim = View(this).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
            visibility = View.GONE
            setOnClickListener {
                closeDrawer()
            }
        }

        root.addView(
            scrim,
            FrameLayout.LayoutParams(-1, -1)
        )

        drawerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(35), dp(20), dp(20))
            setBackgroundColor(drawerColor)
            elevation = dp(15).toFloat()
        }

        val drawerParams = FrameLayout.LayoutParams(
            dp(270),
            -1,
            Gravity.START
        )

        root.addView(drawerPanel, drawerParams)

        drawerPanel.translationX = -dp(270).toFloat()

        val brand = TextView(this).apply {
            text = "♡\nDAYFLOW\nDiary"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.create("serif", Typeface.BOLD)
            setPadding(0, dp(15), 0, dp(35))
        }

        drawerPanel.addView(
            brand,
            LinearLayout.LayoutParams(-1, -2)
        )

        val line = View(this).apply {
            setBackgroundColor(Color.rgb(110, 116, 105))
        }

        drawerPanel.addView(
            line,
            LinearLayout.LayoutParams(-1, dp(1))
        )

        addDrawerItem("⌂", "일기 쓰기", 0)
        addDrawerItem("▤", "세로 메모지", 1)
        addDrawerItem("▧", "포스트잇 모아보기", 2)

        val historyItem = TextView(this).apply {
            text = "▣    기록 보기"
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(17), dp(8), dp(17))
            setOnClickListener {
                closeDrawer()
                showHistory()
            }
        }

        drawerPanel.addView(
            historyItem,
            LinearLayout.LayoutParams(-1, dp(55))
        )

        val spacer = View(this)
        drawerPanel.addView(
            spacer,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        val footerLine = View(this).apply {
            setBackgroundColor(Color.rgb(110, 116, 105))
        }

        drawerPanel.addView(
            footerLine,
            LinearLayout.LayoutParams(-1, dp(1))
        )

        val footer = TextView(this).apply {
            text = "♥\n오늘도 행복한 하루 되세요\n:)"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(20), 0, dp(20))
        }

        drawerPanel.addView(footer)
    }

    // 메뉴 항목
    private fun addDrawerItem(
        icon: String,
        name: String,
        mode: Int
    ) {

        val item = TextView(this).apply {

            text = "$icon    $name"
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL

            setPadding(dp(15), 0, dp(8), 0)

            setTextColor(Color.WHITE)

            background = rounded(
                if (currentMode == mode)
                    Color.rgb(91, 119, 153)
                else
                    drawerColor,
                12
            )

            setOnClickListener {
                currentMode = mode
                showScreen()
            }
        }

        val params = LinearLayout.LayoutParams(
            -1,
            dp(57)
        )

        params.setMargins(0, dp(7), 0, dp(7))

        drawerPanel.addView(item, params)
    }

    // 메뉴 열기
    private fun openDrawer() {

        if (drawerOpen) return

        drawerOpen = true

        scrim.visibility = View.VISIBLE

        scrim.animate()
            .alpha(0.48f)
            .setDuration(220)
            .start()

        drawerPanel.animate()
            .translationX(0f)
            .setDuration(240)
            .start()
    }

    // 메뉴 닫기
    private fun closeDrawer() {

        if (!drawerOpen) return

        drawerOpen = false

        drawerPanel.animate()
            .translationX(-dp(270).toFloat())
            .setDuration(220)
            .start()

        scrim.animate()
            .alpha(0f)
            .setDuration(220)
            .withEndAction {
                scrim.visibility = View.GONE
            }
            .start()
    }

    // 날짜별 사진을 선택하고 같은 날에는 유지
    private fun loadDailyBanner(target: ImageView) {

        val imageUrls = listOf(
            "https://images.unsplash.com/photo-1470252649378-9c29740c9fa8?w=1200&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?w=1200&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1470770841072-f978cf4d019e?w=1200&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=1200&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1501785888041-af3ef285b470?w=1200&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=1200&auto=format&fit=crop"
        )

        val todayKey = SimpleDateFormat(
            "yyyyMMdd",
            Locale.US
        ).format(Date())

        val savedDate = prefs.getString("banner_date", "")

        val imageIndex = if (savedDate == todayKey) {

            prefs.getInt("banner_index", 0)
                .coerceIn(imageUrls.indices)

        } else {

            val previous = prefs.getInt("banner_index", -1)
            var next = Random.nextInt(imageUrls.size)

            if (imageUrls.size > 1 && next == previous) {
                next = (next + 1) % imageUrls.size
            }

            prefs.edit()
                .putString("banner_date", todayKey)
                .putInt("banner_index", next)
                .apply()

            next
        }

        Thread {

            var connection: HttpURLConnection? = null

            try {

                connection = URL(imageUrls[imageIndex])
                    .openConnection() as HttpURLConnection

                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                connection.requestMethod = "GET"
                connection.connect()

                val bitmap = connection.inputStream.use {
                    BitmapFactory.decodeStream(it)
                }

                if (bitmap != null) {

                    runOnUiThread {

                        if (!isFinishing && !isDestroyed) {
                            target.setImageBitmap(bitmap)
                        }
                    }
                }

            } catch (_: Exception) {

                // 사진을 불러오지 못하면 기본 배경 유지

            } finally {

                connection?.disconnect()
            }

        }.start()
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

        val divider = View(this).apply {
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
                setPadding(dp(17), dp(20), dp(17), dp(17))

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
                typeface = Typeface.create("serif", Typeface.BOLD)
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

            params.setMargins(dp(5), dp(5), dp(12), dp(10))
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
