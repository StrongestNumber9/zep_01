#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

bin="$(dirname "${BASH_SOURCE-$0}")"
bin="$(cd "${bin}">/dev/null; pwd)"

function usage() {
    echo "usage) $0 -p <port> -r <intp_port> -d <interpreter dir to load> -g <interpreter group name>"
}

# pre-requisites for checking that we're running in container
if [ -f /proc/self/cgroup ] && [ -n "$(command -v getent)" ]; then
    # checks if we're running in container...
    if awk -F: '/cpu/ && $3 ~ /^\/$/{ c=1 } END { exit c }' /proc/self/cgroup; then
        # Check whether there is a passwd entry for the container UID
        myuid="$(id -u)"
        mygid="$(id -g)"
        # turn off -e for getent because it will return error code in anonymous uid case
        set +e
        uidentry="$(getent passwd "$myuid")"
        set -e

        # If there is no passwd entry for the container UID, attempt to create one
        if [ -z "$uidentry" ] ; then
            if [ -w /etc/passwd ] ; then
                echo "zeppelin:x:$myuid:$mygid:anonymous uid:$ZEPPELIN_HOME:/bin/false" >> /etc/passwd
            else
                echo "Container ENTRYPOINT failed to add passwd entry for anonymous UID"
            fi
        fi
    fi
fi

while getopts "hc:p:r:i:d:v:u:g:" o; do
    case ${o} in
        h)
            usage
            exit 0
            ;;
        d)
            INTERPRETER_DIR=${OPTARG}
            ;;
        c)
            CALLBACK_HOST=${OPTARG} # This will be used callback host
            ;;
        p)
            PORT=${OPTARG} # This will be used for callback port
            ;;
        r)
            INTP_PORT=${OPTARG} # This will be used for interpreter process port
            ;;
        i)
            INTP_GROUP_ID=${OPTARG} # This will be used for interpreter group id
            ;;
        v)
            . "${bin}/common.sh"
            getZeppelinVersion
            ;;
        u)
            ZEPPELIN_IMPERSONATE_USER="${OPTARG}"
            ;;
        g)
            INTERPRETER_SETTING_NAME=${OPTARG}
            ;;
        esac
done


if [ -z "${PORT}" ] || [ -z "${INTERPRETER_DIR}" ]; then
    usage
    exit 1
fi

. "${bin}/common.sh"

check_java_version

ZEPPELIN_INTERPRETER_API_JAR=$(find "${ZEPPELIN_HOME}/interpreter" -name 'zep_01.zeppelin-interpreter-shaded-*.jar')
ZEPPELIN_INTP_CLASSPATH+=":${CLASSPATH}:${ZEPPELIN_INTERPRETER_API_JAR}"

# This is a hack and should be fixed later. Add test classes for unittest
if [[ -d "${ZEPPELIN_HOME}/zeppelin-zengine/target/test-classes" ]]; then
  ZEPPELIN_INTP_CLASSPATH+=":${ZEPPELIN_HOME}/zeppelin-zengine/target/test-classes"
  addJarInDirForIntp "${ZEPPELIN_HOME}/zeppelin-zengine/target/test-classes"
fi

HOSTNAME=$(hostname)
ZEPPELIN_SERVER=com.teragrep.zep_01.interpreter.remote.RemoteInterpreterServer

INTERPRETER_ID=$(basename "${INTERPRETER_DIR}")
addJarInDirForIntp "${INTERPRETER_DIR}"

ZEPPELIN_PID="${ZEPPELIN_PID_DIR}/zeppelin-interpreter-${INTP_GROUP_ID}-${ZEPPELIN_IDENT_STRING}-${HOSTNAME}-${PORT}.pid"

if [[ "${ZEPPELIN_INTERPRETER_LAUNCHER}" == "yarn" ]]; then
    # {LOG_DIRS} is env name in yarn container which point to the log dirs of container
    # split the log dirs to array and use the first one
    IFS=','
    read -ra LOG_DIRS_ARRAY <<< "${LOG_DIRS}"
    ZEPPELIN_LOG_DIR=${LOG_DIRS_ARRAY[0]}
fi

ZEPPELIN_LOGFILE="${ZEPPELIN_LOG_DIR}/zeppelin-interpreter-${INTERPRETER_GROUP_ID}-"

