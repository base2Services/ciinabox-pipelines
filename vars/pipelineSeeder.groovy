/***********************************
  pipelineSeeder Pipeline

  Allows seeding of Jenkins pipeline and JobDSL jobs from a shared-pipeline-repo

  Usage:
  Add Jenkinsfile to shared-pipeline-repo

  ```
  @Library(['ciinabox']) _
  pipelineSeeder()
  ```
  This will scan a directory called pipelines for .groovy (pipelines) and .job (Job DSL) and create them

************************************/
def call(body) {

  pipeline {
    agent any
    stages {
      stage('Prepare') {
        steps {
          sh 'printenv | sort'
        }
      }
      stage('Seed') {
        when {
          anyOf {
            branch 'master';
            branch 'main';
            expression { env.GIT_BRANCH == 'origin/main'};
            expression { env.GIT_BRANCH == 'origin/master'};
          }
        }
        steps {
          script {
            dir('pipelines') {
              def dirs = sh(script: 'find . -type d -maxdepth 1', returnStdout: true).split('\n')[1..-1]
              writeFile text: dirs.join('\n').replace('./',''), file: 'dirs.txt'
              sh 'cat dirs.txt'
              dirs.each { dir ->
                def pipelineScripts = sh(script: "find ${dir} -name '*.groovy' -maxdepth 1", returnStdout: true).split('\n')
                writeFile text: pipelineScripts.join('\n').replace('./','').replace('.groovy', ''), file: "${dir}.txt"
                sh "cat ${dir}.txt"
              }
            }
            jobDsl scriptText: libraryResource('seeder.groovy'),
              removedConfigFilesAction: 'DELETE', 
              removedJobAction: 'DELETE', 
              removedViewAction: 'DELETE',
              ignoreMissingFiles: true

            jobDsl targets: 'pipelines/**/*.job',
              removedConfigFilesAction: 'DELETE', 
              removedJobAction: 'DELETE', 
              removedViewAction: 'DELETE',
              ignoreMissingFiles: true
          }
        }
      }
    }
    post {
      always {
        deleteDir()
      }
    }
  }
}
