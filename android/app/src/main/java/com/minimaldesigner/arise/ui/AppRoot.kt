package com.minimaldesigner.arise.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimaldesigner.arise.core.TICK_FREE
import com.minimaldesigner.arise.core.TICK_PHOTO
import com.minimaldesigner.arise.core.TaskDef
import com.minimaldesigner.arise.core.TaskKind
import com.minimaldesigner.arise.core.bookLog
import com.minimaldesigner.arise.core.compute
import com.minimaldesigner.arise.core.mindfulOn
import com.minimaldesigner.arise.core.pendingMiss
import com.minimaldesigner.arise.core.PauseState
import com.minimaldesigner.arise.core.passesLeft
import com.minimaldesigner.arise.ui.screens.BackSheetBody
import com.minimaldesigner.arise.ui.screens.PauseSheetBody
import com.minimaldesigner.arise.core.suggestWorkout
import com.minimaldesigner.arise.core.workoutsOn
import com.minimaldesigner.arise.data.HealthAccess
import com.minimaldesigner.arise.ui.screens.CustomPicks
import com.minimaldesigner.arise.ui.screens.CustomTasksSheetBody
import com.minimaldesigner.arise.ui.components.Confetti
import com.minimaldesigner.arise.ui.components.ConfettiHost
import com.minimaldesigner.arise.ui.components.FloatingNav
import com.minimaldesigner.arise.ui.components.LocalConfetti
import com.minimaldesigner.arise.ui.components.animationsOn
import com.minimaldesigner.arise.ui.components.NavClearance
import com.minimaldesigner.arise.ui.components.Sheet
import com.minimaldesigner.arise.ui.components.Tab
import com.minimaldesigner.arise.ui.components.Toasts
import com.minimaldesigner.arise.ui.screens.AddPhotoSheetBody
import com.minimaldesigner.arise.ui.screens.AppPickerSheetBody
import com.minimaldesigner.arise.ui.screens.MeditateSheetBody
import com.minimaldesigner.arise.ui.screens.SettingsPage
import com.minimaldesigner.arise.ui.screens.appLabel
import com.minimaldesigner.arise.ui.screens.photoDayText
import com.minimaldesigner.arise.ui.screens.Banner
import com.minimaldesigner.arise.ui.screens.Food
import com.minimaldesigner.arise.ui.screens.Home
import com.minimaldesigner.arise.ui.screens.MissedSheetBody
import com.minimaldesigner.arise.ui.screens.Onboarding
import com.minimaldesigner.arise.ui.screens.PhotoViewerBody
import com.minimaldesigner.arise.ui.screens.Photos
import com.minimaldesigner.arise.ui.screens.AttemptsSheetBody
import com.minimaldesigner.arise.ui.screens.EndSheetBody
import com.minimaldesigner.arise.ui.screens.PickPhotoSheetBody
import com.minimaldesigner.arise.ui.screens.ProfileScreen
import com.minimaldesigner.arise.ui.screens.ReadSheetBody
import com.minimaldesigner.arise.ui.screens.keepLine
import com.minimaldesigner.arise.core.photoPair
import com.minimaldesigner.arise.ui.screens.Week
import com.minimaldesigner.arise.ui.screens.WorkoutSheetBody
import com.minimaldesigner.arise.ui.theme.LocalArise

/** Which Read sheet is open: from the task (ticks it) or from Profile (just updates the book). */
private sealed interface ReadOpen {
    data class ForTask(val task: TaskDef) : ReadOpen
    data object Edit : ReadOpen
}

