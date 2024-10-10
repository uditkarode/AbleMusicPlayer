# Migration
Here i'll add the steps taken for completely migrating to jetpack compose. These are raw steps noted
down once the project is completely and successfully migrated it will be translated to a blog to help
others in their migration journey.

Happy Composing 💚💚💚.
> Note: for gradle migration you can check out this [commit](https://github.com/uditkarode/AbleMusicPlayer/commit/d38d88479fc15092afd5c05ad6cd0034b5b12598)

- Separating data and domain layers from ui layer to start migrating ui to compose (migrating to multi-module project structure)
- create and use core - `model` module
- create service and utils module
- add services to service module
- add utils module
- rolling back changes due dependence of utils, services, activites on each other
- something needs to be done
- unable to move utils due to use of string res
- thinking of plan....
