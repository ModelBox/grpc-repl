/**
 * Copyright 2017 ModelBox Inc.
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
package io.modelbox.repl.data

import com.google.protobuf.Descriptors.MethodDescriptor
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.sync.Mutex


/**
 * Wrap a [SendChannel] as a [StreamObserver], also returning a [Job] which will indicate
 * when the channel has been closed.
 */
internal fun <E> SendChannel<E>.asStreamObserver(): Pair<Job, StreamObserver<E>> {
  val finished = Mutex(true)
  val observer = object : StreamObserver<E> {
    override fun onCompleted() {
      close()
      finished.unlock()
    }

    override fun onError(t: Throwable) {
      close(t)
      finished.unlock()
    }

    override fun onNext(value: E) {
      runBlocking { send(value) }
    }
  }
  return async(CommonPool) { finished.lock() } to observer
}

/**
 * Copy from a [ReceiveChannel] into a [StreamObserver].
 */
internal suspend fun <E> ReceiveChannel<E>.sendTo(observer: StreamObserver<E>) {
  try {
    consumeEach { observer.onNext(it) }
    observer.onCompleted()
  } catch (e: Throwable) {
    observer.onError(e)
  }
}

/**
 * Determine the GRPC method type for a descriptor.
 */
internal val MethodDescriptor.methodType: io.grpc.MethodDescriptor.MethodType
  get () {
    val methodProto = toProto()
    return if (methodProto.clientStreaming) {
      if (methodProto.serverStreaming) {
        io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING
      } else {
        io.grpc.MethodDescriptor.MethodType.CLIENT_STREAMING
      }
    } else if (methodProto.serverStreaming) {
      io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING
    } else {
      io.grpc.MethodDescriptor.MethodType.UNARY
    }
  }
