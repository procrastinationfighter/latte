compile: latc_llvm
	cd src && mvn compile assembly:single

latc_llvm:
	echo "#!/bin/bash" > latc_llvm
	echo "java -jar src/target/latte-compiler-1.0-SNAPSHOT-jar-with-dependencies.jar \$$1" >> latc_llvm
	chmod 744 latc_llvm


clean: clear_antlr clear_mvn clear_llvm

clear_antlr:
	rm -rf src/src/main/java/*

clear_mvn:
	cd src && mvn clean

clear_llvm:
	rm -f latc_llvm
