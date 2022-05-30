pipeline {
    agent {
        node {
            label "${CLIENT_NODE}"
            customWorkspace "/root/workspace/${JOB_BASE_NAME}"
        }
    }

    options {
        timeout(time: 240, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '50'))
        ansiColor('xterm')
    }

    parameters {
        string(name: 'CORTX_HARE_BRANCH', defaultValue: 'main', description: '  Branch for Hare', trim: true)
        string(name: 'CORTX_HARE_REPO', defaultValue: 'https://github.com/Seagate/cortx-hare', description: 'Hare Repository for Image creation', trim: true)
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'Hare_Job', description: 'Branch or GitHash for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/pavankrishnat/cortx-re/', description: 'Repository for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_TEST_BRANCH', defaultValue: 'Hare_QA_Job', description: 'Branch or GitHash for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_TEST_REPO', defaultValue: 'https://github.com/pavankrishnat/cortx-test-1', description: 'Repository for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_SERVER_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-rgw:2.0.0-latest', description: 'CORTX-SERVER image', trim: true)
        string(name: 'CORTX_DATA_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-data:2.0.0-latest', description: 'CORTX-DATA image', trim: true)
        string(name: 'CORTX_CONTROL_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-control:2.0.0-latest', description: 'CORTX-CONTROL image', trim: true)
        string(name: 'SNS_CONFIG', defaultValue: '4+2+2', description: 'sns configuration for deployment. Please select value based on disks available on nodes.', trim: true)
        string(name: 'DIX_CONFIG', defaultValue: '1+2+2', description: 'dix configuration for deployment. Please select value based on disks available on nodes.', trim: true)
        string(name: 'MOTR_CLIENT', defaultValue: '0', description: 'Motr Client number for deployment. Please select value 0,1,2 only.', trim: true)
        string(name: 'CLIENT_NODE', defaultValue: 'ssc-vm-rhev4-2420-hare', description: 'Enter a Machine label where job will get executed', trim: true)
        string(name: 'M_NODE', defaultValue: 'ssc-vm-g2-rhev4-3161.colo.seagate.com', description: 'Enter a Master Node hostname', trim: true)
        string(name: 'HOST_PASS', defaultValue: 'seagate', description: 'Enter a Master Node password', trim: true)
        string(name: 'ADMIN_USR', defaultValue: 'cortxadmin', description: 'Enter CSM user name', trim: true)
        string(name: 'ADMIN_PWD', defaultValue: 'Cortxadmin@123', description: 'Enter CSM user password', trim: true)
        string(name: 'MANUAL_TEST', defaultValue: 'None', description: 'Run a selective test', trim: true)
        string(name: 'CONTROL_EXTERNAL_NODEPORT', defaultValue: '31169', description: 'Port to be used for control service.', trim: true)
        string(name: 'S3_EXTERNAL_HTTP_NODEPORT', defaultValue: '30080', description: 'HTTP Port to be used for IO service.', trim: true)
        string(name: 'S3_EXTERNAL_HTTPS_NODEPORT', defaultValue: '30443', description: 'HTTPS to be used for IO service.', trim: true)
        string(name: 'NAMESPACE', defaultValue: 'cortx', description: 'kubernetes namespace to be used for CORTX deployment.', trim: true)
        string(name: 'EXTERNAL_EXPOSURE_SERVICE', defaultValue: 'NodePort', description: 'External service for S3, NodePort or LoadBalancer as set while deployment.', trim: true)
        booleanParam(name: 'PODS_ON_PRIMARY', defaultValue: false, description: 'Schedule PODs on Primary node')
        booleanParam(name: 'RUN_K8_SETUP', defaultValue: true, description: 'Mark tick if you want to run Kubernets cluster')
        booleanParam(name: 'BUILD_CUSTOM_IMAGE', defaultValue: true, description: 'Mark tick if you want to create custom build')
        booleanParam(name: 'RUN_DEPLOY', defaultValue: true, description: 'Mark tick if you want to Deploy cluster')
        booleanParam(name: 'RUN_QA_SANITY', defaultValue: true, description: 'Mark tick if you want to run CFT Sanity tests')
        booleanParam(name: 'RUN_HARE_SANITY', defaultValue: true, description: 'Mark tick if you want to run Motr Sanity tests')
        booleanParam(name: 'RUN_MANUAL_TEST', defaultValue: false, description: 'Mark tick if you want to run Any Manual tests')
        text(defaultValue: '''hostname=ssc-vm-g2-rhev4-3161.colo.seagate.com,user=root,pass=seagate
hostname=ssc-vm-g2-rhev4-3162.colo.seagate.com,user=root,pass=seagate
hostname=ssc-vm-g2-rhev4-3163.colo.seagate.com,user=root,pass=seagate
hostname=ssc-vm-g2-rhev4-3164.colo.seagate.com,user=root,pass=seagate
hostname=ssc-vm-g2-rhev4-3165.colo.seagate.com,user=root,pass=seagate
hostname=ssc-vm-g2-rhev4-3166.colo.seagate.com,user=root,pass=seagate
''', description: 'VM details to be used for CORTX cluster setup. First node will be used as Master hostname=<hostname>,user=<user>,pass=<password>', name: 'hosts')
        // Please configure CORTX_SCRIPTS_BRANCH and CORTX_SCRIPTS_REPO parameter in Jenkins job configuration.

    }

    environment {
        GITHUB_CRED = credentials('shailesh-github-token')
        Target_Node = 'multi-node-' + "${"${M_NODE}".split("\\.")[0]}"
        Build_Branch = "${"${CORTX_SERVER_IMAGE}".split(":")[0]}"
        Build_VER = "${"${CORTX_SERVER_IMAGE}".split(":")[1]}"

        // Hare Repo Info
        // GITHUB_TOKEN = credentials('cortx-admin-github') // To clone cortx-hare repo
        GPR_REPO = "https://github.com/${ghprbGhRepository}"
        CORTX_HARE_REPO = "${ghprbGhRepository != null ? GPR_REPO : CORTX_HARE_REPO}"
        CORTX_HARE_BRANCH = "${sha1 != null ? sha1 : CORTX_HARE_BRANCH}"

        BRANCH = "${ghprbTargetBranch != null ? ghprbTargetBranch : CORTX_HARE_BRANCH}"

        HARE_GPR_REFSPEC = "+refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*"
        HARE_BRANCH_REFSPEC = "+refs/heads/*:refs/remotes/origin/*"
        HARE_PR_REFSPEC = "${ghprbPullId != null ? HARE_GPR_REFSPEC : HARE_BRANCH_REFSPEC}"
    }

    stages {

        stage('CHECKOUT SCRIPT') {
            steps {
                script { build_stage = env.STAGE_NAME }
                cleanWs()
                script {
                    checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RE_REPO}"]]])

                    echo "--------------BUILD PARAMETERS -------------------"
                    echo "CORTX_HARE_REPO       = ${CORTX_HARE_REPO}"
                    echo "CORTX_HARE_BRANCH     = ${CORTX_HARE_BRANCH}"
                    echo "HARE_PR_REFSPEC       = ${HARE_PR_REFSPEC}"
                    echo "Value of RUN_K8       = ${RUN_K8_SETUP}"
                    echo "Value of RUN_Deploy   = ${RUN_DEPLOY}"
                    echo "Value of RUN_Sanity   = ${RUN_QA_SANITY}"
                    echo "Value of RUN_HARE     = ${RUN_HARE_SANITY}"
                    echo "Value of RUN_Manual   = ${RUN_MANUAL_TEST}"
                    echo "Value of Motr_Client  = ${MOTR_CLIENT}"
                    echo "-----------------------------------------------------------"
                    env.cortxbuild_build_id = 0
                }
            }
        }

        stage('BUILD CORTX IMAGE') {
            when {
                allOf {
                    expression { params.RUN_DEPLOY == true }
                    expression { params.BUILD_CUSTOM_IMAGE == true }
                }
            }
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    try {
                        def buildCortxAllImage = build job: 'Hare/Build_image_for_Hare_PR', wait: true,
                                parameters: [
                                        string(name: 'HARE_BRANCH', value: "${CORTX_HARE_BRANCH}"),
                                        string(name: 'HARE_URL', value: "${CORTX_HARE_REPO}"),
                                        string(name: 'BRANCH', value: "${BRANCH}"),
                                        string(name: 'HARE_PR_REFSPEC', value: "${HARE_PR_REFSPEC}")
                                ]
                        env.cortxbuild_build_id = "$buildCortxAllImage.id"
                        env.cortxbuild_all = "cortx-docker.colo.seagate.com/seagate/cortx-all:2.0.0-${env.cortxbuild_build_id}-hare-pr"
                        env.cortxbuild_rgw = "cortx-docker.colo.seagate.com/seagate/cortx-rgw:2.0.0-${env.cortxbuild_build_id}-hare-pr"
                        env.cortxbuild_data = "cortx-docker.colo.seagate.com/seagate/cortx-data:2.0.0-${env.cortxbuild_build_id}-hare-pr"
                        env.cortxbuild_control = "cortx-docker.colo.seagate.com/seagate/cortx-control:2.0.0-${env.cortxbuild_build_id}-hare-pr"

                        echo "--------------Docker IMAGES Details -------------------"
                        echo "cortxbuild_build_id   = ${cortxbuild_build_id}"
                        echo "cortxbuild_all        = ${cortxbuild_all}"
                        echo "cortxbuild_rgw        = ${cortxbuild_rgw}"
                        echo "cortxbuild_data       = ${cortxbuild_data}"
                        echo "cortxbuild_control    = ${cortxbuild_control}"
                        echo "-----------------------------------------------------------"
                    } catch (err) {
                        build_stage = env.STAGE_NAME
                        error "Failed to Build CORTX-ALL image"
                    }
                }
            }
        }

        stage('SETUP CLUSTER') {
            when { expression { params.RUN_K8_SETUP == true } }
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Tag last_successful', script: '''
pushd solutions/kubernetes/
echo $hosts | tr ' ' '\n' > hosts
cat hosts
./cluster-setup.sh ${PODS_ON_PRIMARY}
popd
'''
            }
        }

        stage('DEPLOY CORTX') {
            when { expression { params.RUN_DEPLOY == true } }
            steps {
                echo "Destory Pre-existing Cluster"
                script { build_stage = env.STAGE_NAME }
                sh label: 'Destroy existing Cluster', script: '''
pushd solutions/kubernetes/
echo $hosts | tr ' ' '\n' > hosts
cat hosts
./cortx-deploy.sh --destroy-cluster
popd
'''
                sh label: 'Deploy CORTX Components', script: '''
                    pushd solutions/kubernetes/
                        echo $hosts | tr ' ' '\n' > hosts
                        cat hosts
                        if [ "$(cat hosts | wc -l)" -eq 2 ]
                        then
                           echo "Current configuration does not support 2 node CORTX cluster deployment. Please try with 1 or more than two nodes."
                           echo "Exiting Jenkins job."
                           exit 1
                        fi
                        export SOLUTION_CONFIG_TYPE=automated
                        export CORTX_SCRIPTS_BRANCH=${CORTX_SCRIPTS_BRANCH}
                        export CORTX_SCRIPTS_REPO=${CORTX_SCRIPTS_REPO}
                        if [[ cortxbuild_build_id -ne 0 ]]
                        then
                            export CORTX_SERVER_IMAGE=${cortxbuild_rgw}
                            export CORTX_DATA_IMAGE=${cortxbuild_data}
                            export CORTX_CONTROL_IMAGE=${cortxbuild_control}
                        else
                            export CORTX_SERVER_IMAGE=${CORTX_SERVER_IMAGE}
                            export CORTX_DATA_IMAGE=${CORTX_DATA_IMAGE}
                            export CORTX_CONTROL_IMAGE=${CORTX_CONTROL_IMAGE}
                        fi
                        # export DEPLOYMENT_METHOD=${DEPLOYMENT_METHOD}
                        export SNS_CONFIG=${SNS_CONFIG}
                        export DIX_CONFIG=${DIX_CONFIG}
                        export EXTERNAL_EXPOSURE_SERVICE=${EXTERNAL_EXPOSURE_SERVICE}
                        export CONTROL_EXTERNAL_NODEPORT=${CONTROL_EXTERNAL_NODEPORT}
                        export S3_EXTERNAL_HTTP_NODEPORT=${S3_EXTERNAL_HTTP_NODEPORT}
                        export S3_EXTERNAL_HTTPS_NODEPORT=${S3_EXTERNAL_HTTPS_NODEPORT}
                        export NAMESPACE=${NAMESPACE}
                        ./cortx-deploy.sh --cortx-cluster
                    popd
                '''
                sh label: 'Perform IO Sanity Test', script: '''
                    pushd solutions/kubernetes/
                        ./cortx-deploy.sh --io-sanity
                    popd
                '''
            }
        }

        stage('QA SETUP') {
            when {
                anyOf {
                    expression { params.RUN_QA_SANITY == true }
                    expression { params.RUN_HARE_SANITY == true }
                    expression { params.RUN_MANUAL_TEST == true }
                }
            }
            steps {
                script { build_stage = env.STAGE_NAME }
                echo "QA CODE CHECKOUT"
                cleanWs()
                checkout([$class: 'GitSCM', branches: [[name: '${CORTX_TEST_BRANCH}']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'rel_sanity_github_auto', url: '${CORTX_TEST_REPO}']]])

                echo "QA ENV SETUP"
                echo "${WORKSPACE}"
                sh label: '', script: '''sh scripts/jenkins_job/virt_env_setup.sh . venv
                source venv/bin/activate
                python --version
                export ADMIN_USR="${ADMIN_USR}"
                export ADMIN_PWD="${ADMIN_PWD}"
                export HOST_PASS="${HOST_PASS}"
                export Target_Node="${Target_Node}"
                export EXTERNAL_EXPOSURE_SERVICE="${EXTERNAL_EXPOSURE_SERVICE}"
                deactivate
                '''

                echo "QA_CLIENT_CONFIG"
                sh label: '', script: '''source venv/bin/activate
                export PYTHONPATH=$WORKSPACE:$PYTHONPATH
                echo "PYTHONPATH= " $PYTHONPATH
                echo "Target_Node= " $Target_Node
                echo "EXTERNAL_EXPOSURE_SERVICE= " $EXTERNAL_EXPOSURE_SERVICE
                sh scripts/cicd_k8s/lb_haproxy.sh
                python3.7 scripts/cicd_k8s/client_multinode_rgw.py --master_node "${M_NODE}" --password "${HOST_PASS}"
                deactivate
               '''
            }
        }

        stage('QA TEST EXECUTION') {
            when { expression { params.RUN_QA_SANITY == true } }
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: '', script: '''source venv/bin/activate
export PYTHONPATH=$WORKSPACE:$PYTHONPATH
echo $PYTHONPATH
export Target_Node="${Target_Node}"
sh scripts/cicd_k8s/run_feature_tests.sh ${Target_Node}
deactivate
'''
            }
        }


//         stage('QA_SANITY_TEST_EXECUTION') {
//         when {expression { params.RUN_QA_SANITY == true }}
//             steps{
//                 script {
//                     env.Sanity_Failed = false
//                     env.Health = 'OK'
//                     status = sh (label: '', returnStatus: true, script: '''#!/bin/sh
// source venv/bin/activate
// set +x
// echo 'Creating s3 account and configuring awscli on client'
// pytest scripts/jenkins_job/aws_configure.py::test_create_acc_aws_conf --local True --target ${Target_Node}
// set -e
// echo "Running Sanity Tests"
// set -x; python3 -u testrunner.py -te=TEST-42724 -tp=TEST-42722 -tg=${Target_Node} -b=${Build_VER} -t=${Build_Branch} --force_serial_run ${Sequential_Execution} -d=${DB_Update} --xml_report True --validate_certs False
// deactivate
// ''' )
//                 if ( fileExists('log/latest/failed_tests.log') ) {
//                     def failures = readFile 'log/latest/failed_tests.log'
//                     def lines = failures.readLines()
//                     if (lines) {
//                         echo "Sanity Test Failed"
//                         env.Sanity_Failed = true
//                         currentBuild.result = 'FAILURE'
//                     }
//                 }
//                 if ( status != 0 ) {
//                     currentBuild.result = 'FAILURE'
//                     env.Health = 'Not OK'
//                     env.Sanity_Failed = true
//                     error('Aborted Sanity due to bad health of deployment')
//                 }
//                 }
//             }
//         }

        stage('HARE_QA_SANITY') {
            when { expression { params.RUN_HARE_SANITY == true } }
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    try {
                        sh label: '', script: ''' source venv/bin/activate
                        export PYTHONPATH=$WORKSPACE:$PYTHONPATH
                        echo $PYTHONPATH
                        export SENDER_MAIL_ID='pavan.k.thunuguntla@seagate.com'
                        export PYTHONHTTPSVERIFY='0'
                        export mail_host='mailhost.seagate.com'
                        export port='587'
                        pytest -m "hare_sanity" --local True --target "multi-node-ssc-vm-g2-rhev4-3161" --validate_certs False --health_check False --junitxml "log/latest/results_hare_qa_sanity.xml" --html "log/latest/results_hare_qa_sanity.html"
                        deactivate
                        '''
                    }
                    catch (err) {
                        currentBuild.result = "FAILURE"
                        sh label: '', script: '''source venv/bin/activate
                        deactivate
                        '''
                    }
                }
            }
        }

        stage('RUN_MANUAL_TEST') {
            when { expression { params.RUN_MANUAL_TEST == true } }
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: '', script: '''source venv/bin/activate
export PYTHONPATH=$WORKSPACE:$PYTHONPATH
echo $PYTHONPATH
export Target_Node="${Target_Node}"
#pytest tests/ha/test_pod_failure.py::TestPodFailure::test_io_operation_pod_shutdown_scale_replicas
pytest ${MANUAL_TEST} --local True --target "multi-node-ssc-vm-g2-rhev4-3161" --validate_certs False --use_ssl False --junitxml "log/latest/results_run_manual_test.xml" --html "log/latest/results_run_manual_test.html"
deactivate
'''
            }
        }

