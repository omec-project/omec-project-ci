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
    k8sLogDir = "k8s-logs"
    containterLogDir = "container-logs"
  }

  stages {
    stage('Clean Up') {
      steps {
        step([$class: 'WsCleanup'])
      }
    }

    stage('Get Container Logs') {
      steps {
        sh label: "Save OMEC logs", script: """
        # Get k8s logs
        mkdir ${k8sLogDir}
        kubectl --context ${params.cpContext} -n omec get pods > ${k8sLogDir}/cp-pods.log || true
        kubectl --context ${params.dpContext} -n omec get pods > ${k8sLogDir}/dp-pods.log || true
        kubectl --context ${params.cpContext} -n omec describe pod hss-0 > ${k8sLogDir}/hss-describe.log || true
        kubectl --context ${params.cpContext} -n omec describe pod mme-0 > ${k8sLogDir}/mme-describe.log || true
        kubectl --context ${params.cpContext} -n omec describe pod spgwc-0 > ${k8sLogDir}/spgwc-describe.log || true
        kubectl --context ${params.dpContext} -n omec describe pod upf-0 > ${k8sLogDir}/upf-describe.log || true
        kubectl --context ${params.dpContext} -n omec describe pod pfcp-0 > ${k8sLogDir}/pfcp-describe.log || true
        kubectl --context ${params.cpContext} -n omec get pod hss-0 -o yaml > ${k8sLogDir}/hss-get.log || true
        kubectl --context ${params.cpContext} -n omec get pod mme-0 -o yaml > ${k8sLogDir}/mme-get.log || true
        kubectl --context ${params.cpContext} -n omec get pod spgwc-0 -o yaml > ${k8sLogDir}/spgwc-get.log || true
        kubectl --context ${params.dpContext} -n omec get pod upf-0 -o yaml > ${k8sLogDir}/upf-get.log || true
        kubectl --context ${params.dpContext} -n omec get pod pfcp-0 -o yaml > ${k8sLogDir}/pfcp-get.log || true

        # Get container logs
        mkdir ${containterLogDir}
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} cassandra-0 > ${containterLogDir}/cassandra-0.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} cassandra-1 > ${containterLogDir}/cassandra-1.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} cassandra-2 > ${containterLogDir}/cassandra-2.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} hss-0 > ${containterLogDir}/hss.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} hss-0 -c hss-bootstrap > ${containterLogDir}/hss-bootstrap.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} mme-0 -c s1ap-app > ${containterLogDir}/s1ap-app.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} mme-0 -c mme-app > ${containterLogDir}/mme-app.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} mme-0 -c s6a-app > ${containterLogDir}/s6a-app.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} mme-0 -c s11-app > ${containterLogDir}/s11-app.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} spgwc-0 -c spgwc > ${containterLogDir}/spgwc.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} spgwc-0 -c gx-app > ${containterLogDir}/gx-app.log || true
        kubectl --context ${params.dpContext} -n omec logs --timestamps --since=${params.logSince} upf-0 -c routectl > ${containterLogDir}/routectl.log || true
        kubectl --context ${params.dpContext} -n omec logs --timestamps --since=${params.logSince} upf-0 -c bessd > ${containterLogDir}/bessd.log || true
        kubectl --context ${params.dpContext} -n omec logs --timestamps --since=${params.logSince} upf-0 -c web > ${containterLogDir}/web.log || true
        kubectl --context ${params.dpContext} -n omec logs --timestamps --since=${params.logSince} upf-0 -c cpiface > ${containterLogDir}/cpiface.log || true
        kubectl --context ${params.dpContext} -n omec logs --timestamps --since=${params.logSince} pfcp-0 > ${containterLogDir}/pfcp.log || true
        """
      }
    }
  }

  post {
    always {
      archiveArtifacts artifacts: "${k8sLogDir}/*", allowEmptyArchive: true
      archiveArtifacts artifacts: "${containterLogDir}/*", allowEmptyArchive: true
    }
  }
}
