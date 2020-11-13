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
      metricsDir = "metrics"
  }

  stages {
    stage('Clean Up') {
      steps {
        step([$class: 'WsCleanup'])
        sh label: 'Kill all packet captures', script: """
        pkill kubectl-sniff || true
        mkdir ${ng40LogDir}
        mkdir ${ng40PcapDir}
        mkdir ${metricsDir}
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
        nohup kubectl sniff -n omec upf-0 -o ${ng40PcapDir}/upf.pcap > /dev/null 2>&1 &
        sleep 5
        """
        sh label: 'Get MME metrics before starting tests', script: """
        mme_ip=\$(kubectl --context ${params.cpContext} get services -n omec | grep 'mme ' | awk '{print \$3}')
        curl -m 5 \$mme_ip:3081/metrics 2>/dev/null 1>${metricsDir}/mme-metrics-before-tests.log || true
        hss_ip=\$(kubectl --context ${params.cpContext} get services -n omec | grep 'hss ' | awk '{print \$3}')
        curl -m 5 \$hss_ip:9089/metrics 2>/dev/null 1>${metricsDir}/hss-metrics-before-tests.log || true
        spgwc_ip=\$(kubectl --context ${params.cpContext} get services -n omec | grep 'spgwc ' | awk '{print \$3}')
        curl -m 5 \$spgwc_ip:9089/metrics 2>/dev/null 1>${metricsDir}/spgwc-metrics-before-tests.log || true
        """
      }
    }

    stage('Test NG40') {
      steps {
        sh label: 'Run NG40 tests', script: """
        ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ${params.ng40VM} '
        ng40forcecleanup all
        cd ${env.ng40Dir}/testlist
        ng40test ${params.ntlFile}
        '
        """
      }
    }
  }
  post {
    always {
      script {
        sh label: 'NG40 cleanup', script: """
        ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ${params.ng40VM} '
        ng40forcecleanup all
        '
        """

        //Get testcase log filename
        testcase_output_filename = sh returnStdout: true, script: """
        ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ${params.ng40VM} '
        ls -Art ${env.ng40Dir}/testlist/log | tail -n 1
        '
        """
        testcase_output_filename = testcase_output_filename.trim()

        //Get number of test cases executed
        testcase_num = sh returnStdout: true, script: """
        ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ${params.ng40VM} '
        grep "Run test case" ${env.ng40Dir}/testlist/log/${testcase_output_filename} | wc -l
        '
        """
        testcase_num = testcase_num.trim()

        //Get number of test cases passed
        passed_testcase_num = sh returnStdout: true, script: """
        ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ${params.ng40VM} '
        grep VERDICT_PASS ${env.ng40Dir}/testlist/log/${testcase_output_filename} | grep -v "Testlist verdict" | wc -l
        '
        """
        passed_testcase_num = passed_testcase_num.trim()

        //Get number of test cases failed
        failed_testcase_num = sh returnStdout: true, script: """
        ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ${params.ng40VM} '
        grep VERDICT_FAIL ${env.ng40Dir}/testlist/log/${testcase_output_filename} | grep -v "Testlist verdict" | wc -l
        '
        """
        failed_testcase_num = failed_testcase_num.trim()

        // Generate csv file
        sh returnStdout: true, script: """
        csv_file=\$(echo ${testcase_output_filename} | cut -d. -f1)".csv"
        echo "failed_cases,passed_cases,planned_cases" > ${ng40LogDir}/\$csv_file
        echo "${failed_testcase_num},${passed_testcase_num},${testcase_num}" >> ${ng40LogDir}/\$csv_file
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
        scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ${params.ng40VM}:${env.ng40Dir}/testlist/log/${testcase_output_filename} ${ng40LogDir}/
        """
        for( String log_name : log_list.split() ) {
          sh returnStdout: true, script: """
          scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ${params.ng40VM}:${env.ng40Dir}/ran/log/${log_name} ${ng40LogDir}/
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
          scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ${params.ng40VM}:${env.ng40Dir}/ran/pcap/${pcap_name} ${ng40PcapDir}/
          """
        }

        //Stop packet capturing on containers
        sh returnStdout: true, script: """
        pkill kubectl-sniff || true
        """

        sh returnStdout: true, script: """
        cd ${ng40PcapDir}
        tar czf pcap.tgz *
        rm *.pcap
        """
        archiveArtifacts artifacts: "${ng40PcapDir}/*", allowEmptyArchive: true
      }
      //Get metrics again
      sh label: 'Get MME metrics after tests', script: """
      mme_ip=\$(kubectl --context ${params.cpContext} get services -n omec | grep 'mme ' | awk '{print \$3}')
      curl -m 5 \$mme_ip:3081/metrics 2>/dev/null 1>${metricsDir}/mme-metrics-after-tests.log || true
      hss_ip=\$(kubectl --context ${params.cpContext} get services -n omec | grep 'hss ' | awk '{print \$3}')
      curl -m 5 \$hss_ip:9089/metrics 2>/dev/null 1>${metricsDir}/hss-metrics-after-tests.log || true
      spgwc_ip=\$(kubectl --context ${params.cpContext} get services -n omec | grep 'spgwc ' | awk '{print \$3}')
      curl -m 5 \$spgwc_ip:9089/metrics 2>/dev/null 1>${metricsDir}/spgwc-metrics-after-tests.log || true
      """
      archiveArtifacts artifacts: "${metricsDir}/*", allowEmptyArchive: true
    }
  }
}
