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

  def ghRepository = 'omec-project/c3po'

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

            running_vms = sh returnStdout: true, script: """
            virsh list --all | grep c3po-hss1 | grep -i running | wc -l
            """
            return running_vms.toInteger() == 1
          }
        }
      }

      stage("Install HSS") {
        timeout(10) {
          sh script: """
          ssh c3po-hss1 '
              if [[ ! -d "${install_path}" ]]; then mkdir -p ${install_path}; fi

              cd ${install_path}
              rm -rf c3po
              git clone https://github.com/omec-project/c3po.git || exit 1
              cd c3po

              if [ ${params.ghprbGhRepository} = ${ghRepository} ]; then
                  # Clone repo.
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
              '
          """

          sh script: """
          ssh c3po-hss1 ${install_path}/c3po/.ci/install/hss/install_hss.sh ${install_path}
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

      hss_dir = sh returnStdout: true, script: """
      ssh c3po-hss1 '${install_path}/c3po/.ci/common/get_log_dir.sh ${install_path} hss'
      """
      hss_dir = hss_dir.replace('\n', '')

      try {
        sh returnStdout: true, script: """
        scp -r c3po-hss1:${hss_dir} .
        """
      } catch (err) {}

      archiveArtifacts artifacts: "**/*", allowEmptyArchive: true
      archiveArtifacts artifacts: "**/*", allowEmptyArchive: true

    }
    echo "RESULT: ${currentBuild.result}"
  }
}
