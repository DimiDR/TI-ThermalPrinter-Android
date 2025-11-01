git add .
git commit -m"changes"
git push -u origin changes1

git log --since=20/11/2025 --numstat --pretty="%H" | awk ' NF==3 {plus+=$1; minus+=$2;} END {printf("+%d, -%d\n", plus, minus)}'