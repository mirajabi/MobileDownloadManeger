package com.miaadrajabi.fetch

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.miaadrajabi.fetch.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val saved = EngineSettings.load(this)
        bind(saved)
        binding.close.setOnClickListener { finish() }
        binding.chunks.addOnChangeListener { _, value, _ ->
            binding.chunkValue.text = chunkLabel(value.toInt())
        }
        binding.minChunk.addOnChangeListener { _, value, _ ->
            binding.minChunkValue.text = "${value.toInt()} KB"
        }
        binding.attempts.addOnChangeListener { _, value, _ ->
            binding.attemptsValue.text = "${value.toInt()} tries"
        }
        binding.delay.addOnChangeListener { _, value, _ ->
            binding.delayValue.text = "${value.toInt()} sec, then longer"
        }
        binding.backoff.addOnChangeListener { _, value, _ ->
            binding.backoffValue.text = String.format(java.util.Locale.US, "%.1fx", value / 10f)
        }
        binding.freeSpace.addOnChangeListener { _, value, _ ->
            binding.freeValue.text = "Keep ${value.toInt()} MB free"
        }
        binding.destinationGroup.setOnCheckedChangeListener { _, checkedId ->
            showFolderField(checkedId)
            lockDestinationChoices()
        }
        binding.publicDownloads.setOnCheckedChangeListener { _, _ ->
            lockDestinationChoices()
        }
        binding.save.setOnClickListener { saveAndClose() }
        lockDestinationChoices()
    }

    private fun bind(saved: EngineSettings) {
        binding.chunks.value = saved.chunkCount.toFloat()
        binding.parallel.isChecked = saved.parallel
        binding.minChunk.value = saved.minChunkKb.toFloat().coerceIn(64f, 2048f)
        binding.attempts.value = saved.retryAttempts.toFloat()
        binding.delay.value = saved.retryDelaySeconds.toFloat()
        binding.backoff.value = saved.backoffTenths.toFloat()
        binding.channel.setText(saved.channelName)
        binding.showProgress.isChecked = saved.showProgress
        binding.persistent.isChecked = saved.persistent
        binding.publicDownloads.isChecked = saved.publicDownloads
        binding.publicFolder.setText(saved.publicFolder)
        binding.overwrite.isChecked = saved.overwrite
        binding.checkSpace.isChecked = saved.checkSpace
        binding.freeSpace.value = saved.minFreeMb.toFloat().coerceIn(1f, 512f)
        binding.promptInstaller.isChecked = saved.promptInstaller
        binding.verifySize.isChecked = saved.verifySize
        binding.verifyChecksum.isChecked = saved.verifyChecksum
        binding.verifyApk.isChecked = saved.verifyApk
        binding.verifyType.isChecked = saved.verifyType
        binding.verifySignature.isChecked = saved.verifySignature
        binding.folder.setText(saved.folder)
        when (saved.iconKey) {
            EngineSettings.ICON_TRAY -> binding.iconTray.isChecked = true
            EngineSettings.ICON_BOLT -> binding.iconBolt.isChecked = true
            EngineSettings.ICON_LAYERS -> binding.iconLayers.isChecked = true
            else -> binding.iconArrow.isChecked = true
        }
        when (saved.destination) {
            EngineSettings.DEST_SCOPED -> binding.destScoped.isChecked = true
            EngineSettings.DEST_CUSTOM -> binding.destCustom.isChecked = true
            else -> binding.destAuto.isChecked = true
        }
        if (saved.useAlarm) binding.defaultAlarm.isChecked = true else binding.defaultWork.isChecked = true
        val customFolder = saved.destination != EngineSettings.DEST_AUTO
        binding.folderLayout.visibility = if (customFolder) View.VISIBLE else View.GONE
        binding.folderLayout.hint = if (saved.destination == EngineSettings.DEST_CUSTOM) {
            "Absolute folder"
        } else {
            "Folder inside app storage"
        }
        binding.chunkValue.text = chunkLabel(saved.chunkCount)
        binding.minChunkValue.text = "${saved.minChunkKb} KB"
        binding.attemptsValue.text = "${saved.retryAttempts} tries"
        binding.delayValue.text = "${saved.retryDelaySeconds} sec, then longer"
        binding.backoffValue.text = String.format(java.util.Locale.US, "%.1fx", saved.backoffTenths / 10f)
        binding.freeValue.text = "Keep ${saved.minFreeMb} MB free"
    }

    private fun saveAndClose() {
        val folder = binding.folder.text?.toString()?.trim().orEmpty()
        val destination = when (binding.destinationGroup.checkedChipId) {
            R.id.destScoped -> EngineSettings.DEST_SCOPED
            R.id.destCustom -> EngineSettings.DEST_CUSTOM
            else -> EngineSettings.DEST_AUTO
        }
        if (destination == EngineSettings.DEST_CUSTOM && !folder.startsWith("/")) {
            binding.folderLayout.error = "Use a full path, starting with /. A name like Fetch is not a folder on the phone."
            return
        }
        val publicFolder = binding.publicFolder.text?.toString()?.trim().orEmpty()
        if (publicFolder.contains("..") || publicFolder.startsWith("/") || publicFolder.contains("\\")) {
            binding.publicFolderLayout.error = "Use a folder name inside Downloads, such as Fetch."
            return
        }
        val channel = binding.channel.text?.toString()?.trim().orEmpty()
        val settings = EngineSettings(
            chunkCount = binding.chunks.value.toInt(),
            parallel = binding.parallel.isChecked,
            minChunkKb = binding.minChunk.value.toInt(),
            retryAttempts = binding.attempts.value.toInt(),
            retryDelaySeconds = binding.delay.value.toInt(),
            backoffTenths = binding.backoff.value.toInt(),
            channelName = if (channel.isBlank()) "Fetch" else channel,
            iconKey = selectedIcon(),
            showProgress = binding.showProgress.isChecked,
            persistent = binding.persistent.isChecked,
            destination = destination,
            folder = if (folder.isBlank()) "Fetch" else folder,
            publicDownloads = binding.publicDownloads.isChecked && destination == EngineSettings.DEST_AUTO,
            publicFolder = publicFolder,
            overwrite = binding.overwrite.isChecked,
            checkSpace = binding.checkSpace.isChecked,
            minFreeMb = binding.freeSpace.value.toInt(),
            promptInstaller = binding.promptInstaller.isChecked,
            verifySize = binding.verifySize.isChecked,
            verifyChecksum = binding.verifyChecksum.isChecked,
            verifyApk = binding.verifyApk.isChecked,
            verifyType = binding.verifyType.isChecked,
            verifySignature = binding.verifySignature.isChecked,
            useAlarm = binding.defaultAlarm.isChecked
        )
        settings.save(this)
        DownloadDesk.apply(this, settings)
        finish()
    }

    private fun selectedIcon(): String {
        return when (binding.iconGroup.checkedChipId) {
            R.id.iconTray -> EngineSettings.ICON_TRAY
            R.id.iconBolt -> EngineSettings.ICON_BOLT
            R.id.iconLayers -> EngineSettings.ICON_LAYERS
            else -> EngineSettings.ICON_ARROW
        }
    }

    private fun refreshLanding() {
        val draft = EngineSettings.load(this).copy(
            destination = when (binding.destinationGroup.checkedChipId) {
                R.id.destScoped -> EngineSettings.DEST_SCOPED
                R.id.destCustom -> EngineSettings.DEST_CUSTOM
                else -> EngineSettings.DEST_AUTO
            },
            folder = binding.folder.text?.toString().orEmpty().ifBlank { "Fetch" },
            publicDownloads = binding.publicDownloads.isChecked &&
                binding.destinationGroup.checkedChipId == R.id.destAuto,
            publicFolder = binding.publicFolder.text?.toString().orEmpty()
        )
        binding.landing.text = friendlyPlace(DownloadDesk.previewPath(this, draft, "download.bin"))
    }

    private fun showFolderField(checkedId: Int) {
        val custom = checkedId == R.id.destScoped || checkedId == R.id.destCustom
        binding.folderLayout.visibility = if (custom) View.VISIBLE else View.GONE
        binding.folderLayout.hint = if (checkedId == R.id.destCustom) {
            "Absolute folder"
        } else {
            "Folder inside app storage"
        }
    }

    private fun lockDestinationChoices() {
        if (locking) return
        locking = true
        val checked = binding.destinationGroup.checkedChipId
        val specific = checked == R.id.destCustom || checked == R.id.destScoped
        if (specific && binding.publicDownloads.isChecked) {
            binding.publicDownloads.isChecked = false
        }
        val publicOn = binding.publicDownloads.isChecked && !specific
        binding.destCustom.isEnabled = !publicOn
        binding.destScoped.isEnabled = !publicOn
        binding.publicDownloads.isEnabled = !specific
        binding.publicFolderLayout.visibility = if (publicOn) View.VISIBLE else View.GONE
        locking = false
        refreshLanding()
    }

    private var locking = false

    private fun chunkLabel(count: Int): String {
        return if (count == 1) "1 connection" else "$count connections"
    }

    private fun friendlyPlace(path: String): String {
        val marker = "/Download/"
        val at = path.indexOf(marker)
        if (at >= 0) {
            return "Downloads / " + path.substring(at + marker.length)
        }
        val name = path.substringAfterLast('/')
        val parent = path.substringBeforeLast('/').substringAfterLast('/')
        return "$parent / $name"
    }
}
