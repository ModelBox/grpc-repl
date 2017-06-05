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

import com.google.protobuf.MessageOrBuilder
import com.google.protobuf.util.JsonFormat
import org.jline.reader.Candidate
import org.jline.utils.AttributedCharSequence
import org.jline.utils.AttributedString
import org.jline.utils.AttributedStyle

internal fun candidate(value: String,
                       display: String = value,
                       group: String? = null,
                       description: String? = null,
                       suffix: String? = null,
                       key: String? = null,
                       complete: Boolean = true): Candidate {
  return Candidate(value, display, group, description, suffix, key, complete)
}

internal fun CharSequence.attributed(
    style: AttributedStyle.() -> AttributedStyle): AttributedCharSequence {
  return AttributedString(this, AttributedStyle.DEFAULT.style())
}


/**
 * Convert the message to its json representation.
 */
internal fun MessageOrBuilder.toJson(prettyPrint: Boolean = false): String =
    try {
      JsonFormat.printer()
          .let {
            if (prettyPrint) {
              it
            } else {
              it.omittingInsignificantWhitespace()
            }
          }
          .print(this)
    } catch (e: Throwable) {
      // Don't blow up since we often call this message from logging statements
      "{\"error\":\"Unable to Jsonify\"}"
    }