if [[ -z "$ZEPPELIN_IMPERSONATE_CMD" ]]; then
    if [[ "${INTERPRETER_ID}" != "spark" || "$ZEPPELIN_IMPERSONATE_SPARK_PROXY_USER" == "false" ]]; then
        ZEPPELIN_IMPERSONATE_RUN_CMD=("ssh" "${ZEPPELIN_IMPERSONATE_USER}@localhost")
    fi
else
    ZEPPELIN_IMPERSONATE_RUN_CMD=$(eval "echo ${ZEPPELIN_IMPERSONATE_CMD} ")
fi


if [[ -n "$ZEPPELIN_IMPERSONATE_USER" ]]; then
    ZEPPELIN_LOGFILE+="${ZEPPELIN_IMPERSONATE_USER}-"
fi
ZEPPELIN_LOGFILE+="${ZEPPELIN_IDENT_STRING}-${HOSTNAME}.log"
JAVA_INTP_OPTS+=" -Dzeppelin.log.file=${ZEPPELIN_LOGFILE}"

if [[ ! -d "${ZEPPELIN_LOG_DIR}" ]]; then
  echo "Log dir doesn't exist, create ${ZEPPELIN_LOG_DIR}"
  mkdir -p "${ZEPPELIN_LOG_DIR}"
fi

# set spark related env variables
if [[ "${INTERPRETER_ID}" == "spark" ]]; then

  # run kinit
  if [[ -n "${ZEPPELIN_SERVER_KERBEROS_KEYTAB}" ]] && [[ -n "${ZEPPELIN_SERVER_KERBEROS_PRINCIPAL}" ]]; then
    kinit -kt "${ZEPPELIN_SERVER_KERBEROS_KEYTAB}" "${ZEPPELIN_SERVER_KERBEROS_PRINCIPAL}"
  fi
  if [[ -n "${SPARK_HOME}" ]]; then
    export SPARK_SUBMIT="${SPARK_HOME}/bin/spark-submit"
    SPARK_APP_JAR="$(ls "${ZEPPELIN_HOME}"/interpreter/spark/zep_01.spark-interpreter*.jar)"
    # This will evantually passes SPARK_APP_JAR to classpath of SparkIMain
    ZEPPELIN_INTP_CLASSPATH+=":${SPARK_APP_JAR}"

    py4j=("${SPARK_HOME}"/python/lib/py4j-*-src.zip)
    # pick the first match py4j zip - there should only be one
    export PYTHONPATH="$SPARK_HOME/python/:$PYTHONPATH"
    export PYTHONPATH="${py4j[0]}:$PYTHONPATH"
  else
    # add Hadoop jars into classpath
    if [[ -n "${HADOOP_HOME}" ]]; then
      # Apache
      addEachJarInDirRecursiveForIntp "${HADOOP_HOME}/share"

      # CDH
      addJarInDirForIntp "${HADOOP_HOME}"
      addJarInDirForIntp "${HADOOP_HOME}/lib"
    fi

    addJarInDirForIntp "${INTERPRETER_DIR}/dep"

    py4j=("${ZEPPELIN_HOME}"/interpreter/spark/pyspark/py4j-*-src.zip)
    # pick the first match py4j zip - there should only be one
    PYSPARKPATH="${ZEPPELIN_HOME}/interpreter/spark/pyspark/pyspark.zip:${py4j[0]}"

    if [[ -z "${PYTHONPATH}" ]]; then
      export PYTHONPATH="${PYSPARKPATH}"
    else
      export PYTHONPATH="${PYTHONPATH}:${PYSPARKPATH}"
    fi
    unset PYSPARKPATH
    export SPARK_CLASSPATH+=":${ZEPPELIN_INTP_CLASSPATH}"
  fi

  if [[ -n "${HADOOP_CONF_DIR}" ]] && [[ -d "${HADOOP_CONF_DIR}" ]]; then
    ZEPPELIN_INTP_CLASSPATH+=":${HADOOP_CONF_DIR}"
    export HADOOP_CONF_DIR=${HADOOP_CONF_DIR}
  else
    # autodetect HADOOP_CONF_HOME by heuristic
    if [[ -n "${HADOOP_HOME}" ]] && [[ -z "${HADOOP_CONF_DIR}" ]]; then
      if [[ -d "${HADOOP_HOME}/etc/hadoop" ]]; then
        export HADOOP_CONF_DIR="${HADOOP_HOME}/etc/hadoop"
      elif [[ -d "/etc/hadoop/conf" ]]; then
        export HADOOP_CONF_DIR="/etc/hadoop/conf"
      fi
    fi
  fi
