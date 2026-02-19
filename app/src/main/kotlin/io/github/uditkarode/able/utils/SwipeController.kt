package io.github.uditkarode.able.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.view.MotionEvent
import android.view.View
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE
import androidx.recyclerview.widget.ItemTouchHelper.LEFT
import androidx.recyclerview.widget.ItemTouchHelper.RIGHT
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.getInputLayout
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.list.listItems
import io.github.uditkarode.able.R
import io.github.uditkarode.able.fragments.Home
import io.github.uditkarode.able.fragments.Search
import io.github.uditkarode.able.model.MusicMode
import io.github.uditkarode.able.model.song.Song
import io.github.uditkarode.able.services.MusicService
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.*

class SwipeControllerActions(
    private var mode: String,
    private var mService: MutableStateFlow<MusicService?>?
) {
    private var songList = ArrayList<Song>()
    private lateinit var itemPressed: Search.SongCallback

    private fun initialiseSongCallback(context: Context?) {
        try {
            itemPressed = context as Activity as Search.SongCallback
        } catch (e: ClassCastException) {
            e.printStackTrace()
        }
    }

    private fun getSongList(context: Context?) {
        songList = Shared.getSongList(Constants.ableSongDir)
        songList.addAll(Shared.getLocalSongs(context!!))
        songList = ArrayList(songList.sortedBy {
            it.name.uppercase(Locale.getDefault())
        })
    }

    fun onLeftClicked(context: Context?, position: Int) {
        when {
            mode.isEmpty() -> {
                getSongList(context!!)
                val playlists = Shared.getPlaylists()
                val names = playlists.run {
                    ArrayList<String>().also {
                        for (playlist in this) it.add(
                            playlist.name.replace(
                                ".json",
                                ""
                            )
                        )
                    }
                }

                names.add(0, context.getString(R.string.pq))
                names.add(1, context.getString(R.string.crp))
                val current = songList[position]
                MaterialDialog(context).show {
                    listItems(items = names) { _, index, _ ->
                        when (index) {
                            0 -> mService!!.value?.addToQueue(current)
                            1 -> {
                                MaterialDialog(context).show {
                                    title(text = context.getString(R.string.playlist_namei))
                                    input(context.getString(R.string.name_s)) { _, charSequence ->
                                        Shared.createPlaylist(charSequence.toString(), context)
                                        Shared.getPlaylists().firstOrNull {
                                            it.name == "$charSequence.json"
                                        }?.let { Shared.addToPlaylist(it, current, context) }
                                    }
                                    getInputLayout().boxBackgroundColor =
                                        Color.parseColor("#000000")
                                }
                            }

                            else -> {
                                Shared.addToPlaylist(playlists[index - 2], current, context)
                            }
                        }
                    }
                }
            }

            else -> {
                initialiseSongCallback(context!!)
                itemPressed.sendItem(Search.resultArray[position], "")
            }
        }
    }

    fun onRightClicked(context: Context?, position: Int) {
        when {
            mode.isEmpty() -> {
                getSongList(context!!)
                val current = songList[position]
                MaterialDialog(context).show {
                    title(text = context.getString(R.string.confirmation))
                    message(
                        text = context.getString(R.string.res_confirm_txt)
                            .format(current.name, current.filePath)
                    )
                    positiveButton(text = "Delete") {
                        val curFile = File(current.filePath)
                        if (curFile.absolutePath.contains("Able")) {
                            val curArt =
                                File(
                                    Constants.ableSongDir.absolutePath + "/album_art",
                                    curFile.nameWithoutExtension
                                )
                            curFile.delete()
                            curArt.delete()
                        } else {
                            try {
                                curFile.delete()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        songList.removeAt(position)
                        Home.songAdapter?.update(songList)
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(current.filePath), null, null
                        )
                    }
                    negativeButton(text = context.getString(R.string.cancel))
                }
            }

            else -> {
                initialiseSongCallback(context!!)
                itemPressed.sendItem(Search.resultArray[position], mode)
            }
        }
    }
}

@Suppress("DEPRECATION")
@SuppressLint("ClickableViewAccessibility")
class SwipeController(
    private val context: Context?,
    private val list: String?,
    private val mService: MutableStateFlow<MusicService?>?
) : ItemTouchHelper.Callback() {

    private var swipeBack = false
    private var buttonsActions = SwipeControllerActions("", mService)
    private val actionThreshold = 350f
    private val cornerRadius = 20f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 52f
        typeface = context?.assets?.let {
            Typeface.createFromAsset(it, "fonts/interbold.otf")
        } ?: Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int = makeMovementFlags(0, LEFT or RIGHT)

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun convertToAbsoluteDirection(flags: Int, layoutDirection: Int): Int {
        if (swipeBack) {
            swipeBack = false
            return 0
        }
        return super.convertToAbsoluteDirection(flags, layoutDirection)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ACTION_STATE_SWIPE) {
            setTouchListener(recyclerView, viewHolder, dX)
            drawSwipeReveal(c, viewHolder, dX)
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    private fun setTouchListener(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float
    ) {
        recyclerView.setOnTouchListener { _, event ->
            swipeBack =
                event.action == MotionEvent.ACTION_CANCEL || event.action == MotionEvent.ACTION_UP
            if (swipeBack) {
                if (dX < -actionThreshold)
                    buttonsActions.onRightClicked(context, viewHolder.adapterPosition)
                else if (dX > actionThreshold)
                    buttonsActions.onLeftClicked(context, viewHolder.adapterPosition)
            }
            false
        }
    }

    private fun drawSwipeReveal(
        c: Canvas,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float
    ) {
        val absDx = kotlin.math.abs(dX)
        if (absDx < 5f) return

        val itemView = viewHolder.itemView
        val progress = (absDx / actionThreshold).coerceIn(0f, 1.5f)

        if (dX > 0) {
            // Swipe right — left action (Playlist / Play)
            drawGradient(c, itemView, dX, isLeft = true)
            val text = if (list == "Home") "Playlist" else "Play"
            drawLabel(c, itemView, dX, progress, isLeft = true, text = text)
            buttonsActions = SwipeControllerActions("", mService)
        } else {
            // Swipe left — right action (Delete / Download / Stream)
            val text = resolveRightAction()
            drawGradient(c, itemView, dX, isLeft = false)
            drawLabel(c, itemView, dX, progress, isLeft = false, text = text)
        }
    }

    private fun drawGradient(
        c: Canvas,
        itemView: View,
        dX: Float,
        isLeft: Boolean
    ) {
        val top = itemView.top.toFloat()
        val bottom = itemView.bottom.toFloat()
        val alpha = 210

        if (isLeft) {
            val left = itemView.left.toFloat()
            val right = itemView.left + dX
            val r = 148; val g = 188; val b = 227

            bgPaint.shader = LinearGradient(
                left, 0f, right, 0f,
                intArrayOf(
                    Color.argb(alpha, r, g, b),
                    Color.argb(alpha, r, g, b),
                    Color.argb(0, r, g, b)
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )

            // Round only the trailing edge (right side) by extending left beyond clip
            c.save()
            c.clipRect(left, top, right, bottom)
            c.drawRoundRect(
                RectF(left - cornerRadius, top, right, bottom),
                cornerRadius, cornerRadius, bgPaint
            )
            c.restore()
        } else {
            val absDx = kotlin.math.abs(dX)
            val left = itemView.right - absDx
            val right = itemView.right.toFloat()

            bgPaint.shader = LinearGradient(
                left, 0f, right, 0f,
                intArrayOf(
                    Color.argb(0, 220, 75, 55),            // leading: transparent
                    Color.argb(alpha, 220, 75, 55),         // soft red, fading in
                    Color.argb(alpha, 195, 40, 35),         // mid: richer red
                    Color.argb(alpha, 170, 25, 20)          // trailing: deep red
                ),
                floatArrayOf(0f, 0.25f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )

            // Round only the trailing edge (left side) by extending right beyond clip
            c.save()
            c.clipRect(left, top, right, bottom)
            c.drawRoundRect(
                RectF(left, top, right + cornerRadius, bottom),
                cornerRadius, cornerRadius, bgPaint
            )
            c.restore()
        }
        bgPaint.shader = null
    }

    private fun drawLabel(
        c: Canvas,
        itemView: View,
        dX: Float,
        progress: Float,
        isLeft: Boolean,
        text: String
    ) {
        // Fade in: invisible until 30% progress, fully opaque at 80%
        val textAlpha = ((progress - 0.3f) / 0.5f * 255).toInt().coerceIn(0, 255)
        textPaint.alpha = textAlpha

        val top = itemView.top.toFloat()
        val bottom = itemView.bottom.toFloat()
        val centerY = (top + bottom) / 2f - (textPaint.descent() + textPaint.ascent()) / 2f

        val centerX = if (isLeft) {
            (itemView.left.toFloat() + itemView.left + dX) / 2f
        } else {
            val absDx = kotlin.math.abs(dX)
            (itemView.right - absDx + itemView.right.toFloat()) / 2f
        }

        c.drawText(text, centerX, centerY, textPaint)
    }

    private fun resolveRightAction(): String {
        return if (list == "Home") {
            buttonsActions = SwipeControllerActions("", mService)
            "Delete"
        } else {
            val mode = PreferenceManager.getDefaultSharedPreferences(context!!)
                .getString("mode_key", MusicMode.download)
            val currentMode =
                if (mode == MusicMode.download) MusicMode.stream else MusicMode.download
            buttonsActions = if (list == "Search")
                SwipeControllerActions(currentMode, mService)
            else
                SwipeControllerActions("", mService)
            currentMode
        }
    }
}
