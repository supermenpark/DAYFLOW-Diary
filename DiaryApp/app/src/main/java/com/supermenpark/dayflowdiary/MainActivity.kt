--- a/DiaryApp/app/src/main/java/com/supermenpark/dayflowdiary/MainActivity.kt
+++ b/DiaryApp/app/src/main/java/com/supermenpark/dayflowdiary/MainActivity.kt
@@
-        // 지역·날씨 표시 영역
-        weatherText = TextView(this).apply {
-            text = "📍 지역·날씨 확인"
-            textSize = 16f
-            setTextColor(Color.BLACK)
-            gravity = Gravity.CENTER
-            setPadding(dp(12), dp(10), dp(12), dp(10))
-            setBackgroundColor(Color.rgb(220, 230, 210))
-            visibility = View.VISIBLE
-            minHeight = dp(55)
-
-            setOnClickListener {
-                loadWeatherForCurrentLocation()
-            }
-        }
-
-        val weatherRowParams = LinearLayout.LayoutParams(
-            LinearLayout.LayoutParams.MATCH_PARENT,
-            dp(55)
-        )
-        weatherRowParams.setMargins(
-            dp(18), dp(0), dp(18), dp(10)
-        )
-
-        appContainer.addView(weatherText, weatherRowParams)
-
-
         loadDailyBanner(bannerImage)
-loadWeatherForCurrentLocation()
@@
         when (currentMode) {
-            0 -> buildDiary(content)
+            0 -> {
+                buildDiary(content)
+                loadWeatherForCurrentLocation()
+            }
@@
         diaryWeatherText = TextView(this).apply {
             text = "날씨 확인 중"
-            textSize = 12f
+            textSize = 10f
             gravity = Gravity.CENTER_VERTICAL or Gravity.END
             setTextColor(Color.GRAY)
             maxLines = 1
+            ellipsize = android.text.TextUtils.TruncateAt.END
+            setPadding(dp(4), 0, 0, 0)
+            setOnClickListener { loadWeatherForCurrentLocation() }
         }
+        // 기존 날씨 조회 함수가 이 위치의 텍스트를 갱신하도록 연결
+        weatherText = diaryWeatherText
@@
-                weatherText.text =
-                    "📍 $placeName\n$weatherLine"
+                weatherText.text =
+                    "📍 $placeName  $weatherLine"
@@
-        } else if (::weatherText.isInitialized) {
+        } else if (::weatherText.isInitialized) {
             weatherText.text = "📍 위치 권한이 필요합니다"
         }
