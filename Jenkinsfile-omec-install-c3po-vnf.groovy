// Copyright 2017-present Open Networking Foundation
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
node("intel-102") {

  def install_path = '/home/jenkins'
  def basedir_dp1 = '/home/jenkins'  //for keys installation

  def base_path = '/var/log/cicd'
  def base_folder = 'install'
  def base_log_dir = "${base_path}/${base_folder}"

  def hss_app = 'hss'
  def sgx_app = 'sgx'

  def hss_dir = "${base_log_dir}/${hss_app}"
  def sgx_dir = "${base_log_dir}/${sgx_app}"

  def action_inst = '_install'
  def action_make = '_make'

  def std_ext = '.stdout.log'
  def err_ext = '.stderr.log'

  def hss_install_std_log = "${hss_dir}/" + "hss" + "${action_inst}${std_ext}"
  def hss_install_err_log = "${hss_dir}/" + "hss" + "${action_inst}${err_ext}"
  def hsssec_std_log = "${hss_dir}/" + "hsssec" + "${action_make}${std_ext}"
  def hsssec_err_log = "${hss_dir}/" + "hsssec" + "${action_make}${err_ext}"
  def hss_std_log = "${hss_dir}/" + "hss" + "${action_make}${std_ext}"
  def hss_err_log = "${hss_dir}/" + "hss" + "${action_make}${err_ext}"

  def dealer_install_std_log = "${sgx_dir}/" + "dealer" + "${action_inst}${std_ext}"
  def dealer_install_err_log = "${sgx_dir}/" + "dealer" + "${action_inst}${err_ext}"
  def dealer_std_log = "${sgx_dir}/" + "dealer" + "${action_make}${std_ext}"
  def dealer_err_log = "${sgx_dir}/" + "dealer" + "${action_make}${err_ext}"
  def kms_install_std_log = "${sgx_dir}/" + "kms" + "${action_inst}${std_ext}"
  def kms_install_err_log = "${sgx_dir}/" + "kms" + "${action_inst}${err_ext}"
  def kms_std_log = "${sgx_dir}/" + "kms" + "${action_make}${std_ext}"
  def kms_err_log = "${sgx_dir}/" + "kms" + "${action_make}${err_ext}"
  def router_install_std_log = "${sgx_dir}/" + "router" + "${action_inst}${std_ext}"
  def router_install_err_log = "${sgx_dir}/" + "router" + "${action_inst}${err_ext}"
  def util_std_log = "${sgx_dir}/" + "util" + "${action_make}${std_ext}"
  def util_err_log = "${sgx_dir}/" + "util" + "${action_make}${err_ext}"
  def ctf_std_log = "${sgx_dir}/" + "ctf" + "${action_make}${std_ext}"
  def ctf_err_log = "${sgx_dir}/" + "ctf" + "${action_make}${err_ext}"
  def cdf_std_log = "${sgx_dir}/" + "cdf" + "${action_make}${std_ext}"
  def cdf_err_log = "${sgx_dir}/" + "cdf" + "${action_make}${err_ext}"

  timeout (60) {
    try {

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
        timeout(1) {
          waitUntil {
            running_vms = sh returnStdout: true, script: """
            virsh list --all | grep c3po-hss1 | grep -i running | wc -l
            """
            return running_vms.toInteger() == 1
          }
        }
      }

      stage("clone c3po for HSS") {
        timeout(20) {

          //TODO: uncomment
          //kill process? ssh c3po-hss1 'if pgrep -f [h]ss; then pkill -f [h]ss; fi'
          sh returnStdout: true, script: """
          ssh c3po-hss1 'rm -fr ${hss_dir}/*'
          ssh sgx-kms-cdr 'rm -fr ${sgx_dir}/*'
          """

          waitUntil {
            hss_c3po_clone_output = sh returnStdout: true, script: """
            ssh c3po-hss1 'cd ${install_path} && rm -rf c3po && git clone https://github.com/omec-project/c3po.git'
            """
            echo "${hss_c3po_clone_output}"
            return true
          }

          waitUntil {
            hss_dealer_in_output = sh returnStdout: true, script: """
            ssh c3po-hss1 'cd ${install_path}/c3po && ./install.sh < ${install_path}/wo-config/hss-auto-install.txt 1>${hss_install_std_log} 2>${hss_install_err_log}'
            """
            echo "${hss_dealer_in_output}"
            return true
          }
          sh returnStdout: true, script: """
          ssh c3po-hss1 'cd ${install_path}/c3po/util && make clean && make 1>${hsssec_std_log} 2>${hsssec_err_log}'
          ssh c3po-hss1 'cd ${install_path}/c3po/hsssec && make clean && make 1>>${hsssec_std_log} 2>>${hsssec_err_log}'
          """

          waitUntil {
            hss_hss_output = sh returnStdout: true, script: """
            ssh c3po-hss1 'cp -f ${install_path}/wo-config/hss.json ${install_path}/c3po/hss/conf/hss.json'

            ssh c3po-hss1 'cd ${install_path}/c3po/hss/conf && ../bin/make_certs.sh hss openair4G.eur'
            """
            echo "${hss_hss_output}"
            return true
          }
          sh returnStdout: true, script: """
          ssh c3po-hss1 'cd ${install_path}/c3po/hss && make clean && make 1>${hss_std_log} 2>${hss_err_log}'
          """
        }
      }
      stage("clone c3po for SGX") {
        timeout(20) {
          //TODO: kill processes?
          waitUntil {
            sgx_c3po_clone_output = sh returnStdout: true, script: """
            ssh sgx-kms-cdr 'cd ${install_path} && rm -rf c3po && git clone https://github.com/omec-project/c3po.git'
            """
            echo "${sgx_c3po_clone_output}"
            return true
          }

          waitUntil {
            c3po_dealer_output = sh returnStdout: true, script: """
            ssh sgx-kms-cdr 'cd ${install_path}/c3po/sgxcdr/dealer && ./install.sh < ${install_path}/wo-config/sgx-auto-install.txt 1>${dealer_install_std_log} 2>${dealer_install_err_log}'
            ssh sgx-kms-cdr 'cp -f ${install_path}/wo-config/dealer-in.json ${install_path}/c3po/sgxcdr/dealer/conf/dealer.json'
            """
            echo "${c3po_dealer_output}"
            return true
          }
          sh returnStdout: true, script: """
          ssh sgx-kms-cdr 'cd ${install_path}/c3po/sgxcdr/dealer && make clean && make SGX_MODE=HW SGX_DEBUG=1 1>${dealer_std_log} 2>${dealer_err_log}'
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
            ssh sgx-kms-cdr 'cd ${install_path}/c3po/sgxcdr/kms && ./install.sh < ${install_path}/wo-config/sgx-auto-install.txt 1>${kms_install_std_log} 2>${kms_install_err_log}'
            ssh sgx-kms-cdr 'cp -f ${install_path}/wo-config/kms.json ${install_path}/c3po/sgxcdr/kms/conf/kms.json'
            """
            echo "${c3po_kms_output}"
            return true
          }
          sh returnStdout: true, script: """
          ssh sgx-kms-cdr 'cd ${install_path}/c3po/sgxcdr/kms && make clean && make SGX_MODE=HW SGX_DEBUG=1 1>${kms_std_log} 2>${kms_err_log}'
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
            ssh sgx-kms-cdr 'cd ${install_path}/c3po/sgxcdr/router && ./install.sh 1>${router_install_std_log} 2>${router_install_err_log}'
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
          ssh sgx-kms-cdr 'cd ${install_path}/c3po/util && make clean && make 1>${util_std_log} 2>${util_err_log}'
          ssh sgx-kms-cdr 'cd ${install_path}/c3po/ctf && mkdir logs && make clean && make 1>${ctf_std_log} 2>${ctf_err_log}'
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
          ssh sgx-kms-cdr 'cd ${install_path}/c3po/cdf && make clean && make 1>${cdf_std_log} 2>${cdf_err_log}'
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
      rm -fr ${hss_app}
      rm -fr ${sgx_app}
      rm -f cicd_*.stdout.log
      rm -f cicd_*.stderr.log
      """

      try {
        sh returnStdout: true, script: """
        scp -r c3po-hss1:${hss_dir} .
        """
      } catch (err) {
      }

      try {
        sh returnStdout: true, script: """
        scp -r sgx-kms-cdr:${sgx_dir} .
        """
      } catch (err) {
      }

      archiveArtifacts artifacts: "**/*${std_ext}", allowEmptyArchive: true
      archiveArtifacts artifacts: "**/*${err_ext}", allowEmptyArchive: true

    }
    echo "RESULT: ${currentBuild.result}"
  }
}