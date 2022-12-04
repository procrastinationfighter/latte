# Latc - compiler for Latte

As for now, the compiler consists only of the frontend (parsing + semantic analysis).

## Used tools and libraries

The compiler was written in Kotlin, using ANTLR4 for parsing (which has code generated in Java).
To build the project, Maven was used.

## File structure 

The source code for the compiler is located in `src/src/main/kotlin` directory.
Directory `src/src/main/java` contains automatically generated code for parsing.
Root directory contains a `Makefile`, Latte's modified grammar written in BNFC format
and a simple script for testing.

## Implemented extensions

The frontend accepts all suggested extensions: arrays, structs and objects.

## Running

To build, type `make` in the root directory. This should:

- download jar with ANTLR4 (if that doesn't happen, you can download it manually
from here: https://www.antlr.org/download/antlr-4.11.1-complete.jar and place it in the root directory)
- generate Java code (this requires `bnfc`, if you're running this on a different machine
than `students`, please add the path to bnfc binary to the `PATH` environmental variable)
- build the project
- create a script `latc` that runs the compiler

To run the compiler, run `./latc INPUT` where `INPUT` is the input file with `.lat` extension.

If the compilation should go to the next phase, the compiler will print `OK\n`
on stderr and return `0`. If the compilation failed, the compiler will print
`ERROR\n` and the first error message on stderr and return `1`.

The script `test.sh` checks if all tests in a directory terminate with given return code.
Example usage: `./test.sh latc lattests/good 0` where `latc` is the compiler script,
`lattests/good` is the directory and `0` is the return code.
