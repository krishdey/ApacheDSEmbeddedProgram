package com.krish.security.hadoop;

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static org.junit.Assert.assertTrue;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.apache.hadoop.conf.Configuration;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LdapGroupsMappingTest {

  public static final String LDAP_CONFIG_PREFIX = "hadoop.security.group.mapping.ldap";

  /*
   * URL of the LDAP server
   */
  public static final String LDAP_URL_KEY = LDAP_CONFIG_PREFIX + ".url";
  public static final String LDAP_URL_DEFAULT = "ldap://localhost:10389";

  /*
   * Should SSL be used to connect to the server
   */
  public static final String LDAP_USE_SSL_KEY = LDAP_CONFIG_PREFIX + ".ssl";
  public static final Boolean LDAP_USE_SSL_DEFAULT = false;

  /*
   * File path to the location of the SSL keystore to use
   */
  public static final String LDAP_KEYSTORE_KEY = LDAP_CONFIG_PREFIX + ".ssl.keystore";
  public static final String LDAP_KEYSTORE_DEFAULT = "";

  /*
   * Password for the keystore
   */
  public static final String LDAP_KEYSTORE_PASSWORD_KEY = LDAP_CONFIG_PREFIX
      + ".ssl.keystore.password";
  public static final String LDAP_KEYSTORE_PASSWORD_DEFAULT = "";

  public static final String LDAP_KEYSTORE_PASSWORD_FILE_KEY = LDAP_KEYSTORE_PASSWORD_KEY + ".file";
  public static final String LDAP_KEYSTORE_PASSWORD_FILE_DEFAULT = "";

  /*
   * User to bind to the LDAP server with
   */
  public static final String BIND_USER_KEY = LDAP_CONFIG_PREFIX + ".bind.user";
  public static final String BIND_USER_DEFAULT = "cn=krish,ou=users,dc=jpmis,dc=com";

  /*
   * Password for the bind user
   */
  public static final String BIND_PASSWORD_KEY = LDAP_CONFIG_PREFIX + ".bind.password";
  public static final String BIND_PASSWORD_DEFAULT = "krish";

  public static final String BIND_PASSWORD_FILE_KEY = BIND_PASSWORD_KEY + ".file";
  public static final String BIND_PASSWORD_FILE_DEFAULT = "";

  /*
   * Base distinguished name to use for searches
   */
  public static final String BASE_DN_KEY = LDAP_CONFIG_PREFIX + ".base";
  public static final String BASE_DN_DEFAULT = "dc=jpmis,dc=com";

  /*
   * Any additional filters to apply when searching for users
   */
  public static final String USER_SEARCH_FILTER_KEY = LDAP_CONFIG_PREFIX + ".search.filter.user";
  public static final String USER_SEARCH_FILTER_DEFAULT =
      "(&(objectClass=user)(sAMAccountName={0}))";

  /*
   * Any additional filters to apply when finding relevant groups
   */
  public static final String GROUP_SEARCH_FILTER_KEY = LDAP_CONFIG_PREFIX + ".search.filter.group";
  public static final String GROUP_SEARCH_FILTER_DEFAULT = "(objectClass=group)";

  /*
   * LDAP attribute to use for determining group membership
   */
  public static final String GROUP_MEMBERSHIP_ATTR_KEY = LDAP_CONFIG_PREFIX + ".search.attr.member";
  public static final String GROUP_MEMBERSHIP_ATTR_DEFAULT = "member";

  /*
   * LDAP attribute to use for identifying a group's name
   */
  public static final String GROUP_NAME_ATTR_KEY = LDAP_CONFIG_PREFIX + ".search.attr.group.name";
  public static final String GROUP_NAME_ATTR_DEFAULT = "cn";

  /*
   * LDAP {@link SearchControls} attribute to set the time limit for an invoked
   * directory search. Prevents infinite wait cases.
   */
  public static final String DIRECTORY_SEARCH_TIMEOUT = LDAP_CONFIG_PREFIX
      + ".directory.search.timeout";
  public static final int DIRECTORY_SEARCH_TIMEOUT_DEFAULT = 10000; // 10s

  private static final Logger LOG = LoggerFactory.getLogger(LdapGroupsMappingTest.class);

  private static final SearchControls SEARCH_CONTROLS = new SearchControls();
  static {
    SEARCH_CONTROLS.setSearchScope(SearchControls.SUBTREE_SCOPE);
  }

  private DirContext ctx;
  private Configuration conf;

  private String ldapUrl;
  private boolean useSsl;
  private String keystore;
  private String keystorePass;
  private String bindUser;
  private String bindPassword;
  private String baseDN;
  private String groupSearchFilter;
  private String userSearchFilter;
  private String groupMemberAttr;
  private String groupNameAttr;

  public static int RECONNECT_RETRY_COUNT = 3;

  /**
   * Returns list of groups for a user.
   *
   * The LdapCtx which underlies the DirContext object is not thread-safe, so we
   * need to block around this whole method. The caching infrastructure will
   * ensure that performance stays in an acceptable range.
   *
   * @param user get groups for this user
   * @return list of groups for a given user
   */
  public synchronized List<String> getGroups(String user) throws IOException {
    List<String> emptyResults = new ArrayList<String>();
    /*
     * Normal garbage collection takes care of removing Context instances when
     * they are no longer in use. Connections used by Context instances being
     * garbage collected will be closed automatically. So in case connection is
     * closed and gets CommunicationException, retry some times with new new
     * DirContext/connection.
     */
    try {
      return doGetGroups(user);
    } catch (CommunicationException e) {
      LOG.warn("Connection is closed, will try to reconnect");
    } catch (NamingException e) {
      LOG.warn("Exception trying to get groups for user " + user + ": " + e.getMessage());
      return emptyResults;
    }

    int retryCount = 0;
    while (retryCount++ < RECONNECT_RETRY_COUNT) {
      // reset ctx so that new DirContext can be created with new connection
      this.ctx = null;

      try {
        return doGetGroups(user);
      } catch (CommunicationException e) {
        LOG.warn("Connection being closed, reconnecting failed, retryCount = " + retryCount);
      } catch (NamingException e) {
        LOG.warn("Exception trying to get groups for user " + user + ":" + e.getMessage());
        return emptyResults;
      }
    }

    return emptyResults;
  }

  List<String> doGetGroups(String user) throws NamingException {
    List<String> groups = new ArrayList<String>();

    DirContext ctx = getDirContext();

    // Search for the user. We'll only ever need to look at the first result
    NamingEnumeration<SearchResult> results =
        ctx.search(baseDN, userSearchFilter, new Object[] { user }, SEARCH_CONTROLS);
    if (results.hasMoreElements()) {
      SearchResult result = results.nextElement();
      String userDn = result.getNameInNamespace();

      NamingEnumeration<SearchResult> groupResults =
          ctx.search(baseDN, "(&" + groupSearchFilter + "(" + groupMemberAttr + "={0}))",
              new Object[] { userDn }, SEARCH_CONTROLS);
      while (groupResults.hasMoreElements()) {
        SearchResult groupResult = groupResults.nextElement();
        Attribute groupName = groupResult.getAttributes().get(groupNameAttr);
        groups.add(groupName.get().toString());
      }
    }

    return groups;
  }

  DirContext getDirContext() throws NamingException {
    if (ctx == null) {
      // Set up the initial environment for LDAP connectivity
      Hashtable<String, String> env = new Hashtable<String, String>();
      env.put(Context.INITIAL_CONTEXT_FACTORY, com.sun.jndi.ldap.LdapCtxFactory.class.getName());
      env.put(Context.PROVIDER_URL, ldapUrl);
      env.put(Context.SECURITY_AUTHENTICATION, "simple");

      // Set up SSL security, if necessary
      if (useSsl) {
        env.put(Context.SECURITY_PROTOCOL, "ssl");
        System.setProperty("javax.net.ssl.keyStore", keystore);
        System.setProperty("javax.net.ssl.keyStorePassword", keystorePass);
      }

      env.put(Context.SECURITY_PRINCIPAL, bindUser);
      env.put(Context.SECURITY_CREDENTIALS, bindPassword);

      ctx = new InitialDirContext(env);
    }

    return ctx;
  }

  /**
   * Caches groups, no need to do that for this provider
   */
  public void cacheGroupsRefresh() throws IOException {
    // does nothing in this provider of user to groups mapping
  }

  /**
   * Adds groups to cache, no need to do that for this provider
   *
   * @param groups unused
   */
  public void cacheGroupsAdd(List<String> groups) throws IOException {
    // does nothing in this provider of user to groups mapping
  }

  public synchronized Configuration getConf() {
    return conf;
  }

  public synchronized void setConf(Configuration conf) {
    ldapUrl = LDAP_URL_DEFAULT;
    if (ldapUrl == null || ldapUrl.isEmpty()) {
      throw new RuntimeException("LDAP URL is not configured");
    }

    useSsl = LDAP_USE_SSL_DEFAULT;
    keystore = conf.get(LDAP_KEYSTORE_KEY, LDAP_KEYSTORE_DEFAULT);

    keystorePass = getPassword(conf, LDAP_KEYSTORE_PASSWORD_KEY, LDAP_KEYSTORE_PASSWORD_DEFAULT);
    if (keystorePass.isEmpty()) {
      keystorePass =
          extractPassword(conf.get(LDAP_KEYSTORE_PASSWORD_FILE_KEY,
              LDAP_KEYSTORE_PASSWORD_FILE_DEFAULT));
    }

    bindUser = BIND_USER_DEFAULT;
    bindPassword = BIND_PASSWORD_DEFAULT;
    if (bindPassword.isEmpty()) {
      bindPassword = extractPassword(conf.get(BIND_PASSWORD_FILE_KEY, BIND_PASSWORD_FILE_DEFAULT));
    }

    baseDN = BASE_DN_DEFAULT;
    groupSearchFilter = GROUP_SEARCH_FILTER_DEFAULT;
    userSearchFilter = USER_SEARCH_FILTER_DEFAULT;
    groupMemberAttr = GROUP_MEMBERSHIP_ATTR_DEFAULT;
    groupNameAttr = GROUP_NAME_ATTR_DEFAULT;

    int dirSearchTimeout = DIRECTORY_SEARCH_TIMEOUT_DEFAULT;
    SEARCH_CONTROLS.setTimeLimit(dirSearchTimeout);

    this.conf = conf;
  }

  String getPassword(Configuration conf, String alias, String defaultPass) {
    String password = null;
    try {
      char[] passchars = conf.getPassword(alias);
      if (passchars != null) {
        password = new String(passchars);
      } else {
        password = defaultPass;
      }
    } catch (IOException ioe) {
      LOG.warn("Exception while trying to password for alias " + alias + ": " + ioe.getMessage());
    }
    return password;
  }

  String extractPassword(String pwFile) {
    if (pwFile.isEmpty()) {
      // If there is no password file defined, we'll assume that we should do
      // an anonymous bind
      return "";
    }

    Reader reader = null;
    try {
      StringBuilder password = new StringBuilder();
      reader = new FileReader(pwFile);
      int c = reader.read();
      while (c > -1) {
        password.append((char) c);
        c = reader.read();
      }
      return password.toString().trim();
    } catch (IOException ioe) {
      throw new RuntimeException("Could not read password file: " + pwFile, ioe);
    } finally {
      // IOUtils.cleanup(LOG, reader);
      try {
        reader.close();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  @Test
  public void testUserGroup() throws Exception {

    LdapGroupsMappingTest ldap = new LdapGroupsMappingTest();
    ldap.setConf(new Configuration());
    System.out.println(ldap.getGroups("krish"));
    assertTrue(ldap.getGroups("krish").get(0).equals("ND-POC-ENG"));

  }

}
