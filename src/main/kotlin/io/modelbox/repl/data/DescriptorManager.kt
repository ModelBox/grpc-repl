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

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.DescriptorProtos.FileDescriptorSet
import com.google.protobuf.Descriptors.*
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
internal class DescriptorManager @Inject constructor(
    @Named("descriptorFile") private val file: File) : Iterable<FileDescriptor> {
  private val cache: LoadingCache<String, FileDescriptor> =
      CacheBuilder.newBuilder().build(CacheLoader.from { name ->
        load(name!!)
      })

  private val protosByName: Map<String, FileDescriptorProto> = run {
    val set = FileInputStream(file).use { s ->
      FileDescriptorSet.parseFrom(s)
    }
    set.fileList.associateBy { it.name }
  }

  override fun iterator(): Iterator<FileDescriptor> {
    return protosByName.asSequence().map { cache[it.key] }.iterator()
  }

  private fun load(name: String): FileDescriptor {
    val proto = protosByName[name] ?: throw IllegalArgumentException(name)

    val dependencies = proto.dependencyList.map { cache[it] }
    return FileDescriptor.buildFrom(proto, dependencies.toTypedArray())
  }

  /**
   * All message descriptors.
   */
  val messages: Sequence<Descriptor>
    get() = asSequence()
        .flatMap { it.messageTypes.asSequence() }
        .flatMap { it.andChildren }

  /**
   * All service methods.
   */
  val methods: Sequence<MethodDescriptor>
    get() = asSequence()
        .flatMap { it.services.asSequence() }
        .flatMap { it.methods.asSequence() }

  /**
   * Emit the descriptor and all of its nested messages.
   */
  private val Descriptor.andChildren: Sequence<Descriptor>
    get() {
      return sequenceOf(this) +
          nestedTypes.asSequence().flatMap { nested ->
            nested.andChildren
          }
    }
}
