package com.krish.directory.service;

import java.io.IOException;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.krish.security.hadoop.impl.GroupsMappingBuilder;
import com.krish.security.hadoop.impl.MultiRegionGroups;

public class DefaultGroupMappingService {

  private GroupsMappingBuilder groupServiceBuilder = new GroupsMappingBuilder();
  private static final Logger LOG = LoggerFactory.getLogger(DefaultGroupMappingService.class);

  public void buildGroupMapping(Path groupMappingXml) throws Exception {
    Configuration conf = new Configuration();
    conf.addResource(groupMappingXml);
    groupServiceBuilder.buildCompositeGroupMappingProviders(conf);
  }

  public void doSchemaUpdate() {
    List<MultiRegionGroups> groupProviders = groupServiceBuilder.getProvidersList();
    for (MultiRegionGroups groupProvider : groupProviders) {
      List<String> groups = groupProvider.getGroups();
      GroupMappingServiceProvider groupMappingProvider = groupProvider.getGroupServiceProvider();
      for (String group : groups) {
        try {
          List<String> users = groupMappingProvider.getUsers(group);
          LOG.info("The users for the group " + group + " are " + users );
          System.out.println("The users for the group " + group + " are " + users );

          // Call the update Schema here
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    }
  }
  
  public static void main(String[] args) throws Exception{
    DefaultGroupMappingService groupMappingService = new DefaultGroupMappingService();
    groupMappingService.buildGroupMapping(null);
    groupMappingService.doSchemaUpdate();
  }
}
