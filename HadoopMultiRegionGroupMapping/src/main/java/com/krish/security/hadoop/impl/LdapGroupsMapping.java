package com.krish.security.hadoop.impl;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.krish.security.hadoop.GroupMappingServiceProvider;

public class LdapGroupsMapping implements GroupMappingServiceProvider, Configurable {

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
  public static final String BIND_USER_DEFAULT = "";

  /*
   * Password for the bind user
   */
  public static final String BIND_PASSWORD_KEY = LDAP_CONFIG_PREFIX + ".bind.password";
  public static final String BIND_PASSWORD_DEFAULT = "";

  public static final String BIND_PASSWORD_FILE_KEY = BIND_PASSWORD_KEY + ".file";
  public static final String BIND_PASSWORD_FILE_DEFAULT = "";

  /*
   * Base distinguished name to use for searches
   */
  public static final String BASE_DN_KEY = LDAP_CONFIG_PREFIX + ".base";
  public static final String BASE_DN_DEFAULT = "";

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
   * LDAP attribute to use for determining group membership
   */
  public static final String MEMBEROF_ATTR_KEY = LDAP_CONFIG_PREFIX + ".search.attr.memberof";
  public static final String MEMBEROF_ATTR_DEFAULT = "";

  /*
   * LDAP attribute to use for identifying a group's name
   */
  public static final String GROUP_NAME_ATTR_KEY = LDAP_CONFIG_PREFIX + ".search.attr.user.name";
  public static final String GROUP_NAME_ATTR_DEFAULT = "cn";

  /*
   * LDAP {@link SearchControls} attribute to set the time limit for an invoked
   * directory search. Prevents infinite wait cases.
   */
  public static final String DIRECTORY_SEARCH_TIMEOUT = LDAP_CONFIG_PREFIX
      + ".directory.search.timeout";
  public static final int DIRECTORY_SEARCH_TIMEOUT_DEFAULT = 10000; // 10s

  private static final Logger LOG = LoggerFactory.getLogger(LdapGroupsMapping.class);

  private static final SearchControls SEARCH_CONTROLS = new SearchControls();

  public static int RECONNECT_RETRY_COUNT = 3;

  private DirContext ctx;

  private String ldapUrl;

  private boolean useSsl;
  private String keystore;

  private String keystorePass;
  private String bindUser;
  private String bindPassword;
  private String baseDN;
  private String groupSearchFilter;
  private String memberOfAttr;
  private String groupMemberAttr;
  private String groupNameAttr;
  private boolean useOneQuery;

  static {
    SEARCH_CONTROLS.setSearchScope(SearchControls.SUBTREE_SCOPE);
  }

  @Override
  public List<String> getUsers(String group) throws IOException {
    List<String> emptyResults = new ArrayList<String>();

    try {
      return doGetUsersOfGroup(group);
    } catch (CommunicationException e) {
      LOG.warn("Connection is closed, will try to reconnect");
    } catch (NamingException e) {
      LOG.warn("Exception trying to get groups for user " + group + ": " + e.getMessage());
      return emptyResults;
    }

    int retryCount = 0;
    while (retryCount++ < RECONNECT_RETRY_COUNT) {
      // reset ctx so that new DirContext can be created with new connection

      try {
        return doGetUsersOfGroup(group);
      } catch (CommunicationException e) {
        LOG.warn("Connection being closed, reconnecting failed, retryCount = " + retryCount);
      } catch (NamingException e) {
        LOG.warn("Exception trying to get groups for user " + group + ":" + e.getMessage());
        return emptyResults;
      }
    }

    return emptyResults;

  }

  @SuppressWarnings("rawtypes")
  List<String> doGetUsersOfGroup(String group) throws NamingException {
    List<String> groups = new ArrayList<String>();

    DirContext ctx = getDirContext();

    SearchControls searchCtrls = new SearchControls();
    searchCtrls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    String[] attributes = groupMemberAttr.split(",");

    searchCtrls.setReturningAttributes(attributes);

    String filter = "(&" + groupSearchFilter + "(" + groupNameAttr + "={0}))";

    NamingEnumeration values = ctx.search(baseDN, filter, new Object[] { group }, searchCtrls);

    while (values.hasMoreElements()) {
      SearchResult sr = (SearchResult) values.next();
      Attributes attrs = sr.getAttributes();

      if (null != attrs) {
        for (NamingEnumeration ae = attrs.getAll(); ae.hasMoreElements();) {
          Attribute atr = (Attribute) ae.next();
          String attributeID = atr.getID();
          Enumeration vals = atr.getAll();

          while (vals.hasMoreElements()) {
            String username = (String) vals.nextElement();
            LdapName ln = new LdapName(username);
            for (Rdn rdn : ln.getRdns()) {
              if (rdn.getType().equalsIgnoreCase("cn")) {
                LOG.debug("CN is: " + rdn.getValue());
                groups.add(rdn.getValue().toString());
                break;
              }
            }

          }
        }
      } else {
        LOG.info("No members for groups found");
      }
    }

    return groups;
  }

