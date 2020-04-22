<!--
Copyright 2019-present Open Networking Foundation

SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# OMEC Project CI

This repo contains automation used for testing and continuous integration for
OMEC Project repos.

## Code review process

OMEC uses Github's Pull Request process coupled the [CORD
Jenkins](https://jenkins.opencord.org/) which is part of the [CORD
Project](https://wiki.opencord.org/).

If you'd like to make a code contribution:

1. Clone a copy of (or make a fork of) one of the [OMEC-Project
   repos](https://github.com/omec-project), make changes using git then submit
   a Pull Request on the main OMEC repo.

2. One of the [OMEC Project
   members](https://github.com/orgs/omec-project/people) will review your
   patchset.  If it's approved, a comment with `ok to test` will be left on the
   pull request, and a [OMEC Jenkins
   Job](https://jenkins.opencord.org/view/OMEC/) will be started to test your
   patchset.

3. If the patchset passes tests, it can be merged into the project.

## Notes for Code Reviewers

OMEC uses the Jenkins [GitHub Pull Request Builder
Plugin](https://github.com/jenkinsci/ghprb-plugin) to handle Pull Request
reviews - see that page for the proper comments to leave to retest and perform
other interactions between Github and Jenkins.

## Developing Jenkinsfiles

Your Jenkinsfiles can be checked with the `jflint.sh` script, which requires
that they use the [Declarative Pipeline
Syntax](https://jenkins.io/doc/book/pipeline/syntax/#declarative-pipeline).

Please use this script to test your Jenkinsfiles before commit.
