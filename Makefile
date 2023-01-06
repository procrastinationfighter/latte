compile: antlr-dir latc_llvm lib/runtime.bc
	cd src && mvn compile assembly:single

antlr-dir: antlr-4.11.1-complete.jar
	mkdir antlr-dir
	export OLDCLASSPATH=$$CLASSPATH && \
	export CLASSPATH=$${PWD}/antlr-4.11.1-complete.jar && \
	echo $$CLASSPATH && \
	export PATH=/home/students/inf/PUBLIC/MRJP/bin:$$PATH && \
	cp Latte.cf antlr-dir/Latte.cf && \
	cd antlr-dir && \
		bnfc --java --antlr -m Latte.cf && \
		sed -i 's/PARSER_FLAGS=.*/& -visitor -no-listener/g' Makefile && \
		make absyn latte/latteLexer.java latte/latteParser.java && \
		cd latte/Absyn && \
		rm *.class
	mkdir -p src/src/main/java
	cp -r antlr-dir/latte src/src/main/java


antlr-4.11.1-complete.jar:
	wget https://www.antlr.org/download/antlr-4.11.1-complete.jar

latc_llvm:
	echo "#!/bin/bash" > latc_llvm
	echo "java -jar src/target/latte-compiler-1.0-SNAPSHOT-jar-with-dependencies.jar \$$1 ./lib/runtime.bc" >> latc_llvm
	chmod 744 latc_llvm

lib/runtime.bc:
	llvm-as -o lib/runtime.bc lib/runtime.ll
	llc  --relocation-model=pic -o lib/runtime.s lib/runtime.bc

clean: clear_antlr clear_mvn clear_llvm clear_java

clear_runtime:
	rm lib/runtime.bc

clear_antlr:
	rm -rf antlr-dir

clear_mvn:
	cd src && mvn clean

clear_llvm:
	rm -f latc_llvm

clear_java:
	rm -rf src/src/main/java/*
