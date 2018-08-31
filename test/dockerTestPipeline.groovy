@Library('ciinabox_tests') _

pipeline {
  agent any
  parameters {
    string(name: 'OPS_ACCOUNT_ID', defaultValue: '', description: '')
    string(name: 'DEV_ACCOUNT_ID', defaultValue: '', description: '')
    string(name: 'AWS_REGION', defaultValue: 'ap-southeast-2', description: '')
    string(name: 'ECR_REPO', defaultValue: '', description: '')
  }
  stages {
    stage('ecr repo') {
      agent {
        node {
          label 'docker'
        }
      }
      steps {
        ecr accountId: params.OPS_ACCOUNT_ID,
          region: params.AWS_REGION,
          image: 'demo/nginx',
          otherAccountIds: [params.DEV_ACCOUNT_ID],
          taggedCleanup: ['develop']
      }
    }
    stage('build image') {
      agent {
        node {
          label 'docker'
        }
      }
      steps {
        sh """
        cat <<EOT > Dockerfile
        FROM nginx
        EOT
        """
        dockerBuild repo: params.ECR_REPO,
          image: "demo/nginx",
          dockerfile: './Dockerfile',
          tags: [ 'develop'],
          push: true,
          cleanup: true
      }
    }
  }
}
