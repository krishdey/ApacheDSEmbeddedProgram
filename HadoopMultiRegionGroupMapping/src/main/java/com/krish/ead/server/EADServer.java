package com.krish.ead.server;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.apache.directory.api.util.Network;
import org.apache.directory.server.core.api.InstanceLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EAD Server
 * @author krishdey
 *
 */
public class EADServer {
  /** A logger for this class */
  private static final Logger LOG = LoggerFactory.getLogger(EADServer.class);

  /** The key of the property use to specify the shutdown port */
  private static final String PROPERTY_SHUTDOWN_PORT = "ead.shutdown.port";

  /** The EAD service */
  private EmbeddedADSVerM23 service;

  private static final String EAD_STARTUP_PORT = "ead.server.port";

  private static int DEFAULT_STARTUP_PORT = 10389;

  private static String HADOOP_GROUP_MAPPING_XML = "hadoop-group-mapping";

  private static String hadoopGroupMappingPath;

  /**
   * Takes a single argument, the path to the installation home, which contains
   * the configuration to load with server startup settings.
   *
   * @param args the arguments
   */
  public static void main(String[] args) throws Exception {
    if ((args == null) || (args.length < 1)) {
      throw new IllegalArgumentException("Instance directory argument is missing");
    }

    String instanceDirectory = args[0];
    Action action = (args.length == 2) ? Action.fromString(args[1]) : Action.START;

    EADServer instance = new EADServer();
    int port =
        StringUtils.isEmpty(System.getProperty(EAD_STARTUP_PORT)) ? DEFAULT_STARTUP_PORT : Integer
            .parseInt(System.getProperty(EAD_STARTUP_PORT));

    hadoopGroupMappingPath = System.getProperty(HADOOP_GROUP_MAPPING_XML);
    if (!new File(hadoopGroupMappingPath).exists()) {
      throw new RuntimeException("hadoop group mapping xml is not found");
    }

    switch (action) {
    case START:
      // Starts the server
      LOG.debug("Starting runtime");
      instance.start(instanceDirectory, port);
      instance.startGroupMappingUpdater();

      break;

    case STOP:
      // Stops the server
      LOG.debug("Stopping runtime");
      InstanceLayout layout = new InstanceLayout(instanceDirectory);
      try (Socket socket = new Socket(Network.LOOPBACK, readShutdownPort(layout));
          PrintWriter writer = new PrintWriter(socket.getOutputStream())) {
        writer.print(readShutdownPassword(layout));
      }

      break;

    default:
      throw new IllegalArgumentException("Unexpected action " + action);
    }

    LOG.trace("Exiting main");
  }

  private int getShutdownPort() {
    int shutdownPort = Integer.parseInt(System.getProperty(PROPERTY_SHUTDOWN_PORT, "0"));
    if (shutdownPort < 0 || (shutdownPort > 0 && shutdownPort < 1024) || shutdownPort > 65536) {
      throw new IllegalArgumentException("Shutdown port [" + shutdownPort
          + "] is an illegal port number");
    }
    return shutdownPort;
  }

  private static int readShutdownPort(InstanceLayout layout) throws IOException {
    return Integer.parseInt(new String(Files.readAllBytes(Paths.get(layout.getRunDirectory()
        .getAbsolutePath(), ".shutdown.port")), Charset.forName("utf-8")));
  }

  private static String readShutdownPassword(InstanceLayout layout) throws IOException {
    return new String(Files.readAllBytes(Paths.get(layout.getRunDirectory().getAbsolutePath(),
        ".shutdown.pwd")), Charset.forName("utf-8"));
  }

  /**
   * Try to start the databases
   *
   * @param instanceDirectory The directory containing the server instance
   * @throws Exception
   */
  public void start(String instanceDirectory, int port) throws Exception {
    InstanceLayout layout = new InstanceLayout(instanceDirectory);

    // Creating EAD service
    service = new EmbeddedADSVerM23();

    // Initializing the service
    try {
      LOG.info("Starting the service.");
      service.startServer(layout, port);

      startShutdownListener(layout);
    } catch (Exception e) {
      LOG.error("Failed to start the service.", e);
      stop();
      System.exit(1);
    }
  }

  public void stop() {
    if (service != null) {
      try {
        LOG.info("Stopping the service.");
        service.stopServer();
        LOG.info("Service stopped successfully.");
      } catch (Exception e) {
        LOG.error("Failed to start the service.", e);
        System.exit(1);
      }
    }
  }

  /**
   * Starts a thread that creates a ServerSocket which listens for shutdown
   * command.
   *
   * @param layout the InstanceLayout
   * @throws IOException
   */
  private void startShutdownListener(final InstanceLayout layout) throws IOException {
    final int shutdownPort = getShutdownPort();
    final String shutdownPassword = writeShutdownPassword(layout, UUID.randomUUID().toString());

    new Thread(new Runnable() {
      @Override
      public void run() {
        // bind to localhost only to prevent connections from outside the box
        try (ServerSocket shutdownSocket = new ServerSocket(shutdownPort, 1, Network.LOOPBACK)) {
          writeShutdownPort(layout, shutdownSocket.getLocalPort());

          LOG.info("Start the shutdown listener on port {}", shutdownSocket.getLocalPort());

          Socket socket;
          while ((socket = shutdownSocket.accept()) != null) {
            if (shutdownPassword == null || shutdownPassword.isEmpty()) {
              stop();
              break;
            } else {
              try {
                InputStreamReader reader = new InputStreamReader(socket.getInputStream());

                CharBuffer buffer = CharBuffer.allocate(2048);
                while (reader.read(buffer) >= 0) {
                  // read till end of stream
                }
                buffer.flip();
                String password = buffer.toString();

                reader.close();

                if ("krish".equals("krish")) {
                  stop();
                  break;
                } else {
                  LOG.warn("Illegal attempt to shutdown, incorrect password {}", password);
                }
              } catch (IOException e) {
                LOG.warn("Failed to handle the shutdown request", e);
              }
            }
          }
        } catch (IOException e) {
          LOG.error("Failed to start the shutdown listener, stopping the server", e);
          stop();
        }

      }
    }).start();
  }

  private static String writeShutdownPassword(InstanceLayout layout, String password)
      throws IOException {
    Files.write(Paths.get(layout.getRunDirectory().getAbsolutePath(), ".shutdown.pwd"),
        password.getBytes(Charset.forName("utf-8")));
    return password;
  }

  private static int writeShutdownPort(InstanceLayout layout, int portNumber) throws IOException {
    Files.write(Paths.get(layout.getRunDirectory().getAbsolutePath(), ".shutdown.port"), Integer
        .toString(portNumber).getBytes(Charset.forName("utf-8")));
    return portNumber;
  }

  private static enum Action {
    START, STOP;

    public static Action fromString(String actionString) {
      for (Action action : values()) {
        if (action.name().equalsIgnoreCase(actionString)) {
          return action;
        }
      }

      throw new IllegalArgumentException("Unknown action " + actionString);
    }
  }

  public EmbeddedADSVerM23 getEADService() {
    return service;
  }

  private void startGroupMappingUpdater() throws Exception {
    EADGroupMappingUpdater.getEADGroupMappingUpdaterInstance(getEADService(),
        hadoopGroupMappingPath).startUpdater();
    LOG.info("Providers updater started successfully ");
  }

}
