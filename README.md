# GRPC REPL

This is a general-purpose read-eval-print loop tool for interacting with GRPC services for
debugging and testing in an ad-hoc manner.

Notable features includes:
* Supports RPC services defined across many `.proto` files by using a `FileDescriptorSet`
as the basis for messages and methods.
* Interactive tab-completion / suggestion support for message names, methods,
and **message contents**.
* Supports self-signed or private-CA SSL certificates.

This tool requires Java 8 to run.

## Quickstart

Compile your `.proto` files with `protoc` the way you normally do, but with a couple of
extra flags: `--descriptor_set_out=my_protos.pb  --include_imports --include_source_info` to produce
a self-contained `FileDescriptorSet`.

[Download v0.1](https://github.com/ModelBox/grpc-repl/releases/tag/0.1) and unpack the repl tool:

```sh
unzip grpc-repl-0.1.zip
./grpc-repl-0.1/bin/grpc-repl \
    -d /path/to/my_protos.pb \
    -t https://my.server
```

![Demo Screencast](demo.gif)

## Current Status

Right now, this tool is pretty basic, intended mainly for creating ad-hoc queries or helping you 
create example payloads for documentation.  The output of the help command is displayed here, which
should give you an overview of what's currently possible.

```
edit
    | Edit a message.
    | 
    | Usage:
    |   * edit - List all editable messages
    |   * edit <variable> - Update an existing message
    |   * edit <variable> <message type> - Create / replace the named message

header
    | Set a header.
    | 
    | Usage:
    |   * header - List all currently-set headers
    |   * header <header> - Show the current value of the header
    |   * header <header> "new value" - Set the header
    | 
    | If the header name ends with "-bin", the value will be
    | interpreted as a base64-encoded value.

help
    | Display this message

messages
    | Describe messages types.
    | 
    | TODO: Display the SourceInfo in the descriptor set
    | 
    | Usage:
    |   * messages - To print all message names
    |   * messages mdlbx.Stat - To print message schema

print
    | Print a variable.
    | 
    | Usage:
    |   * print - List all messages
    |   * print <variable> - Print an existing message

quit
    | Exit the tool

rpc
    | Execute an RPC call.
    | 
    | If the RPC is a client-streaming message, the outgoing stream will be completed
    | when you type ^D.  The call will be cancelled on a ^C.
    | 
    | Usage:
    |   * rpc - List all RPC methods
    |   * rpc <method> - Call the method using an interactive editor
    |   * rpc <method> <variable> - Call the method using the named variable
    |   * rpc <method> <variable> <variable> .... - Call a client-streaming message

```
