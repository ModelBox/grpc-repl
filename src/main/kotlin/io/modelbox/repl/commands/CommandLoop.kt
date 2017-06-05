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

import io.grpc.StatusRuntimeException
import io.modelbox.repl.cli.Console
import io.modelbox.repl.cli.candidate
import org.jline.reader.EndOfFileException
import org.jline.reader.History
import org.jline.reader.LineReader.Option.*
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.terminal.Terminal
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
internal class CommandLoop @Inject constructor(
    private val console: Console,
    commands: MutableSet<Command>,
    @Named("command") private val history: History,
    private val terminal: Terminal) {

  private val commandsByName = commands.associateBy { it.name }

  fun run() {
    val r = LineReaderBuilder.builder()
        .completer {
          reader, line, candidates ->
          when (line.wordIndex()) {
            0 -> commandsByName.forEach { candidates += candidate(it.key) }
            else -> {
              commandsByName[line.words().getOrNull(0)]
                  ?.completer?.complete(reader, line, candidates)
            }
          }
        }
        .history(history)
        .terminal(terminal)
        .build()

    // Clean input setup
    r.setOpt(AUTO_FRESH_LINE)
    r.setOpt(CASE_INSENSITIVE)
    // Treat initial tabs as completion requests
    r.unsetOpt(INSERT_TAB)

    while (true) {
      try {
        console.readLine(r, prompt = "> ")
        val cmdWord = r.parsedLine.words().firstOrNull() ?: continue
        if (cmdWord.isBlank()) {
          continue
        }
        val command = commandsByName[cmdWord]
        when (command) {
          null -> console.error("Unknown command $cmdWord")
          is QuitCommand -> return
          else -> command.execute(r.parsedLine)
        }
      } catch (e: EndOfFileException) {
        return
      } catch (e: StatusRuntimeException) {
        console.error(e.status.toString())
      } catch (e: UserInterruptException) {
        continue
      } catch (e: Throwable) {
        console.print { e.printStackTrace(it) }
      }
    }
  }
}
