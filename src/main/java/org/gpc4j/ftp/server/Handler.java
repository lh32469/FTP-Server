package org.gpc4j.ftp.server;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Class for handling FTP client.
 *
 * @author Lyle T. Harris
 */
public class Handler implements Runnable {

    /**
     * Reader for reading client commands.
     */
    private final BufferedReader reader;

    /**
     * Writer for writing responses to client.
     */
    private final PrintWriter writer;

    /**
     * Map of simple received commands and their responses.
     */
    private final Map<String, String> commandResponses;

    /**
     * The IP address of the client. Set via the PORT command.
     */
    private String clientIP;

    /**
     * The receiving dataport of the client. Set via the PORT command.
     */
    private int dataPort;

    /**
     * FTP User login.
     */
    private String user = "";

    /**
     * FTP User password.
     */
    private String pass = "";

    private long size;

    private ServerSocket uploadSocket;

    private final Socket socket;

    private static final Logger LOG = Logger.getLogger(Handler.class.getName());


    /**
     * Create Handler associate with the socket provided.
     *
     * @param socket Socket to listen for commands on and provide responses.
     *
     * @throws IOException
     */
    public Handler(Socket socket) throws IOException {

        this.socket = socket;

        InputStream iStream = socket.getInputStream();
        InputStreamReader iStreamReader = new InputStreamReader(iStream);
        reader = new BufferedReader(iStreamReader);

        OutputStream oStream = socket.getOutputStream();
        OutputStreamWriter oStreamWriter = new OutputStreamWriter(oStream);
        writer = new PrintWriter(oStreamWriter, true);

        commandResponses = new HashMap<>();
        commandResponses.put("USER", "331 Password required for User.");
        commandResponses.put("PASS", "230 User logged in");

    }


    @Override
    public void run() {

        final String ready = "220 Ftp Server Ready";

        Path ftpDir
                = Paths.get(System.getProperty("FTP_DIR", "/var/tmp/ftp"));

        try {

            writer.println(ready);
            LOG.info(ready);

            String line;
            String response;

            while ((line = reader.readLine()) != null) {

                Scanner scan = new Scanner(line);

                String command = scan.next();
                LOG.info("line = " + line);

                if ("PORT".equals(command)) {

                    String array[] = line.split(" ");
                    array = array[1].split(",");

                    clientIP = array[0] + "." + array[1] + "."
                            + array[2] + "." + array[3];

                    int high = Integer.parseInt(array[4]);
                    int low = Integer.parseInt(array[5]);
                    dataPort = (high << 8) | low;
                    writer.println("200 Port command successful");

                } else if ("USER".equals(command)) {

                    String array[] = line.split(" ");
                    user = array[1].trim();
                    LOG.info("User = " + user);

                    writer.println(commandResponses.get(command));

                } else if ("SIZE".equals(command)) {

                    LOG.info("Size = " + size);
                    writer.println("213 " + size);

                } else if ("TYPE".equals(command)) {

                    if (scan.next().equals("I")) {
                        writer.println("200 Switching to Binary mode.");
                    }

                } else if ("EPSV".equals(command)) {

                    uploadSocket = new ServerSocket(0);

                    LOG.info("Opening port: " + uploadSocket.getLocalPort()
                            + " for uploading");

                    writer.println("229 Entering Extended Passive Mode"
                            + " (|||" + uploadSocket.getLocalPort() + "|)");

                } else if ("STOR".equals(command)) {

                    String file = scan.next();

                    byte[] buff = new byte[8192];
                    Socket client = null;

                    if (uploadSocket == null) {
                        // Via PORT Connection
                        writer.println("150 Opening data connection");
                        client = new Socket(clientIP, dataPort);
                    } else {
                        // Via EPSV Connection
                        client = uploadSocket.accept();
                    }

                    InputStream iStream
                            = client.getInputStream();

                    FileOutputStream oStream
                            = new FileOutputStream(
                                    ftpDir.resolve(file).toString());

                    writer.println("150 Ok to send data.");
                    int count = iStream.read(buff);

                    while (count > 0) {
                        oStream.write(buff, 0, count);
                        count = iStream.read(buff);
                    }
                    oStream.close();

                    if (uploadSocket != null) {
                        uploadSocket.close();
                    }

                    client.close();

                    LOG.info("Stored " + file);
                    writer.println("226 Transfer complete.");

                } else if ("RETR".equals(command)) {

                    writer.println("150 Opening data connection");
                    Socket client = new Socket(clientIP, dataPort);
                    OutputStream oStream = client.getOutputStream();

                    oStream.close();
                    writer.println("226 Transfer complete");

                } else if ("QUIT".equals(command)) {
                    writer.println("221 Goodbye.");
                    socket.close();
                    break;
                } else if ((response = commandResponses.get(command)) != null) {
                    writer.println(response);
                } else {
                    writer.println(ready);
                }

            }

        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }

        LOG.info(this + " ended.");
    }


}
