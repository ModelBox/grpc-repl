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

import org.jline.reader.LineReader
import org.jline.terminal.Terminal
import org.jline.utils.AttributedCharSequence
import org.jline.utils.AttributedStringBuilder
import org.jline.utils.AttributedStyle
import org.jline.utils.AttributedStyle.RED
import java.io.PrintWriter
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock

/**
 * Coordinates printing with reading lines.
 */
@Singleton
internal class Console @Inject constructor(
    private val terminal: Terminal) {

  private var currentReader: LineReader? = null
  private val lock = ReentrantLock()

  fun error(message: CharSequence) {
    val sb = AttributedStringBuilder()
    sb.append("Error: ".attributed { foreground(RED) })
    sb.append(message)
    println(sb)
  }

  /**
   * Print some text above the current input.
   *
   * Note that this method will block if there is an active [LineReader] and the caller
   * is not on the same thread that called [use].
   */
  fun println(str: AttributedCharSequence) {
    println(str.toAnsi(terminal))
  }

  /**
   * Print some text above the current input.
   *
   * Note that this method will block if there is an active [LineReader] and the caller
   * is not on the same thread that called [use].
   */
  fun println(data: CharSequence) {
    print { it.println(data) }
  }


  /**
   * Print some text above the current input.
   *
   * Note that this method will block if there is an active [LineReader] and the caller
   * is not on the same thread that called [use].
   */
  fun println(data: CharSequence, style: AttributedStyle.() -> AttributedStyle) {
    println(data.attributed(style))
  }

  /**
   * Call [LineReader.readLine].
   */
  fun readLine(reader: LineReader,
               prompt: String? = null,
               rightPrompt: String? = null,
               mask: Char? = null,
               buffer: String? = null): String {
    return use(reader) {
      reader.readLine(prompt, rightPrompt, mask, buffer)
    }
  }

  /**
   * Write text to the console.
   *
   * Note that this method will block if there is an active [LineReader] and the caller
   * is not on the same thread that called [use].
   */
  fun print(block: (PrintWriter) -> Unit) {
    lock.withLock {
      val r = currentReader
      if (r == null) {
        block(terminal.writer())
      } else {
        r.callWidget(LineReader.CLEAR)
        block(terminal.writer())
        r.callWidget(LineReader.REDRAW_LINE)
        r.callWidget(LineReader.REDISPLAY)
      }
      terminal.writer().flush()
    }
  }

  /**
   * Execute the given [block], coordinating any printing activity with [newReader].
   */
  fun <T> use(newReader: LineReader, block: () -> T): T {
    lock.withLock {
      if (currentReader != null) {
        throw IllegalStateException("Multiple readers")
      }
      currentReader = newReader
      try {
        return block()
      } finally {
        currentReader = null
      }
    }
  }
}
