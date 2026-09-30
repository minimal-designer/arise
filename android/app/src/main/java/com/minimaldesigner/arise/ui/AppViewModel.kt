package com.minimaldesigner.arise.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minimaldesigner.arise.AriseApp
import com.minimaldesigner.arise.core.Angle
import com.minimaldesigner.arise.core.Attempt
import com.minimaldesigner.arise.core.PauseCheck
import com.minimaldesigner.arise.core.PauseState
import com.minimaldesigner.arise.core.checkPause
import com.minimaldesigner.arise.core.passesLeft
import com.minimaldesigner.arise.core.pauseState
import com.minimaldesigner.arise.core.Programs
import com.minimaldesigner.arise.core.stored
import com.minimaldesigner.arise.core.withDerivedTicks
import com.minimaldesigner.arise.ui.theme.AccentPref
import com.minimaldesigner.arise.core.DayRecord
import com.minimaldesigner.arise.core.ReadCheck
import com.minimaldesigner.arise.core.ReadLog
import com.minimaldesigner.arise.core.checkRead
import com.minimaldesigner.arise.core.finishBook
import com.minimaldesigner.arise.data.AppMeta
import com.minimaldesigner.arise.data.PastRun
import com.minimaldesigner.arise.ui.theme.NumStyle
import java.io.File
import com.minimaldesigner.arise.core.PhotoSource
import com.minimaldesigner.arise.core.TaskKind
import com.minimaldesigner.arise.data.FoodApi
import com.minimaldesigner.arise.data.FoodState
import com.minimaldesigner.arise.data.HealthState
import com.minimaldesigner.arise.data.Reminders
import com.minimaldesigner.arise.data.Photo
import com.minimaldesigner.arise.data.Settings
import com.minimaldesigner.arise.core.Days
import com.minimaldesigner.arise.core.Run
import com.minimaldesigner.arise.core.Sample
import com.minimaldesigner.arise.core.StartChoice
import com.minimaldesigner.arise.core.StartResult
import com.minimaldesigner.arise.core.TaskDef
import com.minimaldesigner.arise.core.Workout
import com.minimaldesigner.arise.core.backfill
import com.minimaldesigner.arise.core.compute
import com.minimaldesigner.arise.core.isDayCleared
import com.minimaldesigner.arise.core.restart
import com.minimaldesigner.arise.core.startRun
import com.minimaldesigner.arise.core.toggle
import com.minimaldesigner.arise.ui.theme.ThemePref
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class Clock(val today: LocalDate, val hour: Int)

data class AppState(
    val loading: Boolean = true,
    val run: Run? = null,
    val days: Days = emptyMap(),
    val photos: List<Photo> = emptyList(),
    val clock: Clock = Clock(LocalDate.now(), LocalDateTime.now().hour),
    val settings: Settings = Settings(),
    val sample: Boolean = false,
    val food: FoodState = FoodState(),
    val health: HealthState = HealthState(),
    val meta: AppMeta = AppMeta(),
    val history: List<PastRun> = emptyList(),
    /** The profile photo's thumbnail, if one is set. */
    val profileThumb: File? = null,
) {
    val theme: ThemePref get() = settings.theme

    /** The live challenge's break, if any. Sample mode never has one. */
    val pause: PauseState? get() = if (sample) null else pauseState(meta.pause, clock.today)

    /** This attempt and the past ones, for placing photos in their own attempt's weeks. */
    val attempts: List<Attempt>
        get() = listOfNotNull(run?.let { Attempt(it.startDate, null, "") }) +
            history.map { Attempt(it.start, it.end, "${Programs.of(it.program).name} · attempt ${it.attempt}") }
}

private data class SampleData(val run: Run?, val days: Days)

