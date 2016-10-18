package com.krish.ead.server;

import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.krish.directory.service.DefaultGroupMappingService;
import com.krish.directory.service.EadSchemaService;
import com.krish.directory.service.GroupMappingService;

public final class EADGroupMappingUpdater {

  static EADGroupMappingUpdater eadGroupMappingUpdater;
  static EadSchemaService eadSchemaService;
  static GroupMappingService groupMappingService;

  private static final Logger LOG = LoggerFactory.getLogger(EADGroupMappingUpdater.class);

  private Thread thread;
  
  private static volatile boolean running = true;

  private EADGroupMappingUpdater() {
      //Do not allow it to instantiated default constructor
  }

  public static EADGroupMappingUpdater getEADGroupMappingUpdaterInstance(EmbeddedADSVerM23 service,
      String hadoopGroupMappingPath) throws Exception {

    if (eadGroupMappingUpdater == null) {
      LOG.info("Initializing Updater class for Providers ....");
      eadGroupMappingUpdater = new EADGroupMappingUpdater();
      eadSchemaService = new EadSchemaService(service.getDirectoryService());

      while (!service.getDirectoryService().isStarted()) {
        System.out.println("Waiting for service to be started");
        Thread.sleep(1000);
      }
      eadSchemaService.loadTestUser();

      groupMappingService = new DefaultGroupMappingService();
      groupMappingService.setEadSchemaService(eadSchemaService);

      groupMappingService.buildGroupMapping(new Path(hadoopGroupMappingPath));

    }
    return eadGroupMappingUpdater;

  }

  public void startUpdater() {
    thread = new Thread(new GroupMappingUpdaterThread(60 * 1000, groupMappingService));
    thread.start();
  }

  public void stopUpdater() {
    running = false;
    thread.interrupt();
    thread = null;
  }

  static class GroupMappingUpdaterThread implements Runnable {

    private int interval;

    public GroupMappingUpdaterThread(int interval, GroupMappingService grpMapService) {
      this.interval = interval;
    }

    @Override
    public void run() {

      while (running) {
        try {
          LOG.info("Going to run schema update");
          groupMappingService.doSchemaUpdate();
          LOG.info("Schema update finishded");
          Thread.sleep(interval);
        } catch (InterruptedException e) {
          LOG.info("Thread has been interrupted " + e);
        }

      }

    }

  }

}
