/*
 * Copyright 2017 Pronghorn Technology LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tech.pronghorn.coroutines.service

import tech.pronghorn.coroutines.awaitable.ExternalQueue
import tech.pronghorn.coroutines.awaitable.await
import tech.pronghorn.util.stackTraceToString
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This variety of queue service supports queueing from worker other than the one running this service.
 * Because of this, functionality is limited
 */
abstract class SingleWriterExternalQueueService<WorkType>(queueCapacity: Int = 16384) : QueueService<WorkType>() {
    private val queue = ExternalQueue(queueCapacity, this)

    private val queueWriterGiven = AtomicBoolean(false)

    protected val queueReader = queue.queueReader

    override fun getQueueWriter(): ExternalQueue.ExternalQueueWriter<WorkType> {
        if (queueWriterGiven.compareAndSet(false, true)) {
            return queue.queueWriter
        }
        else {
            throw Exception("Only one queue writer can be created for this service.")
        }
    }

    abstract suspend protected fun process(work: WorkType)

    override suspend fun run() {
        while (isRunning) {
            val workItem = await(queueReader)
            if (shouldYield()) {
                yieldAsync()
            }

            try {
                process(workItem)
            }
            catch (ex: Exception) {
                logger.error { "Queue service threw exception: ${ex.stackTraceToString()}" }
            }
        }
    }
}