private data class Stored(val run: Run?, val days: Days, val photos: List<Photo>, val meta: AppMeta, val history: List<PastRun>)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as AriseApp).challenges
    private val prefs = (app as AriseApp).prefs
    private val food = (app as AriseApp).food
    private val backups = (app as AriseApp).backups
    val health = (app as AriseApp).health

    /** A line while a backup, restore or import runs (Settings shows it and disables Data). */
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val sample = MutableStateFlow<SampleData?>(null)
    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toasts: SharedFlow<String> = _toasts

    /** Re-reads the clock every 30 s so the day rolls over at midnight and "At risk tonight" turns on at 8 pm. */
    private val clock = flow {
        while (true) {
            val now = LocalDateTime.now()
            emit(Clock(now.toLocalDate(), now.hour))
            delay(30_000)
        }
    }.distinctUntilChanged()

    private val photoStore = (app as AriseApp).photoStore

    private val stored = combine(repo.run, repo.liveDays, repo.photos, repo.meta, repo.history) { r, d, p, m, h -> Stored(r, d, p, m, h) }

    private val outside = combine(food.state, health.state) { f, h -> f to h }

    val state: StateFlow<AppState> = combine(stored, clock, prefs.settings, sample, outside) { st, c, settings, s, (f, h) ->
        // Sample mode has no photos: nothing in it is saved. Food, Health and the profile are the real ones either way.
        val thumb = st.meta.profileThumb(photoStore)?.takeIf { it.exists() }
        if (s != null) AppState(false, s.run, s.days, emptyList(), c, settings, sample = true, food = f, health = h, meta = st.meta, profileThumb = thumb)
        else AppState(false, st.run, st.days, st.photos, c, settings, food = f, health = h, meta = st.meta, history = st.history, profileThumb = thumb)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppState())

    init {
        // Show the saved copy at once, then sync at start-up and whenever the address or token changes.
        viewModelScope.launch {
            food.load()
            prefs.settings
                .distinctUntilChanged { a, b -> a.nasUrl == b.nasUrl && a.token == b.token }
                .collect { food.sync(it, force = true) }
        }
        // Re-arm the evening reminder at start-up and whenever it's switched or moved.
        viewModelScope.launch {
            prefs.settings
                .distinctUntilChanged { a, b -> a.reminderOn == b.reminderOn && a.reminderAt == b.reminderAt }
                .collect { Reminders.schedule(getApplication(), it) }
        }
        // And the break-over notification whenever a pause starts, moves or ends.
        viewModelScope.launch {
            repo.meta.map { it.pause }.distinctUntilChanged().collect { Reminders.schedulePauseEnd(getApplication(), it) }
        }
    }

    fun say(msg: String) {
        _toasts.tryEmit(msg)
    }

    private fun now() = LocalDateTime.now().toString()

    /** Writes a day to Room, or to the in-memory sample. Derived ticks are never kept. */
    private suspend fun saveDay(date: LocalDate, day: DayRecord) {
        val s = sample.value
        if (s != null) sample.value = s.copy(days = s.days + (date to day.stored())) else repo.setDay(date, day)
    }

    private fun milestoneCheck(before: Run, daysBefore: Days, daysAfter: Days, today: LocalDate) {
        val was = compute(before, daysBefore, today).hit.size
        val now = compute(before, daysAfter, today)
        if (now.hit.size > was) {
            val m = now.hit.last()
            viewModelScope.launch {
                delay(700)
                say("Milestone reached: ${m.name} · ${now.cleared} days cleared")
            }
        }
    }

    // ---- onboarding ----

    fun start(choice: StartChoice, onNoTasks: () -> Unit) {
        when (val r = startRun(choice)) {
            StartResult.NoTasks -> onNoTasks()
            is StartResult.Ok -> viewModelScope.launch {
                // Starting for real always leaves sample mode.
                sample.value = null
                val today = state.value.clock.today
                repo.start(r.run, today)
                say(if (r.run.startDate == today) "Day 1 starts today. You've got this." else "Day 1 is ${fmtShort(r.run.startDate)}.")
            }
        }
    }

    fun loadSample() {
        val today = state.value.clock.today
        sample.value = SampleData(Sample.run(today), Sample.days(today))
    }

    fun exitSample() {
        sample.value = null
    }

    // ---- tasks ----

    /** Ticks or unticks a task. Workouts go through [logWorkout], reading through [saveRead]. */
    fun toggleTask(task: TaskDef, workout: Workout? = null, read: ReadLog? = null) {
        val st = state.value
        val run = st.run ?: return
        val today = st.clock.today
        val before = st.days[today] ?: DayRecord()
        val wasDone = before.isDone(task.id)
        val after = toggle(before, task, now(), workout, read)
        val daysAfter = st.days + (today to after)
        viewModelScope.launch {
            saveDay(today, after)
            say(
                when {
                    wasDone -> "Unchecked: ${task.label}"
                    isDayCleared(run, daysAfter, today) -> "All tasks done. Day cleared."
                    else -> "Done: ${task.label}"
                },
            )
            if (!wasDone) milestoneCheck(run, st.days, daysAfter, today)
        }
    }

    fun logWorkout(task: TaskDef, workout: Workout) = toggleTask(task, workout)

    // ---- reading ----

    /**
     * The Read sheet's Done. With a [task] it also ticks today's Read task. Returns an error
     * line for the sheet, or null when saved.
     */
    fun saveRead(task: TaskDef?, title: String, page: Int, total: Int): String? {
        val current = state.value.meta.reading
        return when (val r = checkRead(current, title, page, total, state.value.clock.today)) {
            ReadCheck.NoTitle -> "Add the book's title."
            ReadCheck.NoPage -> "Which page are you on?"
            ReadCheck.PastEnd -> "That page is past the end of the book."
            is ReadCheck.Ok -> {
                viewModelScope.launch {
                    if (sample.value == null) repo.setReading(r.reading)
                    if (task != null && state.value.days[state.value.clock.today]?.isDone(task.id) != true) toggleTask(task, read = r.log)
                    if (r.finishedBook) { delay(600); say("Finished ${r.log.title}") } else if (task == null) say("Book saved")
                }
                null
            }
        }
    }

    fun finishCurrentBook() {
        viewModelScope.launch {
            repo.setReading(finishBook(state.value.meta.reading, state.value.clock.today))
            say("Book finished")
        }
    }

    // ---- profile ----

    fun setName(name: String) {
        val n = name.trim()
        if (n.isEmpty() || sample.value != null) return
        viewModelScope.launch { repo.rename(n); say("Name saved") }
    }

    fun setBody(heightCm: Double?, goalKg: Double?) {
        viewModelScope.launch { repo.setProfile(state.value.meta.profile.copy(heightCm = heightCm, goalKg = goalKg)) }
    }

    fun setProfilePhoto(uri: Uri) {
        viewModelScope.launch {
            try {
                repo.setProfilePhoto(uri, state.value.meta.profile)
                say("Profile photo saved")
            } catch (e: Exception) {
                say(e.message ?: "Couldn't save that photo.")
            }
        }
    }

    // ---- photo pins ----

    /** Pins [id] as the before or after photo; null goes back to the default. */
    fun pinPhoto(before: Boolean, id: String?) {
        val pins = state.value.meta.pins
        val next = if (before) pins.copy(before = id, after = pins.after.takeIf { it != id }) else pins.copy(after = id, before = pins.before.takeIf { it != id })
        viewModelScope.launch {
            repo.setPins(next)
            say(
                when {
                    id == null && before -> "Before: first photo of this attempt"
                    id == null -> "After: your latest photo"
                    before -> "Pinned as before"
                    else -> "Pinned as after"
                },
            )
        }
    }

    // ---- missed day (75 Hard) ----

    /** "I did finish it": spends one of the attempt's free passes. With none left, only a restart is offered. */
    fun backfillDay(date: LocalDate) {
        val st = state.value
        val run = st.run ?: return
        val left = passesLeft(run, st.days, st.clock.today)
        if (left == 0) return
        viewModelScope.launch {
            saveDay(date, backfill(run, st.days[date]))
            say(if (left == 1) "Day fixed. That was your last free pass." else "Day fixed. ${left - 1} free ${if (left - 1 == 1) "pass" else "passes"} left.")
        }
    }

    fun restartRun() {
        val st = state.value
        val run = st.run ?: return
        val today = st.clock.today
        val next = restart(run, today)
        viewModelScope.launch {
            val s = sample.value
            if (s != null) sample.value = SampleData(next, emptyMap())
            else repo.restart(next, today, compute(run, st.days, today).cleared)
            say("Attempt ${next.attempt}: Day 1 is today. Your photos are kept.")
        }
    }

    // ---- pause ----

    /**
     * Starts a break of [days] days from today, or (from the welcome-back sheet) moves the
     * end of the current one. Returns an error line for the sheet, or null when saved.
     */
    fun pause(reason: String, days: Int): String? {
        val st = state.value
        val run = st.run ?: return null
        if (st.sample) return "Exit the sample first: it isn't saved, so it can't be paused."
        val today = st.clock.today
        if (today.isBefore(run.startDate)) return "The challenge hasn't started yet. Change its start instead."
        return when (val r = checkPause(reason, days, today)) {
            PauseCheck.NoName -> "Give the break a name."
            PauseCheck.BadLength -> "A break is 1 to 60 days."
            is PauseCheck.Ok -> {
                // Moving an existing break keeps the day it began.
                val p = st.meta.pause?.let { r.pause.copy(from = it.from) } ?: r.pause
                viewModelScope.launch {
                    repo.setPause(p)
                    say("Paused until ${fmtShort(p.untilDate)}. Enjoy your ${p.phrase}.")
                }
                null
            }
        }
    }

    /** Back before the planned day: the break ends today, so the restart sheet shows. */
    fun endPauseEarly() {
        val p = state.value.meta.pause ?: return
        viewModelScope.launch { repo.setPause(p.copy(until = state.value.clock.today.toString())) }
    }

    /** After a break: the challenge starts again at Day 1, as the next attempt. */
    fun restartAfterPause() {
        val st = state.value
        val run = st.run ?: return
        val today = st.clock.today
        val next = restart(run, today)
        viewModelScope.launch {
            repo.restart(next, today, compute(run, st.days, today).cleared, "paused")
            say("Attempt ${next.attempt}: Day 1 is today. Welcome back.")
        }
    }

    // ---- settings ----

    fun endChallenge() {
        val st = state.value
        val run = st.run ?: return
        viewModelScope.launch {
            if (sample.value != null) sample.value = null
            else repo.end(st.clock.today, compute(run, st.days, st.clock.today).cleared)
        }
    }

    fun setTheme(t: ThemePref) {
        viewModelScope.launch { prefs.setTheme(t) }
    }

    fun setNumbers(n: NumStyle) {
        viewModelScope.launch { prefs.setNumbers(n) }
    }

    fun setAccent(a: AccentPref) {
        viewModelScope.launch { prefs.setAccent(a) }
    }

    fun setCelebrate(on: Boolean) {
        viewModelScope.launch { prefs.setCelebrate(on) }
    }

    fun setMeditateApp(pkg: String, label: String) {
        viewModelScope.launch {
            prefs.setMeditateApp(pkg)
            say("Meditate opens $label")
        }
    }

    fun setReminder(on: Boolean, at: LocalTime) {
        viewModelScope.launch {
            prefs.setReminder(on, at)
            if (on) say("Reminder set for ${fmtTime(at)} if tasks are still open")
        }
    }

    fun saveFoodSync(url: String, token: String) {
        viewModelScope.launch {
            prefs.setFoodSync(url, token)
            say("Food sync settings saved")
        }
    }

    /** Refreshes the food log: always when [force] (Sync now), else only if the copy is stale. */
    fun syncFood(force: Boolean) {
        viewModelScope.launch { food.sync(prefs.settings.first(), force) }
    }

    /** Re-reads Health Connect: always when [force], else only if the last read is getting old. */
    fun refreshHealth(force: Boolean) {
        viewModelScope.launch { health.refresh(force, state.value.run?.startDate) }
    }

    /** The answer from Health Connect's permission screen. */
    fun healthGranted(granted: Set<String>) {
        viewModelScope.launch {
            health.refresh(force = true, state.value.run?.startDate)
            say(if (granted.isEmpty()) "Nothing shared from Health Connect" else "Health Connect connected")
        }
    }

    /** Checks the server with the values currently typed (saved or not). */
    suspend fun testFoodSync(url: String, token: String): String = FoodApi.check(url, token)

    // ---- photos ----

    /**
     * Stores a photo. The photo task needs no tick: any day with a photo counts as done
     * (see the repository's liveDays). [onDone] runs on success so the sheet can close.
     */
    fun addPhoto(uri: Uri, date: LocalDate, angle: Angle, source: PhotoSource, onDone: () -> Unit, onError: (String) -> Unit) {
        if (sample.value != null) {
            onError("Sample data isn't saved, so photos can't be added. Exit the sample first.")
            return
        }
        // Before the save: once it lands, the day already reads as done.
        val st = state.value
        viewModelScope.launch {
            try {
                repo.addPhoto(uri, date, angle, source)
            } catch (e: Exception) {
                onError(e.message ?: "Couldn't save the photo.")
                return@launch
            }
            onDone()
            val run = st.run
            val today = st.clock.today
            val photoTask = run?.tasks?.firstOrNull { it.kind == TaskKind.PHOTO }
            val inRun = run != null && !date.isBefore(run.startDate) && !date.isAfter(today)
            if (run != null && photoTask != null && inRun && st.days[date]?.isDone(photoTask.id) != true) {
                val after = withDerivedTicks(run, st.days, setOf(date), null)
                say(if (date == today && isDayCleared(run, after, today)) "All tasks done. Day cleared." else "Done: ${photoTask.label}")
                milestoneCheck(run, st.days, after, today)
            } else {
                say("Photo added")
            }
        }
    }

    fun setPhotoAngle(id: String, angle: Angle) {
        viewModelScope.launch { repo.setPhotoAngle(id, angle) }
    }

    fun deletePhoto(id: String) {
        viewModelScope.launch {
            repo.deletePhoto(id)
            say("Photo deleted")
        }
    }

    // ---- data: backup, restore, 75 Hard import ----

    private val resolver get() = getApplication<Application>().contentResolver

    /** Runs one Data action with [label] showing, and turns any failure into a toast. */
    private fun dataJob(label: String, block: suspend () -> String) {
        if (_busy.value != null) return
        if (sample.value != null) {
            say("Exit the sample first: it isn't saved, so there's nothing to back up or import into.")
            return
        }
        _busy.value = label
        viewModelScope.launch {
            val msg = try {
                block()
            } catch (e: Exception) {
                e.message ?: "That didn't work (${e.javaClass.simpleName})."
            }
            _busy.value = null
            say(msg)
        }
    }

    fun backup(to: Uri) = dataJob("Backing up…") {
        val version = getApplication<Application>().let { it.packageManager.getPackageInfo(it.packageName, 0).versionName.orEmpty() }
        val n = resolver.openOutputStream(to, "wt")?.use { backups.export(it, version) }
            ?: error("Couldn't open that file to write.")
        "Backed up, with $n ${if (n == 1) "photo" else "photos"}"
    }

    fun restore(from: Uri) = dataJob("Restoring…") {
        val b = backups.restore { resolver.openInputStream(from) ?: error("Couldn't open that file.") }
        "Restored the backup from ${b.createdAt.take(10)}: ${b.runs.size} ${if (b.runs.size == 1) "challenge" else "challenges"}, ${b.photos.size} photos"
    }

    fun importHard75(from: Uri) = dataJob("Importing photos…") {
        val r = backups.importHard75({ resolver.openInputStream(from) ?: error("Couldn't open that file.") }, ZoneId.systemDefault())
        when {
            r.added == 0 && r.already == 0 && r.failed == 0 -> "No 75 Hard photos found in that zip."
            else -> buildList {
                add("Imported ${r.added} ${if (r.added == 1) "photo" else "photos"}")
                if (r.already > 0) add("${r.already} already here")
                if (r.failed > 0) add("${r.failed} couldn't be read")
            }.joinToString(" · ")
        }
    }
}
