# AndroidPlayer

Use ExoPlayer to do your tests
If you need you can modify JavaWebSocket or pull changes from the original repo

**ExoPlayer and Java-WebSocket dependencies**
```
# Download ExoPlayer v2
git remote add -f ExoPlayer https://github.com/google/ExoPlayer.git
git merge -s ours --no-commit ExoPlayer/release-v2
git read-tree --prefix=ExoPlayer/ -u ExoPlayer/release-v2
git commit -m "Merge ExoPlayer/release-v2 as our ExoPlayer subdirectory"
# Update ExoPlayer v2
git pull -s subtree ExoPlayer release-v2
```
```
# Download Java-WebSocket master
git remote add -f JavaWebSocket https://github.com/TooTallNate/Java-WebSocket.git
git merge -s ours --no-commit JavaWebSocket/master
git read-tree --prefix=JavaWebSocket/ -u JavaWebSocket/master
git commit -m "Merge JavaWebSocket/master as our JavaWebSocket subdirectory"
# Update Java-WebSocket master
git pull -s subtree JavaWebSocket master
```

