package com.krish.ead.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.apache.directory.api.ldap.extras.controls.ppolicy.PasswordPolicy;
import org.apache.directory.api.ldap.extras.controls.ppolicy.PasswordPolicyImpl;
import org.apache.directory.api.ldap.model.constants.SchemaConstants;
import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.message.ModifyRequest;
import org.apache.directory.api.ldap.model.message.ModifyRequestImpl;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.LdapComparator;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.api.ldap.model.schema.comparators.NormalizingComparator;
import org.apache.directory.api.ldap.model.schema.registries.ComparatorRegistry;
import org.apache.directory.api.ldap.model.schema.registries.SchemaLoader;
import org.apache.directory.api.ldap.schema.extractor.SchemaLdifExtractor;
import org.apache.directory.api.ldap.schema.extractor.impl.DefaultSchemaLdifExtractor;
import org.apache.directory.api.ldap.schema.loader.LdifSchemaLoader;
import org.apache.directory.api.ldap.schema.manager.impl.DefaultSchemaManager;
import org.apache.directory.api.util.DateUtils;
import org.apache.directory.api.util.FileUtils;
import org.apache.directory.api.util.exception.Exceptions;
import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.api.CacheService;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.DnFactory;
import org.apache.directory.server.core.api.InstanceLayout;
import org.apache.directory.server.core.api.partition.Partition;
import org.apache.directory.server.core.api.schema.SchemaPartition;
import org.apache.directory.server.core.factory.JdbmPartitionFactory;
import org.apache.directory.server.core.factory.PartitionFactory;
import org.apache.directory.server.core.partition.ldif.LdifPartition;
import org.apache.directory.server.core.shared.DefaultDnFactory;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.protocol.shared.store.LdifFileLoader;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.krish.directory.service.EadSchemaService;

/**
 * A simple example exposing how to embed Apache Directory Server version M23
 * into an application.
 *
 * @author <a>krishdey</a>
 * @version $Rev$, $Date$
 */
public class EmbeddedADSVerM23 {

  /**
   * the log for this class
   */
  private static final Logger LOG = LoggerFactory.getLogger(EmbeddedADSVerM23.class);

  /** The directory service */
  private DirectoryService directoryService;

  /** The LDAP server */
  private LdapServer server;

  private PartitionFactory partitionFactory;

  /**
   * Inits the system partition.
   *
   * @throws Exception the exception
   */
  private void initSystemPartition() throws Exception {
    // change the working directory to something that is unique
    // on the system and somewhere either under target directory
    // or somewhere in a temp area of the machine.

    // Inject the System Partition
    Partition systemPartition =
        partitionFactory.createPartition(directoryService.getSchemaManager(), directoryService
            .getDnFactory(), "system", ServerDNConstants.SYSTEM_DN, 500, new File(directoryService
            .getInstanceLayout().getPartitionsDirectory(), "system"));
    systemPartition.setSchemaManager(directoryService.getSchemaManager());

    partitionFactory.addIndex(systemPartition, SchemaConstants.OBJECT_CLASS_AT, 100);

    directoryService.setSystemPartition(systemPartition);
  }

  /**
   * Init Schema
   *
   * @throws Exception
   */
  private void initSchema() throws Exception {
    File workingDirectory = directoryService.getInstanceLayout().getPartitionsDirectory();

    // Extract the schema on disk (a brand new one) and load the registries
    File schemaRepository = new File(workingDirectory, "schema");
    SchemaLdifExtractor extractor = new DefaultSchemaLdifExtractor(workingDirectory);

    try {
      extractor.extractOrCopy();
    } catch (IOException ioe) {
      // The schema has already been extracted, bypass
    }

    SchemaLoader loader = new LdifSchemaLoader(schemaRepository);
    SchemaManager schemaManager = new DefaultSchemaManager(loader);

    // We have to load the schema now, otherwise we won't be able
    // to initialize the Partitions, as we won't be able to parse
    // and normalize their suffix Dn
    schemaManager.loadAllEnabled();

    // Tell all the normalizer comparators that they should not normalize
    // anything
    ComparatorRegistry comparatorRegistry = schemaManager.getComparatorRegistry();

    for (LdapComparator<?> comparator : comparatorRegistry) {
      if (comparator instanceof NormalizingComparator) {
        ((NormalizingComparator) comparator).setOnServer();
      }
    }

    directoryService.setSchemaManager(schemaManager);

    // Init the LdifPartition
    LdifPartition ldifPartition = new LdifPartition(schemaManager, directoryService.getDnFactory());
    ldifPartition.setPartitionPath(new File(workingDirectory, "schema").toURI());
    SchemaPartition schemaPartition = new SchemaPartition(schemaManager);
    schemaPartition.setWrappedPartition(ldifPartition);
    directoryService.setSchemaPartition(schemaPartition);

    List<Throwable> errors = schemaManager.getErrors();

    if (errors.size() != 0) {
      throw new Exception(I18n.err(I18n.ERR_317, Exceptions.printErrors(errors)));
    }
  }

