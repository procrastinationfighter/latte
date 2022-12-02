#!/bin/bash
program=$1
dir=$2
for f in $dir/*.lat
do
	echo "RUNNING TEST" ${f%.lat}
	./${program} $f >/dev/null 2>/dev/null
	testresult=$?
	echo "TEST" ${f%.in} "ENDED WITH RESULT:" ${testresult}
done
