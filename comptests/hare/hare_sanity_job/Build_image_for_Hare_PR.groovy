pipeline {
    agent { label "docker-${OS_VERSION}-node" }

    options {
        timeout(50)  // abort the build after that many minutes
        timestamps()
        ansiColor('xterm')
    }

    parameters {
        string(name: 'HARE_BRANCH', defaultValue: 'main', description: '  Branch for Hare', trim: true)
        string(name: 'HARE_URL', defaultValue: 'https://github.com/Seagate/cortx-hare', description: 'Hare Repository for Image creation', trim: true)
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'Hare_Job', description: 'Branch or GitHash for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/pavankrishnat/cortx-re/', description: 'Repository for Cluster Setup scripts', trim: true)
        string(name: 'BRANCH', defaultValue: 'main', description: '  Branch from Hare PR, if available', trim: true)
        string(name: 'HARE_PR_REFSPEC', defaultValue: "+refs/heads/*:refs/remotes/origin/*", description: 'REFSPEC from Hare PR, if available', trim: true)
    }

    environment {
//////////////////////////////// BUILD VARS //////////////////////////////////////////////////
// OS_VERSION, COMPONENTS_BRANCH and CORTX_SCRIPTS_BRANCH are manually created parameters in jenkins job.

        COMPONENT_NAME = "hare".trim()
        THIRD_PARTY_VERSION = "${OS_VERSION}-2.0.0-k8"
        VERSION = "2.0.0"
        RELEASE_TAG = "last_successful_prod"
        PASSPHARASE = credentials('rpm-sign-passphrase')

        OS_FAMILY=sh(script: "echo '${OS_VERSION}' | cut -d '-' -f1", returnStdout: true).trim()

// Artifacts root location

// 'WARNING' - rm -rf command used on this path please careful when updating this value
        DESTINATION_RELEASE_LOCATION = "/mnt/bigstorage/releases/cortx/github/pr-build/${BRANCH}/${COMPONENT_NAME}/${BUILD_NUMBER}"
        PYTHON_DEPS = "/mnt/bigstorage/releases/cortx/third-party-deps/python-deps/python-packages-2.0.0-latest"
        THIRD_PARTY_DEPS = "/mnt/bigstorage/releases/cortx/third-party-deps/${OS_FAMILY}/${THIRD_PARTY_VERSION}/"
        COMPONENTS_RPM = "/mnt/bigstorage/releases/cortx/components/github/${BRANCH}/${OS_VERSION}/dev/"
        CORTX_BUILD = "http://cortx-storage.colo.seagate.com/releases/cortx/github/pr-build/${BRANCH}/${COMPONENT_NAME}/${BUILD_NUMBER}"

// Artifacts location
        CORTX_ISO_LOCATION = "${DESTINATION_RELEASE_LOCATION}/cortx_iso"
        THIRD_PARTY_LOCATION = "${DESTINATION_RELEASE_LOCATION}/3rd_party"
        PYTHON_LIB_LOCATION = "${DESTINATION_RELEASE_LOCATION}/python_deps"

        ////////////////////////////////// DEPLOYMENT VARS /////////////////////////////////////////////////////
        //STAGE_DEPLOY = "yes"
    }

    stages {
        stage('BUILD HARE IMAGE') {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    // Build hare fromm PR source code
                    script { manager.addHtmlBadge("&emsp;<b>Target Branch : ${BRANCH}</b>&emsp;<br />") }
                    dir("hare") {
                        checkout([$class: 'GitSCM', branches: [[name: "${HARE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', depth: 0, noTags: false, reference: '', shallow: false, timeout: 15], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false, timeout: 15]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${HARE_URL}", name: 'origin', refspec: "${HARE_PR_REFSPEC}"]]])

                        sh label: '', script: '''
            yum remove cortx-hare cortx-motr{,-devel} cortx-py-utils consul -y
            rm -rf /var/crash/* /var/log/seagate/* /var/log/hare/* /var/log/motr/* /var/lib/hare/* /var/motr/* /etc/motr/*
            rm -rf /root/.cache/dhall* /root/rpmbuild
            rm -rf /etc/yum.repos.d/cortx-storage*
            yum erase python36-PyYAML -y
            cat <<EOF > /etc/yum.repos.d/cortx-storage_motr_uploads.repo
[motr-uploads]
baseurl=http://cortx-storage.colo.seagate.com/releases/cortx/github/$BRANCH/$OS_VERSION/$RELEASE_TAG/3rd_party/motr/
gpgcheck=0
name=cortx-storage_motr_uploads
enabled=1
EOF
            cat <<EOF >>/etc/pip.conf
[global]
timeout: 60
index-url: http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/python-deps/python-packages-2.0.0-latest/
trusted-host: cortx-storage.colo.seagate.com
EOF
            pip3 install -r https://raw.githubusercontent.com/Seagate/cortx-utils/$BRANCH/py-utils/python_requirements.txt
            pip3 install -r https://raw.githubusercontent.com/Seagate/cortx-utils/$BRANCH/py-utils/python_requirements.ext.txt
            rm -rf /etc/pip.conf
            '''
                        sh label: 'prepare build env', script: """
                yum-config-manager --add-repo=http://cortx-storage.colo.seagate.com/releases/cortx/github/$BRANCH/$OS_VERSION/$RELEASE_TAG/cortx_iso/
                yum-config-manager --save --setopt=cortx-storage*.gpgcheck=1 cortx-storage* && yum-config-manager --save --setopt=cortx-storage*.gpgcheck=0 cortx-storage*
                yum clean all;rm -rf /var/cache/yum
                yum install cortx-py-utils cortx-motr{,-devel} -y
                # yum install --skip-broken cortx-py-utils cortx-motr{,-devel} -y --nogpgcheck
                # yum install bzip2 rpm-build -y
            """
                        sh label: 'Build', script: '''
                set -xe
                echo "Executing build script"
                export build_number=${BUILD_NUMBER}
                make VERSION=$VERSION rpm
            '''
                    }
                    // Release cortx deployment stack
                    dir('cortx-re') {
                        checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', depth: 1, honorRefspec: true, noTags: true, reference: '', shallow: true], [$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RE_REPO}"]]])
                    }
                    // Install tools required for release process
                    sh label: 'Installed Dependecies', script: '''
            yum install -y expect rpm-sign rng-tools python3-pip
        '''
                    // Integrate components rpms
                    sh label: 'Collect Release Artifacts', script: '''
            rm -rf "${DESTINATION_RELEASE_LOCATION}"
            mkdir -p "${DESTINATION_RELEASE_LOCATION}"
            if [[ ( ! -z `ls /root/rpmbuild/RPMS/x86_64/*.rpm `)]]; then
                mkdir -p "${CORTX_ISO_LOCATION}"
                cp /root/rpmbuild/RPMS/x86_64/*.rpm "${CORTX_ISO_LOCATION}"
            else
                echo "RPM not exists !!!"
                exit 1
            fi
            pushd ${COMPONENTS_RPM}
                for component in `ls -1 | grep -E -v "${COMPONENT_NAME}"`
                do
                    echo -e "Copying RPM's for $component"
                    if ls $component/last_successful/*.rpm 1> /dev/null 2>&1; then
                        cp $component/last_successful/*.rpm "${CORTX_ISO_LOCATION}"
                    fi
                done
            popd
            # Symlink 3rdparty repo artifacts
            ln -s "${THIRD_PARTY_DEPS}" "${THIRD_PARTY_LOCATION}"

            # Symlink python dependencies
            ln -s "${PYTHON_DEPS}" "${PYTHON_LIB_LOCATION}"
        '''
                    sh label: 'Create repo', script: '''
            pushd ${CORTX_ISO_LOCATION}
                yum install -y createrepo
                createrepo .
            popd
        '''
                    sh label: 'Generate RELEASE.INFO', script: '''
            pushd cortx-re/scripts/release_support
                sh build_readme.sh "${DESTINATION_RELEASE_LOCATION}"
                sh build_release_info.sh -v ${VERSION} -l ${CORTX_ISO_LOCATION} -t ${THIRD_PARTY_LOCATION}
                sed -i -e 's/BRANCH:.*/BRANCH: "hare-pr"/g' ${CORTX_ISO_LOCATION}/RELEASE.INFO
            popd
            cp "${THIRD_PARTY_LOCATION}/THIRD_PARTY_RELEASE.INFO" "${DESTINATION_RELEASE_LOCATION}"
            cp "${CORTX_ISO_LOCATION}/RELEASE.INFO" "${DESTINATION_RELEASE_LOCATION}"
            cp "${CORTX_ISO_LOCATION}/RELEASE.INFO" .
        '''
                    //archiveArtifacts artifacts: "RELEASE.INFO", onlyIfSuccessful: false, allowEmptyArchive: true
                    try {
                        def buildCortxAllImage = build job: 'Cortx-PR-Build/Cortx-Deployment/Generic/cortx-docker-images-for-PR', wait: true,
                                parameters: [
                                        string(name: 'CORTX_RE_URL', value: "${CORTX_RE_REPO}"),
                                        string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                                        string(name: 'BUILD', value: "${CORTX_BUILD}"),
                                        string(name: 'OS', value: "${OS_VERSION}"),
                                        string(name: 'CORTX_IMAGE', value: "all"),
                                        string(name: 'GITHUB_PUSH', value: "yes"),
                                        string(name: 'TAG_LATEST', value: "no"),
                                        string(name: 'DOCKER_REGISTRY', value: "cortx-docker.colo.seagate.com"),
                                        string(name: 'EMAIL_RECIPIENTS', value: "DEBUG")
                                ]
                        env.cortxbuild_build_id = "$buildCortxAllImage.id"
                        env.cortxbuild_all = buildCortxAllImage.buildVariables.cortx_all_image
                        env.cortxbuild_rgw = buildCortxAllImage.buildVariables.cortx_rgw_image
                        env.cortxbuild_data = buildCortxAllImage.buildVariables.cortx_data_image
                        env.cortxbuild_control = buildCortxAllImage.buildVariables.cortx_control_image
                    } catch (err) {
                        build_stage = env.STAGE_NAME
                        error "Failed to Build CORTX-ALL image"
                    }
                }
            }
        }
    }
    post {
        always {
            echo 'Cleanup Workspace.'
        }
    }
}
