#! /bin/bash
for file in *
do
        if [ -d "$file" ]; then
                cd "$file";
                for file2 in *
                do
                        if [ -d "$file2" ]; then
                                cd "$file2";
                                python ~/Desktop/experiment_april_6_2/clean.py
                                cd ..;
                                continue
                        fi
                done
                cd ..;
                continue
        fi
done
