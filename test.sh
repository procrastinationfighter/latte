#!/bin/bash
program=$1
dir=$2
expected=$3
for f in $dir/*.lat
do
	./${program} $f
	testresult=$?
	if [ -$testresult -ne -$expected ]
	then
	  echo "test $f failed"
	fi
done
