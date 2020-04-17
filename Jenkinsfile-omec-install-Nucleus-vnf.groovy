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

node("${params.buildNode}") {

  def ghRepository = 'omec-project/nucleus'

  def install_path = '/home/jenkins'

  def base_path = '/var/log/cicd'
  def base_folder = 'install'
  def base_log_dir = "${base_path}/${base_folder}"

  def action_make = '_make'
  def action_install = '_install'

  def stdout_ext = '.stdout.log'
  def stderr_ext = '.stderr.log'

  def mme_base_make_stdout_log = "cicd_nucleus" + "${action_make}${stdout_ext}"
  def mme_base_make_stderr_log = "cicd_nucleus" + "${action_make}${stderr_ext}"

  def mme_base_install_stdout_log = "cicd_nucleus" + "${action_install}${stdout_ext}"
  def mme_base_install_stderr_log = "cicd_nucleus" + "${action_install}${stderr_ext}"

  def mme_make_stdout_log = "${base_log_dir}/${mme_base_make_stdout_log}"
  def mme_make_stderr_log = "${base_log_dir}/${mme_base_make_stderr_log}"

  def mme_install_stdout_log = "${base_log_dir}/${mme_base_install_stdout_log}"
  def mme_install_stderr_log = "${base_log_dir}/${mme_base_install_stderr_log}"


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
            virsh list --all | grep c3po-mme1 | grep -i running | wc -l
            """
            return running_vms.toInteger() == 1
          }
        }
        // Clean all logs
        sh returnStdout: true, script: """
        ssh c3po-mme1 '
            if [ ! -d "${base_log_dir}" ]; then mkdir -p ${base_log_dir}; fi
            rm -fr ${base_log_dir}/*
            '
        """
      }
      stage("Install MME") {
        timeout(10) {
          waitUntil {
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

            c3po_mme1_output = sh returnStdout: true, script: """
            ssh c3po-mme1 '
                cd ${install_path}
                rm -rf nucleus
                git clone https://github.com/omec-project/nucleus.git -b ${params.branch} || exit 1
                # We also need openmme repo for the CI specific configurations
                rm -rf openmme
                git clone https://github.com/omec-project/openmme.git -b paging || exit 1
                cd nucleus

                if [ ${params.ghprbGhRepository} = ${ghRepository} ]; then
                    # Clone repo.
                    git checkout ${params.ghprbTargetBranch}
                    git fetch origin pull/${params.ghprbPullId}/head:jenkins_test || exit 1
                    git checkout jenkins_test
                    git log -1

                    # Check that the merge base between target branch and PR is target branch HEAD.
                    if [[ \$(git merge-base jenkins_test ${params.ghprbTargetBranch}) != \$(git rev-parse ${params.ghprbTargetBranch}) ]]; then

                        message=(
                            ""
                            "*******************************************"
                            "*                                         *"
                            "* PR is not based on current ${params.ghprbTargetBranch} HEAD. *"
                            "* Please rebase your code on ${params.ghprbTargetBranch}.      *"
                            "*                                         *"
                            "*******************************************"
                            ""
                        )
                        printf "%s\\n" "\${message[@]}"

                        exit 1
                    fi
                fi

                cp -f ${install_path}/openmme/.ci/config/mme.json    ${install_path}/nucleus/src/mme-app/conf/mme.json
                cp -f ${install_path}/openmme/.ci/config/s1ap.json   ${install_path}/nucleus/src/s1ap/conf/s1ap.json
                cp -f ${install_path}/openmme/.ci/config/s11.json    ${install_path}/nucleus/src/s11/conf/s11.json
                cp -f ${install_path}/openmme/.ci/config/s6a.json    ${install_path}/nucleus/src/s6a/conf/s6a.json
                cp -f ${install_path}/openmme/.ci/config/s6a_fd.conf ${install_path}/nucleus/src/s6a/conf/s6a_fd.conf
                '
            """
            echo "${c3po_mme1_output}"
            return true
          }
          sh returnStdout: true, script: """
          ssh c3po-mme1 '
              cd ${install_path}/nucleus
              make clean || exit 1
              make 1>${mme_make_stdout_log} 2>${mme_make_stderr_log} || exit 1
              make install 1>${mme_install_stdout_log} 2>${mme_install_stderr_log}
              '
          """
          sh returnStdout: true, script: """
          ssh c3po-mme1 '
              cd ${install_path}/nucleus/target/conf
              ./make_certs.sh mme localdomain
              '
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
        scp c3po-mme1:${base_log_dir}/* .
        """
      } catch (err) {}

      archiveArtifacts artifacts: "*${stdout_ext}", allowEmptyArchive: true
      archiveArtifacts artifacts: "*${stderr_ext}", allowEmptyArchive: true
    }
    echo "RESULT: ${currentBuild.result}"
  }
}
