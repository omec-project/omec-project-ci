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

  def ghRepository = 'omec-project/c3po'

  def install_path = '/home/jenkins'
  def basedir_dp1 = '/home/jenkins'  //for keys installation

  def base_path = '/var/log/cicd'
  def base_folder = 'install'
  def base_log_dir = "${base_path}/${base_folder}"

  def hss_app = 'hss'

  def hss_dir = "${base_log_dir}/${hss_app}"

  def action_inst = '_install'
  def action_make = '_make'

  def stdout_ext = '.stdout.log'
  def stderr_ext = '.stderr.log'

  def hss_install_stdout_log = "${hss_dir}/" + "hss" + "${action_inst}${stdout_ext}"
  def hss_install_stderr_log = "${hss_dir}/" + "hss" + "${action_inst}${stderr_ext}"
  def hsssec_stdout_log = "${hss_dir}/" + "hsssec" + "${action_make}${stdout_ext}"
  def hsssec_stderr_log = "${hss_dir}/" + "hsssec" + "${action_make}${stderr_ext}"
  def hss_stdout_log = "${hss_dir}/" + "hss" + "${action_make}${stdout_ext}"
  def hss_stderr_log = "${hss_dir}/" + "hss" + "${action_make}${stderr_ext}"

  timeout (60) {
    try {

      echo "${params}"

      // to check and update SGX dealer / DP keys
      def mrenclave = ''
      def mrsigner = ''

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
            virsh list --all | grep c3po-hss1 | grep -i running | wc -l
            """
            return running_vms.toInteger() == 1
          }
        }
        // Clean all logs
        sh returnStdout: true, script: """
        ssh c3po-hss1 '
            if [ ! -d "${hss_dir}" ]; then mkdir -p ${hss_dir}; fi
            rm -fr ${hss_dir}/*
            '
        """
      }

      stage("clone c3po for HSS") {
        timeout(20) {
          sh returnStdout: true, script: """ssh c3po-hss1 'if pgrep -f [h]ss; then pkill -f [h]ss; fi'"""
          waitUntil {
            hss_c3po_clone_output = sh returnStdout: true, script: """
            ssh c3po-hss1 '
                cd ${install_path}
                rm -rf c3po
                git clone https://github.com/omec-project/c3po.git || exit 1
                cd c3po

                if [ ${params.ghprbGhRepository} = ${ghRepository} ]; then
                    git fetch origin pull/${params.ghprbPullId}/head:jenkins_test || exit 1
                    git rebase master jenkins_test || exit 1
                    git log -1
                fi
                '
            """
            echo "${hss_c3po_clone_output}"
            return true
          }

          waitUntil {
            hss_dealer_in_output = sh returnStdout: true, script: """
            ssh c3po-hss1 'cd ${install_path}/c3po && ./install.sh < ${install_path}/c3po/.ci/install/hss-auto-install.txt 1>${hss_install_stdout_log} 2>${hss_install_stderr_log}'
            """
            echo "${hss_dealer_in_output}"
            return true
          }
          sh returnStdout: true, script: """
          ssh c3po-hss1 'cd ${install_path}/c3po/util && make clean && make 1>${hsssec_stdout_log} 2>${hsssec_stderr_log}'
          ssh c3po-hss1 'cd ${install_path}/c3po/hsssec && make clean && make 1>>${hsssec_stdout_log} 2>>${hsssec_stderr_log}'
          """

          waitUntil {
            hss_hss_output = sh returnStdout: true, script: """
            ssh c3po-hss1 'cp -f ${install_path}/c3po/.ci/config/hss.json ${install_path}/c3po/hss/conf/hss.json'

            ssh c3po-hss1 'cd ${install_path}/c3po/hss/conf && ../bin/make_certs.sh hss openair4G.eur'
            """
            echo "${hss_hss_output}"
            return true
          }
          sh returnStdout: true, script: """
          ssh c3po-hss1 'cd ${install_path}/c3po/hss && make clean && make 1>${hss_stdout_log} 2>${hss_stderr_log}'
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
        scp -r c3po-hss1:${hss_dir} .
        """
      } catch (err) {}

      archiveArtifacts artifacts: "**/*${stdout_ext}", allowEmptyArchive: true
      archiveArtifacts artifacts: "**/*${stderr_ext}", allowEmptyArchive: true

    }
    echo "RESULT: ${currentBuild.result}"
  }
}
