package com.krish.security.hadoop.impl;

import java.util.List;

import com.krish.security.hadoop.GroupMappingServiceProvider;


public class MultiRegionGroups {
  
  public GroupMappingServiceProvider groupServiceProvider;
  
  public List<String> groups;

  public GroupMappingServiceProvider getGroupServiceProvider() {
    return groupServiceProvider;
  }

  public void setGroupServiceProvider(GroupMappingServiceProvider groupServiceProvider) {
    this.groupServiceProvider = groupServiceProvider;
  }

  public List<String> getGroups() {
    return groups;
  }

  public void setGroups(List<String> groups) {
    this.groups = groups;
  }
  
  
}
