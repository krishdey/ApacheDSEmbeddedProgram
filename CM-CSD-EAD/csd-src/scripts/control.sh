#!/bin/bash
set -x
CMD=$1


echo "CONF_DIR $CONF_DIR 3"
echo "EAD_HEAP_SIZE: ${EAD_HEAP_SIZE}"
echo "EAD_JAVA_OPTS: ${EAD_JAVA_OPTS}"
echo "EAD_PORT: ${EAD_PORT}"
echo "log4j directory: $EAD_LOG4J_DIR"


if [ -z $EAD_HOME ] 
	then
	EAD_HOME="/opt/cloudera/parcels/JPMISEAD"
fi

echo "The EAD HOME IS $EAD_HOME"

export EAD_JAVA_OPTS="$CSD_JAVA_OPTS -Xmx${EAD_HEAP_SIZE}M ${EAD_JAVA_OPTS}"

export EAD_XML_PATH="$CONF_DIR/hadoop_group_mapping.xml"
export EAD_LOG4J_PROPERTIES="file:$CONF_DIR/log4j.properties"
export EAD_LOG_DIR="$EAD_LOG4J_DIR"
export EAD_INSTANCES_DIR="${EAD_INSTANCES_DIR}"

echo "The command is $CMD"

case $CMD in
  (deploy)
     adjust_config
   ;;
  
 (start)
   echo "Starting $PROG on" `hostname`
   rm -rf  $EAD_HOME/run/*
   exec $EAD_HOME/bin/ead.sh krish run 
   ;;
  (*)
    echo "Don't understand [$CMD]"
    ;;
esac   