@Composable
fun AppRoot(vm: AppViewModel, state: AppState) {
    val c = LocalArise.current
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var weekOffset by rememberSaveable { mutableStateOf(0) }
    // Profile & settings: null when closed, else the page showing.
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    var workoutFor by remember { mutableStateOf<TaskDef?>(null) }
    var readOpen by remember { mutableStateOf<ReadOpen?>(null) }
    // Saveable: the camera app can push ARISE out of memory while the sheet waits for the photo.
    var addingPhoto by rememberSaveable { mutableStateOf(false) }
    // Meditate: the task's id, when ARISE was left for the meditation app, and when it came back.
    var meditateFor by rememberSaveable { mutableStateOf<String?>(null) }
    var leftAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var backAt by remember { mutableLongStateOf(0L) }
    var choosingApp by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = before, false = after
    var ending by remember { mutableStateOf(false) }
    var attempts by remember { mutableStateOf(false) }
    var choosingTasks by remember { mutableStateOf(false) }
    var pausing by remember { mutableStateOf(false) }
    val picks = remember { CustomPicks() }
    val busy by vm.busy.collectAsStateWithLifecycle()

    // Health Connect: re-read on every return to the app (it's cheap and rate-limited in the repository).
    val context = LocalContext.current
    // Back from the meditation app: read again at once, so its session shows in the sheet.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        backAt = System.currentTimeMillis()
        vm.refreshHealth(force = leftAt != null)
    }
    val askHealth = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract(), vm::healthGranted)
    fun open(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Health Connect went away or can't be opened: re-check so the card says so.
            vm.refreshHealth(force = true)
        }
    }
    val meditateApp = state.settings.meditateApp
    val meditateLabel = remember(meditateApp) { appLabel(context, meditateApp) }
    fun openMeditation() {
        val launch = if (meditateApp.isEmpty()) null else context.packageManager.getLaunchIntentForPackage(meditateApp)
        if (launch == null) {
            choosingApp = true
            return
        }
        leftAt = System.currentTimeMillis()
        try {
            context.startActivity(launch)
        } catch (e: ActivityNotFoundException) {
            leftAt = null
            choosingApp = true
        }
    }
    fun closeMeditate() {
        meditateFor = null
        leftAt = null
    }
    val connectHealth: () -> Unit = {
        when (state.health.access) {
            HealthAccess.NEEDS_UPDATE -> open(vm.health.installIntent())
            else -> askHealth.launch(vm.health.permissions())
        }
    }

    // Confetti for ticks and a cleared day (0.2.1): off with the setting or the phone's animations.
    val confetti = remember { Confetti() }
    confetti.enabled = state.settings.celebrate && remember(backAt) { animationsOn(context) }

    CompositionLocalProvider(LocalConfetti provides confetti) {
        Box(Modifier.fillMaxSize().background(c.page)) {
            // Until Room answers, show only the page colour (no onboarding flash).
            val run = if (state.loading) null else state.run
            val scroll = rememberScrollState()
            LaunchedEffect(tab, run == null, page) { scroll.scrollTo(0) }
            // Food is optional: its tab only shows once Health Connect shares nutrition.
            val foodOn = state.foodOn
            val tabs = remember(foodOn) { if (foodOn) Tab.entries.toList() else Tab.entries - Tab.Food }
            LaunchedEffect(tabs) { if (tab !in tabs) tab = Tab.Home }
            BackHandler(enabled = run != null && page != null) { page = if (page == SettingsPage.HUB) null else SettingsPage.HUB }
            val keep = keepLine(state.photos.size, state.meta.pins.before != null)

            if (!state.loading) Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp)
                    .padding(top = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.sample) Banner("Sample data. Nothing here is saved.", "Exit", vm::exitSample)
                if (run == null) {
                    Onboarding(state.clock.today, picks, onChooseTasks = { choosingTasks = true }, onStart = vm::start, onSample = vm::loadSample)
                } else if (page != null) {
                    val shownPage = page ?: SettingsPage.HUB
                    ProfileScreen(
                        shownPage, run, compute(run, state.days, state.clock.today), state.settings, state.meta, state.history,
                        state.profileThumb, state.health, state.photos.size,
                        books = bookLog(state.meta.reading, state.days),
                        meditateApp = meditateLabel,
                        onPage = { page = it },
                        onBack = { page = if (shownPage == SettingsPage.HUB) null else SettingsPage.HUB },
                        onName = vm::setName,
                        onPhoto = vm::setProfilePhoto,
                        onBody = vm::setBody,
                        onConnectHealth = connectHealth,
                        onOpenHealth = { open(vm.health.settingsIntent()) },
                        onRefreshHealth = { vm.refreshHealth(force = true) },
                        onEditBook = { readOpen = ReadOpen.Edit },
                        onAttempts = { attempts = true },
                        onTheme = vm::setTheme,
                        onNumbers = vm::setNumbers,
                        onAccent = vm::setAccent,
                        onCelebrate = vm::setCelebrate,
                        onChooseMeditateApp = { choosingApp = true },
                        onReminder = vm::setReminder,
                        onFoodGoals = vm::setFoodGoals,
                        busy = busy,
                        onBackup = vm::backup,
                        onRestore = vm::restore,
                        onImport75 = vm::importHard75,
                        onEnd = { ending = true },
                        pause = state.pause,
                        passesLeft = passesLeft(run, state.days, state.clock.today),
                        onPause = { pausing = true },
                        onEndPause = { page = null; tab = Tab.Home; vm.endPauseEarly() },
                    )
                } else {
                    RunTabs(
                        vm, state, tab, weekOffset,
                        onTab = { tab = it },
                        onWeekOffset = { weekOffset = it },
                        onWorkout = { workoutFor = it },
                        onRead = { readOpen = ReadOpen.ForTask(it) },
                        onAddPhoto = { addingPhoto = true },
                        onMeditate = { meditateFor = it.id },
                        onViewPhoto = { viewing = it },
                        onPick = { picking = it },
                        onProfile = { page = SettingsPage.HUB },
                        onConnectHealth = connectHealth,
                    )
                }
                NavClearance()
            }

            if (run != null) {
                if (page == null) FloatingNav(tab, tabs) { tab = it }
                val p = compute(run, state.days, state.clock.today)
                val pause = state.pause
                val miss = pendingMiss(run, p, paused = pause != null)
                val shown = state.photos.firstOrNull { it.meta.id == viewing }

                Sheet(workoutFor != null, onDismiss = { workoutFor = null }) {
                    workoutFor?.let { t ->
                        val hw = state.health.data?.workouts.orEmpty()
                        WorkoutSheetBody(
                            t, workoutsOn(hw, p.today), suggestWorkout(t, hw, state.days[p.today], p.today),
                            onCancel = { workoutFor = null },
                        ) { w ->
                            workoutFor = null
                            vm.logWorkout(t, w)
                        }
                    }
                }
                Sheet(readOpen != null, onDismiss = { readOpen = null }) {
                    readOpen?.let { r ->
                        val task = (r as? ReadOpen.ForTask)?.task
                        ReadSheetBody(
                            task?.label ?: "Your book", state.meta.reading,
                            onCancel = { readOpen = null },
                            onFinishBook = vm::finishCurrentBook,
                        ) { title, page, total ->
                            vm.saveRead(task, title, page, total).also { if (it == null) readOpen = null }
                        }
                    }
                }
                Sheet(addingPhoto, onDismiss = { addingPhoto = false }) {
                    if (addingPhoto) {
                        AddPhotoSheetBody(state.clock.today, onCancel = { addingPhoto = false }) { uri, date, angle, source, onDone, onError ->
                            vm.addPhoto(uri, date, angle, source, onDone = { onDone(); addingPhoto = false }, onError = onError)
                        }
                    }
                }
                Sheet(shown != null, onDismiss = { viewing = null }) {
                    if (shown != null) {
                        PhotoViewerBody(
                            shown, photoDayText(state.attempts, shown.meta.date), pinnedBefore = state.meta.pins.before == shown.meta.id,
                            onAngle = { vm.setPhotoAngle(shown.meta.id, it) },
                            onPinBefore = { vm.pinPhoto(true, shown.meta.id); viewing = null },
                            onDelete = { viewing = null; vm.deletePhoto(shown.meta.id) },
                            onClose = { viewing = null },
                        )
                    }
                }
                Sheet(picking != null, onDismiss = { picking = null }) {
                    picking?.let { before ->
                        val pair = photoPair(state.photos.map { it.meta }, run, state.meta.pins)
                        PickPhotoSheetBody(
                            state.attempts, state.photos, before, (if (before) pair.before else pair.after)?.id,
                            onPin = { id -> vm.pinPhoto(before, id); picking = null },
                            onCancel = { picking = null },
                        )
                    }
                }
                val meditating = run.tasks.firstOrNull { it.id == meditateFor }
                Sheet(meditating != null && !choosingApp, onDismiss = ::closeMeditate) {
                    if (meditating != null) {
                        val away = leftAt?.let { l -> if (backAt > l) ((backAt - l) / 60_000).toInt() else null }
                        MeditateSheetBody(
                            meditating, meditateLabel, mindfulOn(state.health.data?.mindful.orEmpty(), p.today), away,
                            onOpenApp = ::openMeditation,
                            onChooseApp = { choosingApp = true },
                            onCancel = ::closeMeditate,
                        ) {
                            if (state.days[p.today]?.isDone(meditating.id) != true) vm.toggleTask(meditating)
                            closeMeditate()
                        }
                    }
                }
                Sheet(choosingApp, onDismiss = { choosingApp = false }) {
                    if (choosingApp) {
                        AppPickerSheetBody(meditateApp, onPick = { pkg, label -> choosingApp = false; vm.setMeditateApp(pkg, label) }) { choosingApp = false }
                    }
                }
                Sheet(ending, onDismiss = { ending = false }) {
                    if (ending) EndSheetBody(keep, onCancel = { ending = false }) { ending = false; page = null; tab = Tab.Home; vm.endChallenge() }
                }
                Sheet(attempts, onDismiss = { attempts = false }) {
                    if (attempts) AttemptsSheetBody(state.history) { attempts = false }
                }
                Sheet(miss != null, onDismiss = null) {
                    if (miss != null) {
                        MissedSheetBody(
                            run, state.days, miss, keep, passesLeft(run, state.days, state.clock.today),
                            onFix = { vm.backfillDay(miss) }, onRestart = vm::restartRun,
                        )
                    }
                }
                // A break: the sheet that starts one (or makes it longer), and the welcome back when it's over.
                val over = pause as? PauseState.Over
                Sheet(pausing, onDismiss = { pausing = false }) {
                    if (pausing) {
                        PauseSheetBody(run, over?.pause, state.clock.today, onCancel = { pausing = false }) { reason, days ->
                            vm.pause(reason, days).also { if (it == null) { pausing = false; page = null; tab = Tab.Home } }
                        }
                    }
                }
                Sheet(over != null && !pausing, onDismiss = null) {
                    if (over != null) {
                        BackSheetBody(run, over.pause, keep, onLonger = { pausing = true }) { page = null; tab = Tab.Home; vm.restartAfterPause() }
                    }
                }
            }

            // Custom challenge: choosing the daily tasks.
            Sheet(run == null && choosingTasks, onDismiss = { choosingTasks = false }) {
                if (choosingTasks) CustomTasksSheetBody(picks) { choosingTasks = false }
            }

            ConfettiHost(confetti)
            Toasts(vm.toasts)
        }
    }
}

