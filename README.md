# AndroidPlayer

----------
TODO
----------

 - From from https://github.com/google/ExoPlayer origin/release-v2
 - Modify comments


----------
Info
----------

Use ExoPlayer to do your tests
If you need you can modify JavaWebSocket or pull changes from the original repo

**ExoPlayer + Java-WebSocket + NanoHttpd dependencies**
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
```
# Download NanoHttpd
git remote add -f nanohttpd https://github.com/NanoHttpd/nanohttpd.git
git merge -s ours --no-commit nanohttpd/master
git read-tree --prefix=nanohttpd/ -u nanohttpd/master
git commit -m "Merge nanohttpd/master as our nanohttpd subdirectory"
# Update Java-WebSocket master
git pull -s subtree nanohttpd master
# Make 
cd nanohttpd
gradle init
gradle build
```

----------

Create apk
----------

 - Open Android Visual Studio
 - Open the ExoPlayer project and build the demo

----------

Installing/reinstalling
----------

 - connect the device in adb mode ( adb connect IP:5555 )
 - adb uninstall com.google.android.exoplayer2.demo
 - adb install ./ExoPlayer/demos/exoPlayback.2.4_Exo_2.10.3.apk
 
----------

Running from adb
----------

 - adb shell am kill com.google.android.exoplayer2.demo
 - adb shell am start -n com.google.android.exoplayer2.demo/com.google.android.exoplayer2.demo.SampleChooserActivity 
 

    
