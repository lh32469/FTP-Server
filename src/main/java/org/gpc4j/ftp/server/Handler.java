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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
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
   * Where to store files on local file system.
   */
  final Path ftpDir;

  /**
   * FTP User login and top-level dir under ftpDir to store files in.
   */
  private String user = "";

  /**
   * FTP User password.
   */
  private String pass = "";

  /**
   * The current directory based on client CWD commands.
   */
  private String currentDirectory = "/";

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

    ftpDir = Paths.get(System.getProperty("FTP_DIR", "/var/tmp/ftp"));
  }

  @Override
  public void run() {

    final String ready = "220 Ftp Server Ready";

    try {

      writer.println(ready);
      LOG.info(ready);

      String line;
      String response;

      main:
      while ((line = reader.readLine()) != null) {

        Scanner scan = new Scanner(line);

        String command = scan.next();
        LOG.info("line = " + line);

        switch (command) {

          case "PWD":
            writer.println("257 \"" + currentDirectory
                + "\" is the current directory");
            break;

          case "CWD":
            String dir = scan.next().trim();
            currentDirectory = dir;
            LOG.info("CurrentDir: " + currentDirectory);
            writer.println("250 Directory successfully changed.");
            break;

          case "TYPE":
            String type = scan.next().trim();
            LOG.config("Type = [" + type + "]");
            if (type.equals("I")) {
              writer.println("200 Switching to Binary mode.");
            }
            break;

          case "PORT":
            String array[] = line.split(" ");
            array = array[1].split(",");

            clientIP = array[0] + "." + array[1] + "."
                + array[2] + "." + array[3];

            int high = Integer.parseInt(array[4]);
            int low = Integer.parseInt(array[5]);
            dataPort = (high << 8) | low;
            writer.println("200 Port command successful");
            break;

          case "USER":
            user = line.split(" ")[1].trim();
            LOG.info("User = " + user);
            writer.println(commandResponses.get(command));
            break;

          case "SIZE":
            LOG.info("Size = " + size);
            writer.println("213 " + size);
            break;

          case "STOR":
            handleStoreCommand(scan);
            break;

          case "EPSV":
            uploadSocket = new ServerSocket(0);
            LOG.info("Opening port: " + uploadSocket.getLocalPort()
                + " for uploading");
            writer.println("229 Entering Extended Passive Mode"
                + " (|||" + uploadSocket.getLocalPort() + "|)");
            break;

          case "RETR":
            writer.println("150 Opening data connection");
            Socket client = new Socket(clientIP, dataPort);
            OutputStream oStream = client.getOutputStream();
            oStream.close();
            writer.println("226 Transfer complete");
            break;

          case "QUIT":
            writer.println("221 Goodbye.");
            socket.close();
            // Break out of main labeled loop
            break main;

          default:
            if ((response = commandResponses.get(command)) != null) {
              writer.println(response);
            } else {
              writer.println(ready);
            }

        }
      }

    } catch (IOException ex) {
      LOG.log(Level.SEVERE, null, ex);
    }

    LOG.info(this + " ended.");
  }

  void handleStoreCommand(final Scanner scan) throws IOException {
    String file = scan.next();

    byte[] buff = new byte[8192];
    Socket client = null;

    if (uploadSocket == null) {
      // Via PORT Connection
      writer.println("150 Opening data connection");
      LOG.info("Connecting to ClientIP:Port: " + clientIP + ":" + dataPort);
      client = new Socket(clientIP, dataPort);
    } else {
      // Via EPSV Connection
      client = uploadSocket.accept();
    }

    InputStream iStream
        = client.getInputStream();

    LocalDate now = LocalDate.now(); // 2016-06-17 

    // Store files by date under ftpDir using user id as top-level dir.
    Path dir = ftpDir.resolve(user);

    dir = dir.resolve(now.toString().replaceAll("-", "/"));
    Files.createDirectories(dir);

    FileOutputStream oStream
        = new FileOutputStream(dir.resolve(file).toString());

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
  }

}
