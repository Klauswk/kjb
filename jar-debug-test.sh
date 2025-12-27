set -xe
./build.sh
$JAVA_HOME/bin/javac -Xlint:deprecation -d "test/target/" test/*.java  
$JAVA_HOME/bin/jar -cvfe test/Test.jar example.packag.simple.SimpleClass -C "test/target/" example 
$JAVA_HOME/bin/java -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000 -cp "libs/*:target" com.github.klauswk.tty.Jdb -launch test/Test.jar
