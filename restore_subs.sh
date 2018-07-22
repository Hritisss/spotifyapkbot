#!/bin/bash

filename="$1"
while read -r line
do
        id=$(echo $line | awk -F'|' '{print $1}')
        chat_id=$(echo $line | awk -F'|' '{print $2}')
        echo "RUNNING:  INSERT INTO Subscribers (chat_id, release, beta) VALUES ($chat_id, true, false);"
        sqlite3 spotifyapk.db "INSERT INTO Subscribers (chat_id, release, beta) VALUES ($chat_id, 1, 0)"
done < "$filename"

sqlite3 spotifyapk.db "SELECT * FROM Subscribers"