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

import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType.*
import com.google.protobuf.Descriptors.FieldDescriptor.Type
import com.google.protobuf.Message
import io.modelbox.repl.cli.MessageParser.Companion.arraySentinel
import io.modelbox.repl.cli.MessageParser.Companion.objectSentinel
import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine

/**
 * A [Completer] to produce a json-format version of a protocol message.  This must be used
 * in conjunction with a [MessageParser] to produce a [MessageParsedLine].
 */
internal class MessageCompleter<T : Message>(private val template: T) : Completer {
  override fun complete(reader: LineReader,
                        line: ParsedLine,
                        candidates: MutableList<Candidate>) {
    val parsed = line as? MessageParsedLine<*> ?: return
    if (parsed.location.isEmpty()) {
      candidates += candidate("{")
    } else {
      findCompleter(parsed, template.descriptorForType)
          ?.complete(reader, parsed, candidates)
    }
  }

  /**
   * Given some JSON path that we're building up, find a Completer to perform the current
   * completion.
   *
   * @parsed the extracted JSON context data
   * @idx the current index in the path being operated on
   * @desc the [Descriptor] that should be traversed
   */
  private tailrec fun findCompleter(parsed: MessageParsedLine<*>,
                                    desc: Descriptor,
                                    idx: Int = 0): Completer? {
    val jsonName = parsed.location[idx]
    // If we're in the middle, the name must point to a message field name
    if (idx < parsed.location.lastIndex) {
      // We'll see foo.{}.baz.[].quux when we're drilling down, so
      // ignore object sentinels in the middle of the list
      if (jsonName == objectSentinel || jsonName == arraySentinel) {
        return findCompleter(parsed, desc, idx + 1)
      }
      val field = desc.findFieldByJson(
          jsonName) ?: return Completer { _, _, candidates ->
        candidates += candidate(
            value = "",
            description = "Unknown field $jsonName",
            complete = false)
      }
      if (field.type == Type.MESSAGE) {
        return findCompleter(parsed, field.messageType, idx + 1)
      } else {
        return null
      }
    }

    // We're looking at the last element, so we need to know if we're looking
    // at a field name that's under construction (thus requiring the field names),
    // or if there's a completed field name, then we want its value suggestions
    val field = desc.findFieldByJson(jsonName)
    if (field != null) {
      return field.valueCompleter
    }

    val tailWord = parsed.word().takeIf { it.isNotBlank() } ?:
        parsed.words().getOrNull(parsed.wordIndex() - 1) ?: ""

    // If the tail word ends with a comma, just insert a space
    if (parsed.word().isNotBlank() && tailWord.endsWith(",")) {
      return Completer { _, _, candidates ->
        candidates += candidate("$tailWord ")
      }
    }

    // We may need to insert a comma if we're starting a new chunk of output
    // there are siblings here and the previous word doesn't already end with a comma
    val siblings = parsed.siblings[idx]
    val needsComma = parsed.word().isBlank() && siblings > 0 && !tailWord.endsWith(",")

    return Completer { reader, parsed, candidates ->
      // Kick out a completion for each field name
      desc.fields.forEach { field ->
        field.fieldNameCompleter
            .let { fieldNames ->
              if (needsComma) {
                fieldNames.mapValue { ", " + it }
              } else {
                fieldNames
              }
            }
            .complete(reader, parsed, candidates)
      }
      // If we can add a field name, we can also close the message, too
      candidates += candidate(" }")
    }
  }

  /**
   * Wrap a [Completer] to tweak its suggestions.
   */
  private fun Completer.mapValue(fn: (String) -> String) = Completer { reader, parsed, candidates ->
    val temp = mutableListOf<Candidate>()
    complete(reader, parsed, temp)

    temp.forEach { c ->
      candidates += candidate(
          complete = c.complete(),
          description = c.descr(),
          display = c.displ(),
          group = c.group(),
          key = c.key(),
          suffix = c.suffix(),
          value = fn(c.value()))
    }
  }

  private fun Descriptor.findFieldByJson(jsonName: String): FieldDescriptor? =
      fields.firstOrNull { it.jsonName == jsonName }

  /**
   * A [Completer] that will produce a json field name `"foo" :`.
   */
  private val FieldDescriptor.fieldNameCompleter: Completer
    get() = Completer { _, _, candidates ->
      var mods = ""
      if (isRepeated) {
        mods += " ["
      }
      val displayType = if (type == Type.MESSAGE) {
        mods += " {"
        messageType.name
      } else {
        type.toString()
      }

      val group = containingOneof?.fullName

      candidates += candidate(
          value = "\"$jsonName\":$mods",
          display = "$jsonName: <$displayType>",
          group = group,
          key = jsonName)
    }

  /**
   * A [Completer] that will produce a json value suggestion.
   */
  private val FieldDescriptor.valueCompleter: Completer
    get() = Completer { _, _, candidates ->
      return@Completer when (javaType!!) {
        BOOLEAN -> {
          candidates += candidate("true")
          candidates += candidate("false")
        }
        ENUM -> {
          enumType.values.forEach { enumValue ->
            candidates += candidate(
                value = "\"${enumValue.name}\"",
                group = enumType.name)
          }
        }
        MESSAGE -> {
          candidates += candidate(
              value = if (isRepeated) "[ {" else " {",
              display = "{ ${messageType.name} }")
        }
        else -> {
          candidates += candidate(
              value = if (isRepeated) "[" else "",
              display = type.toString(),
              complete = false)
        }
      }
    }
}
