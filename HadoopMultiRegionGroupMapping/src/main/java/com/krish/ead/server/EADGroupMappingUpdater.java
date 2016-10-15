package com.krish.ead.server;

import com.krish.directory.service.DefaultGroupMappingService;
import com.krish.directory.service.EadSchemaService;

public class EADGroupMappingUpdater {

  static EADGroupMappingUpdater eadGroupMappingUpdater;
  static EadSchemaService eadSchemaService;
  static DefaultGroupMappingService groupMappingService;

  private EADGroupMappingUpdater() {

  }

  public static EADGroupMappingUpdater getEADGroupMappingUpdaterInstance(EmbeddedADSVerM23 service)
      throws Exception {

    if (eadGroupMappingUpdater == null) {
      eadGroupMappingUpdater = new EADGroupMappingUpdater();
      eadSchemaService = new EadSchemaService(service.getDirectoryService());
      while(!service.getDirectoryService().isStarted()){
        System.out.println("Waiting for thread to be started");
        Thread.sleep(1000);
      }
      eadSchemaService.loadTestUser();
    }
    return eadGroupMappingUpdater;

  }

  public void startUpdater() {
    Thread thread = new Thread(new GroupMappingUpdaterThread());
    thread.start();
  }

  static class GroupMappingUpdaterThread implements Runnable {

    private int interval;

    @Override
    public void run() {

      while (true) {
         try {
          Thread.sleep(5*60*1000);
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }

      }

    }

  }

}
