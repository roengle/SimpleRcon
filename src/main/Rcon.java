package main;

import ex.AuthenticationException;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Class which interfaces with the RCON server, constructing and sending RCON packets as necessary.
 */
public class Rcon {
    private final String host;
    private final Integer port;
    private final byte[] password;

    private final Object sync = new Object();
    private final Random rand = new Random();

    private Socket socket;
    private boolean authenticated;

    private int requestId;

    private final Queue<RconPacket> commandResponsePackets = new LinkedList<>();

    private List<Consumer<RconPacket>> onPacketConsumers = new ArrayList<>();

    public static int SERVERDATA_RESPONSE_VALUE = 0;
    //This one isn't technically defined in Source RCON Protocol, but is used for some games such as Squad
    public static int SERVERDATA_BROADCAST = 1;
    public static int SERVERDATA_EXECCOMMAND = 2;
    public static int SERVERDATA_AUTH = 3;

    /**
     * Constructs a {@link Rcon} object with the given connection properties.
     *
     * @param host the host address of the RCON server. Can be a FQDN or IP address
     * @param port the port the RCON server monitors. By default is 21114
     * @param password a {@link String} of the password
     */
    public Rcon(String host, Integer port, String password){
        this(host, port, password.getBytes());
    }

    /**
     * Helper method that's used to construct a {@link Rcon} object with the given connection properties.
     *
     * @param host the host address of the RCON server. Can be a FQDN or IP address
     * @param port the port the RCON server monitors. By default is 21114
     * @param password a byte array representing the password to logon to the RCON server with.
     */
    private Rcon(String host, Integer port, byte[] password){
        this.host = host;
        this.port = port;
        this.password = password;
        new Thread(() -> {
            try{
                try{
                    connect(this.host, this.port, this.password);
                    authenticated = true;
                }catch (AuthenticationException ex){
                    authenticated = false;
                }

                onRconPacket(rconPacket -> {
                    if(rconPacket.getType() == SERVERDATA_RESPONSE_VALUE
                            && !rconPacket.getPayloadAsString().equals("")){
                        commandResponsePackets.add(rconPacket);
                    }
                });

                while(true){
                    while(socketHasData()){
                        RconPacket pak = read(socket.getInputStream());
                        for(Consumer<RconPacket> func : onPacketConsumers){
                            func.accept(pak);
                        }
                    }
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Function that takes in a {@link Consumer<RconPacket>} to consume every time a RCON packet is received. The consumer
     * takes in the supplied {@link RconPacket}
     *
     * @param func the {@link Consumer} to be consumed for each {@link RconPacket} retrieved.
     */
    public void onRconPacket(Consumer<RconPacket> func){
        onPacketConsumers.add(func);
    }

    /**
     * Sends a given command to the RCON server and returns the response if one is sent.
     *
     * May wait for response to be sent be finishing execution.
     *
     * If command returns output longer than the maximum RCON packet size, method stiches together outputs
     * that are sent through mulitple packets.
     *
     * @param command the command to output
     * @return the output of the command sent if the RCON server returns one
     */
    public String command(String command){
        final AtomicReference<String> response = new AtomicReference<>();
        response.set("");

        //New request ID
        requestId = rand.nextInt();
        //Make sure request ID isn't 0 or -1
        while(requestId == 0 || requestId == -1){
            requestId = rand.nextInt();
        }
        //Keep local requestId incase it's changed by another thread
        int thisRequestId = requestId;
        //Asynchronously execute command helper method
        CompletableFuture<Void> future = command(command.getBytes(StandardCharsets.UTF_8));
        //Wait until finished
        while(!future.isDone()){}
        //New list of items to remove from collected list of command responses.
        Collection<RconPacket> removals = new ArrayList<>();

        //Iterate through each command response packet received, and look for those in response to the current command
        commandResponsePackets.forEach(pak -> {
            if(pak.getRequestId() == thisRequestId){
                //If so, append the packet's payload to the output string
                response.set(response.get().concat(pak.getPayloadAsString()));
                //Mark this packet for removal from the list so it doesn't grow too large
                removals.add(pak);
            }
        });
        //Once done iterating, remove all the items marked for removal
        commandResponsePackets.removeAll(removals);
        return response.get();
    }

    /**
     * Private helper method for {@link Rcon#command(String)}.
     *
     * @param payload a byte array of the payload for the command
     * @return a {@link CompletableFuture<Void>} representing the state of execution for the command
     */
    private CompletableFuture<Void> command(byte[] payload){
        send(SERVERDATA_EXECCOMMAND, payload);
        return CompletableFuture.runAsync(this::waitUntilNoData);
    }

    /**
     * Method to reconnect socket and re-authenticate.
     *
     * @throws IOException
     */
    private void reconnect() throws IOException {
        System.out.println("Reconnecting to RCON server.");
        try{
            connect(this.host, this.port, this.password);
            authenticated = true;
        }catch (AuthenticationException ex){
            authenticated = false;
        }
    }

    /**
     * Disconnects the working socket.
     *
     * @throws IOException
     */
    private void disconnect() throws IOException {
        socket.close();
    }

    /**
     * Tests if the socket has data to retrieve from its input stream.
     *
     * @return true if there is data to be retrieved, false if not
     */
    private boolean socketHasData(){
        boolean status = false;
        try {
            status = socket.getInputStream().available() > 0;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return status;
    }

    /**
     * Method that waits until there is no more data to receive from the socket.
     *
     * Should only be used asynchronously as this will hold up execution of a synchronously-running program.
     */
    private void waitUntilNoData(){
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        while(socketHasData()){}
        return;
    }

    /**
     * Connects a socket to an RCON server, then attempts to authenticate with the given password.
     * @param host the host to connect to
     * @param port the port the RCON server is listening on
     * @param password a byte array of the password to authenticate with
     * @throws AuthenticationException when the password is incorrect
     */
    private void connect(String host, Integer port, byte[] password) throws AuthenticationException {
        synchronized (sync){
            try {
                //New request id
                requestId = rand.nextInt();
                while(requestId == -1 || requestId == 0){
                    requestId = rand.nextInt();
                }

                //Can't reuse socket, so make a new one
                socket = new Socket(host, port);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        send(SERVERDATA_AUTH, password);
        //TODO: Run read() twice to check response packets to see if authentication is successful and throw AuthenticationException if not
    }

    /**
     * Sends data to the RCON server, given a packet type and payload
     *
     * @param type the type of packet as defined by the Source RCON Protocol
     * @param payload a byte array of the payload to send to the RCON server
     */
    private void send(int type, byte[] payload){
        synchronized (sync){
            try {
                write(socket.getOutputStream(), requestId, type, payload);
            }
            catch(SocketException se) {
                // Close the socket if something happens
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Writes data to the socket for this Rcon's {@link OutputStream} and flushes it.
     *
     * @param out the {@link OutputStream} to write to
     * @param requestId the id of the request to sent
     * @param type the type of packet
     * @param payload the payload being sent
     * @throws IOException
     */
    private void write(OutputStream out, int requestId, int type, byte[] payload) throws IOException {
        int bodyLength = getBodyLength(payload.length);
        int packetLength = getPacketLength(bodyLength);

        ByteBuffer buffer = ByteBuffer.allocate(packetLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.putInt(bodyLength);
        buffer.putInt(requestId);
        buffer.putInt(type);
        buffer.put(payload);

        // Null bytes terminators
        buffer.put((byte)0);
        buffer.put((byte)0);

        // Bye bye!
        out.write(buffer.array());
        out.flush();
    }

    /**
     * Reads data from this socket's {@link InputStream} and returns individual {@link RconPacket}s
     *
     * @param in the socket's {@link InputStream} to read from
     * @return the {@link RconPacket} read
     * @throws IOException
     */
    private RconPacket read(InputStream in) throws IOException {
        // Header is 3 4-bytes ints
        byte[] header = new byte[4 * 3];

        // Read the 3 ints
        if(in.read(header) == -1){
            //Need to reconnect
            reconnect();
            in.read(header);
        }

        try {
            synchronized (sync){
                // Use a bytebuffer in little endian to read the first 3 ints
                ByteBuffer buffer = ByteBuffer.wrap(header);
                buffer.order(ByteOrder.LITTLE_ENDIAN);

                int length = buffer.getInt();
                int requestId = buffer.getInt();
                int type = buffer.getInt();

                // Payload size can be computed now that we have its length
                byte[] payload = new byte[length - 4 - 4 - 2];

                DataInputStream dis = new DataInputStream(in);

                // Read the full payload
                dis.readFully(payload);

                // Read the null bytes
                dis.read(new byte[2]);

                return new RconPacket(new Date(), length, requestId, type, payload);
            }
        }
        catch(BufferUnderflowException | EOFException e) {}
        return null;
    }

    /* Helper methods */
    public static int getPacketLength(int bodyLength) {
        // 4 bytes for length + x bytes for body length
        return 4 + bodyLength;
    }

    public static int getBodyLength(int payloadLength) {
        // 4 bytes for requestId, 4 bytes for type, x bytes for payload, 2 bytes for two null bytes
        return 4 + 4 + payloadLength + 2;
    }
    
}
