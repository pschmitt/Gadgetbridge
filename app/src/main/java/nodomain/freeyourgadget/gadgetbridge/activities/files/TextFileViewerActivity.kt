package nodomain.freeyourgadget.gadgetbridge.activities.files

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.core.view.MenuProvider
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityTextFileViewerBinding
import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

/**
 * Displays a text file, or a text from the Intent, with buttons to copy or share it.
 */
class TextFileViewerActivity : AbstractGBActivity(), MenuProvider {
    private lateinit var title: String
    private lateinit var text: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityTextFileViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        addMenuProvider(this)

        val path = intent.getStringExtra(EXTRA_PATH)
        val extraText = intent.getStringExtra(EXTRA_TEXT)
        if (path != null) {
            val file = File(path)
            title = file.name
            try {
                text = file.readText().trim()
            } catch (e: IOException) {
                LOG.error("Failed to read {}", file, e)
                GB.toast("Unable to read file", Toast.LENGTH_LONG, GB.ERROR)
                finish()
                return
            }
        } else if (extraText != null) {
            title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.text_file_viewer)
            text = extraText.trim()
        } else {
            GB.toast("Missing path", Toast.LENGTH_LONG, GB.ERROR)
            finish()
            return
        }

        supportActionBar?.title = title
        binding.textFileContent.text = text
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_text_file_viewer, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.text_file_viewer_copy -> {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(title, text))
                GB.toast(getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT, GB.INFO)
                return true
            }

            R.id.text_file_viewer_share -> {
                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.type = "text/plain"
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, title)
                shareIntent.putExtra(Intent.EXTRA_TEXT, text)
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
                return true
            }
        }
        return false
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(TextFileViewerActivity::class.java)

        const val EXTRA_PATH = "path"
        const val EXTRA_TEXT = "text"
        const val EXTRA_TITLE = "title"
    }
}