//         stage ('Destory Cluster') {
//             when {expression { params.RUN_DEPLOY == true }}
//             steps {
//                 script { build_stage = env.STAGE_NAME }
//                 sh label: 'Destroy Cluster', script: '''
// cd ${WORKSPACE}/solutions/kubernetes/
// #pushd solutions/kubernetes/
// echo $hosts | tr ' ' '\n' > hosts
// cat hosts
// ./cortx-deploy.sh --destroy-cluster
// popd
// '''
//             }
//         }

    }

    post {
        always {
            script {
                // Jenkins Summary
                clusterStatus = ""
                if (fileExists('/var/tmp/cortx-cluster-status.txt') && currentBuild.currentResult == "SUCCESS") {
                    clusterStatus = readFile(file: '/var/tmp/cortx-cluster-status.txt')
                    MESSAGE = "Hare Sanity Success for the build ${build_id}"
                    ICON = "accept.gif"
                    STATUS = "SUCCESS"
                   # if ( fileExists('log/latest/results*.log') ) {
                   #     echo "Sanity Passed"
                   # }
                } else if ( currentBuild.currentResult == "FAILURE" ) {
                    manager.buildFailure()
                    MESSAGE = "Hare Sanity Failed for the build ${build_id} in ${build_stage} stage."
                    ICON = "error.gif"
                    STATUS = "FAILURE"

                // } else {
                //     manager.buildUnstable()
                //     MESSAGE = "CORTX Cluster Setup is Unstable"
                //     ICON = "warning.gif"
                //     STATUS = "UNSTABLE"
                }

                clusterStatusHTML = "<pre>${clusterStatus}</pre>"
                manager.createSummary("${ICON}").appendText("<h3>CORTX Cluster Setup ${currentBuild.currentResult} </h3><p>Please check <a href=\"${BUILD_URL}/console\">cluster setup logs</a> for more info <h4>Cluster Status:</h4>${clusterStatusHTML}", false, false, false, "red")
                junit allowEmptyResults: true, testResults: 'log/latest/results.xml'
                // Email Notification
                env.build_stage = "${build_stage}"
                env.cluster_status = "${clusterStatusHTML}"
                def recipientProvidersClass = [[$class: 'RequesterRecipientProvider']]
                mailRecipients = "pavan.k.thunuguntla@seagate.com"
                // subject: "[Hare_k8s_Sanity_PR Build#${build_id} 5Node Deployment]: Jenkins Build ${currentBuild.currentResult} Deployment="${STATUS}", SanityTest=unstable, Regression=unstable",
                emailext(
                        body: '''${SCRIPT, template="cluster-setup-email.template"}''',
                        mimeType: 'text/html',
                        subject: "[Hare_k8s_Sanity_PR Build#${build_id}]: 5Node Deployment, Jenkins Build ${currentBuild.currentResult}",
                        attachLog: true,
                        to: "${mailRecipients}",
                        recipientProviders: recipientProvidersClass
                )
            }
        }

        // cleanup {
        //     sh label: 'Collect Artifacts', script: '''
        //     mkdir -p artifacts
        //     pushd solutions/kubernetes/
        //         HOST_FILE=$PWD/hosts
        //         PRIMARY_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
        //         [ -f /var/tmp/cortx-cluster-status.txt ] && cp /var/tmp/cortx-cluster-status.txt $WORKSPACE/artifacts/
        //         scp -q "$PRIMARY_NODE":/root/deploy-scripts/k8_cortx_cloud/solution.yaml $WORKSPACE/artifacts/
        //         if [ -f /var/tmp/cortx-cluster-status.txt ]; then
        //             cp /var/tmp/cortx-cluster-status.txt $WORKSPACE/artifacts/
        //         fi
        //     popd
        //     '''
        //     script {
        //         // Archive Deployment artifacts in jenkins build
        //         archiveArtifacts artifacts: "artifacts/*.*", onlyIfSuccessful: false, allowEmptyArchive: true
        //     }
        // }
        //
        // failure {
        //     sh label: 'Collect CORTX support bundle logs in artifacts', script: '''
        //     mkdir -p artifacts
        //     pushd solutions/kubernetes/
        //         ./cortx-deploy.sh --support-bundle
        //         HOST_FILE=$PWD/hosts
        //         PRIMARY_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
        //         LOG_FILE=$(ssh -o 'StrictHostKeyChecking=no' $PRIMARY_NODE 'ls -t /root/deploy-scripts/k8_cortx_cloud | grep logs-cortx-cloud | grep .tar | head -1')
        //         scp -q "$PRIMARY_NODE":/root/deploy-scripts/k8_cortx_cloud/$LOG_FILE $WORKSPACE/artifacts/
        //     popd
        //     '''
        // }
    }
}

