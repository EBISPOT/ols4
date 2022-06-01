#!/bin/bash

for f in ./output_csv/*_ontologies.csv
do
	echo -n "--nodes=$f "
done

for f in ./output_csv/*_classes.csv
do
	echo -n "--nodes=$f "
done

for f in ./output_csv/*_properties.csv
do
	echo -n "--nodes=$f "
done

for f in ./output_csv/*_individuals.csv
do
	echo -n "--nodes=$f "
done

for f in ./output_csv/*_edges.csv
do
	echo -n "--relationships=$f "
done


