#!/bin/bash
set -x
CMD=$1


RETVAL=0
PROG="ead"
NAME="ead"

echo "CONF_DIR $CONF"
echo "EAD_HEAP_SIZE: ${EAD_HEAP_SIZE}"
echo "EAD_JAVA_OPTS: ${EAD_JAVA_OPTS}"
echo "EAD_PORT: ${EAD_PORT}"



export EAD_JAVA_OPTS="$CSD_JAVA_OPTS -Xmx${EAD_HEAP_SIZE}M ${EAD_JAVA_OPTS}"

if [ -z $EAD_HOME ] 
	then
	EAD_HOME=/opt/cloudera/parcels/JPMISEAD
fi

echo "The EAD HOME IS $EAD_HOME"

adjust_config() {

   echo "Adjusting program EAD.."
}

export EAD_XML_PATH="$CONF_DIR/hadoop_group_mapping.xml"


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
