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

// Jenkinsfile-omec-ng40-test.groovy: run NG40 tests with deployed OMEC pod


pipeline {

  /* executor is determined by parameter */
  agent {
    label "${params.buildNode}"
  }

  options {
    timeout(time: "${params.timeout}", unit: 'MINUTES')
  }

  environment {
      ng40Dir = "/home/ng40/config/ng40cvnf"
      ng40LogDir = "log"
      ng40PcapDir = "pcap"
  }

  stages {
    stage('Clean Up') {
      steps {
        step([$class: 'WsCleanup'])
        sh label: 'Kill all packet captures', script: """
        pkill kubectl-sniff || true
        mkdir ${ng40LogDir}
        mkdir ${ng40PcapDir}
        """
      }
    }

    stage('Start Packet Capturing') {
      steps {
        sh label: 'Capture packets on containers', script: """
        export BUILD_ID=dontKillMe
        export JENKINS_NODE_COOKIE=dontKillMe
        kubectl config use-context ${params.cpContext}
        nohup kubectl sniff -n omec hss-0 -o ${ng40PcapDir}/hss.pcap > /dev/null 2>&1 &
        nohup kubectl sniff -n omec mme-0 -c mme-app -o ${ng40PcapDir}/mme.pcap > /dev/null 2>&1 &
        nohup kubectl sniff -n omec spgwc-0 -o ${ng40PcapDir}/spgwc.pcap > /dev/null 2>&1 &
        sleep 5
        kubectl config use-context ${params.dpContext}
        nohup kubectl sniff -n omec spgwu-0 -o ${ng40PcapDir}/spgwu.pcap > /dev/null 2>&1 &
        sleep 5
        """
      }
    }

    stage('Test NG40') {
      steps {
        sh label: 'Run NG40 tests', script: """
        ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ${params.ng40VM} '
        ng40forcecleanup all
        cd ${env.ng40Dir}/testlist
        ng40test ${ntlFile}
        '
        """
      }
    }
  }
  post {
    always {
      script {
        //Get testcase log filename
        testcase_output_filename = sh returnStdout: true, script: """
        ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ${params.ng40VM} '
        ls -Art ${env.ng40Dir}/testlist/log | tail -n 1
        '
        """
        testcase_output_filename = testcase_output_filename.trim()

        //Display testcase log
        testcase_output_log = sh returnStdout: true, script: """
        ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ${params.ng40VM} '
        cat ${env.ng40Dir}/testlist/log/${testcase_output_filename}
        '
        """
        echo "=========== Testcase Log ==========="
        echo "${testcase_output_log}"

        //Get number of test cases executed
        testcase_num = sh returnStdout: true, script: """
        ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ${params.ng40VM} '
        grep "Run test case" ${env.ng40Dir}/testlist/log/${testcase_output_filename} | wc -l
        '
        """

        //Get a list of test logs
        log_list = sh returnStdout: true, script: """
        ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ${params.ng40VM} '
        let "num=${testcase_num}+1"
        ls -Art ${env.ng40Dir}/ran/log | tail -n \$num
        '
        """
        log_list = log_list.trim()

        //Copy test logs to workspace
        sh returnStdout: true, script: """
        scp ${params.ng40VM}:${env.ng40Dir}/testlist/log/${testcase_output_filename} ${ng40LogDir}/
        """
        for( String log_name : log_list.split() ) {
          sh returnStdout: true, script: """
          scp ${params.ng40VM}:${env.ng40Dir}/ran/log/${log_name} ${ng40LogDir}/
          """
        }

        archiveArtifacts artifacts: "${ng40LogDir}/*", allowEmptyArchive: true

        //Get a list of test pcaps
        pcap_list = sh returnStdout: true, script: """
        ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ${params.ng40VM} '
        let "num=${testcase_num}+1"
        ls -Art ${env.ng40Dir}/ran/pcap | tail -n \$num
        '
        """
        pcap_list = pcap_list.trim()

        //Copy test pcaps to workspace
        for( String pcap_name : pcap_list.split() ) {
          sh returnStdout: true, script: """
          scp ${params.ng40VM}:${env.ng40Dir}/ran/pcap/${pcap_name} ${ng40PcapDir}/
          """
        }

        //Stop packet capturing on containers
        sh returnStdout: true, script: """
        pkill kubectl-sniff || true
        """

        archiveArtifacts artifacts: "${ng40PcapDir}/*", allowEmptyArchive: true
      }
    }
  }
}