  @SuppressWarnings("restriction")
  private DirContext getDirContext() throws NamingException {
    if (ctx == null) {
      // Set up the initial environment for LDAP connectivity
      Hashtable<String, String> env = new Hashtable<String, String>();
      env.put(Context.INITIAL_CONTEXT_FACTORY, com.sun.jndi.ldap.LdapCtxFactory.class.getName());
      env.put(Context.PROVIDER_URL, LDAP_URL_DEFAULT);
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

  @Override
  public Configuration getConf() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void setConf(Configuration conf) {
    ldapUrl = conf.get(LDAP_URL_KEY, LDAP_URL_DEFAULT);
    if (ldapUrl == null || ldapUrl.isEmpty()) {
      throw new RuntimeException("LDAP URL is not configured");
    }

    useSsl = conf.getBoolean(LDAP_USE_SSL_KEY, LDAP_USE_SSL_DEFAULT);
    keystore = conf.get(LDAP_KEYSTORE_KEY, LDAP_KEYSTORE_DEFAULT);

    keystorePass = getPassword(conf, LDAP_KEYSTORE_PASSWORD_KEY, LDAP_KEYSTORE_PASSWORD_DEFAULT);
    if (keystorePass.isEmpty()) {
      keystorePass =
          extractPassword(conf.get(LDAP_KEYSTORE_PASSWORD_FILE_KEY,
              LDAP_KEYSTORE_PASSWORD_FILE_DEFAULT));
    }

    bindUser = conf.get(BIND_USER_KEY, BIND_USER_DEFAULT);
    bindPassword = getPassword(conf, BIND_PASSWORD_KEY, BIND_PASSWORD_DEFAULT);
    
    if (bindPassword.isEmpty()) {
      bindPassword = extractPassword(conf.get(BIND_PASSWORD_FILE_KEY, BIND_PASSWORD_FILE_DEFAULT));
    }

    baseDN = conf.get(BASE_DN_KEY, BASE_DN_DEFAULT);
    groupSearchFilter = conf.get(GROUP_SEARCH_FILTER_KEY, GROUP_SEARCH_FILTER_DEFAULT);

    memberOfAttr = conf.get(MEMBEROF_ATTR_KEY, MEMBEROF_ATTR_DEFAULT);

    useOneQuery = !memberOfAttr.isEmpty();
    groupMemberAttr = conf.get(GROUP_MEMBERSHIP_ATTR_KEY, GROUP_MEMBERSHIP_ATTR_DEFAULT);
    groupNameAttr = conf.get(GROUP_NAME_ATTR_KEY, GROUP_NAME_ATTR_DEFAULT);

    int dirSearchTimeout = conf.getInt(DIRECTORY_SEARCH_TIMEOUT, DIRECTORY_SEARCH_TIMEOUT_DEFAULT);
    SEARCH_CONTROLS.setTimeLimit(dirSearchTimeout);
    // Limit the attributes returned to only those required to speed up the
    // search.
    // See HADOOP-10626 and HADOOP-12001 for more details.
    String[] returningAttributes;
    if (useOneQuery) {
      returningAttributes = new String[] { groupNameAttr, memberOfAttr };
    } else {
      returningAttributes = new String[] { groupNameAttr };
    }
    SEARCH_CONTROLS.setReturningAttributes(returningAttributes);
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
  
  //For testing
  public static void main(String[] args) throws NamingException, IOException{
    LdapGroupsMapping ldapGrpMapping = new LdapGroupsMapping();
    ldapGrpMapping.setConf(new Configuration());
    System.out.println(ldapGrpMapping.getUsers("ND-POC-ENG"));
    
  }

}
