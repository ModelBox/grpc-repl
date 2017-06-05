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

import io.modelbox.repl.cli.Console
import io.modelbox.repl.cli.attributed
import org.jline.reader.ParsedLine
import org.jline.utils.AttributedStringBuilder
import javax.inject.Inject
import javax.inject.Provider

internal class HelpCommand @Inject constructor(
    private val console: Console,
    private val commands: Provider<MutableSet<Command>>) : Command {
  override val help: String = "Display this message"
  override val name: String = "help"

  override fun execute(line: ParsedLine) {
    commands.get().asSequence()
        .sortedBy { it.name }
        .forEach { command ->
          val sb = AttributedStringBuilder()
          sb.append(command.name.attributed { bold() })
          command.help?.let {
            sb.append("\n")
            sb.append(it.replaceIndent("    | "))
          }
          sb.append("\n")
          console.println(sb)
        }
  }
}
