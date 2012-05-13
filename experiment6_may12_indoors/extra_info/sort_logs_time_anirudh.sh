#! /bin/bash
for d in ./logs_sorted/*
do
  echo "Folder is $d"
  for f in $d/*.txt 
  do
      echo "File is $f"
      sort -s -k1,13 $f > $f.sorted
      sed '/^$/d' $f.sorted > $f.blanksRm
      mv $f.blanksRm $f
      rm $f.sorted
  done 
  
done
