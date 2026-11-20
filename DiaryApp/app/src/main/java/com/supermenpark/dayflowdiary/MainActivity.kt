package com.supermenpark.dayflowdiary

import android.app.Activity
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

class MainActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("my_diary", MODE_PRIVATE)
    }

    private val entries = mutableListOf<DiaryEntry>()
    private val photoUris = mutableListOf<String>()
    private val selectedPhotoUris = mutableSetOf<String>()

    private val PHOTO_PICK_REQUEST = 410

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
                setTextColor(if (currentMode == index) Color.WHITE else accent)
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
    // 슬라이드 메뉴 및 항상 보이는 손잡이
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

        drawerPanel.addView(brand, LinearLayout.LayoutParams(-1, -2))

        val line = View(this).apply {
            setBackgroundColor(Color.rgb(110, 116, 105))
        }
        drawerPanel.addView(line, LinearLayout.LayoutParams(-1, dp(1)))

        addDrawerItem("⌂", "일기 쓰기", 0)
        addDrawerItem("▤", "세로 메모지", 1)
        addDrawerItem("▧", "포스트잇 모아보기", 2)
        addDrawerItem("▣", "사진첩", 3)

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

        drawerPanel.addView(historyItem, LinearLayout.LayoutParams(-1, dp(55)))

        drawerPanel.addView(
            View(this),
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        val footerLine = View(this).apply {
            setBackgroundColor(Color.rgb(110, 116, 105))
        }
        drawerPanel.addView(footerLine, LinearLayout.LayoutParams(-1, dp(1)))

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
            dp(25), dp(76), Gravity.START or Gravity.CENTER_VERTICAL
        )
        root.addView(menuHandle, handleParams)
        menuHandle.translationX = 0f
    }

    private fun addDrawerItem(icon: String, name: String, mode: Int) {
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
            .withEndAction { scrim.visibility = View.GONE }
            .start()
    }

    // 날짜별 배경 사진
    private fun loadDailyBanner(target: ImageView) {
        val imageUrls = listOf(
            "https://images.unsplash.com/photo-1470252649378-9c29740c9fa8?w=1200&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?w=1200&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1470770841072-f978cf4d019e?w=1200&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=1200&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1501785888041-af3ef285b470?w=1200&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=1200&auto=format&fit=crop"
        )

        val todayKey = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val savedDate = prefs.getString("banner_date", "")

        val imageIndex = if (savedDate == todayKey) {
            prefs.getInt("banner_index", 0).coerceIn(imageUrls.indices)
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
                connection = URL(imageUrls[imageIndex]).openConnection() as HttpURLConnection
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

    // 일기 화면
    private fun buildDiary(target: LinearLayout, entry: DiaryEntry? = null) {
        target.removeAllViews()
        selectedId = entry?.id

        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(23), dp(25), dp(23), dp(25))
            setBackground(rounded(paper, 8))
            elevation = dp(3).toFloat()
        }

        target.addView(sheet, LinearLayout.LayoutParams(-1, -2))

        val smallTitle = TextView(this).apply {
            text = "TODAY'S DIARY"
            textSize = 11f
