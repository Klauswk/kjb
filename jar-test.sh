set -xe
./build.sh
$JAVA_HOME/bin/javac -Xlint:deprecation -d "test/target/" test/*.java  
$JAVA_HOME/bin/jar -cvfe Test.jar example.packag.simple.SimpleClass -C "test/target/" example 
$JAVA_HOME/bin/java -cp "libs/*:target" com.github.klauswk.tty.TTY -launch Test.jar
