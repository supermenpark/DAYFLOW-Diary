
    // 날짜별 사진 자동 선택 및 불러오기
    private fun loadDailyBanner(target: ImageView) {

        val imageUrls = listOf(
            "https://images.unsplash.com/photo-1470252649378-9c29740c9fa8?auto=format&fit=crop&w=1200&q=85",
            "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1200&q=85",
            "https://images.unsplash.com/photo-1470770841072-f978cf4d019e?auto=format&fit=crop&w=1200&q=85",
            "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?auto=format&fit=crop&w=1200&q=85",
            "https://images.unsplash.com/photo-1501785888041-af3ef285b470?auto=format&fit=crop&w=1200&q=85",
            "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?auto=format&fit=crop&w=1200&q=85"
        )

        val todayKey = SimpleDateFormat(
            "yyyyMMdd", Locale.US
        ).format(Date())

        val savedDate = prefs.getString("banner_date", "")

        val imageIndex = if (savedDate == todayKey) {
            prefs.getInt("banner_index", 0)
                .coerceIn(imageUrls.indices)
        } else {
            val previous = prefs.getInt("banner_index", -1)
            var next = Random.nextInt(imageUrls.size)

            if (next == previous) {
                next = (next + 1) % imageUrls.size
            }

            prefs.edit()
                .putString("banner_date", todayKey)
                .putInt("banner_index", next)
                .apply()

            next
        }

        Thread {
            var bitmap: android.graphics.Bitmap? = null

            val orderedUrls = listOf(imageUrls[imageIndex]) +
                imageUrls.filterIndexed { index, _ ->
                    index != imageIndex
                }

            for (imageUrl in orderedUrls) {
                var connection: HttpURLConnection? = null

                try {
                    connection = URL(imageUrl)
                        .openConnection() as HttpURLConnection

                    connection.connectTimeout = 15000
                    connection.readTimeout = 20000
                    connection.requestMethod = "GET"
                    connection.instanceFollowRedirects = true
                    connection.doInput = true

                    connection.setRequestProperty(
                        "User-Agent", "DAYFLOW-Diary-Android"
                    )

                    connection.setRequestProperty(
                        "Accept", "image/*"
                    )

                    if (connection.responseCode in 200..299) {
                        bitmap = connection.inputStream.use {
                            BitmapFactory.decodeStream(it)
                        }
                    }

                } catch (_: Exception) {
                    bitmap = null
                } finally {
                    connection?.disconnect()
                }

                if (bitmap != null) break
            }

            val loadedBitmap = bitmap

            if (loadedBitmap != null) {
                runOnUiThread {
                    if (!isFinishing &&
                        !isDestroyed &&
                        target.isAttachedToWindow
                    ) {
                        target.setImageBitmap(loadedBitmap)
                    }
                }
            }

        }.start()
    }
