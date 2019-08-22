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

  def install_path_cp = '/home/jenkins'
  def install_path_dp = '/home/jenkins'

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

            running_vms = sh returnStdout: true, script: """
            virsh list --all | grep "ngic-cp1\\|ngic-dp1" | grep -i running | wc -l
            """
            return running_vms.toInteger() == 2
          }
        }
      }
      stage("Install DP") {
        timeout(10) {
          sh script: """
          ssh ngic-dp1 '
              cd ${install_path_dp}
              rm -rf ngic-rtc
              git clone https://github.com/omec-project/ngic-rtc.git || exit 1

              if [ ${params.ghprbGhRepository} = ${ghRepository} ]; then
                  git fetch origin pull/${params.ghprbPullId}/head:jenkins_test || exit 1
                  git rebase master jenkins_test || exit 1
                  git log -1
              fi
              '
          """

          sh script: """
          ssh ngic-dp1 ${install_path_dp}/ngic-rtc/.ci/install/dp/install_dp.sh ${install_path_dp}
          """
        }
      }
      stage("Install CP") {
        timeout(10) {
          sh script: """
          ssh ngic-cp1 '
              cd ${install_path_cp}
              rm -rf ngic-rtc
              git clone https://github.com/omec-project/ngic-rtc.git || exit 1

              if [ ${params.ghprbGhRepository} = ${ghRepository} ]; then
                  git fetch origin pull/${params.ghprbPullId}/head:jenkins_test || exit 1
                  git rebase master jenkins_test || exit 1
                  git log -1
              fi
              '
          """

          sh script: """
          ssh ngic-cp1 ${install_path_cp}/ngic-rtc/.ci/install/cp/install_cp.sh ${install_path_cp}
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

      cp_dir = sh returnStdout: true, script: """
      ssh ngic-cp1 ${install_path_cp}/ngic-rtc/.ci/common/get_log_dir.sh ${install_path_cp}
      """
      cp_dir = cp_dir.replace('\n', '')

      try {
        sh returnStdout: true, script: """
        scp ngic-cp1:${cp_dir}/* .
        """
      } catch (err) {}

      dp_dir = sh returnStdout: true, script: """
      ssh ngic-dp1 ${install_path_dp}/ngic-rtc/.ci/common/get_log_dir.sh ${install_path_dp}
      """
      dp_dir = dp_dir.replace('\n', '')

      try {
        sh returnStdout: true, script: """
        scp ngic-dp1:${dp_dir}/* .
        """
      } catch (err) {}

      archiveArtifacts artifacts: "*", allowEmptyArchive: true
      archiveArtifacts artifacts: "*", allowEmptyArchive: true
    }
    echo "RESULT: ${currentBuild.result}"
  }
}