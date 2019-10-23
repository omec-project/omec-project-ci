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

// Jenkinsfile-omec-combined.groovy
// Combines multiple pipelines into one job that can be triggered by a GitHub pull request

pipeline {

  /* executor is determined by parameter */
  agent {
    label "${params.executorNode}"
  }

  stages {

    stage('Install') {
      parallel {

        stage('Install c3po-hss') {
          steps {
            build job: 'omec_c3po-hss_install', parameters: [
                  string(name: 'ghprbActualCommit', value: "${ghprbActualCommit}"),
                  string(name: 'ghprbGhRepository', value: "${ghprbGhRepository}"),
                  string(name: 'ghprbPullId', value: "${ghprbPullId}"),
                ]
          }
        }

        stage('Install c3po-sgx') {
          steps {
            build job: 'omec_c3po-sgx_install', parameters: [
                  string(name: 'ghprbActualCommit', value: "${ghprbActualCommit}"),
                  string(name: 'ghprbGhRepository', value: "${ghprbGhRepository}"),
                  string(name: 'ghprbPullId', value: "${ghprbPullId}"),
                ]
          }
        }

        stage('Install ngic-rtc') {
          steps {
            build job: 'omec_ngic-rtc_install', parameters: [
                  string(name: 'ghprbActualCommit', value: "${ghprbActualCommit}"),
                  string(name: 'ghprbGhRepository', value: "${ghprbGhRepository}"),
                  string(name: 'ghprbPullId', value: "${ghprbPullId}"),
                ]
          }
        }

        stage('Install openmme') {
          steps {
            build job: 'omec_openmme_install', parameters: [
                  string(name: 'ghprbActualCommit', value: "${ghprbActualCommit}"),
                  string(name: 'ghprbGhRepository', value: "${ghprbGhRepository}"),
                  string(name: 'ghprbPullId', value: "${ghprbPullId}"),
                ]
          }
        }
      }
    }

    stage('Attach_DataFlow_Detach Regression TC1') {
      steps {
        build job: 'omec_tc1'
      }
    }
  }
}
