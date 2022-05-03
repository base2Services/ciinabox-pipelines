/***********************************
jenkMon Pipeline

execute and report jenkins agaent status to cloudwatch metrics

jenkMon()

************************************/

def call(body) {
    def config= [:]

    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def check_docker = config.get('checkDocker', 'true')
    def agent_label = config.get('agentLabel', 'linux')

    pipeline {
        agent {
            label agent_label
        }
        stages {
            stage('Docker check') {
                when { 
                    expression {
                        check_docker == 'true'
                    }
                }
                steps {
                    sh 'docker info'
                }
            }
            stage('Publish Success Metric to CloudWatch') {
                steps {
                    putCloudWatchMetric(
                        namespace: 'Ciinabox/Jenkins',
                        metricName: 'HealthyAgent',
                        dimensions: [
                            'Jenkins': env.JENKINS_URL,
                            'Label': agent_label,
                            'Monitoring': 'JenkMon'
                        ],
                        value: 1
                    )
                }
            }
        }
    }
}