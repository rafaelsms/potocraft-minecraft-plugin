# PotoCraft Plugin

A plugin used alongside others for the PotoCraft Minecraft server.
The server (2020-2022) got up to 40-50 players simultaneously.

This plugin contains a few systems, mainly:
* Block protection
* PVP and PVE (player versus player and entity combat) punishment if logging out of the server to escape a fight

Compared to previous plugins, those systems were simplified or replaced to minimize maintaince, due to it being a side project.

## Block protection

On original Minecraft, blocks can be placed and broken by other players without restrictions.
This system prevents players from breaking each others' blocks unless they're trusted.
It also allows players from exploding other player's protected blocks using explosions such as by TNT.

* Players can trust other players, allowing them to break existing blocks and place new ones next to theirs
* Requires sub-millisecond response time for the database queries to avoid blocking the game tick and slowing down the server
* Some calls can be done asynchronously without much gameplay annoyances with a bit of refactor, but that wasn't needed for the amount of players of the server
* [HikariCP](https://github.com/brettwooldridge/HikariCP) is used in a executor pool (required to avoid pool exhaustion and deadlock in HikariCP)

## PVP/PVE escaping punishment

Players are encouraged to not escape a fight, even if it is against AI enemies or some kinds of self inflicted damage, automatically dying and losing their items if they do.
