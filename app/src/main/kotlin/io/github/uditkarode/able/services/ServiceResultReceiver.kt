/*
    Copyright 2020 Udit Karode <udit.karode@gmail.com>

    This file is part of AbleMusicPlayer.

    AbleMusicPlayer is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, version 3 of the License.

    AbleMusicPlayer is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with AbleMusicPlayer.  If not, see <https://www.gnu.org/licenses/>.
*/

package io.github.uditkarode.able.services

import android.os.Bundle
import android.os.Handler
import android.os.ResultReceiver

class ServiceResultReceiver(handler: Handler?) : ResultReceiver(handler) {
    private var mReceiver: Receiver? = null

    fun setReceiver(receiver: Receiver?) {
        mReceiver = receiver
    }

    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
        mReceiver?.onReceiveResult(resultCode)
    }

    interface Receiver {
        fun onReceiveResult(resultCode: Int)
    }
}