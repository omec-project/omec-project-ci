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

// Jenkinsfile-omec-deploy.groovy: Deploys OMEC containers


pipeline {

  /* executor is determined by parameter */
  agent {
    label "${params.buildNode}"
  }

  options {
    timeout(time: 30, unit: 'MINUTES')
  }

  environment {
    logDir = "container-logs"
  }

  stages {
    stage('Clean Up') {
      steps {
        step([$class: 'WsCleanup'])
      }
    }

    stage('Get Container Logs') {
      steps {
        sh label: "Save OMEC container logs", script: """
        mkdir ${logDir}
        kubectl --context ${params.cpContext} -n omec get pods > ${logDir}/cp-pods.log
        kubectl --context ${params.dpContext} -n omec get pods > ${logDir}/dp-pods.log

        kubectl --context ${params.cpContext} -n omec logs --timestamps hss-0 > ${logDir}/hss.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps mme-0 -c s1ap-app > ${logDir}/s1ap-app.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps mme-0 -c mme-app > ${logDir}/mme-app.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps mme-0 -c s6a-app > ${logDir}/s6a-app.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps mme-0 -c s11-app > ${logDir}/s11-app.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps spgwc-0 > ${logDir}/spgwc.log || true
        kubectl --context ${params.dpContext} -n omec logs --timestamps spgwu-0 -c routectl > ${logDir}/routectl.log || true
        kubectl --context ${params.dpContext} -n omec logs --timestamps spgwu-0 -c bessd > ${logDir}/bessd.log || true
        kubectl --context ${params.dpContext} -n omec logs --timestamps spgwu-0 -c web > ${logDir}/web.log || true
        kubectl --context ${params.dpContext} -n omec logs --timestamps spgwu-0 -c cpiface > ${logDir}/cpiface.log || true
        """

      }
    }
  }

  post {
    always {
      archiveArtifacts artifacts: "${logDir}/*", allowEmptyArchive: true
    }
  }
}

