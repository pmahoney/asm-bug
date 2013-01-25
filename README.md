Example case demonstrating possible bug in LocalVariablesSorter
==============================================================

Building
--------

    $ git clone https://github.com/pmahoney/asm-bug.git
    $ cd asm-bug
    $ mvn clean package
    
Running
-------

All tests were run ising OpenJDK 7

    $ java -version
    java version "1.7.0_09"
    OpenJDK Runtime Environment (IcedTea7 2.3.3) (7u9-2.3.3-0ubuntu1~12.10.1)
    OpenJDK 64-Bit Server VM (build 23.2-b09, mixed mode)

This will start the program which loads `Example.class` through an ASM
pipeline.  `TraceClassVisitor` is used to print the "before" and
"after" versions of the class.  The class is then loaded into the
program (which works OK).

    $ java -jar target/asm-bug-1.0-SNAPSHOT.jar
    ... trace output trimmed ...
    ----- AFTER -----
      public static f1(I)I
    ...
       FRAME FULL [I] [java/lang/Exception]

The modified `Example.class` is written out.  The following will execute
that modified class:

    $ java Example
    Exception in thread "main" java.lang.VerifyError: Bad local variable type in method Example.f1(I)I at offset 17
      ...

Now enable a hack that forces the `changed` state of the
`LocalVariablesSorter` to `true`.

    $ java -jar target/asm-bug-1.0-SNAPSHOT.jar --apply-hack
    ... trace output trimmed ...
    ----- AFTER -----
      public static f1(I)I
    ...
       FRAME FULL [I I] [java/lang/Exception]

Finally, execute the modified class with hack applied:

    $ java Example
    16
    it worked: 123
    16

Is this a bug or am I using `LocalVariablesSorter` incorrectly?
