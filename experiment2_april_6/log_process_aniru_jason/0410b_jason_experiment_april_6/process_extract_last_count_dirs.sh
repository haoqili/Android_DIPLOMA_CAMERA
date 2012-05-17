#! /bin/bash
for file in *
do
        if [ -d "$file" ]; then
                cd "$file";
                for file2 in *
                do
                        if [ -d "$file2" ]; then
                                cd "$file2";
                                python ~/Desktop/process_extract_last_count.py
                                cd ..;
                                continue
                        fi
                done
                cd ..;
                continue
        fi
done