elif [[ "${INTERPRETER_ID}" == "hbase" ]]; then
  if [[ -n "${HBASE_CONF_DIR}" ]]; then
    ZEPPELIN_INTP_CLASSPATH+=":${HBASE_CONF_DIR}"
  elif [[ -n "${HBASE_HOME}" ]]; then
    ZEPPELIN_INTP_CLASSPATH+=":${HBASE_HOME}/conf"
  else
    echo "HBASE_HOME and HBASE_CONF_DIR are not set, configuration might not be loaded"
  fi
fi

if [[ -n "$ZEPPELIN_IMPERSONATE_USER" ]]; then
  if [[ "${INTERPRETER_ID}" != "spark" || "$ZEPPELIN_IMPERSONATE_SPARK_PROXY_USER" == "false" ]]; then
    suid="$(id -u "${ZEPPELIN_IMPERSONATE_USER}")"
    if [[ -n  "${suid}" || -z "${SPARK_SUBMIT}" ]]; then
       INTERPRETER_RUN_COMMAND+=("${ZEPPELIN_IMPERSONATE_RUN_CMD[@]}")
       if [[ -f "${ZEPPELIN_CONF_DIR}/zeppelin-env.sh" ]]; then
           INTERPRETER_RUN_COMMAND+=("source" "${ZEPPELIN_CONF_DIR}/zeppelin-env.sh;")
       fi
    fi
  fi
fi

if [[ -n "${SPARK_SUBMIT}" ]]; then
  IFS=' ' read -r -a SPARK_SUBMIT_OPTIONS_ARRAY <<< "${SPARK_SUBMIT_OPTIONS}"
  IFS='|' read -r -a ZEPPELIN_SPARK_CONF_ARRAY <<< "${ZEPPELIN_SPARK_CONF}"
  if [[ "${ZEPPELIN_SPARK_YARN_CLUSTER}" == "true"  ]]; then
      INTERPRETER_RUN_COMMAND+=("${SPARK_SUBMIT}" "--class" "${ZEPPELIN_SERVER}" "--driver-java-options" "${JAVA_INTP_OPTS}" "${SPARK_SUBMIT_OPTIONS_ARRAY[@]}" "${ZEPPELIN_SPARK_CONF_ARRAY[@]}" "${SPARK_APP_JAR}" "${CALLBACK_HOST}" "${PORT}" "${INTP_GROUP_ID}" "${INTP_PORT}")
  else
      INTERPRETER_RUN_COMMAND+=("${SPARK_SUBMIT}" "--class" "${ZEPPELIN_SERVER}" "--driver-class-path" "${ZEPPELIN_INTP_CLASSPATH_OVERRIDES}:${ZEPPELIN_INTP_CLASSPATH}" "--driver-java-options" "${JAVA_INTP_OPTS}" "${SPARK_SUBMIT_OPTIONS_ARRAY[@]}" "${ZEPPELIN_SPARK_CONF_ARRAY[@]}" "${SPARK_APP_JAR}" "${CALLBACK_HOST}" "${PORT}" "${INTP_GROUP_ID}" "${INTP_PORT}")
  fi;
  exec "${INTERPRETER_RUN_COMMAND[@]}"
else
  IFS=' ' read -r -a JAVA_INTP_OPTS_ARRAY <<< "${JAVA_INTP_OPTS}"
  IFS=' ' read -r -a ZEPPELIN_INTP_MEM_ARRAY <<< "${ZEPPELIN_INTP_MEM}"
  INTERPRETER_RUN_COMMAND+=("${ZEPPELIN_RUNNER}" "${JAVA_INTP_OPTS_ARRAY[@]}" "${ZEPPELIN_INTP_MEM_ARRAY[@]}" "-cp" "${ZEPPELIN_INTP_CLASSPATH_OVERRIDES}:${ZEPPELIN_INTP_CLASSPATH}" "${ZEPPELIN_SERVER}" "${CALLBACK_HOST}" "${PORT}" "${INTP_GROUP_ID}" "${INTP_PORT}")
  if [[ -z "$ZEPPELIN_IMPERSONATE_CMD" ]]; then
    exec ${INTERPRETER_RUN_COMMAND[@]}
  else
    FIRST_ARG=${INTERPRETER_RUN_COMMAND[0]}
    STRINGIFIED_ARGS="${INTERPRETER_RUN_COMMAND[@]:1}"
    exec env ${FIRST_ARG} "${STRINGIFIED_ARGS}"
  fi
fi
