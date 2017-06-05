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
import org.jline.reader.ParsedLine

internal class MessageParsedLine<out T : Message>(
    private val delegate: ParsedLine,
    /**
     * If present, additional information about why the parse failed.
     */
    val parseError: String? = null,
    /**
     * The path through the JSON being created.
     */
    val location: List<String> = emptyList(),
    /**
     * The total number of siblings at the location.
     */
    val siblings: List<Int> = emptyList(),
    /**
     * The updated message, if the input was successfully parsed.
     */
    val message: T? = null) : ParsedLine by delegate
