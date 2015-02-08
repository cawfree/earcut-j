earcut-j
======

A pure Java port of Vladimir Agafonkin's excellent earcut, the fastest and smallest Javascript polygon triangulation library. This will also work quite nicely on Android.

The latest version is based on earcut 1.2.2. This current distribution is funtionally equivalent as the original project, but has some minor Java-specific features to aid runtime performance and encapsulation. Users are expected to implement an interface 'IEarcutListener', which is passed triangulation data per individual triangle whilst the earcut triangulation procedure is running. 

The original project can be found here:
https://github.com/mapbox/earcut
