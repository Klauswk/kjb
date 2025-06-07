$JAVA_HOME/bin/java -cp "target/:libs/*" --add-exports jdk.jdi/com.sun.tools.example.debug.expr=ALL-UNNAMED com.github.klauswk.tty.TTY -launch test/SimpleClass.java
