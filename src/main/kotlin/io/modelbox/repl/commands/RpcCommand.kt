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
package io.modelbox.repl.commands

import com.google.protobuf.DynamicMessage
import com.google.protobuf.Message
import io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING
import io.grpc.MethodDescriptor.MethodType.UNARY
import io.modelbox.repl.cli.Console
import io.modelbox.repl.cli.MessageReader
import io.modelbox.repl.cli.candidate
import io.modelbox.repl.cli.toJson
import io.modelbox.repl.data.DescriptorManager
import io.modelbox.repl.data.NamedMessages
import io.modelbox.repl.data.RpcCaller
import io.modelbox.repl.data.methodType
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.runBlocking
import org.jline.reader.Completer
import org.jline.reader.EndOfFileException
import org.jline.reader.ParsedLine
import org.jline.terminal.Terminal
import org.jline.terminal.Terminal.Signal.INT
import javax.inject.Inject

internal class RpcCommand @Inject constructor(
    private val console: Console,
    private val descriptors: DescriptorManager,
    private val namedMessages: NamedMessages,
    private val messageReader: MessageReader,
    private val rpcCaller: RpcCaller,
    private val terminal: Terminal) : Command {
  override val help
    get() = """
    Execute an RPC call.

    If the RPC is a client-streaming message, the outgoing stream will be completed
    when you type ^D.  The call will be cancelled on a ^C.

    Usage:
      * $name - List all RPC methods
      * $name <method> - Call the method using an interactive editor
      * $name <method> <variable> - Call the method using the named variable
      * $name <method> <variable> <variable> .... - Call a client-streaming message
  """.trimIndent()

  override val name
    get() = "rpc"

  override val completer: Completer
    get() = Completer { _, line, candidates ->
      when (line.wordIndex()) {
        0 -> Unit
        1 -> descriptors.methods.forEach { method ->
          candidates += candidate(method.fullName)
        }
        else -> {
          val method = descriptors.methods.firstOrNull {
            it.fullName == line.words().getOrNull(1)
          } ?: return@Completer

          // Find obviously-compatible messages
          val compatible = namedMessages.get().filterValues { msg ->
            msg.descriptorForType == method.inputType
          }
          compatible.forEach { entry ->
            candidates += candidate(entry.key)
          }
        }
      }
    }

  override fun execute(line: ParsedLine) {
    val methodName = line.words().getOrNull(1) ?:
        return descriptors.methods.forEach { m ->
          console.println("${m.fullName} - ${m.inputType.fullName} -> ${m.outputType.fullName}")
        }

    val method = descriptors.methods.firstOrNull { it.fullName == methodName } ?:
        return console.error("Unknown method $methodName")

    // Drop "rpc methodname"
    val argNames = line.words().drop(2).filter { it.isNotBlank() }
    val args = if (argNames.isEmpty()) {
      val type = method.methodType

      produce<Message>(CommonPool) {
        outer@ while (!isClosedForSend) {
          try {
            val messageLine = messageReader.read(
                DynamicMessage.getDefaultInstance(method.inputType))
            if (messageLine.message != null) {
              namedMessages.shift("last", messageLine.message)
              send(messageLine.message)

              // Only need one value, so we can just exit from the loop
              when (type) {
                UNARY,
                SERVER_STREAMING -> break@outer
                else -> Unit
              }
            } else if (messageLine.parseError != null) {
              console.error(messageLine.parseError)
              console.println("Try again or ^C to stop")
            }
          } catch (e: EndOfFileException) {
            close()
          }
        }
      }
    } else {
      produce<Message>(CommonPool) {
        argNames.forEach {
          send(namedMessages[it] ?: throw IllegalArgumentException("Unknown variable name $it"))
        }
        close()
      }
    }

    runBlocking {
      val invocation = rpcCaller.invoke(method, args)

      val oldInt = terminal.handle(INT) {
        invocation.cancel()
      }

      try {
        invocation.consumeEach { console.println(it.toJson(true)) }
      } finally {
        terminal.handle(INT, oldInt)
      }
    }
  }
}
