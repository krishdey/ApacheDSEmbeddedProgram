package com.krish.ead.server;

import static org.junit.Assert.assertTrue;

import org.apache.directory.server.core.api.DirectoryService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.krish.directory.service.EadSchemaService;

public class EadIntegrationTest {
  static EADServer eadServer;
  static DirectoryService directoryService;
  static EadSchemaService eadSchemaService;

  @BeforeClass
  public static void setUp() throws Exception {
    eadServer = new EADServer();
    eadServer.start("/tmp/krish", 10389);
    directoryService = eadServer.getEADService().getDirectoryService();
    eadSchemaService = new EadSchemaService(directoryService);

  }

  @Test
  public void testAddUsertoGroup() throws Exception {
    eadSchemaService.createUser("krish", "krish");
    eadSchemaService.createGroup("ND-POC-ENG");
    eadSchemaService.addUserToGroup("krish", "ND-POC-ENG");
    assertTrue(eadSchemaService.checkIfUserExist("krish"));
    assertTrue(eadSchemaService.checkIfGroupExist("ND-POC-ENG"));
    assertTrue(eadSchemaService.checkIfUserMemberOfGroup("krish", "ND-POC-ENG"));
  }

  @AfterClass
  public static void tearDown() {
    eadServer.stop();
  }

}
