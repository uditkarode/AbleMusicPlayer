package io.github.uditkarode.able.activities

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import io.github.uditkarode.able.AbleApplication
import io.github.uditkarode.able.R
import io.github.uditkarode.able.adapters.DownloadItemAdapter
import io.github.uditkarode.able.adapters.DownloadItemAdapter.DownloadItem
import io.github.uditkarode.able.databinding.ActivityDownloadsBinding
import io.github.uditkarode.able.services.DownloadService
import io.github.uditkarode.able.utils.SpotifyImport

class Downloads : AppCompatActivity() {
    private lateinit var binding: ActivityDownloadsBinding
    private lateinit var adapter: DownloadItemAdapter
    private val handler = Handler(Looper.getMainLooper())
    private val pollInterval = 1000L

    private val pollRunnable = object : Runnable {
        override fun run() {
            refreshDownloads()
            refreshSpotify()
            handler.postDelayed(this, pollInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.downloadsToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.downloads_title)

        adapter = DownloadItemAdapter()
        binding.downloadList.layoutManager = LinearLayoutManager(this)
        binding.downloadList.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        handler.post(pollRunnable)
    }

    override fun onPause() {
        handler.removeCallbacks(pollRunnable)
        super.onPause()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase!!, AbleApplication.viewPump))
    }

    private fun refreshDownloads() {
        val current = DownloadService.currentDownload
        val pending = DownloadService.pendingQueue
        val items = mutableListOf<DownloadItem>()

        if (current != null) {
            items.add(
                DownloadItem(
                    name = current.name,
                    artist = current.artist,
                    status = DownloadService.currentStatus,
                    isActive = true
                )
            )
        }

        for (song in pending) {
            items.add(
                DownloadItem(
                    name = song.name,
                    artist = song.artist,
                    status = getString(R.string.downloads_waiting),
                    isActive = false
                )
            )
        }

        adapter.update(items)

        if (items.isEmpty()) {
            binding.downloadList.visibility = View.GONE
            binding.downloadsEmpty.visibility = View.VISIBLE
        } else {
            binding.downloadList.visibility = View.VISIBLE
            binding.downloadsEmpty.visibility = View.GONE
        }
    }

    private fun refreshSpotify() {
        if (SpotifyImport.isImporting && SpotifyImport.totalTracks > 0) {
            binding.spotifyIdle.visibility = View.GONE
            binding.spotifyActive.visibility = View.VISIBLE

            binding.spotifyTrackName.text = SpotifyImport.currentTrackName
            binding.spotifyProgressText.text =
                "Track ${SpotifyImport.currentTrackIndex} of ${SpotifyImport.totalTracks} — ${SpotifyImport.currentTrackStatus}"

            val progress = SpotifyImport.currentTrackStatus.removeSuffix("%").toIntOrNull()
            if (progress != null) {
                binding.spotifyProgressBar.isIndeterminate = false
                binding.spotifyProgressBar.progress = progress
            } else {
                binding.spotifyProgressBar.isIndeterminate = true
            }
        } else {
            binding.spotifyIdle.visibility = View.VISIBLE
            binding.spotifyActive.visibility = View.GONE
        }
    }
}
