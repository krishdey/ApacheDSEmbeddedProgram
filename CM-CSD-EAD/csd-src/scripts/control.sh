#!/bin/bash
CMD=$1

_term() { 
  echo "Caught SIGTERM signal!" 
  kill -TERM "$child" 2>/dev/null
}

trap _term SIGTERM

RETVAL=0
PROG="ead"
NAME="ead"

if [ -z $EAD_HOME ] 
	then
	EAD_HOME=/opt/cloudera/parcels/JPMISEAD
fi


adjust_config() {

   echo "Starting program EAD.."
}

case $CMD in
  (deploy)
  
   adjust_config

   ;;
  (start)
   
   adjust_config

   echo "Starting $PROG on" `hostname`
   $EAD_HOME/bin/ead.sh krish start
   child=$! 
   wait "$child"
   ;;
  (stop)
  
    echo "Shutting down $PROG on" `hostname`
	$EAD_HOME/bin/ead.sh stop

    pkill -P `pgrep -f "control.sh start"`
    ;;
  (*)
    echo "Don't understand [$CMD]"
    ;;
esac
