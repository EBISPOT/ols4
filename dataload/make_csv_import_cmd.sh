#!/bin/bash

shopt -s nullglob

for f in $1/*_ontologies.csv
do
	echo -n "--nodes=$f "
done

for f in $1/*_classes.csv
do
	echo -n "--nodes=$f "
done

for f in $1/*_properties.csv
do
	echo -n "--nodes=$f "
done

for f in $1/*_individuals.csv
do
	echo -n "--nodes=$f "
done

for f in $1/*_embedding_nodes.csv
do
	echo -n "--nodes=$f "
done

for f in $1/*_edges.csv
do
	[[ "$f" == *_embedding_edges.csv ]] && continue
	echo -n "--relationships=$f "
done

for f in $1/*_embedding_edges.csv
do
	echo -n "--relationships=$f "
done


