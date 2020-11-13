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
        mkdir ${WORKSPACE}/${k8sLogDir} && cd ${WORKSPACE}/${k8sLogDir}
        kubectl --context ${params.cpContext} -n omec get pods > cp-pods.log || true
        kubectl --context ${params.dpContext} -n omec get pods > dp-pods.log || true
        kubectl --context ${params.cpContext} -n omec describe pod hss-0 > hss-describe.log || true
        kubectl --context ${params.cpContext} -n omec describe pod mme-0 > mme-describe.log || true
        kubectl --context ${params.cpContext} -n omec describe pod spgwc-0 > spgwc-describe.log || true
        kubectl --context ${params.dpContext} -n omec describe pod upf-0 > upf-describe.log || rm upf-describe.log
        kubectl --context ${params.dpContext} -n omec describe pod pfcp-0 > pfcp-describe.log || rm pfcp-describe.log
        kubectl --context ${params.cpContext} -n omec get pod hss-0 -o yaml > hss-get.log || true
        kubectl --context ${params.cpContext} -n omec get pod mme-0 -o yaml > mme-get.log || true
        kubectl --context ${params.cpContext} -n omec get pod spgwc-0 -o yaml > spgwc-get.log || true
        kubectl --context ${params.dpContext} -n omec get pod upf-0 -o yaml > upf-get.log || rm upf-get.log
        kubectl --context ${params.dpContext} -n omec get pod pfcp-0 -o yaml > pfcp-get.log || rm pfcp-get.log

        # Get container logs
        mkdir ${WORKSPACE}/${containterLogDir} && cd ${WORKSPACE}/${containterLogDir}
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} cassandra-0 > cassandra-0.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} cassandra-1 > cassandra-1.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} cassandra-2 > cassandra-2.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} hss-0 > hss.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} hss-0 --previous > hss-previous.log || rm hss-previous.log
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} hss-0 -c hss-bootstrap > hss-bootstrap.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} mme-0 -c s1ap-app > s1ap-app.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} mme-0 -c s1ap-app --previous > s1ap-app-previous.log || rm s1ap-app-previous.log
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} mme-0 -c mme-app > mme-app.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} mme-0 -c mme-app --previous > mme-app-previous.log || rm mme-app-previous.log
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} mme-0 -c s6a-app > s6a-app.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} mme-0 -c s6a-app --previous > s6a-app-previous.log || rm s6a-app-previous.log
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} mme-0 -c s11-app > s11-app.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} mme-0 -c s11-app --previous > s11-app-previous.log || rm s11-app-previous.log
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} spgwc-0 -c spgwc > spgwc.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} spgwc-0 -c spgwc --previous > spgwc-previous.log || rm spgwc-previous.log
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} spgwc-0 -c gx-app > gx-app.log || true
        kubectl --context ${params.cpContext} -n omec logs --timestamps --since=${params.logSince} spgwc-0 -c gx-app --previous > gx-app-previous.log || rm gx-app-previous.log
        kubectl --context ${params.dpContext} -n omec logs --timestamps --since=${params.logSince} upf-0 -c routectl > routectl.log || rm routectl.log
        kubectl --context ${params.dpContext} -n omec logs --timestamps --since=${params.logSince} upf-0 -c routectl --previous > routectl-previous.log || rm routectl-previous.log
        kubectl --context ${params.dpContext} -n omec logs --timestamps --since=${params.logSince} upf-0 -c bessd > bessd.log || rm bessd.log
        kubectl --context ${params.dpContext} -n omec logs --timestamps --since=${params.logSince} upf-0 -c bessd --previous > bessd-previous.log || rm bessd-previous.log
        kubectl --context ${params.dpContext} -n omec logs --timestamps --since=${params.logSince} upf-0 -c web > web.log || rm web.log
        kubectl --context ${params.dpContext} -n omec logs --timestamps --since=${params.logSince} upf-0 -c web --previous > web-previous.log || rm web-previous.log
        kubectl --context ${params.dpContext} -n omec logs --timestamps --since=${params.logSince} upf-0 -c cpiface > cpiface.log || rm cpiface.log
        kubectl --context ${params.dpContext} -n omec logs --timestamps --since=${params.logSince} upf-0 -c cpiface --previous > cpiface-previous.log || rm cpiface-previous.log
        kubectl --context ${params.dpContext} -n omec logs --timestamps --since=${params.logSince} pfcp-0 > pfcp.log || rm pfcp.log
        tar czf container-logs.tgz *
        rm *.log
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
