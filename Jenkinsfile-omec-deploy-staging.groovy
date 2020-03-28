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

// Jenkinsfile-omec-deploy-staging.groovy

pipeline {

  /* executor is determined by parameter */
  agent {
    label "${params.buildNode}"
  }

  environment {
    omec_cp="~/pod-configs/deployment-configs/aether/apps/gcp-stg/omec-cp.yaml"
    omec_dp="~/pod-configs/deployment-configs/aether/apps/menlo-stg/omec-dp.yaml"
  }

  stages {
    stage('Fetch Images from Params') {
      steps {
        sh label: 'Fetch Images from Params', script: """
          ssh comac@192.168.122.57 '
            rm -rf pod-configs/
            git clone https://gerrit.opencord.org/pod-configs/

            # Tags for staging-central-gcp

            if [ ! -z "${params.hssdb_tag}" ]
            then
              sed -i "s;hssdb: .*;hssdb: \\"${params.hssdb_tag}\\";" $omec_cp
            fi

            if [ ! -z "${params.hss_tag}" ]
            then
              sed -i "s;hss: .*;hss: \\"${params.hss_tag}\\";" $omec_cp
            fi

            if [ ! -z "${params.mme_tag}" ]
            then
              sed -i "s;mme: .*;mme: \\"${params.mme_tag}\\";" $omec_cp
            fi

            if [ ! -z "${params.mmeExporter_tag}" ]
            then
              sed -i "s;mmeExporter: .*;mmeExporter: \\"${params.mmeExporter_tag}\\";" $omec_cp
            fi

            if [ ! -z "${params.spgwc_tag}" ]
            then
              sed -i "s;spgwc: .*;spgwc: \\"${params.spgwc_tag}\\";" $omec_cp
            fi

            if [ ! -z "${params.spgwu_tag}" ]
            then
              sed -i "s;spgwu: .*;spgwu: \\"${params.spgwu_tag}\\";" $omec_dp
            fi
            '
          """
      }
    }

    stage('Deploy: staging-central-gcp') {
      steps {
        sh label: 'staging-central-gcp', script: '''
          ssh comac@192.168.122.57 '
            kubectl config use-context staging-central-gcp

            helm del --purge omec-control-plane | true

            helm install --kube-context staging-central-gcp \
                         --name omec-control-plane \
                         --namespace omec \
                         --values pod-configs/deployment-configs/aether/apps/gcp-stg/omec-cp.yaml \
                         cord/omec-control-plane

            kubectl --context staging-central-gcp -n omec wait \
                         --for=condition=Ready \
                         --timeout=300s \
                         pod -l app=spgwc
            '
          '''
      }
    }

    stage('Deploy: omec-data-plane') {
      steps {
        sh label: 'staging-edge-onf-menlo', script: '''
          ssh comac@192.168.122.57 '
            kubectl config use-context staging-edge-onf-menlo

            helm del --purge omec-data-plane | true

            helm install --kube-context staging-edge-onf-menlo \
                         --name omec-data-plane \
                         --namespace omec \
                         --values pod-configs/deployment-configs/aether/apps/menlo-stg/omec-dp.yaml \
                         cord/omec-data-plane

            kubectl --context staging-edge-onf-menlo -n omec wait \
                         --for=condition=Ready \
                         --timeout=300s \
                         pod -l app=spgwu
            '
          '''
      }
    }

    stage('Deploy: accelleran-cbrs') {
      steps {
        sh label: 'accelleran-cbrs-common', script: '''
          ssh comac@192.168.122.57 '
            kubectl config use-context staging-edge-onf-menlo

            helm del --purge accelleran-cbrs-common | true
            helm del --purge accelleran-cbrs-cu | true

            helm install --kube-context staging-edge-onf-menlo \
                         --name accelleran-cbrs-common \
                         --namespace omec \
                         --values pod-configs/deployment-configs/aether/apps/menlo-stg/accelleran-cbrs-common.yaml \
                         cord/accelleran-cbrs-common

            helm install --kube-context staging-edge-onf-menlo \
                         --name accelleran-cbrs-cu \
                         --namespace omec \
                         --values pod-configs/deployment-configs/aether/apps/menlo-stg/accelleran-cbrs-cu.yaml \
                         cord/accelleran-cbrs-cu

            kubectl --context staging-edge-onf-menlo -n omec wait \
                         --for=condition=Ready \
                         --timeout=300s \
                         pod -l app=accelleran-cbrs-cu
            '
          '''
      }
    }
  }
}