  private void loadJpmisSchema() throws IOException {

    String ldifData = IOUtils.toString(getClass().getClassLoader().getResourceAsStream("krish.schema"));
    
    File ldifFile = File.createTempFile("ldif", ".tmp");
    ldifFile.deleteOnExit();
    
    FileOutputStream out = new FileOutputStream(ldifFile);
    IOUtils.write(ldifData, out);

    LdifFileLoader loader = new LdifFileLoader(directoryService.getAdminSession(), ldifFile.getAbsolutePath());
    int count = loader.execute();
    LOG.info("Krish schema has been loaded with count " + count);

  }

  /**
   * Initialize the server. It creates the partition, adds the index, and
   * injects the context entries for the created partitions.
   *
   * @param workDir the directory to be used for storing the data
   * @throws Exception if there were some problems while initializing the system
   */
  private void initDirectoryService(InstanceLayout layout) throws Exception {
    // Initialize the LDAP service
    directoryService = new DefaultDirectoryService();
    buildInstanceDirectory(layout);

    CacheService cacheService = new CacheService();
    cacheService.initialize(directoryService.getInstanceLayout(), "krish");

    directoryService.setCacheService(cacheService);

    // first load the schema
    initSchema();
    initSystemPartition();

    DnFactory dnFactory =
        new DefaultDnFactory(directoryService.getSchemaManager(), cacheService.getCache("krish"));
    directoryService.setDnFactory(dnFactory);

    // Disable the ChangeLog system
    directoryService.getChangeLog().setEnabled(false);
    directoryService.setDenormalizeOpAttrsEnabled(true);
    directoryService.startup();
  }

  /**
   * Build the working directory
   */
  private void buildInstanceDirectory(InstanceLayout instanceLayout) throws IOException {

    if (instanceLayout.getInstanceDirectory().exists()) {
      try {
        FileUtils.deleteDirectory(instanceLayout.getInstanceDirectory());
      } catch (IOException e) {
        System.out
            .println("couldn't delete the instance directory before initializing the DirectoryService"
                + e);
      }
    }

    directoryService.setInstanceLayout(instanceLayout);
  }

