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
import com.google.protobuf.DynamicMessage
import com.google.protobuf.Message
import io.grpc.CallCredentials
import io.grpc.CallOptions
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.Metadata.*
import io.grpc.MethodDescriptor.MethodType.*
import io.grpc.MethodDescriptor.generateFullMethodName
import io.grpc.netty.GrpcSslContexts
import io.grpc.netty.NettyChannelBuilder.forAddress
import io.grpc.protobuf.ProtoUtils
import io.grpc.stub.ClientCalls
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.produce
import java.net.URI
import java.security.KeyStore
import java.util.Base64
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import javax.net.ssl.TrustManagerFactory

@Singleton
internal class RpcCaller @Inject constructor(
    @Named("target") private val target: URI,
    @Named("trustKeystore") private val trustKeystore: KeyStore?) {

  internal val mutableHeaders = mutableMapOf<String, String>()
  private val ch: ManagedChannel

  init {
    val sslContext = if (trustKeystore != null) {
      val tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).also {
        it.init(trustKeystore)
      }

      GrpcSslContexts.forClient().trustManager(tm).build()
    } else {
      null
    }

    val (ssl, defaultPort) = when (target.scheme?.toLowerCase()) {
      "http" -> false to 80
      "https" -> true to 443
      null -> throw IllegalArgumentException("Must specify a URI scheme for the target")
      else -> throw IllegalArgumentException("Unrecognized URI scheme ${target.scheme}")
    }

    ch = forAddress(target.host, target.port.takeIf { it > 0 } ?: defaultPort)
        .also { builder ->
          builder.directExecutor()
          builder.idleTimeout(1, java.util.concurrent.TimeUnit.MINUTES)
          if (ssl && sslContext != null) {
            builder.sslContext(sslContext)
          } else if (!ssl) {
            builder.usePlaintext(true)
          }
        }
        .build()
  }

  fun invoke(method: MethodDescriptor,
             args: ReceiveChannel<Message>) = produce<Message>(
      CommonPool) {
    val type = method.methodType

    val rpc = io.grpc.MethodDescriptor.create<Message, Message>(
        type,
        generateFullMethodName(method.service.fullName, method.name),
        ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(method.inputType)),
        ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(method.outputType)))

    val creds = CallCredentials { _, _, _, applier ->
      val meta = Metadata()
      mutableHeaders.forEach { entry ->
        if (entry.key.endsWith(BINARY_HEADER_SUFFIX)) {
          val key = Key.of(entry.key, BINARY_BYTE_MARSHALLER)
          val value = Base64.getDecoder().decode(entry.value)
          meta.put(key, value)
        } else {
          val key = Key.of(entry.key, ASCII_STRING_MARSHALLER)
          meta.put(key, entry.value)
        }
      }
      applier.apply(meta)
    }

    val call = ch.newCall(rpc, CallOptions.DEFAULT.withCallCredentials(creds))
    val (finished, observer) = asStreamObserver()
    when (type) {
      BIDI_STREAMING -> {
        val out = ClientCalls.asyncBidiStreamingCall(call, observer)
        args.sendTo(out)
      }

      CLIENT_STREAMING -> {
        val out = ClientCalls.asyncClientStreamingCall(call, observer)
        args.sendTo(out)
      }

      SERVER_STREAMING ->
        ClientCalls.asyncServerStreamingCall(call, args.receive(), observer)

      UNARY ->
        ClientCalls.asyncUnaryCall(call, args.receive(), observer)

      UNKNOWN -> throw UnsupportedOperationException(type.name)
    }
    finished.join()
  }
}
