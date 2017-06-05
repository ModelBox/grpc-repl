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

import org.jline.reader.LineReaderBuilder
import org.jline.terminal.TerminalBuilder
import org.jline.utils.AttributedStyle
import java.io.File
import java.net.URI
import java.security.KeyStore
import kotlin.system.exitProcess

/**
 * Entry point and trivial command-line parsing.
 */
object Repl {
  private val help = """
  GRPC REPL tool

  Flags:

  -d, --descriptor /path/to/file_descriptor_set.pb
      A path to the output from protoc --descriptor_set_out which contains a FileDescriptorSet.
      You probably also want to call protoc with --include_imports and --include_source_info
      to create a completely self-contained file.

  -h, --help
      This message.

  -t, --target https://your.server
      An https:// or http:// URI to specify the target server.  An alternate port may be specified.

  --trust /path/to/trusted_keys.jks
      A path to a keystore file containing SSL certificates that should be trusted when connecting
      to SSL servers using self-signed or private-CA certificates.

      To create this file, use the java keytool command:
        keytool -import \
          -alias "development" \
          -file PATH/TO/self_signed.crt \
          -keystore trusted_keys.jks \

      The alias used for the key doesn't matter, it just needs to be distinct within the store.

  --trustpasswd trustStorePassword
      The password to use when reading the trust store.  If not specified, you will be prompted.
  """.trimIndent()

  @JvmStatic
  fun main(args: Array<String>) {
    val terminal = TerminalBuilder.builder().build()
    val out = terminal.writer()

    var descriptorFile: File? = null
    var target: URI? = null
    var trustFile: File? = null
    var trustPassword: String? = null

    args.asSequence()
        .flatMap { arg -> arg.split("=").asSequence() }
        .iterator()
        .let {
          while (it.hasNext()) {
            val flag = it.next().toLowerCase()
            when (flag) {
              "-d", "--descriptors" -> {
                descriptorFile = File(it.next()).also {
                  check(it.isFile) { "$it is not a file" }
                }
              }
              "-h", "--help" -> {
                out.println(help)
                out.flush()
                exitProcess(0)
              }
              "-t", "--target" -> {
                target = URI.create(it.next()).also {
                  check(it.scheme != null) { "--target URI requires a scheme (e.g. https://)" }
                }
              }
              "--trust" -> {
                trustFile = File(it.next()).also {
                  check(it.isFile) { "$it is not a file" }
                }
              }
              "--trustpasswd" -> {
                trustPassword = it.next()
              }
              else -> {
                out.println("Unknown flag: $flag")
                out.flush()
                exitProcess(1)
              }
            }
          }
        }


    val tool = try {
      ReplTool.build { builder ->
        builder.bindDescriptors(descriptorFile ?:
            throw IllegalArgumentException("Missing flag --descriptor"))
        builder.bindTarget(target ?: throw IllegalArgumentException("Missing flag --target"))
        builder.bindTerminal(terminal)

        trustFile?.let { f ->
          val pw = trustPassword ?: run {
            val pwReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build()

            pwReader.readLine("Enter keystore password (will not echo): ", ' ')
          }

          val keystore = f.inputStream().use { inStream ->
            val ks = KeyStore.getInstance("JKS")
            ks.load(inStream, pw.toCharArray())
            ks
          }

          builder.bindTrustKeystore(keystore)
        }
      }
    } catch (e: Throwable) {
      terminal.writer().println(e.message)
      terminal.flush()
      exitProcess(1)
    }

    // Use our Console coordinator now that the tool is running
    val console = tool.console

    console.println("GRPC REPL ready") {
      foreground(AttributedStyle.GREEN)
    }
    console.println("${tool.descriptors.messages.count()} messages") {
      foreground(AttributedStyle.GREEN)
    }
    console.println("${tool.descriptors.methods.count()} RPC methods") {
      foreground(AttributedStyle.GREEN)
    }
    console.println("Type 'help' for information. 'quit' or ^D to exit.")

    // Loop until done
    try {
      tool.commandLoop.run()
      exitProcess(0)
    } catch (e: Throwable) {
      console.print { e.printStackTrace(it) }
      exitProcess(1)
    }
  }
}
