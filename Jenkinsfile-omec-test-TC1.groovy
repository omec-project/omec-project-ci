// Copyright 2019-present Open Networking Foundation
//
// SPDX-License-Identifier: Apache-2.0
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

node("${params.buildNode}") {

  def basedir_config = '/home/jenkins/wo-config'

  def basedir_cp1 = '/home/jenkins'
  def basedir_dp1 = '/home/jenkins'
  def basedir_mme = '/home/jenkins'
  def basedir_hss = '/home/jenkins'
  def basedir_sgx = '/home/jenkins'


  def test_case = 'TC1_zmq_kni'
  def test_output_log = 'ng40'

  def base_path = '/var/log/cicd'
  def base_folder = 'test'
  def base_log_dir = "${base_path}/${base_folder}"

  def ngic_app = 'ngic-rtc'
  def mme_app = 'mme'
  def hss_app = 'c3po-hss'
  def sgx_app = 'c3po-sgx'

  def ngic_dir = "${base_log_dir}/${ngic_app}"
  def mme_dir = "${base_log_dir}/${mme_app}"
  def hss_dir = "${base_log_dir}/${hss_app}"
  def sgx_dir = "${base_log_dir}/${sgx_app}"

  def stdout_ext = '.stdout.log'
  def stderr_ext = '.stderr.log'

  // ngic-rtc logs
  def cp_stdout_log = "${ngic_dir}/" + "cp1_" + "${test_case}" + "${stdout_ext}"
  def cp_stderr_log = "${ngic_dir}/" + "cp1_" + "${test_case}" + "${stderr_ext}"

  def dp_stdout_log = "${ngic_dir}/" + "dp1_" + "${test_case}" + "${stdout_ext}"
  def dp_stderr_log = "${ngic_dir}/" + "dp1_" + "${test_case}" + "${stderr_ext}"

  // mme logs
  def mme_stdout_log = "${mme_dir}/" + "mme_app" + "${stdout_ext}"
  def mme_stderr_log = "${mme_dir}/" + "mme_app" + "${stderr_ext}"
  def s1ap_stdout_log = "${mme_dir}/" + "s1ap_app" + "${stdout_ext}"
  def s1ap_stderr_log = "${mme_dir}/" + "s1ap_app" + "${stderr_ext}"
  def s11_stdout_log = "${mme_dir}/" + "s11_app" + "${stdout_ext}"
  def s11_stderr_log = "${mme_dir}/" + "s11_app" + "${stderr_ext}"
  def s6a_stdout_log = "${mme_dir}/" + "s6a_app" + "${stdout_ext}"
  def s6a_stderr_log = "${mme_dir}/" + "s6a_app" + "${stderr_ext}"

  // c3po-hss logs
  def hss_stdout_log = "${hss_dir}/" + "hss" + "${stdout_ext}"
  def hss_stderr_log = "${hss_dir}/" + "hss" + "${stderr_ext}"

  // c3po-sgx logs
  def ctf_stdout_log = "${sgx_dir}/" + "ctf" + "${stdout_ext}"
  def ctf_stderr_log = "${sgx_dir}/" + "ctf" + "${stderr_ext}"

  def cdf_stdout_log = "${sgx_dir}/" + "cdf" + "${stdout_ext}"
  def cdf_stderr_log = "${sgx_dir}/" + "cdf" + "${stderr_ext}"

  def router_monitor_stdout_log = "${sgx_dir}/" + "router_monitor" + "${stdout_ext}"
  def router_monitor_stderr_log = "${sgx_dir}/" + "router_monitor" + "${stderr_ext}"

  def in_queue_router_stdout_log = "${sgx_dir}/" + "in_queue_router" + "${stdout_ext}"
  def in_queue_router_stderr_log = "${sgx_dir}/" + "in_queue_router" + "${stderr_ext}"

  def out_queue_router_stdout_log = "${sgx_dir}/" + "out_queue_router" + "${stdout_ext}"
  def out_queue_router_stderr_log = "${sgx_dir}/" + "out_queue_router" + "${stderr_ext}"

  def kms_stdout_log = "${sgx_dir}/" + "kms" + "${stdout_ext}"
  def kms_stderr_log = "${sgx_dir}/" + "kms" + "${stderr_ext}"

  def dealer_stdout_log = "${sgx_dir}/" + "dealer" + "${stdout_ext}"
  def dealer_stderr_log = "${sgx_dir}/" + "dealer" + "${stderr_ext}"

  def dealer_out_stdout_log = "${sgx_dir}/" + "dealer_out" + "${stdout_ext}"
  def dealer_out_stderr_log = "${sgx_dir}/" + "dealer_out" + "${stderr_ext}"

  def ng40_log_workspace_dir = "ng40"


  // To check SGX-DP connection.
  def mrenclave = ''
  def mrsigner = ''

  timeout (60) {
    try {
      stage("VMs check") {
        timeout(1) {
          waitUntil {
            running_vms = sh returnStdout: true, script: """
            virsh list --all
            """
            echo "Running VMs: ${running_vms}"
            return true
          }
        }
        timeout(1) {
          waitUntil {
            running_vms = sh returnStdout: true, script: """
            virsh list --all | grep "c3po-mme1\\|c3po-hss1\\|ngic-cp1\\|ngic-dp1\\|c3po-dbn1" | grep -i running | wc -l
            """
            echo "Running VMs: ${running_vms}"
            return running_vms.toInteger() == 5
          }
        }
        // Clean all logs
        sh returnStdout: true, script: """
        ssh ngic-cp1 '
            if [ ! -d "${ngic_dir}" ]; then mkdir -p ${ngic_dir}; fi
            rm -fr ${ngic_dir}/*
            '
        """
        sh returnStdout: true, script: """
        ssh ngic-dp1 '
            if [ ! -d "${ngic_dir}" ]; then mkdir -p ${ngic_dir}; fi
            rm -fr ${ngic_dir}/*
            '
        """
        sh returnStdout: true, script: """
        ssh c3po-mme1 '
            if [ ! -d "${mme_dir}" ]; then mkdir -p ${mme_dir}; fi
            rm -fr ${mme_dir}/*
            '
        """
        sh returnStdout: true, script: """
        ssh c3po-hss1 '
            if [ ! -d "${hss_dir}" ]; then mkdir -p ${hss_dir}; fi
            rm -fr ${hss_dir}/*
            '
        """
        sh returnStdout: true, script: """
        ssh sgx-kms-cdr '
            if [ ! -d "${sgx_dir}" ]; then mkdir -p ${sgx_dir}; fi
            rm -fr ${sgx_dir}/*
        '
        """

        sh returnStdout: true, script: """
        ssh ng40@ilnperf7 '
            cd /home/ng40/config/ng40cvnf/testlist/log
        '
        """

      }
      stage("c3po-ctf/cdf") {
        sh returnStdout: true, script: """
        ssh sgx-kms-cdr '
            if pgrep ctf; then pkill ctf; fi
            if pgrep cdf; then pkill cdf; fi
            rm -f /tmp/*.csv
            '
        """
        timeout(2) {
          waitUntil {
            sgx_output = sh returnStdout: true, script: """
            ssh sgx-kms-cdr '
                cd ${basedir_sgx}/c3po/ctf
                stdbuf -o0 ./bin/ctf -j conf/ctf.json 2>${ctf_stderr_log} 1>${ctf_stdout_log} &

                sleep 5

                cd ${basedir_sgx}/c3po/cdf
                stdbuf -o0 ./bin/cdf -f conf/cdf.conf 2>${cdf_stderr_log} 1>${cdf_stdout_log} &
                '

            ssh sgx-kms-cdr 'pgrep -l ctf || (cat ${ctf_stderr_log} && cat ${ctf_stdout_log} && exit 1)'
            ssh sgx-kms-cdr 'pgrep -l cdf || (cat ${cdf_stderr_log} && cat ${cdf_stdout_log} && exit 1)'
            """
            echo "${sgx_output}"
            return true
          }
        }
        sh returnStdout: true, script: """ssh sgx-kms-cdr 'pgrep ctf'"""
        sh returnStdout: true, script: """ssh sgx-kms-cdr 'pgrep cdf'"""
      }
      stage("c3po-router") {
        sh returnStdout: true, script: """
        ssh sgx-kms-cdr '
            if pgrep -f start_and__monitor\\.py; then pkill -f start_and__monitor\\.py; fi
            if pgrep -f in_queue_router\\.py; then pkill -f in_queue_router\\.py; fi
            if pgrep -f out_queue_router\\.py; then pkill -f out_queue_router\\.py; fi
            if pgrep -f cdr_slave_streamer_device\\.py; then pkill -f cdr_slave_streamer_device\\.py; fi
            rm -fr ${basedir_sgx}/c3po/sgxcdr/router/ipc
            '
        """
        timeout(10) {
          waitUntil {
            router_output = sh returnStdout: true, script: """
            ssh sgx-kms-cdr '
                cd ${basedir_sgx}/c3po/sgxcdr/router
                stdbuf -o0 python start_and__monitor.py 2>${router_monitor_stderr_log} 1>${router_monitor_stdout_log} &

                sleep 2

                cd ${basedir_sgx}/c3po/sgxcdr/router
                stdbuf -o0 python in_queue_router.py 2>${in_queue_router_stderr_log} 1>${in_queue_router_stdout_log} &

                sleep 2

                cd ${basedir_sgx}/c3po/sgxcdr/router
                stdbuf -o0 python out_queue_router.py 2>${out_queue_router_stderr_log} 1>${out_queue_router_stdout_log} &
                '

            ssh sgx-kms-cdr 'pgrep -a -fl start_and__monitor\\.py || (cat ${router_monitor_stderr_log} && cat ${router_monitor_stdout_log} && exit 1)'
            ssh sgx-kms-cdr 'pgrep -a -fl in_queue_router\\.py || (cat ${in_queue_router_stderr_log} && cat /${in_queue_router_stdout_log} && exit 1)'
            ssh sgx-kms-cdr 'pgrep -a -fl out_queue_router\\.py || (cat ${out_queue_router_stderr_log} && cat ${out_queue_router_stdout_log} & exit 1)'
            """
            echo "${router_output}"
            return true
          }
        }
        sh returnStdout: true, script: """ssh sgx-kms-cdr 'pgrep -f start_and__monitor\\.py'"""
        sh returnStdout: true, script: """ssh sgx-kms-cdr 'pgrep -f in_queue_router\\.py'"""
        sh returnStdout: true, script: """ssh sgx-kms-cdr 'pgrep -f out_queue_router\\.py'"""
      }
      stage("c3po-sgxcdr") {
        sh returnStdout: true, script: """
        ssh sgx-kms-cdr '
            if pgrep kms; then pkill kms; fi
            if pgrep -x dealer; then pkill -x dealer; fi
            if pgrep -x dealer-out; then pkill -x dealer-out; fi
            # Clean cdr directory from other possible <Identity>_<MRENCLAVE>.csv files to simplify later tests.
            rm -fr ${basedir_sgx}/c3po/sgxcdr/dealer-out/cdr/*
            '
        """
        timeout(3) {
          waitUntil {
            sgxcdr_output = sh returnStdout: true, script: """
            ssh sgx-kms-cdr '
                cd ${basedir_sgx}/c3po/sgxcdr/kms
                stdbuf -o0 ./kms -j conf/kms.json 2>${kms_stderr_log} 1>${kms_stdout_log} &

                sleep 10

                cd ${basedir_sgx}/c3po/sgxcdr/dealer
                stdbuf -o0 ./dealer -j conf/dealer.json 2>${dealer_stderr_log} 1>${dealer_stdout_log} &

                sleep 5

                cd ${basedir_sgx}/c3po/sgxcdr/dealer-out
                stdbuf -o0 ./dealer-out -j conf/dealer.json 2>${dealer_out_stderr_log} 1>${dealer_out_stdout_log} &
                '

            ssh sgx-kms-cdr 'pgrep -l kms || (cat ${kms_stderr_log} && cat ${kms_stdout_log} && exit 1)'
            ssh sgx-kms-cdr 'pgrep -xl dealer || (cat ${dealer_stderr_log} && cat ${dealer_stdout_log} && exit 1)'
            ssh sgx-kms-cdr 'pgrep -xl dealer-out || (cat ${dealer_out_stderr_log} && cat ${dealer_out_stdout_log} && exit 1)'
            """
            echo "${sgxcdr_output}"
            return true
          }
          // Fetch MRENCLAVE/MRSIGNER from dealer with -x flag.
          waitUntil {
            keys = sh returnStdout: true, script: """
                ssh sgx-kms-cdr '
                cd ${basedir_sgx}/c3po/sgxcdr/dealer
                output=(\$(./dealer -j conf/dealer.json -x | sed -n \"s/MR.* ://p\"))

                MRENCLAVE=\${output[0]}
                MRSIGNER=\${output[1]}

                echo \$MRENCLAVE
                echo \$MRSIGNER
               '
            """
            //echo "${keys}"
            (mrenclave, mrsigner) = keys.trim().split("\n")
            echo "${mrenclave}"
            echo "${mrsigner}"
            return true
          }
        }
        sh returnStdout: true, script: """ssh sgx-kms-cdr 'pgrep kms'"""
        sh returnStdout: true, script: """ssh sgx-kms-cdr 'pgrep -x dealer'"""
        sh returnStdout: true, script: """ssh sgx-kms-cdr 'pgrep -x dealer-out'"""
      }
      stage("c3po-dbn1") {
        timeout(2) {
          c3po_dbn1_output = sh returnStdout: true, script: """
          ssh c3po-dbn1 '
              /home/c3po/db_docs/data_provisioning_users.sh 208014567891234 1122334455 internet 465B5CE8B199B49FAA5F0A2EE238A6BC 25 10.0.4.60 d4416644f6154936193433dd20a0ace0
              '
          """
          echo "${c3po_dbn1_output}"
        }
      }
      stage("c3po-hss1") {
        sh returnStdout: true, script: """ssh c3po-hss1 'if pgrep -f [h]ss; then pkill -f [h]ss; fi'"""
        timeout(2) {
          waitUntil {
            c3po_hss1_output = sh returnStdout: true, script: """
            ssh c3po-hss1 '
                cd ${basedir_hss}/c3po/hss
                stdbuf -o0 ./bin/hss -j conf/hss.json 2>${hss_stderr_log} 1>${hss_stdout_log} &
                '
            sleep 5;
            ssh c3po-hss1 'pgrep -fl [h]ss || (cat ${hss_stderr_log} && cat ${hss_stdout_log} && exit 1)'
            """
            echo "${c3po_hss1_output}"
            return true
          }
        }
        sh returnStdout: true, script: """ssh c3po-hss1 'pgrep -fl [h]ss'"""
      }
      stage("c3po-mme1") {
        c3po_mme_kill_output = sh returnStdout: true, script: """
        ssh c3po-mme1 '
            ps -e | grep -P "(mme|s11|s1ap|s6a)-app" | grep -v grep || echo "no running process found"
            if pgrep -f [m]me-app; then pkill -f [m]me-app; fi
            sleep 1
            if pgrep -f [s]1ap-app; then pkill -f [s]1ap-app; fi
            sleep 1
            if pgrep -f [s]11-app; then pkill -f [s]11-app; fi
            sleep 1
            if pgrep -f [s]6a-app; then pkill -f [s]6a-app; fi
            sleep 5
            ps -e | grep -P "(mme|s11|s1ap|s6a)-app" | grep -v grep && exit 1 || echo "no running process found"
            '
        """
        echo "${c3po_mme_kill_output}"
        timeout(2) {
          waitUntil {
            c3po_mme1_output = sh returnStdout: true, script: """
            ssh c3po-mme1 '
                cd ${basedir_mme}/${params.mmeRepo}/target
                export LD_LIBRARY_PATH=./lib

                stdbuf -o0 ./bin/mme-app 2>${mme_stderr_log} 1>${mme_stdout_log} &
                sleep 2
                stdbuf -o0 ./bin/s1ap-app 2>${s1ap_stderr_log} 1>${s1ap_stdout_log} &
                sleep 2
                stdbuf -o0 ./bin/s11-app 2>${s11_stderr_log} 1>${s11_stdout_log} &
                sleep 2
                stdbuf -o0 ./bin/s6a-app 2>${s6a_stderr_log} 1>${s6a_stdout_log} &
                sleep 2
                '
            ssh c3po-mme1 'pgrep -fl [m]me-app || (cat ${mme_stderr_log} && cat ${mme_stdout_log} && exit 1)'
            ssh c3po-mme1 'pgrep -fl [s]1ap-app || (cat ${s1ap_stderr_log} && cat ${s1ap_stdout_log} && exit 1)'
            ssh c3po-mme1 'pgrep -fl [s]11-app || (cat ${s11_stderr_log} && cat ${s11_stdout_log} && exit 1)'
            ssh c3po-mme1 'pgrep -fl [s]6a-app || (cat ${s6a_stderr_log} && cat ${s6a_stdout_log} && exit 1)'
            """
            echo "${c3po_mme1_output}"
            return true
          }
        }
        sh returnStdout: true, script: """
        ssh c3po-mme1 'pgrep -f [m]me-app && pgrep -f [s]1ap-app && pgrep -f [s]11-app && pgrep -fl [s]6a-app'
        """
      }
      stage("make CP") {
        timeout(3) {
          sh returnStdout: true, script: """
          ssh ngic-cp1 '
              if pgrep -f [n]gic_controlplane; then pkill -f [n]gic_controlplane; fi
              cp -f ${basedir_cp1}/ngic-rtc/.ci/tc1/config/cp_config.cfg ${basedir_cp1}/ngic-rtc/config/cp_config.cfg
              cp -f ${basedir_cp1}/ngic-rtc/.ci/tc1/config/interface.cfg ${basedir_cp1}/ngic-rtc/config/interface.cfg

              cp -f ${basedir_cp1}/ngic-rtc/.ci/tc1/cp/custom-cp.mk ${basedir_cp1}/ngic-rtc/cp/custom-cp.mk
              cd ${basedir_cp1}/ngic-rtc/cp && source ../setenv.sh && make clean && make -j\$(nproc)
              '
          """
        }
      }
      stage("make DP") {
        timeout(3) {
          sh returnStdout: true, script: """
          ssh ngic-dp1 '
              if pgrep -f [n]gic_dataplane; then pkill -f [n]gic_dataplane; fi
              cp -f ${basedir_dp1}/ngic-rtc/.ci/tc1/config/dp_config.cfg ${basedir_dp1}/ngic-rtc/config/dp_config.cfg
              cp -f ${basedir_dp1}/ngic-rtc/.ci/tc1/config/interface.cfg ${basedir_dp1}/ngic-rtc/config/interface.cfg
              rm -f ${basedir_dp1}/ngic-rtc/config/static_arp.cfg
              '
          """
          waitUntil {
            // Apply MRENCLAVE/MRSIGNER from dealer to ngic-rtc/config/interface.cfg
            ngic_rtc_output = sh returnStdout: true, script: """
            ssh ngic-dp1 '
                cd ${basedir_dp1}/ngic-rtc/config

                MRENCLAVE=${mrenclave}
                MRSIGNER=${mrsigner}

                sed -i \"s/dealer_in_mrenclave *= *.*/dealer_in_mrenclave = \${MRENCLAVE}/\" interface.cfg
                sed -i \"s/dealer_in_mrsigner *= *.*/dealer_in_mrsigner = \${MRSIGNER}/\" interface.cfg

                grep -P \"dealer_in_mrenclave|dealer_in_mrsigner\" interface.cfg
                '
            """
            echo "${ngic_rtc_output}"
            return true
          }
          sh returnStdout: true, script: """
          ssh ngic-dp1 '
              cp -f ${basedir_dp1}/ngic-rtc/.ci/tc1/dp/custom-dp.mk ${basedir_dp1}/ngic-rtc/dp/custom-dp.mk
              cd ${basedir_dp1}/ngic-rtc/dp && source ../setenv.sh && make clean && make -j\$(nproc)
              '
          """
        }
      }
      stage("ZMQ [push/pull] start") {
        timeout(3) {
          waitUntil {
            ngic_rtc_zmq_output = sh returnStdout: true, script: """
            ssh ngic-cp1 'cd ${basedir_cp1}/ngic-rtc/dev_scripts && (./stop-ZMQ_Streamer.sh > /dev/null 2>&1)'
            sleep 2
            ssh ngic-cp1 'cd ${basedir_cp1}/ngic-rtc/dev_scripts && (nohup ./start-ZMQ_Streamer.sh > /dev/null 2>&1 &)'
            sleep 5
            """
            echo "${ngic_rtc_zmq_output}"
            return true
          }
        }
        sh returnStdout: true, script: """ssh ngic-cp1 'pgrep -f req_streamer_dev'"""
        sh returnStdout: true, script: """ssh ngic-cp1 'pgrep -f resp_streamer_dev'"""
      }
      stage("run ngic-rtc") {
        timeout(3) {
          waitUntil {
            ngic_rtc_run_output = sh returnStdout: true, script: """
            ssh ngic-dp1 '
                cd ${basedir_dp1}/ngic-rtc/dp
                stdbuf -o0 ./run.sh 1>${dp_stdout_log} 2>${dp_stderr_log} &

                date +"%Y-%m-%d %H:%M:%S.%3N"
                sleep 40;
                date +"%Y-%m-%d %H:%M:%S.%3N"

                cd ${basedir_dp1}/ngic-rtc/kni_ifcfg && ./kni-SGIdevcfg.sh
                cd ${basedir_dp1}/ngic-rtc/kni_ifcfg && ./kni-S1Udevcfg.sh
                '

            sleep 10

            ssh ngic-cp1 '
                cd ${basedir_cp1}/ngic-rtc/cp
                stdbuf -o0 ./run.sh 1>${cp_stdout_log} 2>${cp_stderr_log} &
                '

            sleep 5

            ssh ngic-cp1 'pgrep -fl [n]gic_controlplane || (cat ${cp_stderr_log} && cat ${cp_stdout_log})'
            ssh ngic-dp1 'pgrep -fl [n]gic_dataplane || (cat ${dp_stderr_log} && cat ${dp_stdout_log})'
            """
            echo "${ngic_rtc_run_output}"
            return true
          }
        }
      }
      stage("check processes") {
        check_process_output = sh returnStdout: true, script: """
        # ctf / cdf
        ssh sgx-kms-cdr 'pgrep -l ctf && pgrep -l cdf'
        # router
        ssh sgx-kms-cdr 'pgrep -a -fl start_and__monitor\\.py && pgrep -a -fl in_queue_router\\.py && pgrep -a -fl out_queue_router\\.py'
        # kms & dealer & dealer-out
        ssh sgx-kms-cdr 'pgrep -l kms && pgrep -xl dealer && pgrep -xl dealer-out'
        # hss
        ssh c3po-hss1 'pgrep -fl [h]ss'
        # all mme processes
        ssh c3po-mme1 'pgrep -fl [m]me-app && pgrep -fl [s]1ap-app && pgrep -fl [s]11-app && pgrep -fl [s]6a-app'
        # all ZMQ streamers
        ssh ngic-cp1 'pgrep -a -fl [r]eq_streamer_dev && pgrep -a -fl [r]esp_streamer_dev'
        # CP & DP
        ssh ngic-cp1 'pgrep -fl [n]gic_controlplane'
        ssh ngic-dp1 'pgrep -fl [n]gic_dataplane'
        """
        echo "${check_process_output}"
      }
      stage("test ng40") {
        timeout(10) {
          try {
              sh returnStdout: true, script: """
              ssh ng40@ilnperf7 'ng40forcecleanup all && cd config/ng40cvnf/testlist && ng40test run.ntl'
              """
          } finally {
            //Get testcase log filename.
            testcase_output_filename = sh returnStdout: true, script: """
            ssh ng40@ilnperf7 'ls -Art /home/ng40/config/ng40cvnf/testlist/log | tail -n 1'
            """
            testcase_output_filename = testcase_output_filename.trim()

            //Display testcase log.
            testcase_output_log = sh returnStdout: true, script: """
            ssh ng40@ilnperf7 'cat /home/ng40/config/ng40cvnf/testlist/log/${testcase_output_filename}'
            """
            echo "=========== Testcase Log ==========="
            echo "${testcase_output_log}"

            //Get 4G_M2AS_UDP log filename.
            FOURG_M2AS_UDP_output_filename = sh returnStdout: true, script: """
            ssh ng40@ilnperf7 'ls -Art /home/ng40/config/ng40cvnf/ran/log | tail -n 1'
            """
            FOURG_M2AS_UDP_output_filename = FOURG_M2AS_UDP_output_filename.trim()

            //Display 4G_M2AS_UDP log.
            FOURG_M2AS_UDP_output_log = sh returnStdout: true, script: """
            ssh ng40@ilnperf7 'cat /home/ng40/config/ng40cvnf/ran/log/${FOURG_M2AS_UDP_output_filename}'
            """
            echo "=========== 4G_M2AS_UDP Log ==========="
            echo "${FOURG_M2AS_UDP_output_log}"
          }
        }
      }
      stage("test sgx-dp") {
        timeout(time: 20, unit: 'SECONDS')  {
          // Compare Identity file in DP log with file in dealer-out/cdr directory.
          waitUntil {
            // '.csv' is stripped for cenvenience, cdr_filename info is taken without '_<MRENCLAVE>.csv'
            dp_filename = sh returnStdout: true, script: """
            ssh ngic-dp1 'cat ${dp_stdout_log} | sed -n -e \"s/^.*Identify\\/Filename is *//p\" | sed \"s/.\\{4\\}\$//\"'
            """
            dp_filename = dp_filename.trim()
            echo "DP log: DP Identify/Filename: " + "${dp_filename}"

            /* Check router monitor output
            router_filename = sh returnStdout: true, script: """
            ssh sgx-kms-cdr 'grep -o -P "^[0-9]+\\.csv" ${router_monitor_stdout_log} | cut -c 2- | sort | uniq'
            """
            router_filename = router_filename.trim()
            echo "router log: identity read from DP: " + "$router_filename"
            */

            cdr_filename = sh returnStdout: true, script: """
            ssh sgx-kms-cdr 'ls ${basedir_sgx}/c3po/sgxcdr/dealer-out/cdr | grep -oP "\\d+(?=_.*\\.csv)" | uniq'
            """
            cdr_filename = cdr_filename.trim()
            echo "cdr directory: identity: " + "$cdr_filename"

            return dp_filename == cdr_filename
          }
        }
      }
      /*
      stage("Kill apps") {
        sh returnStdout: true, script: """
        ssh ngic-cp1 'if pgrep -f [n]gic_controlplane; then pkill -f [n]gic_controlplane; fi'
        ssh ngic-dp1 'if pgrep -f [n]gic_dataplane; then pkill -f [n]gic_dataplane; fi'
        ssh c3po-hss1 'if pgrep -f [h]ss; then pkill -f [h]ss; fi'
        ssh c3po-mme1 'if pgrep -f [m]me-app; then pkill -f [m]me-app; fi'
        ssh c3po-mme1 'if pgrep -f [s]1ap-app; then pkill -f [s]1ap-app; fi'
        ssh c3po-mme1 'if pgrep -f [s]11-app; then pkill -f [s]11-app; fi'
        ssh c3po-mme1 'if pgrep -f [s]6a-app; then pkill -f [s]6a-app; fi'
        """
      }
      */
      currentBuild.result = 'SUCCESS'
    } catch (err) {
      currentBuild.result = 'FAILURE'
    } finally {
      sh returnStdout: true, script: """
      rm -fr *
      """

      try {
        sh returnStdout: true, script: """
        scp -r ngic-cp1:${ngic_dir} .
        """
      } catch (err) {}

      try {
        sh returnStdout: true, script: """
        scp -r ngic-dp1:${ngic_dir} .
        """
      } catch (err) {}

      try {
        sh returnStdout: true, script: """
        scp -r c3po-mme1:${mme_dir} .
        """
      } catch (err) {}

      try {
        sh returnStdout: true, script: """
        scp -r c3po-hss1:${hss_dir} .
        """
      } catch (err) {}

      try {
        sh returnStdout: true, script: """
        scp -r sgx-kms-cdr:${sgx_dir} .
        """
      } catch (err) {}

      try {
        sh returnStdout: true, script: """
        mkdir ${ng40_log_workspace_dir}
        scp ng40@ilnperf7:/home/ng40/config/ng40cvnf/testlist/log/${testcase_output_filename} ${ng40_log_workspace_dir}/
        scp ng40@ilnperf7:/home/ng40/config/ng40cvnf/ran/log/${FOURG_M2AS_UDP_output_filename} ${ng40_log_workspace_dir}/
        """
      } catch (err) {}

      archiveArtifacts artifacts: "**/*${stdout_ext}", allowEmptyArchive: true
      archiveArtifacts artifacts: "**/*${stderr_ext}", allowEmptyArchive: true
      archiveArtifacts artifacts: "${test_output_log}/*", allowEmptyArchive: true
    }
    echo "RESULT: ${currentBuild.result}"
  }
}
