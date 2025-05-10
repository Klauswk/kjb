javac -d "test/target/" test/*.java  
jar -cvfe Test.jar example.packag.simple.SimpleClass -C "test/target/" example 
java -cp "libs/*:target" com.github.klauswk.tty.TTY -launch Test.jar
