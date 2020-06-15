// Copyright 2020-present Open Networking Foundation
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

// Jenkinsfile-omec-container.groovy
// Builds docker images, deploys OMEC on hardware pod and runs NG40 tests


pipeline {

  agent {
    label "${params.buildNode}"
  }

  stages {
    stage ("Environment Cleanup"){
      steps {
        step([$class: 'WsCleanup'])
      }
    }

    stage ("Build Docker Image"){
      steps {
        script {
          if (params.ghprbPullId == ""){
            docker_tag = "jenkins_debug"
          } else {
            pull_request_num = "PR_${params.ghprbPullId}"
            abbreviated_commit_hash = params.ghprbActualCommit.substring(0, 7)
            docker_tag = "${params.ghprbTargetBranch}-${pull_request_num}-${abbreviated_commit_hash}"
          }
        }
        sh label: 'Clone repo', script: """
          if [ "${params.project}" = "c3po" ]
          then
            git clone https://github.com/omec-project/${params.project} --recursive
          else
            git clone https://github.com/omec-project/${params.project}
          fi
          cd ${params.project}
          if [ ! -z "${params.ghprbPullId}" ]
          then
            echo "Checking out GitHub Pull Request: ${params.ghprbPullId}"
            git fetch origin pull/${params.ghprbPullId}/head && git checkout FETCH_HEAD
          else
            echo "GERRIT_REFSPEC not provided. Checking out target branch."
            git checkout ${params.ghprbTargetBranch}
          fi

          sudo make DOCKER_REGISTRY=${params.registry}/ DOCKER_TAG=${docker_tag} docker-build
          sudo make DOCKER_REGISTRY=${params.registry}/ DOCKER_TAG=${docker_tag} docker-push
        """
      }
    }

    stage ("Prepare OMEC deployment"){
      steps {
        script {
          // Get latest image tags for each component
          hssdb_tag = sh returnStdout: true, script: """curl -s 'https://registry.hub.docker.com/v2/repositories/omecproject/c3po-hssdb/tags/' | jq '.results[] | select(.name | contains("${c3poBranch}")).name' | head -1 | tr -d \\\""""
          hss_tag = sh returnStdout: true, script: """curl -s 'https://registry.hub.docker.com/v2/repositories/omecproject/c3po-hss/tags/' | jq '.results[] | select(.name | contains("${c3poBranch}")).name' | head -1 | tr -d \\\""""
          mme_tag = sh returnStdout: true, script: """curl -s 'https://registry.hub.docker.com/v2/repositories/omecproject/nucleus/tags/' | jq '.results[] | select(.name | contains("${nucleusBranch}")).name' | head -1 | tr -d \\\""""
          spgwc_tag = sh returnStdout: true, script: """curl -s 'https://registry.hub.docker.com/v2/repositories/omecproject/ngic-cp/tags/' | jq '.results[] | select(.name | contains("${ngicBranch}")).name' | head -1 | tr -d \\\""""
          spgwu_tag = sh returnStdout: true, script: """curl -s 'https://registry.hub.docker.com/v2/repositories/omecproject/ngic-dp/tags/' | jq '.results[] | select(.name | contains("${ngicBranch}")).name' | head -1 | tr -d \\\""""

          hssdb_image = "omecproject/c3po-hssdb:"+hssdb_tag
          hss_image = "omecproject/c3po-hss:"+hss_tag
          mme_image = "omecproject/nucleus:"+mme_tag
          spgwc_image = "omecproject/ngic-cp:"+spgwc_tag
          spgwu_image = "omecproject/ngic-dp:"+spgwu_tag

          switch("${params.project}") {
          case "c3po":
            hssdb_image = "${params.registry}/c3po-hssdb:${docker_tag}"
            hss_image = "${params.registry}/c3po-hss:${docker_tag}"
            break
          // TODO: add upf-epc repo
          case "ngic-rtc":
            spgwc_image = "${params.registry}/ngic-cp:${docker_tag}"
            spgwu_image = "${params.registry}/ngic-dp:${docker_tag}"
            break
          case "Nucleus":
            mme_image = "${params.registry}/nucleus:${docker_tag}"
            break
          }
          echo "Using hssdb image: ${hssdb_image}"
          echo "Using hss image: ${hss_image}"
          echo "Using mme image: ${mme_image}"
          echo "Using spgwc image: ${spgwc_image}"
          echo "Using spgwu image: ${spgwu_image}"
        }
      }
    }

    stage ("Deploy and Test"){
      options {
        lock(resource: 'aether-dev-cluster')
      }

      stages {
        stage ("Deploy OMEC"){
          steps {
            build job: "omec_deploy_dev", parameters: [
                  string(name: 'hssdbImage', value: "${hssdb_image.trim()}"),
                  string(name: 'hssImage', value: "${hss_image.trim()}"),
                  string(name: 'mmeImage', value: "${mme_image.trim()}"),
                  string(name: 'spgwcImage', value: "${spgwc_image.trim()}"),
                  string(name: 'spgwuImage', value: "${spgwu_image.trim()}"),
            ]
          }
        }

        stage ("Test NG40"){
          steps {
            build job: "omec_ng40-test_dev"
          }
        }
      }
    }
  }
}