  // Add jpmis partition
  private void addJpmisPartition() throws Exception {
    Partition jpmisPartition =
        partitionFactory.createPartition(directoryService.getSchemaManager(), directoryService
            .getDnFactory(), "jpmis", "dc=jpmis,dc=com", 500, new File(directoryService
            .getInstanceLayout().getPartitionsDirectory(), "jpmis"));
    jpmisPartition.setSchemaManager(directoryService.getSchemaManager());

    partitionFactory.addIndex(jpmisPartition, SchemaConstants.OBJECT_CLASS_AT, 100);
    directoryService.addPartition(jpmisPartition);

    Dn suffixDn = new Dn(directoryService.getSchemaManager(), "dc=jpmis,dc=com");

    Entry jpmisEntry = directoryService.newEntry(suffixDn);
    jpmisEntry.add("objectClass", "top", "domain", "extensibleObject");
    directoryService.getAdminSession().add(jpmisEntry);

    // Add OU=Groups
    Dn groupDn = new Dn(directoryService.getSchemaManager(), "ou=groups,dc=jpmis,dc=com");

    Entry groupEntry = new DefaultEntry(directoryService.getSchemaManager(), groupDn);

    groupEntry.put(SchemaConstants.OBJECT_CLASS_AT, SchemaConstants.TOP_OC,
        SchemaConstants.ORGANIZATIONAL_UNIT_OC);

    groupEntry.put(SchemaConstants.OU_AT, "groups");
    groupEntry.put(SchemaConstants.CREATORS_NAME_AT, ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED);
    groupEntry.put(SchemaConstants.CREATE_TIMESTAMP_AT, DateUtils.getGeneralizedTime());
    groupEntry.add(SchemaConstants.ENTRY_UUID_AT, UUID.randomUUID().toString());

    directoryService.getAdminSession().add(groupEntry);

    Dn userDn = new Dn(directoryService.getSchemaManager(), "ou=users,dc=jpmis,dc=com");

    Entry usersEntry = new DefaultEntry(directoryService.getSchemaManager(), userDn);

    usersEntry.put(SchemaConstants.OBJECT_CLASS_AT, SchemaConstants.TOP_OC,
        SchemaConstants.ORGANIZATIONAL_UNIT_OC);

    usersEntry.put(SchemaConstants.OU_AT, "users");
    usersEntry.put(SchemaConstants.CREATORS_NAME_AT, ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED);
    usersEntry.put(SchemaConstants.CREATE_TIMESTAMP_AT, DateUtils.getGeneralizedTime());
    usersEntry.add(SchemaConstants.ENTRY_UUID_AT, UUID.randomUUID().toString());

    directoryService.getAdminSession().add(usersEntry);
  }

  /**
   * Creates a new instance of EmbeddedADS. It initializes the directory
   * service.
   *
   * @throws Exception If something went wrong
   */
  public EmbeddedADSVerM23() throws Exception {
    try {
      String typeName = System.getProperty("apacheds.partition.factory");

      if (typeName != null) {
        @SuppressWarnings("unchecked")
        Class<? extends PartitionFactory> type =
            (Class<? extends PartitionFactory>) Class.forName(typeName);
        partitionFactory = type.newInstance();
      } else {
        partitionFactory = new JdbmPartitionFactory();
      }
    } catch (Exception e) {
      System.out.println("Error instantiating custom partiton factory" + e);
      throw new RuntimeException(e);
    }

  }

  /**
   * starts the LdapServer
   *
   * @throws Exception
   */
  public void startServer(InstanceLayout layout, int serverPort) throws Exception {
    initDirectoryService(layout);
    // Add JPMIS related attributes to schemaManager
    loadJpmisSchema();
    changePassword(new Dn("uid=admin, ou=system"), "secret", "krish".getBytes());
    addJpmisPartition();
    server = new LdapServer();
    server.setTransports(new TcpTransport(serverPort));
    server.setDirectoryService(directoryService);
    server.start();
  }

  /**
   * Stop the server and services
   * 
   * @throws Exception
   */
  public void stopServer() throws Exception {
    directoryService.shutdown();
    server.stop();
  }

  private void changePassword(Dn userDn, String oldPassword, byte[] newPassword) throws Exception {
    PasswordPolicy PP_REQ_CTRL = new PasswordPolicyImpl();
    ModifyRequest modifyRequest = new ModifyRequestImpl();
    modifyRequest.setName(userDn);
    modifyRequest.replace("userPassword", newPassword);
    modifyRequest.addControl(PP_REQ_CTRL);
    directoryService.getAdminSession().modify(modifyRequest);
  }

  public DirectoryService getDirectoryService() {
    return directoryService;
  }

  /**
   * 
   * This is for testing purpose.  DO NOT REMOVE
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    InstanceLayout layout = new InstanceLayout("/tmp/krish");
    EmbeddedADSVerM23 ads = new EmbeddedADSVerM23();
    ads.startServer(layout, 10689);
    EadSchemaService eadSchemaService = new EadSchemaService(ads.getDirectoryService());
    eadSchemaService.createUser("krish", "krish");
    eadSchemaService.createGroup("ND-POC-ENG");
    eadSchemaService.addUserToGroup("krish", "ND-POC-ENG");
  }
}