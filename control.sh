#!/bin/bash
set -x
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
	EAD_HOME=/Hadoop/git/CM-CSD-EAD/JPMISEAD-1.0.0/
fi

echo "The EAD HOME IS $EAD_HOME"

adjust_config() {

   echo "Adjusting program EAD.."
}

echo "The command is $CMD"
case $CMD in
  (deploy)
  adjust_config
   ;;
  
 (start)
   
   echo "Starting $PROG on" `hostname`
   rm -rf  $EAD_HOME/run/*
   exec $EAD_HOME/bin/ead.sh krish run
   child=$! 
   echo "The child is $child"
   wait "$child"
   ;;
 (stop)
  
    echo "Shutting down $PROG on" `hostname`
    exec $EAD_HOME/bin/ead.sh stop

    pkill -P `pgrep -f "control.sh start"`
    ;;
  (*)
    echo "Don't understand [$CMD]"
    ;;
esac
