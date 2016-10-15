package com.krish.ead.server;

import org.apache.directory.server.core.api.DirectoryService;
import org.junit.Before;
import org.junit.Test;

import com.krish.directory.service.EadSchemaService;

public class EadIntegrationTest {
  EADServer eadServer;
  DirectoryService directoryService;
  EadSchemaService eadSchemaService;

  @Before
  public void setUp() throws Exception {
    eadServer = new EADServer();
    eadServer.start("/tmp/krish",10389);
    directoryService = eadServer.getEADService().getDirectoryService();
    eadSchemaService = new EadSchemaService(directoryService);

  }

  @Test
  public void testAddUsertoGroup() throws Exception {
    eadSchemaService.createUser("krish", "krish");
    eadSchemaService.createGroup("ND-POC-ENG");
    eadSchemaService.addUserToGroup("krish", "ND-POC-ENG");
  }

}
