/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.test

import android.view.InputDevice
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_CANCEL
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_POINTER_DOWN
import android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT
import android.view.MotionEvent.ACTION_POINTER_UP
import android.view.MotionEvent.ACTION_UP
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.ViewRootForTest

internal actual fun createInputDispatcher(
    testContext: TestContext,
    root: RootForTest
): InputDispatcher {
    require(root is ViewRootForTest) {
        "InputDispatcher only supports dispatching to ViewRootForTest, not to " +
            root::class.java.simpleName
    }
    val view = root.view
    return AndroidInputDispatcher(testContext, root) { view.dispatchTouchEvent(it) }
}

internal class AndroidInputDispatcher(
    private val testContext: TestContext,
    private val root: ViewRootForTest?,
    private val sendEvent: (MotionEvent) -> Unit
) : InputDispatcher(testContext, root) {

    private val batchLock = Any()
    private var batchedEvents = mutableListOf<MotionEvent>()
    private var acceptEvents = true
    private var currentClockTime = currentTime

    override fun PartialGesture.enqueueDown(pointerId: Int) {
        enqueueTouchEvent(
            if (lastPositions.size == 1) ACTION_DOWN else ACTION_POINTER_DOWN,
            lastPositions.keys.sorted().indexOf(pointerId)
        )
    }

    override fun PartialGesture.enqueueMove() {
        enqueueTouchEvent(ACTION_MOVE, 0)
    }

    override fun PartialGesture.enqueueUp(pointerId: Int) {
        enqueueTouchEvent(
            if (lastPositions.size == 1) ACTION_UP else ACTION_POINTER_UP,
            lastPositions.keys.sorted().indexOf(pointerId)
        )
    }

    override fun PartialGesture.enqueueCancel() {
        enqueueTouchEvent(ACTION_CANCEL, 0)
    }

    /**
     * Generates a MotionEvent with the given [action] and [actionIndex], adding all pointers that
     * are currently in the gesture, and adds the MotionEvent to the batch.
     *
     * @see MotionEvent.getAction
     * @see MotionEvent.getActionIndex
     */
    private fun PartialGesture.enqueueTouchEvent(action: Int, actionIndex: Int) {
        val entries = lastPositions.entries.sortedBy { it.key }
        enqueueTouchEvent(
            downTime = downTime,
            eventTime = currentTime,
            action = action,
            actionIndex = actionIndex,
            coordinates = List(entries.size) { entries[it].value },
            pointerIds = List(entries.size) { entries[it].key }
        )
    }

    /**
     * Generates an event with the given parameters.
     */
    private fun enqueueTouchEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        actionIndex: Int,
        coordinates: List<Offset>,
        pointerIds: List<Int>
    ) {
        synchronized(batchLock) {
            check(acceptEvents) {
                "Can't enqueue touch event (" +
                    "downTime=$downTime, " +
                    "eventTime=$eventTime, " +
                    "action=$action, " +
                    "actionIndex=$actionIndex, " +
                    "pointerIds=$pointerIds, " +
                    "coordinates=$coordinates" +
                    "), events have already been (or are being) dispatched or disposed"
            }
            val positionInScreen = if (root != null) {
                val array = intArrayOf(0, 0)
                root.view.getLocationOnScreen(array)
                Offset(array[0].toFloat(), array[1].toFloat())
            } else {
                Offset.Zero
            }
            batchedEvents.add(
                MotionEvent.obtain(
                    /* downTime = */ downTime,
                    /* eventTime = */ eventTime,
                    /* action = */ action + (actionIndex shl ACTION_POINTER_INDEX_SHIFT),
                    /* pointerCount = */ coordinates.size,
                    /* pointerProperties = */ Array(coordinates.size) {
                        MotionEvent.PointerProperties().apply {
                            id = pointerIds[it]
                            toolType = MotionEvent.TOOL_TYPE_FINGER
                        }
                    },
                    /* pointerCoords = */ Array(coordinates.size) {
                        MotionEvent.PointerCoords().apply {
                            x = positionInScreen.x + coordinates[it].x
                            y = positionInScreen.y + coordinates[it].y
                        }
                    },
                    /* metaState = */ 0,
                    /* buttonState = */ 0,
                    /* xPrecision = */ 1f,
                    /* yPrecision = */ 1f,
                    /* deviceId = */ 0,
                    /* edgeFlags = */ 0,
                    /* source = */ InputDevice.SOURCE_TOUCHSCREEN,
                    /* flags = */ 0
                ).apply {
                    offsetLocation(-positionInScreen.x, -positionInScreen.y)
                }
            )
        }
    }

    override fun sendAllSynchronous() {
        // Must inject on the main thread, because it might modify View properties
        @OptIn(InternalTestApi::class)
        testContext.testOwner.runOnUiThread {
            checkAndStopAcceptingEvents()

            batchedEvents.forEach { event ->
                // Before injecting the next event, pump the clock
                // by the difference between this and the last event
                advanceClockTime(event.eventTime - currentClockTime)
                currentClockTime = event.eventTime
                sendAndRecycleEvent(event)
            }
        }
        // Each invocation of performGesture (Actions.kt) uses a new instance of an input
        // dispatcher, so we don't have to reset firstEventTime after use
    }

    @OptIn(InternalTestApi::class)
    private fun advanceClockTime(millis: Long) {
        // Don't bother advancing the clock if there's nothing to advance
        if (millis > 0) {
            testContext.testOwner.mainClock.advanceTimeBy(millis, ignoreFrameDuration = true)
        }
    }

    override fun onDispose() {
        stopAcceptingEvents()
    }

    private fun checkAndStopAcceptingEvents() {
        synchronized(batchLock) {
            check(acceptEvents) { "Events have already been (or are being) dispatched or disposed" }
            acceptEvents = false
        }
    }

    private fun stopAcceptingEvents(): Boolean {
        synchronized(batchLock) {
            return acceptEvents.also { acceptEvents = false }
        }
    }

    /**
     * Sends and recycles the given [event].
     */
    private fun sendAndRecycleEvent(event: MotionEvent) {
        try {
            sendEvent(event)
        } finally {
            event.recycle()
        }
    }
}
