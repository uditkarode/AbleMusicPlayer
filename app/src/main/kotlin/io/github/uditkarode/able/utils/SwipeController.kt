package io.github.uditkarode.able.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.*
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.getInputLayout
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.list.listItems
import io.github.uditkarode.able.R
import io.github.uditkarode.able.fragments.Home
import io.github.uditkarode.able.models.Song
import java.io.File
import java.lang.Exception

internal enum class ButtonsState {
    GONE, LEFT_VISIBLE, RIGHT_VISIBLE
}
class SwipeControllerActions {
    private var songList = ArrayList<Song>()

    fun onLeftClicked(context: Context?,position: Int) {
        songList = Shared.getSongList(Constants.ableSongDir)
        songList.addAll(Shared.getLocalSongs(context!!))
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

        names.add(0, context!!.getString(R.string.pq))
        names.add(1, context.getString(R.string.crp))
        val current=songList[position]
        MaterialDialog(context).show {
            listItems(items = names) { _, index, _ ->
                when(index)
                {
                    0 -> Shared.mService.addToQueue(current)
                    1 -> {
                        MaterialDialog(context).show {
                            title(text = context.getString(R.string.playlist_namei))
                            input(context.getString(R.string.name_s)){ _, charSequence ->
                                Shared.createPlaylist(charSequence.toString(), context)
                                Shared.addToPlaylist(Shared.getPlaylists().filter {
                                    it.name == "$charSequence.json"
                                }[0], current,context)
                            }
                            getInputLayout().boxBackgroundColor = Color.parseColor("#000000")
                        }
                    }
                    else -> {
                        Shared.addToPlaylist(playlists[index-2], current, context)
                    }
                }
            }
        }
    }
    fun onRightClicked(context: Context?,position: Int) {
        songList = Shared.getSongList(Constants.ableSongDir)
        songList.addAll(Shared.getLocalSongs(context!!))
        songList= ArrayList(songList.sortedBy { it.name.toUpperCase() })
        val current=songList[position]
        MaterialDialog(context).show {
            title(text = context.getString(R.string.confirmation))
            message(text = context.getString(R.string.res_confirm_txt).format(current.name, current.filePath))
            positiveButton(text = "Delete"){
                val curFile = File(current.filePath)
                if(curFile.absolutePath.contains("Able")) {
                    val curArt =
                        File(
                            Constants.ableSongDir.absolutePath + "/album_art",
                            curFile.nameWithoutExtension
                        )
                    curFile.delete()
                    curArt.delete()
                }
                else
                {
                    try{
                        curFile.delete()
                    }
                    catch (e:Exception)
                    {
                        e.printStackTrace()
                    }
                }
                songList.removeAt(position)
                Home.songAdapter?.update(songList)
            }
            negativeButton(text = context.getString(R.string.cancel))
        }
    }
}
@Suppress("DEPRECATION")
@SuppressLint("ClickableViewAccessibility")
class SwipeController(private val context: Context?) :
    ItemTouchHelper.Callback() {
    private var swipeBack = false
    private var buttonShowedState = ButtonsState.GONE
    private var buttonInstance: RectF? = null
    private val buttonsActions=SwipeControllerActions()
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
        }
        buttonShowedState = when {
            dX<-50 -> ButtonsState.RIGHT_VISIBLE
            dX>50 -> ButtonsState.LEFT_VISIBLE
            else -> ButtonsState.GONE
        }
        drawButtons(c,viewHolder)
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    private fun setTouchListener(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float
    ) {
        recyclerView.setOnTouchListener { _, event ->
            swipeBack = event.action == MotionEvent.ACTION_CANCEL || event.action == MotionEvent.ACTION_UP
            if (swipeBack) {
                if (dX < -buttonWidth)
                    {
                        buttonShowedState = ButtonsState.RIGHT_VISIBLE
                        buttonsActions.onRightClicked(context,viewHolder.adapterPosition)
                    }
                else if (dX > buttonWidth){
                    buttonShowedState = ButtonsState.LEFT_VISIBLE
                    buttonsActions.onLeftClicked(context,viewHolder.adapterPosition)
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
            drawText("Playlist", c, leftButton, p)
            buttonInstance = leftButton
        }
        else if (buttonShowedState == ButtonsState.RIGHT_VISIBLE) {
            val rightButton = RectF(
                itemView.right - buttonWidthWithoutPadding,
                itemView.top.toFloat(),
                itemView.right.toFloat(),
                itemView.bottom.toFloat()
            )
            p.color = Color.argb(255, 183, 28, 28)
            c.drawRoundRect(rightButton, corners, corners, p)
            drawText("DELETE", c, rightButton, p)
            buttonInstance = rightButton
        }
        buttonShowedState=ButtonsState.GONE
    }

    private fun drawText(
        text: String,
        c: Canvas,
        button: RectF,
        p: Paint
    ) {
        val textSize = 50f
        p.color = Color.WHITE
        p.isAntiAlias = true
        p.textSize = textSize
        val textWidth = p.measureText(text)
        c.drawText(text, button.centerX() - textWidth / 2, button.centerY() + textSize / 2, p)
    }
}
