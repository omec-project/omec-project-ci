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
            virsh list --all | grep ngic-dp1 | grep -i running | wc -l
            """
            return running_vms.toInteger() == 1
          }
        }
      }


      stage("Install SGX-Dealer") {
        timeout(10) {
          sh returnStdout: true, script: """
          ssh sgx-kms-cdr '
              if [[ ! -d "${install_path}" ]]; then mkdir -p ${install_path}; fi

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

          sh script: """
          ssh sgx-kms-cdr ${install_path}/c3po/.ci/install/sgx/install_sgx.sh ${install_path}
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

      sgx_dir = sh returnStdout: true, script: """
      ssh sgx-kms-cdr '${install_path}/c3po/.ci/common/get_log_dir.sh ${install_path} sgx'
      """
      sgx_dir = sgx_dir.replace('\n', '')

      try {
        sh returnStdout: true, script: """
        scp -r sgx-kms-cdr:${sgx_dir} .
        """
      } catch (err) {}

      archiveArtifacts artifacts: "**/*", allowEmptyArchive: true
      archiveArtifacts artifacts: "**/*", allowEmptyArchive: true

    }
    echo "RESULT: ${currentBuild.result}"
  }
}