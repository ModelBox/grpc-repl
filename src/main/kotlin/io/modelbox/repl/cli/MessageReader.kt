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
package io.modelbox.repl.cli

import com.google.protobuf.Message
import dagger.Reusable
import org.jline.reader.LineReader.Option.*
import org.jline.reader.LineReader.SECONDARY_PROMPT_PATTERN
import org.jline.reader.LineReaderBuilder
import org.jline.terminal.Terminal
import javax.inject.Inject

@Reusable
internal class MessageReader @Inject constructor(
    private val console: Console,
    private val terminal: Terminal) {

  /**
   * Reads a single message based on [template] from the [terminal].
   */
  fun <T : Message> read(template: T): MessageParsedLine<T> {
    val r = LineReaderBuilder.builder()
        .terminal(terminal)
        .completer(MessageCompleter(template))
        .expander(NoOpExpander)
        .parser(MessageParser(template))
        .build()

    val prompt = "${template.descriptorForType.name} > "
    r.setVariable(SECONDARY_PROMPT_PATTERN, "> ".padStart(prompt.length, ' '))

    // Clean input setup
    r.setOpt(AUTO_FRESH_LINE)
    r.setOpt(CASE_INSENSITIVE)
    // Prevent stray exclamation marks from breaking things
    r.setOpt(DISABLE_EVENT_EXPANSION)
    // Treat initial tabs as completion requests
    r.unsetOpt(INSERT_TAB)

    console.readLine(
        reader = r,
        prompt = prompt,
        buffer = if (template == template.defaultInstanceForType) null else template.toJson(true))
    @Suppress("UNCHECKED_CAST")
    return r.parsedLine as MessageParsedLine<T>
  }
}
