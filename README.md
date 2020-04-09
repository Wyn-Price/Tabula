# Tabula
Originally made by [iChun](https://github.com/iChun/), tabula is a model making software.

### iChunUtils
Note for cube grow (formally mcScale) to work, you need to have [my custom build](https://github.com/Wyn-Price/iChunUtil) of iChunUtil to be installed

## Features
 + Cube Grow is enabled. Thanks goes to [gegy100](https://github.com/gegy1000)
 + Changing the parent hiarechy won't move cubes around (diabled by holding down ALT)
 + Added vertex snapping. Clicking the vertex snapping button (<img src="https://i.imgur.com/Ji8qoSf.png" width="20"/>) turns it on. From there, select a vertex, select a different cube then select a new vertex. The second cube will now move so that both cubes selected vertices are touching.
 + Added support for opening files outside of the `mods/tabula/saves` directory. First, copy the file you want to open, then click the `Open Project` button (like normal) and press Ctrl + V. This also works for textures.
 + Ctrl clicking in the open projects window will allow you to open multiple projects.
 + Open Projects list is now sorted by name, size and last modified.
 + You can now drag around cubes on the texturemap.
 + Fixed lighting bug where untextured models would just be a big white blob
