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

import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet

@Module
internal interface CommandModule {
  @Binds
  @IntoSet
  fun edit(x: EditCommand): Command

  @Binds
  @IntoSet
  fun help(x: HelpCommand): Command

  @Binds
  @IntoSet
  fun header(x: HeaderCommand): Command

  @Binds
  @IntoSet
  fun messages(x: MessagesCommand): Command

  @Binds
  @IntoSet
  fun print(x: PrintCommand): Command

  @Binds
  @IntoSet
  fun quit(x: QuitCommand): Command

  @Binds
  @IntoSet
  fun rpc(x: RpcCommand): Command
}

