#! /bin/bash
for file in *
do
        if [ -d "$file" ]; then
                cd "$file";
                python ~/Desktop/experiment_april_6_2/clean.py
                cd ..;
                continue
        fi
done
