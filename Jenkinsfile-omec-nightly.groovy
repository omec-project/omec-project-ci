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

// Nightly job that deploys OMEC and runs NG40 tests

pipeline {

  agent {
    label "${params.buildNode}"
  }
  options {
    lock(resource: "${params.pod}")
  }

  stages {
    stage ("Environment Cleanup"){
      steps {
        step([$class: 'WsCleanup'])
      }
    }

    stage ("Deploy OMEC"){
      when { expression { return params.redeploy } }
      steps {
        build job: "omec_deploy_${params.pod}", parameters: [
          string(name: 'hssdbImage', value: "${params.hssdbImage}"),
          string(name: 'hssImage', value: "${params.hssImage}"),
          string(name: 'mmeImage', value: "${params.mmeImage}"),
          string(name: 'spgwcImage', value: "${params.spgwcImage}"),
          string(name: 'bessImage', value: "${params.bessImage}"),
          string(name: 'zmqifaceImage', value: "${params.zmqifaceImage}"),
          string(name: 'pfcpifaceImage', value: "${params.pfcpifaceImage}"),
        ]
      }
    }

    stage ("Run NG40 Tests"){
      steps {
        script {
          // Get timestamp before running tests
          datetime = sh returnStdout: true, script: """
          date -u +"%Y-%m-%dT%H:%M:%S.%NZ"
          """
          datetime = datetime.trim()
        }
        catchError {
          build job: "omec_ng40-test_${params.pod}", parameters: [
                string(name: 'ntlFile', value: "${params.ntlFile}"),
                string(name: 'timeout', value: "${params.timeout}")
          ]
        }
      }
    }

    stage ("Post Results"){
      steps {
        // Collect and copy OMEC logs
        build job: "omec_archive-artifacts_${params.pod}", parameters: [
              string(name: 'logSince', value: ""),
              string(name: 'logSinceTime', value: "${datetime}")
        ]
        copyArtifacts projectName: "omec_archive-artifacts_${params.pod}", target: 'omec', selector: lastCompleted()
        archiveArtifacts artifacts: "omec/*/*", allowEmptyArchive: true
        // Collect and Copy NG40 logs
        build job: "omec_post-results_${params.pod}", parameters: [
                string(name: 'testType', value: "${params.testType}"),
                string(name: 'buildNumber', value: "${BUILD_NUMBER}")
        ]
        copyArtifacts projectName: "omec_post-results_${params.pod}", selector: lastCompleted()
        archiveArtifacts artifacts: "ng40/*/*", allowEmptyArchive: true
        // Copy plots
        copyArtifacts projectName: "omec_post-results_${params.pod}", selector: lastCompleted()
        archiveArtifacts artifacts: "plots/*", allowEmptyArchive: true
      }
    }
  }
  post {
    failure {
      step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: "${params.maintainers}", sendToIndividuals: false])
    }
  }
}