/** The four tabs of a running challenge. */
@Composable
private fun RunTabs(
    vm: AppViewModel,
    state: AppState,
    tab: Tab,
    weekOffset: Int,
    onTab: (Tab) -> Unit,
    onWeekOffset: (Int) -> Unit,
    onWorkout: (TaskDef) -> Unit,
    onRead: (TaskDef) -> Unit,
    onAddPhoto: () -> Unit,
    onMeditate: (TaskDef) -> Unit,
    onViewPhoto: (String) -> Unit,
    onPick: (before: Boolean) -> Unit,
    onProfile: () -> Unit,
    onConnectHealth: () -> Unit,
) {
    val run = state.run ?: return
    val p = compute(run, state.days, state.clock.today)
    // Switching tabs: the new one fades in, nudged in from the side of the tab that was tapped.
    AnimatedContent(
        tab,
        transitionSpec = {
            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
            (fadeIn(tween(220, delayMillis = 60)) + slideInHorizontally(tween(280)) { dir * it / 10 }) togetherWith
                (fadeOut(tween(110)) + slideOutHorizontally(tween(200)) { -dir * it / 14 }) using SizeTransform(clip = false) { _, _ -> snap() }
        },
        label = "tab",
    ) { shown ->
        when (shown) {
            Tab.Home -> Home(
                run, state.days, p, state.clock.hour,
                profileThumb = state.profileThumb,
                food = state.food,
                reading = state.meta.reading,
                health = state.health,
                onTask = { t ->
                    val at = state.days[p.today]?.done?.get(t.id)
                    when {
                        // Ticked by a photo or by the free first night: nothing to untick here.
                        at == TICK_PHOTO -> { vm.say("Today's photo counts. It's in Photos."); onTab(Tab.Photos) }
                        at == TICK_FREE -> vm.say("Day 1's sleep is free: last night came before the start.")
                        at != null -> vm.toggleTask(t)
                        t.kind == TaskKind.WORKOUT -> onWorkout(t)
                        t.kind == TaskKind.READ -> onRead(t)
                        t.kind == TaskKind.MIND -> onMeditate(t)
                        // Sample data can't store photos, so there the task just ticks (as in v2).
                        t.kind == TaskKind.PHOTO && !state.sample -> onAddPhoto()
                        else -> vm.toggleTask(t)
                    }
                },
                onOpenWeek = { onTab(Tab.Week) },
                onOpenFood = { onTab(Tab.Food) },
                onOpenProfile = onProfile,
                onConnectHealth = onConnectHealth,
                pause = state.pause as? PauseState.Active,
                onEndPause = vm::endPauseEarly,
                foodOn = state.foodOn,
            )
            Tab.Week -> Week(run, state.days, p, weekOffset, state.pause?.pause) { onWeekOffset(it.coerceAtMost(0)) }
            Tab.Food -> Food(state.food, state.health, onRefresh = { vm.refreshHealth(force = true) }, onConnect = onConnectHealth)
            Tab.Photos -> Photos(
                run, state.attempts, state.photos, state.health.data, state.meta.pins,
                onAdd = onAddPhoto, onOpen = { onViewPhoto(it.meta.id) }, onPick = onPick,
            )
        }
    }
}
