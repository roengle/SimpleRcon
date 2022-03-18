# SimpleRcon
Simple program that implements [Valve's Source RCON protocol](https://developer.valvesoftware.com/wiki/Source_RCON_Protocol).
Provides a way to fetch responses for commands, as well as read incoming packets sent out by the RCON server,
(for the game Squad, this is indicated by packet type `1` and requstId `0`.)

Continuously reads the connect socket's input stream to monitor for any new data. This is done to receive any data
that is broadcasted by the RCON server.

Uses some of Java's asynchronous libraries for executing (and waiting for responses from) commands issued.

## Not Implemented
* Auto-reconnect due to timeout from RCON server.

## Using SimpleRcon
With the `SimpleRcon.jar` file included in your project's dependencies, you can use SimpleRcon the following way:
```java
class TestClass{
    public static void main(String[] args){
        //Create Rcon object
        //Threads running in Rcon object will take care of receiving packets
        Rcon rcon = new Rcon("HOST_IP", 21114, "PASSWORD");
        //You can retrieve a response from issuing a command
        String response = rcon.command("ListPlayers");
        System.out.println(response);
        //You can also add consumers to run code when a packet is recevied
        rcon.onRconPacket(rconPacket -> {
            if(rconPacket.getType() == Rcon.SERVERDATA_BROADCAST){
                System.out.println("Received broadcasted packet: " + rconPacket);
            }
        });
    }
}

```

## Credits
* [Kronos6/rkon-core](https://github.com/Kronos666/rkon-core/tree/1.1.2) - Provided methods for reading/writing to streams from connected socket.
* [Source RCON Protocol Wiki](https://developer.valvesoftware.com/wiki/Source_RCON_Protocol) - Documentation for RCON protocol.