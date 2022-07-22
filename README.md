FFXIV_DataExtractor
===================


This is a fork of [FFXIV Explorer Fork by goaaats](https://github.com/goaaats/ffxiv-explorer-fork) which is a fork of [FFXIV Explorer by Ioncannon](https://bitbucket.org/Ioncannon/ffxiv-explorer/overview) with some usability changes and new features.
PoC ULD reader courtesy of [RozeDoyanawa](https://github.com/RozeDoyanawa).

My (emarron) fork,

* Updates the paths for endwalker.
* Changes TEX export from PNG to TGA, to better support images with transparency.
* Changes the path search to only show *.tex files.

Basically, this lets you grab all the texture files in the game and exports them as TGA files.




## To use
Use IntelliJ or another Java IDE with Maven support to import a Maven project.
Select the pom.xml file. If using IntelliJ, the build/run configurations should
automatically appear. In other IDEs, check the .idea/runConfigurations folder
for the command-line args for the build/run configs, or, if you know Maven, you
might just know the commands off the top of your head.
