/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.compose.ui.test.junit4

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.test.espresso.Espresso.onIdle
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

@LargeTest
class ComposeIdlingResourceTest {
    companion object {
        private const val nonIdleDuration = 1000L
        private const val animateFromX = 0f
        private const val animateToX = 50f
        private val rectSize = Size(50.0f, 50.0f)
    }

    private var animationRunning = false
    private val recordedAnimatedValues = mutableListOf<Float>()

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /**
     * High level test to only verify that [ComposeTestRule.runOnIdle] awaits animations.
     */
    @Test
    fun testRunOnIdle() {
        val animationState = mutableStateOf(AnimationStates.From)
        rule.setContent { Ui(animationState) }

        rule.runOnIdle {
            // Kick off the animation
            animationRunning = true
            animationState.value = AnimationStates.To
        }

        // Verify that animation is kicked off
        assertThat(animationRunning).isTrue()
        // Wait until it is finished
        rule.runOnIdle {
            // Verify it was finished
            assertThat(animationRunning).isFalse()
        }
    }

    /**
     * High level test to only verify that [onIdle] awaits animations.
     */
    @Test
    fun testAnimationIdle_simple() {
        val animationState = mutableStateOf(AnimationStates.From)
        rule.setContent { Ui(animationState) }

        rule.runOnIdle {
            // Kick off the animation
            animationRunning = true
            animationState.value = AnimationStates.To
        }

        // Verify that animation is kicked off
        assertThat(animationRunning).isTrue()
        // Wait until it is finished
        onIdle()
        // Verify it was finished
        assertThat(animationRunning).isFalse()
    }

    @Composable
    private fun Ui(animationState: State<AnimationStates>) {
        Box(modifier = Modifier.background(color = Color.Yellow).fillMaxSize()) {
            val transition = updateTransition(animationState.value)
            animationRunning = transition.currentState != transition.targetState
            val x by transition.animateFloat(
                transitionSpec = {
                    if (AnimationStates.From isTransitioningTo AnimationStates.To) {
                        tween(
                            easing = LinearEasing,
                            durationMillis = nonIdleDuration.toInt()
                        )
                    } else {
                        snap()
                    }
                }
            ) {
                if (it == AnimationStates.From) {
                    animateFromX
                } else {
                    animateToX
                }
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                recordedAnimatedValues.add(x)
                drawRect(Color.Cyan, Offset(x, 0f), rectSize)
            }
        }
    }
}

private enum class AnimationStates {
    From,
    To
}