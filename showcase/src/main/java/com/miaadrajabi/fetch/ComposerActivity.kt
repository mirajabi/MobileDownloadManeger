package com.miaadrajabi.fetch

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.miaadrajabi.downloader.ChecksumAlgorithm
import com.miaadrajabi.downloader.ScheduleTime
import com.miaadrajabi.downloader.Weekday
import com.miaadrajabi.fetch.databinding.ActivityComposerBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ComposerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityComposerBinding
    private var year: Int = 0
    private var month: Int = 1
    private var day: Int = 1
    private var hour: Int = 9
    private var minute: Int = 0
    private var pendingStart: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComposerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        defaultClock()
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (!shared.isNullOrBlank()) {
            binding.links.setText(shared)
        }
        binding.links.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                refreshLinks()
            }
        })
        binding.paste.setOnClickListener { pasteClipboard() }
        binding.close.setOnClickListener { finish() }
        binding.engine.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.whenGroup.setOnCheckedChangeListener { _, checkedId -> showPanel(checkedId) }
        binding.pickDate.setOnClickListener { pickDate() }
        binding.pickTime.setOnClickListener { pickTime() }
        binding.pickWeekTime.setOnClickListener { pickTime() }
        binding.checksumGroup.setOnCheckedChangeListener { _, _ -> refreshChecksumHint() }
        binding.start.setOnClickListener { submit() }
        val settings = EngineSettings.load(this)
        if (settings.useAlarm) binding.clockAlarm.isChecked = true else binding.clockWork.isChecked = true
        showPanel(binding.whenGroup.checkedChipId)
        refreshLinks()
        refreshChecksumHint()
        renderClock()
    }

    override fun onStart() {
        super.onStart()
        FetchForeground.enter()
    }

    override fun onResume() {
        super.onResume()
        ClipOffer.present(this)
    }

    override fun onStop() {
        FetchForeground.leave()
        super.onStop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_STORAGE) return
        val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        val action = pendingStart
        pendingStart = null
        if (granted && action != null) action() else if (!granted) {
            snack("Storage permission was denied. The file can still land in app storage.")
        }
    }

    private fun pasteClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip: ClipData? = clipboard.primaryClip
        val text = clip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (text.isBlank()) {
            snack("The clipboard is empty.")
            return
        }
        val current = binding.links.text?.toString().orEmpty()
        val next = if (current.isBlank()) text else current.trimEnd() + "\n" + text
        binding.links.setText(next)
        binding.links.setSelection(binding.links.text?.length ?: 0)
    }

    private fun refreshLinks() {
        val urls = LinkParser.parse(binding.links.text?.toString().orEmpty())
        binding.detected.text = when (urls.size) {
            0 -> "No link yet. Paste one address, or several."
            1 -> "1 link ready."
            else -> "${urls.size} links ready. Each one becomes its own download."
        }
        val single = urls.size == 1
        binding.fileNameLayout.isEnabled = single
        if (!single) {
            binding.fileName.setText("")
            binding.fileNameLayout.hint = "File name is taken from each link"
            binding.landing.text = "Each link keeps its own file name."
        } else if (binding.fileName.text.isNullOrBlank()) {
            binding.fileNameLayout.hint = "File name"
            binding.fileName.setText(LinkParser.fileNameFrom(urls[0]))
        }
        if (single) {
            val sample = binding.fileName.text?.toString().orEmpty()
            showLanding(EngineSettings.load(this), sample.ifBlank { "download.bin" })
        }
    }

    private fun showLanding(settings: EngineSettings, fileName: String = "download.bin") {
        binding.landing.text = "Saves as  " + friendlyPlace(DownloadDesk.previewPath(this, settings, fileName))
    }

    private fun showPanel(checkedId: Int) {
        binding.panelMinutes.visibility = if (checkedId == R.id.whenMinutes) View.VISIBLE else View.GONE
        binding.panelDate.visibility = if (checkedId == R.id.whenDate) View.VISIBLE else View.GONE
        binding.panelWeek.visibility = if (checkedId == R.id.whenWeek) View.VISIBLE else View.GONE
        binding.panelRepeat.visibility = if (checkedId == R.id.whenRepeat) View.VISIBLE else View.GONE
        val clock = checkedId == R.id.whenMinutes || checkedId == R.id.whenDate || checkedId == R.id.whenWeek
        binding.clockBlock.visibility = if (clock) View.VISIBLE else View.GONE
        binding.whenHelp.text = when (checkedId) {
            R.id.whenMinutes -> "Waits this many minutes, then starts once."
            R.id.whenDate -> "One calendar day and a clock time. It does not repeat."
            R.id.whenWeek -> "The next matching weekday. Leave every day unselected to use the clock each day."
            R.id.whenRepeat -> "WorkManager repeats this. Anything under 15 minutes is raised to 15. AlarmManager is not used for a repeat."
            else -> "Starts as soon as you leave this screen. The foreground notification appears with it."
        }
        binding.start.text = when (checkedId) {
            R.id.whenRepeat -> "Repeat"
            R.id.whenNow -> "Start now"
            else -> "Schedule"
        }
    }

    private fun pickDate() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Which day?")
            .setSelection(utcMillis(year, month, day))
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.timeInMillis = selection
            year = calendar.get(Calendar.YEAR)
            month = calendar.get(Calendar.MONTH) + 1
            day = calendar.get(Calendar.DAY_OF_MONTH)
            renderClock()
        }
        picker.show(supportFragmentManager, "fetch-date")
    }

    private fun pickTime() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(hour)
            .setMinute(minute)
            .setTitleText("What time?")
            .build()
        picker.addOnPositiveButtonClickListener {
            hour = picker.hour
            minute = picker.minute
            renderClock()
        }
        picker.show(supportFragmentManager, "fetch-time")
    }

    private fun submit() {
        val urls = LinkParser.parse(binding.links.text?.toString().orEmpty())
        if (urls.isEmpty()) {
            snack("Add at least one http or https link.")
            return
        }
        val headerName = binding.headerName.text?.toString()?.trim().orEmpty()
        val headerValue = binding.headerValue.text?.toString()?.trim().orEmpty()
        if (headerName.equals("Range", ignoreCase = true) || headerName.equals("Content-Range", ignoreCase = true)) {
            snack("Leave Range to the library. Use the byte fields instead.")
            return
        }
        if (headerName.isNotBlank() && headerValue.isBlank()) {
            snack("That header needs a value.")
            return
        }
        val start = readLong(binding.rangeStart.text?.toString().orEmpty())
        val end = readLong(binding.rangeEnd.text?.toString().orEmpty())
        if (start is LongRead.Bad || end is LongRead.Bad) {
            snack("Byte fields need a whole number, or stay empty.")
            return
        }
        val rangeStart = (start as LongRead.Ok).value
        val rangeEnd = (end as LongRead.Ok).value
        if (rangeStart != null && rangeStart < 0L || rangeEnd != null && rangeEnd < 0L) {
            snack("A byte position cannot be negative.")
            return
        }
        if (rangeStart != null && rangeEnd != null && rangeEnd < rangeStart) {
            snack("The last byte has to be the same as the first, or later.")
            return
        }
        val algorithm = selectedAlgorithm()
        val checksumRaw = binding.checksum.text?.toString()?.trim().orEmpty()
        val checksum = if (checksumRaw.isEmpty()) null else checksumRaw
        if (checksum != null && !checksumMatches(checksum, algorithm)) {
            snack(checksumHint(algorithm))
            return
        }
        val mode = binding.whenGroup.checkedChipId
        val schedule = readSchedule(mode) ?: return
        val names = LinkParser.uniqueNames(urls, binding.fileName.text?.toString().orEmpty())
        val headers = LinkedHashMap<String, String>()
        val cookie = binding.cookie.text?.toString()?.trim().orEmpty()
        if (cookie.isNotBlank()) headers["Cookie"] = cookie
        if (headerName.isNotBlank()) headers[headerName] = headerValue
        val settings = EngineSettings.load(this).copy(useAlarm = binding.clockAlarm.isChecked)
        settings.save(this)
        ensureStorage {
            val store = (application as FetchApp).store
            for (index in urls.indices) {
                val request = DownloadDesk.newRequest(
                    url = urls[index],
                    fileName = names[index],
                    headers = headers,
                    checksum = checksum,
                    algorithm = algorithm,
                    rangeStart = rangeStart,
                    rangeEndInclusive = rangeEnd
                )
                val label = schedule.label
                val status = if (mode == R.id.whenNow) TransferStore.STATUS_QUEUED else TransferStore.STATUS_SCHEDULED
                val preview = DownloadDesk.previewPath(this, settings, request.fileName)
                store.insert(
                    Transfer(
                        id = request.id,
                        url = request.url,
                        fileName = request.fileName,
                        status = status,
                        message = if (mode == R.id.whenNow) "Waiting to start" else "Waiting for the clock",
                        whenLabel = label,
                        localPath = if (preview.startsWith("/")) preview else ""
                    )
                )
                when (mode) {
                    R.id.whenRepeat -> DownloadDesk.repeat(this, settings, request, schedule.repeatMinutes)
                    R.id.whenNow -> DownloadDesk.enqueue(this, settings, request)
                    else -> DownloadDesk.schedule(this, settings, request, schedule.time!!)
                }
            }
            finish()
        }
    }

    private fun readSchedule(mode: Int): SchedulePlan? {
        return when (mode) {
            R.id.whenMinutes -> {
                val plus = binding.minutes.text?.toString()?.toLongOrNull()
                if (plus == null || plus < 1L || plus > 10080L) {
                    snack("Use a wait between 1 minute and 7 days.")
                    null
                } else {
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.MINUTE, plus.toInt())
                    val time = ScheduleTime(
                        hour = calendar.get(Calendar.HOUR_OF_DAY),
                        minute = calendar.get(Calendar.MINUTE),
                        year = calendar.get(Calendar.YEAR),
                        month = calendar.get(Calendar.MONTH) + 1,
                        dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
                    )
                    SchedulePlan(time, "In $plus min", 0L)
                }
            }
            R.id.whenDate -> {
                SchedulePlan(
                    ScheduleTime(hour, minute, null, year, month, day),
                    dateLabel() + " at " + clockLabel(),
                    0L
                )
            }
            R.id.whenWeek -> {
                val weekday = selectedWeekday()
                val dayName = weekday?.name?.let { pretty(it) } ?: "Every day"
                SchedulePlan(
                    ScheduleTime(hour, minute, weekday),
                    "$dayName at ${clockLabel()}",
                    0L
                )
            }
            R.id.whenRepeat -> {
                val minutes = binding.repeatMinutes.text?.toString()?.toLongOrNull()
                if (minutes == null || minutes < 1L) {
                    snack("Say how many minutes between repeats.")
                    null
                } else {
                    val shown = if (minutes < 15L) 15L else minutes
                    SchedulePlan(null, "Every $shown min", minutes)
                }
            }
            else -> SchedulePlan(null, "Now", 0L)
        }
    }

    private fun selectedWeekday(): Weekday? {
        return when (binding.weekGroup.checkedChipId) {
            R.id.daySun -> Weekday.SUNDAY
            R.id.dayMon -> Weekday.MONDAY
            R.id.dayTue -> Weekday.TUESDAY
            R.id.dayWed -> Weekday.WEDNESDAY
            R.id.dayThu -> Weekday.THURSDAY
            R.id.dayFri -> Weekday.FRIDAY
            R.id.daySat -> Weekday.SATURDAY
            else -> null
        }
    }

    private fun selectedAlgorithm(): ChecksumAlgorithm {
        return when (binding.checksumGroup.checkedChipId) {
            R.id.sum512 -> ChecksumAlgorithm.SHA512
            R.id.sumMd5 -> ChecksumAlgorithm.MD5
            else -> ChecksumAlgorithm.SHA256
        }
    }

    private fun refreshChecksumHint() {
        binding.checksumLayout.hint = checksumHint(selectedAlgorithm())
    }

    private fun checksumHint(algorithm: ChecksumAlgorithm): String {
        val length = when (algorithm) {
            ChecksumAlgorithm.SHA512 -> 128
            ChecksumAlgorithm.MD5 -> 32
            else -> 64
        }
        return "Checksum, $length hex characters, or leave empty"
    }

    private fun checksumMatches(value: String, algorithm: ChecksumAlgorithm): Boolean {
        if (!value.matches(Regex("^[0-9a-fA-F]+$"))) return false
        val length = when (algorithm) {
            ChecksumAlgorithm.SHA512 -> 128
            ChecksumAlgorithm.MD5 -> 32
            else -> 64
        }
        return value.length == length
    }

    private fun readLong(raw: String): LongRead {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return LongRead.Ok(null)
        val value = trimmed.toLongOrNull() ?: return LongRead.Bad
        return LongRead.Ok(value)
    }

    private fun ensureStorage(then: () -> Unit) {
        val needsPermission = EngineSettings.load(this).publicDownloads &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            Build.VERSION.SDK_INT <= 29
        if (!needsPermission) {
            then()
            return
        }
        val missing = STORAGE.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (!missing) {
            then()
            return
        }
        pendingStart = then
        requestPermissions(STORAGE, REQUEST_STORAGE)
    }

    private fun defaultClock() {
        val soon = Calendar.getInstance()
        soon.add(Calendar.MINUTE, 30)
        hour = soon.get(Calendar.HOUR_OF_DAY)
        minute = soon.get(Calendar.MINUTE)
        val tomorrow = Calendar.getInstance()
        tomorrow.add(Calendar.DAY_OF_YEAR, 1)
        year = tomorrow.get(Calendar.YEAR)
        month = tomorrow.get(Calendar.MONTH) + 1
        day = tomorrow.get(Calendar.DAY_OF_MONTH)
    }

    private fun renderClock() {
        val date = dateLabel()
        val clock = clockLabel()
        binding.pickDate.text = date
        binding.pickTime.text = clock
        binding.pickWeekTime.text = clock
    }

    private fun dateLabel(): String {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month - 1)
        calendar.set(Calendar.DAY_OF_MONTH, day)
        val format = SimpleDateFormat("EEE, MMM d", Locale.US)
        return format.format(Date(calendar.timeInMillis))
    }

    private fun clockLabel(): String {
        return String.format(Locale.US, "%02d:%02d", hour, minute)
    }

    private fun pretty(raw: String): String {
        if (raw.isEmpty()) return raw
        val lower = raw.toLowerCase(Locale.US)
        return lower.substring(0, 1).toUpperCase(Locale.US) + lower.substring(1)
    }

    private fun utcMillis(year: Int, month: Int, day: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month - 1)
        calendar.set(Calendar.DAY_OF_MONTH, day)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun snack(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun friendlyPlace(path: String): String {
        val name = path.substringAfterLast('/')
        val parent = path.substringBeforeLast('/').substringAfterLast('/')
        val publicDownloads = path.contains("/emulated/0/Download/")
        return if (publicDownloads) "Public Downloads / $name" else "$parent / $name"
    }

    private data class SchedulePlan(
        val time: ScheduleTime?,
        val label: String,
        val repeatMinutes: Long
    )

    private sealed class LongRead {
        data class Ok(val value: Long?) : LongRead()
        object Bad : LongRead()
    }

    companion object {
        private const val REQUEST_STORAGE = 41
        private val STORAGE = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }
}
