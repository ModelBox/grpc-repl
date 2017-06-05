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
package io.modelbox.repl

import dagger.BindsInstance
import dagger.Component
import io.modelbox.repl.cli.Console
import io.modelbox.repl.commands.CommandLoop
import io.modelbox.repl.commands.CommandModule
import io.modelbox.repl.data.DescriptorManager
import org.jline.reader.History
import org.jline.reader.impl.history.DefaultHistory
import org.jline.terminal.Terminal
import java.io.File
import java.net.URI
import java.security.KeyStore
import javax.inject.Named
import javax.inject.Singleton

@Component(modules = arrayOf(CommandModule::class))
@Singleton
internal interface ReplTool {
  companion object {
    inline fun build(fn: (Builder) -> Unit): ReplTool =
        DaggerReplTool.builder().also(fn).build()
  }

  @dagger.Component.Builder
  @Suppress("LeakingThis")
  abstract class Builder {
    init {
      bindCommandHistory(DefaultHistory())
      bindMessageHistory(DefaultHistory())
    }

    @BindsInstance
    abstract fun bindCommandHistory(@Named("command") history: History)

    @BindsInstance
    abstract fun bindDescriptors(@Named("descriptorFile") file: File)

    @BindsInstance
    abstract fun bindMessageHistory(@Named("message") history: History)

    @BindsInstance
    abstract fun bindTarget(@Named("target") target: URI)

    @BindsInstance
    abstract fun bindTrustKeystore(@Named("trustKeystore") store: KeyStore?)

    @BindsInstance
    abstract fun bindTerminal(terminal: Terminal)

    abstract fun build(): ReplTool

  }

  val console: Console
  val commandLoop: CommandLoop
  val descriptors: DescriptorManager
}
