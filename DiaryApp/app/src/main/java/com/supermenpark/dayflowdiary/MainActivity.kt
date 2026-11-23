package com.supermenpark.dayflowdiary

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MainActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("my_diary", MODE_PRIVATE)
    }

    private val entries = mutableListOf<DiaryEntry>()
    private val photoUris = mutableListOf<String>()
    private val selectedPhotoUris = mutableSetOf<String>()

    private val PHOTO_PICK_REQUEST = 410
    private val LOCATION_PERMISSION_REQUEST = 520
    private val BACKUP_CREATE_REQUEST = 610
    private val BACKUP_RESTORE_REQUEST = 611

    private lateinit var weatherText: TextView
    private lateinit var diaryWeatherText: TextView
    private lateinit var root: FrameLayout
    private lateinit var appContainer: LinearLayout
    private lateinit var drawerPanel: LinearLayout
    private lateinit var scrim: View
    private lateinit var menuHandle: TextView

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

    private val drawerWidth = 270

    data class DiaryEntry(
        val id: Long,
        val date: String,
        val title: String,
        val body: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadEntries()
        loadPhotos()
        showScreen()
    }

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
                    if (!drawerOpen && touchStartX < dp(35) && dx > 0) {
                        openDrawer()
                    } else if (
                        drawerOpen &&
                        dx < 0 &&
                        touchStartX < dp(drawerWidth + 50)
                    ) {
                        closeDrawer()
                    }
                }
            }
        }

        return super.dispatchTouchEvent(event)
    }

    private fun showScreen() {
        drawerOpen = false

        root = FrameLayout(this).apply {
            setBackgroundColor(pageBackground)
        }

        appContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBackground)
        }

        root.addView(appContainer, FrameLayout.LayoutParams(-1, -1))

        val header = FrameLayout(this).apply {
            background = rounded(Color.rgb(115, 125, 104), 14)
            clipToOutline = true
        }

        val bannerImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(115, 125, 104))
        }

        header.addView(bannerImage, FrameLayout.LayoutParams(-1, -1))

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

        header.addView(shade, FrameLayout.LayoutParams(-1, -1))

        val bannerQuote = TextView(this).apply {
            text = dailyBannerQuote()
            textSize = 19f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(12), dp(24), dp(12))
            maxLines = 2
            setShadowLayer(5f, 1f, 2f, Color.BLACK)
        }

        header.addView(
            bannerQuote,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        appContainer.addView(
            header,
            LinearLayout.LayoutParams(-1, dp(180)).apply {
                setMargins(dp(18), dp(12), dp(18), dp(12))
            }
        )

        // 화면에는 표시하지 않는 날씨 갱신용 뷰
        // 날씨는 다이어리 우측 상단에만 표시
        weatherText = TextView(this).apply {
            text = "📍 지역·날씨 확인"
        }

        loadDailyBanner(bannerImage)
        loadWeatherForCurrentLocation()

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
            3 -> buildPhotoAlbum(content)
        }

        buildDrawer()
    }

    // 매일 바뀌는 응원 문구
    private fun dailyBannerQuote(): String {
        val quotes = listOf(
            "오늘도 충분히 잘하고 있어.",
            "천천히 가도 괜찮아. 멈추지만 않으면 돼.",
            "오늘의 작은 걸음이 내일을 바꿔.",
            "너의 하루는 그 자체로 소중해.",
            "잘하려 애쓴 오늘, 그것만으로 충분해.",
            "좋은 일은 생각보다 가까이에 있어.",
            "오늘도 너답게, 편안하게.",
            "지금까지 잘 버텨왔고 앞으로도 잘할 거야.",
            "작은 행복을 놓치지 않는 하루가 되길.",
            "오늘은 어제보다 조금 더 웃어보자.",
            "새로운 하루에는 새로운 기회가 있어.",
            "오늘도 네 편이 되어주자.",
            "조금 느려도 괜찮아. 잘 가고 있어.",
            "마음 편한 하루가 가장 좋은 하루야.",
            "언젠가 오늘을 웃으며 이야기할 거야."
        )

        val todayKey =
            SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

        val savedDate = prefs.getString("banner_quote_date", "")

        val index = if (savedDate == todayKey) {
            prefs.getInt("banner_quote_index", 0)
                .coerceIn(quotes.indices)
        } else {
            val previous = prefs.getInt("banner_quote_index", -1)
            var next = Random.nextInt(quotes.size)

            if (quotes.size > 1 && next == previous) {
                next = (next + 1) % quotes.size
            }

            prefs.edit()
                .putString("banner_quote_date", todayKey)
                .putInt("banner_quote_index", next)
                .apply()

            next
        }

        return quotes[index]
    }

    // 매일 바뀌는 자연 풍경 배경
    private fun loadDailyBanner(target: ImageView) {

        val imageUrls = listOf(
            "https://images.unsplash.com/photo-1501785888041-af3ef285b470?w=1200&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1470770841072-f978cf4d019e?w=1200&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=1200&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1470252649378-9c29740c9fa8?w=1200&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1518837695005-2083093ee35b?w=1200&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=1200&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1500534623283-312aade485b7?w=1200&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?w=1200&auto=format&fit=crop"
        )

        val todayKey =
            SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

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
                connection =
                    URL(imageUrls[imageIndex])
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
                // 사진 로딩 실패 시 기본 배경 유지
            } finally {
                connection?.disconnect()
            }

        }.start()
    }

    // 슬라이드 메뉴
    private fun buildDrawer() {
        scrim = View(this).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
            visibility = View.GONE
            setOnClickListener { closeDrawer() }
        }

        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))

        drawerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(35), dp(20), dp(20))
            setBackgroundColor(drawerColor)
            elevation = dp(15).toFloat()
        }

        val drawerParams = FrameLayout.LayoutParams(
            dp(drawerWidth), -1, Gravity.START
        )

        root.addView(drawerPanel, drawerParams)
        drawerPanel.translationX = -dp(drawerWidth).toFloat()

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
        addDrawerItem("▣", "사진첩", 3)

        // 새 백업·복원 메뉴
        val backupItem = TextView(this).apply {
            text = "⇅    데이터 백업 · 복원"
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE)
            setPadding(dp(15), 0, dp(8), 0)
            background = rounded(drawerColor, 12)

            setOnClickListener {
                closeDrawer()
                showBackupRestoreDialog()
            }
        }

        val backupParams = LinearLayout.LayoutParams(-1, dp(57))
        backupParams.setMargins(0, dp(7), 0, dp(7))
        drawerPanel.addView(backupItem, backupParams)

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

        drawerPanel.addView(
            View(this),
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

        menuHandle = TextView(this).apply {
            text = "›"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)

            background = GradientDrawable().apply {
                setColor(Color.rgb(117, 125, 91))
                cornerRadii = floatArrayOf(
                    0f, 0f,
                    dp(13).toFloat(), dp(13).toFloat(),
                    dp(13).toFloat(), dp(13).toFloat(),
                    0f, 0f
                )
            }

            elevation = dp(20).toFloat()

            setOnClickListener {
                if (drawerOpen) closeDrawer() else openDrawer()
            }
        }

        val handleParams = FrameLayout.LayoutParams(
            dp(25), dp(76),
            Gravity.START or Gravity.CENTER_VERTICAL
        )

        root.addView(menuHandle, handleParams)
        menuHandle.translationX = 0f
    }

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
                if (currentMode == mode) Color.rgb(91, 119, 153)
                else drawerColor,
                12
            )

            setOnClickListener {
                currentMode = mode
                selectedPhotoUris.clear()
                showScreen()
            }
        }

        val params = LinearLayout.LayoutParams(-1, dp(57))
        params.setMargins(0, dp(7), 0, dp(7))
        drawerPanel.addView(item, params)
    }

    private fun openDrawer() {
        if (drawerOpen) return

        drawerOpen = true
        scrim.visibility = View.VISIBLE
        menuHandle.text = "‹"

        scrim.animate().alpha(0.48f).setDuration(220).start()
        drawerPanel.animate().translationX(0f).setDuration(240).start()

        menuHandle.animate()
            .translationX(dp(drawerWidth).toFloat())
            .setDuration(240)
            .start()
    }

    private fun closeDrawer() {
        if (!drawerOpen) return

        drawerOpen = false
        menuHandle.text = "›"

        drawerPanel.animate()
            .translationX(-dp(drawerWidth).toFloat())
            .setDuration(220)
            .start()

        menuHandle.animate()
            .translationX(0f)
            .setDuration(220)
            .start()

        scrim.animate()
            .alpha(0f)
            .setDuration(220)
            .withEndAction {
                scrim.visibility = View.GONE
            }
            .start()
    }    // 일기 화면
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

        val diaryHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val smallTitle = TextView(this).apply {
            text = "TODAY'S DIARY"
            textSize = 11f
            letterSpacing = 0.15f
            setTextColor(accent)
        }

        diaryHeaderRow.addView(
            smallTitle,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        diaryWeatherText = TextView(this).apply {
            text = "📍 지역 확인 중 · 날씨 확인 중"
            textSize = 12f
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setTextColor(Color.GRAY)
            maxLines = 2
        }

        diaryHeaderRow.addView(
            diaryWeatherText,
            LinearLayout.LayoutParams(-2, -2)
        )

        sheet.addView(diaryHeaderRow)

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
            setOnClickListener {
                saveDiary()
            }
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

    // 일기 저장
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

    // 메모 모아보기
    private fun buildMemoCollection(target: LinearLayout, sticky: Boolean) {
        target.removeAllViews()

        val heading = TextView(this).apply {
            text = if (sticky) "나의 작은 기록들" else "소중한 하루의 기록"
            textSize = 21f
            typeface = Typeface.create("serif", Typeface.BOLD)
            setTextColor(ink)
            setPadding(dp(5), dp(10), dp(5), dp(8))
        }

        target.addView(heading)

        val description = TextView(this).apply {
            text = if (sticky) {
                "메모지를 좌우로 넘겨 기록을 살펴보세요."
            } else {
                "아이보리 종이 위에 쌓인 나의 일기입니다."
            }

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

        target.addView(add, LinearLayout.LayoutParams(-1, -2))

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

                setBackground(rounded(cardColor, if (sticky) 5 else 8))
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

            val params = LinearLayout.LayoutParams(dp(width), dp(height))
            params.setMargins(dp(5), dp(5), dp(12), dp(10))

            row.addView(card, params)
        }

        horizontal.addView(row)

        target.addView(
            horizontal,
            LinearLayout.LayoutParams(-1, -2)
        )
    }

    // 사진첩 화면
    private fun buildPhotoAlbum(target: LinearLayout) {
        target.removeAllViews()

        val heading = TextView(this).apply {
            text = "나의 사진첩"
            textSize = 23f
            typeface = Typeface.create("serif", Typeface.BOLD)
            setTextColor(ink)
            setPadding(dp(5), dp(12), dp(5), dp(6))
        }

        target.addView(heading)

        val description = TextView(this).apply {
            text = "소중한 순간을 사진으로 보관하세요."
            textSize = 13f
            setTextColor(Color.GRAY)
            setPadding(dp(5), 0, dp(5), dp(18))
        }

        target.addView(description)

        val addButton = TextView(this).apply {
            text = "+ 휴대폰에서 사진 가져오기"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(15), dp(12), dp(15))
            background = rounded(accent, 12)
            setOnClickListener { openPhotoPicker() }
        }

        target.addView(
            addButton,
            LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, 0, 0, dp(18))
            }
        )

        if (photoUris.isEmpty()) {
            val empty = TextView(this).apply {
                text = "\n\n📷\n\n아직 보관한 사진이 없습니다.\n위 버튼을 눌러 첫 사진을 추가해 보세요."
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(Color.GRAY)
                setPadding(dp(12), dp(35), dp(12), dp(35))
                background = rounded(paper, 12)
            }

            target.addView(empty, LinearLayout.LayoutParams(-1, dp(250)))
            return
        }

        val selectionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }

        val selectionInfo = TextView(this).apply {
            text = if (selectedPhotoUris.isEmpty()) {
                "사진을 길게 눌러 여러 장을 선택하세요."
            } else {
                "${selectedPhotoUris.size}장 선택됨"
            }

            textSize = 12f
            setTextColor(Color.GRAY)
        }

        selectionBar.addView(
            selectionInfo,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val deleteButton = TextView(this).apply {
            text = "선택 삭제"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(13), dp(9), dp(13), dp(9))
            background = rounded(accent, 9)

            visibility = if (selectedPhotoUris.isEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }

            setOnClickListener {
                if (selectedPhotoUris.isEmpty()) return@setOnClickListener

                AlertDialog.Builder(this@MainActivity)
                    .setMessage("${selectedPhotoUris.size}장의 사진을 삭제할까요?")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("삭제") { _, _ ->

                        val deleting = selectedPhotoUris.toList()

                        deleting.forEach { uriString ->
                            deleteStoredPhoto(uriString)
                            photoUris.remove(uriString)
                        }

                        selectedPhotoUris.clear()
                        savePhotos()
                        showScreen()

                        Toast.makeText(
                            this@MainActivity,
                            "선택한 사진을 삭제했습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .show()
            }
        }

        selectionBar.addView(deleteButton)
        target.addView(selectionBar)

        val grid = android.widget.GridLayout(this).apply {
            columnCount = 3
            alignmentMode = android.widget.GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }

        photoUris.forEach { uriString ->

            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = rounded(Color.rgb(230, 226, 215), 7)

                try {
                    setImageURI(Uri.parse(uriString))
                } catch (_: Exception) {
                    setBackgroundColor(Color.LTGRAY)
                }

                setOnClickListener {
                    if (selectedPhotoUris.isNotEmpty()) {
                        togglePhotoSelection(uriString)
                    } else {
                        showPhoto(uriString)
                    }
                }

                setOnLongClickListener {
                    togglePhotoSelection(uriString)
                    true
                }

                if (selectedPhotoUris.contains(uriString)) {
                    foreground = ColorDrawable(0x66000000)
                }
            }

            val params = android.widget.GridLayout.LayoutParams().apply {
                width = 0
                height = dp(112)

                columnSpec = android.widget.GridLayout.spec(
                    android.widget.GridLayout.UNDEFINED,
                    1f
                )

                setMargins(dp(3), dp(3), dp(3), dp(3))
            }

            grid.addView(image, params)
        }

        target.addView(grid)
    }

    // 휴대폰 사진 선택
    private fun openPhotoPicker() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }

            startActivityForResult(intent, PHOTO_PICK_REQUEST)

        } catch (_: Exception) {
            Toast.makeText(
                this,
                "사진 선택 기능을 열 수 없습니다.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK || data?.data == null) return

        val uri = data.data!!

        when (requestCode) {
            BACKUP_CREATE_REQUEST -> {
                createDiaryBackup(uri)
                return
            }

            BACKUP_RESTORE_REQUEST -> {
                restoreDiaryBackup(uri)
                return
            }

            PHOTO_PICK_REQUEST -> {
                // 기존 사진 선택 기능 유지
            }

            else -> return
        }

        val picked = mutableListOf<Uri>()

        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                picked.add(clip.getItemAt(i).uri)
            }
        }

        if (picked.isEmpty()) {
            picked.add(uri)
        }

        var added = 0

        picked.forEach { photoUri ->
            try {
                val folder = java.io.File(filesDir, "diary_photos")

                if (!folder.exists()) {
                    folder.mkdirs()
                }

                val extension = when (
                    contentResolver.getType(photoUri)?.lowercase(Locale.ROOT)
                ) {
                    "image/png" -> ".png"
                    "image/webp" -> ".webp"
                    "image/gif" -> ".gif"
                    else -> ".jpg"
                }

                val destination = java.io.File(
                    folder,
                    "photo_${System.currentTimeMillis()}_${Random.nextInt(100000)}$extension"
                )

                val copied = contentResolver.openInputStream(photoUri)?.use { input ->
                    destination.outputStream().use { output ->
                        input.copyTo(output)
                    }
                    true
                } ?: false

                if (
                    copied &&
                    destination.exists() &&
                    destination.length() > 0L
                ) {
                    val savedUri = Uri.fromFile(destination).toString()

                    if (!photoUris.contains(savedUri)) {
                        photoUris.add(0, savedUri)
                        added++
                    }
                }

            } catch (_: Exception) {
                // 읽을 수 없는 사진은 건너뜀
            }
        }

        selectedPhotoUris.clear()
        savePhotos()
        showScreen()

        Toast.makeText(
            this,
            "${added}장의 사진을 앱에 보관했습니다.",
            Toast.LENGTH_SHORT
        ).show()
    }

    // 사진 선택 상태 전환
    private fun togglePhotoSelection(uriString: String) {
        if (selectedPhotoUris.contains(uriString)) {
            selectedPhotoUris.remove(uriString)
        } else {
            selectedPhotoUris.add(uriString)
        }

        showScreen()
    }

    // 앱 내부에 저장된 사진 파일 삭제
    private fun deleteStoredPhoto(uriString: String) {
        try {
            val uri = Uri.parse(uriString)

            if (uri.scheme == "file") {
                uri.path?.let { path ->
                    val file = java.io.File(path)

                    if (file.exists()) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) {
            // 삭제할 파일이 없는 경우 건너뜀
        }
    }

    // 사진 크게 보기 및 삭제
    private fun showPhoto(uriString: String) {
        val image = ImageView(this).apply {
            setImageURI(Uri.parse(uriString))
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setBackgroundColor(Color.BLACK)
        }

        AlertDialog.Builder(this)
            .setTitle("사진")
            .setView(image)
            .setPositiveButton("닫기", null)
            .setNegativeButton("사진 삭제") { _, _ ->

                deleteStoredPhoto(uriString)
                photoUris.remove(uriString)
                selectedPhotoUris.remove(uriString)
                savePhotos()
                showScreen()

                Toast.makeText(
                    this,
                    "사진을 사진첩에서 삭제했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    // 사진첩은 기존 저장 방식 그대로 유지
    private fun loadPhotos() {
        photoUris.clear()

        try {
            val data = JSONArray(prefs.getString("photos", "[]"))

            for (i in 0 until data.length()) {
                photoUris.add(data.getString(i))
            }

        } catch (_: Exception) {
            photoUris.clear()
        }
    }

    private fun savePhotos() {
        val array = JSONArray()

        photoUris.forEach {
            array.put(it)
        }

        prefs.edit().putString("photos", array.toString()).apply()
    }

    // 기록 보기
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

    // 저장된 일기 불러오기
    private fun loadEntries() {
        entries.clear()

        try {
            val data = JSONArray(prefs.getString("entries", "[]"))

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

    // 기존 일기 저장 구조 유지
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

        prefs.edit().putString("entries", array.toString()).apply()
    }

    // 백업 및 복원 메뉴
    private fun showBackupRestoreDialog() {
        AlertDialog.Builder(this)
            .setTitle("DAYFLOW 데이터 관리")
            .setItems(
                arrayOf(
                    "일기와 사진 백업하기",
                    "백업 파일 복원하기"
                )
            ) { _, which ->
                if (which == 0) {
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/zip"
                        putExtra(
                            Intent.EXTRA_TITLE,
                            "DAYFLOW_Backup_${System.currentTimeMillis()}.zip"
                        )
                    }

                    startActivityForResult(
                        intent,
                        BACKUP_CREATE_REQUEST
                    )
                } else {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/zip"
                    }

                    startActivityForResult(
                        intent,
                        BACKUP_RESTORE_REQUEST
                    )
                }
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    // 일기와 사진을 ZIP 파일로 백업
    private fun createDiaryBackup(uri: Uri) {
        try {
            val manifest = JSONObject()
            val diaryArray = JSONArray()
            val photoArray = JSONArray()

            entries.forEach { entry ->
                diaryArray.put(
                    JSONObject().apply {
                        put("id", entry.id)
                        put("date", entry.date)
                        put("title", entry.title)
                        put("body", entry.body)
                    }
                )
            }

            val existingPhotos = mutableListOf<java.io.File>()

            photoUris.forEach { photo ->
                val path = Uri.parse(photo).path ?: return@forEach
                val file = java.io.File(path)

                if (file.exists() && file.isFile) {
                    photoArray.put(file.name)
                    existingPhotos.add(file)
                }
            }

            manifest.put("format", "DAYFLOW_DIARY_BACKUP_1")
            manifest.put("entries", diaryArray)
            manifest.put("photos", photoArray)

            contentResolver.openOutputStream(uri)?.use { output ->
                ZipOutputStream(output).use { zip ->

                    zip.putNextEntry(ZipEntry("backup.json"))
                    zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    existingPhotos.forEach { file ->
                        zip.putNextEntry(
                            ZipEntry("photos/${file.name}")
                        )

                        file.inputStream().use { input ->
                            input.copyTo(zip)
                        }

                        zip.closeEntry()
                    }
                }
            } ?: throw Exception("백업 파일을 만들 수 없습니다.")

            Toast.makeText(
                this,
                "일기 ${entries.size}개와 사진 ${existingPhotos.size}장을 백업했습니다.",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "백업 실패: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // 백업 파일을 검사한 뒤 기존 기록에 추가 복원
    private fun restoreDiaryBackup(uri: Uri) {
        val extractedFiles = mutableListOf<java.io.File>()

        try {
            val zipContents = mutableMapOf<String, ByteArray>()

            contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry

                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val name = entry.name

                            if (
                                name == "backup.json" ||
                                (
                                    name.startsWith("photos/") &&
                                    name.substringAfterLast('/').isNotBlank()
                                )
                            ) {
                                val output = ByteArrayOutputStream()
                                zip.copyTo(output)
                                zipContents[name] = output.toByteArray()
                            }
                        }

                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: throw Exception("백업 파일을 읽을 수 없습니다.")

            val manifestBytes = zipContents["backup.json"]
                ?: throw Exception("백업 정보가 없습니다.")

            val manifest = JSONObject(
                String(manifestBytes, Charsets.UTF_8)
            )

            if (
                manifest.optString("format") !=
                "DAYFLOW_DIARY_BACKUP_1"
            ) {
                throw Exception("DAYFLOW 백업 파일이 아닙니다.")
            }

            val diaryArray =
                manifest.optJSONArray("entries") ?: JSONArray()

            val folder = java.io.File(filesDir, "diary_photos")

            if (!folder.exists() && !folder.mkdirs()) {
                throw Exception("사진 저장 폴더를 만들 수 없습니다.")
            }

            // 사진 파일을 기존 파일과 겹치지 않는 이름으로 저장
            val restoredPhotoUris = mutableListOf<String>()

            zipContents.forEach { (name, bytes) ->
                if (name.startsWith("photos/")) {
                    val originalName = name.substringAfterLast('/')

                    if (originalName.isBlank()) return@forEach

                    val extension = originalName.substringAfterLast(
                        ".",
                        "jpg"
                    ).take(8)

                    val destination = java.io.File(
                        folder,
                        "restored_${System.currentTimeMillis()}_${Random.nextInt(100000)}.$extension"
                    )

                    destination.writeBytes(bytes)

                    if (
                        destination.exists() &&
                        destination.length() > 0L
                    ) {
                        extractedFiles.add(destination)
                        restoredPhotoUris.add(
                            Uri.fromFile(destination).toString()
                        )
                    }
                }
            }

            // 기존 일기는 덮어쓰지 않고 없는 ID만 추가
            var addedEntries = 0

            for (i in 0 until diaryArray.length()) {
                val item = diaryArray.getJSONObject(i)
                val id = item.getLong("id")

                if (entries.none { it.id == id }) {
                    entries.add(
                        DiaryEntry(
                            id = id,
                            date = item.optString("date"),
                            title = item.optString("title"),
                            body = item.optString("body")
                        )
                    )

                    addedEntries++
                }
            }

            // 기존 사진 목록도 유지하면서 복원 사진 추가
            restoredPhotoUris.forEach { photo ->
                if (!photoUris.contains(photo)) {
                    photoUris.add(photo)
                }
            }

            persistEntries()
            savePhotos()

            Toast.makeText(
                this,
                "복원 완료\n일기 ${addedEntries}개 추가\n사진 ${restoredPhotoUris.size}장 추가",
                Toast.LENGTH_LONG
            ).show()

            showScreen()

        } catch (e: Exception) {
            // 복원 실패 시 이번 작업에서 만든 파일만 정리
            extractedFiles.forEach { file ->
                try {
                    file.delete()
                } catch (_: Exception) {
                }
            }

            Toast.makeText(
                this,
                "복원 실패: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun today(): String {
        return SimpleDateFormat(
            "yyyy년 M월 d일 E요일",
            Locale.KOREAN
        ).format(Date())
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    // 현재 위치 새로 확인
    private fun loadWeatherForCurrentLocation() {
        if (!::weatherText.isInitialized) return

        val granted =
            checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            weatherText.text = "📍 위치 권한 허용 필요"

            if (::diaryWeatherText.isInitialized) {
                diaryWeatherText.text = "📍 위치 권한 허용 필요"
            }

            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST
            )

            return
        }

        try {
            val manager =
                getSystemService(LOCATION_SERVICE) as LocationManager

            val provider = when {
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                    LocationManager.NETWORK_PROVIDER

                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                    LocationManager.GPS_PROVIDER

                else -> null
            }

            if (provider == null) {
                weatherText.text = "📍 위치 서비스를 켜주세요."

                if (::diaryWeatherText.isInitialized) {
                    diaryWeatherText.text = "📍 위치 서비스를 켜주세요."
                }
                return
            }

            weatherText.text = "📍 현재 위치 확인 중…"

            if (::diaryWeatherText.isInitialized) {
                diaryWeatherText.text = "📍 현재 위치 확인 중…"
            }

            @Suppress("MissingPermission")
            manager.requestSingleUpdate(
                provider,
                object : android.location.LocationListener {

                    override fun onLocationChanged(location: Location) {
                        fetchWeather(
                            location.latitude,
                            location.longitude
                        )
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(
                        provider: String?,
                        status: Int,
                        extras: Bundle?
                    ) {}

                    override fun onProviderEnabled(provider: String) {}

                    override fun onProviderDisabled(provider: String) {}
                },
                mainLooper
            )

        } catch (_: SecurityException) {
            weatherText.text = "📍 위치 권한을 확인해 주세요."

            if (::diaryWeatherText.isInitialized) {
                diaryWeatherText.text = "📍 위치 권한을 확인해 주세요."
            }

        } catch (_: Exception) {
            weatherText.text = "📍 위치 확인 실패"

            if (::diaryWeatherText.isInitialized) {
                diaryWeatherText.text = "📍 위치 확인 실패"
            }
        }
    }

    // 현재 날씨 조회
    private fun fetchWeather(
        latitude: Double,
        longitude: Double
    ) {
        if (::weatherText.isInitialized) {
            weatherText.text = "📍 날씨 불러오는 중…"
        }

        if (::diaryWeatherText.isInitialized) {
            diaryWeatherText.text = "📍 날씨 불러오는 중…"
        }

        Thread {
            var placeName = "현재 위치"
            var weatherLine = "날씨 조회 실패"

            try {
                try {
                    val geocoder = Geocoder(this, Locale.KOREAN)

                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(
                        latitude,
                        longitude,
                        1
                    )

                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]

                        placeName =
                            address.subLocality
                                ?: address.locality
                                ?: address.adminArea
                                ?: "현재 위치"
                    }

                } catch (_: Exception) {
                    placeName = "현재 위치"
                }

                val url = URL(
                    "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=$latitude" +
                        "&longitude=$longitude" +
                        "&current=temperature_2m,weather_code" +
                        "&timezone=auto"
                )

                val connection =
                    url.openConnection() as HttpURLConnection

                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.requestMethod = "GET"

                try {
                    val responseCode = connection.responseCode

                    if (responseCode != 200) {
                        throw java.io.IOException(
                            "서버 응답 오류: $responseCode"
                        )
                    }

                    val response =
                        connection.inputStream.bufferedReader().use {
                            it.readText()
                        }

                    val json = JSONObject(response)
                    val current = json.getJSONObject("current")

                    val temperature =
                        current.getDouble("temperature_2m").toInt()

                    val code = current.getInt("weather_code")

                    val description = when (code) {
                        0 -> "맑음"
                        1, 2 -> "구름 조금"
                        3 -> "흐림"
                        45, 48 -> "안개"
                        51, 53, 55, 56, 57 -> "이슬비"
                        61, 63, 65, 66, 67 -> "비"
                        71, 73, 75, 77 -> "눈"
                        80, 81, 82 -> "소나기"
                        85, 86 -> "눈 소나기"
                        95, 96, 99 -> "뇌우"
                        else -> "날씨 정보"
                    }

                    val icon = when {
                        code == 0 -> "☀️"
                        code in listOf(1, 2, 3) -> "☁️"
                        code in listOf(45, 48) -> "🌫️"
                        code in listOf(71, 73, 75, 77, 85, 86) -> "❄️"
                        code in listOf(95, 96, 99) -> "⛈️"
                        code in listOf(
                            51, 53, 55, 56, 57,
                            61, 63, 65, 66, 67,
                            80, 81, 82
                        ) -> "🌧️"

                        else -> "🌤️"
                    }

                    weatherLine = "$icon $description ${temperature}°C"

                } finally {
                    connection.disconnect()
                }

            } catch (e: Exception) {
                weatherLine =
                    "날씨 조회 실패 (${e.message ?: "네트워크 확인"})"
            }

            runOnUiThread {
                if (!isFinishing && !isDestroyed) {

                    val displayText =
                        "📍 $placeName  ·  $weatherLine"

                    if (::weatherText.isInitialized) {
                        weatherText.text = displayText
                    }

                    if (::diaryWeatherText.isInitialized) {
                        diaryWeatherText.text = displayText
                    }
                }
            }

        }.start()
    }

    // 위치 권한 결과 확인
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (
                grantResults.any {
                    it == PackageManager.PERMISSION_GRANTED
                }
            ) {
                loadWeatherForCurrentLocation()

            } else if (::weatherText.isInitialized) {
                weatherText.text = "📍 위치 권한이 필요합니다"

                if (::diaryWeatherText.isInitialized) {
                    diaryWeatherText.text = "📍 위치 권한이 필요합니다"
                }
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
