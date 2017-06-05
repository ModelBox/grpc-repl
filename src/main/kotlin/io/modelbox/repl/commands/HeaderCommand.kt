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

import dagger.Reusable
import io.grpc.Metadata
import io.modelbox.repl.cli.Console
import io.modelbox.repl.data.RpcCaller
import org.jline.reader.ParsedLine
import javax.inject.Inject

@Reusable
internal class HeaderCommand @Inject constructor(
    private val caller: RpcCaller,
    private val console: Console) : Command {

  override val help: String
    get() = """
    Set a header.

    Usage:
      * $name - List all currently-set headers
      * $name <header> - Show the current value of the header
      * $name <header> "new value" - Set the header

    If the header name ends with "${Metadata.BINARY_HEADER_SUFFIX}", the value will be
    interpreted as a base64-encoded value.
    """.trimIndent()

  override val name: String
    get() = "header"

  override fun execute(line: ParsedLine) {
    val args = line.words().filter { it.isNotBlank() }.drop(1)

    when (args.size) {
      0 -> caller.mutableHeaders.forEach { entry ->
        console.println("${entry.key} ${entry.value}")
      }
      1 -> console.println(caller.mutableHeaders[args[0]] ?: "Unset")
      2 -> {
        val key = args[0]
        val value = args[1]
        caller.mutableHeaders[key] = value
      }
      else -> console.error("Could not interpret")
    }
  }
}
