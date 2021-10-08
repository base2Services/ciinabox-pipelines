
def call(body) {
    def config = body

    pipeline {
        environment {
            JENKINS_HOST = config.jenkinsHost
            AWS_DEFAULT_REGION = config.region
        }

        agent {
            label 'docker'
        }

        stages {

            stage('Publish Success Metric to CloudWatch') {
                agent {
                    docker {
                    image 'ghcr.io/base2services/jenkmon'
                    }
                }
                steps {
                    sh "python /app/jenkmon.py"
                }
            }

        }
    }
}