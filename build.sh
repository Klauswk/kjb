$JAVA_HOME/bin/javac -Xlint:deprecation -cp "libs/*" --add-exports jdk.jdi/com.sun.tools.example.debug.expr=ALL-UNNAMED -d target/ ./src/*.java
