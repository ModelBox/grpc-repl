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
import io.modelbox.repl.data.DescriptorManager
import io.modelbox.repl.data.NamedMessages
import io.modelbox.repl.cli.Console
import io.modelbox.repl.cli.MessageReader
import io.modelbox.repl.cli.candidate
import io.modelbox.repl.cli.toJson
import org.jline.reader.Completer
import org.jline.reader.ParsedLine
import javax.inject.Inject
import javax.inject.Provider

internal class EditCommand @Inject constructor(
    private val console: Console,
    private val descriptors: DescriptorManager,
    private val namedMessages: NamedMessages,
    private val messageReaders: Provider<MessageReader>) : Command {
  override val help
    get() = """
    Edit a message.

    Usage:
      * $name - List all editable messages
      * $name <variable> - Update an existing message
      * $name <variable> <message type> - Create / replace the named message
    """.trimIndent()

  override val completer: Completer
    get() = Completer { _, line, candidates ->
      when (line.wordIndex()) {
        1 -> {
          candidates += candidate(value = "", description = "<Variable Name>")
          namedMessages.get().forEach { entry ->
            candidates += candidate(entry.key)
          }
        }
        2 -> descriptors.messages.forEach { desc ->
          candidates += candidate(desc.fullName)
        }
        else -> console.error("Bad index ${line.wordIndex()}")
      }
    }
  override val name
    get() = "edit"

  override fun execute(line: ParsedLine) {
    val words = line.words()

    val varName = words.getOrNull(1) ?:
        return namedMessages.get().forEach { entry ->
          console.println("${entry.key} - ${entry.value.descriptorForType.fullName}")
        }

    val existing = namedMessages[varName]
    val template = if (existing == null) {
      val messageName = words.getOrNull(2) ?:
          return console.error("Message type name required")
      val message = descriptors.messages.firstOrNull { it.fullName == messageName } ?:
          return console.error("Unknown message type $messageName")
      DynamicMessage.getDefaultInstance(message)
    } else {
      existing
    }

    val read = messageReaders.get().read(template)
    if (read.message != null) {
      namedMessages[varName] = read.message
      console.println(read.message.toJson(true))
    } else if (read.parseError != null) {
      console.error(read.parseError)
    }
  }

}
