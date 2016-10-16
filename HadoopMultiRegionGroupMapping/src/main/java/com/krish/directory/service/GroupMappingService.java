package com.krish.directory.service;

import org.apache.hadoop.fs.Path;

public interface GroupMappingService {
  
  public void setEadSchemaService(EadSchemaService schemaService);
  
  public void doSchemaUpdate();
  
  public void buildGroupMapping(Path groupMappingXml) throws Exception;

}
