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
        withCredentials([string(credentialsId: '64fe2b1a-b33a-4f13-8442-ad8360434003', variable: 'omecproject_api')]) {
          checkout([
            $class: 'GitSCM',
            userRemoteConfigs: [[ url: "https://omecproject:${omecproject_api}@github.com/omec-project/${params.project}.git", refspec: "pull/${params.ghprbPullId}/head" ]],
            branches: [[name: "FETCH_HEAD"]],
            extensions: [
              [$class: 'RelativeTargetDirectory', relativeTargetDir: "${params.project}"],
              [$class: 'SubmoduleOption', recursiveSubmodules: true]]
            ],)
        }
        script {
          if (params.ghprbPullId == ""){
            docker_tag = "jenkins_debug"
          } else {
            pull_request_num = "PR_${params.ghprbPullId}"
            abbreviated_commit_hash = params.ghprbActualCommit.substring(0, 7)
            docker_tag = "${params.ghprbTargetBranch}-${pull_request_num}-${abbreviated_commit_hash}"
          }
        }
        sh label: 'Docker build', script: """
          cd ${params.project}
          if [ -z "${params.ghprbPullId}" ]
          then
            echo "GERRIT_REFSPEC not provided. Checking out target branch."
            git checkout ${params.ghprbTargetBranch}
          fi
          if [ "${params.project}" = "upf-epc" ]
          then
            EXTRA_VARS='CPU=haswell'
            # TODO: enable docker registry cache
            sed -i '/--cache-from/d' Makefile
          else
            EXTRA_VARS=''
          fi
          sudo make \$EXTRA_VARS DOCKER_REGISTRY=${params.registry}/ DOCKER_TAG=${docker_tag} docker-build
          sudo make \$EXTRA_VARS DOCKER_REGISTRY=${params.registry}/ DOCKER_TAG=${docker_tag} docker-push
        """
      }
    }

    stage ("Prepare OMEC deployment"){
      steps {
        script {
          // Get latest image tags for each component
          hssdb_tag = sh returnStdout: true, script: """curl -s 'https://registry.hub.docker.com/v2/repositories/omecproject/c3po-hssdb/tags/' | jq '.results[] | select(.name | test("${c3poBranch}-[0-9a-z]{7}\$")) | select(.last_updater_username=="zdwonf") | .name' | head -1 | tr -d \\\""""
          hss_tag = sh returnStdout: true, script: """curl -s 'https://registry.hub.docker.com/v2/repositories/omecproject/c3po-hss/tags/' | jq '.results[] | select(.name | test("${c3poBranch}-[0-9a-z]{7}\$")) | select(.last_updater_username=="zdwonf") |.name' | head -1 | tr -d \\\""""
          mme_tag = sh returnStdout: true, script: """curl -s 'https://registry.hub.docker.com/v2/repositories/omecproject/nucleus/tags/' | jq '.results[] | select(.name | test("${nucleusBranch}-[0-9a-z]{7}\$")) | select(.last_updater_username=="zdwonf") |.name' | head -1 | tr -d \\\""""
          spgwc_tag = sh returnStdout: true, script: """curl -s 'https://registry.hub.docker.com/v2/repositories/omecproject/spgw/tags/' | jq '.results[] | select(.name | test("${spgwBranch}-[0-9a-z]{7}\$")) | select(.last_updater_username=="zdwonf") |.name' | head -1 | tr -d \\\""""
          bess_tag = sh returnStdout: true, script: """curl -s 'https://registry.hub.docker.com/v2/repositories/omecproject/upf-epc-bess/tags/' | jq '.results[] | select(.name | test("${upfBranch}-[0-9a-z]{7}\$")) | select(.last_updater_username=="zdwonf") |.name' | head -1 | tr -d \\\""""
          zmqiface_tag = sh returnStdout: true, script: """curl -s 'https://registry.hub.docker.com/v2/repositories/omecproject/upf-epc-cpiface/tags/' | jq '.results[] | select(.name | test("${upfBranch}-[0-9a-z]{7}\$")) | select(.last_updater_username=="zdwonf") |.name' | head -1 | tr -d \\\""""
          pfcpiface_tag = sh returnStdout: true, script: """curl -s 'https://registry.hub.docker.com/v2/repositories/omecproject/upf-epc-pfcpiface/tags/' | jq '.results[] | select(.name | test("${upfBranch}-[0-9a-z]{7}\$")) | select(.last_updater_username=="zdwonf") |.name' | head -1 | tr -d \\\""""

          hssdb_image = "omecproject/c3po-hssdb:"+hssdb_tag
          hss_image = "omecproject/c3po-hss:"+hss_tag
          mme_image = "omecproject/nucleus:"+mme_tag
          spgwc_image = "omecproject/spgw:"+spgwc_tag
          bess_image = "omecproject/upf-epc-bess:"+bess_tag
          zmqiface_image = "omecproject/upf-epc-cpiface:"+zmqiface_tag
          pfcpiface_image = "omecproject/upf-epc-pfcpiface:"+pfcpiface_tag

          switch("${params.project}") {
          case "c3po":
            hssdb_image = "${params.registry}/c3po-hssdb:${docker_tag}"
            hss_image = "${params.registry}/c3po-hss:${docker_tag}"
            break
          case "Nucleus":
            mme_image = "${params.registry}/nucleus:${docker_tag}"
            break
          case "spgw":
            spgwc_image = "${params.registry}/spgw:${docker_tag}"
            break
          case "upf-epc":
            bess_image = "${params.registry}/upf-epc-bess:${docker_tag}"
            zmqiface_image = "${params.registry}/upf-epc-cpiface:${docker_tag}"
            pfcpiface_image = "${params.registry}/upf-epc-pfcpiface:${docker_tag}"
            break
          }
          echo "Using hssdb image: ${hssdb_image}"
          echo "Using hss image: ${hss_image}"
          echo "Using mme image: ${mme_image}"
          echo "Using spgwc image: ${spgwc_image}"
          echo "Using bess image: ${bess_image}"
          echo "Using zmqiface image: ${zmqiface_image}"
          echo "Using pfcpiface image: ${pfcpiface_image}"
        }
      }
    }

    stage ("Deploy and Test"){
      options {
        lock(resource: 'aether-dev-cluster')
      }
      steps {
        script {
          try {
            runTest = false
            build job: "omec_deploy_dev", parameters: [
                  string(name: 'hssdbImage', value: "${hssdb_image.trim()}"),
                  string(name: 'hssImage', value: "${hss_image.trim()}"),
                  string(name: 'mmeImage', value: "${mme_image.trim()}"),
                  string(name: 'spgwcImage', value: "${spgwc_image.trim()}"),
                  string(name: 'bessImage', value: "${bess_image.trim()}"),
                  string(name: 'zmqifaceImage', value: "${zmqiface_image.trim()}"),
                  string(name: 'pfcpifaceImage', value: "${pfcpiface_image.trim()}"),
            ]
            runTest = true
            build job: "omec_ng40-test_dev"
            currentBuild.result = 'SUCCESS'
          } catch (err) {
            currentBuild.result = 'FAILURE'
            throw err
          } finally {
            // Collect and copy OMEC logs
            build job: "omec_archive-artifacts_dev"
            copyArtifacts projectName: 'omec_archive-artifacts_dev', target: 'omec', selector: lastCompleted()
            archiveArtifacts artifacts: "omec/*/*", allowEmptyArchive: true

            if (runTest) {
              // Copy NG40 logs
              copyArtifacts projectName: 'omec_ng40-test_dev', target: 'ng40', selector: lastCompleted()
              archiveArtifacts artifacts: "ng40/*/*", allowEmptyArchive: true
            }
          }
        }
      }
    }
  }
}
