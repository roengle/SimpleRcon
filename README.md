# SimpleRcon
Simple program that implements [Valve's Source RCON protocol](https://developer.valvesoftware.com/wiki/Source_RCON_Protocol).
Provides a way to fetch responses for commands, as well as read incoming packets sent out by the RCON server,
(usually indicated with a **requestId** of `0` and **type** `1`.)

Continuously reads the connect socket's input stream to monitor for any new data. This is done to receive any data
that is broadcasted by the RCON server.

Uses some of Java's asynchronous libraries for executing (and waiting for responses from) commands issued.

## Not Implemented
* Auto-reconnect due to timeout from RCON server.

## Credits
* [Kronos6/rkon-core](https://github.com/Kronos666/rkon-core/tree/1.1.2) - Provided methods for reading/writing to streams from connected socket.
* [Source RCON Protocol Wiki](https://developer.valvesoftware.com/wiki/Source_RCON_Protocol) - Documentation for RCON protocol.