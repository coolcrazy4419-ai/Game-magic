private fun startFallbackUsageMonitor() {
        fallbackMonitorJob = serviceScope.launch {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            while (isActive) {
                delay(1200L)
                if (usm == null) continue
                val selected = TurboSettingsStore.state.value.selectedPackages
                if (selected.isEmpty()) {
                    if (activeGamePackage != null) handleForegroundUpdate(null, false)
                    continue
                }

                val a11yPkg = GameAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString()
                if (a11yPkg != null && a11yPkg != packageName && a11yPkg != "com.android.systemui") {
                    val isLauncher = a11yPkg.lowercase().contains("launcher") ||
                        a11yPkg == "com.miui.home" ||
                        a11yPkg == "com.mi.android.globallauncher"
                    val isGame = !isLauncher && selected.contains(a11yPkg)
                    handleForegroundUpdate(a11yPkg, isGame)
                    continue
                }

                val end = System.currentTimeMillis()
                val events = usm.queryEvents(end - 6000L, end)
                val event = UsageEvents.Event()
                var latestForegroundPkg: String? = null
                var activeGamePaused = false

                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    when (event.eventType) {
                        UsageEvents.Event.MOVE_TO_FOREGROUND,
                        UsageEvents.Event.ACTIVITY_RESUMED -> {
                            latestForegroundPkg = event.packageName
                            if (event.packageName == activeGamePackage) {
                                activeGamePaused = false
                            }
                        }
                        UsageEvents.Event.MOVE_TO_BACKGROUND,
                        UsageEvents.Event.ACTIVITY_PAUSED -> {
                            if (event.packageName == activeGamePackage) {
                                activeGamePaused = true
                            }
                        }
                    }
                }

                if (latestForegroundPkg != null && latestForegroundPkg != packageName) {
                    val isLauncher = latestForegroundPkg.lowercase().contains("launcher") ||
                        latestForegroundPkg == "com.miui.home" ||
                        latestForegroundPkg == "com.mi.android.globallauncher"
                    val isGame = !isLauncher && selected.contains(latestForegroundPkg)
                    handleForegroundUpdate(latestForegroundPkg, isGame)
                } else if (activeGamePaused && activeGamePackage != null) {
                    handleForegroundUpdate(null, false)
                }
            }
        }
    }
