# Migration
Here i'll add the steps taken for completely migrating to jetpack compose. These are raw steps noted
down once the project is completely and successfully migrated it will be translated to a blog to help
others in their migration journey.

Happy Composing 💚💚💚.
> Note: for gradle migration you can check out this [commit](https://github.com/uditkarode/AbleMusicPlayer/commit/d38d88479fc15092afd5c05ad6cd0034b5b12598)

# Goal
The goal is to improve code and migrate to compose (multiplatform eventually).

# Roadmap
As of the current state of the app can stream local media and can search and stream single media from 
youtube music and this app being `binding` and `xml` it make it pretty hard to separate ui and domain

App contains mix of activities and fragments with the basic functionality working the first screen to 
be migrated would be `Now Playing` screen it wont be final code or layout but would be a first step 
to migrate ui and domain to compose.

Current version is `InterdimensionalBoop` and we don't intend to change it for stable version until
we migrated and stabilised the whole till then we will be it will added later.

# Features Planned 
> **These features are planned as of writing this doc so there might be more or less of them later on**
- **Modern Responsive UI**
- **Support Legacy Android Versions?**
- **Multiplatform**
- **Stream YT Music**
- **Download & Play Playlist (yt or spotify)**

# Lines Of Code
let's see how low can we drop xml usage
>as of: 12/10/2025
```markdown
-------------------------------------------------------------------------------
Language                     files          blank        comment           code
-------------------------------------------------------------------------------
Kotlin                          59           1073           1782           6395
XML                            112            394             20           5730
JSON                            13              0              0           2412
Java                            12            517           1715           1247
Text                             9             84              0            399
Gradle                           8             35              7            239
CMake                            5             60             30            178
Bourne Shell                     3             21             22            177
ProGuard                         2             12             19            142
YAML                             3              3              0            114
TOML                             1              5             13             94
DOS Batch                        1             23              2             59
Markdown                         2             12              0             45
Properties                       7              0             25             21
C++                              1              1              5              1
-------------------------------------------------------------------------------
SUM:                           238           2240           3640          17253
-------------------------------------------------------------------------------
```

### old
```
- Separating data and domain layers from ui layer to start migrating ui to compose (migrating to multi-module project structure)
- create and use core - `model` module
- create service and utils module
- add services to service module
- add utils module
- rolling back changes due dependence of utils, services, activites on each other
- something needs to be done
- unable to move utils due to use of string res
- thinking of plan....
```