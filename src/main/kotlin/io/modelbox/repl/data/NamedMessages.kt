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
package io.modelbox.repl.data

import com.google.protobuf.Empty.getDefaultInstance
import com.google.protobuf.Message
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Holds named message instances for use by the editor and RPC endpoints.
 */
@Singleton
internal class NamedMessages @Inject constructor() : Provider<Map<String, Message>> {
  private val map = mutableMapOf<String, Message>()

  init {
    map["empty"] = getDefaultInstance()
  }

  operator fun get(name: String): Message? = map[name]
  override fun get(): Map<String, Message> = map.toSortedMap()
  operator fun set(name: String, message: Message) {
    map[name] = message
  }

  fun shift(name: String, message: Message, limit: Int = 20) {
    val shifted = map.entries.asSequence()
        .filter { it.key == name || it.key.startsWith("${name}_") }
        .map { entry ->
          val parts = entry.key.split("_", limit = 2)
          val idx = parts.getOrElse(1) { "0" }.toInt()
          if (idx < limit) {
            "${name}_${idx + 1}" to entry.value
          } else {
            null
          }
        }
        .filterNotNull()
        .toMap()

    map.putAll(shifted)
    map[name] = message
  }
}
