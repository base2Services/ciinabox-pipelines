pipeline {
    parameters {
        string(name: 'JENKINS_HOST',  description: 'Full jenkins host name for cloudwatch identifier')
        string(name: 'AWS_DEFAULT_REGION',  description: 'Default region for SDK to run')
    }
    environment {
        JENKINS_HOST = params.JENKINS_HOST
        AWS_DEFAULT_REGION = params.AWS_DEFAULT_REGION
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