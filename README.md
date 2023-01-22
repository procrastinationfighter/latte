# Latc_llvm - compiler for Latte

As for now, the compiler does the following steps:

1.frontend (parsing + semantic correctness checks)
2.converting into SSA (with Phi variables)
3.optimizing SSA structure (LCSE + removing dead code)
4.generating LLVM code
5.assembling and linking generated LLVM code into a single `.bc` file

## Used tools and libraries

The compiler was written in Kotlin, using ANTLR4 for parsing (which has code generated in Java).
To build the project, Maven was used.

Additional file `runtime.ll` with built-in latte functions has been generated using `clang`, basing on code in C.
Besides defining standard functions for Latte, this file also defines two functions: `add.Str` and `compare.Str` that
are used for concatenating and comparing strings.

## File structure 

The source code for the compiler is located in `src/src/main/kotlin` directory.
Directory `src/src/main/java` contains automatically generated code for parsing.
Root directory contains a `Makefile`, Latte's modified grammar written in BNFC format
and a simple script for testing.

## Implemented optimizations

The compiler currently:

- uses Phi instructions instead of `alloca`
- performs LCSE optimizations on generated SSA structure
- removes dead code (by removing unused operations different from function calls)

## Implemented language extensions

The backend supports classes with virtual functions.

## Fixes

Bugs of frontend have been fixed in this version. In particular, grammar conflicts have been resolved.
The only remaining conflicts are shift/reduce conflicts - one of them is the if/else conflict and the rest of them are analogous.

One particular nasty problem came from the parser, where most binary operations like `+` failed in some cases. It has been fixed.

## Running

To build, type `make` in the root directory. This should:

- download jar with ANTLR4 (if that doesn't happen, you can download it manually
from here: https://www.antlr.org/download/antlr-4.11.1-complete.jar and place it in the root directory)
- generate Java code (this requires `bnfc`, if you're running this on a different machine
than `students`, please add the path to bnfc binary to the `PATH` environmental variable)
- build the project
- create a script `latc_llvm` that runs the compiler
- create a script `latc` that runs only the frontend

To run the compiler, run `./latc_llvm INPUT` in the root directory,
where `INPUT` is the input file with `.lat` extension.

If the compilation succeeded, the compiler will print `OK\n`
on stderr and compile the program. If the compilation failed, the compiler will print
`ERROR\n` and the first error message on stderr and return `1`.

For a correct input file `foo/bar/example.lat`, the compiler will generate file `foo/bar/example.bc`
which can be run using `lli foo/bar/example.bc`. 

To correctly compile the project, programs `llvm-link` and `llvm-as` must be located in one of directories specified by `PATH` environmental variable.

### Turning off optimizations

By default, the compiler runs using all optimizations. To turn them off, build the compiler with `make no-opt` command.

## Special cases

- int type corresponds to `i32` type in LLVM
- memory allocated for string input and concatenation does not get freed
- number literals can be in range from `-2147483647` to `2147483647`


## Update 23.01.2023

### Bugfixes

The diff in code that shows what has been fixed can be seen in `bugfix.diff`.

The following bugs have been removed:

#### Optimization of `if(true)`, `if(false)`, `while(true)` and `while(false)`

A missing return statement caused incorrect code generation in these cases - the block that should be generated,
was generated, but after that the whole statement was generated anyway.

#### Nested SSA blocks

SSA code was wrongly generated for some cases where the SSA blocks were "nested", e.g. when a lazy boolean expression
was used in a loop's condition or when there were nested if statements. For example, such expression:
```
if (a) {
  if (b) {
    printInt(2);
  }
}
```

generated a block structure, where after the inner `if` block, there was a block that wasn't jumping anywhere.

### New features

New features added:

- structs
- objects
- virtual functions
