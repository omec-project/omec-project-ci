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

// Jenkinsfile-omec-install-ngic-rtc-vnf.groovy

pipeline {
  /* no label, executor is determined by parameter */
  agent {
    label "${params.executorNode}"
  }

  stages {

    stage ("Checkout Pull Request") {
      steps {
        checkout(changelog: false, \
          poll: false,
          scm: [$class: 'GitSCM', \
            userRemoteConfigs: [[ \
              url: "https://github.com/omec-project/${params.project}", \
              name: 'origin', \
              refspec: "+refs/pull/*:refs/remotes/origin/pr/*", \
            ]], \
            branches: [[name: "${params.ghprbActualCommit}"]], \
            ])
      }
    }

    stage ("Basic shell examples") {
      steps {
        sh """
           #!/usr/bin/env bash

           # enable "safe mode" - will exit immediately on command failures
           set -eux -o pipefail

           echo "Example shell commands"
           git log
           ls -la

           echo "Environmental Variables"
           set
           """
      }
    }

  } // end stages
} // end pipeline

