// Copyright 2019-present Open Networking Foundation
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

node("${params.executorNode}") {

  def ghRepository = 'omec-project/ngic-rtc'

  def base_path = '/var/log/cicd'
  def base_folder = 'install'
  def base_log_dir = "${base_path}/${base_folder}"

  def action_inst = '_install'

  def stdout_ext = '.stdout.log'
  def stderr_ext = '.stderr.log'

  def cp_base_stdout_log = "cicd_cp1" + "${action_inst}${stdout_ext}"
  def cp_base_stderr_log = "cicd_cp1" + "${action_inst}${stderr_ext}"
  def dp_base_stdout_log = "cicd_dp1" + "${action_inst}${stdout_ext}"
  def dp_base_stderr_log = "cicd_dp1" + "${action_inst}${stderr_ext}"

  def cp_stdout_log = "${base_log_dir}/${cp_base_stdout_log}"
  def cp_stderr_log = "${base_log_dir}/${cp_base_stderr_log}"
  def dp_stdout_log = "${base_log_dir}/${dp_base_stdout_log}"
  def dp_stderr_log = "${base_log_dir}/${dp_base_stderr_log}"

  def install_path = '/home/jenkins'

  timeout (60) {
    try {

      echo "${params}"

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
            virsh list --all | grep "ngic-cp1\\|ngic-dp1" | grep -i running | wc -l
            """
            return running_vms.toInteger() == 2
          }
        }
        // Clean all logs
        sh returnStdout: true, script: """
        ssh ngic-cp1 '
            if [ ! -d "${base_log_dir}" ]; then mkdir -p ${base_log_dir}; fi
            rm -fr ${base_log_dir}/*
            '
        """
        sh returnStdout: true, script: """
        ssh ngic-dp1 '
            if [ ! -d "${base_log_dir}" ]; then mkdir -p ${base_log_dir}; fi
            rm -fr ${base_log_dir}/*
            '
        """
      }
      stage("Install DP") {
        timeout(20) {
          sh returnStdout: true, script: """
          ssh ngic-dp1 'if pgrep -f [n]gic_dataplane; then pkill -f [n]gic_dataplane; fi'
          """
          waitUntil {
            ngic_dp1_output = sh returnStdout: true, script: """
            ssh ngic-dp1 '
                cd ${install_path}
                rm -rf ngic-rtc
                git clone https://github.com/omec-project/ngic-rtc.git || exit 1
                cd ngic-rtc

                if [ ${params.ghprbGhRepository} = ${ghRepository} ]; then
                    git fetch origin pull/${params.ghprbPullId}/head:jenkins_test || exit 1
                    git checkout jenkins_test || exit 1
                    git log -1
                fi

                cp -f ${install_path}/wo-config/dp_config.cfg ${install_path}/ngic-rtc/config/dp_config.cfg
                cp -f ${install_path}/wo-config/interface.cfg ${install_path}/ngic-rtc/config/interface.cfg
                cp -f ${install_path}/wo-config/udp-ng-core_cfg.mk ${install_path}/ngic-rtc/config/ng-core_cfg.mk
                cp -f ${install_path}/wo-config/static_arp.cfg ${install_path}/ngic-rtc/config/static_arp.cfg
                cp -f ${install_path}/wo-config/kni_Makefile ${install_path}/ngic-rtc/dp/Makefile
                '
            """
            echo "${ngic_dp1_output}"
            return true
          }
          sh returnStdout: true, script: """
          ssh ngic-dp1 'cd ${install_path}/ngic-rtc && ./install.sh < ${install_path}/wo-config/dp-auto-install-options.txt 1>${dp_stdout_log} 2>${dp_stderr_log}'
          """
          sh returnStdout: true, script: """
          ssh ngic-dp1 'cd ${install_path}/ngic-rtc/dp && source ../setenv.sh && make clean && make'
          """
        }
      }
      stage("Install CP") {
        timeout(10) {
          sh returnStdout: true, script: """
          ssh ngic-cp1 'if pgrep -f [n]gic_controlplane; then pkill -f [n]gic_controlplane; fi'
          """
          waitUntil {
            ngic_cp1_output = sh returnStdout: true, script: """
            ssh ngic-cp1 '
                cd ${install_path}
                rm -rf ngic-rtc
                git clone https://github.com/omec-project/ngic-rtc.git || exit 1
                cd ngic-rtc

                if [ ${params.ghprbGhRepository} = ${ghRepository} ]; then
                    git fetch origin pull/${params.ghprbPullId}/head:jenkins_test || exit 1
                    git checkout jenkins_test || exit 1
                    git log -1
                fi

                cp -f ${install_path}/wo-config/cp_config.cfg ${install_path}/ngic-rtc/config/cp_config.cfg
                cp -f ${install_path}/wo-config/interface.cfg ${install_path}/ngic-rtc/config/interface.cfg
                cp -f ${install_path}/wo-config/ng-core_cfg.mk ${install_path}/ngic-rtc/config/ng-core_cfg.mk
                '
            """
            echo "${ngic_cp1_output}"
            return true
          }
          sh returnStdout: true, script: """
          ssh ngic-cp1 'cd ${install_path}/ngic-rtc && ./install.sh < ${install_path}/wo-config/cp-auto-install-options.txt 1>${cp_stdout_log} 2>${cp_stderr_log}'
          """
          sh returnStdout: true, script: """
          ssh ngic-cp1 'cd ${install_path}/ngic-rtc/cp && source ../setenv.sh && make clean && make'
          """
        }
      }
      currentBuild.result = 'SUCCESS'
    } catch (err) {
      currentBuild.result = 'FAILURE'
    } finally {

      sh returnStdout: true, script: """
      rm -fr *
      """

      try {
        sh returnStdout: true, script: """
        scp ngic-cp1:${base_log_dir}/* .
        """
      } catch (err) {}
      try {
        sh returnStdout: true, script: """
        scp ngic-dp1:${base_log_dir}/* .
        """
      } catch (err) {}

      archiveArtifacts artifacts: "*${stdout_ext}", allowEmptyArchive: true
      archiveArtifacts artifacts: "*${stderr_ext}", allowEmptyArchive: true
    }
    echo "RESULT: ${currentBuild.result}"
  }
}