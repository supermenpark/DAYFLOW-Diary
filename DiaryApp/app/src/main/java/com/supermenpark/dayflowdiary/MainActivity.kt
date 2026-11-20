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
