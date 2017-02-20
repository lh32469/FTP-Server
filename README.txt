Simple FTP Server initially created to handle images sent from Cisco WVC210 Wireless-G PTZ Internet Camera.  

By default, listens on port 8005 and uploads to directory '/var/tmp/ftp' and does not do any authentication so any login/password works.

To build, use Maven Assembly plugin:   

  $ mvn clean assembly:assembly

To run, use resulting assembly file 'ftp-server.jar' created in target directory:

  $ java -jar ftp-server.jar

To override default port and Directory, set properties:

  $ java -DPORT=7777 -DFTP_DIR=/tmp -jar ftp-server.jar
