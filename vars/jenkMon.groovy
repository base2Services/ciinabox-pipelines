import com.base2.ciinabox.aws.Util

pipeline {
    agent {
        label 'docker'
    }
    environment {
        'AWS_DEFAULT_REGION': Util.getRegion())
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