package com.krish.security.hadoop.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.krish.security.hadoop.GroupMappingServiceProvider;

public class GroupsMappingBuilder {

  private Configuration conf;

  public static final String GROUP_MAPPING_CONFIG_PREFIX = "hadoop.security.group.mapping";
  public static final String MAPPING_PROVIDERS_CONFIG_KEY = GROUP_MAPPING_CONFIG_PREFIX
      + ".providers";
  public static final String MAPPING_PROVIDER_CONFIG_PREFIX = GROUP_MAPPING_CONFIG_PREFIX
      + ".provider";

  private static final Logger LOG = LoggerFactory.getLogger(GroupsMappingBuilder.class);

  private List<MultiRegionGroups> providersList = new ArrayList<MultiRegionGroups>();

  static {
    Configuration.addDefaultResource("hadoop-groupmapping.xml");
  }

  public void buildCompositeGroupMappingProviders() throws Exception {
    conf = new Configuration();
    loadMappingProviders();
  }

  public List<MultiRegionGroups> getProvidersList() {
    return providersList;
  }

  public synchronized void setConf(Configuration conf) throws Exception {
    this.conf = conf;
    loadMappingProviders();
  }

  public Configuration getConf() {
    return conf;
  }

  private void loadMappingProviders() throws ClassNotFoundException {
    String[] providerNames = conf.getStrings(MAPPING_PROVIDERS_CONFIG_KEY, new String[] {});

    String providerKey;
    for (String name : providerNames) {
      providerKey = MAPPING_PROVIDER_CONFIG_PREFIX + "." + name;
      Class<?> providerClass =
          conf.getClass(providerKey,
              conf.getClassByName("com.krish.security.hadoop.impl.LdapGroupsMapping"));
      if (providerClass == null) {
        LOG.error("The mapping provider, " + name + " does not have a valid class");
      } else {
        addMappingProvider(name, providerClass);
      }
    }
  }

  private void addMappingProvider(String providerName, Class<?> providerClass) {
    Configuration newConf = prepareConf(providerName);
    GroupMappingServiceProvider provider =
        (GroupMappingServiceProvider) ReflectionUtils.newInstance(providerClass, newConf);

    MultiRegionGroups multiRegionGroup = new MultiRegionGroups();
    multiRegionGroup.setGroupServiceProvider(provider);
    multiRegionGroup.setGroups(Arrays.asList(conf.getStrings(MAPPING_PROVIDER_CONFIG_PREFIX + "."
        + providerName + ".ldap.groups")));

    providersList.add(multiRegionGroup);

  }

  /*
   * For any provider specific configuration properties, such as
   * "hadoop.security.group.mapping.ldap.url" and the like, allow them to be
   * configured as "hadoop.security.group.mapping.provider.PROVIDER-X.ldap.url",
   * so that a provider such as LdapGroupsMapping can be used to composite a
   * complex one with other providers.
   */
  private Configuration prepareConf(String providerName) {
    Configuration newConf = new Configuration();
    Iterator<Map.Entry<String, String>> entries = conf.iterator();
    String providerKey = MAPPING_PROVIDER_CONFIG_PREFIX + "." + providerName;
    while (entries.hasNext()) {
      Map.Entry<String, String> entry = entries.next();
      String key = entry.getKey();

      if (key.startsWith(providerKey) && !key.equals(providerKey)) {
        key = key.replace(".provider." + providerName, "");
        newConf.set(key, entry.getValue());
      }
    }
    return newConf;
  }

}
