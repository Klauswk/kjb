set -xe
./build.sh
$JAVA_HOME/bin/javac -g -Xlint:deprecation -d "test/target/" test/src/example/packag/simple/*.java

$JAVA_HOME/bin/jar -cvfe test/Test.jar example.packag.simple.SimpleClass -C "test/target/" example 
$JAVA_HOME/bin/java -cp "libs/*:target" com.github.klauswk.tty.TTY -launch test/Test.jar
