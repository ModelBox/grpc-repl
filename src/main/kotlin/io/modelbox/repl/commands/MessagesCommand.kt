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

import com.google.protobuf.Descriptors.FieldDescriptor.Type.ENUM
import com.google.protobuf.Descriptors.FieldDescriptor.Type.MESSAGE
import io.modelbox.repl.cli.Console
import io.modelbox.repl.data.DescriptorManager
import io.modelbox.repl.cli.candidate
import org.jline.reader.Completer
import org.jline.reader.ParsedLine
import javax.inject.Inject

internal class MessagesCommand @Inject constructor(
    private val console: Console,
    private val descriptors: DescriptorManager) : Command {
  override val help: String = """
    Describe messages types.

    TODO: Display the SourceInfo in the descriptor set

    Usage:
      * messages - To print all message names
      * messages mdlbx.Stat - To print message schema
    """.trimIndent()
  override val name: String = "messages"

  override val completer: Completer
    get() = Completer { _, _, candidates ->
      descriptors.messages.forEach { desc ->
        candidates += candidate(desc.fullName)
      }
    }

  override fun execute(line: ParsedLine) {
    val name = line.words().getOrNull(1)
    if (name != null && name.isNotBlank()) {
      val found = descriptors.messages
          .firstOrNull { it.fullName == name }

      if (found == null) {
        console.error("Unknown message $name")
      } else {
        found.fields.forEach { field ->
          console.print { p ->
            p.print(field.jsonName)
            p.print(" - ")
            when (field.type) {
              ENUM -> p.print(field.enumType.fullName)
              MESSAGE -> p.print(field.messageType.fullName)
              else -> p.print(field.type)
            }
            p.println()
          }
        }
      }
    } else {
      descriptors.messages
          .map { it.fullName }
          .sorted()
          .forEach { console.println(it) }
    }
  }
}
