pipeline {
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