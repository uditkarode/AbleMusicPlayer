package io.github.uditkarode.able.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.*
import android.media.MediaScannerConnection
import android.view.MotionEvent
import android.view.View
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.*
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.getInputLayout
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.list.listItems
import io.github.uditkarode.able.R
import io.github.uditkarode.able.fragments.Home
import io.github.uditkarode.able.fragments.Search
import io.github.uditkarode.able.models.MusicMode
import io.github.uditkarode.able.models.Song
import io.github.uditkarode.able.services.MusicService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

internal enum class ButtonsState {
    GONE, LEFT_VISIBLE, RIGHT_VISIBLE
}

@ExperimentalCoroutinesApi
class SwipeControllerActions(private var mode: String, private var mService: MutableStateFlow<MusicService?>?) {
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
            it.name.toUpperCase(
                Locale.getDefault()
            )
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
                            0 ->mService!!.value?.addToQueue(current)
                            1 -> {
                                MaterialDialog(context).show {
                                    title(text = context.getString(R.string.playlist_namei))
                                    input(context.getString(R.string.name_s)) { _, charSequence ->
                                        Shared.createPlaylist(charSequence.toString(), context)
                                        Shared.addToPlaylist(Shared.getPlaylists().filter {
                                            it.name == "$charSequence.json"
                                        }[0], current, context)
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
                itemPressed.sendItem(Search.resultArray[position], MusicMode.both)
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
                        MediaScannerConnection.scanFile(context,
                            arrayOf(current.filePath) , null,null)
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

@ExperimentalCoroutinesApi
@Suppress("DEPRECATION")
@SuppressLint("ClickableViewAccessibility")
class SwipeController(
    private val context: Context?,
    private val list: String?,
    private val mService: MutableStateFlow<MusicService?>?
) :
    ItemTouchHelper.Callback() {
    private var swipeBack = false
    private var buttonShowedState = ButtonsState.GONE
    private var buttonInstance: RectF? = null
    private var buttonsActions = SwipeControllerActions("", mService)
    private val buttonWidth = 200f

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        return makeMovementFlags(
            0,
            LEFT or RIGHT
        )
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    }

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
        }
        buttonShowedState = when {
            dX < -50 -> ButtonsState.RIGHT_VISIBLE
            dX > 50 -> ButtonsState.LEFT_VISIBLE
            else -> ButtonsState.GONE
        }
        drawButtons(c, viewHolder)
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
                if (dX < -buttonWidth) {
                    buttonShowedState = ButtonsState.RIGHT_VISIBLE
                    buttonsActions.onRightClicked(context, viewHolder.adapterPosition)
                } else if (dX > buttonWidth) {
                    buttonShowedState = ButtonsState.LEFT_VISIBLE
                    buttonsActions.onLeftClicked(context, viewHolder.adapterPosition)
                }
            }
            false
        }
    }

    private fun drawButtons(
        c: Canvas,
        viewHolder: RecyclerView.ViewHolder
    ) {
        val buttonWidthWithoutPadding = buttonWidth - 20
        val corners = 16f
        val itemView: View = viewHolder.itemView
        val p = Paint()
        buttonInstance = null
        if (buttonShowedState == ButtonsState.LEFT_VISIBLE) {
            val leftButton = RectF(
                itemView.left.toFloat(),
                itemView.top.toFloat(),
                itemView.left.toFloat() + buttonWidthWithoutPadding,
                itemView.bottom.toFloat()
            )
            p.color = Color.argb(255, 148, 188, 227)
            c.drawRoundRect(leftButton, corners, corners, p)
            if (list.equals("Home"))
                drawText("Playlist", c, leftButton, p)
            else
                drawText(MusicMode.both, c, leftButton, p)
            buttonsActions = when (list) {
                "Search" ->
                    SwipeControllerActions(MusicMode.both, mService)
                else -> SwipeControllerActions("", mService)
            }
            buttonInstance = leftButton
        } else if (buttonShowedState == ButtonsState.RIGHT_VISIBLE) {
            val rightButton = RectF(
                itemView.right - buttonWidthWithoutPadding,
                itemView.top.toFloat(),
                itemView.right.toFloat(),
                itemView.bottom.toFloat()
            )
            p.color = Color.argb(255, 183, 28, 28)
            c.drawRoundRect(rightButton, corners, corners, p)
            if (list.equals("Home"))
                drawText("DELETE", c, rightButton, p)
            else {
                val mode: String? = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString("mode_key", MusicMode.download)
                val currentMode: String = if (mode == MusicMode.download) {
                    drawText(MusicMode.stream, c, rightButton, p)
                    MusicMode.stream
                } else {
                    drawText(MusicMode.download, c, rightButton, p)
                    MusicMode.download
                }
                buttonsActions = when (list) {
                    "Search" ->
                        SwipeControllerActions(currentMode, mService)
                    else -> SwipeControllerActions("",mService)
                }
            }
            buttonInstance = rightButton
        }
        buttonShowedState = ButtonsState.GONE
    }

    private fun drawText(
        text: String,
        c: Canvas,
        button: RectF,
        p: Paint
    ) {
        var textSize = 50f
        if (text == MusicMode.download)
            textSize = 35f
        p.color = Color.WHITE
        p.isAntiAlias = true
        p.textSize = textSize
        val textWidth = p.measureText(text)
        c.drawText(text, button.centerX() - textWidth / 2, button.centerY() + textSize / 2, p)
    }
}
