package com.krish.directory.service;


import java.io.IOException;
import java.util.List;

import org.apache.hadoop.fs.CommonConfigurationKeysPublic;
import org.apache.hadoop.security.Groups;

/**
 * An interface for the implementation of a user-to-groups mapping service
 * used by {@link Groups}.
 */

public interface GroupMappingServiceProvider {
  public static final String GROUP_MAPPING_CONFIG_PREFIX = CommonConfigurationKeysPublic.HADOOP_SECURITY_GROUP_MAPPING;
  
  /**
   * Get all various users of a given group.
   * Returns EMPTY list in case of non-existing user
   * @param  group
   * @return users of group
   * @throws IOException
   */
  public List<String> getUsers(String group) throws IOException;

}

