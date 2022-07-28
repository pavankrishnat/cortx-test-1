
pipeline {
    agent {
        node {
            label "${Client_Node}"
            customWorkspace "/root/${JOB_BASE_NAME}"
        }
    }

    options {
        timeout(time: 240, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm')
    }

    //parameters {}

    //environment {}

    stages {
        stage('CODE_CHECKOUT') {
            steps {
                cleanWs()
                checkout([$class: 'GitSCM', branches: [[name: "*/CORTX33668"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'rel_sanity_github_auto', url: "https://github.com/pavankrishnat/cortx-test-1/"]]])
                withCredentials([file(credentialsId: 'qa_secrets_json_new', variable: 'secrets_json_path')]) {
                    sh "cp /$secrets_json_path $WORKSPACE/secrets.json"
                }
                sh script: '''
pwd
ls -lsrth comptests/hare/resource_fw.py
scp -r -o StrictHostKeyChecking=no $WORKSPACE/comptests/hare/resource_fw.py root@ssc-vm-g4-rhev4-1587.colo.seagate.com:/root/resource_fw.py
ssh root@ssc-vm-g4-rhev4-1587.colo.seagate.com "cd && python3 resource_fw.py --config
'''
            }
        }

        stage ('Stage 1') {
            parallel {
                stage("Monitor script with Deployment") {
                    steps {
                        sh script: '''
ssh root@ssc-vm-g4-rhev4-1587.colo.seagate.com "cd && python3 resource_fw.py -p phase1_deployment -t 10"
'''
                    }
                }
                stage("Deployment") {
                    steps {
                        script { build_stage = env.STAGE_NAME }
                        script {
                            try {
                                def buildCortxdeploy = build job: 'Cortx-Automation/RGW/setup-cortx-rgw-cluster', wait: true,
                                        parameters: [
                                                text(name: 'hosts', value: "${params.VM_List}"),
                                                string(name: 'SNS_CONFIG', value: "${params.SNS_CONFIG}"),
                                                string(name: 'DIX_CONFIG', value: "${params.DIX_CONFIG}"),
                                                string(name: 'CORTX_CONTROL_IMAGE', value: "ghcr.io/seagate/cortx-control:2.0.0-874"),
                                                string(name: 'CORTX_DATA_IMAGE', value: "ghcr.io/seagate/cortx-data:2.0.0-874"),
                                                string(name: 'CORTX_SERVER_IMAGE', value: "ghcr.io/seagate/cortx-rgw:2.0.0-874")
                                        ]
                            } catch (err) {
                                build_stage = env.STAGE_NAME
                                error "Failed to Deploy"
                            }
                        }
                    }
                }
            }
        }

        stage ('stage 2: Before io') {
            steps {
                sh script: '''
ssh root@ssc-vm-g4-rhev4-1587.colo.seagate.com "cd && python3 resource_fw.py  -p phase2_before_io -t 60"
'''
            }
        }

        stage ('Stage 3') {
            parallel {
                stage("Monitor script with IOs") {
                    steps {
                        sh script: '''
ssh root@ssc-vm-g4-rhev4-1587.colo.seagate.com "cd && python3 resource_fw.py  -p phase3_during_io -t 5"
'''
                    }
                }
                stage("IO's") {
                    steps {
                        script { build_stage = env.STAGE_NAME }
                        script {
                            echo "${WORKSPACE}"
                            echo "SETUP_ENTRY : cicd_deploy_ssc-vm-g2-rhev4-3159_3"
                            echo "TEST_Name : ${params.TEST_Name}"
                            echo "CLIENT_SESSIONS_PER_WORKER_NODE : ${params.CLIENT_SESSIONS_PER_WORKER_NODE}"
                            // echo "DURATION_OF_TEST_IN_DAYS : ${params.DURATION_OF_TEST_IN_DAYS}"
                            sh label: '', script: '''
                    yum install -y nfs-utils
                    yum install -y s3cmd
                    yum install -y s3fs-fuse
                    sh scripts/jenkins_job/virt_env_setup.sh . venv
                    source venv/bin/activate
                    python --version
                    deactivate
                '''
                        }
                        script {
                            try {
                                sh label: '', script: ''' source venv/bin/activate
                    export PYTHONPATH=$WORKSPACE:$PYTHONPATH
                    test_name=($TEST_Name)
                    pytest -k $test_name --local True --target cicd_deploy_ssc-vm-g4-rhev4-1587_2 --validate_certs False --health_check False
                    deactivate
                    '''
                            }
                            catch (err) {
                                currentBuild.result = "FAILURE"
                                sh label: '', script: '''source venv/bin/activate
                    chmod a+x scripts/cicd_k8s_cortx_deploy/log_collecter.sh
                    . ./scripts/cicd_k8s_cortx_deploy/log_collecter.sh ${BUILD} ${SETUP_ENTRY} ${WORKSPACE} IOsrun
                    echo $LOG_PATH
                    deactivate
                    '''
                            }
                        }
                    }
                }
            }
        }

        stage ('stage 4: After io') {
            steps {
                sh script: '''
ssh root@ssc-vm-g4-rhev4-1587.colo.seagate.com "cd && python3 resource_fw.py  -p phase4_after_io -t 60"
'''
            }
        }

        stage ('Stage 5') {
            parallel {
                stage("Monitor script with Destroy") {
                    steps {
                        sh script: '''
ssh root@ssc-vm-g4-rhev4-1587.colo.seagate.com "cd && python3 resource_fw.py  -p phase5_destroy -t 10"
'''
                    }
                }
                stage("Destroy") {
                    steps {
                        script { build_stage = env.STAGE_NAME }
                        script {
                            try {
                                def buildCortxdeploy = build job: 'Cortx-kubernetes/destroy-cortx-cluster', wait: true,
                                        parameters: [
                                                text(name: 'hosts', value: "${params.VM_List}")
                                        ]
                            } catch (err) {
                                build_stage = env.STAGE_NAME
                                error "Failed to Destroy"
                            }
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            echo "End of jenkins job"
        }
    }
}
