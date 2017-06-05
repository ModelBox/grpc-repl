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

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken.*
import com.google.gson.stream.MalformedJsonException
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat
import org.jline.reader.EOFError
import org.jline.reader.ParsedLine
import org.jline.reader.Parser.ParseContext
import org.jline.reader.impl.DefaultParser
import java.io.EOFException
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * A parser that reads JSON as its input in order to produce an updated message from [template].
 */
internal class MessageParser<out T : Message>(private val template: T) : DefaultParser() {
  internal companion object {
    const val arraySentinel = "[]"
    const val objectSentinel = "{}"
  }

  init {
    quoteChars = charArrayOf()
  }

  override fun parse(line: String, cursor: Int, context: ParseContext): ParsedLine {
    val delegate = super.parse(line, cursor, context)
    if (context == ParseContext.SECONDARY_PROMPT) {
      return delegate
    }

    // Stop parsing early if we're trying to tab-complete
    val toRead = if (context == ParseContext.COMPLETE) {
      line.substring(0, cursor)
    } else {
      line
    }

    val reader = JsonReader(toRead.reader())
    reader.isLenient = true

    // Initialize stacks
    val locationStack = ArrayDeque<Pair<String, AtomicInteger>>()

    fun beginValue(name: String, newFrame: Boolean) {
      val completedValues = if (newFrame) {
        AtomicInteger()
      } else {
        locationStack.peekLast().second.also { it.incrementAndGet() }
      }
      locationStack.addLast(name to completedValues)
    }

    fun endValue(closeFrame: Boolean) {
      if (locationStack.isEmpty()) {
        return
      }
      locationStack.removeLast()
      if (closeFrame && locationStack.isNotEmpty()) {
        locationStack.removeLast()
      }
    }

    try {
      outer@ while (true) {
        val token = reader.peek()
        when (token!!) {
          BEGIN_ARRAY -> {
            reader.beginArray()
            beginValue(arraySentinel, true)
          }
          END_ARRAY -> {
            reader.endArray()
            endValue(true)
          }
          BEGIN_OBJECT -> {
            reader.beginObject()
            // Add a sentinel to know what we're looking at a new object.
            beginValue(objectSentinel, true)
          }
          END_OBJECT -> {
            reader.endObject()
            endValue(true)
          }
          NAME -> beginValue(reader.nextName(), false)
          STRING,
          NUMBER,
          BOOLEAN,
          NULL -> {
            reader.skipValue()
            endValue(false)
          }
          END_DOCUMENT -> break@outer
        }
      }
    } catch (e: MalformedJsonException) {
      return MessageParsedLine<Nothing>(
          delegate,
          parseError = e.message,
          location = locationStack.map { it.first },
          siblings = locationStack.map { it.second.get() })
    } catch (e: EOFException) {
      // Catch the case where we want to go into multi-line editing
      if (context == ParseContext.ACCEPT_LINE) {
        throw EOFError(0, 0, "EOF", locationStack.joinToString("."))
      }
      return MessageParsedLine<Nothing>(
          delegate,
          parseError = e.message,
          location = locationStack.map { it.first },
          siblings = locationStack.map { it.second.get() })
    }

    // If we're in the final pass, reconstitute the message
    if (context == ParseContext.ACCEPT_LINE) {
      val builder = template.newBuilderForType()
      try {
        JsonFormat.parser().merge(line, builder)
        return MessageParsedLine(delegate, message = builder.build())
      } catch (e: InvalidProtocolBufferException) {
        if (e.cause is EOFException) {
          throw EOFError(0, 0, e.message)
        } else {
          return MessageParsedLine<Nothing>(delegate, parseError = e.message)
        }
      } catch (e: EOFException) {
        throw EOFError(0, 0, e.message)
      }
    }

    return delegate
  }
}
