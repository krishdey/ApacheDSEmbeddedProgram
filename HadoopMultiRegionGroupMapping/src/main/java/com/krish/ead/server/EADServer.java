package com.krish.ead.server;

import java.io.File;

import org.apache.commons.lang.StringUtils;
import org.apache.directory.server.core.api.InstanceLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EAD Server
 * 
 * @author krishdey
 *
 */
public class EADServer {
  /** A logger for this class */
  private static final Logger LOG = LoggerFactory.getLogger(EADServer.class);

  /** The EAD service */
  private static EmbeddedADSVerM23 service;

  private static final String EAD_STARTUP_PORT = "ead.server.port";

  private static final int DEFAULT_STARTUP_PORT = 10389;

  private static final String HADOOP_GROUP_MAPPING_XML = "hadoop-group-mapping";

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

    //Check for hadoop xml location for group mapping
    hadoopGroupMappingPath = System.getProperty(HADOOP_GROUP_MAPPING_XML);
    if (!new File(hadoopGroupMappingPath).exists()) {
      throw new RuntimeException("hadoop group mapping xml is not found");
    }
    

    switch (action) {
    case START:
      startShutdownHook();
      instance.start(instanceDirectory, port);
      instance.startGroupMappingUpdater();

      break;

    case STOP:
      // Stops the server
      LOG.debug("Stopping runtime");
      shutdown();
      break;

    default:
      throw new IllegalArgumentException("Unexpected action " + action);
    }

    LOG.trace("Exiting main");
  }

  /**
   * Add the shutdown hook
   */
  private static void startShutdownHook(){
    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
      public void run() {
        LOG.info("Shutdown thread called");
        try {
          shutdown();
        } catch (Exception e) {
          LOG.warn("Failed to shut down the EAD service: " + e);
        }
      }
    }, "ApacheDS Shutdown Hook"));

    // Starts the server
    LOG.debug("Starting runtime");
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
      
    } catch (Exception e) {
      LOG.error("Failed to start the service.", e);
      stop();
      System.exit(1);
    }
  }

  private static void shutdown() {
    try {
      EADGroupMappingUpdater.getEADGroupMappingUpdaterInstance(getEADService(),
          hadoopGroupMappingPath).stopUpdater();
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    stop();
  }

  public static void stop() {
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

  public static EmbeddedADSVerM23 getEADService() {
    return service;
  }

  private void startGroupMappingUpdater() throws Exception {
    EADGroupMappingUpdater.getEADGroupMappingUpdaterInstance(getEADService(),
        hadoopGroupMappingPath).startUpdater();
    LOG.info("Providers updater started successfully ");
  }

}
