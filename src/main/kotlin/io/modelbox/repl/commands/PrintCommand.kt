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
import io.modelbox.repl.cli.Console
import io.modelbox.repl.data.NamedMessages
import io.modelbox.repl.cli.candidate
import io.modelbox.repl.cli.toJson
import org.jline.reader.Completer
import org.jline.reader.ParsedLine
import javax.inject.Inject

@Reusable
internal class PrintCommand @Inject constructor(
    private val console: Console,
    private val namedMessages: NamedMessages) : Command {
  override val completer: Completer
    get() = Completer { _, line, candidates ->
      when (line.wordIndex()) {
        1 -> {
          candidates += candidate(value = "", description = "<Variable Name>")
          namedMessages.get().forEach { entry ->
            candidates += candidate(entry.key)
          }
        }
      }
    }

  override val help: String?
    get() = """
    Print a variable.

    Usage:
      * $name - List all messages
      * $name <variable> - Print an existing message
    """.trimIndent()

  override val name: String
    get() = "print"

  override fun execute(line: ParsedLine) {
    val varName = line.words().getOrNull(1) ?:
        return namedMessages.get().forEach { entry ->
          console.println("${entry.key} - ${entry.value.descriptorForType.fullName}")
        }

    val message = namedMessages[varName] ?:
        return console.error("Unknown message - $varName")

    console.println(message.toJson())
  }

}
