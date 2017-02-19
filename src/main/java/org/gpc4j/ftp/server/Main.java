package org.gpc4j.ftp.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;


/**
 *
 * @author Lyle T Harris
 */
public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());


    public static void main(String[] args) throws IOException {
        
        String portNumber = System.getProperty("PORT", "8005");
        
          Path ftpDir
                = Paths.get(System.getProperty("FTP_DIR", "/var/tmp/ftp"));

        
        int port = Integer.parseInt(portNumber);

        LOG.info("FTP Server started at port: " + port+ 
                ", using direcory: " + ftpDir);

        ServerSocket server = new ServerSocket(port);

        while (true) {
            Socket s = server.accept();
            Handler h = new Handler(s);
            Thread t = new Thread(h);
            t.start();
        }
    }


}
