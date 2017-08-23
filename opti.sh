#!/bin/bash
# Optimize images (this process is threaded as well [-P8]).
# To run: sh opti.sh

cd ..
for d in ./*/ ; do (cd "$d" && find -name '*.png' -print0 | xargs -P8 -L1 -0 optipng -nc -nb -o7); done
