#! /bin/bash
for file in *
do
        if [ -d "$file" ]; then
                cd "$file";
                     python ~/Desktop/experiment_april_6_2/process_extract_last_count.py
                cd ..;
                continue
        fi
done
