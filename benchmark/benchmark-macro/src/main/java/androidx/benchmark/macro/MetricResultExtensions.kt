/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.benchmark.macro

import android.util.Log
import androidx.benchmark.MetricResult

/**
 * Merge the Map<String, Long> results from each iteration into one List<MetricResult>
 */
internal fun List<Map<String, Long>>.mergeToMetricResults(
    @Suppress("UNUSED_PARAMETER") tracePaths: List<String>
): List<MetricResult> {
    val setOfAllKeys = flatMap { it.keys }.toSet()

    // build Map<String, List<Long>>
    val listResults: Map<String, List<Long>> = setOfAllKeys.associateWith { key ->
        mapIndexedNotNull { iteration, resultMap ->
            if (resultMap.keys != setOfAllKeys) {
                // TODO: assert that metrics are always captured (b/193827052)
                Log.d(TAG, "Skipping results from iter $iteration, it didn't capture all metrics")
                null
            } else {
                resultMap[key] ?: error("Metric $key not observed in iteration")
            }
        }
    }

    // transform to List<MetricResult>, sorted by metric name
    return listResults.map { (metricName, values) ->
        MetricResult(metricName, values.toLongArray())
    }.sortedBy { it.stats.name }
}