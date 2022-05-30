pipeline {
    agent {
        node {
            label "docker-rockylinux-8.4-node"
            customWorkspace "/root/workspace/${JOB_BASE_NAME}"
        }
    }

    options {
        timeout(time: 240, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm')
    }

    stages {
        stage('SETUP') {
            steps {
                script { build_stage = env.STAGE_NAME }
                cleanWs()
                script {
                    sh script: '''
rm -rf * 
losetup -D                         
yum remove cortx-hare cortx-motr{,-devel} cortx-py-utils consul -y                         
rm -rf /var/crash/* /var/log/seagate/* /var/log/hare/* /var/log/motr/* /var/lib/hare/* /var/motr/* /etc/motr/*
rm -rf /root/.cache/dhall* /root/rpmbuild
rm -rf /etc/yum.repos.d/motr_last_successful.repo /etc/yum.repos.d/motr_uploads.repo /etc/yum.repos.d/lustre_release.repo                        
rm -rf /etc/yum.repos.d/motr_last_successful.repo
rm -rf /etc/yum.repos.d/motr_uploads.repo
REPO_PATH='http://cortx-storage.colo.seagate.com//releases/cortx/github/main/rockylinux-8.4/'

cat <<EOF > /etc/yum.repos.d/motr_last_successful.repo
[motr-dev]
baseurl=$REPO_PATH/last_successful/
gpgcheck=0
name=motr-dev
enabled=1
EOF
cat /etc/yum.repos.d/motr_last_successful.repo
cat <<EOF > /etc/yum.repos.d/motr_uploads.repo
[motr-uploads]
baseurl=$REPO_PATH/last_successful_prod/3rd_party/motr/
gpgcheck=0
name=motr-uploads
enabled=1
EOF
cat /etc/yum.repos.d/motr_uploads.repo
# Hashicorp Consul repo
yum -y install yum-utils
yum-config-manager --add-repo https://rpm.releases.hashicorp.com/RHEL/hashicorp.repo

yum erase python36-PyYAML -y
cat <<EOF >>/etc/pip.conf
[global]
timeout: 60
index-url: http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/python-deps/python-packages-2.0.0-latest/
trusted-host: cortx-storage.colo.seagate.com
EOF
cat /etc/pip.conf
pip3 install -r https://raw.githubusercontent.com/Seagate/cortx-utils/main/py-utils/python_requirements.txt
pip3 install -r https://raw.githubusercontent.com/Seagate/cortx-utils/main/py-utils/python_requirements.ext.txt
rm -rf /etc/pip.conf
yum-config-manager --add-repo=http://cortx-storage.colo.seagate.com/releases/cortx/github/main/rockylinux-8.4/last_successful_prod/cortx_iso/
yum clean all;rm -rf /var/cache/yum
yum install cortx-py-utils cortx-motr{,-devel} -y --nogpgcheck
pip3 install coverage

'''
                    checkout([$class: 'GitSCM', branches: [[name: "Hare_Code_Coverage"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "https://github.com/pavankrishnat/cortx-hare"]]])

                    echo "--------------BUILD PARAMETERS -------------------"
                    echo "CORTX_HARE_REPO       = https://github.com/pavankrishnat/cortx-hare"
                    echo "CORTX_HARE_BRANCH     = Hare_Code_Coverage"
                    echo "-----------------------------------------------------------"
                }
            }
        }

        stage('COVERAGE') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh script: '''
make check
cd hax/
tar -zcvf Hare_code_cov.tar.gz htmlcov
readlink -f Hare_code_cov.tar.gz
#html_index_file=$(readlink -f htmlcov/index.html)
'''
            }
        }
    }
    post {
        always {
            script {
                archiveArtifacts artifacts: 'hax/Hare_code_cov.tar.gz'
                CoverageReport = ""
                if (fileExists("/root/workspace/Hare_Code_Coverage/hax/htmlcov/index.html") && currentBuild.currentResult == "SUCCESS") {
                    CoverageReport = readFile(file: "/root/workspace/Hare_Code_Coverage/hax/htmlcov/index.html")
                    MESSAGE = "${build_id}: Generated Hare Code Coverage report successgully"
                    ICON = "accept.gif"
                    STATUS = "SUCCESS"
                }else if ( currentBuild.currentResult == "FAILURE" ) {
                    manager.buildFailure()
                    MESSAGE = "${build_id}: Failed to generate Hare Code Coverage."
                    ICON = "error.gif"
                    STATUS = "FAILURE"
                }
                manager.createSummary("${ICON}").appendText("${CoverageReport}", false, false, false, "red")

                def recipientProvidersClass = [[$class: 'RequesterRecipientProvider']]
                def toEmail = ""
                emailext (
                        body: '''${SCRIPT}''',
                        mimeType: 'text/html',
                        subject: "Hare Code Coverage Build ${currentBuild.currentResult}",
                        attachLog: true,
                        to: toEmail,
                        recipientProviders: recipientProvidersClass
                )
            }
        }
    }
}