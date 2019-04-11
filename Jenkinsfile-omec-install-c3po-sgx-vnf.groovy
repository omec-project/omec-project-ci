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

node("${params.executorNode}") {

  def ghRepository = 'omec-project/c3po'

  def install_path = '/home/jenkins'
  def basedir_dp1 = '/home/jenkins'  //for keys installation

  def base_path = '/var/log/cicd'
  def base_folder = 'install'
  def base_log_dir = "${base_path}/${base_folder}"

  def sgx_app = 'sgx'

  def sgx_dir = "${base_log_dir}/${sgx_app}"

  def action_inst = '_install'
  def action_make = '_make'

  def stdout_ext = '.stdout.log'
  def stderr_ext = '.stderr.log'

  def dealer_install_stdout_log = "${sgx_dir}/" + "dealer" + "${action_inst}${stdout_ext}"
  def dealer_install_stderr_log = "${sgx_dir}/" + "dealer" + "${action_inst}${stderr_ext}"
  def dealer_stdout_log = "${sgx_dir}/" + "dealer" + "${action_make}${stdout_ext}"
  def dealer_stderr_log = "${sgx_dir}/" + "dealer" + "${action_make}${stderr_ext}"
  def kms_install_stdout_log = "${sgx_dir}/" + "kms" + "${action_inst}${stdout_ext}"
  def kms_install_stderr_log = "${sgx_dir}/" + "kms" + "${action_inst}${stderr_ext}"
  def kms_stdout_log = "${sgx_dir}/" + "kms" + "${action_make}${stdout_ext}"
  def kms_stderr_log = "${sgx_dir}/" + "kms" + "${action_make}${stderr_ext}"
  def router_install_stdout_log = "${sgx_dir}/" + "router" + "${action_inst}${stdout_ext}"
  def router_install_stderr_log = "${sgx_dir}/" + "router" + "${action_inst}${stderr_ext}"
  def util_stdout_log = "${sgx_dir}/" + "util" + "${action_make}${stdout_ext}"
  def util_stderr_log = "${sgx_dir}/" + "util" + "${action_make}${stderr_ext}"
  def ctf_stdout_log = "${sgx_dir}/" + "ctf" + "${action_make}${stdout_ext}"
  def ctf_stderr_log = "${sgx_dir}/" + "ctf" + "${action_make}${stderr_ext}"
  def cdf_stdout_log = "${sgx_dir}/" + "cdf" + "${action_make}${stdout_ext}"
  def cdf_stderr_log = "${sgx_dir}/" + "cdf" + "${action_make}${stderr_ext}"

  timeout (60) {
    try {

      echo "${params}"

      // to check and update SGX dealer / DP keys
      def mrenclave = ''
      def mrsigner = ''

      stage("VMs check") {
        timeout(1) {
          waitUntil {
            running_vms = sh returnStdout: true, script: """
            virsh list --all
            """
            echo "Running VMs: ${running_vms}"
            return true
          }
        }
        // Clean all logs
        sh returnStdout: true, script: """
        ssh sgx-kms-cdr '
            if [ ! -d "${sgx_dir}" ]; then mkdir -p ${sgx_dir}; fi
            rm -fr ${sgx_dir}/*
            '
        """
      }

      stage("clone c3po for SGX") {
        timeout(20) {
          sh returnStdout: true, script: """
          ssh sgx-kms-cdr '
              if pgrep ctf; then pkill ctf; fi
              if pgrep cdf; then pkill cdf; fi
              if pgrep kms; then pkill kms; fi
              if pgrep -x dealer; then pkill -x dealer; fi
              if pgrep -x dealer-out; then pkill -x dealer-out; fi
              '
          """
          waitUntil {
            sgx_c3po_clone_output = sh returnStdout: true, script: """
            ssh sgx-kms-cdr '
                cd ${install_path}
                rm -rf c3po
                git clone https://github.com/omec-project/c3po.git || exit 1
                cd c3po

                if [ ${params.ghprbGhRepository} = ${ghRepository} ]; then
                    git fetch origin pull/${params.ghprbPullId}/head:jenkins_test || exit 1
                    git rebase master jenkins_test || exit 1
                    git log -1
                fi
                '
            """
            echo "${sgx_c3po_clone_output}"
            return true
          }

          // TODO: temporary, to be removed once https://github.com/omec-project/c3po/pull/19 is merged
          waitUntil {
            sgx_kms_patch_output = sh returnStdout: true, script: """
            ssh sgx-kms-cdr '
                cp -f ${install_path}/wo-config/ias-ra.c ${install_path}/c3po/sgxcdr/kms/App/ias-ra.c
                cp -f ${install_path}/wo-config/ias-ra.c ${install_path}/c3po/sgxcdr/dealer/App/ias-ra.c
                '
            """
            echo "Temporary: " + "${sgx_kms_patch_output}"
            return true
          }

          waitUntil {
            c3po_dealer_output = sh returnStdout: true, script: """
            ssh sgx-kms-cdr 'cd ${install_path}/c3po/sgxcdr/dealer && ./install.sh < ${install_path}/wo-config/sgx-auto-install.txt 1>${dealer_install_stdout_log} 2>${dealer_install_stderr_log}'
            ssh sgx-kms-cdr 'cp -f ${install_path}/wo-config/dealer-in.json ${install_path}/c3po/sgxcdr/dealer/conf/dealer.json'
            """
            echo "${c3po_dealer_output}"
            return true
          }
          sh returnStdout: true, script: """
          ssh sgx-kms-cdr 'cd ${install_path}/c3po/sgxcdr/dealer && make clean && make SGX_MODE=HW SGX_DEBUG=1 1>${dealer_stdout_log} 2>${dealer_stderr_log}'
          """
        }

        // update ca_bundle.h with MRENCLAVE / MRSIGNER
        timeout(1) {
          waitUntil {
            keys = sh returnStdout: true, script: """
            ssh sgx-kms-cdr '
                cd ${install_path}/c3po/sgxcdr/dealer
                output=(\$(./dealer -j conf/dealer.json -x | sed -n \"s/MR.* ://p\"))

                MRENCLAVE=\${output[0]}
                MRSIGNER=\${output[1]}

                echo \$MRENCLAVE
                echo \$MRSIGNER
               '
            """
            //echo "${keys}"
            (mrenclave, mrsigner) = keys.trim().split("\n")
            echo "${mrenclave}"
            echo "${mrsigner}"
            return true
          }

          waitUntil {
            ca_bundle_output = sh returnStdout: true, script: """
            ssh sgx-kms-cdr '
                cd ${install_path}/c3po/sgxcdr/kms/Enclave
                sed -i \"0,/\\\"\\\",/{s/\\\"\\\",/\\\"${mrenclave}\\\",/}\" ca_bundle.h
                sed -i \"0,/\\\"\\\",/{s/\\\"\\\",/\\\"${mrsigner}\\\",/}\" ca_bundle.h
               '
            """
            echo "${ca_bundle_output}"
            return true
          }
        }

        timeout(5) {
          waitUntil {
            c3po_kms_output = sh returnStdout: true, script: """
            ssh sgx-kms-cdr 'cd ${install_path}/c3po/sgxcdr/kms && ./install.sh < ${install_path}/wo-config/sgx-auto-install.txt 1>${kms_install_stdout_log} 2>${kms_install_stderr_log}'
            ssh sgx-kms-cdr 'cp -f ${install_path}/wo-config/kms.json ${install_path}/c3po/sgxcdr/kms/conf/kms.json'
            """
            echo "${c3po_kms_output}"
            return true
          }
          sh returnStdout: true, script: """
          ssh sgx-kms-cdr 'cd ${install_path}/c3po/sgxcdr/kms && make clean && make SGX_MODE=HW SGX_DEBUG=1 1>${kms_stdout_log} 2>${kms_stderr_log}'
          """
        }

        timeout(5) {
          waitUntil {
            c3po_dealer_out_output = sh returnStdout: true, script: """
            ssh sgx-kms-cdr 'cd ${install_path}/c3po/sgxcdr && cp -R dealer dealer-out'

            ssh sgx-kms-cdr 'cd ${install_path}/c3po/sgxcdr/dealer-out && mv dealer dealer-out'
            ssh sgx-kms-cdr 'cd ${install_path}/c3po/sgxcdr/dealer-out && mkdir cdr'
            ssh sgx-kms-cdr 'cp -f ${install_path}/wo-config/dealer-out.json ${install_path}/c3po/sgxcdr/dealer-out/conf/dealer.json'
            """
            echo "${c3po_dealer_out_output}"
            return true
          }
        }

        timeout(2) {
          waitUntil {
            c3po_router_output = sh returnStdout: true, script: """
            ssh sgx-kms-cdr 'cd ${install_path}/c3po/sgxcdr/router && ./install.sh 1>${router_install_stdout_log} 2>${router_install_stderr_log}'
            """
            echo "${c3po_router_output}"
            return true
          }
        }

        timeout(5) {
          waitUntil {
            c3po_ctf_output = sh returnStdout: true, script: """
            ssh sgx-kms-cdr 'cp -f ${install_path}/wo-config/ctf.json ${install_path}/c3po/ctf/conf/ctf.json'
            """
            echo "${c3po_ctf_output}"
            return true
          }
          sh returnStdout: true, script: """
          ssh sgx-kms-cdr 'cd ${install_path}/c3po/util && make clean && make 1>${util_stdout_log} 2>${util_stderr_log}'
          ssh sgx-kms-cdr 'cd ${install_path}/c3po/ctf && mkdir logs && make clean && make 1>${ctf_stdout_log} 2>${ctf_stderr_log}'
          """
          waitUntil {
            c3po_ctf_conf_output = sh returnStdout: true, script: """
            ssh sgx-kms-cdr 'cd ${install_path}/c3po/ctf/conf && ../bin/make_certs.sh ctf test3gpp.net'
            """
            echo "${c3po_ctf_conf_output}"
            return true
          }
        }

        timeout(5) {
          waitUntil {
            c3po_cdf_output = sh returnStdout: true, script: """
            ssh sgx-kms-cdr 'cp -f ${install_path}/wo-config/cdf.conf ${install_path}/c3po/cdf/conf/cdf.conf'
            """
            echo "${c3po_cdf_output}"
            return true
          }
          sh returnStdout: true, script: """
          ssh sgx-kms-cdr 'cd ${install_path}/c3po/cdf && make clean && make 1>${cdf_stdout_log} 2>${cdf_stderr_log}'
          """
          waitUntil {
            c3po_cdf_conf_output = sh returnStdout: true, script: """
            ssh sgx-kms-cdr 'cd ${install_path}/c3po/cdf/conf && ../bin/make_certs.sh sgx-kms-cdr test3gpp.net'
            """
            echo "${c3po_cdf_conf_output}"
            return true
          }
        }
      }

      // update MRENCLAVE / MRSIGNER in ngic-rtc/config/interface.cfg
      timeout(1) {
        waitUntil {
          ngic_rtc_output = sh returnStdout: true, script: """
          ssh ngic-dp1 '
              cd ${basedir_dp1}/ngic-rtc/config

              MRENCLAVE=${mrenclave}
              MRSIGNER=${mrsigner}

              sed -i \"s/dealer_in_mrenclave *= *.*/dealer_in_mrenclave = \${MRENCLAVE}/\" interface.cfg
              sed -i \"s/dealer_in_mrsigner *= *.*/dealer_in_mrsigner = \${MRSIGNER}/\" interface.cfg

              grep -P \"dealer_in_mrenclave|dealer_in_mrsigner\" interface.cfg
             '
          """
          echo "${ngic_rtc_output}"
          return true
        }
      }

      currentBuild.result = 'SUCCESS'
    } catch (err) {
      currentBuild.result = 'FAILURE'
    } finally {

      sh returnStdout: true, script: """
      rm -fr *
      """

      try {
        sh returnStdout: true, script: """
        scp -r sgx-kms-cdr:${sgx_dir} .
        """
      } catch (err) {}

      archiveArtifacts artifacts: "**/*${stdout_ext}", allowEmptyArchive: true
      archiveArtifacts artifacts: "**/*${stderr_ext}", allowEmptyArchive: true

    }
    echo "RESULT: ${currentBuild.result}"
  }
}