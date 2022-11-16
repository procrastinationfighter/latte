compile: generate-antlr latc_llvm
	cd src && mvn compile assembly:single

generate-antlr: antlr-4.11.1-complete.jar antlr-dir
	export CLASSPATH=$$PWD/antlr-4.11.1-complete.jar:$$CLASSPATH
	export PATH=/home/students/inf/PUBLIC/MRJP/bin:$$PATH
	cp Latte.cf antlr-dir/Latte.cf
	cd antlr-dir && \
		bnfc --java --antlr -m Latte.cf && \
		make absyn latte/latteLexer.java latte/latteParser.java&& \
		cd latte && \
		rm -f *.g4 *.class Absyn/*.class *.tokens *.interp
	mkdir -p src/src/main/java
	cp -r antlr-dir/latte src/src/main/java

antlr-dir:
	mkdir antlr-dir

antlr-4.11.1-complete.jar:
	wget https://www.antlr.org/download/antlr-4.11.1-complete.jar

latc_llvm:
	echo "#!/bin/bash" > latc_llvm
	echo "java -jar src/target/latte-compiler-1.0-SNAPSHOT-jar-with-dependencies.jar \$$1" >> latc_llvm
	chmod 744 latc_llvm


clean: clear_antlr clear_mvn clear_llvm clear_java

clear_antlr:
	rm -rf antlr-dir

clear_mvn:
	cd src && mvn clean

clear_llvm:
	rm -f latc_llvm

clear_java:
	rm -rf src/src/main/java/*
