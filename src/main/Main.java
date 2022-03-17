package main;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args){
        Rcon rcon = new Rcon("HOST_IP", 21114, "HOST_PASSWORD");
        //Test use of consumer pattern
        rcon.onRconPacket(rconPacket -> {
            if(rconPacket.getType() == Rcon.SERVERDATA_BROADCAST){
                System.out.println("Received broadcasted packet: " + rconPacket);
            }
        });

        //Test use of executing commands.
        Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
            String response = rcon.command("ListPlayers");
            System.out.println("response: " + response);
        }, 2, 10, TimeUnit.SECONDS);
    }
}
