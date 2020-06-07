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

package androidx.ui.savedinstancestate

import androidx.compose.Composable
import androidx.compose.CompositionLifecycleObserver
import androidx.compose.ExperimentalComposeApi
import androidx.compose.MutableState
import androidx.compose.currentComposer
import androidx.compose.remember

/**
 * Remember the value produced by [init].
 *
 * It behaves similarly to [remember], but the stored value will survive the activity or process
 * recreation using the saved instance state mechanism (for example it happens when the screen is
 * rotated in the Android application).
 *
 * @sample androidx.ui.savedinstancestate.samples.RememberSavedInstanceStateSample
 *
 * This function works nicely with mutable objects, when you update the state of this object
 * instead of recreating it. If you work with immutable objects [savedInstanceState] can suit you
 * more as it wraps the value into the [MutableState].
 *
 * If you use it with types which can be stored inside the Bundle then it will be saved and
 * restored automatically using [autoSaver], otherwise you will need to provide a custom [Saver]
 * implementation via the [saver] param.
 *
 * @sample androidx.ui.savedinstancestate.samples.CustomSaverSample
 *
 * @param inputs A set of inputs such that, when any of them have changed, will cause the state to
 * reset and [init] to be rerun
 * @param saver The [Saver] object which defines how the state is saved and restored.
 * @param key An optional key to be used as a key for the saved value. If not provided we use the
 * automatically generated by the Compose runtime which is unique for the every exact code location
 * in the composition tree
 * @param init A factory function to create the initial value of this state
 */
@OptIn(ExperimentalComposeApi::class)
@Composable
fun <T : Any> rememberSavedInstanceState(
    vararg inputs: Any?,
    saver: Saver<T, out Any> = autoSaver(),
    key: String? = null,
    init: () -> T
): T {
    val finalKey = if (!key.isNullOrEmpty()) {
        key
    } else {
        currentComposer.currentCompoundKeyHash.toString()
    }
    val registry = UiSavedStateRegistryAmbient.current
    val valueProvider = remember(*inputs) { ValueProvider<T>() }
    return valueProvider.updateAndReturnValue(registry, saver, finalKey, init)
}

private class ValueProvider<T : Any> : SaverScope, CompositionLifecycleObserver {

    private var registry: UiSavedStateRegistry? = null
    private var saver: Saver<T, out Any>? = null
    private var key: String? = null
    private var value: T? = null

    @Suppress("UNCHECKED_CAST")
    private fun saver(): Saver<T, Any> = (saver as Saver<T, Any>)

    fun updateAndReturnValue(
        registry: UiSavedStateRegistry?,
        saver: Saver<T, out Any>,
        key: String,
        init: () -> T
    ): T {
        val oldRegistry = this.registry
        val oldKey = this.key
        this.saver = saver
        this.registry = registry
        this.key = key
        val value = value ?: run {
            // TODO not restore when the input values changed (use hashKeys?) b/152014032
            val restored = registry?.consumeRestored(key)?.let {
                saver().restore(it)
            }
            val result = restored ?: init()
            this.value = result
            result
        }
        if (oldRegistry !== registry || oldKey != key) {
            oldRegistry?.unregisterProvider(oldKey!!)
            registry?.registerProvider(key) {
                val result = with(saver()) { save(value) }
                if (result != null) {
                    check(registry.canBeSaved(result))
                }
                result
            }
        }
        return value
    }

    override fun canBeSaved(value: Any) = registry!!.canBeSaved(value)

    override fun onLeave() {
        registry?.unregisterProvider(key!!)
    }

    override fun onEnter() {
        // no-op
    }
}
