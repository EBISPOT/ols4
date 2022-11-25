#!/bin/bash

for f in $(find $1 -type f -name *_ontologies.csv -print)
do
	echo -n "--nodes=$f "
done

for f in $(find $1 -type f -name *_classes.csv -print)
do
	echo -n "--nodes=$f "
done

for f in $(find $1 -type f -name *_properties.csv -print)
do
	echo -n "--nodes=$f "
done

for f in $(find $1 -type f -name *_individuals.csv -print)
do
	echo -n "--nodes=$f "
done

for f in $(find $1 -type f -name *_edges.csv -print)
do
	echo -n "--relationships=$f "
done


