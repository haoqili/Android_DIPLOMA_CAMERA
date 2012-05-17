#! /bin/bash
for file in *
do
        if [ -d "$file" ]; then
                cd "$file";
                for file2 in *
                do
                        if [ -d "$file2" ]; then
                                cd "$file2";
                                python ~/Desktop/rename.py
                                cd ..;
                                continue
                        fi
                done
                cd ..;
                continue
        fi
done
