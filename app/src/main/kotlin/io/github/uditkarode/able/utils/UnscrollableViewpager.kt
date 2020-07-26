package io.github.uditkarode.able.utils

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager

/**
 * A ViewPager that cannot be scrolled and can only change pages if programatically
 * made to do so.
 */
class UnscrollableViewpager: ViewPager {
    constructor(context: Context): super(context)
    constructor(context: Context, attrs: AttributeSet): super(context, attrs)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?) = false
    override fun onInterceptTouchEvent(event: MotionEvent?) = false
}