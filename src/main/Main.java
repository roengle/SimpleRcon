package main;

import core.Rcon;
import core.ex.AuthenticationException;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args){
        Rcon rcon = null;
        try {
            rcon = new Rcon("45.35.55.30", 21114, "P@$$W0rD");
        } catch (AuthenticationException e) {
            e.printStackTrace();
            System.exit(1);
        }
        //Test use of consumer pattern
        rcon.onRconPacket(rconPacket -> {
            if(rconPacket.getType() == Rcon.SERVERDATA_BROADCAST){
                System.out.println("Received broadcasted packet: " + rconPacket);
            }
        });

        //Test use of executing commands.
        Rcon finalRcon = rcon;
        Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
            String response = finalRcon.command("ListPlayers");
            System.out.println("response: " + response);
        }, 2, 10, TimeUnit.SECONDS);
    }
}
